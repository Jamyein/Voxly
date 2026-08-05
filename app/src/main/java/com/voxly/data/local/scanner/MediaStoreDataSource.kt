package com.voxly.data.local.scanner

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.voxly.core.util.Constants
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.model.parseMediaStoreTrackField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStore data source for audio file queries.
 * Handles all interactions with Android's MediaStore API.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val safWriteAccessService: SafWriteAccessService
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "MediaStoreDataSource"
        private val AUDIO_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "ape", "opus")

        // Fast projection - only MediaStore columns, no file parsing needed
        private val FAST_PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.COMPOSER
        )
    }

    /**
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
    }

    /**
     * Query audio files from a specific directory using MediaStore.
     *
     * Returns every audio file in scope (raw). Whitelist/blacklist/min-duration
     * filtering is applied downstream by the read-stage filteredAllAudios flow,
     * so short files and excluded paths remain in the cache for instant toggles.
     */
    suspend fun queryFromDirectory(
        relativePath: String
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("MediaStore queryFromDirectory: relativePath=$relativePath")
        val audioFiles = mutableListOf<AudioFile>()

        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND (")
            append("${MediaStore.Audio.Media.RELATIVE_PATH} = ?")
            append(" OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
            append(")")
        }
        val selectionArgs = arrayOf(
            relativePath,
            "$relativePath%"
        )

        contentResolver.query(
            AUDIO_URI, FAST_PROJECTION, selection, selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            cursorToAudioFiles(cursor, audioFiles)
        }

        audioFiles
    }

    /**
     * Query all audio files using MediaStore.
     *
     * Returns every audio file in scope (raw). Whitelist/blacklist/min-duration
     * filtering is applied downstream by the read-stage filteredAllAudios flow.
     */
    suspend fun queryAll(): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("MediaStore queryAll")
        val audioFiles = mutableListOf<AudioFile>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI, FAST_PROJECTION, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            cursorToAudioFiles(cursor, audioFiles)
        }

        Timber.d(TAG, "Full scan complete: ${audioFiles.size} files found")
        audioFiles
    }

    /**
     * Query lightweight file info (path + modification time) for incremental scanning.
     * Fetches ALL audio file paths + mtimes from MediaStore for in-memory diffing.
     * Prefer [queryFilesChangedSince] when a last-scan timestamp is available.
     */
    fun queryFilePathsAndModificationTimes(): List<Pair<String, Long>> {
        val output = mutableListOf<Pair<String, Long>>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATE_MODIFIED
            ),
            selection, null, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameCol) ?: continue
                val relativePath = cursor.getString(relativeCol)
                val filePath = buildPathFromRelativePath(relativePath, displayName)
                val extension = displayName.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val lastModified = cursor.getLong(modifiedCol) * Constants.MS_PER_SECOND
                    output.add(filePath to lastModified)
                }
            }
        }
        return output
    }

    /**
     * Result of enumerating a directory tree: whether the tree was fully
     * readable and the audio files found inside it.
     *
     * Callers MUST NOT treat an inaccessible tree as "empty": an unreadable
     * directory (scoped-storage File-walk failure, missing SAF tree URI) would
     * otherwise make every cached file inside it look "deleted", triggering a
     * purge of valid entries. Lesson #24.
     */
    data class DirectoryFileListing(
        val accessible: Boolean,
        val files: List<Pair<String, Long>>
    )

    /**
     * Query directory file paths and modification times by walking the actual
     * filesystem. This is the authoritative fallback enumeration: it sees every
     * audio-extension file on disk, indexed by MediaStore or not (USB/SD
     * volumes, unindexed trees — lesson #24).
     */
    fun queryDirectoryFilePathsAndModificationTimes(directory: File): DirectoryFileListing {
        val output = mutableListOf<Pair<String, Long>>()
        val accessible = collectDirectoryFileModificationTimes(directory, output)
        return DirectoryFileListing(accessible = accessible, files = output)
    }

    /**
     * MediaStore-indexed path enumeration of a primary-storage directory
     * subtree (RELATIVE_PATH range query), with mtimes re-statted from the
     * real filesystem. Returns null when the directory is not under primary
     * external storage (MediaStore cannot scope the query to it) or the query
     * yields no live files — both signal "walk the filesystem instead".
     */
    fun queryDirectoryPathsAndMtimesViaMediaStore(directory: File): DirectoryFileListing? {
        val relativePath = getRelativePathFromAbsolute(directory.absolutePath) ?: return null
        // Empty relative path means the storage root was selected — a
        // `LIKE '%'` would match the ENTIRE audio collection, not just this
        // tree. Root-directory whitelists are handled by the filesystem walk
        // (same as the full-scan path).
        if (relativePath.isBlank()) return null
        val files = mutableListOf<Pair<String, Long>>()
        val selection = buildString {
            append("${MediaStore.Audio.Media.RELATIVE_PATH} = ?")
            append(" OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
        }
        val selectionArgs = arrayOf(relativePath, "$relativePath%")
        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
            ),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameCol) ?: continue
                val relative = cursor.getString(relativeCol)
                val extension = displayName.substringAfterLast('.', "").lowercase()
                if (extension !in AUDIO_EXTENSIONS) continue
                val path = buildPathFromRelativePath(relative, displayName)
                // mtime must be like-for-like with the cache, which stores
                // File.lastModified() millis (lesson #27). MediaStore's
                // DATE_MODIFIED is second-precision AND lags external edits
                // until its scanner observes them — a diff against it would
                // silently miss edits (and a sub-second truncation is a
                // non-issue thanks to the 1s tolerance). Re-stat the real path
                // (µs per file); DATE_MODIFIED is only a fallback for the
                // rare genuinely-unstatable file.
                val mtime = File(path).lastModified()
                if (mtime <= 0L) {
                    // File gone from disk (MediaStore row stale) — skip it so
                    // the caller's purge sees it as deleted instead of keeping
                    // a phantom row in currentFiles (deletion latency is the
                    // whole point of the incremental scan, lesson #14).
                    continue
                }
                files.add(path to mtime)
            }
        }
        // Empty result is ambiguous (genuinely empty vs unindexed tree) — let
        // the caller fall back to the filesystem walk rather than risk treating
        // an unindexed tree as "empty" (lesson #24: never purge against an
        // inaccessible/unknown tree).
        if (files.isEmpty()) return null
        return DirectoryFileListing(accessible = true, files = files)
    }

    /**
     * Incremental-change query: returns only files whose `DATE_MODIFIED` is
     * strictly greater than [timestampSecs] (Unix epoch seconds). This is the
     * key optimization over [queryFilePathsAndModificationTimes]: instead of
     * fetching ALL files and diffing mtime in-memory, the ContentProvider does
     * the filtering for us (backed by an index on `date_modified`).
     *
     * Falls back to the full query when [timestampSecs] <= 0 (no scan history).
     *
     * @return list of (filePath, lastModifiedInMillis) for each changed file.
     */
    fun queryFilesChangedSince(timestampSecs: Long): List<Pair<String, Long>> {
        if (timestampSecs <= 0L) return queryFilePathsAndModificationTimes()

        val output = mutableListOf<Pair<String, Long>>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf(timestampSecs.toString())

        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATE_MODIFIED
            ),
            selection, selectionArgs, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameCol) ?: continue
                val relativePath = cursor.getString(relativeCol)
                val filePath = buildPathFromRelativePath(relativePath, displayName)
                val extension = displayName.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val lastModified = cursor.getLong(modifiedCol) * Constants.MS_PER_SECOND
                    output.add(filePath to lastModified)
                }
            }
        }
        return output
    }

    /**
     * Fast path-only query for deletion detection. Returns the complete set of
     * audio-file paths currently tracked by MediaStore, with only the
     * minimum columns projected. Noticeably cheaper than the full mtime query.
     */
    fun queryAllPaths(): Set<String> {
        val output = mutableSetOf<String>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
            ),
            selection, null, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameCol) ?: continue
                val relativePath = cursor.getString(relativeCol)
                val filePath = buildPathFromRelativePath(relativePath, displayName)
                val extension = displayName.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    output.add(filePath)
                }
            }
        }
        return output
    }

    /**
     * Recursively scan directory for audio files not yet in MediaStore.
     */
    suspend fun scanDirectoryRecursive(directory: File): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()
        scanDirectoryRecursiveInternal(directory, audioFiles)
        audioFiles
    }

    /**
     * Scan directory via SAF DocumentFile API when File.listFiles() returns empty.
     * Used as fallback for SAF-managed paths where MediaStore hasn't indexed new files yet.
     */
    suspend fun scanDirectoryViaSaf(directoryPath: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val treeUri = safWriteAccessService.findTreeUriForPath(directoryPath)
            ?: run {
                Timber.w(TAG, "No TreeUri found for path: $directoryPath")
                return@withContext emptyList()
            }

        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: run {
                Timber.w(TAG, "Cannot create DocumentFile from TreeUri: $treeUri")
                return@withContext emptyList()
            }

        val result = mutableListOf<AudioFile>()
        scanDocumentFilesRecursive(documentFile, result)
        Timber.d(TAG, "SAF scan found ${result.size} audio files in $directoryPath")
        result
    }

    /**
     * Query file paths and modification times via SAF DocumentFile API when
     * File.listFiles() returns empty.
     *
     * Returns [DirectoryFileListing] so callers can distinguish "tree exists but
     * empty" (accessible, safe to purge against) from "no tree URI / unusable"
     * (inaccessible, must NOT purge — see [DirectoryFileListing] KDoc).
     */
    fun queryDirectoryViaSaf(directoryPath: String): DirectoryFileListing {
        val treeUri = safWriteAccessService.findTreeUriForPath(directoryPath)
            ?: run {
                Timber.w(TAG, "No TreeUri found for path: $directoryPath")
                return DirectoryFileListing(accessible = false, files = emptyList())
            }

        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: run {
                Timber.w(TAG, "Cannot create DocumentFile from TreeUri: $treeUri")
                return DirectoryFileListing(accessible = false, files = emptyList())
            }

        val result = mutableListOf<Pair<String, Long>>()
        val accessible = collectDocumentFilesViaSafRecursive(documentFile, result)
        Timber.d(TAG, "SAF query found ${result.size} audio files in $directoryPath")
        return DirectoryFileListing(accessible = accessible, files = result)
    }

    private fun collectDocumentFilesViaSafRecursive(
        docFile: DocumentFile,
        output: MutableList<Pair<String, Long>>
    ): Boolean {
        val children = runCatching { docFile.listFiles() }.getOrNull() ?: return false
        var accessible = true
        children.forEach { child ->
            runCatching {
                when {
                    child.isDirectory -> {
                        if (!collectDocumentFilesViaSafRecursive(child, output)) accessible = false
                    }
                    child.isFile -> {
                        val name = child.name
                        if (name != null && name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                            val path = getPathFromDocumentUri(child.uri)
                            if (path != null) {
                                // Use the real filesystem mtime, NOT
                                // DocumentFile.lastModified(): the SAF provider's
                                // value can have a different precision/rounding
                                // than File.lastModified(), which would make every
                                // cached mtime "differ" and force a full TagLib
                                // re-read on the next incremental launch (the
                                // cache stores File-based mtimes). Lesson #26.
                                val mtime = File(path).lastModified()
                                    .takeIf { it > 0L }
                                    ?: child.lastModified()
                                output.add(path to mtime)
                            }
                        }
                    }
                }
            }.onFailure {
                Timber.w(TAG, "SAF document access failed: ${docFile.uri}", it)
                accessible = false
            }
        }
        return accessible
    }

    private suspend fun scanDocumentFilesRecursive(docFile: DocumentFile, output: MutableList<AudioFile>) {
        docFile.listFiles().forEach { child ->
            when {
                child.isDirectory -> scanDocumentFilesRecursive(child, output)
                child.isFile -> {
                    val name = child.name
                    if (name != null && name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                        val path = getPathFromDocumentUri(child.uri) ?: return@forEach
                        output.add(createAudioFileFromPath(path))
                    }
                }
            }
        }
    }

    private fun getPathFromDocumentUri(docUri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(docUri)
        if (docId.startsWith("raw:")) {
            return docId.removePrefix("raw:")
        }
        val parts = docId.split(":", limit = 2)
        val volume = parts.firstOrNull().orEmpty()
        val relativePath = parts.getOrNull(1)?.trim('/').orEmpty()
        return when {
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$relativePath"
            volume.equals("home", ignoreCase = true) -> "/storage/emulated/0/Documents/$relativePath"
            else -> "/storage/$volume/$relativePath"
        }
    }

    private suspend fun scanDirectoryRecursiveInternal(directory: File, output: MutableList<AudioFile>) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> scanDirectoryRecursiveInternal(file, output)
                file.extension.lowercase() in AUDIO_EXTENSIONS && file.canRead() -> {
                    output.add(createAudioFileFromPath(file.absolutePath))
                }
            }
        }
    }

    /**
     * Create AudioFile from path by reading file metadata.
     */
    private suspend fun createAudioFileFromPath(filePath: String): AudioFile {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        // Read metadata + audio info via TagLib
        val completeMetadata = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false)
        val fullMetadata = completeMetadata?.metadata ?: com.voxly.domain.model.AudioMetadata()

        // Try MediaStore first for duration
        var duration = 0L
        var bitrate = 0

        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.BITRATE),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                duration = cursor.getLong(0)
                bitrate = cursor.getInt(1) / Constants.BPS_TO_KBPS
            }
        }

        // Fallback to TagLib audio info if not provided by complete metadata
        val audioInfo = completeMetadata?.audioInfo ?: metadataProcessor.readAudioInfo(filePath)
        if (duration == 0L) duration = audioInfo?.durationMs ?: 0L
        if (bitrate == 0) bitrate = (audioInfo?.bitrate ?: 0) / Constants.BPS_TO_KBPS

        return AudioFile(
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = duration,
            format = AudioFormat.fromExtension(extension),
            bitrate = bitrate,
            sampleRate = audioInfo?.sampleRate ?: 0,
            channels = audioInfo?.channels ?: 0,
            metadata = fullMetadata
        )
    }

    /**
     * Queries all MediaStore fields for a single file in one ContentResolver round-trip.
     * Replaces the previous pattern of 3 separate queries (duration/bitrate, basic metadata, album id).
     * Returns null if the file is not found in MediaStore.
     */
    data class FileMediaStoreData(
        val duration: Long,
        val bitrate: Int,
        val albumId: Long?,
        val artistId: Long?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: String?,
        val trackNumber: Int?,
        val totalTracks: Int?
    )

    suspend fun queryAllMediaStoreFields(filePath: String): FileMediaStoreData? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK
            ),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val duration = cursor.getLong(0)
                val bitrate = cursor.getInt(1) / Constants.BPS_TO_KBPS
                val albumId = cursor.getLong(2).takeIf { it > 0L }
                val artistId = cursor.getLong(3).takeIf { it > 0L }
                val title = cursor.getString(4)?.takeIf { it.isNotBlank() }
                val artist = cursor.getString(5)?.takeIf { it.isNotBlank() }
                val album = cursor.getString(6)?.takeIf { it.isNotBlank() }
                val yearInt = cursor.getInt(7)
                val (trackNumber, totalTracks) = parseMediaStoreTrackField(cursor.getInt(8))
                FileMediaStoreData(
                    duration = duration,
                    bitrate = bitrate,
                    albumId = albumId,
                    artistId = artistId,
                    title = title,
                    artist = artist,
                    album = album,
                    year = if (yearInt > 0) yearInt.toString() else null,
                    trackNumber = trackNumber,
                    totalTracks = totalTracks
                )
            } else null
        }
    }

    /**
     * Query MediaStore for a single file's duration and bitrate.
     */
    suspend fun queryFileDurationAndBitrate(filePath: String): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var duration = 0L
        var bitrate = 0

        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.BITRATE),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                duration = cursor.getLong(0)
                bitrate = cursor.getInt(1) / Constants.BPS_TO_KBPS
            }
        }

        Pair(duration, bitrate)
    }

    /**
     * Query MediaStore for a single file's album ID.
     * Returns the album ID that maps to content://media/external/audio/albumart/{albumId}.
     */
    suspend fun queryMediaStoreAlbumId(filePath: String): Long? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.ALBUM_ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val albumId = cursor.getLong(0)
                return@withContext if (albumId > 0L) albumId else null
            }
        }
        null
    }

    /**
     * Query MediaStore for a single file's year.
     */
    suspend fun queryYearFromMediaStore(filePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.YEAR),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val year = cursor.getInt(0)
                return@withContext if (year > 0) year.toString() else null
            }
        }
        null
    }

    /**
     * Query MediaStore for a single file's basic metadata.
     */
    suspend fun queryBasicMetadata(filePath: String): com.voxly.domain.model.AudioMetadata? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        val selection: String
        val selectionArgs: Array<String>

        if (relativePath != null) {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(file.name, relativePath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(file.name)
        }

        contentResolver.query(
            AUDIO_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.COMPOSER
            ),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val trackValue = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                val (parsedTrack, parsedTotal) = parseMediaStoreTrackField(trackValue)
                val yearInt = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))
                com.voxly.domain.model.AudioMetadata(
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                        ?.takeIf { it.isNotBlank() },
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                        ?.takeIf { it.isNotBlank() },
                    album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                        ?.takeIf { it.isNotBlank() },
                    year = if (yearInt > 0) yearInt.toString() else null,
                    trackNumber = parsedTrack,
                    totalTracks = parsedTotal,
                    albumArtist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST))
                        ?.takeIf { it.isNotBlank() },
                    composer = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER))
                        ?.takeIf { it.isNotBlank() }
                )
            } else {
                null
            }
        }
    }

    /**
     * Convert MediaStore cursor to AudioFile list.
     */
    private fun cursorToAudioFiles(
        cursor: Cursor,
        output: MutableList<AudioFile>
    ) {
        val columns = CursorColumns(cursor)

        while (cursor.moveToNext()) {
            val displayName = cursor.getString(columns.name) ?: continue
            val relativePath = cursor.getString(columns.relativePath)
            val filePath = buildPathFromRelativePath(relativePath, displayName)
            val extension = displayName.substringAfterLast('.', "")

            if (AudioFormat.fromExtension(extension) == AudioFormat.OTHER) continue

            val duration = cursor.getLong(columns.duration)

            val albumId = cursor.getLong(columns.albumId).takeIf { it > 0L }
            val artistId = cursor.getLong(columns.artistId).takeIf { it > 0L }
            val (trackNum, totalTracks) = parseMediaStoreTrackField(cursor.getInt(columns.track))

            val yearInt = cursor.getInt(columns.year)
            val composerStr = cursor.getString(columns.composer)?.takeIf { it.isNotBlank() }
            val metadata = com.voxly.domain.model.AudioMetadata(
                title = cursor.getString(columns.title)?.takeIf { it.isNotBlank() },
                artist = cursor.getString(columns.artist)?.takeIf { it.isNotBlank() },
                album = cursor.getString(columns.album)?.takeIf { it.isNotBlank() },
                year = if (yearInt > 0) yearInt.toString() else null,
                trackNumber = trackNum,
                totalTracks = totalTracks,
                albumArt = null,
                albumArtist = cursor.getString(columns.albumArtist)?.takeIf { it.isNotBlank() },
                genre = null,
                discNumber = null,
                totalDiscs = null,
                composer = composerStr,
                lyricist = null,
                conductor = null,
                originalArtist = null,
                comment = null,
                lyrics = null,
                customFields = emptyMap()
            )

            output.add(
                AudioFile(
                    path = filePath,
                    name = displayName,
                    size = cursor.getLong(columns.size),
                    duration = duration,
                    format = AudioFormat.fromExtension(extension),
                    mimeType = cursor.getString(columns.mimeType),
                    bitrate = cursor.getInt(columns.bitrate) / Constants.BPS_TO_KBPS,
                    sampleRate = 0,
                    channels = 0,
                    mediaStoreAlbumId = albumId,
                    mediaStoreArtistId = artistId,
                    dateAdded = cursor.getLong(columns.dateAdded),
                    metadata = metadata
                )
            )
        }
    }

    /**
     * Helper class to cache column indices.
     */
    private class CursorColumns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val artistId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val bitrate = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val relativePath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val albumArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
        val composer = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
    }

    /**
     * Collect file modification times from directory recursively.
     *
     * @return true when the whole tree was readable; false when the directory is
     *   missing/unreadable or any file/subdirectory could not be enumerated
     *   (scoped-storage listFiles()==null, unreadable file). Callers use this to
     *   decide whether an empty result means "no audio files" (safe to purge)
     *   or "couldn't look" (must NOT purge). Lesson #24.
     */
    private fun collectDirectoryFileModificationTimes(
        directory: File,
        output: MutableList<Pair<String, Long>>
    ): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false
        val children = directory.listFiles() ?: return false
        var accessible = true
        children.forEach { file ->
            when {
                file.isDirectory -> {
                    if (!collectDirectoryFileModificationTimes(file, output)) accessible = false
                }
                file.extension.lowercase() in AUDIO_EXTENSIONS -> {
                    if (file.canRead()) {
                        output.add(file.absolutePath to file.lastModified())
                    } else {
                        accessible = false
                    }
                }
            }
        }
        return accessible
    }

    /**
     * Convert content URI to filesystem path.
     * Delegates to the canonical implementation in PathUtils.
     */
    fun getPathFromUri(uri: Uri): String = com.voxly.core.util.PathUtils.getPathFromUri(uri)

    /**
     * Convert URI string to filesystem path.
     */
    fun getPathFromUriString(uriString: String): String {
        return runCatching {
            val uri = Uri.parse(uriString)
            getPathFromUri(uri)
        }.getOrElse { uriString }
    }

    private fun buildPathFromRelativePath(relativePath: String?, displayName: String): String {
        val sanitizedRelative = relativePath?.trimStart('/')?.replace('\\', '/') ?: ""
        val base = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        return if (sanitizedRelative.isBlank()) {
            "$base/$displayName"
        } else {
            "$base/$sanitizedRelative$displayName"
        }
    }

    fun getRelativePathFromAbsolute(absolutePath: String): String? {
        val normalized = absolutePath.replace('\\', '/').trimEnd('/')
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath.replace('\\', '/').trimEnd('/')
        if (!normalized.startsWith(primaryRoot)) return null
        val relative = normalized.removePrefix(primaryRoot).trimStart('/')
        return if (relative.isBlank()) "" else "$relative/"
    }

    /**
     * Check if path is inside directory.
     */
    fun isPathInsideDirectory(filePath: String, directoryPath: String): Boolean {
        val normalizedFile = filePath.trimEnd('/', '\\')
        val normalizedDir = directoryPath.trimEnd('/', '\\')
        return normalizedFile == normalizedDir ||
            normalizedFile.startsWith("$normalizedDir/") ||
            normalizedFile.startsWith("$normalizedDir\\")
    }

    /**
     * Get audio extensions set.
     */
    fun getAudioExtensions(): Set<String> = AUDIO_EXTENSIONS

    /**
     * Query artist names for a list of artist IDs.
     * Returns a map of artistId to artist name.
     */
    fun queryArtistNames(artistIds: List<Long>): Map<Long, String> {
        if (artistIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<Long, String>()
        val artistUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST
        )

        val selection = "${MediaStore.Audio.Artists._ID} IN (${artistIds.joinToString(",")})"

        contentResolver.query(
            artistUri,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(artistCol)
                if (name != null) {
                    result[id] = name
                }
            }
        }

        return result
    }
}
