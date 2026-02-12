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
            (preferences[ONLINE_SEARCH_LIMIT] ?: 25).coerceIn(5, 50)
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
            preferences[ONLINE_SEARCH_LIMIT] = limit.coerceIn(5, 50)
        }
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
}
