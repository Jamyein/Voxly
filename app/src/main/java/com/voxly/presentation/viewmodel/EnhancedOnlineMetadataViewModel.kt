package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.remote.itunes.ITunesRepository
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for online metadata lookup supporting multiple data sources.
 * Enhanced version with Apple Music/iTunes support.
 */
@HiltViewModel
class EnhancedOnlineMetadataViewModel @Inject constructor(
    private val aggregatedRepository: AggregatedOnlineMetadataRepository,
    private val iTunesRepository: ITunesRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<OnlineRelease>>(emptyList())
    val searchResults: StateFlow<List<OnlineRelease>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _dataSource = MutableStateFlow<DataSource>(DataSource.BOTH)
    val dataSource: StateFlow<DataSource> = _dataSource.asStateFlow()

    /**
     * Enum representing available data sources.
     */
    enum class DataSource {
        MUSICBRAINZ,
        ITUNES_APPLE_MUSIC,
        BOTH
    }

    /**
     * Sets the preferred data source for metadata lookup.
     */
    fun setDataSource(source: DataSource) {
        _dataSource.value = source
        aggregatedRepository.preferredSource = when (source) {
            DataSource.MUSICBRAINZ -> 
                AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
            DataSource.ITUNES_APPLE_MUSIC -> 
                AggregatedOnlineMetadataRepository.DataSource.ITUNES
            DataSource.BOTH -> 
                AggregatedOnlineMetadataRepository.DataSource.BOTH
        }
    }

    /**
     * Searches for releases using the selected data source.
     */
    fun searchReleases(artist: String, album: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = aggregatedRepository.searchByArtistAlbum(artist, album)

            result.fold(
                onSuccess = { releases ->
                    _searchResults.value = releases
                },
                onFailure = { error ->
                    _error.value = error.message ?: "Search failed"
                    _searchResults.value = emptyList()
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Searches specifically in Apple Music/iTunes catalog.
     * This method provides access to iTunes-specific features like high-res artwork.
     */
    fun searchAppleMusic(artist: String, album: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = iTunesRepository.searchByArtistAlbum(artist, album)

            result.fold(
                onSuccess = { releases ->
                    _searchResults.value = releases
                },
                onFailure = { error ->
                    _error.value = error.message ?: "Apple Music search failed"
                    _searchResults.value = emptyList()
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Gets high-resolution artwork from Apple Music.
     * iTunes provides higher quality artwork than most other sources.
     */
    suspend fun getHighResolutionArtwork(releaseId: String): ByteArray? {
        return try {
            val result = iTunesRepository.getCoverArt(releaseId)
            if (result.isSuccess) {
                result.getOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears search results.
     */
    fun clearResults() {
        _searchResults.value = emptyList()
        _error.value = null
    }

    /**
     * Gets the data source display name.
     */
    fun getDataSourceName(source: DataSource): String {
        return when (source) {
            DataSource.MUSICBRAINZ -> "MusicBrainz"
            DataSource.ITUNES_APPLE_MUSIC -> "Apple Music"
            DataSource.BOTH -> "Both Sources"
        }
    }
}
