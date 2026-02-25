package com.voxly.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 Gradient Colors
 *
 * MD3 Expressive uses gradients for enhanced visual appeal.
 * Gradients are typically used for backgrounds, hero sections, and special UI elements.
 *
 * Reference: Now in Android (Nia) Gradient implementation
 */

/**
 * Gradient colors for light theme
 */
val GradientColorsLight = GradientColors(
    primary = listOf(
        Color(0xFF6750A4),
        Color(0xFF9A82DB)
    ),
    secondary = listOf(
        Color(0xFF625B71),
        Color(0xFF958DA5)
    ),
    tertiary = listOf(
        Color(0xFF7D5260),
        Color(0xFFB58392)
    ),
    surface = listOf(
        Color(0xFFFFFBFE),
        Color(0xFFF3EDF7)
    ),
    background = listOf(
        Color(0xFFFFFBFE),
        Color(0xFFE7E0EC)
    )
)

/**
 * Gradient colors for dark theme
 */
val GradientColorsDark = GradientColors(
    primary = listOf(
        Color(0xFFD0BCFF),
        Color(0xFF6750A4)
    ),
    secondary = listOf(
        Color(0xFFCCC2DC),
        Color(0xFF625B71)
    ),
    tertiary = listOf(
        Color(0xFFEFB8C8),
        Color(0xFF7D5260)
    ),
    surface = listOf(
        Color(0xFF1C1B1F),
        Color(0xFF2B2930)
    ),
    background = listOf(
        Color(0xFF1C1B1F),
        Color(0xFF2B2930)
    )
)

/**
 * Expressive gradient colors - more vibrant for light theme
 */
val ExpressiveGradientColorsLight = GradientColors(
    primary = listOf(
        Color(0xFF6B5E95),
        Color(0xFF9A8FC7)
    ),
    secondary = listOf(
        Color(0xFF745D69),
        Color(0xFFA68A95)
    ),
    tertiary = listOf(
        Color(0xFF7E5F58),
        Color(0xFFB08A82)
    ),
    surface = listOf(
        Color(0xFFFFFBFF),
        Color(0xFFF3EDF7)
    ),
    background = listOf(
        Color(0xFFFFFBFF),
        Color(0xFFE8E0F0)
    )
)

/**
 * Expressive gradient colors - more vibrant for dark theme
 */
val ExpressiveGradientColorsDark = GradientColors(
    primary = listOf(
        Color(0xFFD4C1E8),
        Color(0xFF6B5E95)
    ),
    secondary = listOf(
        Color(0xFFE4BDC4),
        Color(0xFF745D69)
    ),
    tertiary = listOf(
        Color(0xFFEFC4BC),
        Color(0xFF7E5F58)
    ),
    surface = listOf(
        Color(0xFF1D1B20),
        Color(0xFF2B282F)
    ),
    background = listOf(
        Color(0xFF1D1B20),
        Color(0xFF2B282F)
    )
)

/**
 * Data class holding gradient colors for different color roles
 */
data class GradientColors(
    val primary: List<Color> = emptyList(),
    val secondary: List<Color> = emptyList(),
    val tertiary: List<Color> = emptyList(),
    val surface: List<Color> = emptyList(),
    val background: List<Color> = emptyList()
) {
    /**
     * Returns true if any gradient colors are defined
     */
    fun any(): Boolean = primary.isNotEmpty() ||
        secondary.isNotEmpty() ||
        tertiary.isNotEmpty() ||
        surface.isNotEmpty() ||
        background.isNotEmpty()

    /**
     * Returns a gradient for the given color role
     */
    fun forColor(color: GradientColor): List<Color> = when (color) {
        GradientColor.PRIMARY -> primary
        GradientColor.SECONDARY -> secondary
        GradientColor.TERTIARY -> tertiary
        GradientColor.SURFACE -> surface
        GradientColor.BACKGROUND -> background
    }
}

/**
 * Color roles for gradients
 */
enum class GradientColor {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SURFACE,
    BACKGROUND
}

/**
 * CompositionLocal for gradient colors
 */
val LocalGradientColors = staticCompositionLocalOf {
    GradientColors()
}

/**
 * Gradient colors provider that automatically selects light/dark based on system theme
 */
@Composable
fun GradientColorsProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    expressive: Boolean = true,
    content: @Composable () -> Unit
) {
    val gradientColors = when {
        expressive -> if (darkTheme) ExpressiveGradientColorsDark else ExpressiveGradientColorsLight
        else -> if (darkTheme) GradientColorsDark else GradientColorsLight
    }

    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        content = content
    )
}

/**
 * Gradient colors provider with explicit light/dark selection
 */
@Composable
fun GradientColorsProvider(
    darkTheme: Boolean,
    expressive: Boolean,
    gradientColors: GradientColors,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        content = content
    )
}
