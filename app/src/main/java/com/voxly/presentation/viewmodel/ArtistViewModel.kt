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
        .distinctUntilChanged { oldList, newList ->
            // Compare by artist names and sizes to prevent unnecessary emissions
            if (oldList.size != newList.size) return@distinctUntilChanged false
            oldList.map { it.name to it.albums.size to it.files.size } ==
                    newList.map { it.name to it.albums.size to it.files.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val artistListItems: StateFlow<List<ArtistListItemState>> = artists
        .map { artistGroups ->
            artistGroups.map { artist ->
                // Select best cover file: prefer MediaStore album ID for faster loading
                val coverFile = artist.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ?: artist.files.firstOrNull()
                ArtistListItemState(
                    name = artist.name,
                    coverPath = coverFile?.path,
                    coverAlbumId = coverFile?.mediaStoreAlbumId,
                    albumCount = artist.albums.size,
                    trackCount = artist.files.size
                )
            }
        }
        .distinctUntilChanged { oldList, newList ->
            // Compare by essential fields to prevent unnecessary emissions
            if (oldList.size != newList.size) return@distinctUntilChanged false
            oldList.map { it.name to it.coverAlbumId to it.albumCount to it.trackCount } ==
                    newList.map { it.name to it.coverAlbumId to it.albumCount to it.trackCount }
        }
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
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
            } catch (e: Exception) {
                Timber.e(e, "Artist refresh failed")
            } finally {
                _isRefreshing.value = false
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
