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
import com.voxly.presentation.screens.settings.SettingsUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Timeout for StateFlow sharing in milliseconds */
private const val STATE_FLOW_TIMEOUT_MS = 5000L

/**
 * UI state for the drag-to-reorder source priority dialog.
 */
data class DragDialogState(
    val sourceType: DataSourceType,
    val sources: List<DragDialogSourceItem>,
    val draggedIndex: Int? = null,
    val dragOffset: Float = 0f,
    val originalDragIndex: Int? = null
)

/**
 * Individual source item within the drag dialog.
 */
data class DragDialogSourceItem(
    val sourceId: String,
    val enabled: Boolean,
    val order: Int,
    val extraOptions: Map<String, String> = emptyMap()
)

/**
 * ViewModel for the settings screen.
 * Manages user preferences with persistent storage.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    /**
     * Drag dialog state - manages UI state for the draggable source priority dialog
     */
    private val _dragDialogState = MutableStateFlow<DragDialogState?>(null)
    val dragDialogState: StateFlow<DragDialogState?> = _dragDialogState.asStateFlow()

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
     * ReplayGain clip mode state (n=none, p=positive, a=always)
     */
    val replayGainClipMode: StateFlow<String> = settingsDataStore.replayGainClipMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = "p"
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
     * Set ReplayGain clip mode preference
     */
    fun setReplayGainClipMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setReplayGainClipMode(mode)
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

    // ==================== Drag Dialog State Management ====================

    /**
     * Initialize drag dialog state from source configurations
     */
    fun initDragDialogState(type: DataSourceType) {
        val config = sourceConfigurations.value.getConfig(type)
        val sortedSources = config.sources.sortedBy { it.order }
        _dragDialogState.update {
            DragDialogState(
                sourceType = type,
                sources = sortedSources.map {
                    DragDialogSourceItem(
                        sourceId = it.sourceId,
                        enabled = it.enabled,
                        order = it.order,
                        extraOptions = it.extraOptions
                    )
                }
            )
        }
    }

    /**
     * Clear drag dialog state
     */
    fun clearDragDialogState() {
        _dragDialogState.update { null }
    }

    /**
     * Start dragging an item
     */
    fun startDragging(index: Int) {
        _dragDialogState.update {
            it?.copy(
                originalDragIndex = index,
                draggedIndex = index,
                dragOffset = 0f
            )
        }
    }

    /**
     * Update drag offset and swap items if needed
     */
    fun updateDragOffset(offset: Float, itemHeightPx: Float) {
        val currentState = _dragDialogState.value ?: return
        val draggedIdx = currentState.draggedIndex ?: return

        val newDragOffset = currentState.dragOffset + offset
        val offsetInItems = newDragOffset / itemHeightPx
        val newTargetIndex = (draggedIdx + offsetInItems.toInt())
            .coerceIn(0, currentState.sources.lastIndex)

        if (newTargetIndex != draggedIdx && newTargetIndex in currentState.sources.indices) {
            // Swap items in the list
            val newList = currentState.sources.toMutableList()
            val item = newList.removeAt(draggedIdx)
            newList.add(newTargetIndex, item)

            _dragDialogState.update {
                currentState.copy(
                    sources = newList,
                    draggedIndex = newTargetIndex,
                    dragOffset = 0f
                )
            }
        } else {
            _dragDialogState.update { currentState.copy(dragOffset = newDragOffset) }
        }
    }

    /**
     * End dragging and persist the reordered list
     */
    fun endDragging() {
        val currentState = _dragDialogState.value ?: return
        val originalIdx = currentState.originalDragIndex
        val currentIdx = currentState.draggedIndex

        // Persist if order changed
        if (originalIdx != null && originalIdx != currentIdx) {
            val reorderedIds = currentState.sources.map { it.sourceId }
            reorderSources(currentState.sourceType, reorderedIds)
        }

        _dragDialogState.update {
            currentState.copy(
                draggedIndex = null,
                dragOffset = 0f,
                originalDragIndex = null
            )
        }
    }

    /**
     * Cancel dragging and revert to original order
     */
    fun cancelDragging() {
        val currentState = _dragDialogState.value ?: return
        val originalIdx = currentState.originalDragIndex

        if (originalIdx != null) {
            // Re-fetch original order from persistent storage
            initDragDialogState(currentState.sourceType)
        } else {
            _dragDialogState.update {
                currentState.copy(
                    draggedIndex = null,
                    dragOffset = 0f,
                    originalDragIndex = null
                )
            }
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
            initialValue = """["&","/","\\"]"""
        )

    /**
     * Artist separators as Set<String> for UI layer (tag display)
     */
    val artistSeparatorsSet: StateFlow<Set<String>> = settingsDataStore.artistSeparatorsSet
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = setOf("&", "/", "\\")
        )

    /**
     * Lyrics timestamp format enabled state (3-digit to 2-digit format)
     */
    val lyricsTimestampFormatEnabled: StateFlow<Boolean> = settingsDataStore.lyricsTimestampFormatEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = true
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

    /**
     * Set artist separators from UI (Set version — preferred)
     */
    fun setArtistSeparators(separators: Set<String>) {
        viewModelScope.launch {
            settingsDataStore.setArtistSeparators(separators)
        }
    }

    /**
     * Set lyrics timestamp format enabled preference
     */
    fun setLyricsTimestampFormatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLyricsTimestampFormatEnabled(enabled)
        }
    }

    /**
     * Combined UI state for the settings screen.
     * Replaces 32 individual collectAsState() calls with a single state holder.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        dynamicColors,
        languageTag,
        themeMode,
        appleCountryCode,
        onlineSearchLimit,
        onlineSearchLimitMusicBrainz,
        onlineSearchLimitITunes,
        onlineSearchLimitNetease,
        onlineSearchLimitQQMusic,
        sourceConfigurations,
        loggingEnabled,
        fileLoggingEnabled,
        consoleLoggingEnabled,
        crashReportingEnabled,
        replayGainTargetLoudness,
        scanMode,
        minDurationFilterEnabled,
        lyricsTimestampFormatEnabled
    ) { values ->
        SettingsUiState(
            dynamicColors = values[0] as Boolean,
            savedLanguageTag = values[1] as String?,
            themeMode = values[2] as String,
            appleCountryCode = values[3] as String,
            onlineSearchLimit = values[4] as Int,
            onlineSearchLimitMusicBrainz = values[5] as Int,
            onlineSearchLimitITunes = values[6] as Int,
            onlineSearchLimitNetease = values[7] as Int,
            onlineSearchLimitQQMusic = values[8] as Int,
            sourceConfigurations = values[9] as SourceConfigurations,
            loggingEnabled = values[10] as Boolean,
            fileLoggingEnabled = values[11] as Boolean,
            consoleLoggingEnabled = values[12] as Boolean,
            crashReportingEnabled = values[13] as Boolean,
            replayGainTargetLoudness = values[14] as Float,
            scanMode = values[15] as String,
            minDurationFilterEnabled = values[16] as Boolean,
            lyricsTimestampFormatEnabled = values[17] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), SettingsUiState())
}
