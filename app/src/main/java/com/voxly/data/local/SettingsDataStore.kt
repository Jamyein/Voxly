package com.voxly.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.voxly.domain.model.DataSourceConfig
import com.voxly.domain.model.DataSourceType
import com.voxly.domain.model.SourceConfigurations
import com.voxly.domain.model.SourceTypeConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore manager for application settings.
 * Provides persistent storage for user preferences.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_DIRECTORY_URIS = stringPreferencesKey("selected_directory_uris")
        val APPLE_COUNTRY_CODE = stringPreferencesKey("apple_country_code")
        val ONLINE_SEARCH_LIMIT = intPreferencesKey("online_search_limit")
        val ONLINE_SEARCH_LIMIT_MUSICBRAINZ = intPreferencesKey("online_search_limit_musicbrainz")
        val ONLINE_SEARCH_LIMIT_ITUNES = intPreferencesKey("online_search_limit_itunes")
        val ONLINE_SEARCH_LIMIT_NETEASE = intPreferencesKey("online_search_limit_netease")
        val ONLINE_SEARCH_LIMIT_QQ_MUSIC = intPreferencesKey("online_search_limit_qq_music")
        val SOURCE_ENABLED_MUSICBRAINZ = booleanPreferencesKey("source_enabled_musicbrainz")
        val SOURCE_ENABLED_ITUNES = booleanPreferencesKey("source_enabled_itunes")
        val SOURCE_ENABLED_NETEASE = booleanPreferencesKey("source_enabled_netease")
        val SOURCE_ENABLED_QQ_MUSIC = booleanPreferencesKey("source_enabled_qq_music")
        val METADATA_SOURCE_ENABLED_MUSICBRAINZ = booleanPreferencesKey("metadata_source_enabled_musicbrainz")
        val METADATA_SOURCE_ENABLED_ITUNES = booleanPreferencesKey("metadata_source_enabled_itunes")
        val METADATA_SOURCE_ENABLED_NETEASE = booleanPreferencesKey("metadata_source_enabled_netease")
        val METADATA_SOURCE_ENABLED_QQ_MUSIC = booleanPreferencesKey("metadata_source_enabled_qq_music")
        val LYRICS_SOURCE_ENABLED_MUSICBRAINZ = booleanPreferencesKey("lyrics_source_enabled_musicbrainz")
        val LYRICS_SOURCE_ENABLED_ITUNES = booleanPreferencesKey("lyrics_source_enabled_itunes")
        val LYRICS_SOURCE_ENABLED_NETEASE = booleanPreferencesKey("lyrics_source_enabled_netease")
        val LYRICS_SOURCE_ENABLED_QQ_MUSIC = booleanPreferencesKey("lyrics_source_enabled_qq_music")
        val COVER_SOURCE_ENABLED_MUSICBRAINZ = booleanPreferencesKey("cover_source_enabled_musicbrainz")
        val COVER_SOURCE_ENABLED_ITUNES = booleanPreferencesKey("cover_source_enabled_itunes")
        val COVER_SOURCE_ENABLED_NETEASE = booleanPreferencesKey("cover_source_enabled_netease")
        val COVER_SOURCE_ENABLED_QQ_MUSIC = booleanPreferencesKey("cover_source_enabled_qq_music")
        val PRIORITY_METADATA_SOURCES = stringPreferencesKey("priority_metadata_sources")
        val PRIORITY_LYRICS_SOURCES = stringPreferencesKey("priority_lyrics_sources")
        val PRIORITY_COVER_SOURCES = stringPreferencesKey("priority_cover_sources")
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val FILE_LOGGING_ENABLED = booleanPreferencesKey("file_logging_enabled")
        val CONSOLE_LOGGING_ENABLED = booleanPreferencesKey("console_logging_enabled")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val REPLAY_GAIN_TARGET_LOUDNESS = floatPreferencesKey("replay_gain_target_loudness")
        val SCAN_MODE = stringPreferencesKey("scan_mode")
        val REPLAY_GAIN_CLIP_MODE = stringPreferencesKey("replay_gain_clip_mode")
        val MIN_DURATION_FILTER_ENABLED = booleanPreferencesKey("min_duration_filter_enabled")
        val MIN_DURATION_FILTER_THRESHOLD_MS = intPreferencesKey("min_duration_filter_threshold_ms")
        val SOURCE_CONFIGURATIONS = stringPreferencesKey("source_configurations")
        val SOURCE_CONFIGURATIONS_MIGRATED = booleanPreferencesKey("source_configurations_migrated")
        val WHITELIST_ENABLED = booleanPreferencesKey("whitelist_enabled")
        val BLACKLIST_ENABLED = booleanPreferencesKey("blacklist_enabled")
        val BLACKLIST_DIRECTORY_URIS = stringPreferencesKey("blacklist_directory_uris")
        // Proxy settings
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        // Artist separator settings
        val ARTIST_SEPARATOR_ENABLED = booleanPreferencesKey("artist_separator_enabled")
        val ARTIST_SEPARATORS = stringPreferencesKey("artist_separators")
        // Lyrics timestamp format settings
        val LYRICS_TIMESTAMP_FORMAT_ENABLED = booleanPreferencesKey("lyrics_timestamp_format_enabled")
        // File browser root tab setting
        val FILE_BROWSER_ROOT_TAB = stringPreferencesKey("file_browser_root_tab")
    }

    /**
     * Dark theme preference flow
     */
    val darkTheme: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DARK_THEME] ?: false
        }

    /**
     * Dynamic colors preference flow
     */
    val dynamicColors: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLORS] ?: true
        }

    /**
     * Theme mode preference flow (system | light | dark)
     */
    val themeMode: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[THEME_MODE] ?: "system"
        }

    /**
     * Language tag preference flow (null means system default)
     */
    val languageTag: Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LANGUAGE_TAG]
        }

    /**
     * Selected directory URI list for file browser multi-folder mode.
     */
    val selectedDirectoryUris: Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_DIRECTORY_URIS]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    /**
     * Apple Music country code preference flow (ISO 2 code).
     */
    val appleCountryCode: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[APPLE_COUNTRY_CODE] ?: "us"
        }

    /**
     * Online search result limit preference flow.
     */
    val onlineSearchLimit: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeOnlineSearchLimit(preferences[ONLINE_SEARCH_LIMIT] ?: 25)
        }

    /**
     * Per-source online search result limit preference flows.
     */
    val onlineSearchLimitMusicBrainz: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeOnlineSearchLimit(preferences[ONLINE_SEARCH_LIMIT_MUSICBRAINZ] ?: 0)
        }

    val onlineSearchLimitITunes: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeOnlineSearchLimit(preferences[ONLINE_SEARCH_LIMIT_ITUNES] ?: 0)
        }

    val onlineSearchLimitNetease: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeOnlineSearchLimit(preferences[ONLINE_SEARCH_LIMIT_NETEASE] ?: 0)
        }

    val onlineSearchLimitQQMusic: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeOnlineSearchLimit(preferences[ONLINE_SEARCH_LIMIT_QQ_MUSIC] ?: 0)
        }

    val sourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SOURCE_ENABLED_MUSICBRAINZ] ?: true
        }

    val sourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SOURCE_ENABLED_ITUNES] ?: true
        }

    val sourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SOURCE_ENABLED_NETEASE] ?: true
        }

    val sourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SOURCE_ENABLED_QQ_MUSIC] ?: true
        }

    val metadataSourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_MUSICBRAINZ] ?: true
        }

    val metadataSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_ITUNES] ?: true
        }

    val metadataSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_NETEASE] ?: true
        }

    val metadataSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_QQ_MUSIC] ?: true
        }

    val lyricsSourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_MUSICBRAINZ] ?: true
        }

    val lyricsSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_ITUNES] ?: true
        }

    val lyricsSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_NETEASE] ?: true
        }

    val lyricsSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_QQ_MUSIC] ?: true
        }

    val coverSourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_MUSICBRAINZ] ?: true
        }

    val coverSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_ITUNES] ?: true
        }

    val coverSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_NETEASE] ?: true
        }

    val coverSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_QQ_MUSIC] ?: true
        }

    val metadataSourcePriority: Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            parsePriority(preferences[PRIORITY_METADATA_SOURCES], defaultSourcePriority())
        }

    val lyricsSourcePriority: Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            parsePriority(preferences[PRIORITY_LYRICS_SOURCES], defaultSourcePriority())
        }

    val coverSourcePriority: Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            parsePriority(preferences[PRIORITY_COVER_SOURCES], defaultSourcePriority())
        }

    val loggingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LOGGING_ENABLED] ?: true
        }

    val fileLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FILE_LOGGING_ENABLED] ?: true
        }

    val consoleLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CONSOLE_LOGGING_ENABLED] ?: true
        }

    val crashReportingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CRASH_REPORTING_ENABLED] ?: true
        }

    /**
     * ReplayGain target loudness preference flow (in LUFS, default -18.0)
     * foobar2000 modern default is -18 LUFS
     */
    val replayGainTargetLoudness: Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REPLAY_GAIN_TARGET_LOUDNESS] ?: -18f
        }

    /**
     * ReplayGain scan mode preference flow (Track Only, Album Only, Track & Album)
     */
    val scanMode: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SCAN_MODE] ?: "TRACK_ONLY"
        }

    /**
     * ReplayGain clip mode preference flow (n=none, p=positive, a=always)
     */
    val replayGainClipMode: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REPLAY_GAIN_CLIP_MODE] ?: "p"
        }

    /**
     * Minimum duration filter enabled preference flow
     */
    val minDurationFilterEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MIN_DURATION_FILTER_ENABLED] ?: false
        }

    /**
     * Minimum duration filter threshold (in milliseconds) preference flow
     */
    val minDurationFilterThresholdMs: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MIN_DURATION_FILTER_THRESHOLD_MS] ?: 60000
        }

    /**
     * Whitelist mode enabled preference flow
     */
    val whitelistEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[WHITELIST_ENABLED] ?: false
        }

    /**
     * Blacklist mode enabled preference flow
     */
    val blacklistEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[BLACKLIST_ENABLED] ?: false
        }

    /**
     * Blacklist directory URI list flow
     */
    val blacklistDirectoryUris: Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            preferences[BLACKLIST_DIRECTORY_URIS]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    // Json instance for serialization (Kotlinx Serialization)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Cache for migrated source configurations to avoid repeated migration work
    @Volatile
    private var cachedSourceConfigurations: SourceConfigurations? = null

    /**
     * Unified source configurations flow (new approach - stores enabled, priority, and extra options together)
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sourceConfigurations: Flow<SourceConfigurations> = context.settingsDataStore.data
        .map { preferences ->
            val jsonString = preferences[SOURCE_CONFIGURATIONS]
            if (!jsonString.isNullOrBlank()) {
                try {
                    return@map json.decodeFromString<SourceConfigurations>(jsonString)
                } catch (e: Exception) {
                    // Fall through to migration
                }
            }
            // Use cached migration result if available to avoid repeated work
            cachedSourceConfigurations?.let { return@map it }
            // Run migration and cache the result
            migrateToNewFormat(preferences).also { cachedSourceConfigurations = it }
        }
        .distinctUntilChanged()

    /**
     * Migrate from old format to new unified format
     */
    private fun migrateToNewFormat(preferences: Preferences): SourceConfigurations {
        val metadataPriority = parsePriority(preferences[PRIORITY_METADATA_SOURCES], defaultSourcePriority())
        val lyricsPriority = parsePriority(preferences[PRIORITY_LYRICS_SOURCES], lyricsDefaultSourcePriority())
        val coverPriority = parsePriority(preferences[PRIORITY_COVER_SOURCES], defaultSourcePriority())

        val appleCountryCode = preferences[APPLE_COUNTRY_CODE] ?: "us"

        // Build metadata sources
        val metadataSources = metadataPriority.mapIndexed { index, sourceId ->
            val enabled = when (sourceId) {
                "musicbrainz" -> preferences[METADATA_SOURCE_ENABLED_MUSICBRAINZ] ?: true
                "itunes" -> preferences[METADATA_SOURCE_ENABLED_ITUNES] ?: true
                "netease" -> preferences[METADATA_SOURCE_ENABLED_NETEASE] ?: true
                "qq_music" -> preferences[METADATA_SOURCE_ENABLED_QQ_MUSIC] ?: true
                else -> true
            }
            val extraOptions = if (sourceId == "itunes") {
                mapOf("countryCode" to appleCountryCode)
            } else {
                emptyMap()
            }
            DataSourceConfig(sourceId = sourceId, enabled = enabled, order = index, extraOptions = extraOptions)
        }

        // Build lyrics sources
        val lyricsSources = lyricsPriority.mapIndexed { index, sourceId ->
            val enabled = when (sourceId) {
                "musicbrainz" -> preferences[LYRICS_SOURCE_ENABLED_MUSICBRAINZ] ?: true
                "itunes" -> preferences[LYRICS_SOURCE_ENABLED_ITUNES] ?: true
                "netease" -> preferences[LYRICS_SOURCE_ENABLED_NETEASE] ?: true
                "qq_music" -> preferences[LYRICS_SOURCE_ENABLED_QQ_MUSIC] ?: true
                else -> true
            }
            DataSourceConfig(sourceId = sourceId, enabled = enabled, order = index)
        }

        // Build cover sources
        val coverSources = coverPriority.mapIndexed { index, sourceId ->
            val enabled = when (sourceId) {
                "musicbrainz" -> preferences[COVER_SOURCE_ENABLED_MUSICBRAINZ] ?: true
                "itunes" -> preferences[COVER_SOURCE_ENABLED_ITUNES] ?: true
                "netease" -> preferences[COVER_SOURCE_ENABLED_NETEASE] ?: true
                "qq_music" -> preferences[COVER_SOURCE_ENABLED_QQ_MUSIC] ?: true
                else -> true
            }
            val extraOptions = if (sourceId == "itunes") {
                mapOf("countryCode" to appleCountryCode)
            } else {
                emptyMap()
            }
            DataSourceConfig(sourceId = sourceId, enabled = enabled, order = index, extraOptions = extraOptions)
        }

        return SourceConfigurations(
            metadata = SourceTypeConfig(DataSourceType.METADATA, metadataSources),
            lyrics = SourceTypeConfig(DataSourceType.LYRICS, lyricsSources),
            cover = SourceTypeConfig(DataSourceType.COVER, coverSources)
        )
    }

    /**
     * Save unified source configurations
     */
    suspend fun setSourceConfigurations(config: SourceConfigurations) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCE_CONFIGURATIONS] = json.encodeToString(config)
        }
    }

    /**
     * Update a single source within a source type configuration
     * Also updates legacy preference keys for backward compatibility
     */
    suspend fun updateSourceConfig(type: DataSourceType, source: DataSourceConfig) {
        context.settingsDataStore.edit { preferences ->
            // First try to read from existing JSON, only migrate if no JSON exists
            val current = try {
                val jsonString = preferences[SOURCE_CONFIGURATIONS]
                if (!jsonString.isNullOrBlank()) {
                    json.decodeFromString<SourceConfigurations>(jsonString)
                } else {
                    migrateToNewFormat(preferences)
                }
            } catch (e: Exception) {
                migrateToNewFormat(preferences)
            }
            val typeConfig = current.getConfig(type)
            val updated = typeConfig.updateSource(source)
            val newConfig = current.updateConfig(updated)
            preferences[SOURCE_CONFIGURATIONS] = json.encodeToString(newConfig)

            // Sync to legacy preference keys for backward compatibility
            // This ensures AggregatedOnlineMetadataRepository reads the correct enabled state
            when (type) {
                DataSourceType.METADATA -> {
                    when (source.sourceId) {
                        "musicbrainz" -> preferences[METADATA_SOURCE_ENABLED_MUSICBRAINZ] = source.enabled
                        "itunes" -> preferences[METADATA_SOURCE_ENABLED_ITUNES] = source.enabled
                        "netease" -> preferences[METADATA_SOURCE_ENABLED_NETEASE] = source.enabled
                        "qq_music" -> preferences[METADATA_SOURCE_ENABLED_QQ_MUSIC] = source.enabled
                    }
                }
                DataSourceType.LYRICS -> {
                    when (source.sourceId) {
                        "musicbrainz" -> preferences[LYRICS_SOURCE_ENABLED_MUSICBRAINZ] = source.enabled
                        "itunes" -> preferences[LYRICS_SOURCE_ENABLED_ITUNES] = source.enabled
                        "netease" -> preferences[LYRICS_SOURCE_ENABLED_NETEASE] = source.enabled
                        "qq_music" -> preferences[LYRICS_SOURCE_ENABLED_QQ_MUSIC] = source.enabled
                    }
                }
                DataSourceType.COVER -> {
                    when (source.sourceId) {
                        "musicbrainz" -> preferences[COVER_SOURCE_ENABLED_MUSICBRAINZ] = source.enabled
                        "itunes" -> preferences[COVER_SOURCE_ENABLED_ITUNES] = source.enabled
                        "netease" -> preferences[COVER_SOURCE_ENABLED_NETEASE] = source.enabled
                        "qq_music" -> preferences[COVER_SOURCE_ENABLED_QQ_MUSIC] = source.enabled
                    }
                }
            }
        }
    }

    /**
     * Reorder sources within a source type
     * Also updates legacy priority strings for backward compatibility
     */
    suspend fun reorderSources(type: DataSourceType, orderedSourceIds: List<String>) {
        context.settingsDataStore.edit { preferences ->
            // First try to read from existing JSON, only migrate if no JSON exists
            val current = try {
                val jsonString = preferences[SOURCE_CONFIGURATIONS]
                if (!jsonString.isNullOrBlank()) {
                    json.decodeFromString<SourceConfigurations>(jsonString)
                } else {
                    migrateToNewFormat(preferences)
                }
            } catch (e: Exception) {
                migrateToNewFormat(preferences)
            }
            val typeConfig = current.getConfig(type)
            val reordered = typeConfig.reorderSources(orderedSourceIds)
            val newConfig = current.updateConfig(reordered)
            preferences[SOURCE_CONFIGURATIONS] = json.encodeToString(newConfig)
            
            // Also update legacy priority strings for backward compatibility
            // This ensures OnlineMetadataViewModel and AggregatedOnlineMetadataRepository 
            // read the correct priority when using metadataSourcePriority/lyricsSourcePriority/coverSourcePriority
            when (type) {
                DataSourceType.METADATA -> {
                    preferences[PRIORITY_METADATA_SOURCES] = orderedSourceIds.joinToString(",")
                }
                DataSourceType.LYRICS -> {
                    preferences[PRIORITY_LYRICS_SOURCES] = orderedSourceIds.joinToString(",")
                }
                DataSourceType.COVER -> {
                    preferences[PRIORITY_COVER_SOURCES] = orderedSourceIds.joinToString(",")
                }
            }
        }
    }

    /**
     * Save dark theme preference
     */
    suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DARK_THEME] = enabled
        }
    }

    /**
     * Save dynamic colors preference
     */
    suspend fun setDynamicColors(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS] = enabled
        }
    }

    /**
     * Save theme mode preference
     */
    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    /**
     * Save language tag preference (null for system default)
     */
    suspend fun setLanguageTag(tag: String?) {
        context.settingsDataStore.edit { preferences ->
            if (tag == null) {
                preferences.remove(LANGUAGE_TAG)
            } else {
                preferences[LANGUAGE_TAG] = tag
            }
        }
    }

    /**
     * Save selected directory URI list. Empty list clears the key.
     */
    suspend fun setSelectedDirectoryUris(uris: List<String>) {
        context.settingsDataStore.edit { preferences ->
            if (uris.isEmpty()) {
                preferences.remove(SELECTED_DIRECTORY_URIS)
            } else {
                preferences[SELECTED_DIRECTORY_URIS] = uris.joinToString("\n")
            }
        }
    }

    suspend fun setAppleCountryCode(code: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[APPLE_COUNTRY_CODE] = code.lowercase().ifBlank { "us" }
        }
    }

    suspend fun setOnlineSearchLimit(limit: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLINE_SEARCH_LIMIT] = normalizeOnlineSearchLimit(limit)
        }
    }

    suspend fun setOnlineSearchLimitMusicBrainz(limit: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLINE_SEARCH_LIMIT_MUSICBRAINZ] = normalizeOnlineSearchLimit(limit)
        }
    }

    suspend fun setOnlineSearchLimitITunes(limit: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLINE_SEARCH_LIMIT_ITUNES] = normalizeOnlineSearchLimit(limit)
        }
    }

    suspend fun setOnlineSearchLimitNetease(limit: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLINE_SEARCH_LIMIT_NETEASE] = normalizeOnlineSearchLimit(limit)
        }
    }

    suspend fun setOnlineSearchLimitQQMusic(limit: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLINE_SEARCH_LIMIT_QQ_MUSIC] = normalizeOnlineSearchLimit(limit)
        }
    }

    private fun normalizeOnlineSearchLimit(limit: Int): Int {
        return if (limit <= 0) 0 else limit.coerceIn(5, 200)
    }

    suspend fun setSourceEnabledMusicBrainz(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCE_ENABLED_MUSICBRAINZ] = enabled
        }
    }

    suspend fun setSourceEnabledITunes(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCE_ENABLED_ITUNES] = enabled
        }
    }

    suspend fun setSourceEnabledNetease(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCE_ENABLED_NETEASE] = enabled
        }
    }

    suspend fun setSourceEnabledQQMusic(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCE_ENABLED_QQ_MUSIC] = enabled
        }
    }

    suspend fun setMetadataSourceEnabledMusicBrainz(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[METADATA_SOURCE_ENABLED_MUSICBRAINZ] = enabled
        }
    }

    suspend fun setMetadataSourceEnabledITunes(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[METADATA_SOURCE_ENABLED_ITUNES] = enabled
        }
    }

    suspend fun setMetadataSourceEnabledNetease(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[METADATA_SOURCE_ENABLED_NETEASE] = enabled
        }
    }

    suspend fun setMetadataSourceEnabledQQMusic(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[METADATA_SOURCE_ENABLED_QQ_MUSIC] = enabled
        }
    }

    suspend fun setLyricsSourceEnabledMusicBrainz(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_MUSICBRAINZ] = enabled
        }
    }

    suspend fun setLyricsSourceEnabledITunes(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_ITUNES] = enabled
        }
    }

    suspend fun setLyricsSourceEnabledNetease(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_NETEASE] = enabled
        }
    }

    suspend fun setLyricsSourceEnabledQQMusic(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_QQ_MUSIC] = enabled
        }
    }

    suspend fun setCoverSourceEnabledMusicBrainz(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[COVER_SOURCE_ENABLED_MUSICBRAINZ] = enabled
        }
    }

    suspend fun setCoverSourceEnabledITunes(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[COVER_SOURCE_ENABLED_ITUNES] = enabled
        }
    }

    suspend fun setCoverSourceEnabledNetease(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[COVER_SOURCE_ENABLED_NETEASE] = enabled
        }
    }

    suspend fun setCoverSourceEnabledQQMusic(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[COVER_SOURCE_ENABLED_QQ_MUSIC] = enabled
        }
    }

    suspend fun setMetadataSourcePriority(priority: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PRIORITY_METADATA_SOURCES] = normalizePriority(priority).joinToString(",")
        }
    }

    suspend fun setLyricsSourcePriority(priority: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PRIORITY_LYRICS_SOURCES] = normalizePriority(priority).joinToString(",")
        }
    }

    suspend fun setCoverSourcePriority(priority: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PRIORITY_COVER_SOURCES] = normalizePriority(priority).joinToString(",")
        }
    }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LOGGING_ENABLED] = enabled
        }
    }

    suspend fun setFileLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[FILE_LOGGING_ENABLED] = enabled
        }
    }

    suspend fun setConsoleLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CONSOLE_LOGGING_ENABLED] = enabled
        }
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CRASH_REPORTING_ENABLED] = enabled
        }
    }

    /**
     * Save ReplayGain target loudness preference
     */
    suspend fun setReplayGainTargetLoudness(loudness: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[REPLAY_GAIN_TARGET_LOUDNESS] = loudness.coerceIn(-24f, -14f)
        }
    }

    /**
     * Save scan mode preference
     */
    suspend fun setScanMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SCAN_MODE] = mode
        }
    }

    /**
     * Save ReplayGain clip mode preference
     */
    suspend fun setReplayGainClipMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[REPLAY_GAIN_CLIP_MODE] = mode
        }
    }


    /**
     * Save minimum duration filter enabled preference
     */
    suspend fun setMinDurationFilterEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIN_DURATION_FILTER_ENABLED] = enabled
        }
    }

    /**
     * Save minimum duration filter threshold preference (in milliseconds)
     */
    suspend fun setMinDurationFilterThresholdMs(thresholdMs: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIN_DURATION_FILTER_THRESHOLD_MS] = thresholdMs.coerceAtLeast(0)
        }
    }


    /**
     * Save whitelist enabled preference
     */
    suspend fun setWhitelistEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WHITELIST_ENABLED] = enabled
        }
    }

    /**
     * Save blacklist enabled preference
     */
    suspend fun setBlacklistEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[BLACKLIST_ENABLED] = enabled
        }
    }

    /**
     * Save blacklist directory URI list. Empty list clears the key.
     */
    suspend fun setBlacklistDirectoryUris(uris: List<String>) {
        context.settingsDataStore.edit { preferences ->
            if (uris.isEmpty()) {
                preferences.remove(BLACKLIST_DIRECTORY_URIS)
            } else {
                preferences[BLACKLIST_DIRECTORY_URIS] = uris.joinToString("\n")
            }
        }
    }

    private fun parsePriority(raw: String?, fallback: List<String>): List<String> {
        if (raw.isNullOrBlank()) return fallback
        return normalizePriority(
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        )
    }

    private fun normalizePriority(priority: List<String>): List<String> {
        val allowed = defaultSourcePriority()
        val normalized = priority.map { it.lowercase() }.filter { it in allowed }
        return (normalized + allowed).distinct()
    }

    private fun defaultSourcePriority(): List<String> {
        return listOf("itunes", "musicbrainz", "netease", "qq_music")
    }

    private fun lyricsDefaultSourcePriority(): List<String> {
        return listOf("netease", "qq_music")
    }

    // ==================== Proxy Settings (Encrypted) ====================

    /**
     * Encrypted SharedPreferences for sensitive proxy settings.
     * Uses Android Keystore for encryption to protect proxy credentials at rest.
     * 
     * Note: This encrypts proxy host and port. If proxy authentication (username/password)
     * is added in the future, those should also be stored here.
     */
    private val encryptedProxyPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "proxy_settings_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Proxy enabled state (stored in regular DataStore - not sensitive)
     */
    val proxyEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[PROXY_ENABLED] ?: false
        }

    /**
     * Proxy type state (stored in regular DataStore - not sensitive)
     */
    val proxyType: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[PROXY_TYPE] ?: "HTTP"
        }

    /**
     * Proxy host state (ENCRYPTED at rest)
     * Reads from EncryptedSharedPreferences for security.
     */
    val proxyHost: Flow<String> = context.settingsDataStore.data
        .map {
            // Read from encrypted storage
            encryptedProxyPrefs.getString(ProxyKeys.KEY_PROXY_HOST, "") ?: ""
        }

    /**
     * Proxy port state (ENCRYPTED at rest)
     * Reads from EncryptedSharedPreferences for security.
     */
    val proxyPort: Flow<Int> = context.settingsDataStore.data
        .map {
            // Read from encrypted storage
            encryptedProxyPrefs.getInt(ProxyKeys.KEY_PROXY_PORT, 0)
        }

    private object ProxyKeys {
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
    }

    /**
     * Lyrics timestamp format enabled preference flow (3-digit to 2-digit)
     */
    val lyricsTimestampFormatEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_TIMESTAMP_FORMAT_ENABLED] ?: true
        }

    /**
     * Artist separator enabled preference flow
     */
    val artistSeparatorEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[ARTIST_SEPARATOR_ENABLED] ?: true
        }

    /**
     * Artist custom separators preference flow
     */
    val artistSeparators: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[ARTIST_SEPARATORS] ?: """["&","/","\\"]"""
        }

    /**
     * Artist separators as Set<String> — exposed for ViewModel use
     * Handles migration from old format automatically
     */
    val artistSeparatorsSet: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            val raw = preferences[ARTIST_SEPARATORS] ?: """["&","/","\\"]"""
            migrateArtistSeparators(raw)
        }

    // ==================== Input Validation ====================

    /**
     * Validates a proxy host string.
     * Proxy host should be a valid hostname or IP address.
     *
     * @param host The proxy host to validate
     * @return true if the host is valid, false otherwise
     */
    fun isValidProxyHost(host: String): Boolean {
        if (host.isBlank()) return false
        // Basic hostname pattern: starts and ends with alphanumeric, allows hyphens and dots in middle
        val hostPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]{0,253}[a-zA-Z0-9]$")
        return hostPattern.matches(host)
    }

    /**
     * Validates a proxy port number.
     * Valid ports are in the range 1-65535.
     *
     * @param port The port number to validate
     * @return true if the port is valid, false otherwise
     */
    fun isValidProxyPort(port: Int): Boolean = port in 1..65535

    /**
     * Save proxy enabled preference
     */
    suspend fun setProxyEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PROXY_ENABLED] = enabled
        }
    }

    /**
     * Save proxy type preference
     */
    suspend fun setProxyType(type: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PROXY_TYPE] = type
        }
    }

    /**
     * Save proxy host preference (ENCRYPTED at rest)
     * @param host The proxy host to save (will be trimmed and validated)
     */
    suspend fun setProxyHost(host: String) {
        // Write to encrypted storage (synchronous, call from suspend context)
        encryptedProxyPrefs.edit()
            .putString(ProxyKeys.KEY_PROXY_HOST, host.trim())
            .apply()
    }

    /**
     * Save proxy port preference (ENCRYPTED at rest)
     * @param port The port to save (will be validated, must be in 1-65535)
     */
    suspend fun setProxyPort(port: Int) {
        // Write to encrypted storage (synchronous, call from suspend context)
        // Validate port range - only allow valid ports when enabling proxy
        val validPort = if (isValidProxyPort(port)) port else 0
        encryptedProxyPrefs.edit()
            .putInt(ProxyKeys.KEY_PROXY_PORT, validPort)
            .apply()
    }

    /**
     * Save artist separator enabled preference
     */
    suspend fun setArtistSeparatorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ARTIST_SEPARATOR_ENABLED] = enabled
        }
    }

    /**
     * Save lyrics timestamp format enabled preference
     */
    suspend fun setLyricsTimestampFormatEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LYRICS_TIMESTAMP_FORMAT_ENABLED] = enabled
        }
    }

    /**
     * Save artist separators preference (legacy String version for backward compat)
     */
    suspend fun setArtistSeparators(separators: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ARTIST_SEPARATORS] = separators.ifBlank { """["&","/","\\"]""" }
        }
    }

    /**
     * Save artist separators preference (Set version — JSON serialization)
     */
    suspend fun setArtistSeparators(separators: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[ARTIST_SEPARATORS] = json.encodeToString(separators)
        }
    }

    /**
     * Migrate artist separators from old string format to new Set<String> format.
     * Old format: "&\\" (concatenated chars)
     * New format: ["&","/","\\"] (JSON array)
     */
    private fun migrateArtistSeparators(raw: String): Set<String> {
        return if (raw.startsWith("[")) {
            // New JSON format — parse directly
            try {
                json.decodeFromString<Set<String>>(raw)
            } catch (e: Exception) {
                // Parse failed, return default
                setOf("&", "/", "\\")
            }
        } else {
            // Old format: split by character (filter whitespace)
            raw.toCharArray().filter { !it.isWhitespace() }.map { it.toString() }.toSet()
        }
    }

    // ==================== File Browser Settings ====================

    /**
     * File browser root tab preference flow (DIRECTORIES or ALL)
     */
    val fileBrowserRootTab: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FILE_BROWSER_ROOT_TAB] ?: "DIRECTORIES"
        }

    /**
     * Save file browser root tab preference
     */
    suspend fun setFileBrowserRootTab(tab: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[FILE_BROWSER_ROOT_TAB] = tab
        }
    }
}
