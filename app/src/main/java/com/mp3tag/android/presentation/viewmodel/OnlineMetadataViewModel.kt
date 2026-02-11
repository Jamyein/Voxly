package com.mp3tag.android.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.domain.model.AudioMetadata
import com.mp3tag.android.domain.repository.OnlineMetadataRepository
import com.mp3tag.android.domain.repository.OnlineRelease
import com.mp3tag.android.domain.repository.OnlineReleaseDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for online metadata lookup using MusicBrainz API.
 */
@HiltViewModel
class OnlineMetadataViewModel @Inject constructor(
    private val onlineMetadataRepository: OnlineMetadataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = savedStateHandle.get<String>("filePath") ?: ""

    private val _uiState = MutableStateFlow<OnlineMetadataUiState>(OnlineMetadataUiState.Idle)
    val uiState: StateFlow<OnlineMetadataUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<OnlineRelease>>(emptyList())
    val searchResults: StateFlow<List<OnlineRelease>> = _searchResults.asStateFlow()

    private val _selectedRelease = MutableStateFlow<OnlineReleaseDetails?>(null)
    val selectedRelease: StateFlow<OnlineReleaseDetails?> = _selectedRelease.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Searches for releases by artist and album.
     * @param artist Artist name
     * @param album Album title
     */
    fun searchByArtistAlbum(artist: String, album: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = OnlineMetadataUiState.Searching

            val result = onlineMetadataRepository.searchByArtistAlbum(artist, album)

            result.fold(
                onSuccess = { releases ->
                    _searchResults.value = releases
                    _uiState.value = if (releases.isEmpty()) {
                        OnlineMetadataUiState.NoResults
                    } else {
                        OnlineMetadataUiState.Results(releases)
                    }
                },
                onFailure = { error ->
                    _uiState.value = OnlineMetadataUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Searches for recordings by track title and artist.
     * @param title Track title
     * @param artist Artist name (optional)
     */
    fun searchByTrack(title: String, artist: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = OnlineMetadataUiState.Searching

            val result = onlineMetadataRepository.searchByTrack(title, artist)

            result.fold(
                onSuccess = { recordings ->
                    // Convert recordings to releases for display
                    val releases = recordings.mapNotNull { recording ->
                        recording.releaseId?.let { releaseId ->
                            OnlineRelease(
                                id = releaseId,
                                title = recording.title,
                                artist = recording.artist,
                                year = null,
                                format = null,
                                trackCount = null,
                                coverArtUrl = null
                            )
                        }
                    }
                    _searchResults.value = releases
                    _uiState.value = if (releases.isEmpty()) {
                        OnlineMetadataUiState.NoResults
                    } else {
                        OnlineMetadataUiState.Results(releases)
                    }
                },
                onFailure = { error ->
                    _uiState.value = OnlineMetadataUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Gets detailed information about a release.
     * @param releaseId The MusicBrainz release ID
     */
    fun getReleaseDetails(releaseId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = onlineMetadataRepository.getReleaseDetails(releaseId)

            result.fold(
                onSuccess = { details ->
                    _selectedRelease.value = details
                },
                onFailure = { error ->
                    _uiState.value = OnlineMetadataUiState.Error(
                        error.message ?: "Failed to get release details"
                    )
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Fetches cover art for the selected release.
     */
    fun fetchCoverArt() {
        val releaseId = _selectedRelease.value?.id ?: return

        viewModelScope.launch {
            _isLoading.value = true

            val result = onlineMetadataRepository.getCoverArt(releaseId)

            result.fold(
                onSuccess = { coverArt ->
                    _selectedRelease.value = _selectedRelease.value?.copy(
                        coverArtUrl = coverArt?.let { "data:image/jpeg;base64," + 
                            android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) }
                    )
                },
                onFailure = {
                    // Cover art fetch failed but we can still proceed
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Applies the selected release's metadata to the audio file.
     * @return AudioMetadata with the online data
     */
    fun applyMetadata(): AudioMetadata? {
        val details = _selectedRelease.value ?: return null

        return AudioMetadata(
            title = details.tracks.find { it.number == 1 }?.title,
            artist = details.artist,
            album = details.title,
            albumArtist = details.artist,
            year = details.year?.toString(),
            genre = details.genre,
            trackNumber = 1,
            totalTracks = details.trackCount,
            // Album art would be fetched separately
        )
    }

    /**
     * Clears the current selection.
     */
    fun clearSelection() {
        _selectedRelease.value = null
    }
}

/**
 * Sealed class representing online metadata UI states.
 */
sealed class OnlineMetadataUiState {
    data object Idle : OnlineMetadataUiState()
    data object Searching : OnlineMetadataUiState()
    data object NoResults : OnlineMetadataUiState()
    data class Results(val releases: List<OnlineRelease>) : OnlineMetadataUiState()
    data class Error(val message: String) : OnlineMetadataUiState()
}
