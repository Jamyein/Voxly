package com.voxly.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pill height — a compact icon-only chip. */
private val ItemHeight = 56.dp
private val InactiveWidth = 56.dp

/**
 * Companion item for [FloatingToolbarNavigationBar].
 *
 * Horizontal nav item (M3E horizontal variant): the label sits **to the RIGHT of the icon**
 * and is shown **only on the active pill** — unused indicators are transparent icon-only
 * chips. The item animates its own **width** (`Modifier.widthIn(min = animatedWidth)`,
 * `animateDpAsState`): the selected pill widens to fit `[icon][label]` while the neighbors
 * stay compact, so the expanding pill **squeezes** into the bar. Because the bar is
 * `wrapContentWidth`, a switch expands one item and shrinks the other symmetrically — the
 * total width stays ~constant, so there is no weight-redistribution layout cascade (this
 * is what keeps the animation smooth).
 *
 * The width spring is underdamped (0.6), so the pill **overshoots past its resting width
 * ("回弹")** before settling. The label is gated on the pill having expanded enough to hold
 * it, so it never snaps in mid-animation.
 *
 * M3E styling applied:
 *   - Active pill `secondaryContainer`, rounded-capsule (`RoundedCornerShape(h/2)`);
 *     unused indicators transparent — the icon floats on the capsule container;
 *   - Icon 24 dp, filled when active / outlined when resting, active `onSecondaryContainer`
 *     (on the pill), resting `onSurfaceVariant`;
 *   - Active label bold (700) `onSecondaryContainer`, shown only while selected.
 *
 * Accessibility: the click target is a `Role.Tab` (mutually-exclusive navigation choices),
 * selectable via TalkBack; the icon's content description is the label.
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
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium

    // Selected pill width = this item's label + icon + gap + padding, measured once so the
    // animation never re-measures. (The three labels are close in width, so switches stay
    // near-symmetric and the bar's total width barely changes.)
    val selectedWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(label),
            style = labelStyle,
            maxLines = 1,
        ).size.width.toDp() + (24 + 8).dp + (16 * 2).dp
    }

    // Bouncy width spring: the underdamped overshoot is the pill "回弹". damping 0.6 is the
    // M3 Expressive fast-spatial token (0.6, 800); 800 stiffness keeps the same pop but
    // settles faster than the old hand-rolled 300.
    val itemWidthSpring = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()

    val targetWidth = if (selected) selectedWidth else InactiveWidth
    // First composition starts from the OPPOSITE width so the initial page loads animate
    // too; afterwards animateDpAsState just tracks target changes directly.
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { initial = false }
    val oppositeWidth = if (selected) InactiveWidth else selectedWidth
    val animatedWidth by animateDpAsState(
        targetValue = if (initial) oppositeWidth else targetWidth,
        animationSpec = itemWidthSpring,
        label = "floatingNavItemWidth",
    )
    // Show the label only once the pill has expanded enough to hold it — prevents the
    // content snapping in mid-animation.
    val showLabel = selected && animatedWidth >= selectedWidth - 2.dp

    Box(
        modifier = modifier
            .height(ItemHeight)
            .widthIn(min = animatedWidth)
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                shape = RoundedCornerShape(ItemHeight / 2),
            )
            // No ripple / state-layer indication — `indication = null` keeps the surface
            // clean; the width squeeze + pop is the selection feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            if (showLabel) {
                Text(
                    text = label,
                    // M3E type hierarchy: active label bold (700).
                    style = labelStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
