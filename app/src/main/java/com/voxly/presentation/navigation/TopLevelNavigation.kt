package com.voxly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        val currentRoute = currentStack.lastOrNull()
            ?: error("Route not found in stack")

        if (currentRoute == state.topLevelRoute) {
            return false
        }
        currentStack.removeLastOrNull()
        return true
    }
}

class NavigationState(
    val startRoute: NavKey,
    private val topLevelRouteState: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey
        get() = topLevelRouteState.value
        set(value) { topLevelRouteState.value = value }

    fun isAtTabRoot(): Boolean {
        val currentStack = backStacks[topLevelRoute] ?: return true
        return currentStack.lastOrNull() == topLevelRoute
    }

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider
            )
        }

        val startStack = decoratedEntries[startRoute] ?: emptyList()
        val topLevelStack = decoratedEntries[topLevelRoute] ?: emptyList()

        return if (topLevelRoute == startRoute) {
            topLevelStack
        } else {
            startStack + topLevelStack.drop(1)
        }
    }
}

private enum class TopLevelRouteKey { FILE_BROWSER, ALBUMS, ARTISTS, SETTINGS }

private fun NavKey.toRouteKey(): TopLevelRouteKey = when (this) {
    is FileBrowser -> TopLevelRouteKey.FILE_BROWSER
    is Albums -> TopLevelRouteKey.ALBUMS
    is Artists -> TopLevelRouteKey.ARTISTS
    is Settings -> TopLevelRouteKey.SETTINGS
    else -> TopLevelRouteKey.FILE_BROWSER
}

private fun TopLevelRouteKey.toNavKey(): NavKey = when (this) {
    TopLevelRouteKey.FILE_BROWSER -> FileBrowser
    TopLevelRouteKey.ALBUMS -> Albums
    TopLevelRouteKey.ARTISTS -> Artists
    TopLevelRouteKey.SETTINGS -> Settings
}

@Composable
fun rememberNavigationState(startRoute: NavKey): NavigationState {
    val startBackStack = rememberNavBackStack(startRoute)
    val albumsBackStack = rememberNavBackStack(Albums)
    val artistsBackStack = rememberNavBackStack(Artists)
    val settingsBackStack = rememberNavBackStack(Settings)

    val backStacks = remember(startRoute) {
        mapOf(
            FileBrowser to startBackStack,
            Albums to albumsBackStack,
            Artists to artistsBackStack,
            Settings to settingsBackStack
        )
    }

    val topLevelRouteKey by remember { mutableStateOf(startRoute.toRouteKey()) }
    val topLevelRouteState = remember(topLevelRouteKey) {
        mutableStateOf(topLevelRouteKey.toNavKey())
    }

    return NavigationState(
        startRoute = startRoute,
        topLevelRouteState = topLevelRouteState,
        backStacks = backStacks
    )
}
