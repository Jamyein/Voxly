package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.components.FlexibleBottomAppBar
import com.voxly.presentation.screens.filebrowser.FileBrowserScreen
import com.voxly.presentation.screens.filebrowser.FileSearchScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.RecentEditsScreen
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.StatisticsScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import java.net.URLDecoder
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.presentation.viewmodel.AppViewModel

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
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    // Scroll progress for flexible bottom bar (0 = expanded, 1 = collapsed)
    var scrollProgress by remember { mutableFloatStateOf(0f) }

    // Reset scroll progress when navigating to non-bottom-nav screens
    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route !in bottomNavRoutes) {
            scrollProgress = 0f
        }
    }

    // Box layout: content + bottom bar positioned absolutely
    // This allows the bottom bar to hide without affecting content padding
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            startDestination = Screen.FileBrowser.route,
            enterTransition = {
                val destinations = bottomNavRoutes
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 底部导航主页间使用滑动+淡入 (更有表现力)
                    fromRoute in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.BottomNavSlideEnter
                    }
                    // 从非主页返回时从左侧滑入 (Shared Axis)
                    fromRoute !in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.SharedAxisPopEnter
                    }
                    // 新页面从右侧滑入 (Shared Axis - 新页面滑入，旧页面轻微向左)
                    else -> ExpressiveAnimations.SharedAxisEnter
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 主页间切换使用向下滑出
                    fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes -> {
                        ExpressiveAnimations.BottomNavSlideExit
                    }
                    // 进入子页面时旧页面向左轻微移动 (Shared Axis)
                    fromRoute in bottomNavRoutes && toRoute !in bottomNavRoutes -> {
                        ExpressiveAnimations.SharedAxisExit
                    }
                    // 返回时向右滑出
                    else -> ExpressiveAnimations.SharedAxisPopExit
                }
            },
            popEnterTransition = {
                // 返回时从左侧进入 (Shared Axis)
                ExpressiveAnimations.SharedAxisPopEnter
            },
            popExitTransition = {
                // 返回时向右滑出 (Shared Axis)
                ExpressiveAnimations.SharedAxisPopExit
            }
        ) {
            composable(Screen.FileBrowser.route) {
                FileBrowserScreen(
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    },
                    onNavigateToDirectory = { directoryUri ->
                        navController.navigate(Screen.DirectoryContent.createRoute(directoryUri))
                    },
                    onNavigateToSearch = { audioFiles ->
                        navController.navigate(Screen.FileSearch.createRoute(audioFiles.map { it.path }))
                    },
                    onNavigateToAlbum = { albumName, albumArtist ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumName, albumArtist))
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    bottomNavScrollProgress = scrollProgress,
                    onBottomBarScrollProgressChange = { progress ->
                        scrollProgress = progress
                    }
                )
            }

            composable(
                route = Screen.DirectoryContent.route,
                arguments = listOf(
                    navArgument("directoryUri") { type = NavType.StringType }
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
                DirectoryContentScreen(
                    directoryUri = directoryUri,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    }
                )
            }

            composable(
                route = Screen.FileSearch.route,
                arguments = listOf(
                    navArgument("filePaths") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPaths = backStackEntry.arguments?.getString("filePaths") ?: ""
                val filePaths = if (encodedPaths.isNotBlank()) {
                    encodedPaths.split(",").map { URLDecoder.decode(it, "UTF-8") }
                } else {
                    emptyList()
                }
                FileSearchScreen(
                    filePaths = filePaths,
                    onNavigateBack = { navController.popBackStack() },
                    onFileSelected = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath)) {
                            popUpTo(Screen.FileBrowser.route)
                        }
                    }
                )
            }

            composable(Screen.RecentEdits.route) {
                RecentEditsScreen(
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    },
                    bottomNavScrollProgress = scrollProgress,
                    onBottomBarScrollProgressChange = { progress ->
                        scrollProgress = progress
                    }
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToArtist = { artistName ->
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    bottomNavScrollProgress = scrollProgress,
                    onBottomBarScrollProgressChange = { progress ->
                        scrollProgress = progress
                    }
                )
            }

            composable(Screen.Settings.route) {
                val context = LocalContext.current
                SettingsScreen(
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
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                val pendingOnlineMetadata by backStackEntry.savedStateHandle
                    .getStateFlow<AudioMetadata?>("online_metadata_result", null)
                    .collectAsState()
                val pendingOnlineLyrics by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("online_lyrics_result", null)
                    .collectAsState()
                MetadataEditorScreen(
                    filePath = filePath,
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
                val filePaths = encodedPaths.split(",").map { URLDecoder.decode(it, "UTF-8") }
                ReplayGainScannerScreen(
                    filePaths = filePaths,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath)) {
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
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
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
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    }
                )
            }

        }

        // Bottom navigation bar positioned absolutely at the bottom
        // Uses Box alignment instead of Scaffold bottomBar to avoid fixed padding
        FlexibleBottomAppBar(
            navController = navController,
            currentRoute = currentDestination?.route,
            scrollProgress = scrollProgress,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
