package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.screens.BatchOperationsScreen
import com.voxly.presentation.screens.DirectoryManagementScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.RecentEditsScreen
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.LyricsEditorScreen
import java.net.URLDecoder

/**
 * Main navigation host for the MP3 Tag Editor app.
 * Implements bottom navigation with Material Design 3 components.
 */
@Composable
fun MP3TagNavHost(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if bottom bar should be shown
    val showBottomBar = currentDestination?.route in listOf(
        Screen.FileBrowser.route,
        Screen.RecentEdits.route,
        Screen.BatchOperations.route
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        val label = stringResource(item.labelResId)
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    painter = appIconPainter(if (selected) item.selectedIcon else item.unselectedIcon),
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.FileBrowser.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.FileBrowser.route) {
                FileBrowserScreen(
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.RecentEdits.route) {
                RecentEditsScreen(
                    onNavigateToMetadata = { filePath ->
                        navController.navigate(Screen.MetadataEditor.createRoute(filePath))
                    }
                )
            }

            composable(Screen.BatchOperations.route) {
                BatchOperationsScreen(
                    onNavigateToReplayGain = { filePaths ->
                        navController.navigate(Screen.ReplayGainScanner.createRoute(filePaths))
                    }
                )
            }

            composable(Screen.Settings.route) {
                val context = LocalContext.current
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDirectoryManagement = {
                        navController.navigate(Screen.DirectoryManagement.route)
                    },
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
                    onCleanupLogs = {
                        val deletedCount = LogManager.cleanupOldLogs()
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
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.DirectoryManagement.route) {
                DirectoryManagementScreen(
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
                MetadataEditorScreen(
                    filePath = filePath,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOnlineMetadata = {
                        navController.navigate(Screen.OnlineMetadata.createRoute(filePath))
                    },
                    onNavigateToLyrics = { trackName, artistName ->
                        navController.navigate(Screen.LyricsEditor.createRoute(filePath, trackName, artistName))
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
                route = Screen.LyricsEditor.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("trackName") { type = NavType.StringType },
                    navArgument("artistName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val encodedTrack = backStackEntry.arguments?.getString("trackName") ?: ""
                val encodedArtist = backStackEntry.arguments?.getString("artistName") ?: ""
                
                LyricsEditorScreen(
                    filePath = URLDecoder.decode(encodedPath, "UTF-8"),
                    trackName = URLDecoder.decode(encodedTrack, "UTF-8"),
                    artistName = URLDecoder.decode(encodedArtist, "UTF-8"),
                    onNavigateBack = { navController.popBackStack() }
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
        screen = Screen.BatchOperations,
        labelResId = R.string.nav_batch_operations,
        selectedIcon = AppIcon.PlaylistAdd,
        unselectedIcon = AppIcon.PlaylistAddOutlined
    )
)
