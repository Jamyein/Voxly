package com.voxly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        val SCAN_QUALITY = stringPreferencesKey("scan_quality")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_DIRECTORY_URIS = stringPreferencesKey("selected_directory_uris")
        val APPLE_COUNTRY_CODE = stringPreferencesKey("apple_country_code")
        val ONLINE_SEARCH_LIMIT = intPreferencesKey("online_search_limit")
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
     * Scan quality preference flow
     */
    val scanQuality: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SCAN_QUALITY] ?: "Normal"
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
            preferences[METADATA_SOURCE_ENABLED_MUSICBRAINZ]
                ?: preferences[SOURCE_ENABLED_MUSICBRAINZ]
                ?: true
        }

    val metadataSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_ITUNES]
                ?: preferences[SOURCE_ENABLED_ITUNES]
                ?: true
        }

    val metadataSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_NETEASE]
                ?: preferences[SOURCE_ENABLED_NETEASE]
                ?: true
        }

    val metadataSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[METADATA_SOURCE_ENABLED_QQ_MUSIC]
                ?: preferences[SOURCE_ENABLED_QQ_MUSIC]
                ?: true
        }

    val lyricsSourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_MUSICBRAINZ]
                ?: preferences[SOURCE_ENABLED_MUSICBRAINZ]
                ?: true
        }

    val lyricsSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_ITUNES]
                ?: preferences[SOURCE_ENABLED_ITUNES]
                ?: true
        }

    val lyricsSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_NETEASE]
                ?: preferences[SOURCE_ENABLED_NETEASE]
                ?: true
        }

    val lyricsSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LYRICS_SOURCE_ENABLED_QQ_MUSIC]
                ?: preferences[SOURCE_ENABLED_QQ_MUSIC]
                ?: true
        }

    val coverSourceEnabledMusicBrainz: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_MUSICBRAINZ]
                ?: preferences[SOURCE_ENABLED_MUSICBRAINZ]
                ?: true
        }

    val coverSourceEnabledITunes: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_ITUNES]
                ?: preferences[SOURCE_ENABLED_ITUNES]
                ?: true
        }

    val coverSourceEnabledNetease: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_NETEASE]
                ?: preferences[SOURCE_ENABLED_NETEASE]
                ?: true
        }

    val coverSourceEnabledQQMusic: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[COVER_SOURCE_ENABLED_QQ_MUSIC]
                ?: preferences[SOURCE_ENABLED_QQ_MUSIC]
                ?: true
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
            preferences[LOGGING_ENABLED] ?: false
        }

    val fileLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FILE_LOGGING_ENABLED] ?: false
        }

    val consoleLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CONSOLE_LOGGING_ENABLED] ?: false
        }

    val crashReportingEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CRASH_REPORTING_ENABLED] ?: true
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
     * Save scan quality preference
     */
    suspend fun setScanQuality(quality: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SCAN_QUALITY] = quality
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
}
