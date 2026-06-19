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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val minDurationEnabled: Boolean,
        val minDurationMs: Long,
        val whitelistPaths: List<String>,
        val blacklistPaths: List<String>,
        val separatorEnabled: Boolean,
        val separators: Set<String>
    )

    private data class FilterConfig(
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val minDurationEnabled: Boolean,
        val minDurationMs: Long,
        val whitelistPaths: List<String>,
        val blacklistPaths: List<String>
    )

    private val _albumsMap = MutableStateFlow<Map<String, AlbumGroup>>(emptyMap())
    private val _artistsMap = MutableStateFlow<Map<String, ArtistGroup>>(emptyMap())

    // Reverse index: filePath -> albumKey for O(1) lookup when metadata changes
    // ConcurrentHashMap because buildAlbumsFromFiles may run on a different coroutine
    // than incrementalUpdateFile (e.g. via direct init rebuild vs changeFlow collect).
    private val _fileAlbumMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Reverse index: filePath -> artistKey for O(1) lookup when metadata changes
    private val _fileArtistMap = java.util.concurrent.ConcurrentHashMap<String, String>()

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
            minDurationMs = minDurationMs.toLong(),
            whitelistPaths = emptyList(),
            blacklistPaths = emptyList()
        )
    }

    private val filterConfig = combine(
        baseFilterConfig,
        whitelistRepository.getValidWhitelistPaths().distinctUntilChanged(),
        whitelistRepository.getValidBlacklistPaths().distinctUntilChanged()
    ) { baseConfig, whitelistPaths, blacklistPaths ->
        baseConfig.copy(
            whitelistPaths = whitelistPaths,
            blacklistPaths = blacklistPaths
        )
    }

    private val aggregationConfig = combine(
        filterConfig,
        settingsDataStore.artistSeparatorEnabled,
        settingsDataStore.artistSeparatorsSet
    ) { filterConfig, separatorEnabled, separators ->
        AggregationConfig(
            whitelistEnabled = filterConfig.whitelistEnabled,
            blacklistEnabled = filterConfig.blacklistEnabled,
            minDurationEnabled = filterConfig.minDurationEnabled,
            minDurationMs = filterConfig.minDurationMs,
            whitelistPaths = filterConfig.whitelistPaths,
            blacklistPaths = filterConfig.blacklistPaths,
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
                        val files = libraryCache.getCachedAudioFilesOnce()
                        buildAggregatesFromFiles(files, config)
                    }
                    is CacheChange.FileUpdated -> {
                        Timber.d(TAG, "FileUpdated: ${change.filePath}, albumKey=${change.albumKey}, artistKey=${change.artistKey}")
                        incrementalUpdateFile(change.filePath, change.albumKey, change.artistKey)
                    }
                    is CacheChange.FileDeleted -> {
                        Timber.d(TAG, "FileDeleted: ${change.filePath}, albumKey=${change.albumKey}, artistKey=${change.artistKey}")
                        removeFileFromAggregates(change.filePath, change.albumKey, change.artistKey)
                    }
                    is CacheChange.FilesBatchUpdated -> {
                        val totalCached = libraryCache.getCachedFileCount()
                        val changedSize = change.filePaths.size
                        val isLargeDiff = totalCached > 0 &&
                            changedSize.toDouble() / totalCached > LARGE_DIFF_RATIO
                        val keysMissing = change.albumKeys.isEmpty() || change.artistKeys.isEmpty()
                        if (isLargeDiff || keysMissing) {
                            Timber.d(TAG, "FilesBatchUpdated: full rebuild (changed=$changedSize, total=$totalCached, isLargeDiff=$isLargeDiff, keysMissing=$keysMissing)")
                            val config = aggregationConfig.first()
                            val files = libraryCache.getCachedAudioFilesOnce()
                            buildAggregatesFromFiles(files, config)
                        } else {
                            Timber.d(TAG, "FilesBatchUpdated: incremental rebuild (albums=${change.albumKeys.size}, artists=${change.artistKeys.size}, files=$changedSize)")
                            // Fetch + filter once, reuse across all per-key rebuilds.
                            // Without hoisting, rebuildAlbum would call
                            // getCachedAudioFilesOnce() once per albumKey.
                            val allFiles = libraryCache.getCachedAudioFilesOnce()
                            val config = aggregationConfig.first()
                            val filtered = filterEngine.applyFilters(
                                allFiles,
                                FilterEngine.FilterSettings(
                                    whitelistEnabled = config.whitelistEnabled,
                                    blacklistEnabled = config.blacklistEnabled,
                                    minDurationEnabled = config.minDurationEnabled,
                                    whitelistPaths = config.whitelistPaths,
                                    blacklistPaths = config.blacklistPaths,
                                    minDurationMs = config.minDurationMs
                                )
                            )
                            aggregatorMutex.withLock {
                                change.albumKeys.forEach { albumKey -> applyFilteredToAlbum(albumKey, filtered) }
                                change.artistKeys.forEach { artistKey -> rebuildArtist(artistKey) }
                            }
                        }
                    }
                    is CacheChange.AlbumMetadataChanged -> {
                        Timber.d(TAG, "AlbumMetadataChanged: ${change.albumKey}")
                        rebuildAlbum(change.albumKey)
                    }
                    is CacheChange.ArtistMetadataChanged -> {
                        Timber.d(TAG, "ArtistMetadataChanged: ${change.artistKey}")
                        rebuildArtist(change.artistKey)
                    }
                }
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
        val files = libraryCache.getCachedAudioFilesOnce()
        if (files.isEmpty()) {
            Timber.d(TAG, "kickOffInitialBuild: cache empty, waiting for FullRefresh event")
            return
        }
        val config = aggregationConfig.first()
        buildAggregatesFromFiles(files, config)
    }

    private suspend fun incrementalUpdateFile(
        filePath: String,
        albumKey: String?,
        artistKey: String?
    ) {
        val config = aggregationConfig.first()
        val file = libraryCache.getCachedFile(filePath) ?: return

        val newAlbumKey = CacheChangeKeys.extractAlbumKey(file)
        val newArtistKeys = CacheChangeKeys.extractArtistKeysWithSeparators(file, config.separators)

        // Use reverse index to find old album/artist keys for O(1) lookup
        val oldAlbumKey = _fileAlbumMap[filePath]
        val oldArtistKey = _fileArtistMap[filePath]

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

        // Remove from old artist if key changed
        if (oldArtistKey != null && !newArtistKeys.contains(oldArtistKey)) {
            removeFileFromArtist(filePath, oldArtistKey)
        }

        // Add/update in new artists
        for (key in newArtistKeys) {
            if (key != oldArtistKey) {
                addFileToArtist(file, key, config.separators)
            } else {
                updateArtistIncremental(file, key, config.separators)
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
        
        // Update reverse index
        _fileArtistMap.remove(filePath)
    }

    private suspend fun addFileToArtist(file: AudioFile, artistKey: String, separators: Set<String>) {
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

        val displayName = resolveArtistDisplayName(artistKey, file)
        currentMap[artistKey] = ArtistGroup(
            name = displayName,
            albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
            files = newFiles.sortedBy { it.metadata.album }.toImmutableList(),
            coverPath = coverFile?.path
        )
        _artistsMap.value = currentMap
        
        // Update reverse index
        _fileArtistMap[file.path] = artistKey
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
        separators: Set<String>
    ) {
        val currentMap = _artistsMap.value.toMutableMap()

        val artistKeys = CacheChangeKeys.extractArtistKeysWithSeparators(file, separators)
        for (key in artistKeys) {
            val currentArtist = currentMap[key]
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

            val displayName = resolveArtistDisplayName(key, file)
            currentMap[key] = ArtistGroup(
                name = displayName,
                albums = newArtistFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
                files = newArtistFiles.sortedBy { it.metadata.album }.toImmutableList(),
                coverPath = coverFile?.path
            )
            
            // Update reverse index
            _fileArtistMap[file.path] = key
        }

        _artistsMap.value = currentMap
    }

    private suspend fun removeFileFromAggregates(
        filePath: String,
        albumKey: String?,
        artistKey: String?
    ) {
        val config = aggregationConfig.first()

        if (albumKey != null) {
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

        if (artistKey != null) {
            val artistKeys = listOf(artistKey)
            val currentMap = _artistsMap.value.toMutableMap()

            for (key in artistKeys) {
                val currentArtist = currentMap[key] ?: continue
                val newFiles = currentArtist.files.filter { it.path != filePath }

                if (newFiles.isEmpty()) {
                    currentMap.remove(key)
                } else {
                    val sortedForCover = newFiles.sortedWith(
                        compareByDescending<AudioFile> { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                            .thenBy { it.metadata.album }
                    )
                    val coverFile = sortedForCover.firstOrNull()

                currentMap[key] = currentArtist.copy(
                    albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted().toImmutableList(),
                    files = newFiles.sortedBy { it.metadata.album }.toImmutableList(),
                    coverPath = coverFile?.path
                    )
                }
            }

            _artistsMap.value = currentMap
            // Update reverse index
            _fileArtistMap.remove(filePath)
        }

        emitUpdatedLists()
    }

    private suspend fun rebuildAlbum(albumKey: String) {
        val allFiles = libraryCache.getCachedAudioFilesOnce()
        val config = aggregationConfig.first()
        val filtered = filterEngine.applyFilters(
            allFiles,
            FilterEngine.FilterSettings(
                whitelistEnabled = config.whitelistEnabled,
                blacklistEnabled = config.blacklistEnabled,
                minDurationEnabled = config.minDurationEnabled,
                whitelistPaths = config.whitelistPaths,
                blacklistPaths = config.blacklistPaths,
                minDurationMs = config.minDurationMs
            )
        )
        applyFilteredToAlbum(albumKey, filtered)
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

    private suspend fun rebuildArtist(artistKey: String) {
        val config = aggregationConfig.first()

        // Look up all files belonging to this artist via the artist_links table.
        // artistKey is either "id:<long>" (from MediaStore artist id) or a raw artist name.
        // The values returned by `getTrackIdsForArtist` are file paths (the
        // `ArtistLinkEntity.trackId` column now stores `file.path` instead of
        // the previous 32-bit `file.id.hashCode()` — see lesson.md #24 + #25).
        val trackIds: List<String> = when {
            artistKey.startsWith("id:") -> {
                // For id: prefixed keys, the MediaStore id maps to the canonical artist name
                // via the primary artist of each file. Fall back to scanning all files.
                libraryCache.getCachedAudioFilesOnce()
                    .filter { it.mediaStoreArtistId?.toString() == artistKey.removePrefix("id:") }
                    .map { it.path }
            }
            else -> libraryCache.getTrackIdsForArtist(artistKey).mapNotNull { path ->
                libraryCache.getCachedAudioFilesOnce().firstOrNull { it.path == path }?.path
            }
        }

        if (trackIds.isEmpty()) return

        // Re-process each file so the artist group gets re-derived correctly
        trackIds.forEach { path ->
            incrementalUpdateFile(path, null, artistKey)
        }
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
     * Called on full refresh to rebuild all maps from scratch.
     */
    private suspend fun buildAggregatesFromFiles(
        files: List<AudioFile>,
        config: AggregationConfig
    ) = withContext(Dispatchers.Default) {
        val filtered = filterEngine.applyFilters(
            files,
            FilterEngine.FilterSettings(
                whitelistEnabled = config.whitelistEnabled,
                blacklistEnabled = config.blacklistEnabled,
                minDurationEnabled = config.minDurationEnabled,
                whitelistPaths = config.whitelistPaths,
                blacklistPaths = config.blacklistPaths,
                minDurationMs = config.minDurationMs
            )
        )
        buildAlbumsFromFiles(filtered)
        buildArtistsFromFiles(
            files = filtered,
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
        
        // Rebuild reverse index for artists
        _fileArtistMap.clear()
        artistsMap.forEach { (key, artist) ->
            artist.files.forEach { file ->
                _fileArtistMap[file.path] = key
            }
        }
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
     * Resolve artist display name from an artist key.
     * For "id:" prefixed keys, queries MediaStore to get the actual artist name.
     * For other keys, returns the key as-is.
     */
    private suspend fun resolveArtistDisplayName(artistKey: String, file: AudioFile): String {
        if (!artistKey.startsWith("id:")) {
            return artistKey
        }
        val artistId = artistKey.removePrefix("id:").toLongOrNull() ?: return artistKey
        val artistNames = mediaStoreDataSource.queryArtistNames(listOf(artistId))
        return artistNames[artistId] ?: file.metadata.artist ?: artistKey
    }
}
