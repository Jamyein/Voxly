package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.AlbumSortOption
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.CacheChange
import com.voxly.domain.model.CacheChangeKeys
import com.voxly.domain.model.IncrementalList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Aggregates audio files into albums and artists.
 * Listens to cacheVersionFlow to trigger re-aggregation only when cache version changes,
 * avoiding unnecessary recomputation on every Flow emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * Diff-based album list updates, modelled after Gramophone's IncrementalList.
     *
     * For a 10000-item library, a single album added is a 1-element `Insert`
     * event instead of a 10000-element list replacement. UI consumers
     * (LazyColumn) can apply the diff locally.
     *
     * Replay = 1 so late subscribers (e.g. user navigates to Albums mid-scan)
     * receive the most recent event to reconstruct current state.
     */
    private val _albumDiff = MutableSharedFlow<IncrementalList<AlbumGroup>>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val albumDiff: SharedFlow<IncrementalList<AlbumGroup>> = _albumDiff.asSharedFlow()

    /** Diff-based artist list updates, same model as [albumDiff]. */
    private val _artistDiff = MutableSharedFlow<IncrementalList<ArtistGroup>>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val artistDiff: SharedFlow<IncrementalList<ArtistGroup>> = _artistDiff.asSharedFlow()

    private val _albumsBySort = MutableStateFlow<Map<AlbumSortOption, List<AlbumGroup>>>(
        mapOf(
            AlbumSortOption.NAME_ASC to emptyList(),
            AlbumSortOption.TRACK_COUNT_DESC to emptyList(),
            AlbumSortOption.YEAR_DESC to emptyList()
        )
    )
    val albumsBySort: StateFlow<Map<AlbumSortOption, List<AlbumGroup>>> = _albumsBySort.asStateFlow()

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
     * Serializes FilesBatchUpdated work so concurrent batch emissions do not
     * race on `_albumsMap` / `_artistsMap` / reverse indexes.
     * Single-file events (FileUpdated / FileDeleted) are NOT serialized here
     * because they only touch a single path and use ConcurrentHashMap for
     * the reverse index — worst case is a stale-by-one entry which the next
     * batch will reconcile.
     */
    private val aggregatorMutex = Mutex()

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
                        buildAggregatesFromFiles(filteredAllAudios.value, config)
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
                            buildAggregatesFromFiles(filteredAllAudios.value, config)
                        } else {
                            // The shared filtered flow is already filtered with the
                            // current settings — reuse it across all per-key rebuilds.
                            val filtered = filteredAllAudios.value
                            val config = aggregationConfig.first()
                            // The event's artistKeys use extractArtistKey (raw name /
                            // id), which never matches the split-name groups — derive
                            // the affected artist keys from the changed file paths with
                            // the same name-based key logic as the full build.
                            val affectedArtistKeys = change.filePaths
                                .mapNotNull { path ->
                                    libraryCache.getCachedFile(path)?.let { file ->
                                        CacheChangeKeys.extractArtistKeysWithSeparators(
                                            file,
                                            if (config.separatorEnabled) config.separators else emptySet()
                                        )
                                    }
                                }
                                .flatten()
                                .toSet()
                            Timber.d(TAG, "FilesBatchUpdated: incremental rebuild (albums=${change.albumKeys.size}, artists=$affectedArtistKeys, files=$changedSize)")
                            aggregatorMutex.withLock {
                                change.albumKeys.forEach { albumKey -> applyFilteredToAlbum(albumKey, filtered) }
                                affectedArtistKeys.forEach { artistKey -> applyFilteredToArtist(artistKey, filtered, config) }
                            }
                        }
                    }
                }
            }
        }

        // Filter toggles (whitelist/blacklist/min-duration, including the
        // threshold value) change the filtered library WITHOUT touching the
        // cache, so no cache event fires. Rebuild from the shared filtered flow
        // on every settings change. drop(1) skips the initial emission, which
        // kickOffInitialBuild already handles.
        applicationScope.launch(Dispatchers.Default) {
            filterSettings.drop(1).collect {
                Timber.d(TAG, "Filter settings changed, re-building aggregates")
                val config = aggregationConfig.first()
                buildAggregatesFromFiles(filteredAllAudios.value, config)
            }
        }

        // Artist separator toggles/values change the artist grouping WITHOUT
        // touching the cache, so no cache event fires. Rebuild artists from the
        // shared filtered flow on every separator change. drop(1) skips the
        // initial emission, which kickOffInitialBuild already handles.
        applicationScope.launch(Dispatchers.Default) {
            aggregationConfig.drop(1).collect { config ->
                Timber.d(TAG, "Artist separator settings changed, re-building artists")
                buildArtistsFromFiles(filteredAllAudios.value, config.separatorEnabled, config.separators)
            }
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
        if (libraryCache.getCachedAudioFilesOnce().isEmpty()) {
            Timber.d(TAG, "kickOffInitialBuild: cache empty, waiting for FullRefresh event")
            return
        }
        // Await the first real filtered emission (real cache data + real
        // settings from DataStore) instead of reading filterSettings.value,
        // which may still hold the initial no-filter value at startup.
        val config = aggregationConfig.first()
        buildAggregatesFromFiles(filteredAllAudios.first { it.isNotEmpty() }, config)
    }

    private suspend fun incrementalUpdateFile(
        filePath: String,
        albumKey: String?
    ) {
        // The shared filtered flow is authoritative: a single-file update whose
        // path is currently filtered out (blacklisted, too short, outside the
        // whitelist) must not re-enter an album/artist.
        if (filteredAllAudios.value.none { it.path == filePath }) return

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
     * Construct [AlbumGroup] for [albumKey] from a pre-fetched `filtered` list
     * of all cached audio files, then write it to [_albumsMap] and update
     * the reverse index. Caller is responsible for [emitUpdatedLists] when
     * batching across multiple keys.
     */
    private fun applyFilteredToAlbum(albumKey: String, filtered: List<AudioFile>) {
        val filesForAlbum = filtered.filter { CacheChangeKeys.extractAlbumKey(it) == albumKey }
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
            coverPath = coverFile?.path,
            year = albumYear
        )
        _albumsMap.value = currentMap

        // Rebuild reverse index entries for this album's files (other albums'
        // entries are untouched)
        filesForAlbum.forEach { f -> _fileAlbumMap[f.path] = albumKey }
    }

    /**
     * Construct [ArtistGroup] for [artistKey] from a pre-fetched `filtered` list
     * of all cached audio files, then write it to [_artistsMap]. Mirrors
     * [applyFilteredToAlbum] and uses the same name-based key logic as the full
     * build, so incremental batch artist updates stay consistent with a rebuild.
     * Caller is responsible for [emitUpdatedLists] when batching across keys.
     */
    private suspend fun applyFilteredToArtist(
        artistKey: String,
        filtered: List<AudioFile>,
        config: AggregationConfig
    ) {
        val filesForArtist = filtered.filter { file ->
            CacheChangeKeys.extractArtistKeysWithSeparators(
                file,
                if (config.separatorEnabled) config.separators else emptySet()
            ).contains(artistKey)
        }
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
            name = resolveArtistDisplayName(artistKey, filesForArtist.last(), config.separatorEnabled),
            albums = filesForArtist.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
            files = filesForArtist.sortedBy { it.metadata.album }.toImmutableList(),
            coverPath = coverFile?.path
        )
        _artistsMap.value = currentMap
    }

    private fun emitUpdatedLists() {
        val albumsList = _albumsMap.value.values.sortedBy { SortUtil.toSortablePinyin(it.name) }
        if (!areAlbumListsEqual(albumsList, _albums.value)) {
            _albums.value = albumsList
            computeAndCacheSortOrders(albumsList)
            emitAlbumDiff(albumsList)
        }

        val artistsList = _artistsMap.value.values.sortedBy { SortUtil.toSortablePinyin(it.name) }
        if (!areArtistListsEqual(artistsList, _artists.value)) {
            _artists.value = artistsList
            emitArtistDiff(artistsList)
        }
    }

    /**
     * Compute and emit a coarse-grained [IncrementalList] diff between
     * the previous album list and the new [albumsList]. For 10000-item
     * libraries with rare changes, this is O(changes) instead of O(N).
     */
    private fun emitAlbumDiff(albumsList: List<AlbumGroup>) {
        val previous = _albums.value
        val prevMap = previous.associateBy { keyFor(it) }
        val newMap = albumsList.associateBy { keyFor(it) }

        val added = albumsList.filter { it.key() !in prevMap }
        val removed = previous.filter { it.key() !in newMap }

        when {
            // Full reset: previous list is empty (initial load) or completely different
            previous.isEmpty() || (added.size > previous.size / 2 && removed.size > previous.size / 2) -> {
                _albumDiff.tryEmit(IncrementalList.Reset(albumsList))
            }
            // Only additions (typical scan-with-changes case)
            added.isNotEmpty() && removed.isEmpty() -> {
                _albumDiff.tryEmit(IncrementalList.Insert(added, albumsList))
            }
            // Only removals (deletion case)
            removed.isNotEmpty() && added.isEmpty() -> {
                _albumDiff.tryEmit(IncrementalList.Remove(removed, albumsList))
            }
            // Mixed changes — fall back to Reset for correctness
            else -> {
                _albumDiff.tryEmit(IncrementalList.Reset(albumsList))
            }
        }
    }

    private fun emitArtistDiff(artistsList: List<ArtistGroup>) {
        val previous = _artists.value
        val prevSet = previous.map { it.name }.toSet()
        val newSet = artistsList.map { it.name }.toSet()

        val added = artistsList.filter { it.name !in prevSet }
        val removed = previous.filter { it.name !in newSet }

        when {
            previous.isEmpty() || (added.size > previous.size / 2 && removed.size > previous.size / 2) -> {
                _artistDiff.tryEmit(IncrementalList.Reset(artistsList))
            }
            added.isNotEmpty() && removed.isEmpty() -> {
                _artistDiff.tryEmit(IncrementalList.Insert(added, artistsList))
            }
            removed.isNotEmpty() && added.isEmpty() -> {
                _artistDiff.tryEmit(IncrementalList.Remove(removed, artistsList))
            }
            else -> {
                _artistDiff.tryEmit(IncrementalList.Reset(artistsList))
            }
        }
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

    private fun computeAndCacheSortOrders(albumsList: List<AlbumGroup>) {
        val sortedAlbumsByOption = mapOf(
            AlbumSortOption.NAME_ASC to albumsList.sortedBy { SortUtil.toSortablePinyin(it.name) },
            AlbumSortOption.TRACK_COUNT_DESC to albumsList.sortedByDescending { it.files.size },
            AlbumSortOption.YEAR_DESC to albumsList.sortedByDescending { album ->
                album.year ?: Int.MIN_VALUE
            }
        )

        if (!areSortedAlbumMapsEqual(sortedAlbumsByOption, _albumsBySort.value)) {
            _albumsBySort.value = sortedAlbumsByOption
        }
    }

    private fun extractAlbumYear(file: AudioFile): Int? {
        val rawYear = file.metadata.year?.trim().orEmpty()
        if (rawYear.isEmpty()) return null
        return Regex("""\d{4}""").find(rawYear)?.value?.toIntOrNull()
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
     * Lightweight equality check for sorted album maps.
     */
    private fun areSortedAlbumMapsEqual(
        a: Map<AlbumSortOption, List<AlbumGroup>>,
        b: Map<AlbumSortOption, List<AlbumGroup>>
    ): Boolean {
        if (a.size != b.size || a.keys != b.keys) return false
        for (key in a.keys) {
            if (!areAlbumListsEqual(a.getValue(key), b.getValue(key))) return false
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

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        return artist.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }
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
        val artistNames = mediaStoreDataSource.queryArtistNames(listOf(artistId))
        return artistNames[artistId] ?: artistKey
    }
}
