package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.usecase.UnifiedScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App-level ViewModel that initializes global settings watching.
 * Uses UnifiedScanManager to centralize refresh logic.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val unifiedScanManager: UnifiedScanManager
) : ViewModel() {

    init {
        // Start watching settings and auto-trigger refresh on changes
        unifiedScanManager.startWatchingSettings()
    }
}
