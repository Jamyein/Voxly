package com.voxly.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
 * Uses Material 3 NavigationBar component.
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
        NavigationBar(
            modifier = modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentKey == item.key

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            // Find index of current bottom nav item and replace
                            val currentIndex = backStack.indexOfFirst { it in bottomNavKeys }
                            if (currentIndex >= 0) {
                                // Replace the current bottom nav item
                                backStack[currentIndex] = item.key
                            }
                        }
                    },
                    icon = {
                        androidx.compose.material3.Icon(
                            imageVector = if (selected) item.selectedIcon.vector else item.unselectedIcon.vector,
                            contentDescription = item.label
                        )
                    },
                    label = if (selected) {
                        {
                            Text(
                                text = item.label,
                                maxLines = 1
                            )
                        }
                    } else null,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

/**
 * Data class representing a bottom navigation item with icons.
 */
data class FlexibleBottomNavItem(
    val key: NavKey,
    val label: String,
    val selectedIcon: AppIcon,
    val unselectedIcon: AppIcon
)

/**
 * List of bottom navigation items.
 */
private val bottomNavItems = listOf(
    FlexibleBottomNavItem(
        key = FileBrowser,
        label = "Files",
        selectedIcon = AppIcon.Folder,
        unselectedIcon = AppIcon.FolderOutlined
    ),
    FlexibleBottomNavItem(
        key = Albums,
        label = "Albums",
        selectedIcon = AppIcon.Album,
        unselectedIcon = AppIcon.AlbumOutlined
    ),
    FlexibleBottomNavItem(
        key = Artists,
        label = "Artists",
        selectedIcon = AppIcon.Artist,
        unselectedIcon = AppIcon.ArtistOutlined
    ),
    FlexibleBottomNavItem(
        key = Settings,
        label = "Settings",
        selectedIcon = AppIcon.Settings,
        unselectedIcon = AppIcon.SettingsOutlined
    )
)
