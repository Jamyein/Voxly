@file:OptIn(ExperimentalFoundationStyleApi::class)

package com.voxly.presentation.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.StyleStateKey
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.state
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

// ==================== Style-state model ====================

/**
 * The three theme appearances a user can choose from. `System` resolves to Light/Dark
 * at render time based on the device setting.
 */
enum class AppThemeMode { System, Light, Dark }

/**
 * Custom [StyleStateKey] carrying the theme appearance a styleable component should render as.
 *
 * Lets Style definitions react to the user's theme selection with built-in `animate {}`
 * transitions at layout/draw time instead of composition-time conditional Modifiers.
 */
val ThemeModeKey = StyleStateKey(AppThemeMode.System)

/** Read/write the resolved theme mode on a [MutableStyleState]. */
var MutableStyleState.themeMode: AppThemeMode
    get() = this[ThemeModeKey]
    set(value) {
        this[ThemeModeKey] = value
    }

/** Activates [block] only while the component's [ThemeModeKey] state equals [mode]. */
fun StyleScope.themeMode(mode: AppThemeMode, block: () -> Unit) {
    state(ThemeModeKey, block) { key, state -> state[key] == mode }
}

// ==================== StyleScope → theme token bridges ====================

/**
 * The active Material 3 [ColorScheme], readable from any Style definition.
 *
 * Style lambdas run inside their own observation scope (layout/draw phase), so reading the
 * scheme here reacts to theme changes with minimal invalidation instead of recomposition.
 */
val StyleScope.materialColorScheme: ColorScheme
    get() = MaterialTheme.LocalMaterialTheme.currentValue.colorScheme

/** Active M3 typography, readable from Style definitions. */
val StyleScope.materialTypography: Typography
    get() = MaterialTheme.LocalMaterialTheme.currentValue.typography

/** Voxly's custom app-level background theme (CompositionLocal-backed). */
val StyleScope.voxlyBackgroundTheme: BackgroundTheme
    get() = LocalBackgroundTheme.currentValue

/** Voxly's custom icon-tint theme (CompositionLocal-backed). */
val StyleScope.voxlyTintTheme: TintTheme
    get() = LocalTintTheme.currentValue

// ==================== Component styles ====================

/**
 * Voxly component styles — the Styles API bridge between theme tokens and components.
 *
 * Styles own the visual/interactive properties (background, shape, borders, scale, content
 * padding) that were previously hardcoded per composable. Predefined interaction states
 * (`selected`, `pressed`, `hovered`, `disabled`) and the custom [ThemeModeKey] state switch
 * visuals with built-in `animate {}` at layout/draw time, skipping recomposition.
 */
object VoxlyStyles {
    /**
     * Theme-selector card. Morphs its background between the light/dark schemes based on
     * the resolved [ThemeModeKey] state, so selecting a theme animates the preview.
     */
    val themeSelectorCard: Style = Style {
        shape(ExpressiveShapes.Large)
        contentPadding(16.dp)
        themeMode(AppThemeMode.Light) {
            animate(spec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) {
                background(ExpressiveLightColorScheme.surfaceContainer)
            }
        }
        themeMode(AppThemeMode.Dark) {
            animate(spec = spring(dampingRatio = Spring.DampingRatioNoBouncy)) {
                background(ExpressiveDarkColorScheme.surfaceContainer)
            }
        }
    }

    /**
     * Single theme-mode tile. Selection/press/hover feedback is fully Style-driven:
     * `selected` lifts the tile with a primary border + container, `pressed` squashes it.
     */
    val themeTile: Style = Style {
        shape(ExpressiveShapes.Medium)
        background(materialColorScheme.surfaceContainerHighest)
        contentPadding(10.dp)
        pressed {
            animate(spec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) {
                scale(0.97f)
            }
        }
        hovered {
            animate {
                background(materialColorScheme.surfaceContainerHigh)
            }
        }
        selected {
            animate(spec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) {
                background(materialColorScheme.primaryContainer)
                border(2.dp, materialColorScheme.primary)
                scale(1.03f)
            }
        }
    }

    /**
     * Vertical settings card (ReplayGain-style option rows). Replaces the previous
     * `Surface(onClick = {})` hack — the Styles API now owns shape/background/padding.
     */
    val verticalSettingsCard: Style = Style {
        shape(ExpressiveShapes.Medium)
        background(materialColorScheme.surfaceContainer)
        contentPadding(16.dp)
    }

    /**
     * Shared default for M3-based settings rows. M3 components draw their own surface
     * (not Style-able), so the default adds the interaction states M3 rows can't express
     * (disabled fade) and is the caller override point via each row's `style` parameter.
     */
    val settingsRowStyle: Style = Style {
        disabled {
            alpha(0.5f)
        }
    }

    /**
     * Settings-section header title. Moves the title's typography/color tokens into a Style
     * so callers can restyle every section title through the `style` parameter.
     */
    val settingsSectionTitleStyle: Style = Style {
        textStyle(materialTypography.titleSmall)
        contentColor(materialColorScheme.onSurfaceVariant)
    }
}
