package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.model.ArtistGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin ViewModel layer for ArtistScreen.
 * Uses AudioFileScanner directly for data (same singleton instance as LibraryViewModel).
 * The repeatOnLifecycle bug was fixed by removing it - screens passively collect data.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {

    // Directly use AudioFileScanner's artists - same singleton instance as LibraryViewModel
    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists

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
                Timber.e(e, "Artist refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
