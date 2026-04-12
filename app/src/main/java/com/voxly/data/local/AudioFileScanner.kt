package com.voxly.data.local

import android.content.Context
import com.voxly.core.util.SortUtil
import com.voxly.data.local.cache.AlbumInfoManager
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.data.local.scanner.FilterEngine
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val albumInfoManager: AlbumInfoManager,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope,
    // New injected components for separation of concerns
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val albumArtistAggregator: AlbumArtistAggregator
) {
    companion object {
        private const val TAG = "AudioFileScanner"

        /** Collator for Chinese pinyin sorting */
        private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }

        fun parseTrackField(value: Int): Pair<Int?, Int?> = 
            com.voxly.domain.model.parseMediaStoreTrackField(value)
    }

    // Delegate albums/artists to aggregator
    val albums: StateFlow<List<AlbumGroup>> = albumArtistAggregator.albums
    val artists: StateFlow<List<ArtistGroup>> = albumArtistAggregator.artists

    // Raw cached audio files from database
    val cachedAudioFilesFlow: Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()
        .catch { e ->
            Timber.e(e, "Error observing cached audio files")
        }

    // Filtered audio files - applies all filters and reacts to settings changes
    @Suppress("UNCHECKED_CAST")
    val filteredAudioFiles: Flow<List<AudioFile>> = combine(
        cachedAudioFilesFlow,
        libraryCache.cacheVersionFlow,
        settingsDataStore.whitelistEnabled,
        settingsDataStore.blacklistEnabled,
        settingsDataStore.minDurationFilterEnabled,
        settingsDataStore.selectedDirectoryUris,
        settingsDataStore.blacklistDirectoryUris,
        settingsDataStore.minDurationFilterThresholdMs
    ) { arrays ->
        val files = arrays[0] as List<AudioFile>
        val cacheVersion = arrays[1] as Long
        val whitelistEnabled = arrays[2] as Boolean
        val blacklistEnabled = arrays[3] as Boolean
        val minDurationEnabled = arrays[4] as Boolean
        val whitelistUris = arrays[5] as List<String>
        val blacklistUris = arrays[6] as List<String>
        val minDurationMs = (arrays[7] as Int).toLong()

        val settings = FilterEngine.FilterSettings(
            whitelistEnabled = whitelistEnabled,
            blacklistEnabled = blacklistEnabled,
            minDurationEnabled = minDurationEnabled,
            whitelistUris = whitelistUris,
            blacklistUris = blacklistUris,
            minDurationMs = minDurationMs
        )

        val filtered = filterEngine.applyFilters(files, settings)
        val result = FilteredResult(
            version = filterEngine.computeFilterVersion(cacheVersion, settings),
            files = filtered
        )
        Timber.d("$TAG: combine.emit: version=${result.version}, files=${result.files.size}, cacheVersion=$cacheVersion")
        result
    }
        .conflate()
        .distinctUntilChangedBy { it.version }
        .onEach { result: FilteredResult -> Timber.d("$TAG: filteredAudioFiles.emit: version=${result.version}, files=${result.files.size}") }
        .map { it.files }
        .flowOn(Dispatchers.Default)
        .catch { e ->
            Timber.e(e, "Error observing filtered audio files")
            emit(emptyList())
        }

    init {
        // Auto-update albums and artists when filtered data changes
        applicationScope.launch(Dispatchers.Default) {
            filteredAudioFiles
                .conflate()
                .collectLatest { files ->
                    if (coroutineContext.isActive) {
                        kotlinx.coroutines.delay(50) // Debounce: wait 50ms to batch rapid updates
                    }
                    if (coroutineContext.isActive) {
                        Timber.d("$TAG: updateAlbumsAndArtistsFromFiles with ${files.size} files")
                        albumArtistAggregator.updateAlbumsAndArtistsFromFiles(files, filterEngine)
                    }
                }
        }
    }

    private data class FilteredResult(
        val version: Long,
        val files: List<AudioFile>
    )

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
            val dirFile = File(dir)
            currentFiles.addAll(mediaStoreDataSource.queryDirectoryFilePathsAndModificationTimes(dirFile))
        }

        val currentPaths = currentFiles.map { it.first }.toSet()
        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)

        Timber.i(TAG, "Directory incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val cachedInDirs = cachedFiles.filter { cached ->
            directoryPaths.any { mediaStoreDataSource.isPathInsideDirectory(cached.path, it) }
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
        val currentFiles = mediaStoreDataSource.queryFilePathsAndModificationTimes()

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
        val normalizedDir = directoryPath.trimEnd('/', '\\')

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val relativeDir = mediaStoreDataSource.getRelativePathFromAbsolute(normalizedDir)
        val audioFiles = if (relativeDir != null) {
            mediaStoreDataSource.queryFromDirectory(relativeDir, minDurationEnabled, minDurationMs)
        } else {
            emptyList()
        }

        // Fallback for files not yet indexed
        if (audioFiles.isEmpty()) {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                mediaStoreDataSource.scanDirectoryRecursive(dir)
            } else {
                emptyList()
            }
        } else {
            audioFiles
        }
    }

    /**
     * Scan all audio files for caching.
     */
    private suspend fun scanAllFilesForCache(output: MutableList<AudioFile>) {
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        output.addAll(mediaStoreDataSource.queryAll(minDurationEnabled, minDurationMs))
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
        val (duration, bitrate) = mediaStoreDataSource.queryFileDurationAndBitrate(filePath)

        // Fallback to TagLib audio info if not provided by complete metadata
        val audioInfo = completeMetadata?.audioInfo ?: metadataProcessor.readAudioInfo(filePath)
        val finalDuration = if (duration == 0L) audioInfo?.durationMs ?: 0L else duration
        val finalBitrate = if (bitrate == 0) (audioInfo?.bitrate ?: 0) / 1000 else bitrate

        AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = finalDuration,
            format = extension.uppercase(),
            bitrate = finalBitrate,
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
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): android.net.Uri {
        return mediaStoreDataSource.getAlbumArtUri(albumId)
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
