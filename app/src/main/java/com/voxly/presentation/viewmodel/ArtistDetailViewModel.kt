package com.voxly.presentation.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.repository.ArtistCacheRepository
import com.voxly.data.repository.ArtistGroup
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for ArtistDetailScreen.
 * Loads artist data from memory cache or database.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artistCacheRepository: ArtistCacheRepository
) : ViewModel() {

    private val _artistName = MutableStateFlow("")
    val artistName: StateFlow<String> = _artistName.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files.asStateFlow()

    private val _albumCount = MutableStateFlow(0)
    val albumCount: StateFlow<Int> = _albumCount.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _coverPath = MutableStateFlow<String?>(null)
    val coverPath: StateFlow<String?> = _coverPath.asStateFlow()

    private val _albumCovers = MutableStateFlow<Map<String, String?>>(emptyMap())
    val albumCovers: StateFlow<Map<String, String?>> = _albumCovers.asStateFlow()

    /**
     * Load artist data by artist name.
     * First tries to get from cache, then falls back to database query.
     */
    fun loadArtist(artistName: String) {
        viewModelScope.launch {
            try {
                // First try to get from cache
                val cachedArtist = artistCacheRepository.getArtist(artistName)

                if (cachedArtist != null) {
                    _artistName.value = cachedArtist.name
                    _files.value = cachedArtist.files
                    _coverPath.value = cachedArtist.coverPath
                    calculateStats(cachedArtist.files)
                    precomputeAlbumCovers(cachedArtist.files)
                } else {
                    // If not in cache, we need to query from database
                    // For now, show empty state
                    _artistName.value = artistName
                    _files.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _artistName.value = artistName
            }
        }
    }

    /**
     * Cache artist data for navigation.
     */
    fun cacheArtistData(artistName: String, files: List<AudioFile>) {
        val artistGroup = ArtistGroup(name = artistName, files = files)
        artistCacheRepository.cacheArtist(artistGroup)

        // Also update ViewModel state
        _artistName.value = artistName
        _files.value = files
        calculateStats(files)
        precomputeAlbumCovers(files)
    }

    private fun calculateStats(files: List<AudioFile>) {
        // Calculate album count (distinct albums)
        val albums = files.mapNotNull { it.metadata.album }.distinct()
        _albumCount.value = albums.size

        // Calculate total duration
        _totalDuration.value = files.sumOf { it.duration }
    }

    /**
     * Precompute album covers to avoid duplicate MediaMetadataRetriever calls in UI.
     * Returns a map of album name to file path that has embedded art.
     */
    private fun precomputeAlbumCovers(files: List<AudioFile>) {
        viewModelScope.launch {
            val covers = withContext(Dispatchers.IO) {
                val albumGroups = files.groupBy { it.metadata.album ?: "" }
                albumGroups.mapNotNull { (albumName, albumFiles) ->
                    if (albumName.isEmpty()) return@mapNotNull null

                    // Find first file with embedded art
                    val fileWithArt = albumFiles.firstOrNull { file ->
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(file.path)
                            val hasArt = retriever.embeddedPicture != null
                            retriever.release()
                            hasArt
                        } catch (e: Exception) {
                            false
                        }
                    }

                    albumName to fileWithArt?.path
                }.toMap()
            }
            _albumCovers.value = covers
        }
    }

    /**
     * Get formatted total duration string.
     */
    fun getFormattedDuration(): String {
        val duration = _totalDuration.value
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
