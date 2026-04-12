package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cache.AlbumInfoManager
import com.voxly.domain.model.AlbumGroup
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
    private val albumInfoManager: AlbumInfoManager,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
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

    // Filtered audio files - exposed for LibraryViewModel and other consumers
    private val _filteredFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val filteredFiles: StateFlow<List<AudioFile>> = _filteredFiles.asStateFlow()

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
        // Load current filter settings
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val whitelistUris = settingsDataStore.selectedDirectoryUris.first()
        val blacklistUris = settingsDataStore.blacklistDirectoryUris.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val filtered = filterEngine.applyFilters(
            files,
            FilterEngine.FilterSettings(
                whitelistEnabled = whitelistEnabled,
                blacklistEnabled = blacklistEnabled,
                minDurationEnabled = minDurationEnabled,
                whitelistUris = whitelistUris,
                blacklistUris = blacklistUris,
                minDurationMs = minDurationMs
            )
        )
        _filteredFiles.value = filtered
        updateAlbumsFromFiles(filtered)
        updateArtistsFromFiles(filtered)
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
                albumArtist = key.albumArtist.takeIf { it.isNotBlank() },
                files = albumFiles.sortedBy { it.metadata.trackNumber },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        _albums.value = albumsList
        timber.log.Timber.d("AlbumArtistAggregator: Updated albums count = ${albumsList.size}")

        // Update album info cache
        if (albumsForCache.isNotEmpty()) {
            albumInfoManager.updateAlbumInfoBatch(albumsForCache)
        }
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

            val coverFile = artistFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: artistFiles.firstOrNull()
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
