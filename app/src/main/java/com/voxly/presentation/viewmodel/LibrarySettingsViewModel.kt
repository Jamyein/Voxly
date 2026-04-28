package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.UiStateDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for library settings observation.
 * Handles sort options, filters, and root tab preferences.
 */
@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val uiStateDataStore: UiStateDataStore
) : ViewModel() {

    companion object {
        private const val STATE_FLOW_TIMEOUT_MS = 5000L
    }

    /**
     * File browser (All audios tab) sort option from persistent storage
     */
    val fileBrowserSortOption = uiStateDataStore.fileBrowserSortOption

    /**
     * Directory content screen sort option from persistent storage
     */
    val directoryFileSortOption = uiStateDataStore.directoryFileSortOption

    /**
     * Save file browser sort option to persistent storage
     */
    fun setFileBrowserSortOption(option: String) {
        Timber.tag("Voxly").i("LibrarySettingsViewModel: setFileBrowserSortOption")
        viewModelScope.launch {
            uiStateDataStore.setFileBrowserSortOption(option)
        }
    }

    /**
     * Save directory file sort option to persistent storage
     */
    fun setDirectoryFileSortOption(option: String) {
        Timber.tag("Voxly").i("LibrarySettingsViewModel: setDirectoryFileSortOption")
        viewModelScope.launch {
            uiStateDataStore.setDirectoryFileSortOption(option)
        }
    }

    /**
     * Save file browser root tab to persistent storage
     */
    fun setFileBrowserRootTab(tab: String) {
        Timber.tag("Voxly").i("LibrarySettingsViewModel: setFileBrowserRootTab")
        viewModelScope.launch {
            settingsDataStore.setFileBrowserRootTab(tab)
        }
    }

    /**
     * File browser root tab from persistent storage (DIRECTORIES or ALL)
     */
    val fileBrowserRootTab: StateFlow<String> = settingsDataStore.fileBrowserRootTab
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = "DIRECTORIES"
        )

    val artistSeparatorEnabled: StateFlow<Boolean> = settingsDataStore.artistSeparatorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)

    /**
     * Artist separators as Set<String> for splitArtist()
     */
    val artistSeparatorsSet: StateFlow<Set<String>> = settingsDataStore.artistSeparatorsSet
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = setOf("&", "/", "\\")
        )
}
