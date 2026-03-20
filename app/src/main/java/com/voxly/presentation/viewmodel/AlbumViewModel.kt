package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
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
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {

    // Directly use AudioFileScanner's albums - same singleton instance as LibraryViewModel
    val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        // Cancel previous refresh if still running
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
            } catch (e: Exception) {
                Timber.e(e, "Album refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
