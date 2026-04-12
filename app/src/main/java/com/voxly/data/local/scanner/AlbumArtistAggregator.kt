package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cache.AlbumInfoManager
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates audio files into albums and artists.
 * Maintains derived StateFlows for albums and artists that auto-update when files change.
 */
@Singleton
class AlbumArtistAggregator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val albumInfoManager: AlbumInfoManager
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

    /**
     * Updates albums and artists from audio files.
     * Called automatically when cache changes.
     * Applies whitelist/blacklist filtering before aggregation.
     */
    suspend fun updateAlbumsAndArtistsFromFiles(
        files: List<AudioFile>,
        filterEngine: FilterEngine
    ) {
        // Load current filter settings
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val whitelistUris = settingsDataStore.selectedDirectoryUris.first()
        val blacklistUris = settingsDataStore.blacklistDirectoryUris.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val filteredFiles = filterEngine.applyFilters(
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
        updateAlbumsFromFiles(filteredFiles)
        updateArtistsFromFiles(filteredFiles)
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

        // Update album info cache
        if (albumsForCache.isNotEmpty()) {
            albumInfoManager.updateAlbumInfoBatch(albumsForCache)
        }
    }

    /**
     * Derives artists from audio files.
     */
    private suspend fun updateArtistsFromFiles(files: List<AudioFile>) {
        val isSeparatorEnabled = settingsDataStore.artistSeparatorEnabled.first()
        val customSeparators = settingsDataStore.artistSeparatorsSet.first()

        val artistsMap = mutableMapOf<String, MutableList<AudioFile>>()

        files.filter { it.metadata.artist?.isNotBlank() == true }.forEach { file ->
            val artistField = file.metadata.artist!!

            if (isSeparatorEnabled && customSeparators.isNotEmpty()) {
                splitArtist(artistField, customSeparators).forEach { artistName ->
                    artistsMap.getOrPut(artistName) { mutableListOf() }.add(file)
                }
            } else {
                artistsMap.getOrPut(artistField) { mutableListOf() }.add(file)
            }
        }

        val artistsList = artistsMap.map { (artistName, artistFiles) ->
            val coverFile = artistFiles.firstOrNull {
                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
            } ?: artistFiles.firstOrNull()
            ArtistGroup(
                name = artistName,
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
