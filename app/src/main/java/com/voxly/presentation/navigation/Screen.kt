package com.voxly.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object FileBrowser : Screen("file_browser")
    data object RecentEdits : Screen("recent_edits")
    data object Statistics : Screen("statistics")
    data object BatchOperations : Screen("batch_operations")
    data object Settings : Screen("settings")
    data object ScanDirectorySettings : Screen("scan_directory_settings")
    data object LogViewer : Screen("log_viewer")
    data object MetadataEditor : Screen("metadata_editor/{filePath}") {
        fun createRoute(filePath: String) = "metadata_editor/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object ReplayGainScanner : Screen("replay_gain_scanner/{filePaths}") {
        fun createRoute(filePaths: List<String>) =
            "replay_gain_scanner/${filePaths.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }}"
    }
    data object OnlineMetadata : Screen("online_metadata/{filePath}") {
        fun createRoute(filePath: String) = "online_metadata/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object OnlineLyricsSearch : Screen("online_lyrics_search/{filePath}") {
        fun createRoute(filePath: String) = "online_lyrics_search/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object OnlineCoverSearch : Screen("online_cover_search/{filePath}") {
        fun createRoute(filePath: String) = "online_cover_search/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
}

/**
 * Bottom navigation items for the main screen.
 */
enum class BottomNavItem(
    val screen: Screen,
    val label: String,
    val iconName: String
) {
    FILE_BROWSER(Screen.FileBrowser, "Files", "folder"),
    RECENT_EDITS(Screen.RecentEdits, "Recent", "history"),
    STATISTICS(Screen.Statistics, "Statistics", "bar_chart"),
    SETTINGS(Screen.Settings, "Settings", "settings")
}
