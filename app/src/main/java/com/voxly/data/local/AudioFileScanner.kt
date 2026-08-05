package com.voxly.data.local

import android.content.Context
import com.voxly.core.util.SortUtil
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.data.local.scanner.FilterEngine
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.data.local.scanner.FastScanProcessor
import com.voxly.data.local.scanner.ScanFilterProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.voxly.data.local.worker.EnrichmentWorker
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.ArtistListItemState
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    @Named("ApplicationScope") private val applicationScope: CoroutineScope,
    // New injected components for separation of concerns
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val scanFilterProvider: ScanFilterProvider,
    private val albumArtistAggregator: AlbumArtistAggregator,
    private val uiStateDataStore: UiStateDataStore,
    // Scan strategies
    private val globalScanStrategy: com.voxly.data.local.scanner.GlobalScanStrategy,
    private val incrementalScanStrategy: com.voxly.data.local.scanner.IncrementalScanStrategy,
    private val directoryScanStrategy: com.voxly.data.local.scanner.DirectoryScanStrategy,
    private val fileProcessor: com.voxly.data.local.scanner.FileProcessor
) {
    companion object {
        private const val TAG = "AudioFileScanner"

        /** Collator for Chinese pinyin sorting */
        private val chineseCollator = SortUtil.chineseCollator

        fun parseTrackField(value: Int): Pair<Int?, Int?> = 
            com.voxly.domain.model.parseMediaStoreTrackField(value)
    }

    // Delegate albums/artists to aggregator
    val albums: StateFlow<List<AlbumGroup>> = albumArtistAggregator.albums
    val artists: StateFlow<List<ArtistGroup>> = albumArtistAggregator.artists

    /**
     * The single filtered library for display (Files page, Songs, search).
     * Raw cache + live whitelist/blacklist/min-duration settings, maintained by
     * the aggregator. Consumers must read this flow, NOT the raw cache.
     */
    val filteredAllAudios: StateFlow<List<AudioFile>> = albumArtistAggregator.filteredAllAudios

    /** True once the aggregator's initial build finished (cache or empty). */
    val libraryInitialized: StateFlow<Boolean> = albumArtistAggregator.isInitialized

    // ─── Display-ready projections (unified pattern) ─────────────────────
    // Every screen-facing list below is a pure projection of the aggregator's
    // StateFlows, Eagerly started on the APPLICATION scope. They are hot and
    // correct BEFORE the user navigates anywhere, so no screen ever renders an
    // empty initial frame (the pre-unification flash: VMs re-wrapped the same
    // flows with stateIn(WhileSubscribed, emptyList) and were created lazily on
    // first navigation). ViewModels expose these directly without re-wrapping;
    // sorting/UI-pref transforms live next to their data source. The Eagerly
    // collectors also pre-warm UiStateDataStore at app start, so every
    // DataStore-backed combine emits within a frame.

    /**
     * Albums selected by the persisted sort option.
     *
     * Derived from the aggregator's canonical NAME_ASC list on demand: sortKey is
     * precomputed on AlbumGroup at build time, so the active option's sort is
     * sub-ms field compares, executed on the Default dispatcher. There is no
     * cached sort-order snapshot — the aggregator maintains only the identity
     * map + canonical list, so this projection cannot go stale.
     */
    val sortedAlbums: StateFlow<List<AlbumGroup>> = combine(
        albumArtistAggregator.albums,
        uiStateDataStore.albumSortOption
    ) { albums, currentOption ->
        val option = try {
            AlbumSortOption.valueOf(currentOption)
        } catch (e: IllegalArgumentException) {
            AlbumSortOption.NAME_ASC
        }
        when (option) {
            AlbumSortOption.NAME_ASC -> albums
            AlbumSortOption.TRACK_COUNT_DESC -> albums.sortedWith(
                compareByDescending<AlbumGroup> { it.files.size }.thenBy { it.sortKey }
            )
            AlbumSortOption.YEAR_DESC -> albums.sortedWith(
                compareByDescending<AlbumGroup> { it.year ?: Int.MIN_VALUE }.thenBy { it.sortKey }
            )
        }
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /** All audios sorted by the persisted file-browser sort option. */
    val sortedAllAudios: StateFlow<List<AudioFile>> = combine(
        filteredAllAudios,
        uiStateDataStore.fileBrowserSortOption.map { toFileSortOption(it) }
    ) { audios, sortOption ->
        sortAudioFiles(audios, sortOption)
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * Artist list items for the Artists screen (grouped by display name with
     * per-artist cover/album/track stats). Moved from ArtistViewModel so the
     * mapping runs once at app level instead of on every tab re-entry.
     */
    val artistListItems: StateFlow<List<ArtistListItemState>> = albumArtistAggregator.artists
        .map { artistGroups ->
            artistGroups
                .groupBy { it.name }
                .map { (name, groups) ->
                    val first = groups.first()
                    val albumNames = groups.flatMap { it.files }
                        .mapNotNull { it.metadata.album }
                        .filter { it.isNotBlank() }
                        .toSet()
                    val coverFile = groups.flatMap { it.files }
                        .firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ArtistListItemState(
                        name = name,
                        coverPath = first.coverPath,
                        coverAlbumId = coverFile?.mediaStoreAlbumId,
                        albumCount = albumNames.size,
                        trackCount = groups.sumOf { it.files.size }
                    )
                }
                .sortedBy { SortUtil.toSortablePinyin(it.name) }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private fun sortAudioFiles(files: List<AudioFile>, sortOption: FileSortOption): List<AudioFile> {
        return when (sortOption) {
            FileSortOption.NAME_ASC -> files.sortedBy {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            FileSortOption.NAME_DESC -> files.sortedByDescending {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            FileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            FileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
        }
    }

    private val scanMutex = Mutex()

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
        // Materialized hot cache (set by any cache read, e.g. the aggregator's
        // kickOffInitialBuild) is authoritative. Relying only on wasWarmedUp is
        // racy on cold start: warmUp() may not have finished when loadAudioFiles
        // runs, even though the cache was already loaded into memory.
        if (libraryCache.isWarm() || libraryCache.hasHotCache()) return true
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
        val servedFromCache = hasCachedData() && !incremental && !forceRefresh

        val files = when {
            !directoryPaths.isNullOrEmpty() -> directoryScanStrategy.scanDirectories(directoryPaths, incremental, forceRefresh)
            incremental && hasCachedData() -> {
                // Progressive scan path: intermediate batches are written to
                // cache as they arrive, so the UI sees results in stages.
                incrementalScanStrategy.scan { batch ->
                    libraryCache.updateCache(batch)
                    libraryCache.bumpCacheVersion()
                }
            }
            else -> {
                if (servedFromCache) {
                    val cachedCount = getCachedFileCount()
                    if (cachedCount > 0) {
                        Timber.tag("Voxly").i("Using cache: $cachedCount files")
                        return@withLock libraryCache.getCachedAudioFilesOnce()
                    }
                }
                globalScanStrategy.scan()
            }
        }

        // For non-incremental paths (global / directory): single cache write.
        // For incremental: already written progressively in the callback above.
        if (!servedFromCache && !incremental) {
            libraryCache.updateCache(files)
        }

        if (servedFromCache) {
            libraryCache.bumpCacheVersion()
            scheduleMetadataBackfill()
        }

        Timber.tag("Voxly").i("AudioFileScanner scan completed: fileCount=${files.size} incremental=$incremental")
        files
    }

    /**
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): android.net.Uri {
        return mediaStoreDataSource.getAlbumArtUri(albumId)
    }

    /**
     * Queries MediaStore for the correct album ID of a file.
     * This re-queries MediaStore because album ID can change when album/artist metadata changes.
     */
    suspend fun queryMediaStoreAlbumId(filePath: String): Long? {
        return mediaStoreDataSource.queryMediaStoreAlbumId(filePath)
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
                // SQL-side predicate: only paths missing year/sampleRate/album come
                // back, instead of loading the whole library into memory and
                // filtering in Kotlin.
                val missingPaths = libraryCache.getPathsMissingMetadata()
                if (missingPaths.isEmpty()) {
                    Timber.d(TAG, "No files need year/sampleRate/album backfill")
                    return@launch
                }

                // Materialize only the candidate rows (bounded by the SQL predicate).
                val needsEnrichment = libraryCache.getAudioFilesByPaths(missingPaths)
                val filtered = filterEngine.applyFilters(
                    needsEnrichment,
                    scanFilterProvider.current()
                )

                if (filtered.isEmpty()) {
                    Timber.d(TAG, "No files need backfill after filtering")
                    return@launch
                }

                // enqueueEnrichmentJobs uses INSERT ... ON CONFLICT IGNORE, so
                // re-enqueueing a path that already has a pending job is a no-op
                // — no per-path EXISTS check needed.
                val pathsToEnqueue = filtered.map { it.path }
                libraryCache.enqueueEnrichmentJobs(pathsToEnqueue)
                Timber.d(TAG, "Enqueued ${pathsToEnqueue.size} files for metadata backfill")

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

/**
 * Shared parse helper: persisted FileSortOption string -> enum, defaulting to
 * NAME_ASC on unknown values (forward-compatible with older stored prefs).
 */
fun toFileSortOption(value: String): FileSortOption {
    return try {
        FileSortOption.valueOf(value)
    } catch (_: IllegalArgumentException) {
        FileSortOption.NAME_ASC
    }
}
