package com.voxly.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.navigation.Albums
import com.voxly.presentation.navigation.Artists
import com.voxly.presentation.navigation.FileBrowser
import com.voxly.presentation.navigation.Settings

/**
 * Flexible navigation bar for NavigationSuiteScaffold.
 * Uses standard NavigationBar with M3E styling.
 * Note: NavigationSuiteScaffold manages its own navigation bar internally,
 * so scroll-to-hide is not implemented here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlexibleBottomAppBar(
    backStack: MutableList<NavKey>,
    currentKey: NavKey?,
    modifier: Modifier = Modifier
) {
    val bottomNavKeys = remember {
        listOf(FileBrowser, Albums, Artists, Settings)
    }

    // Only show on bottom nav routes
    if (currentKey != null && currentKey in bottomNavKeys) {
        // M3E styling: compact NavigationBar with surfaceContainer background
        NavigationBar(
            modifier = modifier.fillMaxWidth(),
            tonalElevation = 0.dp, // M3E: flat visual hierarchy
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            windowInsets = WindowInsets.navigationBars
        ) {
            // Files
            val isFileSelected = currentKey == FileBrowser
            NavigationBarItem(
                selected = isFileSelected,
                onClick = {
                    if (!isFileSelected) {
                        val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                        if (currentIndex >= 0) {
                            backStack[currentIndex] = FileBrowser
                        }
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                        contentDescription = "Files"
                    )
                },
                label = { Text("Files") },
                // M3E: secondary container for active indicator
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Albums
            val isAlbumsSelected = currentKey == Albums
            NavigationBarItem(
                selected = isAlbumsSelected,
                onClick = {
                    if (!isAlbumsSelected) {
                        val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                        if (currentIndex >= 0) {
                            backStack[currentIndex] = Albums
                        }
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                        contentDescription = "Albums"
                    )
                },
                label = { Text("Albums") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Artists
            val isArtistsSelected = currentKey == Artists
            NavigationBarItem(
                selected = isArtistsSelected,
                onClick = {
                    if (!isArtistsSelected) {
                        val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                        if (currentIndex >= 0) {
                            backStack[currentIndex] = Artists
                        }
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                        contentDescription = "Artists"
                    )
                },
                label = { Text("Artists") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Settings
            val isSettingsSelected = currentKey == Settings
            NavigationBarItem(
                selected = isSettingsSelected,
                onClick = {
                    if (!isSettingsSelected) {
                        val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                        if (currentIndex >= 0) {
                            backStack[currentIndex] = Settings
                        }
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (isSettingsSelected) AppIcon.Settings.vector else AppIcon.SettingsOutlined.vector,
                        contentDescription = "Settings"
                    )
                },
                label = { Text("Settings") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
