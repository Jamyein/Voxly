package com.voxly.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Floating Pill / Capsule bottom navigation bar (M3E style).
 *
 * Implements the M3E expressive "悬浮药丸状 (Floating Pill / Capsule)" pattern documented
 * at `docs/实现"悬浮药丸状（Floating Pill  Capsule）"的底部导航栏.md`, kept compact per
 * design iteration:
 *
 *   - **Width = 65 % of screen** (down from 85 %) so the pill reads as a small floating
 *     object rather than a near-full-width bar;
 *   - **Height ≈ 56 dp** (down from ~80 dp) via a tight 48 dp item touch target + 4 dp
 *     vertical padding. Small enough to feel like a capsule, large enough for a11y;
 *   - **2 dp inter-item gap** — destinations sit visibly snug together;
 *   - 8 dp shadow, 4 dp tonal elevation, `surfaceContainerHigh` fill, `CircleShape` clip.
 *
 * We render this as `Surface` + `Row` (not `NavigationBar`) because:
 *   1. `NavigationBar`'s `Arrangement.spacedBy` is hard-coded and too loose;
 *   2. `NavigationBarItem` paints an icon-state-layer ripple that animates on every tap —
 *      that gray flash on switch is exactly what we want to avoid. Callers should use
 *      [FloatingNavBarItem] (the matching companion in this package) instead, which has
 *      no built-in ripple.
 *
 * Modifier chain (per the design doc — insets first, then the lift):
 *
 * ```
 * .windowInsetsPadding(WindowInsets.safeDrawing)  // status + gesture nav
 * .padding(bottom = 16.dp)                         // extra lift above gesture nav
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingToolbarNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 1. Reserve space for status bar + gesture-nav inset.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // 2. Lift the pill 16 dp above the gesture-nav top edge so it visibly floats.
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Capsule container: 65 % screen width, drop shadow, fully-rounded clip.
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                // Cast a soft drop shadow so the pill reads as a physical object above
                // the content. Shadow shape must match the clip shape to render correctly.
                .shadow(elevation = 8.dp, shape = CircleShape)
                // Force a fully-rounded Pill / Capsule silhouette.
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            // Custom Row with tight arrangement + selectableGroup for accessibility.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // selectableGroup() lets screen readers know the items are mutually
                    // exclusive choices (the standard NavigationBar behaviour).
                    .selectableGroup()
                    // Match the 48 dp minimum touch-target guideline while staying compact.
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 2.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}