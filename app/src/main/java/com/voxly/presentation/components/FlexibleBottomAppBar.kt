package com.voxly.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    // M3E optimized: use translationY instead of height to avoid re-layout
    // Calculate offset Y based on scroll progress (0 = visible, 1 = hidden)
    val bottomBarHeight = 80.dp

    // Animate offsetY using translationY (M3E best practice)
    val offsetY by animateDpAsState(
        targetValue = if (scrollProgress > 0.5f) bottomBarHeight else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottomBarOffset"
    )

    // Animate alpha changes smoothly with spring physics
    val animatedAlpha by animateFloatAsState(
        targetValue = (1f - scrollProgress).coerceIn(0f, 1f),
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

    if (showBottomBar && scrollProgress < 1f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(bottomBarHeight) // Fixed height, use translationY for hiding
                .graphicsLayer {
                    translationY = offsetY.toPx()
                    alpha = animatedAlpha
                }
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
                            // Only show label when bottom bar is mostly visible (scrollProgress < 0.3)
                            if (scrollProgress < 0.3f) {
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
