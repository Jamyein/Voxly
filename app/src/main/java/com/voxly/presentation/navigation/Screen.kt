package com.voxly.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(
    val route: String,
    val showBottomBar: Boolean = false
) {
    data object FileBrowser : Screen("file_browser", showBottomBar = true)
    data object DirectoryContent : Screen("directory_content/{directoryUri}/{directoryName}?filePaths={filePaths}", showBottomBar = false) {
        fun createRoute(directoryUri: String, directoryName: String, filePaths: List<String> = emptyList()) =
            "directory_content/${java.net.URLEncoder.encode(directoryUri, "UTF-8")}/${java.net.URLEncoder.encode(directoryName, "UTF-8")}?filePaths=${filePaths.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }}"
    }
    data object RecentEdits : Screen("recent_edits", showBottomBar = true)
    data object Statistics : Screen("statistics", showBottomBar = true)
    data object Settings : Screen("settings", showBottomBar = true)
    data object ScanDirectorySettings : Screen("scan_directory_settings", showBottomBar = false)
    data object LogViewer : Screen("log_viewer", showBottomBar = false)
    data object MetadataEditor : Screen("metadata_editor/{filePath}/{coverTag}", showBottomBar = false) {
        fun createRoute(filePath: String, coverTag: String? = null) =
            "metadata_editor/${java.net.URLEncoder.encode(filePath, "UTF-8")}/${coverTag ?: ""}"
    }
    data object ReplayGainScanner : Screen("replay_gain_scanner/{filePaths}", showBottomBar = false) {
        fun createRoute(filePaths: List<String>) =
            "replay_gain_scanner/${filePaths.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }}"
    }
    data object OnlineMetadata : Screen("online_metadata/{filePath}", showBottomBar = false) {
        fun createRoute(filePath: String) = "online_metadata/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object OnlineLyricsSearch : Screen("online_lyrics_search/{filePath}", showBottomBar = false) {
        fun createRoute(filePath: String) = "online_lyrics_search/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object OnlineCoverSearch : Screen("online_cover_search/{filePath}", showBottomBar = false) {
        fun createRoute(filePath: String) = "online_cover_search/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
    data object LyricsSelector : Screen(
        "lyrics_selector/{filePath}?title={title}&artist={artist}&album={album}",
        showBottomBar = false
    ) {
        fun createRoute(
            filePath: String,
            title: String = "",
            artist: String = "",
            album: String = ""
        ): String {
            return "lyrics_selector/${java.net.URLEncoder.encode(filePath, "UTF-8")}" +
                "?title=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                "&artist=${java.net.URLEncoder.encode(artist, "UTF-8")}" +
                "&album=${java.net.URLEncoder.encode(album, "UTF-8")}"
        }
    }
    data object AlbumDetail : Screen("album_detail/{albumName}/{albumArtist}", showBottomBar = false) {
        fun createRoute(albumName: String, albumArtist: String?) =
            "album_detail/${java.net.URLEncoder.encode(albumName, "UTF-8")}/${java.net.URLEncoder.encode(albumArtist ?: "", "UTF-8")}"
    }
    data object ArtistDetail : Screen("artist_detail/{artistName}", showBottomBar = false) {
        fun createRoute(artistName: String) =
            "artist_detail/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
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
