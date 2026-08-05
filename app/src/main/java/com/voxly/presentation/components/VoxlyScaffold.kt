package com.voxly.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Unified screen scaffold (thin wrapper over M3 [Scaffold]).
 *
 * Locks in the project-wide conventions so screens stop re-deciding them:
 *   - `containerColor` defaults to the theme background;
 *   - `contentWindowInsets` defaults to [WindowInsets(0)] — **edge-to-edge
 *     convention**: the M3 top bar handles the status bar itself, and every
 *     scrollable content applies its own explicit bottom inset via
 *     [libraryContentPadding] / [Modifier.navBarsContentPadding], so the
 *     system-bar insets are never double-applied and never leak into
 *     `innerPadding` semantics.
 *
 * Screens with a custom `containerColor` (transparent detail pages, gradient
 * heroes) or special insets (IME handling) pass their own values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxlyScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content
    )
}
