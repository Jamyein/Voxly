package com.mp3tag.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore manager for application settings.
 * Provides persistent storage for user preferences.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val SCAN_QUALITY = stringPreferencesKey("scan_quality")
    }

    /**
     * Dark theme preference flow
     */
    val darkTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_THEME] ?: false
        }

    /**
     * Dynamic colors preference flow
     */
    val dynamicColors: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLORS] ?: true
        }

    /**
     * Scan quality preference flow
     */
    val scanQuality: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SCAN_QUALITY] ?: "Normal"
        }

    /**
     * Save dark theme preference
     */
    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME] = enabled
        }
    }

    /**
     * Save dynamic colors preference
     */
    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS] = enabled
        }
    }

    /**
     * Save scan quality preference
     */
    suspend fun setScanQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_QUALITY] = quality
        }
    }
}
