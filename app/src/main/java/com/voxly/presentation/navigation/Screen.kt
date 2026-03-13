package com.voxly.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation3 routes using @Serializable + NavKey interface.
 * Each route is a data object/class that implements NavKey.
 */

/**
 * Bottom navigation items that show bottom bar.
 */
@Serializable
data object FileBrowser : NavKey

@Serializable
data object RecentEdits : NavKey

@Serializable
data object Statistics : NavKey

@Serializable
data object Settings : NavKey

/**
 * Routes with arguments using @Serializable data classes.
 */
@Serializable
data class DirectoryContent(
    val directoryUri: String,
    val directoryName: String,
    val filePaths: List<String> = emptyList()
) : NavKey

@Serializable
data class MetadataEditor(
    val filePath: String,
    val coverTag: String = ""
) : NavKey

@Serializable
data class ReplayGainScanner(
    val filePaths: List<String>
) : NavKey

@Serializable
data class OnlineMetadata(
    val filePath: String
) : NavKey

@Serializable
data class OnlineLyricsSearch(
    val filePath: String
) : NavKey

@Serializable
data class OnlineCoverSearch(
    val filePath: String
) : NavKey

@Serializable
data class LyricsSelector(
    val filePath: String,
    val title: String = "",
    val artist: String = "",
    val album: String = ""
) : NavKey

@Serializable
data class AlbumDetail(
    val albumName: String,
    val albumArtist: String = ""
) : NavKey

@Serializable
data class ArtistDetail(
    val artistName: String
) : NavKey

@Serializable
data object ScanDirectorySettings : NavKey

@Serializable
data object LogViewer : NavKey

/**
 * Bottom navigation items for the main screen.
 */
enum class BottomNavItem(
    val key: NavKey,
    val label: String,
    val iconName: String
) {
    FILE_BROWSER(FileBrowser, "Files", "folder"),
    RECENT_EDITS(RecentEdits, "Recent", "history"),
    STATISTICS(Statistics, "Statistics", "bar_chart"),
    SETTINGS(Settings, "Settings", "settings")
}
