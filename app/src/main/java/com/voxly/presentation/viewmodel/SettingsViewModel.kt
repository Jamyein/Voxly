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
import com.voxly.core.util.Constants
import javax.inject.Inject
import com.voxly.domain.model.DataSourceConfig
import com.voxly.domain.model.DataSourceType
import com.voxly.domain.model.ScanModeConstants
import com.voxly.domain.model.SourceConfigurations

/** Timeout for StateFlow sharing in milliseconds */
private const val STATE_FLOW_TIMEOUT_MS = 5000L

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
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    /**
     * Dynamic colors state
     */
    val dynamicColors: StateFlow<Boolean> = settingsDataStore.dynamicColors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    /**
     * Language tag state (null means system default)
     */
    val languageTag: StateFlow<String?> = settingsDataStore.languageTag
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = null
        )

    /**
     * Theme mode state (system | light | dark)
     */
    val themeMode: StateFlow<String> = settingsDataStore.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = ThemeConstants.MODE_SYSTEM
        )

    val appleCountryCode: StateFlow<String> = settingsDataStore.appleCountryCode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = "us"
        )

    val onlineSearchLimit: StateFlow<Int> = settingsDataStore.onlineSearchLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 25
        )

    val onlineSearchLimitMusicBrainz: StateFlow<Int> = settingsDataStore.onlineSearchLimitMusicBrainz
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 0
        )

    val onlineSearchLimitITunes: StateFlow<Int> = settingsDataStore.onlineSearchLimitITunes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 0
        )

    val onlineSearchLimitNetease: StateFlow<Int> = settingsDataStore.onlineSearchLimitNetease
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 0
        )

    val onlineSearchLimitQQMusic: StateFlow<Int> = settingsDataStore.onlineSearchLimitQQMusic
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 0
        )

    val sourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.sourceEnabledMusicBrainz
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val sourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.sourceEnabledITunes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val sourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.sourceEnabledNetease
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val sourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.sourceEnabledQQMusic
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val metadataSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val metadataSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val metadataSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val metadataSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.metadataSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)

    val lyricsSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val lyricsSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val lyricsSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val lyricsSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.lyricsSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)

    val coverSourceEnabledMusicBrainz: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledMusicBrainz
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val coverSourceEnabledITunes: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledITunes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val coverSourceEnabledNetease: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledNetease
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)
    val coverSourceEnabledQQMusic: StateFlow<Boolean> = settingsDataStore.coverSourceEnabledQQMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), true)

    val metadataSourcePriority: StateFlow<List<String>> = settingsDataStore.metadataSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = listOf("itunes", "musicbrainz", "netease", "qq_music")
        )

    val lyricsSourcePriority: StateFlow<List<String>> = settingsDataStore.lyricsSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = listOf("netease", "qq_music")
        )

    val coverSourcePriority: StateFlow<List<String>> = settingsDataStore.coverSourcePriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = listOf("itunes", "musicbrainz", "netease", "qq_music")
        )

    val loggingEnabled: StateFlow<Boolean> = settingsDataStore.loggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val fileLoggingEnabled: StateFlow<Boolean> = settingsDataStore.fileLoggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val consoleLoggingEnabled: StateFlow<Boolean> = settingsDataStore.consoleLoggingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    val crashReportingEnabled: StateFlow<Boolean> = settingsDataStore.crashReportingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    val replayGainTargetLoudness: StateFlow<Float> = settingsDataStore.replayGainTargetLoudness
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = -14f
        )

    /**
     * Scan mode state (TRACK_ONLY, ALBUM_ONLY, TRACK_AND_ALBUM)
     */
    val scanMode: StateFlow<String> = settingsDataStore.scanMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = ScanModeConstants.TRACK_ONLY
    )

    /**
     * Minimum duration filter enabled state
     */
    val minDurationFilterEnabled: StateFlow<Boolean> = settingsDataStore.minDurationFilterEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    /**
     * Minimum duration filter threshold in milliseconds
     */
    val minDurationFilterThresholdMs: StateFlow<Int> = settingsDataStore.minDurationFilterThresholdMs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = Constants.MIN_DURATION_FILTER_THRESHOLD_MS.toInt()
        )

    /**
     * Unified source configurations (new approach)
     */
    val sourceConfigurations: StateFlow<SourceConfigurations> = settingsDataStore.sourceConfigurations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = SourceConfigurations()
        )

    /**
     * Update a single source within a source type
     */
    fun updateSourceConfig(type: DataSourceType, source: DataSourceConfig) {
        viewModelScope.launch {
            settingsDataStore.updateSourceConfig(type, source)
        }
    }

    /**
     * Reorder sources within a source type
     */
    fun reorderSources(type: DataSourceType, orderedSourceIds: List<String>) {
        viewModelScope.launch {
            settingsDataStore.reorderSources(type, orderedSourceIds)
        }
    }

    /**
     * Set source enabled state
     */
    fun setSourceEnabled(type: DataSourceType, sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = sourceConfigurations.value
            val typeConfig = current.getConfig(type)
            val source = typeConfig.getSource(sourceId)
            if (source != null) {
                settingsDataStore.updateSourceConfig(type, source.copy(enabled = enabled))
            }
        }
    }

    /**
     * Set source extra option (e.g., country code for iTunes)
     */
    fun setSourceExtraOption(type: DataSourceType, sourceId: String, key: String, value: String) {
        viewModelScope.launch {
            val current = sourceConfigurations.value
            val typeConfig = current.getConfig(type)
            val source = typeConfig.getSource(sourceId)
            if (source != null) {
                val updated = source.withExtraOption(key, value)
                settingsDataStore.updateSourceConfig(type, updated)
            }
        }
    }

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

    /**
     * Set minimum duration filter enabled preference
     */
    fun setMinDurationFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setMinDurationFilterEnabled(enabled)
        }
    }

    /**
     * Set minimum duration filter threshold in milliseconds
     */
    fun setMinDurationFilterThresholdMs(thresholdMs: Int) {
        viewModelScope.launch {
            settingsDataStore.setMinDurationFilterThresholdMs(thresholdMs)
        }
    }

    // ==================== Proxy Settings ====================

    /**
     * Proxy enabled state
     */
    val proxyEnabled: StateFlow<Boolean> = settingsDataStore.proxyEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    /**
     * Proxy type state (HTTP, SOCKS)
     */
    val proxyType: StateFlow<String> = settingsDataStore.proxyType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = "HTTP"
        )

    /**
     * Proxy host state
     */
    val proxyHost: StateFlow<String> = settingsDataStore.proxyHost
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = ""
        )

    /**
     * Proxy port state
     */
    val proxyPort: StateFlow<Int> = settingsDataStore.proxyPort
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = 0
        )

    /**
     * Artist separator enabled state
     */
    val artistSeparatorEnabled: StateFlow<Boolean> = settingsDataStore.artistSeparatorEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
        )

    /**
     * Artist separators state
     */
    val artistSeparators: StateFlow<String> = settingsDataStore.artistSeparators
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = "&\\"
        )

    /**
     * Set proxy enabled preference
     */
    fun setProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setProxyEnabled(enabled)
        }
    }

    /**
     * Set proxy type preference
     */
    fun setProxyType(type: String) {
        viewModelScope.launch {
            settingsDataStore.setProxyType(type)
        }
    }

    /**
     * Set proxy host preference
     */
    fun setProxyHost(host: String) {
        viewModelScope.launch {
            settingsDataStore.setProxyHost(host)
        }
    }

    /**
     * Set proxy port preference
     */
    fun setProxyPort(port: Int) {
        viewModelScope.launch {
            settingsDataStore.setProxyPort(port)
        }
    }

    /**
     * Set artist separator enabled preference
     */
    fun setArtistSeparatorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setArtistSeparatorEnabled(enabled)
        }
    }

    /**
     * Set artist separators preference
     */
    fun setArtistSeparators(separators: String) {
        viewModelScope.launch {
            settingsDataStore.setArtistSeparators(separators)
        }
    }
}
