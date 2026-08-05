package com.voxly.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The dynamic system navigation-bar inset (gesture bar ≈ 24dp, 3-button nav
 * ≈ 48dp). Everything else (8/12/16dp etc.) is decorative spacing that MAY be
 * hard-coded; the system inset itself must NEVER be.
 */
@Composable
fun navBarsBottomInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Standard list/grid `contentPadding` for library screens
 * (`start/end = 12dp`, `top = 8dp`, `bottom = 8dp + nav-bar inset`).
 *
 * Replaces the hand-written
 * `PaddingValues(start=12.dp, end=12.dp, top=8.dp, bottom=8.dp + WindowInsets.navigationBars...)`
 * blocks that were duplicated across Files / Albums / Artists.
 */
@Composable
fun libraryContentPadding(
    start: Dp = 12.dp,
    end: Dp = 12.dp,
    top: Dp = 8.dp,
    bottomGap: Dp = 8.dp
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottomGap + navBarsBottomInset()
)

/**
 * Modifier variant of [libraryContentPadding] for non-scrollable containers
 * that must clear the navigation bar (e.g. a Box hosting a list).
 */
fun Modifier.navBarsContentPadding(bottomGap: Dp = 8.dp): Modifier =
    padding(bottom = bottomGap).navigationBarsPadding()
