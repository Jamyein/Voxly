package com.voxly.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R

/**
 * Shared top-app-bar color themes. Every screen picks one of these instead of
 * hand-writing `TopAppBarDefaults.topAppBarColors(...)` blocks, so the three
 * duplicated color sets (library tabs, immersive details, defaults) live in
 * exactly one place.
 *
 * The metadata editor intentionally does NOT use these: it runs under a
 * dynamic-color theme whose container must follow background/onBackground from
 * the dynamic palette, so its top bar stays a bespoke implementation.
 */
enum class TopBarTheme {
    /** Library tabs: opaque surface that shifts to surfaceContainer once scrolled/collapsed. */
    Library,

    /** Immersive detail pages: ALWAYS transparent (hero gradient shows through, no scrim). */
    Immersive,

    /** Detail pages: transparent at rest, becomes surface once scrolled. */
    ImmersiveToSurface,

    /** Stock M3 colors. */
    Default
}

/** Resolve a [TopBarTheme] to concrete [TopAppBarColors]. */
@Composable
fun topBarColors(theme: TopBarTheme): TopAppBarColors = when (theme) {
    TopBarTheme.Library -> TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
    TopBarTheme.Immersive -> TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
    TopBarTheme.ImmersiveToSurface -> TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
    TopBarTheme.Default -> TopAppBarDefaults.topAppBarColors()
}

/**
 * Shared top app bar. Centralizes the back button (FilledTonalIconButton +
 * ArrowBack + 12dp leading inset) that was hand-written in 7+ screens, and the
 * color sets via [TopBarTheme].
 *
 * @param onBack when non-null, a back navigation icon is rendered (content
 *   description "Go back"); when null no navigation icon is shown.
 * @param large render a [LargeTopAppBar] instead of the regular [TopAppBar]
 *   (used by library tab headers: Albums / Artists / Files / directory).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxlyTopAppBar(
    title: @Composable () -> Unit,
    theme: TopBarTheme = TopBarTheme.Default,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val colors = topBarColors(theme)
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    }
    if (large) {
        LargeTopAppBar(
            title = title,
            scrollBehavior = scrollBehavior,
            colors = colors,
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = modifier
        )
    } else {
        TopAppBar(
            title = title,
            scrollBehavior = scrollBehavior,
            colors = colors,
            navigationIcon = navigationIcon,
            actions = actions,
            modifier = modifier
        )
    }
}
