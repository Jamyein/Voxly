package com.voxly.data.local

import android.content.Context
import com.voxly.core.util.SortUtil
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.data.local.scanner.FilterEngine
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.data.local.scanner.FastScanProcessor
import com.voxly.data.local.scanner.DeepEnrichProcessor
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
import kotlinx.coroutines.sync.Semaphore
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
    // Two-pass scanning processors
    private val fastScanProcessor: FastScanProcessor,
    private val deepEnrichProcessor: DeepEnrichProcessor,
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
            !directoryPaths.isNullOrEmpty() -> directoryScanStrategy.scanDirectories(directoryPaths, incremental, false)
            incremental && hasCachedData() -> incrementalScanStrategy.scan()
            else -> {
                if (!forceRefresh && hasCachedData()) {
                    val cachedCount = getCachedFileCount()
                    if (cachedCount > 0) {
                        Timber.d(TAG, "Using cache: $cachedCount files")
                        return libraryCache.getCachedAudioFilesOnce()
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

        // Two-pass: Background enrichment for cover art pre-caching + year from TagLib
        if (!servedFromCache) {
            enrichCoversInBackground(files)
        } else {
            // servedFromCache: check for files with missing year or sampleRate and backfill asynchronously
            backfillMissingMetadataInBackground()
        }

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
     * Background enrichment for cover art pre-caching.
     * Uses DeepEnrichProcessor to extract and cache cover art + year from file tags.
     * Results are written back to cache so AlbumArtistAggregator picks up updates.
     */
    private fun enrichCoversInBackground(files: List<AudioFile>) {
        if (files.isEmpty()) return

        applicationScope.launch {
            try {
                Timber.d(TAG, "Starting background cover enrichment for ${files.size} files")
                val enriched = deepEnrichProcessor.enrichBatch(files)
                // Write enriched data (year, sampleRate, etc. from TagLib) back to cache
                libraryCache.updateCache(enriched)
                Timber.d(TAG, "Background cover enrichment completed and written to cache")
            } catch (e: Exception) {
                Timber.w(TAG, "Background cover enrichment failed", e)
            }
        }
    }

    /**
     * Checks cached files for missing year or sampleRate and triggers
     * asynchronous enrichment to backfill from TagLib. Skips files that
     * already have valid data to minimize unnecessary I/O.
     */
    private fun backfillMissingMetadataInBackground() {
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
                val filtered = if (whitelistEnabled && whitelistPaths.isNotEmpty()) {
                    filterEngine.applyFilters(
                        needsEnrichment,
                        FilterEngine.FilterSettings(
                            whitelistEnabled = true,
                            blacklistEnabled = false,
                            minDurationEnabled = false,
                            whitelistUris = whitelistPaths,
                            blacklistUris = emptyList(),
                            minDurationMs = 0L
                        )
                    )
                } else needsEnrichment
                if (filtered.isEmpty()) {
                    Timber.d(TAG, "No files need year/sampleRate backfill after whitelist filtering")
                    return@launch
                }
                Timber.d(TAG, "Backfilling year/sampleRate for ${filtered.size} cached files (whitelistEnabled=$whitelistEnabled)")
                val enriched = deepEnrichProcessor.enrichBatch(filtered, includeAlbumArt = false)
                libraryCache.updateCache(enriched)
                Timber.d(TAG, "Year/sampleRate backfill completed for ${enriched.size} files")
            } catch (e: Exception) {
                Timber.w(TAG, "Year/sampleRate backfill failed", e)
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
