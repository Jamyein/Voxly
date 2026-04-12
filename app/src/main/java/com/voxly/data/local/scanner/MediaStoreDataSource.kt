package com.voxly.data.local.scanner

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
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
    private val metadataProcessor: TagLibMetadataProcessor
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
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED
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
     */
    suspend fun queryFromDirectory(
        relativePath: String,
        minDurationEnabled: Boolean,
        minDurationMs: Long
    ): List<AudioFile> = withContext(Dispatchers.IO) {
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
            cursorToAudioFiles(cursor, audioFiles, minDurationEnabled, minDurationMs)
        }

        audioFiles
    }

    /**
     * Query all audio files using MediaStore.
     */
    suspend fun queryAll(
        minDurationEnabled: Boolean,
        minDurationMs: Long
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI, FAST_PROJECTION, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            cursorToAudioFiles(cursor, audioFiles, minDurationEnabled, minDurationMs)
        }

        Timber.d(TAG, "Full scan complete: ${audioFiles.size} files found")
        audioFiles
    }

    /**
     * Query lightweight file info (path + modification time) for incremental scanning.
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
                    val lastModified = cursor.getLong(modifiedCol) * 1000
                    output.add(filePath to lastModified)
                }
            }
        }
        return output
    }

    /**
     * Query directory file paths and modification times for incremental scanning.
     */
    fun queryDirectoryFilePathsAndModificationTimes(directory: File): List<Pair<String, Long>> {
        val output = mutableListOf<Pair<String, Long>>()
        collectDirectoryFileModificationTimes(directory, output)
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
                bitrate = cursor.getInt(1) / 1000
            }
        }

        // Fallback to TagLib audio info if not provided by complete metadata
        val audioInfo = completeMetadata?.audioInfo ?: metadataProcessor.readAudioInfo(filePath)
        if (duration == 0L) duration = audioInfo?.durationMs ?: 0L
        if (bitrate == 0) bitrate = (audioInfo?.bitrate ?: 0) / 1000

        return AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = duration,
            format = extension.uppercase(),
            bitrate = bitrate,
            sampleRate = audioInfo?.sampleRate ?: 0,
            channels = audioInfo?.channels ?: 0,
            metadata = fullMetadata
        )
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
                bitrate = cursor.getInt(1) / 1000
            }
        }

        Pair(duration, bitrate)
    }

    /**
     * Convert MediaStore cursor to AudioFile list.
     */
    private fun cursorToAudioFiles(
        cursor: Cursor,
        output: MutableList<AudioFile>,
        minDurationEnabled: Boolean,
        minDurationMs: Long
    ) {
        val columns = CursorColumns(cursor)

        while (cursor.moveToNext()) {
            val displayName = cursor.getString(columns.name) ?: continue
            val relativePath = cursor.getString(columns.relativePath)
            val filePath = buildPathFromRelativePath(relativePath, displayName)
            val extension = displayName.substringAfterLast('.', "")

            if (AudioFormat.fromExtension(extension) == AudioFormat.OTHER) continue

            val duration = cursor.getLong(columns.duration)
            if (duration != 0L && minDurationEnabled && duration < minDurationMs) continue

            val albumId = cursor.getLong(columns.albumId).takeIf { it > 0L }
            val (trackNum, totalTracks) = parseMediaStoreTrackField(cursor.getInt(columns.track))

            val yearInt = cursor.getInt(columns.year)
            val metadata = com.voxly.domain.model.AudioMetadata(
                title = cursor.getString(columns.title)?.takeIf { it.isNotBlank() },
                artist = cursor.getString(columns.artist)?.takeIf { it.isNotBlank() },
                album = cursor.getString(columns.album)?.takeIf { it.isNotBlank() },
                year = if (yearInt > 0) yearInt.toString() else null,
                trackNumber = trackNum,
                totalTracks = totalTracks,
                albumArt = null,
                albumArtist = null,
                genre = null,
                discNumber = null,
                totalDiscs = null,
                composer = null,
                lyricist = null,
                conductor = null,
                originalArtist = null,
                comment = null,
                lyrics = null,
                customFields = emptyMap()
            )

            output.add(
                AudioFile(
                    id = cursor.getLong(columns.id).toString(),
                    path = filePath,
                    name = displayName,
                    size = cursor.getLong(columns.size),
                    duration = duration,
                    format = extension.uppercase(),
                    bitrate = cursor.getInt(columns.bitrate) / 1000,
                    sampleRate = 0,
                    channels = 0,
                    mediaStoreAlbumId = albumId,
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
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val bitrate = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val relativePath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
    }

    /**
     * Collect file modification times from directory recursively.
     */
    private fun collectDirectoryFileModificationTimes(
        directory: File,
        output: MutableList<Pair<String, Long>>
    ) {
        if (!directory.exists() || !directory.isDirectory) return

        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectDirectoryFileModificationTimes(file, output)
                file.extension.lowercase() in AUDIO_EXTENSIONS && file.canRead() -> {
                    output.add(file.absolutePath to file.lastModified())
                }
            }
        }
    }

    /**
     * Convert content URI to filesystem path.
     */
    fun getPathFromUri(uri: Uri): String {
        return runCatching {
            when {
                uri.scheme == "file" -> uri.path.orEmpty()
                uri.scheme != "content" -> uri.path.orEmpty()
                else -> {
                    val documentId = DocumentsContract.getTreeDocumentId(uri)
                    if (documentId.startsWith("raw:")) {
                        documentId.removePrefix("raw:")
                    } else {
                        val parts = documentId.split(":", limit = 2)
                        val volume = parts.firstOrNull().orEmpty()
                        val relativePath = parts.getOrNull(1)?.trim('/').orEmpty()

                        when {
                            volume.equals("primary", ignoreCase = true) -> {
                                val root = Environment.getExternalStorageDirectory().absolutePath
                                if (relativePath.isEmpty()) root else "$root/$relativePath"
                            }
                            volume.equals("home", ignoreCase = true) -> {
                                val root = Environment.getExternalStorageDirectory().absolutePath
                                val docsRoot = "$root/Documents"
                                if (relativePath.isEmpty()) docsRoot else "$docsRoot/$relativePath"
                            }
                            volume.isNotEmpty() -> {
                                if (relativePath.isEmpty()) "/storage/$volume"
                                else "/storage/$volume/$relativePath"
                            }
                            else -> uri.path.orEmpty()
                        }
                    }
                }
            }
        }.getOrElse { uri.path.orEmpty() }
    }

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
}
