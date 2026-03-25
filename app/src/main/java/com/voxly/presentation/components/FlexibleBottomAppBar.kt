package com.voxly.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.navigation.Albums
import com.voxly.presentation.navigation.Artists
import com.voxly.presentation.navigation.FileBrowser
import com.voxly.presentation.navigation.Settings

/**
 * Bottom navigation bar for the app using Navigation3.
 * Uses Material 3 Expressive ShortNavigationBar component (Flexible navigation bar).
 */
@Composable
fun FlexibleBottomAppBar(
    backStack: MutableList<NavKey>,
    currentKey: NavKey,
    modifier: Modifier = Modifier
) {
    // Bottom navigation keys
    val bottomNavKeys = remember {
        listOf(FileBrowser, Albums, Artists, Settings)
    }

    // Only show bottom bar on bottom nav routes
    if (currentKey in bottomNavKeys) {
        ShortNavigationBar(
            modifier = modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets.navigationBars,
            arrangement = ShortNavigationBarDefaults.arrangement
        ) {
            // File Browser
            val isFileBrowserSelected = currentKey == FileBrowser
            ShortNavigationBarItem(
                selected = isFileBrowserSelected,
                onClick = {
                    if (!isFileBrowserSelected) {
                        val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                        if (currentIndex >= 0) {
                            backStack[currentIndex] = FileBrowser
                        }
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = if (isFileBrowserSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                        contentDescription = "Files"
                    )
                },
                label = { Text("Files") },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Albums
            val isAlbumsSelected = currentKey == Albums
            ShortNavigationBarItem(
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
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Artists
            val isArtistsSelected = currentKey == Artists
            ShortNavigationBarItem(
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
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Settings
            val isSettingsSelected = currentKey == Settings
            ShortNavigationBarItem(
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
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
