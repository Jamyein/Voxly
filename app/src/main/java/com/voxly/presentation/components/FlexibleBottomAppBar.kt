package com.voxly.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.res.stringResource
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.navigation.Screen

/**
 * A flexible bottom app bar that dynamically adjusts its height and opacity based on scroll position.
 *
 * Features:
 * - Smooth height transition based on scroll direction
 * - Opacity fade as it scrolls away
 * - Optimized performance with derivedStateOf
 * - Persists across navigation changes (single instance)
 */
@Composable
fun FlexibleBottomAppBar(
    navController: NavHostController,
    currentRoute: String?,
    scrollProgress: Float,
    modifier: Modifier = Modifier
) {
    // Calculate height based on scroll progress (0 = full height, 1 = fully hidden)
    val targetHeight by remember(scrollProgress) {
        derivedStateOf {
            val minHeight = 0.dp   // Fully hidden
            val maxHeight = 80.dp  // Full height
            // Use smooth curve for more natural hiding effect
            val progress = scrollProgress.coerceIn(0f, 1f)
            val easedProgress = progress * progress * (3f - 2f * progress) // Smoothstep
            maxHeight - (easedProgress * maxHeight.value).dp
        }
    }

    // Calculate alpha based on scroll progress - fully hidden = 0 alpha
    val targetAlpha by remember(scrollProgress) {
        derivedStateOf {
            // Smooth fade out as it scrolls away, 0 when fully hidden
            (1f - scrollProgress).coerceIn(0f, 1f)
        }
    }

    // Animate height changes smoothly with spring physics (M3E style)
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottomBarHeight"
    )

    // Animate alpha changes smoothly with spring physics
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottomBarAlpha"
    )

    val bottomNavRoutes = listOf(
        Screen.FileBrowser.route,
        Screen.RecentEdits.route,
        Screen.Statistics.route,
        Screen.Settings.route
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentRoute in bottomNavRoutes

    if (showBottomBar && animatedHeight > 0.dp) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .alpha(animatedAlpha)
                .height(animatedHeight)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true
                    val label = stringResource(item.labelResId)

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = if (selected) item.selectedIcon.vector else item.unselectedIcon.vector,
                                contentDescription = label
                            )
                        },
                        label = {
                            // Only show label when height is sufficient (> 60dp)
                            if (animatedHeight > 60.dp) {
                                Text(
                                    text = label,
                                    maxLines = 1
                                )
                            }
                        },
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
}

/**
 * Data class representing a bottom navigation item with icons.
 */
data class FlexibleBottomNavItem(
    val screen: Screen,
    val labelResId: Int,
    val selectedIcon: AppIcon,
    val unselectedIcon: AppIcon
)

/**
 * List of bottom navigation items.
 */
private val bottomNavItems = listOf(
    FlexibleBottomNavItem(
        screen = Screen.FileBrowser,
        labelResId = R.string.nav_file_browser,
        selectedIcon = AppIcon.Folder,
        unselectedIcon = AppIcon.FolderOutlined
    ),
    FlexibleBottomNavItem(
        screen = Screen.RecentEdits,
        labelResId = R.string.nav_recent_edits,
        selectedIcon = AppIcon.History,
        unselectedIcon = AppIcon.HistoryOutlined
    ),
    FlexibleBottomNavItem(
        screen = Screen.Statistics,
        labelResId = R.string.nav_statistics,
        selectedIcon = AppIcon.BarChart,
        unselectedIcon = AppIcon.BarChart
    ),
    FlexibleBottomNavItem(
        screen = Screen.Settings,
        labelResId = R.string.nav_settings,
        selectedIcon = AppIcon.Settings,
        unselectedIcon = AppIcon.SettingsOutlined
    )
)
