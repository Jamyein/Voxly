package com.voxly.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 Background Theme
 *
 * Provides a unified way to handle background colors across the app.
 * Used for consistent background rendering in screens and components.
 *
 * Reference: Now in Android (Nia) Background implementation
 */

/**
 * Background theme configuration for the app
 */
data class BackgroundTheme(
    val containerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified
)

/**
 * Light background theme
 */
val BackgroundThemeLight = BackgroundTheme(
    containerColor = Color(0xFFFFFBFE),
    contentColor = Color(0xFF1C1B1F)
)

/**
 * Dark background theme
 */
val BackgroundThemeDark = BackgroundTheme(
    containerColor = Color(0xFF1C1B1F),
    contentColor = Color(0xFFE6E1E5)
)

/**
 * Expressive light background theme
 */
val ExpressiveBackgroundThemeLight = BackgroundTheme(
    containerColor = Color(0xFFFFFBFF),
    contentColor = Color(0xFF1D1B20)
)

/**
 * Expressive dark background theme
 */
val ExpressiveBackgroundThemeDark = BackgroundTheme(
    containerColor = Color(0xFF1D1B20),
    contentColor = Color(0xFFE6E1E5)
)

/**
 * CompositionLocal for background theme
 */
val LocalBackgroundTheme = staticCompositionLocalOf {
    BackgroundTheme()
}

/**
 * Background theme provider that automatically selects light/dark based on system theme
 */
@Composable
fun BackgroundThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    expressive: Boolean = true,
    content: @Composable () -> Unit
) {
    val backgroundTheme = when {
        expressive -> if (darkTheme) ExpressiveBackgroundThemeDark else ExpressiveBackgroundThemeLight
        else -> if (darkTheme) BackgroundThemeDark else BackgroundThemeLight
    }

    CompositionLocalProvider(
        LocalBackgroundTheme provides backgroundTheme,
        content = content
    )
}

/**
 * Background theme provider with explicit background theme
 */
@Composable
fun BackgroundThemeProvider(
    darkTheme: Boolean,
    expressive: Boolean,
    backgroundTheme: BackgroundTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBackgroundTheme provides backgroundTheme,
        content = content
    )
}
