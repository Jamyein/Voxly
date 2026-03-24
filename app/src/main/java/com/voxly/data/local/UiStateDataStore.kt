package com.voxly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_state")

/**
 * DataStore manager for UI state persistence.
 * Stores user preferences for UI-specific states like sort options.
 * Separated from SettingsDataStore to keep concerns clean.
 */
@Singleton
class UiStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Album screen sort option
        val ALBUM_SORT_OPTION = stringPreferencesKey("album_sort_option")

        // File browser (All audios tab) sort option
        val FILE_BROWSER_SORT_OPTION = stringPreferencesKey("file_browser_sort_option")

        // Directory content screen sort option
        val DIRECTORY_FILE_SORT_OPTION = stringPreferencesKey("directory_file_sort_option")
    }

    // ==================== Album Sort ====================

    /**
     * Album screen sort option flow
     */
    val albumSortOption: Flow<String> = context.uiStateDataStore.data
        .map { preferences ->
            preferences[ALBUM_SORT_OPTION] ?: AlbumSortOption.NAME_ASC.name
        }

    /**
     * Save album sort option
     */
    suspend fun setAlbumSortOption(option: String) {
        context.uiStateDataStore.edit { preferences ->
            preferences[ALBUM_SORT_OPTION] = option
        }
    }

    // ==================== File Browser Sort ====================

    /**
     * File browser (All audios tab) sort option flow
     */
    val fileBrowserSortOption: Flow<String> = context.uiStateDataStore.data
        .map { preferences ->
            preferences[FILE_BROWSER_SORT_OPTION] ?: FileSortOption.NAME_ASC.name
        }

    /**
     * Save file browser sort option
     */
    suspend fun setFileBrowserSortOption(option: String) {
        context.uiStateDataStore.edit { preferences ->
            preferences[FILE_BROWSER_SORT_OPTION] = option
        }
    }

    // ==================== Directory File Sort ====================

    /**
     * Directory content screen sort option flow
     */
    val directoryFileSortOption: Flow<String> = context.uiStateDataStore.data
        .map { preferences ->
            preferences[DIRECTORY_FILE_SORT_OPTION] ?: DirFileSortOption.NAME_ASC.name
        }

    /**
     * Save directory file sort option
     */
    suspend fun setDirectoryFileSortOption(option: String) {
        context.uiStateDataStore.edit { preferences ->
            preferences[DIRECTORY_FILE_SORT_OPTION] = option
        }
    }
}

/**
 * Album screen sort options
 */
enum class AlbumSortOption {
    NAME_ASC,
    TRACK_COUNT_DESC,
    YEAR_DESC
}

/**
 * File browser (All audios tab) sort options
 */
enum class FileSortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}

/**
 * Directory content screen sort options
 */
enum class DirFileSortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}
