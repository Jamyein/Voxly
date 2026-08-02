package com.voxly.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.presentation.theme.MaterialShapes

/**
 * Stable role colors (accent / on-accent / container / on-container) for a
 * given seed. Role 0/1/2 maps to primary/secondary/tertiary, so the same
 * title, name, or path always renders the same color.
 */
@Immutable
data class RoleAccent(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color
)

/** Resolves a role accent from a hash seed — same seed, same color. */
@Composable
fun rememberRoleAccent(seed: Any): RoleAccent = rememberRoleAccentAt(seed.hashCode().mod(3))

/** Resolves a role accent for an explicit role index (0/1/2). */
@Composable
fun rememberRoleAccentAt(roleIndex: Int): RoleAccent {
    val colorScheme = MaterialTheme.colorScheme
    return when (roleIndex.mod(3)) {
        0 -> RoleAccent(
            colorScheme.primary,
            colorScheme.onPrimary,
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer
        )
        1 -> RoleAccent(
            colorScheme.secondary,
            colorScheme.onSecondary,
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer
        )
        else -> RoleAccent(
            colorScheme.tertiary,
            colorScheme.onTertiary,
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer
        )
    }
}

/** Vertical accent → faded gradient used by role placeholders and badges. */
fun roleAccentGradient(accent: Color): Brush =
    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.72f)))

/**
 * Cookie9Sided badge with a role-accent gradient background and a centered
 * icon. Used for section titles, directory hero tiles, and empty cover art.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoleGradientBadge(
    painter: Painter,
    contentDescription: String?,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 30.dp,
    iconSize: Dp = 16.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(roleAccentGradient(accent)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = onAccent,
            modifier = Modifier.size(iconSize)
        )
    }
}
