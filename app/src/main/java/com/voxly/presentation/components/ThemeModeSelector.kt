@file:OptIn(ExperimentalFoundationStyleApi::class)

package com.voxly.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.theme.AppThemeMode
import com.voxly.presentation.theme.ExpressiveDarkColorScheme
import com.voxly.presentation.theme.ExpressiveLightColorScheme
import com.voxly.presentation.theme.ExpressiveShapes
import com.voxly.presentation.theme.VoxlyStyles
import com.voxly.presentation.theme.themeMode

/**
 * Theme-mode selector card for the Settings → Appearance section.
 *
 * A Styles API showcase: the card and its three preview tiles are custom components whose
 * visuals (background, shape, border, scale, press/hover/selected feedback) are defined in
 * [VoxlyStyles] and driven through `Modifier.styleable` with a custom [ThemeModeKey] state.
 * Selecting a tile morphs the card to that scheme with the Styles API's built-in `animate {}`
 * (layout/draw phase only — no recomposition of the style lambda).
 *
 * @param selectedMode persisted theme mode: "system" | "light" | "dark"
 */
@Composable
fun ThemeModeSelector(
    selectedMode: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    val systemDark = isSystemInDarkTheme()
    // Resolve "system" so the card preview always matches the appearance the app will use.
    val resolvedMode = when (selectedMode) {
        "dark" -> AppThemeMode.Dark
        "light" -> AppThemeMode.Light
        else -> if (systemDark) AppThemeMode.Dark else AppThemeMode.Light
    }
    val cardStyleState = rememberUpdatedStyleState(null) { it.themeMode = resolvedMode }
    val systemPreviewScheme = if (systemDark) ExpressiveDarkColorScheme else ExpressiveLightColorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .styleable(cardStyleState, VoxlyStyles.themeSelectorCard, style)
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.settings_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeModeTile(
                label = stringResource(R.string.settings_theme_light),
                icon = Icons.Filled.LightMode,
                previewScheme = ExpressiveLightColorScheme,
                isSelected = selectedMode == "light",
                onClick = { onSelected("light") },
                modifier = Modifier.weight(1f)
            )
            ThemeModeTile(
                label = stringResource(R.string.settings_theme_system),
                icon = Icons.Filled.BrightnessAuto,
                previewScheme = systemPreviewScheme,
                isSelected = selectedMode == "system",
                onClick = { onSelected("system") },
                modifier = Modifier.weight(1f)
            )
            ThemeModeTile(
                label = stringResource(R.string.settings_theme_dark),
                icon = Icons.Filled.DarkMode,
                previewScheme = ExpressiveDarkColorScheme,
                isSelected = selectedMode == "dark",
                onClick = { onSelected("dark") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One selectable theme tile: a mini app preview + label. The container's selection/press/hover
 * visuals are owned by [VoxlyStyles.themeTile] and applied through [Modifier.styleable]; the
 * mini preview is decorative content drawn on top.
 */
@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun ThemeModeTile(
    label: String,
    icon: ImageVector,
    previewScheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isSelected = isSelected }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .styleable(styleState, VoxlyStyles.themeTile, style),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mini app preview (decorative content; the tile container visuals come from the Style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(previewScheme.background, RoundedCornerShape(8.dp))
        ) {
            // Fake app bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(previewScheme.surfaceContainer)
            )
            // Fake now-playing card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .size(width = 22.dp, height = 8.dp)
                    .background(previewScheme.primaryContainer, RoundedCornerShape(3.dp))
            )
            // Selection check badge
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
