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
data object Albums : NavKey

@Serializable
data object Artists : NavKey

@Serializable
data object Settings : NavKey

/**
 * Routes with arguments using @Serializable data classes.
 */
@Serializable
data class DirectoryContent(
    val directoryUri: String,
    val directoryName: String
) : NavKey

@Serializable
data class MetadataEditor(
    val filePath: String,
    val coverTag: String = "",
    // 列表行已解析的封面 URI（folder/内嵌场景）。编辑器首帧用它作 fallbackUri → 与列表行
    // 同 memoryCacheKey → 命中内存缓存 → 共享过渡首帧直接出图，无占位闪烁。
    // 可空默认值，旧存档反序列化向后兼容。
    val coverUri: String = "",
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
data class LyricsPoster(
    val filePath: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val lyricsText: String = "",
    val selectedLyricsIndices: List<Int> = emptyList()
) : NavKey

@Serializable
data class AlbumDetail(
    val albumName: String,
    val albumArtist: String = "",
    // 封面快速通道：导航时带上列表页已解析的封面来源，详情页首帧即可命中 Coil 内存缓存
    // （同一 memoryCacheKey 的位图直接画出，无需解码）。可空默认值，旧存档反序列化向后兼容。
    val initialCoverAlbumId: Long = 0,
    val initialCoverPath: String = "",
) : NavKey

@Serializable
data class ArtistDetail(
    val artistName: String
) : NavKey

@Serializable
data object ScanDirectorySettings : NavKey

@Serializable
data object SourceSettings : NavKey

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
    ALBUMS(Albums, "Albums", "album"),
    ARTISTS(Artists, "Artists", "person"),
    SETTINGS(Settings, "Settings", "settings")
}
