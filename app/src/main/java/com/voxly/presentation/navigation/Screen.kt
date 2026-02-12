package com.voxly.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object FileBrowser : Screen("file_browser")
    data object RecentEdits : Screen("recent_edits")
    data object BatchOperations : Screen("batch_operations")
    data object Settings : Screen("settings")
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
    data object LyricsEditor : Screen("lyrics_editor/{filePath}/{trackName}/{artistName}") {
        fun createRoute(filePath: String, trackName: String, artistName: String) =
            "lyrics_editor/${java.net.URLEncoder.encode(filePath, "UTF-8")}/${java.net.URLEncoder.encode(trackName, "UTF-8")}/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
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
    BATCH_OPERATIONS(Screen.BatchOperations, "Batch", "playlist_add")
}
