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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import kotlinx.coroutines.launch
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

    private val _filteredFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val filteredFiles: StateFlow<List<AudioFile>> = _filteredFiles.asStateFlow()

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
    }

    init {
        applicationScope.launch(Dispatchers.IO) {
            val cachedFiles = libraryCache.getCachedAudioFilesOnce()
            if (cachedFiles.isNotEmpty()) {
                val config = aggregationConfig.first()
                buildAggregatesFromFiles(cachedFiles, config)
            }
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
                        Timber.d(TAG, "FilesBatchUpdated: ${change.filePaths.size} files")
                        val config = aggregationConfig.first()
                        val files = libraryCache.getCachedAudioFilesOnce()
                        buildAggregatesFromFiles(files, config)
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

    private suspend fun incrementalUpdateFile(
        filePath: String,
        albumKey: String?,
        artistKey: String?
    ) {
        val config = aggregationConfig.first()
        val file = libraryCache.getCachedFile(filePath) ?: return

        val newAlbumKey = CacheChangeKeys.extractAlbumKey(file)
        val newArtistKeys = CacheChangeKeys.extractArtistKeysWithSeparators(file, config.separators)

        if (albumKey != null && newAlbumKey != null && albumKey != newAlbumKey) {
            removeFileFromAlbum(filePath, albumKey)
        }
        if (newAlbumKey != null && newAlbumKey != albumKey) {
            addFileToAlbum(file, newAlbumKey)
        } else if (albumKey != null) {
            updateAlbumIncremental(file, albumKey)
        }

        val oldArtistKeys = artistKey?.let { setOf(it) } ?: emptySet()
        val keysToRemove = oldArtistKeys - newArtistKeys.toSet()
        val keysToAdd = newArtistKeys.toSet() - oldArtistKeys

        for (key in keysToRemove) {
            removeFileFromArtist(filePath, key)
        }
        for (key in keysToAdd) {
            addFileToArtist(file, key, config.separators)
        }

        emitUpdatedLists()
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
                files = newFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path,
                year = albumYear
            )
        }
        _albumsMap.value = currentMap
    }

    private fun addFileToAlbum(file: AudioFile, albumKey: String) {
        val currentMap = _albumsMap.value.toMutableMap()
        val existingAlbum = currentMap[albumKey]

        val newFiles = if (existingAlbum != null) {
            existingAlbum.files.filter { it.path != file.path } + file
        } else {
            listOf(file)
        }

        val albumName = file.metadata.album ?: albumKey.removePrefix("id:").removePrefix("str:")
        val albumArtist = file.metadata.albumArtist ?: file.metadata.artist
        val coverFile = newFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: newFiles.firstOrNull()
        val albumYear = newFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

        currentMap[albumKey] = AlbumGroup(
            name = albumName,
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            files = newFiles.sortedBy { it.metadata.trackNumber },
            coverPath = coverFile?.path,
            year = albumYear
        )
        _albumsMap.value = currentMap
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
                albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = newFiles.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
        }
        _artistsMap.value = currentMap
    }

    private fun addFileToArtist(file: AudioFile, artistKey: String, separators: Set<String>) {
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

        val displayName = if (artistKey.startsWith("id:")) {
            file.metadata.artist ?: artistKey.removePrefix("id:")
        } else {
            artistKey
        }
        currentMap[artistKey] = ArtistGroup(
            name = displayName,
            albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
            files = newFiles.sortedBy { it.metadata.album },
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

        val albumName = file.metadata.album ?: albumKey.removePrefix("id:").removePrefix("str:")
        val albumArtist = file.metadata.albumArtist ?: file.metadata.artist
        val coverFile = newAlbumFiles.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: newAlbumFiles.firstOrNull()
        val albumYear = newAlbumFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

        currentMap[albumKey] = AlbumGroup(
            name = albumName,
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            files = newAlbumFiles.sortedBy { it.metadata.trackNumber },
            coverPath = coverFile?.path,
            year = albumYear
        )

        _albumsMap.value = currentMap
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

            val displayName = if (key.startsWith("id:")) {
                file.metadata.artist ?: key.removePrefix("id:")
            } else {
                key
            }
            currentMap[key] = ArtistGroup(
                name = displayName,
                albums = newArtistFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = newArtistFiles.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
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
                    files = newFiles.sortedBy { it.metadata.trackNumber },
                    coverPath = coverFile?.path,
                    year = albumYear
                )
            }

            _albumsMap.value = currentMap
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
                        albums = newFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                        files = newFiles.sortedBy { it.metadata.album },
                        coverPath = coverFile?.path
                    )
                }
            }

            _artistsMap.value = currentMap
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
                whitelistUris = config.whitelistPaths,
                blacklistUris = config.blacklistPaths,
                minDurationMs = config.minDurationMs
            )
        )

        val filesForAlbum = filtered.filter { CacheChangeKeys.extractAlbumKey(it) == albumKey }
        if (filesForAlbum.isEmpty()) {
            _albumsMap.value = _albumsMap.value.toMutableMap().apply { remove(albumKey) }
        } else {
            updateAlbumIncremental(filesForAlbum.first(), albumKey)
        }

        emitUpdatedLists()
    }

    private suspend fun rebuildArtist(artistKey: String) {
        val config = aggregationConfig.first()
        incrementalUpdateFile(
            libraryCache.getCachedAudioFilesOnce().firstOrNull()?.path ?: return,
            CacheChangeKeys.extractArtistKey(libraryCache.getCachedAudioFilesOnce().first()),
            artistKey
        )
    }

    private fun emitUpdatedLists() {
        val albumsList = _albumsMap.value.values.sortedBy { SortUtil.toSortablePinyin(it.name) }
        if (!areAlbumListsEqual(albumsList, _albums.value)) {
            _albums.value = albumsList
            computeAndCacheSortOrders(albumsList)
        }

        val artistsList = _artistsMap.value.values.sortedBy { SortUtil.toSortablePinyin(it.name) }
        if (!areArtistListsEqual(artistsList, _artists.value)) {
            _artists.value = artistsList
        }

        _filteredFiles.value = _albumsMap.value.values.flatMap { it.files }.distinctBy { it.path }
    }

/**
     * Builds complete aggregates from audio files.
     * Called on full refresh to rebuild all maps from scratch.
     */
    private suspend fun buildAggregatesFromFiles(
        files: List<AudioFile>,
        config: AggregationConfig
    ) {
        val filtered = filterEngine.applyFilters(
            files,
            FilterEngine.FilterSettings(
                whitelistEnabled = config.whitelistEnabled,
                blacklistEnabled = config.blacklistEnabled,
                minDurationEnabled = config.minDurationEnabled,
                whitelistUris = config.whitelistPaths,
                blacklistUris = config.blacklistPaths,
                minDurationMs = config.minDurationMs
            )
        )
        _filteredFiles.value = filtered
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
                    ?: albumFiles.firstOrNull()?.metadata?.artist
            } else {
                val parts = key?.removePrefix("str:")?.split("|") ?: listOf()
                albumName = parts.firstOrNull() ?: ""
                albumArtist = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            }

            val coverFile = albumFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: albumFiles.firstOrNull()
            val albumYear = albumFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()

            key!! to AlbumGroup(
                name = albumName,
                albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                files = albumFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path,
                year = albumYear
            )
        }.toMap()

        _albumsMap.value = albumsMap
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
                    val splitArtists = CacheChangeKeys.extractArtistKeysWithSeparators(file, customSeparators)
                    for (splitName in splitArtists) {
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
                albums = artistFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = artistFiles.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
        }.toMap()

        _artistsMap.value = artistsMap
    }

    /**
     * Derives albums from audio files.
     * Groups by albumId if available, otherwise falls back to (albumName, albumArtist) string.
     */
    private suspend fun updateAlbumsFromFiles(files: List<AudioFile>) {
        // First level: group by albumId or (albumName, albumArtist) string
        val primaryGroups = mutableMapOf<String?, MutableList<AudioFile>>()
        val albumIdSet = mutableSetOf<Long>()

        files.forEach { file ->
            val effectiveAlbumId = file.mediaStoreAlbumId?.takeIf { it > 0 }
            val effectiveAlbumName = file.metadata.album?.takeIf { it.isNotBlank() }
            val effectiveAlbumArtist = file.metadata.albumArtist?.takeIf { it.isNotBlank() }
                ?: file.metadata.artist?.takeIf { it.isNotBlank() }

            when {
                effectiveAlbumId != null && effectiveAlbumName != null -> {
                    primaryGroups.getOrPut("id:$effectiveAlbumId") { mutableListOf() }.add(file)
                    albumIdSet.add(effectiveAlbumId)
                }
                effectiveAlbumName != null -> {
                    val key = "str:$effectiveAlbumName|${effectiveAlbumArtist.orEmpty()}"
                    primaryGroups.getOrPut(key) { mutableListOf() }.add(file)
                }
            }
        }

        val albumsList = primaryGroups.map { (key, albumFiles) ->
            val albumName: String
            val albumArtist: String?

            if (key?.startsWith("id:") == true) {
                // albumId group - use metadata values
                albumName = albumFiles.firstOrNull()?.metadata?.album
                    ?: key.removePrefix("id:")
                albumArtist = albumFiles.firstOrNull()?.metadata?.albumArtist
                    ?: albumFiles.firstOrNull()?.metadata?.artist
            } else {
                // String fallback group
                val parts = key?.removePrefix("str:")?.split("|") ?: listOf()
                albumName = parts.firstOrNull() ?: ""
                albumArtist = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            }

            val coverFile = albumFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: albumFiles.firstOrNull()
            val albumYear = albumFiles.mapNotNull { extractAlbumYear(it) }.maxOrNull()
            AlbumGroup(
                name = albumName,
                albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                files = albumFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path,
                year = albumYear
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        if (!areAlbumListsEqual(albumsList, _albums.value)) {
            _albums.value = albumsList
            computeAndCacheSortOrders(albumsList)
        }
    }

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

    /**
     * Derives artists from audio files.
     * Two-level grouping:
     * 1. Primary grouping by mediaStoreArtistId (if available)
     * 2. Secondary splitting by separator within each artistId group
     * A file can belong to multiple artist groups if separator splits its artist string.
     */
    private suspend fun updateArtistsFromFiles(
        files: List<AudioFile>,
        separatorEnabled: Boolean,
        customSeparators: Set<String>
    ) {
        // First level: group by artistId or artist string
        val primaryGroups = mutableMapOf<String?, MutableList<AudioFile>>()
        val artistIdSet = mutableSetOf<Long>()

        files.forEach { file ->
            val effectiveArtistId = file.mediaStoreArtistId?.takeIf { it > 0 }
            val effectiveArtistName = file.metadata.artist?.takeIf { it.isNotBlank() }

            when {
                effectiveArtistId != null && effectiveArtistName != null -> {
                    primaryGroups.getOrPut("id:$effectiveArtistId") { mutableListOf() }.add(file)
                    artistIdSet.add(effectiveArtistId)
                }
                effectiveArtistName != null -> {
                    primaryGroups.getOrPut(effectiveArtistName) { mutableListOf() }.add(file)
                }
                else -> {
                    // No artist info, skip
                }
            }
        }

        // Query artist names for artistIds
        val artistIdNameMap = if (artistIdSet.isNotEmpty()) {
            mediaStoreDataSource.queryArtistNames(artistIdSet.toList())
        } else {
            emptyMap()
        }

        // Second level: split by separator and build final artist groups
        val artistFilesMap = mutableMapOf<String, MutableList<AudioFile>>()
        val artistNameToId = mutableMapOf<String, Long?>()
        val artistNameUseRawString = mutableSetOf<String>()

        primaryGroups.forEach { (primaryKey, groupFiles) ->
            val artistId = if (primaryKey?.startsWith("id:") == true) {
                primaryKey.removePrefix("id:").toLongOrNull()
            } else null

            for (file in groupFiles) {
                val artistName = file.metadata.artist ?: continue

                if (separatorEnabled && customSeparators.isNotEmpty()) {
                    val splitArtists = splitArtist(artistName, customSeparators)
                    for (splitName in splitArtists) {
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

        val artistsList = artistFilesMap.map { (artistName, artistFiles) ->
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
            ArtistGroup(
                name = displayName,
                albums = artistFiles.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = artistFiles.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        if (!areArtistListsEqual(artistsList, _artists.value)) {
            _artists.value = artistsList
        }
    }

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
}
