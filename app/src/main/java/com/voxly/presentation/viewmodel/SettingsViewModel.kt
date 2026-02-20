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

    val onlineSearchLimitMusicBrainz: StateFlow<Int> = settingsDataStore.onlineSearchLimitMusicBrainz
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val onlineSearchLimitITunes: StateFlow<Int> = settingsDataStore.onlineSearchLimitITunes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val onlineSearchLimitNetease: StateFlow<Int> = settingsDataStore.onlineSearchLimitNetease
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val onlineSearchLimitQQMusic: StateFlow<Int> = settingsDataStore.onlineSearchLimitQQMusic
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
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

    val metadataSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val metadataSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val metadataSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val metadataSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lyricsSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lyricsSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lyricsSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lyricsSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val coverSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val coverSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val coverSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val coverSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val metadataSourcePriority: StateFlow<List<String>> = settingsDataStore.metadataSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("itunes", "musicbrainz", "netease", "qq_music")
        )

    val lyricsSourcePriority: StateFlow<List<String>> = settingsDataStore.lyricsSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("itunes", "musicbrainz", "netease", "qq_music")
        )

    val coverSourcePriority: StateFlow<List<String>> = settingsDataStore.coverSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("itunes", "musicbrainz", "netease", "qq_music")
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

    val replayGainTargetLoudness: StateFlow<Float> = settingsDataStore.replayGainTargetLoudness
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = -18f
        )

    /**
     * Scan mode state (TRACK_ONLY, ALBUM_ONLY, TRACK_AND_ALBUM)
     */
    val scanMode: StateFlow<String> = settingsDataStore.scanMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "TRACK_ONLY"
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

    fun setOnlineSearchLimitMusicBrainz(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setOnlineSearchLimitMusicBrainz(limit)
        }
    }

    fun setOnlineSearchLimitITunes(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setOnlineSearchLimitITunes(limit)
        }
    }

    fun setOnlineSearchLimitNetease(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setOnlineSearchLimitNetease(limit)
        }
    }

    fun setOnlineSearchLimitQQMusic(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setOnlineSearchLimitQQMusic(limit)
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

    fun setMetadataSourceEnabledMusicBrainz(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMetadataSourceEnabledMusicBrainz(enabled) }
    }

    fun setMetadataSourceEnabledITunes(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMetadataSourceEnabledITunes(enabled) }
    }

    fun setMetadataSourceEnabledNetease(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMetadataSourceEnabledNetease(enabled) }
    }

    fun setMetadataSourceEnabledQQMusic(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMetadataSourceEnabledQQMusic(enabled) }
    }

    fun setLyricsSourceEnabledMusicBrainz(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLyricsSourceEnabledMusicBrainz(enabled) }
    }

    fun setLyricsSourceEnabledITunes(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLyricsSourceEnabledITunes(enabled) }
    }

    fun setLyricsSourceEnabledNetease(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLyricsSourceEnabledNetease(enabled) }
    }

    fun setLyricsSourceEnabledQQMusic(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLyricsSourceEnabledQQMusic(enabled) }
    }

    fun setCoverSourceEnabledMusicBrainz(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverSourceEnabledMusicBrainz(enabled) }
    }

    fun setCoverSourceEnabledITunes(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverSourceEnabledITunes(enabled) }
    }

    fun setCoverSourceEnabledNetease(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverSourceEnabledNetease(enabled) }
    }

    fun setCoverSourceEnabledQQMusic(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverSourceEnabledQQMusic(enabled) }
    }

    fun setMetadataSourcePriority(priority: List<String>) {
        viewModelScope.launch {
            settingsDataStore.setMetadataSourcePriority(priority)
        }
    }

    fun setLyricsSourcePriority(priority: List<String>) {
        viewModelScope.launch {
            settingsDataStore.setLyricsSourcePriority(priority)
        }
    }

    fun setCoverSourcePriority(priority: List<String>) {
        viewModelScope.launch {
            settingsDataStore.setCoverSourcePriority(priority)
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

    fun setReplayGainTargetLoudness(loudness: Float) {
        viewModelScope.launch {
            settingsDataStore.setReplayGainTargetLoudness(loudness)
        }
    }

    /**
     * Set scan mode preference
     */
    fun setScanMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setScanMode(mode)
        }
    }
}
