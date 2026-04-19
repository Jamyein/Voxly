package com.voxly.data.local

import android.content.Context
import com.voxly.core.util.SortUtil
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.data.local.scanner.FilterEngine
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.data.local.scanner.FastScanProcessor
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.voxly.data.local.worker.EnrichmentWorker
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.WhitelistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    @Named("ApplicationScope") private val applicationScope: CoroutineScope,
    // New injected components for separation of concerns
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val whitelistRepository: WhitelistRepository,
    private val albumArtistAggregator: AlbumArtistAggregator,
    // Scan strategies
    private val globalScanStrategy: com.voxly.data.local.scanner.GlobalScanStrategy,
    private val incrementalScanStrategy: com.voxly.data.local.scanner.IncrementalScanStrategy,
    private val directoryScanStrategy: com.voxly.data.local.scanner.DirectoryScanStrategy,
    private val fileProcessor: com.voxly.data.local.scanner.FileProcessor
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

    // Delegate albums/artists/filteredFiles to aggregator
    val albums: StateFlow<List<AlbumGroup>> = albumArtistAggregator.albums
    val albumsBySort: StateFlow<Map<AlbumSortOption, List<AlbumGroup>>> = albumArtistAggregator.albumsBySort
    val artists: StateFlow<List<ArtistGroup>> = albumArtistAggregator.artists
    val filteredFiles: StateFlow<List<AudioFile>> = albumArtistAggregator.filteredFiles

    private val scanMutex = Mutex()

    // Raw cached audio files from database
    val cachedAudioFilesFlow: Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()
        .catch { e ->
            Timber.e(e, "Error observing cached audio files")
        }

    /**
     * Get cached audio files (from Room database).
     * This is the primary data source for all audio files.
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()

    /**
     * Check if cache has data.
     * Uses warmup state to skip redundant DB queries if warmup already succeeded.
     */
    suspend fun hasCachedData(): Boolean {
        if (libraryCache.isWarm()) {
            val count = libraryCache.getCachedFileCount()
            Timber.d(TAG, "hasCachedData: warm cache confirmed, $count files")
            return count > 0
        }
        return libraryCache.hasCache()
    }

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
    ): List<AudioFile> = scanMutex.withLock {
        val files = when {
            !directoryPaths.isNullOrEmpty() -> directoryScanStrategy.scanDirectories(directoryPaths, incremental, false)
            incremental && hasCachedData() -> incrementalScanStrategy.scan()
            else -> {
                if (!forceRefresh && hasCachedData()) {
                    val cachedCount = getCachedFileCount()
                    if (cachedCount > 0) {
                        Timber.d(TAG, "Using cache: $cachedCount files")
                        return@withLock libraryCache.getCachedAudioFilesOnce()
                    }
                }
                globalScanStrategy.scan()
            }
        }

        // Update cache with scan results only if we actually scanned new data
        // Skip updateCache when serving from cache to avoid triggering Flow emissions
        val servedFromCache = !incremental && !forceRefresh && hasCachedData()
        if (!servedFromCache) {
            libraryCache.updateCache(files)
        }

        // Bump cache version when serving from cache to trigger AlbumArtistAggregator's flatMapLatest
        if (servedFromCache) {
            libraryCache.bumpCacheVersion()
        }

        // Background backfill for missing year/sampleRate via persistent WorkManager queue
        if (servedFromCache) {
            scheduleMetadataBackfill()
        }

        files
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
     * Schedules background metadata backfill for cached files missing year or sampleRate.
     * Uses a persistent Room queue + WorkManager to avoid OOM from loading cover art.
     * Applies whitelist/blacklist filters before enqueuing.
     */
    private fun scheduleMetadataBackfill() {
        applicationScope.launch {
            try {
                val cached = libraryCache.getCachedAudioFilesOnce()
                val needsEnrichment = cached.filter { audioFile ->
                    audioFile.metadata.year.isNullOrBlank() || audioFile.sampleRate == 0
                }
                if (needsEnrichment.isEmpty()) {
                    Timber.d(TAG, "No files need year/sampleRate backfill")
                    return@launch
                }

                val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
                val whitelistPaths = if (whitelistEnabled) {
                    whitelistRepository.getValidWhitelistPathsOnce()
                } else emptyList()

                val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
                val blacklistPaths = if (blacklistEnabled) {
                    settingsDataStore.blacklistDirectoryUris.first()
                } else emptyList()

                val filtered = filterEngine.applyFilters(
                    needsEnrichment,
                    FilterEngine.FilterSettings(
                        whitelistEnabled = whitelistEnabled && whitelistPaths.isNotEmpty(),
                        blacklistEnabled = blacklistEnabled && blacklistPaths.isNotEmpty(),
                        minDurationEnabled = false,
                        whitelistUris = whitelistPaths,
                        blacklistUris = blacklistPaths,
                        minDurationMs = 0L
                    )
                )

                if (filtered.isEmpty()) {
                    Timber.d(TAG, "No files need backfill after filtering")
                    return@launch
                }

                // Only enqueue files that don't already have a pending job
                val pathsToEnqueue = filtered
                    .map { it.path }
                    .filter { path -> !libraryCache.hasEnrichmentJobForPath(path) }

                if (pathsToEnqueue.isNotEmpty()) {
                    libraryCache.enqueueEnrichmentJobs(pathsToEnqueue)
                    Timber.d(TAG, "Enqueued ${pathsToEnqueue.size} files for metadata backfill")
                }

                // Trigger WorkManager (existing policy keeps only one active worker)
                val workRequest = OneTimeWorkRequestBuilder<EnrichmentWorker>()
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    EnrichmentWorker.workName(),
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                Timber.w(TAG, "scheduleMetadataBackfill failed", e)
            }
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        // No-op: ApplicationScope is managed at app level
    }
}
