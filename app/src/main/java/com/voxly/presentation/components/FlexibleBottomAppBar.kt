package com.voxly.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemColors
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
            bottomNavItems.forEach { item ->
                val selected = currentKey == item.key

                ShortNavigationBarItem(
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
                    label = {
                        Text(
                            text = item.label,
                            maxLines = 1
                        )
                    },
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
