package com.voxly.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 Tint Theme
 *
 * Provides tint colors for icons and components that need consistent coloring.
 * Tints are typically applied to monochromatic icons and some UI elements.
 *
 * Reference: Now in Android (Nia) Tint implementation
 */

/**
 * Tint theme configuration for the app
 */
data class TintTheme(
    val iconTint: Color = Color.Unspecified,
    val primaryTint: Color = Color.Unspecified,
    val secondaryTint: Color = Color.Unspecified,
    val tertiaryTint: Color = Color.Unspecified
)

/**
 * Light tint theme - uses standard MD3 tints
 */
val TintThemeLight = TintTheme(
    iconTint = Color(0xFF1C1B1F),
    primaryTint = Color(0xFF6750A4),
    secondaryTint = Color(0xFF625B71),
    tertiaryTint = Color(0xFF7D5260)
)

/**
 * Dark tint theme
 */
val TintThemeDark = TintTheme(
    iconTint = Color(0xFFE6E1E5),
    primaryTint = Color(0xFFD0BCFF),
    secondaryTint = Color(0xFFCCC2DC),
    tertiaryTint = Color(0xFFEFB8C8)
)

/**
 * Expressive light tint theme - more vibrant
 */
val ExpressiveTintThemeLight = TintTheme(
    iconTint = Color(0xFF1D1B20),
    primaryTint = Color(0xFF6B5E95),
    secondaryTint = Color(0xFF745D69),
    tertiaryTint = Color(0xFF7E5F58)
)

/**
 * Expressive dark tint theme - more vibrant
 */
val ExpressiveTintThemeDark = TintTheme(
    iconTint = Color(0xFFE6E1E5),
    primaryTint = Color(0xFFD4C1E8),
    secondaryTint = Color(0xFFE4BDC4),
    tertiaryTint = Color(0xFFEFC4BC)
)

/**
 * CompositionLocal for tint theme
 */
val LocalTintTheme = staticCompositionLocalOf {
    TintTheme()
}

/**
 * Tint theme provider that automatically selects light/dark based on system theme
 */
@Composable
fun TintThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    expressive: Boolean = true,
    content: @Composable () -> Unit
) {
    val tintTheme = when {
        expressive -> if (darkTheme) ExpressiveTintThemeDark else ExpressiveTintThemeLight
        else -> if (darkTheme) TintThemeDark else TintThemeLight
    }

    CompositionLocalProvider(
        LocalTintTheme provides tintTheme,
        content = content
    )
}

/**
 * Tint theme provider with explicit tint theme
 */
@Composable
fun TintThemeProvider(
    darkTheme: Boolean,
    expressive: Boolean,
    tintTheme: TintTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTintTheme provides tintTheme,
        content = content
    )
}
