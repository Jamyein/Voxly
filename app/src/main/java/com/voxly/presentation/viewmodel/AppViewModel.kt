package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level ViewModel that observes scan settings changes from anywhere in the app.
 * This ensures that when settings are changed in Settings screen, the library
 * refresh is triggered even if FileBrowser hasn't been visited yet.
 * 
 * Uses @HiltViewModel for dependency injection via hiltViewModel().
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    // Track previous values to detect changes
    private var lastMinDurationFilterEnabled = false
    private var lastWhitelistEnabled = false
    private var lastBlacklistEnabled = false

    // Event to trigger library refresh
    private val _libraryRefreshEvent = MutableStateFlow(0)
    val libraryRefreshEvent: StateFlow<Int> = _libraryRefreshEvent.asStateFlow()

    // Observe settings at app level
    private val minDurationFilterEnabled = settingsDataStore.minDurationFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val whitelistEnabled = settingsDataStore.whitelistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val blacklistEnabled = settingsDataStore.blacklistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        observeScanSettingsChanges()
    }

    private fun observeScanSettingsChanges() {
        viewModelScope.launch {
            // Observe min duration filter enabled changes
            launch {
                minDurationFilterEnabled.collect { enabled ->
                    if (lastMinDurationFilterEnabled != enabled) {
                        lastMinDurationFilterEnabled = enabled
                        triggerLibraryRefresh()
                    }
                }
            }

            // Observe whitelist enabled changes
            launch {
                whitelistEnabled.collect { enabled ->
                    if (lastWhitelistEnabled != enabled) {
                        lastWhitelistEnabled = enabled
                        triggerLibraryRefresh()
                    }
                }
            }

            // Observe blacklist enabled changes
            launch {
                blacklistEnabled.collect { enabled ->
                    if (lastBlacklistEnabled != enabled) {
                        lastBlacklistEnabled = enabled
                        triggerLibraryRefresh()
                    }
                }
            }
        }
    }

    private fun triggerLibraryRefresh() {
        _libraryRefreshEvent.value = _libraryRefreshEvent.value + 1
    }
}
