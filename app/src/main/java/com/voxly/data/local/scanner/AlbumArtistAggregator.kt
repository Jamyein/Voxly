package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cache.AggregateSnapshotEntity
import com.voxly.data.local.cache.AlbumSnapshotDto
import com.voxly.data.local.cache.ArtistSnapshotDto
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.CacheChange
import com.voxly.domain.model.CacheChangeKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Aggregates audio files into albums and artists.
 * Listens to cacheVersionFlow to trigger re-aggregation only when cache version changes,
 * avoiding unnecessary recomputation on every Flow emission.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class AlbumArtistAggregator @Inject constructor(
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val whitelistRepository: com.voxly.domain.repository.WhitelistRepository,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    private data class AggregationConfig(
        val separatorEnabled: Boolean,
        val separators: Set<String>
    )

    private data class FilterConfig(
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val minDurationEnabled: Boolean,
        val minDurationMs: Long
    )

    private val _albumsMap = MutableStateFlow<Map<String, AlbumGroup>>(emptyMap())
    private val _artistsMap = MutableStateFlow<Map<String, ArtistGroup>>(emptyMap())

    // Reverse index: filePath -> albumKey for O(1) lookup when metadata changes
    // ConcurrentHashMap because buildAlbumsFromFiles may run on a different coroutine
    // than incrementalUpdateFile (e.g. via direct init rebuild vs changeFlow collect).
    private val _fileAlbumMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val baseFilterConfig = combine(
        settingsDataStore.whitelistEnabled,
        settingsDataStore.blacklistEnabled,
        settingsDataStore.minDurationFilterEnabled,
        settingsDataStore.minDurationFilterThresholdMs
    ) { whitelistEnabled, blacklistEnabled, minDurationEnabled, minDurationMs ->
        FilterConfig(
            whitelistEnabled = whitelistEnabled,
            blacklistEnabled = blacklistEnabled,
            minDurationEnabled = minDurationEnabled,
            minDurationMs = minDurationMs.toLong()
        )
    }

    /**
     * Live whitelist/blacklist/min-duration settings — the single read-stage
     * authority for what the library displays. Consumed by [filteredAllAudios].
     */
    val filterSettings: StateFlow<FilterEngine.FilterSettings> = combine(
        baseFilterConfig,
        whitelistRepository.getValidWhitelistPaths().distinctUntilChanged(),
        whitelistRepository.getValidBlacklistPaths().distinctUntilChanged()
    ) { baseConfig, whitelistPaths, blacklistPaths ->
        FilterEngine.FilterSettings(
            whitelistEnabled = baseConfig.whitelistEnabled && whitelistPaths.isNotEmpty(),
            blacklistEnabled = baseConfig.blacklistEnabled && blacklistPaths.isNotEmpty(),
            minDurationEnabled = baseConfig.minDurationEnabled,
            whitelistPaths = whitelistPaths,
            blacklistPaths = blacklistPaths,
            minDurationMs = baseConfig.minDurationMs
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = FilterEngine.FilterSettings(
            whitelistEnabled = false,
            blacklistEnabled = false,
            minDurationEnabled = false,
            whitelistPaths = emptyList(),
            blacklistPaths = emptyList(),
            minDurationMs = 0L
        )
    )

    /**
     * The single filtered library: every audio file in the raw Room cache,
     * filtered by the current whitelist/blacklist/min-duration settings.
     * Files page, Songs, search, and album/artist aggregation all consume this
     * one flow, so filter toggles take effect instantly and everywhere.
     */
    val filteredAllAudios: StateFlow<List<AudioFile>> = combine(
        libraryCache.getCachedAudioFiles(),
        filterSettings
    ) { files, settings ->
        filterEngine.applyFilters(files, settings)
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val aggregationConfig = combine(
        settingsDataStore.artistSeparatorEnabled,
        settingsDataStore.artistSeparatorsSet
    ) { separatorEnabled, separators ->
        AggregationConfig(
            separatorEnabled = separatorEnabled,
            separators = separators
        )
    }.distinctUntilChanged()

    companion object {
        private const val TAG = "AlbumArtistAggregator"
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val SPLIT_REGEX_CACHE = java.util.concurrent.ConcurrentHashMap<String, Regex>()

        /**
         * Cached artistId → display-name map. Artist names come from MediaStore
         * and only change on rescan, so the cache is safe across the aggregator's
         * singleton lifetime. Populated during full rebuilds, queried by
         * [resolveArtistDisplayName] on single-file events.
         */
        private val artistNameCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

        /**
         * Threshold ratio of changed files to total cached files above which we
         * abandon incremental rebuild and fall back to a single full rebuild.
         * Tuned for typical libraries: incremental per-album rebuild cost is
         * O(K log K) per affected album, so when most of the library changes,
         * a single full pass wins.
         */
        private const val LARGE_DIFF_RATIO = 0.5
    }

    /**
     * Serializes the aggregate snapshot. Compact DTOs (keys + display fields +
     * sorted path lists) — AudioFile payloads stay in the cache. Used by
     * [persistSnapshot] / [hydrateFromSnapshot].
     */
    private val snapshotJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val snapshotDirty = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Fingerprint of the last state the aggregates were built/hydrated for.
     * Settings collectors compare against it to skip redundant rebuilds (e.g.
     * the stateIn placeholder-to-real transition on cold start, which fires
     * even when the stored settings are unchanged). Volatile: written by
     * [persistSnapshot] / [kickOffInitialBuild], read by the collectors.
     */
    @Volatile
    private var lastSnapshotFingerprint: String? = null

    /**
     * Serializes [aggregatorMutex] work so concurrent batch emissions do not
     * race on `_albumsMap` / `_artistsMap` / reverse indexes.
     * Single-file events (FileUpdated / FileDeleted) are NOT serialized here
     * because they only touch a single path and use ConcurrentHashMap for
     * the reverse index — worst case is a stale-by-one entry which the next
     * batch will reconcile.
     */
    private val aggregatorMutex = Mutex()

    /** Set true once the initial aggregate build has finished (cache or empty). */
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // No runBlocking on the main thread here -- the initial load is driven by
        // an explicit kick-off below. The changeFlow is a SharedFlow (no initial
        // value), so the collector below would never see a FullRefresh event on
        // app startup. We do the initial build directly here, asynchronously on
        // applicationScope. This keeps Hilt singleton construction cheap and
        // avoids ANR risk on large libraries.
        applicationScope.launch(Dispatchers.Default) {
            kickOffInitialBuild()
        }

        applicationScope.launch(Dispatchers.Default) {
            libraryCache.changeFlow.collect { change ->
                when (change) {
                    is CacheChange.FullRefresh -> {
                        Timber.d(TAG, "FullRefresh received, re-building aggregates")
                        val config = aggregationConfig.first()
                        buildAggregatesFromFiles(freshFilteredLibrary(), config)
                    }
                    is CacheChange.FileUpdated -> {
                        Timber.d(TAG, "FileUpdated: ${change.filePath}, albumKey=${change.albumKey}")
                        incrementalUpdateFile(change.filePath, change.albumKey)
                    }
                    is CacheChange.FileDeleted -> {
                        Timber.d(TAG, "FileDeleted: ${change.filePath}, albumKey=${change.albumKey}")
                        removeFileFromAggregates(change.filePath, change.albumKey)
                    }
                    is CacheChange.FilesBatchUpdated -> {
                        val totalCached = libraryCache.getCachedFileCount()
                        val changedSize = change.filePaths.size
                        val isLargeDiff = totalCached > 0 &&
                            changedSize.toDouble() / totalCached > LARGE_DIFF_RATIO
                        val keysMissing = change.albumKeys.isEmpty()
                        if (isLargeDiff || keysMissing) {
                            Timber.d(TAG, "FilesBatchUpdated: full rebuild (changed=$changedSize, total=$totalCached, isLargeDiff=$isLargeDiff, keysMissing=$keysMissing)")
                            val config = aggregationConfig.first()
                            buildAggregatesFromFiles(freshFilteredLibrary(), config)
                        } else {
                            // Read the fresh cache directly: filteredAllAudios.value
                            // is almost certainly the PRE-write list when this event
                            // fires (Room flow emission + combine are async, while
                            // FilesBatchUpdated is emitted synchronously after the
                            // DB write). A stale list would (a) never contain the new
                            // files → new albums/artists never appear, and (b) empty
                            // albumsByKey for every touched key → applyFilteredToAlbum
                            // removes existing albums. Lesson #24.
                            val filtered = freshFilteredLibrary()
                            val config = aggregationConfig.first()

                            // Group the changed files by album key ONCE (O(N)) instead
                            // of re-filtering the whole list per key (O(K·N)).
                            val affectedFiles = change.filePaths.toSet()
                            val changedFiles = filtered.filter { it.path in affectedFiles }
                            val albumsByKey = changedFiles.groupBy { CacheChangeKeys.extractAlbumKey(it) }
                            // Derive affected artist keys from the changed files with the
                            // same name-based key logic as the full build (the event's
                            // artistKeys use extractArtistKey, which never matches the
                            // split-name groups).
                            val artistsByKey = changedFiles
                                .flatMap { file ->
                                    CacheChangeKeys.extractArtistKeysWithSeparators(
                                        file,
                                        if (config.separatorEnabled) config.separators else emptySet()
                                    ).map { key -> key to file }
                                }
                                .groupBy({ it.first }, { it.second })

                            // Resolve display names for id-backed artists in one batch
                            // MediaStore query instead of one round-trip per artist.
                            val artistDisplayNames = if (config.separatorEnabled) {
                                emptyMap()
                            } else {
                                val artistIds = artistsByKey.keys.mapNotNull { key ->
                                    artistsByKey[key]?.lastOrNull()?.mediaStoreArtistId?.takeIf { it > 0 }
                                }.toSet()
                                if (artistIds.isEmpty()) emptyMap()
                                else mediaStoreDataSource.queryArtistNames(artistIds.toList())
                            }
                            artistNameCache.putAll(artistDisplayNames)

                            Timber.d(TAG, "FilesBatchUpdated: incremental rebuild (albums=${albumsByKey.size}, artists=${artistsByKey.size}, files=$changedSize)")
                            aggregatorMutex.withLock {
                                change.albumKeys.forEach { albumKey ->
                                    applyFilteredToAlbum(albumKey, albumsByKey[albumKey].orEmpty())
                                }
                                artistsByKey.forEach { (artistKey, files) ->
                                    val id = files.lastOrNull()?.mediaStoreArtistId?.takeIf { it > 0 }
                                    applyFilteredToArtist(
                                        artistKey,
                                        files,
                                        config,
                                        displayName = artistDisplayNames[id] ?: artistKey
                                    )
                                }
                                emitUpdatedLists()
                            }
                        }
                    }
                }
            }
        }

        // Filter toggles (whitelist/blacklist/min-duration, including the
        // threshold value) change the filtered library WITHOUT touching the
        // cache, so no cache event fires. Rebuild from a fresh read on every
        // settings change. drop(1) skips the initial emission, which
        // kickOffInitialBuild already handles. freshFilteredLibrary() (not
        // filteredAllAudios.value) because the combine may not have re-emitted
        // yet when this collector runs — a stale list would rebuild albums from
        // the pre-toggle filter. Lesson #24.
        applicationScope.launch(Dispatchers.Default) {
            filterSettings.drop(1).debounce(300).collect { newSettings ->
                // Skip when the aggregates already match these settings: the
                // stateIn fires its placeholder -> real transition once per cold
                // start even when nothing changed (the snapshot hydration or
                // the initial rebuild already published the correct result).
                val fingerprint = runCatching {
                    buildFingerprint(newSettings, aggregationConfig.first())
                }.getOrNull()
                if (fingerprint != null && fingerprint == lastSnapshotFingerprint) {
                    return@collect
                }
                Timber.d(TAG, "Filter settings changed, re-building aggregates")
                val config = aggregationConfig.first()
                buildAggregatesFromFiles(freshFilteredLibrary(), config)
            }
        }

        // Artist separator toggles/values change the artist grouping WITHOUT
        // touching the cache, so no cache event fires. Rebuild artists from the
        // shared filtered flow on every separator change. drop(1) skips the
        // initial emission, which kickOffInitialBuild already handles.
        applicationScope.launch(Dispatchers.Default) {
            aggregationConfig.drop(1).debounce(300).collect { config ->
                val fingerprint = runCatching {
                    buildFingerprint(currentFilterSettings(), config)
                }.getOrNull()
                if (fingerprint != null && fingerprint == lastSnapshotFingerprint) {
                    return@collect
                }
                Timber.d(TAG, "Artist separator settings changed, re-building artists")
                buildArtistsFromFiles(
                    freshFilteredLibrary(),
                    config.separatorEnabled,
                    config.separators
                )
            }
        }

        // Persist the aggregate snapshot after every mutation. Debounced so a
        // progressive-scan batch storm (one emitUpdatedLists per batch) writes
        // once after the dust settles. The fingerprint gate keeps hydration
        // safe: a stale or in-flight snapshot never matches the current cache.
        applicationScope.launch(Dispatchers.Default) {
            snapshotDirty.debounce(800).collect {
                persistSnapshot()
            }
        }
    }

    /**
     * The filtered library as of right now, reading the fresh cache directly.
     *
     * `filteredAllAudios.value` can be stale whenever a cache/settings event is
     * processed: the Room flow emission + combine recompute are asynchronous,
     * while cache events are emitted synchronously after the DB write. Reading
     * the hot cache (already merged by [MusicLibraryCache.updateCache] before
     * the event fires) + current filter settings is deterministic. Lesson #24.
     */
    private suspend fun freshFilteredLibrary(): List<AudioFile> {
        val raw = libraryCache.getCachedAudioFilesOnce()
        return filterEngine.applyFilters(raw, filterSettings.value)
    }

    // ==================== Aggregate snapshot (persisted cold-start) ====================

    /**
     * Current filter settings read deterministically from the raw DataStore
     * flows (first() waits for the stored value). Mirrors the [filterSettings]
     * combine exactly, so fingerprints and rebuilds always agree with what the
     * stateIn flow will publish.
     */
    private suspend fun currentFilterSettings(): FilterEngine.FilterSettings {
        val whitelistPaths = whitelistRepository.getValidWhitelistPaths().first()
        val blacklistPaths = whitelistRepository.getValidBlacklistPaths().first()
        return FilterEngine.FilterSettings(
            whitelistEnabled = settingsDataStore.whitelistEnabled.first() && whitelistPaths.isNotEmpty(),
            blacklistEnabled = settingsDataStore.blacklistEnabled.first() && blacklistPaths.isNotEmpty(),
            minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first(),
            whitelistPaths = whitelistPaths,
            blacklistPaths = blacklistPaths,
            minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()
        )
    }

    /**
     * Deterministic fingerprint of every aggregate input: cached rows + filter
     * settings + separator config. Aggregation is a pure function of these, so
     * an equal fingerprint at cold start proves the persisted snapshot is
     * still valid; any mismatch (new scan, settings toggle, separator change)
     * falls back to a rebuild. Delimited raw values (no hashing) keep it
     * debuggable.
     */
    private suspend fun buildFingerprint(
        settings: FilterEngine.FilterSettings,
        config: AggregationConfig
    ): String {
        val contentFp = libraryCache.getContentFingerprint()
        return buildString {
            append(contentFp)
            append("|filter:")
            append(settings.whitelistEnabled)
            append(',')
            append(settings.blacklistEnabled)
            append(',')
            append(settings.minDurationEnabled)
            append(',')
            append(settings.minDurationMs)
            append('|')
            append("w:")
            append(settings.whitelistPaths.sorted().joinToString(","))
            append('|')
            append("b:")
            append(settings.blacklistPaths.sorted().joinToString(","))
            append('|')
            append("sep:")
            append(config.separatorEnabled)
            append(',')
            append(config.separators.sorted().joinToString(","))
        }
    }

    /**
     * Serializes the current aggregates to the cache DB. Captures the
     * published (pinyin-sorted) lists so hydrate needs no re-sorting, plus the
     * per-group display fields and sorted file paths. AudioFile payloads stay
     * in the cache.
     */
    private suspend fun persistSnapshot() {
        try {
            val albumsList = _albums.value
            val artistsList = _artists.value
            if (albumsList.isEmpty() && artistsList.isEmpty()) return
            val keyByAlbum = HashMap<AlbumGroup, String>(albumsList.size)
            _albumsMap.value.forEach { (k, v) -> keyByAlbum[v] = k }
            val keyByArtist = HashMap<ArtistGroup, String>(artistsList.size)
            _artistsMap.value.forEach { (k, v) -> keyByArtist[v] = k }

            val albumsJson = snapshotJson.encodeToString(
                albumsList.map { group ->
                    AlbumSnapshotDto(
                        key = keyByAlbum.getValue(group),
                        name = group.name,
                        albumArtist = group.albumArtist,
                        coverPath = group.coverPath,
                        year = group.year,
                        paths = group.files.map { it.path }
                    )
                }
            )
            val artistsJson = snapshotJson.encodeToString(
                artistsList.map { group ->
                    ArtistSnapshotDto(
                        key = keyByArtist.getValue(group),
                        name = group.name,
                        albums = group.albums,
                        coverPath = group.coverPath,
                        paths = group.files.map { it.path }
                    )
                }
            )
            val fingerprint = buildFingerprint(currentFilterSettings(), aggregationConfig.first())
            libraryCache.saveAggregateSnapshot(fingerprint, albumsJson, artistsJson)
            lastSnapshotFingerprint = fingerprint
            Timber.d(TAG, "persistSnapshot: ${albumsList.size} albums, ${artistsList.size} artists")
        } catch (e: Exception) {
            Timber.w(TAG, "persistSnapshot failed", e)
        }
    }

    /**
     * Rebuilds the in-memory maps from a persisted snapshot. Every path in the
     * snapshot must resolve against the current hot cache; a missing path means
     * the cache changed under the fingerprint (or serialization drifted) and
     * the caller must rebuild instead. Returns false on any inconsistency.
     */
    private suspend fun hydrateFromSnapshot(
        snapshot: AggregateSnapshotEntity,
        cachedFiles: List<AudioFile>
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            val albumsDto = snapshotJson.decodeFromString<List<AlbumSnapshotDto>>(snapshot.albumsJson)
            val artistsDto = snapshotJson.decodeFromString<List<ArtistSnapshotDto>>(snapshot.artistsJson)
            val pathToFile = cachedFiles.associateBy { it.path }

            val albumsMap = LinkedHashMap<String, AlbumGroup>(albumsDto.size)
            val albumsList = ArrayList<AlbumGroup>(albumsDto.size)
            for (dto in albumsDto) {
                val files = dto.paths.mapNotNull { pathToFile[it] }
                if (files.size != dto.paths.size) return@withContext false
                val group = AlbumGroup(
                    name = dto.name,
                    albumArtist = dto.albumArtist,
                    files = files.toImmutableList(),
                    sortKey = SortUtil.toSortablePinyin(dto.name),
                    coverPath = dto.coverPath,
                    year = dto.year
                )
                albumsMap[dto.key] = group
                albumsList.add(group)
            }

            val artistsMap = LinkedHashMap<String, ArtistGroup>(artistsDto.size)
            val artistsList = ArrayList<ArtistGroup>(artistsDto.size)
            for (dto in artistsDto) {
                val files = dto.paths.mapNotNull { pathToFile[it] }
                if (files.size != dto.paths.size) return@withContext false
                val group = ArtistGroup(
                    name = dto.name,
                    albums = dto.albums.toImmutableList(),
                    files = files.toImmutableList(),
                    coverPath = dto.coverPath
                )
                artistsMap[dto.key] = group
                artistsList.add(group)
            }

            // Publish WITHOUT re-running the pinyin sorts: the persisted list
            // order IS the pinyin order. The other two sort options derive
            // cheaply in the display projection (track count / year need no
            // transliteration).
            _albumsMap.value = albumsMap
            _albums.value = albumsList
            _artistsMap.value = artistsMap
            _artists.value = artistsList
            _fileAlbumMap.clear()
            albumsMap.forEach { (key, group) -> group.files.forEach { _fileAlbumMap[it.path] = key } }
            true
        } catch (e: Exception) {
            Timber.w(TAG, "hydrateFromSnapshot failed, falling back to rebuild", e)
            false
        }
    }

    /**
     * Initial aggregate build for app startup. Previously this was driven by the
     * `MutableStateFlow(FullRefresh())` initial value of `_changeFlow`; after the
     * migration to `MutableSharedFlow`, no initial value exists, so we trigger
     * the build explicitly. Safe to call repeatedly — if the collector above
     * has already produced aggregates from a `FullRefresh` event, the data is
     * the same and the work is wasted but not incorrect.
     */
    private suspend fun kickOffInitialBuild() {
        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        // Release the splash as soon as the cache is materialized — aggregate
        // building below can complete in the background and fill the UI.
        _isInitialized.value = true
        if (cachedFiles.isEmpty()) {
            Timber.d(TAG, "kickOffInitialBuild: cache empty, waiting for FullRefresh event")
            return
        }
        // Build aggregates directly from the in-memory / Room cache with the
        // current filter settings — no waiting for the combine + Room flow
        // pipeline to emit its first value. The first UI frame sees data.
        // Deterministic settings read (first() waits for DataStore) so the
        // snapshot fingerprint is comparable across processes: `filterSettings.value`
        // may still be the initial placeholder on cold start.
        val settings = currentFilterSettings()
        val config = aggregationConfig.first()
        val fingerprint = buildFingerprint(settings, config)
        val snapshot = libraryCache.loadAggregateSnapshot()
        if (snapshot != null && snapshot.fingerprint == fingerprint) {
            Timber.i(TAG, "kickOffInitialBuild: hydrating from snapshot (${cachedFiles.size} files)")
            if (hydrateFromSnapshot(snapshot, cachedFiles)) {
                lastSnapshotFingerprint = fingerprint
                Timber.i(TAG, "kickOffInitialBuild: hydrated")
                return
            }
            Timber.w(TAG, "kickOffInitialBuild: snapshot hydrate failed, rebuilding")
        }
        val filtered = filterEngine.applyFilters(cachedFiles, settings)
        buildAggregatesFromFiles(filtered, config)
        persistSnapshot()
    }

    private suspend fun incrementalUpdateFile(
        filePath: String,
        albumKey: String?
    ) {
        // The filtered library is authoritative: a single-file update whose
        // path is currently filtered out (blacklisted, too short, outside the
        // whitelist) must not re-enter an album/artist. Read fresh —
        // filteredAllAudios.value may still be the pre-write list right after a
        // scan/sync wrote this file (lesson #24).
        if (freshFilteredLibrary().none { it.path == filePath }) return

        val config = aggregationConfig.first()
        val file = libraryCache.getCachedFile(filePath) ?: return

        val newAlbumKey = CacheChangeKeys.extractAlbumKey(file)
        // Gate on separatorEnabled so a disabled toggle never splits — matches
        // buildArtistsFromFiles (which only splits when separatorEnabled).
        val newArtistKeys = CacheChangeKeys.extractArtistKeysWithSeparators(
            file,
            if (config.separatorEnabled) config.separators else emptySet()
        )

        // Album: reverse index stays (a file belongs to at most one album).
        val oldAlbumKey = _fileAlbumMap[filePath]

        // Remove from old album if key changed
        if (oldAlbumKey != null && oldAlbumKey != newAlbumKey) {
            removeFileFromAlbum(filePath, oldAlbumKey)
        }

        // Add/update in new album
        if (newAlbumKey != null) {
            if (newAlbumKey != oldAlbumKey) {
                addFileToAlbum(file, newAlbumKey)
            } else {
                updateAlbumIncremental(file, newAlbumKey)
            }
        }

        // Artists: no reverse index — reconcile by scanning the current groups
        // for this path, so multi-artist files (split by separators) are always
        // removed/added consistently with a full rebuild. Single-file events are
        // rare, so the O(groups) scan is fine.
        val oldArtistKeys = currentArtistKeysFor(filePath)
        val newArtistKeySet = newArtistKeys.toSet()

        (oldArtistKeys - newArtistKeySet).forEach { removeFileFromArtist(filePath, it) }
        for (key in newArtistKeys) {
            if (key in oldArtistKeys) {
                updateArtistIncremental(file, key, config.separatorEnabled)
            } else {
                addFileToArtist(file, key, config.separatorEnabled)
            }
        }

        emitUpdatedLists()
    }

    private fun addFileToAlbum(file: AudioFile, albumKey: String) {
        val currentMap = _albumsMap.value.toMutableMap()
        val existingAlbum = currentMap[albumKey]

        val newFiles = if (existingAlbum != null) {
            existingAlbum.files.filter { it.path != file.path } + file
        } else {
            listOf(file)
        }

        val albumName = file.metadata.album ?: ""
        val albumArtist = file.metadata.albumArtist
        val coverFile = newFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: newFiles.firstOrNull()
        val albumYear = newFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

        currentMap[albumKey] = AlbumGroup(
            name = albumName,
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            files = newFiles.sortedBy { it.metadata.trackNumber }.toImmutableList(),
            sortKey = SortUtil.toSortablePinyin(albumName),
            coverPath = coverFile?.path,
            year = albumYear
        )
        _albumsMap.value = currentMap
        
        // Update reverse index
        _fileAlbumMap[file.path] = albumKey
    }

    private fun removeFileFromAlbum(filePath: String, albumKey: String) {
        val currentMap = _albumsMap.value.toMutableMap()
        val currentAlbum = currentMap[albumKey] ?: return
        val newFiles = currentAlbum.files.filter { it.path != filePath }

        if (newFiles.isEmpty()) {
            currentMap.remove(albumKey)
        } else {
            val coverFile = newFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                ?: newFiles.firstOrNull()
            val albumYear = newFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

            currentMap[albumKey] = currentAlbum.copy(
                files = newFiles.sortedBy { it.metadata.trackNumber }.toImmutableList(),
                coverPath = coverFile?.path,
                year = albumYear
            )
        }
        _albumsMap.value = currentMap
        
        // Update reverse index
        _fileAlbumMap.remove(filePath)
    }

    private fun removeFileFromArtist(filePath: String, artistKey: String) {
        val currentMap = _artistsMap.value.toMutableMap()
        val currentArtist = currentMap[artistKey] ?: return
        val newFiles = currentArtist.files.filter { it.path != filePath }

        if (newFiles.isEmpty()) {
            currentMap.remove(artistKey)
        } else {
            val sortedForCover = newFiles.sortedWith(
                compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    .thenBy { it.metadata.album }
            )
            val coverFile = sortedForCover.firstOrNull()

            currentMap[artistKey] = currentArtist.copy(
                albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
                files = newFiles.sortedBy { it.metadata.album }.toImmutableList(),
                coverPath = coverFile?.path
            )
        }
        _artistsMap.value = currentMap
    }

    /** Artist group keys that currently contain [filePath]. Scans the live map
     *  instead of a reverse index, so multi-artist membership is always exact. */
    private fun currentArtistKeysFor(filePath: String): Set<String> =
        _artistsMap.value.filterValues { group -> group.files.any { it.path == filePath } }.keys

    private suspend fun addFileToArtist(file: AudioFile, artistKey: String, separatorEnabled: Boolean) {
        val currentMap = _artistsMap.value.toMutableMap()
        val existingArtist = currentMap[artistKey]

        val newFiles = if (existingArtist != null) {
            existingArtist.files.filter { it.path != file.path } + file
        } else {
            listOf(file)
        }

        val sortedForCover = newFiles.sortedWith(
            compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                .thenBy { it.metadata.album }
        )
        val coverFile = sortedForCover.firstOrNull()

        val displayName = resolveArtistDisplayName(artistKey, file, separatorEnabled)
        currentMap[artistKey] = ArtistGroup(
            name = displayName,
            albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
            files = newFiles.sortedBy { it.metadata.album }.toImmutableList(),
            coverPath = coverFile?.path
        )
        _artistsMap.value = currentMap
    }

    private suspend fun updateAlbumIncremental(file: AudioFile, albumKey: String) {
        val currentMap = _albumsMap.value.toMutableMap()
        val currentAlbum = currentMap[albumKey]

        val newAlbumFiles = if (currentAlbum != null) {
            currentAlbum.files.filter { it.path != file.path } + file
        } else {
            listOf(file)
        }

        val albumName = file.metadata.album ?: ""
        val albumArtist = file.metadata.albumArtist
        val coverFile = newAlbumFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: newAlbumFiles.firstOrNull()
        val albumYear = newAlbumFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

        currentMap[albumKey] = AlbumGroup(
            name = albumName,
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            files = newAlbumFiles.sortedBy { it.metadata.trackNumber }.toImmutableList(),
            sortKey = SortUtil.toSortablePinyin(albumName),
            coverPath = coverFile?.path,
            year = albumYear
        )

        _albumsMap.value = currentMap
        
        // Update reverse index
        _fileAlbumMap[file.path] = albumKey
    }

    private suspend fun updateArtistIncremental(
        file: AudioFile,
        artistKey: String,
        separatorEnabled: Boolean
    ) {
        val currentMap = _artistsMap.value.toMutableMap()
        val currentArtist = currentMap[artistKey]
        val newArtistFiles = if (currentArtist != null) {
            currentArtist.files.filter { it.path != file.path } + file
        } else {
            listOf(file)
        }

        val sortedForCover = newArtistFiles.sortedWith(
            compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                .thenBy { it.metadata.album }
        )
        val coverFile = sortedForCover.firstOrNull()

        currentMap[artistKey] = ArtistGroup(
            name = resolveArtistDisplayName(artistKey, file, separatorEnabled),
            albums = newArtistFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
            files = newArtistFiles.sortedBy { it.metadata.album }.toImmutableList(),
            coverPath = coverFile?.path
        )
        _artistsMap.value = currentMap
    }

    private suspend fun removeFileFromAggregates(
        filePath: String,
        albumKey: String?
    ) {
        if (albumKey != null) {
            val currentMap = _albumsMap.value.toMutableMap()
            val currentAlbum = currentMap[albumKey]
            // Album group may be absent (e.g. already rebuilt by a full pass);
            // that must not skip the artist removal below.
            if (currentAlbum != null) {
                val newFiles = currentAlbum.files.filter { it.path != filePath }

                if (newFiles.isEmpty()) {
                    currentMap.remove(albumKey)
                } else {
                    val coverFile = newFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                        ?: newFiles.firstOrNull()
                    val albumYear = newFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

                    currentMap[albumKey] = currentAlbum.copy(
                        files = newFiles.sortedBy { it.metadata.trackNumber }.toImmutableList(),
                        coverPath = coverFile?.path,
                        year = albumYear
                    )
                }

                _albumsMap.value = currentMap
                // Update reverse index
                _fileAlbumMap.remove(filePath)
            }
        }

        // Remove the file from every artist group that currently contains it.
        // The event's artistKey uses extractArtistKey (raw name / id), which
        // never matches the split-name groups — scan the current groups instead.
        currentArtistKeysFor(filePath).forEach { removeFileFromArtist(filePath, it) }

        emitUpdatedLists()
    }

    /**
     * Construct [AlbumGroup] for [albumKey] from a pre-grouped `filesForAlbum`
     * list, then write it to [_albumsMap] and update the reverse index. Caller
     * is responsible for [emitUpdatedLists] when batching across multiple keys.
     */
    private fun applyFilteredToAlbum(albumKey: String, filesForAlbum: List<AudioFile>) {
        val currentMap = _albumsMap.value.toMutableMap()
        if (filesForAlbum.isEmpty()) {
            if (currentMap.remove(albumKey) != null) {
                _albumsMap.value = currentMap
                _fileAlbumMap.entries.removeAll { it.value == albumKey }
            }
            return
        }

        // Derive album identity from the key (mirrors buildAlbumsFromFiles)
        val first = filesForAlbum.first()
        val albumName: String
        val albumArtist: String?
        if (albumKey.startsWith("id:")) {
            albumName = first.metadata.album ?: albumKey.removePrefix("id:")
            albumArtist = first.metadata.albumArtist
        } else {
            val parts = albumKey.removePrefix("str:").split("|")
            albumName = parts.firstOrNull() ?: ""
            albumArtist = first.metadata.albumArtist?.takeIf { it.isNotBlank() }
        }
        val coverFile = filesForAlbum.firstOrNull {
            it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
        } ?: filesForAlbum.firstOrNull()
        val albumYear = filesForAlbum.mapNotNull { extractAlbumYear(it) }.maxOrNull()

        currentMap[albumKey] = AlbumGroup(
            name = albumName,
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            files = filesForAlbum.sortedBy { it.metadata.trackNumber }.toImmutableList(),
            sortKey = SortUtil.toSortablePinyin(albumName),
            coverPath = coverFile?.path,
            year = albumYear
        )
        _albumsMap.value = currentMap

        // Rebuild reverse index entries for this album's files (other albums'
        // entries are untouched)
        filesForAlbum.forEach { f -> _fileAlbumMap[f.path] = albumKey }
    }

    /**
     * Construct [ArtistGroup] for [artistKey] from a pre-grouped `filesForArtist`
     * list, then write it to [_artistsMap]. Mirrors
     * [applyFilteredToAlbum] and uses the same name-based key logic as the full
     * build, so incremental batch artist updates stay consistent with a rebuild.
     * Caller is responsible for [emitUpdatedLists] when batching across keys.
     */
    private suspend fun applyFilteredToArtist(
        artistKey: String,
        filesForArtist: List<AudioFile>,
        config: AggregationConfig,
        displayName: String = artistKey
    ) {
        val currentMap = _artistsMap.value.toMutableMap()
        if (filesForArtist.isEmpty()) {
            if (currentMap.remove(artistKey) != null) {
                _artistsMap.value = currentMap
            }
            return
        }

        val sortedForCover = filesForArtist.sortedWith(
            compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                .thenBy { it.metadata.album }
        )
        val coverFile = sortedForCover.firstOrNull()

        // Display name uses the last file's artist id, matching the full build's
        // artistNameToId "last write wins" semantics.
        currentMap[artistKey] = ArtistGroup(
            name = displayName,
            albums = filesForArtist.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
            files = filesForArtist.sortedBy { it.metadata.album }.toImmutableList(),
            coverPath = coverFile?.path
        )
        _artistsMap.value = currentMap
    }

    private fun emitUpdatedLists() {
        // sortKey is precomputed on each AlbumGroup at build time, so the sort
        // is O(n log n) pure string field compares — no transliteration here.
        val albumsList = _albumsMap.value.values.sortedBy { it.sortKey }
        if (!areAlbumListsEqual(albumsList, _albums.value)) {
            _albums.value = albumsList
        }

        val artistsList = _artistsMap.value.values
            .map { artist -> artist to SortUtil.toSortablePinyin(artist.name) }
            .sortedBy { it.second }
            .map { it.first }
        if (!areArtistListsEqual(artistsList, _artists.value)) {
            _artists.value = artistsList
        }

        // Any mutation funnel marks the snapshot dirty; the debounced saver
        // persists it after the current burst settles.
        snapshotDirty.tryEmit(Unit)
    }

    /** Stable identity key for an album (used to detect add/remove/update). */
    private fun keyFor(album: AlbumGroup): String =
        "${album.albumArtist.orEmpty()}|${album.name}"

    /** Same as [keyFor] but as extension for readability. */
    private fun AlbumGroup.key(): String = keyFor(this)

    /**
     * Builds complete aggregates from audio files.
     * [files] is expected to be the already-filtered library (filteredAllAudios).
     * Called on full refresh to rebuild all maps from scratch.
     */
    private suspend fun buildAggregatesFromFiles(
        files: List<AudioFile>,
        config: AggregationConfig
    ) = withContext(Dispatchers.Default) {
        buildAlbumsFromFiles(files)
        buildArtistsFromFiles(
            files = files,
            separatorEnabled = config.separatorEnabled,
            customSeparators = config.separators
        )
    }

    /**
     * Builds albums map from audio files (full rebuild).
     */
    private suspend fun buildAlbumsFromFiles(files: List<AudioFile>) {
        val primaryGroups = mutableMapOf<String?, MutableList<AudioFile>>()
        val albumIdSet = mutableSetOf<Long>()

        files.forEach { file ->
            val key = CacheChangeKeys.extractAlbumKey(file)
            if (key != null) {
                primaryGroups.getOrPut(key) { mutableListOf() }.add(file)
                val id = file.mediaStoreAlbumId?.takeIf { it > 0 }
                if (id != null) albumIdSet.add(id)
            }
        }

        val albumsMap = primaryGroups.map { (key, albumFiles) ->
            val albumName: String
            val albumArtist: String?

            if (key?.startsWith("id:") == true) {
                albumName = albumFiles.firstOrNull()?.metadata?.album
                    ?: key.removePrefix("id:")
                albumArtist = albumFiles.firstOrNull()?.metadata?.albumArtist
            } else {
                val parts = key?.removePrefix("str:")?.split("|") ?: listOf()
                albumName = parts.firstOrNull() ?: ""
                albumArtist = albumFiles.firstOrNull()?.metadata?.albumArtist?.takeIf { it.isNotBlank() }
            }

            val coverFile = albumFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: albumFiles.firstOrNull()
            val albumYear = albumFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

            key!! to AlbumGroup(
                name = albumName,
                albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                files = albumFiles.sortedBy { it.metadata.trackNumber }.toImmutableList(),
                sortKey = SortUtil.toSortablePinyin(albumName),
                coverPath = coverFile?.path,
                year = albumYear
            )
        }.toMap()

        _albumsMap.value = albumsMap
        
        // Rebuild reverse index for albums
        _fileAlbumMap.clear()
        albumsMap.forEach { (key, album) ->
            album.files.forEach { file ->
                _fileAlbumMap[file.path] = key
            }
        }
        
        emitUpdatedLists()
    }

    /**
     * Builds artists map from audio files (full rebuild).
     */
    private suspend fun buildArtistsFromFiles(
        files: List<AudioFile>,
        separatorEnabled: Boolean,
        customSeparators: Set<String>
    ) {
        val primaryGroups = mutableMapOf<String?, MutableList<AudioFile>>()
        val artistIdSet = mutableSetOf<Long>()

        files.forEach { file ->
            val key = CacheChangeKeys.extractArtistKey(file)
            if (key != null) {
                primaryGroups.getOrPut(key) { mutableListOf() }.add(file)
                val id = file.mediaStoreArtistId?.takeIf { it > 0 }
                if (id != null) artistIdSet.add(id)
            }
        }

        val artistIdNameMap = if (artistIdSet.isNotEmpty()) {
            mediaStoreDataSource.queryArtistNames(artistIdSet.toList())
        } else {
            emptyMap()
        }
        artistNameCache.putAll(artistIdNameMap)

        val artistFilesMap = mutableMapOf<String, MutableList<AudioFile>>()
        val artistNameToId = mutableMapOf<String, Long?>()
        val artistNameUseRawString = mutableSetOf<String>()

        primaryGroups.forEach { (primaryKey, groupFiles) ->
            val artistId = primaryKey?.removePrefix("id:")?.toLongOrNull()

            for (file in groupFiles) {
                val artistName = file.metadata.artist ?: continue

                if (separatorEnabled && customSeparators.isNotEmpty()) {
                    val splitArtistNames = splitArtist(artistName, customSeparators)
                    for (splitName in splitArtistNames) {
                        artistFilesMap.getOrPut(splitName) { mutableListOf() }.add(file)
                        artistNameToId[splitName] = artistId
                        artistNameUseRawString.add(splitName)
                    }
                } else {
                    artistFilesMap.getOrPut(artistName) { mutableListOf() }.add(file)
                    artistNameToId[artistName] = artistId
                }
            }
        }

        val artistsMap = artistFilesMap.map { (artistName, artistFiles) ->
            val artistId = artistNameToId[artistName]
            val displayName = when {
                artistName in artistNameUseRawString -> artistName
                artistId != null -> artistIdNameMap[artistId] ?: artistName
                else -> artistName
            }

            val sortedForCover = artistFiles.sortedWith(
                compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    .thenBy { it.metadata.album }
            )
            val coverFile = sortedForCover.firstOrNull()

            artistName to ArtistGroup(
                name = displayName,
                albums = artistFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
                files = artistFiles.sortedBy { it.metadata.album }.toImmutableList(),
                coverPath = coverFile?.path
            )
        }.toMap()

        _artistsMap.value = artistsMap
        emitUpdatedLists()
    }

    // updateAlbumsFromFiles was removed — all call sites use buildAlbumsFromFiles instead.

    private fun extractAlbumYear(file: AudioFile): Int? {
        val rawYear = file.metadata.year?.trim().orEmpty()
        if (rawYear.isEmpty()) return null
        return YEAR_REGEX.find(rawYear)?.value?.toIntOrNull()
    }

    // updateArtistsFromFiles was removed — all call sites use buildArtistsFromFiles instead.

    /**
     * Lightweight equality check for album lists to avoid unnecessary emissions.
     */
    private fun areAlbumListsEqual(a: List<AlbumGroup>, b: List<AlbumGroup>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val ai = a[i]
            val bi = b[i]
            if (ai.name != bi.name ||
                ai.albumArtist != bi.albumArtist ||
                ai.files.size != bi.files.size ||
                ai.coverPath != bi.coverPath ||
                ai.year != bi.year
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Lightweight equality check for artist lists to avoid unnecessary emissions.
     */
    private fun areArtistListsEqual(a: List<ArtistGroup>, b: List<ArtistGroup>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val ai = a[i]
            val bi = b[i]
            if (ai.name != bi.name ||
                ai.files.size != bi.files.size ||
                ai.coverPath != bi.coverPath
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Split artist string by separators.
     */
    private fun splitArtist(artist: String, separators: Set<String>): List<String> {
        if (artist.isBlank()) return emptyList()
        if (separators.isEmpty()) return listOf(artist)

        return artist.split(separatorRegex(separators))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun separatorRegex(separators: Set<String>): Regex {
        val key = separators.sorted().joinToString(" ")
        return SPLIT_REGEX_CACHE.getOrPut(key) {
            Regex(separators.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })
        }
    }

    /**
     * Display name for an artist group key, mirroring buildArtistsFromFiles:
     * when separators are enabled the (split) name IS the display name; when
     * disabled, prefer the MediaStore canonical name for id-backed files.
     */
    private suspend fun resolveArtistDisplayName(
        artistKey: String,
        file: AudioFile,
        separatorEnabled: Boolean
    ): String {
        if (separatorEnabled) return artistKey
        val artistId = file.mediaStoreArtistId?.takeIf { it > 0 } ?: return artistKey
        artistNameCache[artistId]?.let { return it }
        val artistNames = mediaStoreDataSource.queryArtistNames(listOf(artistId))
        artistNameCache.putAll(artistNames)
        return artistNames[artistId] ?: artistKey
    }
}
