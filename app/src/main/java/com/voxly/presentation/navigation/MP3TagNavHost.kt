package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.components.FlexibleBottomAppBar
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.screens.RecentEditsScreen
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.StatisticsScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.viewmodel.AppViewModel
import java.net.URLDecoder

/**
 * Main navigation host for the MP3 Tag Editor app.
 * Implements bottom navigation with Material Design 3 components.
 */
@Composable
fun MP3TagNavHost(
    navController: NavHostController = rememberNavController()
) {
    // Initialize AppViewModel at navigation level to observe settings changes app-wide
    val appViewModel: AppViewModel = hiltViewModel()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if bottom bar should be shown based on BottomNavItem routes
    val bottomNavRoutes = BottomNavItem.entries.map { it.screen.route }

    // SharedTransitionLayout for shared element transitions (e.g., cover art morphing)
    Scaffold(
        bottomBar = {
            FlexibleBottomAppBar(
                navController = navController,
                currentRoute = currentDestination?.route
            )
        }
    ) { outerPadding ->
        NavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
            startDestination = Screen.FileBrowser.route,
            enterTransition = {
                val destinations = bottomNavRoutes
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 底部导航主页间使用 Fade Through + Scale
                    fromRoute in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.BottomNavEnter
                    }
                    // 从非主页返回时使用 Pop Enter
                    fromRoute !in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.PagePopEnterM3E
                    }
                    // M3E 规范: 进入动画 = 向上位移 + 缩放(95%->100%) + 渐显, Spring
                    else -> {
                        ExpressiveAnimations.PageEnterM3E
                    }
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 主页间切换使用 Fade Through + Scale
                    fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes -> {
                        ExpressiveAnimations.BottomNavExit
                    }
                    // 进入子页面时旧页面缩小并变暗
                    fromRoute in bottomNavRoutes && toRoute !in bottomNavRoutes -> {
                        ExpressiveAnimations.PageExitM3E
                    }
                    // 其他退出使用 M3E Pop Exit
                    else -> {
                        ExpressiveAnimations.PageExitM3E
                    }
                }
            },
            popEnterTransition = {
                ExpressiveAnimations.PagePopEnterM3E
            },
            popExitTransition = {
                ExpressiveAnimations.PagePopExitM3E
            }
        ) {
            composable(Screen.FileBrowser.route) {
                FileBrowserScreen(
                    outerPadding = outerPadding,
                    onNavigateToMetadata = { filePath, coverTag ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    },
                    onNavigateToDirectory = { directoryUri, directoryName, filePaths ->
                        navController.navigate(Screen.DirectoryContent.createRoute(directoryUri, directoryName, filePaths))
                    },
                    onNavigateToSearch = {},
                    onNavigateToAlbum = { albumName, albumArtist ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumName, albumArtist))
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    }
                )
            }

            composable(
                route = Screen.DirectoryContent.route,
                arguments = listOf(
                    navArgument("directoryUri") { type = NavType.StringType },
                    navArgument("directoryName") { type = NavType.StringType },
                    navArgument("filePaths") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                ),
                enterTransition = {
                    ExpressiveAnimations.SlideInHorizontallyInitialOffsetForward
                },
                exitTransition = {
                    ExpressiveAnimations.SlideOutHorizontallyInitialOffsetForward
                },
                popEnterTransition = {
                    ExpressiveAnimations.SlideInHorizontallyInitialOffsetBackward
                },
                popExitTransition = {
                    ExpressiveAnimations.SlideOutHorizontallyInitialOffsetBackward
                }
            ) { backStackEntry ->
                val encodedDirectoryUri = backStackEntry.arguments?.getString("directoryUri") ?: ""
                val directoryUri = URLDecoder.decode(encodedDirectoryUri, "UTF-8")
                val encodedDirectoryName = backStackEntry.arguments?.getString("directoryName") ?: ""
                val directoryName = URLDecoder.decode(encodedDirectoryName, "UTF-8")
                val encodedFilePaths = backStackEntry.arguments?.getString("filePaths") ?: ""
                val initialFiles = if (encodedFilePaths.isNotBlank()) {
                    encodedFilePaths.split(",").map { URLDecoder.decode(it, "UTF-8") }
                } else {
                    emptyList()
                }
                DirectoryContentScreen(
                    directoryUri = directoryUri,
                    directoryName = directoryName,
                    initialFiles = initialFiles,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath, coverTag ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    }
                )
            }

            composable(Screen.RecentEdits.route) {
                RecentEditsScreen(
                    outerPadding = outerPadding,
                    onNavigateToMetadata = { filePath, coverTag ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag))
                    }
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    outerPadding = outerPadding,
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    }
                )
            }

            composable(Screen.Settings.route) {
                val context = LocalContext.current
                SettingsScreen(
                    outerPadding = outerPadding,
                    onNavigateToLogViewer = {
                        navController.navigate(Screen.LogViewer.route)
                    },
                    onExportLogs = {
                        val viewModel = com.voxly.presentation.screens.log.LogViewerViewModel()
                        viewModel.exportLogs(context) { uri ->
                            if (uri != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                            } else {
                                Toast.makeText(context, R.string.settings_logging_no_logs, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onNavigateToScanDirectorySettings = {
                        navController.navigate(Screen.ScanDirectorySettings.route)
                    },
                    onCleanupLogs = {
                        val deletedCount = LogManager.clearAllLogs()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_logging_cleanup_complete, deletedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            composable(Screen.LogViewer.route) {
                LogViewerScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ScanDirectorySettings.route) {
                com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.MetadataEditor.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("coverTag") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                val coverTag = backStackEntry.arguments?.getString("coverTag")?.takeIf { it.isNotEmpty() }
                val pendingOnlineMetadata by backStackEntry.savedStateHandle
                    .getStateFlow<AudioMetadata?>("online_metadata_result", null)
                    .collectAsState()
                val pendingOnlineLyrics by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("online_lyrics_result", null)
                    .collectAsState()

                MetadataEditorScreen(
                    filePath = filePath,
                    coverTag = coverTag,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOnlineMetadata = {
                        navController.navigate(Screen.OnlineMetadata.createRoute(filePath))
                    },
                    onNavigateToOnlineLyricsSearch = {
                        navController.navigate(Screen.OnlineLyricsSearch.createRoute(filePath))
                    },
                    onNavigateToOnlineCoverSearch = {
                        navController.navigate(Screen.OnlineCoverSearch.createRoute(filePath))
                    },
                    onNavigateToLyricsSelector = { _, title, artist, album, _ ->
                        navController.navigate(Screen.LyricsSelector.createRoute(
                            filePath = filePath,
                            title = title,
                            artist = artist,
                            album = album
                        ))
                    },
                    pendingOnlineMetadata = pendingOnlineMetadata,
                    onConsumePendingOnlineMetadata = {
                        backStackEntry.savedStateHandle.remove<AudioMetadata>("online_metadata_result")
                    },
                    pendingOnlineLyrics = pendingOnlineLyrics,
                    onConsumePendingOnlineLyrics = {
                        backStackEntry.savedStateHandle.remove<String>("online_lyrics_result")
                    }
                )
            }

            composable(
                route = Screen.ReplayGainScanner.route,
                arguments = listOf(
                    navArgument("filePaths") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPaths = backStackEntry.arguments?.getString("filePaths") ?: ""
                val filePaths: List<String> = encodedPaths.split(",").map { URLDecoder.decode(it, "UTF-8") }
                ReplayGainScannerScreen(
                    filePaths = filePaths,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath: String, coverTag: String? ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag)) {
                            popUpTo(Screen.FileBrowser.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.OnlineMetadata.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                OnlineMetadataScreen(
                    filePath = URLDecoder.decode(encodedPath, "UTF-8"),
                    onNavigateBack = { navController.popBackStack() },
                    onApplyMetadata = { metadata ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("online_metadata_result", metadata)
                        navController.popBackStack()
                    }
                )
            }

            // Online lyrics search screen
            composable(
                route = Screen.OnlineLyricsSearch.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                
                OnlineLyricsSearchScreen(
                    filePath = filePath,
                    onNavigateBack = { navController.popBackStack() },
                    onLyricsSelected = { lyricsText ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("online_lyrics_result", lyricsText)
                        navController.popBackStack()
                    }
                )
            }

            // Online cover search screen
            composable(
                route = Screen.OnlineCoverSearch.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                OnlineCoverSearchScreen(
                    filePath = URLDecoder.decode(encodedPath, "UTF-8"),
                    onNavigateBack = { navController.popBackStack() },
                    onCoverSelected = { coverBytes ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("online_cover_result", coverBytes)
                        navController.popBackStack()
                    }
                )
            }

            // Lyrics selector screen
            composable(
                route = Screen.LyricsSelector.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument("artist") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument("album") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                val title = URLDecoder.decode(encodedTitle, "UTF-8")
                val encodedArtist = backStackEntry.arguments?.getString("artist") ?: ""
                val artist = URLDecoder.decode(encodedArtist, "UTF-8")
                val encodedAlbum = backStackEntry.arguments?.getString("album") ?: ""
                val album = URLDecoder.decode(encodedAlbum, "UTF-8")

                LyricsSelectorScreen(
                    title = title,
                    artist = artist,
                    album = album,
                    onNavigateBack = { navController.popBackStack() },
                    onDismiss = {
                        // Clear selected lyrics and go back
                        navController.popBackStack()
                    }
                )
            }

            // Album detail screen
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(
                    navArgument("albumName") { type = NavType.StringType },
                    navArgument("albumArtist") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName")?.let {
                    URLDecoder.decode(it, "UTF-8")
                } ?: ""
                val albumArtist = backStackEntry.arguments?.getString("albumArtist")?.let {
                    URLDecoder.decode(it, "UTF-8")
                }?.takeIf { it.isNotEmpty() }
                AlbumDetailScreen(
                    albumName = albumName,
                    albumArtist = albumArtist,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath, coverTag ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag))
                    }
                )
            }

            // Artist detail screen
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(
                    navArgument("artistName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val artistName = backStackEntry.arguments?.getString("artistName")?.let {
                    URLDecoder.decode(it, "UTF-8")
                } ?: ""
                ArtistDetailScreen(
                    artistName = artistName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath, coverTag ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath, coverTag))
                    }
                )
            }
        }
    }
}

/**
 * Data class representing a bottom navigation item with icons.
 */
data class BottomNavItemData(
    val screen: Screen,
    val labelResId: Int,
    val selectedIcon: AppIcon,
    val unselectedIcon: AppIcon
)

/**
 * List of bottom navigation items.
 */
private val bottomNavItems = listOf(
    BottomNavItemData(
        screen = Screen.FileBrowser,
        labelResId = R.string.nav_file_browser,
        selectedIcon = AppIcon.Folder,
        unselectedIcon = AppIcon.FolderOutlined
    ),
    BottomNavItemData(
        screen = Screen.RecentEdits,
        labelResId = R.string.nav_recent_edits,
        selectedIcon = AppIcon.History,
        unselectedIcon = AppIcon.HistoryOutlined
    ),
    BottomNavItemData(
        screen = Screen.Statistics,
        labelResId = R.string.nav_statistics,
        selectedIcon = AppIcon.BarChart,
        unselectedIcon = AppIcon.BarChart
    ),
    BottomNavItemData(
        screen = Screen.Settings,
        labelResId = R.string.nav_settings,
        selectedIcon = AppIcon.Settings,
        unselectedIcon = AppIcon.SettingsOutlined
    )
)
