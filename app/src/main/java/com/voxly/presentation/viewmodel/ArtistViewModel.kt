package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.ArtistListItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {

    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists

    val artistListItems: StateFlow<List<ArtistListItemState>> = artists
        .map { artistGroups ->
            artistGroups.map { artist ->
                ArtistListItemState(
                    name = artist.name,
                    coverPath = artist.coverPath,
                    albumCount = artist.albums.size,
                    trackCount = artist.files.size
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
            } catch (e: Exception) {
                Timber.e(e, "Artist refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
