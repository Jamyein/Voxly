package com.voxly.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Import MD3 Expressive color schemes
import com.voxly.presentation.theme.ExpressiveLightColorScheme
import com.voxly.presentation.theme.ExpressiveDarkColorScheme

// Import new theme components
import com.voxly.presentation.theme.BackgroundThemeProvider
import com.voxly.presentation.theme.ExpressiveBackgroundThemeDark
import com.voxly.presentation.theme.ExpressiveBackgroundThemeLight
import com.voxly.presentation.theme.ExpressiveGradientColorsDark
import com.voxly.presentation.theme.ExpressiveGradientColorsLight
import com.voxly.presentation.theme.ExpressiveTintThemeDark
import com.voxly.presentation.theme.ExpressiveTintThemeLight
import com.voxly.presentation.theme.GradientColorsProvider
import com.voxly.presentation.theme.TintThemeProvider

// Material 3 Motion
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

// Material Design 3 Color Schemes (Fallback for older Android)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4)
)

/**
 * MP3 Tag Editor theme with Material Design 3 Expressive support.
 * Supports dynamic colors on Android 12+, Expressive color schemes, and automatic dark theme.
 * Uses MD3 Expressive surface container colors and shapes for a more playful appearance.
 *
 * Integrates:
 * - GradientColors: For gradient backgrounds
 * - BackgroundTheme: For unified background handling
 * - TintTheme: For icon and component tints
 * - MotionScheme: For official MD3 motion animations
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MP3TagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ with dynamic color enabled
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Android 12+ without dynamic color - use MD3 Expressive static colors
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
        }
        // Android 11 and below - use fallback color schemes (no surface containers)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Get the appropriate background theme
    val backgroundTheme = if (darkTheme) ExpressiveBackgroundThemeDark else ExpressiveBackgroundThemeLight

    // Get the appropriate gradient colors
    val gradientColors = if (darkTheme) ExpressiveGradientColorsDark else ExpressiveGradientColorsLight

    // Get the appropriate tint theme
    val tintTheme = if (darkTheme) ExpressiveTintThemeDark else ExpressiveTintThemeLight

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Provide all theme contexts
    CompositionLocalProvider(
        // Background theme
        com.voxly.presentation.theme.LocalBackgroundTheme provides backgroundTheme,
        // Gradient colors
        com.voxly.presentation.theme.LocalGradientColors provides gradientColors,
        // Tint theme
        com.voxly.presentation.theme.LocalTintTheme provides tintTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,  // Use MD3 Expressive shapes
            motionScheme = motionScheme,
            content = content
        )
    }
}

/**
 * Alias for MP3TagTheme for backward compatibility
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoxlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit
) {
    MP3TagTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        motionScheme = motionScheme,
        content = content
    )
}

// ==================== Theme Previews ====================

@Preview
@Composable
fun LightThemePreview() {
    MP3TagTheme(darkTheme = false, dynamicColor = false) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialTheme.colorScheme.primary
        }
    }
}

@Preview
@Composable
fun DarkThemePreview() {
    MP3TagTheme(darkTheme = true, dynamicColor = false) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialTheme.colorScheme.primary
        }
    }
}

@Preview
@Composable
fun LightDynamicThemePreview() {
    MP3TagTheme(darkTheme = false, dynamicColor = true) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialTheme.colorScheme.primary
        }
    }
}

@Preview
@Composable
fun DarkDynamicThemePreview() {
    MP3TagTheme(darkTheme = true, dynamicColor = true) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialTheme.colorScheme.primary
        }
    }
}
