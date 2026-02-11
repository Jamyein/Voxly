package com.mp3tag.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.data.local.SettingsDataStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
     * Language tag state (null means system default)
     */
    val languageTag: StateFlow<String?> = settingsDataStore.languageTag
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Theme mode state (system | light | dark)
     */
    val themeMode: StateFlow<String> = settingsDataStore.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "system"
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

    /**
     * Set theme mode preference
     */
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    /**
     * Set language preference and apply it immediately
     * @param tag Language tag (e.g., "en", "zh-CN") or null for system default
     */
    fun setLanguage(tag: String?) {
        viewModelScope.launch {
            // Save to DataStore
            settingsDataStore.setLanguageTag(tag)
            
            // Apply immediately using AppCompatDelegate
            AppCompatDelegate.setApplicationLocales(
                if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
            )
        }
    }
}
