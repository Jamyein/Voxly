package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.usecase.MusicLibraryRefreshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App-level ViewModel that triggers library refresh when settings change.
 * Uses MusicLibraryRefreshManager to centralize refresh logic.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val refreshManager: MusicLibraryRefreshManager
) : ViewModel() {

    init {
        // Start watching settings and auto-trigger refresh on changes
        refreshManager.startWatchingSettings()
    }
}
