package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
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

    val appleCountryCode: StateFlow<String> = settingsDataStore.appleCountryCode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "us"
        )

    val onlineSearchLimit: StateFlow<Int> = settingsDataStore.onlineSearchLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 25
        )

    val sourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.sourceEnabledMusicBrainz
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val sourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.sourceEnabledITunes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val sourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.sourceEnabledNetease
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val sourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.sourceEnabledQQMusic
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val loggingEnabled: StateFlow<Boolean> = settingsDataStore.loggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val fileLoggingEnabled: StateFlow<Boolean> = settingsDataStore.fileLoggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val consoleLoggingEnabled: StateFlow<Boolean> = settingsDataStore.consoleLoggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val crashReportingEnabled: StateFlow<Boolean> = settingsDataStore.crashReportingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
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

    fun setAppleCountryCode(code: String) {
        viewModelScope.launch {
            settingsDataStore.setAppleCountryCode(code)
        }
    }

    fun setOnlineSearchLimit(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setOnlineSearchLimit(limit)
        }
    }

    fun setSourceEnabledMusicBrainz(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSourceEnabledMusicBrainz(enabled)
        }
    }

    fun setSourceEnabledITunes(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSourceEnabledITunes(enabled)
        }
    }

    fun setSourceEnabledNetease(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSourceEnabledNetease(enabled)
        }
    }

    fun setSourceEnabledQQMusic(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSourceEnabledQQMusic(enabled)
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLoggingEnabled(enabled)
        }
    }

    fun setFileLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setFileLoggingEnabled(enabled)
        }
    }

    fun setConsoleLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setConsoleLoggingEnabled(enabled)
        }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setCrashReportingEnabled(enabled)
        }
    }
}
