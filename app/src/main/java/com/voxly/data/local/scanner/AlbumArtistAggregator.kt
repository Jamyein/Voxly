package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.AlbumSortOption
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.UiStateDataStore
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.repository.WhitelistRepository
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Aggregates audio files into albums and artists.
 * Directly observes Room database and applies filters before aggregation.
 */
@Singleton
class AlbumArtistAggregator @Inject constructor(
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val uiStateDataStore: UiStateDataStore,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val whitelistRepository: WhitelistRepository,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    // Albums derived from cached audio files - auto-updated when cache changes
    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    // Artists derived from cached audio files - auto-updated when cache changes
    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    // Filtered audio files - exposed for LibraryViewModel and other consumers
    private val _filteredFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val filteredFiles: StateFlow<List<AudioFile>> = _filteredFiles.asStateFlow()

    // Albums sorted by different sort options
    private val _albumsBySort = MutableStateFlow<Map<AlbumSortOption, List<AlbumGroup>>>(emptyMap())
    val albumsBySort: StateFlow<Map<AlbumSortOption, List<AlbumGroup>>> = _albumsBySort.asStateFlow()

    init {
        applicationScope.launch(Dispatchers.Default) {
            libraryCache.getCachedAudioFiles()
                .collectLatest { files ->
                    kotlinx.coroutines.delay(50)
                    updateAlbumsAndArtistsFromFilesInternal(files)
                }
        }
    }

    /**
     * Updates albums and artists from audio files.
     * Called automatically when cache changes.
     * Applies whitelist/blacklist filtering before aggregation.
     */
    private suspend fun updateAlbumsAndArtistsFromFilesInternal(files: List<AudioFile>) {
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val whitelistPaths = whitelistRepository.getValidWhitelistPathsOnce()
        val blacklistPaths = whitelistRepository.getValidBlacklistPathsOnce()

        val filtered = filterEngine.applyFilters(
            files,
            FilterEngine.FilterSettings(
                whitelistEnabled = whitelistEnabled,
                blacklistEnabled = blacklistEnabled,
                minDurationEnabled = minDurationEnabled,
                whitelistUris = whitelistPaths,
                blacklistUris = blacklistPaths,
                minDurationMs = minDurationMs
            )
        )
        _filteredFiles.value = filtered
        updateAlbumsFromFiles(filtered)
        updateArtistsFromFiles(filtered)
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
            AlbumGroup(
                name = albumName,
                albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                files = albumFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        _albums.value = albumsList
        timber.log.Timber.d("AlbumArtistAggregator: Updated albums count = ${albumsList.size}")

        computeAndCacheSortOrders(albumsList)
    }

    private fun computeAndCacheSortOrders(albumsList: List<AlbumGroup>) {
        val sortedAlbumsByOption = mapOf(
            AlbumSortOption.NAME_ASC to albumsList.sortedBy { SortUtil.toSortablePinyin(it.name) },
            AlbumSortOption.TRACK_COUNT_DESC to albumsList.sortedByDescending { it.files.size },
            AlbumSortOption.YEAR_DESC to albumsList.sortedByDescending { album ->
                album.files.mapNotNull { audioFile ->
                    audioFile.metadata.year
                        ?.let { Regex("""\d{4}""").find(it)?.value }
                        ?.toIntOrNull()
                }.maxOrNull() ?: Int.MIN_VALUE
            }
        )

        _albumsBySort.value = sortedAlbumsByOption
    }

    /**
     * Derives artists from audio files.
     * Two-level grouping:
     * 1. Primary grouping by mediaStoreArtistId (if available)
     * 2. Secondary splitting by separator within each artistId group
     * A file can belong to multiple artist groups if separator splits its artist string.
     */
    private suspend fun updateArtistsFromFiles(files: List<AudioFile>) {
        val isSeparatorEnabled = settingsDataStore.artistSeparatorEnabled.first()
        val customSeparators = settingsDataStore.artistSeparatorsSet.first()

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

                if (isSeparatorEnabled && customSeparators.isNotEmpty()) {
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
}
