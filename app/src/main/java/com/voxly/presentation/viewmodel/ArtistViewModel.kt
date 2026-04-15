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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {

    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val artistListItems: StateFlow<List<ArtistListItemState>> = artists
        .map { artistGroups ->
            artistGroups.map { artist ->
                val albumNames = mutableSetOf<String>()
                artist.files.forEach { file ->
                    file.metadata?.album?.takeIf { it.isNotBlank() }?.let { albumNames.add(it) }
                }
                ArtistListItemState(
                    name = artist.name,
                    coverPath = artist.coverPath,
                    albumCount = albumNames.size,
                    trackCount = artist.files.size
                )
            }.sortedBy { it.name }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.update { true }
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
            } catch (e: Exception) {
                Timber.e(e, "Artist refresh failed")
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    init {
        viewModelScope.launch {
            if (audioFileScanner.hasCachedData()) {
                Timber.d("ArtistViewModel: Using cached data, skipping initial refresh")
            } else {
                Timber.d("ArtistViewModel: No cached data, performing initial load")
                refresh(forceRefresh = false)
            }
        }
    }
}
