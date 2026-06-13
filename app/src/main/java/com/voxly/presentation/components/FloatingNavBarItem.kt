package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Companion item for [FloatingToolbarNavigationBar].
 *
 * We **do not** use `NavigationBarItem` here because it paints an internal icon state
 * layer (ripple + gray fade) on every selection — visible as a jarring gray flash when
 * the user switches pages. This composable paints the whole selected region as a single
 * capsule highlight instead, with a smooth (but very short) color cross-fade and **no
 * ripple**.
 *
 * Layout: a centered `Column` of `Icon` (22 dp) + `Text` (labelSmall), wrapped in a
 * `Box` that fills 1/3 of the parent `Row` (via `Modifier.weight(1f)`) so the three
 * destinations sit snug and equally-spaced inside the floating capsule.
 *
 * Selection visual: the whole `Box` background animates between `Color.Transparent` and
 * `secondaryContainer`, then clipped to `CircleShape` so the highlight reads as a pill
 * within the floating capsule.
 *
 * Accessibility: the click target is a `Role.Tab` (semantic role for mutually-exclusive
 * navigation choices), selectable via TalkBack.
 *
 * @param selected whether this destination is the current page.
 * @param onClick invoked when the user taps this item.
 * @param icon image vector shown when [selected] is `false`.
 * @param selectedIcon image vector shown when [selected] is `true` (typically the filled
 *   variant of [icon]).
 * @param label short label (e.g. "Files", "Albums", "Artists") — also used as the
 *   content-description for the icon.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.FloatingNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    // Smooth but short color cross-fade for the highlight pill. Kept subtle (120 ms)
    // so it reads as a single selection gesture rather than a flashy animation.
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
                      else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "floatingNavContainer"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            // Equal-share the parent Row's width — keeps the three destinations compact
            // and visually balanced inside the capsule.
            .weight(1f)
            .clip(CircleShape)
            .background(containerColor)
            // No ripple / state-layer indication — `indication = null` suppresses the
            // default ripple that NavigationBarItem would have produced on the icon.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}