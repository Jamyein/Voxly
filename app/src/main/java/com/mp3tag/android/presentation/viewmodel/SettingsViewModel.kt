package com.mp3tag.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 * Manages user preferences with persistent storage.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    /**
     * Dark theme state
     */
    val darkTheme: StateFlow<Boolean> = settingsDataStore.darkTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Dynamic colors state
     */
    val dynamicColors: StateFlow<Boolean> = settingsDataStore.dynamicColors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Scan quality state
     */
    val scanQuality: StateFlow<String> = settingsDataStore.scanQuality
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Normal"
        )

    /**
     * Set dark theme preference
     */
    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDarkTheme(enabled)
        }
    }

    /**
     * Set dynamic colors preference
     */
    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDynamicColors(enabled)
        }
    }

    /**
     * Set scan quality preference
     */
    fun setScanQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setScanQuality(quality)
        }
    }
}
