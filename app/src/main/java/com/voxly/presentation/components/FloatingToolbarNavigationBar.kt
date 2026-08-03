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
import androidx.compose.foundation.layout.wrapContentWidth
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
 * A floating capsule (`surfaceContainerHigh`, `CircleShape`, 8 dp shadow) that hugs its
 * content width (the active pill widens to fit `[icon][label]`, unused indicators are
 * icon-only) and floats above the gesture-nav inset (`WindowInsets.safeDrawing` + 16 dp
 * lift).
 *
 * Smoothness (pattern from ReadYou's FloatingFilterBarRow): the row is `wrapContentWidth`
 * and each item animates its own **width** via `animateDpAsState` — on a switch one item
 * expands while the other shrinks symmetrically, so the total row width stays ~constant
 * and there is NO weight-redistribution layout cascade. The width spring carries the
 * bounce.
 *
 * M3E styling applied:
 *   - Horizontal nav items: each [FloatingNavBarItem] is a pill with the label to the
 *     RIGHT of the icon, shown only on the active indicator;
 *   - Active pill `secondaryContainer`, unused indicators transparent;
 *   - Active content `onSecondaryContainer`, resting `onSurfaceVariant`.
 *
 * Modifier chain (insets first, then the lift):
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
        // Capsule container: wraps the items' content width, drop shadow, fully-rounded.
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                // Cast a soft drop shadow so the pill reads as a physical object above
                // the content. Shadow shape must match the clip shape to render correctly.
                // 4dp keeps the lift while cutting the per-frame re-rasterization (the capsule
                // re-measures every frame the active pill's width animates) ~4x vs 8dp.
                .shadow(elevation = 4.dp, shape = CircleShape)
                // Force a fully-rounded Pill / Capsule silhouette.
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            // Custom Row with tight arrangement + selectableGroup for accessibility.
            Row(
                modifier = Modifier
                    .wrapContentWidth()
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
