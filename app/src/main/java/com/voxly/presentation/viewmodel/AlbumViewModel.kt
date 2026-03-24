package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.UiStateDataStore
import com.voxly.domain.model.AlbumGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin ViewModel layer for AlbumScreen.
 * Uses AudioFileScanner directly for data (same singleton instance as LibraryViewModel).
 * The repeatOnLifecycle bug was fixed by removing it - screens passively collect data.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val uiStateDataStore: UiStateDataStore
) : ViewModel() {

    // Directly use AudioFileScanner's albums - same singleton instance as LibraryViewModel
    val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Sort option from persistent storage
    val sortOption = uiStateDataStore.albumSortOption

    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        // Cancel previous refresh if still running
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                Timber.d("AlbumViewModel.refresh: starting loadAudioFiles(isIncremental=${!forceRefresh})")
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
                Timber.d("AlbumViewModel.refresh: loadAudioFiles completed")
            } catch (e: Exception) {
                Timber.e(e, "Album refresh failed")
            } finally {
                Timber.d("AlbumViewModel.refresh: finally block, setting isRefreshing=false")
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Save album sort option to persistent storage
     */
    fun setSortOption(option: String) {
        viewModelScope.launch {
            uiStateDataStore.setAlbumSortOption(option)
        }
    }
}
