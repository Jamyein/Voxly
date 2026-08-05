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
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** Timeout for StateFlow sharing in milliseconds */
private const val STATE_FLOW_TIMEOUT_MS = 5000L

/**
 * Transient UI-only state for the source priority dialog: which dialog is open and the
 * current visual order of source IDs while dragging. Enabled flags / extra options are
 * intentionally NOT copied here — [dragDialogState] derives them live from DataStore so
 * switch toggles reflect immediately in the open dialog.
 */
data class DragInteraction(
    val sourceType: DataSourceType? = null,
    val order: List<String> = emptyList(),
    val draggedIndex: Int? = null,
    val dragOffset: Float = 0f,
    val originalDragIndex: Int? = null
)

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
     * Transient drag interaction for the source priority dialog. Only the open type and
     * the in-drag source ID order live here; item details are derived in [dragDialogState].
     */
    private val _dragInteraction = MutableStateFlow(DragInteraction())

    // ==================== Settings Data (from DataStore) ====================
    // Individual properties below are used in methods or UI. All other settings
    // are accessed through the combined uiState flow which reads from DataStore
    // directly, avoiding redundant stateIn() wrappers.

    /**
     * Unified source configurations (used synchronously in source-enabled methods)
     */
    val sourceConfigurations: StateFlow<SourceConfigurations> = settingsDataStore.sourceConfigurations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = SourceConfigurations()
        )

    /**
     * Drag dialog state — derived (not snapshotted) from persisted source configurations
     * plus the transient drag interaction. Deriving keeps the open dialog's switches and
     * iTunes country in sync with DataStore immediately, without re-opening the dialog.
     */
    val dragDialogState: StateFlow<DragDialogState?> = combine(
        sourceConfigurations,
        _dragInteraction
    ) { config, interaction ->
        val type = interaction.sourceType ?: return@combine null
        val typeConfig = config.getConfig(type)
        DragDialogState(
            sourceType = type,
            sources = interaction.order.map { sourceId ->
                val source = typeConfig.getSource(sourceId)
                DragDialogSourceItem(
                    sourceId = sourceId,
                    enabled = source?.enabled ?: false,
                    order = source?.order ?: 0,
                    extraOptions = source?.extraOptions ?: emptyMap()
                )
            },
            draggedIndex = interaction.draggedIndex,
            dragOffset = interaction.dragOffset,
            originalDragIndex = interaction.originalDragIndex
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = null
    )

    /**
     * Artist separators as Set<String> for UI layer (collected directly in SettingsScreen)
     */
    val artistSeparatorsSet: StateFlow<Set<String>> = settingsDataStore.artistSeparatorsSet
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = setOf("&", "/", "\\")
        )

    /**
     * Update a single source within a source type
     */
    fun updateSourceConfig(type: DataSourceType, source: DataSourceConfig) {
        viewModelScope.launch {
            Timber.tag("Voxly").i("SettingsViewModel: settings saved")
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
     * Set metadata editor dynamic album color preference
     */
    fun setMetadataEditorDynamicAlbumColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setMetadataEditorDynamicAlbumColor(enabled)
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
     * Open the priority dialog for a source type. Snapshots only the transient ordering
     * (source IDs); enabled/extraOptions stay derived from DataStore via [dragDialogState].
     */
    fun openDialog(type: DataSourceType) {
        val order = sourceConfigurations.value.getConfig(type)
            .sources.sortedBy { it.order }.map { it.sourceId }
        _dragInteraction.value = DragInteraction(sourceType = type, order = order)
    }

    /**
     * Clear drag dialog state
     */
    fun clearDragDialogState() {
        _dragInteraction.value = DragInteraction()
    }

    /**
     * Start dragging an item
     */
    fun startDragging(index: Int) {
        _dragInteraction.update {
            it.copy(
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
        val current = _dragInteraction.value
        val draggedIdx = current.draggedIndex ?: return

        val newDragOffset = current.dragOffset + offset
        val offsetInItems = newDragOffset / itemHeightPx
        val newTargetIndex = (draggedIdx + offsetInItems.toInt())
            .coerceIn(0, current.order.lastIndex)

        if (newTargetIndex != draggedIdx && newTargetIndex in current.order.indices) {
            // Swap items in the list
            val newList = current.order.toMutableList()
            val item = newList.removeAt(draggedIdx)
            newList.add(newTargetIndex, item)

            _dragInteraction.update {
                current.copy(
                    order = newList,
                    draggedIndex = newTargetIndex,
                    dragOffset = 0f
                )
            }
        } else {
            _dragInteraction.update { current.copy(dragOffset = newDragOffset) }
        }
    }

    /**
     * End dragging and persist the reordered list
     */
    fun endDragging() {
        val current = _dragInteraction.value
        val type = current.sourceType ?: return
        val originalIdx = current.originalDragIndex
        val currentIdx = current.draggedIndex

        // Persist if order changed
        if (originalIdx != null && originalIdx != currentIdx) {
            reorderSources(type, current.order)
        }

        _dragInteraction.update {
            current.copy(
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
        val current = _dragInteraction.value
        val originalIdx = current.originalDragIndex

        if (originalIdx != null) {
            // Re-fetch original order from persistent storage
            current.sourceType?.let { openDialog(it) }
        } else {
            _dragInteraction.update {
                current.copy(
                    draggedIndex = null,
                    dragOffset = 0f,
                    originalDragIndex = null
                )
            }
        }
    }

    // ==================== Proxy, Artist, Lyrics & UI Settings ====================

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
     * Set floating bottom navigation bar preference
     */
    fun setFloatingBottomNavEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setFloatingBottomNavEnabled(enabled)
        }
    }

    /**
     * Combined UI state for the settings screen.
     * Replaces 32 individual collectAsState() calls with a single state holder.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.dynamicColors,
        settingsDataStore.metadataEditorDynamicAlbumColor,
        settingsDataStore.languageTag,
        settingsDataStore.themeMode,
        settingsDataStore.appleCountryCode,
        settingsDataStore.onlineSearchLimit,
        settingsDataStore.onlineSearchLimitMusicBrainz,
        settingsDataStore.onlineSearchLimitITunes,
        settingsDataStore.onlineSearchLimitNetease,
        settingsDataStore.onlineSearchLimitQQMusic,
        sourceConfigurations,
        settingsDataStore.loggingEnabled,
        settingsDataStore.fileLoggingEnabled,
        settingsDataStore.consoleLoggingEnabled,
        settingsDataStore.crashReportingEnabled,
        settingsDataStore.replayGainTargetLoudness,
        settingsDataStore.scanMode,
        settingsDataStore.minDurationFilterEnabled,
        settingsDataStore.lyricsTimestampFormatEnabled,
        settingsDataStore.floatingBottomNavEnabled
    ) { values ->
        SettingsUiState(
            dynamicColors = values[0] as Boolean,
            metadataEditorDynamicAlbumColor = values[1] as Boolean,
            savedLanguageTag = values[2] as String?,
            themeMode = values[3] as String,
            appleCountryCode = values[4] as String,
            onlineSearchLimit = values[5] as Int,
            onlineSearchLimitMusicBrainz = values[6] as Int,
            onlineSearchLimitITunes = values[7] as Int,
            onlineSearchLimitNetease = values[8] as Int,
            onlineSearchLimitQQMusic = values[9] as Int,
            sourceConfigurations = values[10] as SourceConfigurations,
            loggingEnabled = values[11] as Boolean,
            fileLoggingEnabled = values[12] as Boolean,
            consoleLoggingEnabled = values[13] as Boolean,
            crashReportingEnabled = values[14] as Boolean,
            replayGainTargetLoudness = values[15] as Float,
            scanMode = values[16] as String,
            minDurationFilterEnabled = values[17] as Boolean,
            lyricsTimestampFormatEnabled = values[18] as Boolean,
            floatingBottomNavEnabled = values[19] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS), SettingsUiState())
}
