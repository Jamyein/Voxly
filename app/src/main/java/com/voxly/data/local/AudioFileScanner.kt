package com.voxly.data.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.os.Environment
import com.voxly.core.util.SortUtil
import com.voxly.data.local.cache.AlbumInfoManager
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.model.parseMediaStoreTrackField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

/**
 * Local data source for scanning and accessing audio files from device storage.
 * Uses Android's MediaStore API for efficient file discovery.
 *
 * This is the single source of truth for all audio library data.
 * Automatically maintains albums and artists from cached audio files.
 *
 * Optimization features:
 * - Persistent caching with Room database
 * - Incremental scanning (only scan new/modified files)
 * - Parallel metadata reading
 * - Lazy metadata loading
 * - Auto-aggregation of albums and artists
 * 
 * Note: Cover art is loaded on-demand via MediaStore URIs and folder cover files.
 * No local WebP cache is maintained.
 */
@Singleton
class AudioFileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val albumInfoManager: AlbumInfoManager,  // Added for album info caching
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private data class AlbumAggregationKey(
        val album: String,
        val albumArtist: String
    )

    // Albums derived from cached audio files - auto-updated when cache changes
    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    // Artists derived from cached audio files - auto-updated when cache changes
    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    // Raw cached audio files from database
    val cachedAudioFilesFlow: Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()
        .catch { e ->
            Timber.e(e, "Error observing cached audio files")
        }

    // Filtered audio files - applies all filters and reacts to settings changes
    // Optimized: settings collected once per emission, no runBlocking needed
    // Uses conflate() to drop intermediate values during rapid settings changes
    val filteredAudioFiles: Flow<List<AudioFile>> = combine(
        cachedAudioFilesFlow,
        settingsDataStore.whitelistEnabled,
        settingsDataStore.blacklistEnabled,
        settingsDataStore.minDurationFilterEnabled,
        settingsDataStore.selectedDirectoryUris,
        settingsDataStore.blacklistDirectoryUris,
        settingsDataStore.minDurationFilterThresholdMs
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val files = array[0] as List<AudioFile>
        val whitelistEnabled = array[1] as Boolean
        val blacklistEnabled = array[2] as Boolean
        val minDurationEnabled = array[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val whitelistUris = array[4] as List<String>
        @Suppress("UNCHECKED_CAST")
        val blacklistUris = array[5] as List<String>
        val minDurationMs = (array[6] as Int).toLong()

        // Apply filters with pre-collected settings (no suspend calls, no I/O)
        applyFilters(
            files,
            FilterSettings(
                whitelistEnabled = whitelistEnabled,
                blacklistEnabled = blacklistEnabled,
                minDurationEnabled = minDurationEnabled,
                whitelistUris = whitelistUris,
                blacklistUris = blacklistUris,
                minDurationMs = minDurationMs
            )
        )
    }
        .conflate()
        .distinctUntilChanged { old, new ->
            // Fast path: if sizes differ, they are definitely different
            if (old.size != new.size) return@distinctUntilChanged false
            // Both empty: they are the same
            if (old.isEmpty()) return@distinctUntilChanged true
            // Compare first and last items for quick check
            // For sorted lists (by path), this should be sufficient
            old.first().path == new.first().path && old.last().path == new.last().path
        }
        .flowOn(Dispatchers.Default)
        .catch { e ->
            Timber.e(e, "Error observing filtered audio files")
            emit(emptyList())
        }

    init {
        // Auto-update albums and artists when filtered data changes
        // Uses debounce to prevent rapid recomputation during incremental scans
        applicationScope.launch(Dispatchers.Default) {
            filteredAudioFiles
                .collectLatest { files ->
                    kotlinx.coroutines.delay(50) // Debounce: wait 50ms to batch rapid updates
                    updateAlbumsAndArtistsFromFiles(files)
                }
        }
    }

    companion object {
        private const val TAG = "AudioFileScanner"
        private val AUDIO_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

        /** Collator for Chinese pinyin sorting */
        private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }

        // Helper function to parse MediaStore TRACK field correctly
        fun parseTrackField(value: Int): Pair<Int?, Int?> = parseMediaStoreTrackField(value)

        // Fast projection - only MediaStore columns, no file parsing needed
        private val FAST_PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK
        )

        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "ape", "opus")
        private val YEAR_REGEX = Regex("""\d{4}""")
    }

    /**
     * Get cached audio files (from Room database).
     * This is the primary data source for all audio files.
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()

    /**
     * Check if cache has data.
     */
    suspend fun hasCachedData(): Boolean = libraryCache.hasCache()

    /**
     * Get count of cached files.
     */
    suspend fun getCachedFileCount(): Int = libraryCache.getCachedFileCount()

    /**
     * Get last scan timestamp.
     */
    suspend fun getLastScanTime(): Long? = libraryCache.getLastScanTime()

    /**
     * Unified scan method - handles all scan scenarios.
     *
     * Cover art is not extracted during scanning. It's loaded on-demand
     * via MediaStore URIs and folder cover files.
     *
     * @param directoryPaths Optional list of specific directories to scan. If null/empty, scans all audio files.
     * @param incremental If true, only scans changed files. If false, full scan.
     * @param forceRefresh If true, ignores cache and performs full scan.
     */
    suspend fun scan(
        directoryPaths: List<String>? = null,
        incremental: Boolean = false,
        forceRefresh: Boolean = false
    ): List<AudioFile> {
        val files = when {
            // Specific directories scan
            !directoryPaths.isNullOrEmpty() -> {
                scanDirectories(directoryPaths, incremental, forceRefresh)
            }
            // Global scan
            incremental && hasCachedData() -> {
                scanIncremental()
            }
            else -> {
                scanGlobal(forceRefresh)
            }
        }

        // Update cache with scan results
        libraryCache.updateCache(files)

        return files
    }

    /**
     * Loads audio files - compatibility method for existing code.
     * Automatically determines whether to use incremental or full scan.
     *
     * @param isIncremental If true, only scan changed files; if false, full scan
     */
    suspend fun loadAudioFiles(isIncremental: Boolean = false) {
        scan(
            directoryPaths = emptyList(),
            incremental = isIncremental,
            forceRefresh = false
        )
    }

    /**
     * Scans audio files within specific directories.
     */
    private suspend fun scanDirectories(
        directoryPaths: List<String>,
        incremental: Boolean,
        forceRefresh: Boolean
    ): List<AudioFile> {
        val normalizedDirs = directoryPaths
            .map { it.trimEnd('/', '\\') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedDirs.isEmpty()) return emptyList()

        return if (incremental) {
            scanDirectoriesIncremental(normalizedDirs)
        } else {
            scanDirectoriesFull(normalizedDirs, forceRefresh)
        }
    }

    /**
     * Full scan of specific directories.
     */
    private suspend fun scanDirectoriesFull(
        directoryPaths: List<String>,
        forceRefresh: Boolean
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        directoryPaths.flatMap { dir ->
            scanDirectoryInternal(dir, forceRefresh)
        }.distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    /**
     * Incremental scan of specific directories.
     */
    private suspend fun scanDirectoriesIncremental(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val currentFiles = mutableListOf<Pair<String, Long>>()
        directoryPaths.forEach { dir ->
            collectDirectoryFileModificationTimes(File(dir), currentFiles)
        }

        val currentPaths = currentFiles.map { it.first }.toSet()
        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)

        Timber.i(TAG, "Directory incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val cachedInDirs = cachedFiles.filter { cached ->
            directoryPaths.any { isPathInsideDirectory(cached.path, it) }
        }

        val retainedFiles = cachedInDirs.filter { cached ->
            cached.path !in pathsNeedingRescan && cached.path in currentPaths
        }

        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            scanFilesInParallel(pathsNeedingRescan)
        } else {
            emptyList()
        }

        // Remove deleted files from cache
        val deletedPaths = cachedInDirs.map { it.path }.filter { it !in currentPaths }
        libraryCache.removeFromCache(deletedPaths)
        if (deletedPaths.isNotEmpty()) {
            albumInfoManager.cleanupOrphanedAlbums()
        }

        (retainedFiles + updatedFiles)
            .distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    /**
     * Global full scan of all audio files.
     */
    private suspend fun scanGlobal(forceRefresh: Boolean): List<AudioFile> = withContext(Dispatchers.IO) {
        // Check cache first
        if (!forceRefresh && hasCachedData()) {
            val cachedCount = getCachedFileCount()
            if (cachedCount > 0) {
                Timber.d(TAG, "Using cache: $cachedCount files")
                return@withContext libraryCache.getCachedAudioFilesOnce()
            }
        }

        // Full scan
        val files = mutableListOf<AudioFile>()
        scanAllFilesForCache(files)
        files.sortWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
        files
    }

    /**
     * Global incremental scan.
     */
    private suspend fun scanIncremental(): List<AudioFile> = withContext(Dispatchers.IO) {
        val currentFiles = mutableListOf<Pair<String, Long>>()
        scanAllAudioFilesInternal(currentFiles)

        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)
        Timber.i(TAG, "Incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val retainedFiles = cachedFiles.filter { it.path !in pathsNeedingRescan }

        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            scanFilesInParallel(pathsNeedingRescan)
        } else {
            emptyList()
        }

        // Cleanup deleted files
        val deletedCount = libraryCache.cleanupDeletedFiles(currentFiles.map { it.first })
        if (deletedCount > 0) {
            albumInfoManager.cleanupOrphanedAlbums()
        }

        (retainedFiles + updatedFiles)
            .distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    /**
     * Internal directory scan using MediaStore.
     */
    private suspend fun scanDirectoryInternal(
        directoryPath: String,
        forceRefresh: Boolean
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()
        val normalizedDir = directoryPath.trimEnd('/', '\\')

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND (")
            append("${MediaStore.Audio.Media.DATA} = ?")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE ?")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE ?")
            append(")")
        }
        val selectionArgs = arrayOf(
            normalizedDir,
            "$normalizedDir/%",
            "$normalizedDir\\%"
        )

        contentResolver.query(
            AUDIO_URI, FAST_PROJECTION, selection, selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            cursorToAudioFiles(cursor, audioFiles, minDurationEnabled, minDurationMs)
        }

        // Fallback for files not yet indexed
        if (audioFiles.isEmpty()) {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectoryRecursive(dir, audioFiles)
            }
        }

        audioFiles.sortWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
        audioFiles
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
            val filePath = cursor.getString(columns.data)
            val extension = filePath.substringAfterLast('.', "")

            if (AudioFormat.fromExtension(extension) == AudioFormat.OTHER) continue

            val duration = cursor.getLong(columns.duration)
            if (duration != 0L && minDurationEnabled && duration < minDurationMs) continue

            val albumId = cursor.getLong(columns.albumId).takeIf { it > 0L }
            val (trackNum, totalTracks) = parseTrackField(cursor.getInt(columns.track))

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
                    name = cursor.getString(columns.name) ?: filePath.substringAfterLast('/'),
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
        val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val bitrate = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
    }

    /**
     * Scan all audio files for caching.
     */
    private suspend fun scanAllFilesForCache(output: MutableList<AudioFile>) {
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI, FAST_PROJECTION, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            cursorToAudioFiles(cursor, output, minDurationEnabled, minDurationMs)
        }

        Timber.d(TAG, "Full scan complete: ${output.size} files found")
    }

    /**
     * Lightweight scan - only paths and modification times.
     */
    private fun scanAllAudioFilesInternal(output: MutableList<Pair<String, Long>>) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            selection, null, null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataCol)
                val extension = filePath.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val file = File(filePath)
                    if (file.exists()) {
                        output.add(filePath to file.lastModified())
                    }
                }
            }
        }
    }

    /**
     * Recursively scan directory for files not in MediaStore.
     */
    private suspend fun scanDirectoryRecursive(directory: File, output: MutableList<AudioFile>) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> scanDirectoryRecursive(file, output)
                file.extension.lowercase() in AUDIO_EXTENSIONS && file.canRead() -> {
                    output.add(createAudioFileFromPath(file.absolutePath))
                }
            }
        }
    }

    /**
     * Collect file modification times from directory.
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
     * Create AudioFile from path by reading file metadata.
     */
    private suspend fun createAudioFileFromPath(filePath: String): AudioFile = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        // OPTIMIZATION: Read metadata + audio info in one TagLib call when possible
        val completeMetadata = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false)
        val fullMetadata = completeMetadata?.metadata ?: com.voxly.domain.model.AudioMetadata()

        // Try MediaStore first for duration
        var duration = 0L
        var bitrate = 0

        contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.BITRATE),
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(filePath), null
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

        AudioFile(
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
     * Scan files in parallel.
     */
    private suspend fun scanFilesInParallel(
        filePaths: List<String>,
        maxConcurrency: Int = 4
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        coroutineScope {
            filePaths.chunked(maxConcurrency * 2).flatMap { batch: List<String> ->
                val deferreds = batch.map { path ->
                    async<AudioFile?> {
                        try {
                            createAudioFileFromPath(path)
                        } catch (e: Exception) {
                            Timber.w(TAG, "Failed to scan: $path", e)
                            null
                        }
                    }
                }
                deferreds.mapNotNull { it.await() }
            }
        }
    }

    /**
     * Updates albums and artists from audio files.
     * Called automatically when cache changes.
     * Applies whitelist/blacklist filtering before aggregation.
     */
    private suspend fun updateAlbumsAndArtistsFromFiles(files: List<AudioFile>) {
        // Load current filter settings
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val whitelistUris = settingsDataStore.selectedDirectoryUris.first()
        val blacklistUris = settingsDataStore.blacklistDirectoryUris.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val filteredFiles = applyFilters(
            files,
            FilterSettings(
                whitelistEnabled = whitelistEnabled,
                blacklistEnabled = blacklistEnabled,
                minDurationEnabled = minDurationEnabled,
                whitelistUris = whitelistUris,
                blacklistUris = blacklistUris,
                minDurationMs = minDurationMs
            )
        )
        updateAlbumsFromFiles(filteredFiles)
        updateArtistsFromFiles(filteredFiles)
    }

    /**
     * Data class to hold filter settings.
     * Prevents multiple I/O operations by collecting settings once.
     */
    private data class FilterSettings(
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val minDurationEnabled: Boolean,
        val whitelistUris: List<String>,
        val blacklistUris: List<String>,
        val minDurationMs: Long
    )

    /**
     * Applies all filters (whitelist, blacklist, min duration) to audio files.
     * @param files List of audio files to filter
     * @param settings Pre-collected filter settings (no I/O within this method)
     * @return Filtered list of audio files
     */
    private fun applyFilters(files: List<AudioFile>, settings: FilterSettings): List<AudioFile> {
        // If no filters are enabled, return all files
        if (!settings.whitelistEnabled && !settings.blacklistEnabled && !settings.minDurationEnabled) {
            return files
        }

        // Pre-compute whitelist and blacklist paths once (avoid repeated computation)
        val whitelistPaths = if (settings.whitelistEnabled && settings.whitelistUris.isNotEmpty()) {
            settings.whitelistUris.map { getPathFromUriString(it) }
        } else null

        val blacklistPaths = if (settings.blacklistEnabled && settings.blacklistUris.isNotEmpty()) {
            settings.blacklistUris.map { getPathFromUriString(it) }
        } else null

        // Optimization: Pre-compute directory prefixes for faster matching
        val whitelistPrefixes = whitelistPaths?.map { it.trimEnd('/', '\\') }
        val blacklistPrefixes = blacklistPaths?.map { it.trimEnd('/', '\\') }

        return files.filter { file ->
            val path = file.path

            // Apply whitelist filter: file must be in one of the whitelist directories
            if (whitelistPrefixes != null) {
                val isInWhitelist = whitelistPrefixes.any { whitelistPath ->
                    path == whitelistPath ||
                    path.startsWith("$whitelistPath/") ||
                    path.startsWith("$whitelistPath\\")
                }
                if (!isInWhitelist) return@filter false
            }

            // Apply blacklist filter: file must NOT be in any blacklist directory
            if (blacklistPrefixes != null) {
                val isBlacklisted = blacklistPrefixes.any { blacklistPath ->
                    path == blacklistPath ||
                    path.startsWith("$blacklistPath/") ||
                    path.startsWith("$blacklistPath\\")
                }
                if (isBlacklisted) return@filter false
            }

            // Apply min duration filter: file duration must be >= minDurationMs
            if (settings.minDurationEnabled && file.duration > 0 && file.duration < settings.minDurationMs) {
                return@filter false
            }

            true
        }
    }

    /**
     * Convert URI string to filesystem path.
     */
    private fun getPathFromUriString(uriString: String): String {
        return runCatching {
            val uri = Uri.parse(uriString)
            getPathFromUri(uri)
        }.getOrElse { uriString }
    }

    /**
     * Derives albums from audio files.
     */
    private suspend fun updateAlbumsFromFiles(files: List<AudioFile>) {
        val albumsMap = files
            .filter { it.metadata.album?.isNotBlank() == true }
            .groupBy { file ->
                AlbumAggregationKey(
                    album = file.metadata.album!!,
                    albumArtist = file.metadata.albumArtist
                        ?.takeIf { it.isNotBlank() }
                        ?: file.metadata.artist.orEmpty()
                )
            }

        // Build albums list and cache data in one pass
        val albumsForCache = mutableMapOf<Pair<String, String?>, List<AudioFile>>()

        val albumsList = albumsMap.map { (key, albumFiles) ->
            // Add to cache map (albumName, albumArtist) -> List<AudioFile>
            albumsForCache[key.album to key.albumArtist.takeIf { it.isNotBlank() }] = albumFiles

            val coverFile = albumFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: albumFiles.firstOrNull()
            AlbumGroup(
                name = key.album,
                artist = key.albumArtist.ifBlank { albumFiles.firstOrNull()?.metadata?.artist },
                files = albumFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        _albums.value = albumsList

        // Update album info cache
        if (albumsForCache.isNotEmpty()) {
            albumInfoManager.updateAlbumInfoBatch(albumsForCache)
        }
    }

    /**
     * Derives artists from audio files.
     */
    private suspend fun updateArtistsFromFiles(files: List<AudioFile>) {
        val isSeparatorEnabled = settingsDataStore.artistSeparatorEnabled.first()
        val customSeparators = settingsDataStore.artistSeparatorsSet.first()

        val artistsMap = mutableMapOf<String, MutableList<AudioFile>>()

        files.filter { it.metadata.artist?.isNotBlank() == true }.forEach { file ->
            val artistField = file.metadata.artist!!

            if (isSeparatorEnabled && customSeparators.isNotEmpty()) {
                splitArtist(artistField, customSeparators).forEach { artistName ->
                    artistsMap.getOrPut(artistName) { mutableListOf() }.add(file)
                }
            } else {
                artistsMap.getOrPut(artistField) { mutableListOf() }.add(file)
            }
        }

        val artistsList = artistsMap.map { (artistName, artistFiles) ->
            val coverFile = artistFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: artistFiles.firstOrNull()
            ArtistGroup(
                name = artistName,
                albums = artistFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = artistFiles.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        _artists.value = artistsList
    }

    /**
     * Split artist string by separators.
     */
    private fun splitArtist(artist: String, separators: Set<String>): List<String> {
        if (artist.isBlank()) return emptyList()
        if (separators.isEmpty()) return listOf(artist)

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        return artist.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Extract year from string.
     */
    private fun extractYearValue(rawYear: String?): String? {
        return rawYear?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { YEAR_REGEX.find(it)?.value }
    }

    /**
     * Check if path is inside directory.
     */
    private fun isPathInsideDirectory(filePath: String, directoryPath: String): Boolean {
        val normalizedFile = filePath.trimEnd('/', '\\')
        val normalizedDir = directoryPath.trimEnd('/', '\\')
        return normalizedFile == normalizedDir ||
            normalizedFile.startsWith("$normalizedDir/") ||
            normalizedFile.startsWith("$normalizedDir\\")
    }

    /**
     * Convert content URI to filesystem path.
     */
    private fun getPathFromUri(uri: Uri): String {
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
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
    }

    /**
     * Loads detailed metadata on-demand.
     */
    suspend fun loadDetailedMetadata(
        filePath: String,
        includeAlbumArt: Boolean = false
    ): com.voxly.domain.model.AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            metadataProcessor.readMetadata(filePath, includeAlbumArt)
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to load detailed metadata: $filePath", e)
            null
        }
    }

    /**
     * Loads audio properties on-demand.
     */
    suspend fun loadAudioProperties(filePath: String): TagLibMetadataProcessor.AudioInfo? =
        withContext(Dispatchers.IO) {
            try {
                metadataProcessor.readAudioInfo(filePath)
            } catch (e: Exception) {
                Timber.w(TAG, "Failed to load audio properties: $filePath", e)
                null
            }
        }

    /**
     * Clear the scan cache.
     */
    suspend fun clearCache() {
        libraryCache.clearCache()
        albumInfoManager.clearAll()
    }

    /**
     * Remove a file from cache.
     */
    suspend fun removeFromCache(filePath: String) = libraryCache.removeFromCache(filePath)

    /**
     * Update cache for a single file.
     */
    suspend fun syncFileToCache(audioFile: AudioFile) = libraryCache.syncFileToCache(audioFile)

    /**
     * Check if a file is accessible.
     */
    suspend fun isFileAccessible(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).let { it.exists() && it.canRead() }
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        // No-op: ApplicationScope is managed at app level
    }
}
