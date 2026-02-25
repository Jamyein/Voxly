package com.voxly.presentation.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxly.presentation.theme.BackgroundTheme
import com.voxly.presentation.theme.ExpressiveBackgroundThemeDark
import com.voxly.presentation.theme.ExpressiveBackgroundThemeLight
import com.voxly.presentation.theme.ExpressiveGradientColorsDark
import com.voxly.presentation.theme.ExpressiveGradientColorsLight
import com.voxly.presentation.theme.ExpressiveTintThemeDark
import com.voxly.presentation.theme.ExpressiveTintThemeLight
import com.voxly.presentation.theme.GradientColors
import com.voxly.presentation.theme.LocalBackgroundTheme
import com.voxly.presentation.theme.LocalGradientColors
import com.voxly.presentation.theme.LocalTintTheme
import com.voxly.presentation.theme.TintTheme
import java.util.Locale

/**
 * Custom annotation for theme previews
 *
 * Usage:
 * ```
 * @ThemePreviews
 * @Composable
 * fun MyComponentPreview() {
 *     MyComponent(...)
 * }
 * ```
 */
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.FUNCTION
)
annotation class ThemePreviews

/**
 * Theme preview utilities following Now in Android patterns
 */
object ThemePreviewUtils {
    /**
     * Creates a preview modifier with appropriate background
     */
    @Composable
    fun Modifier.niaBackground(
        modifier: Modifier = Modifier,
        containerColor: Color = MaterialTheme.colorScheme.surface,
        contentColor: Color = MaterialTheme.colorScheme.onSurface
    ): Modifier {
        val configuration = LocalConfiguration.current
        val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val backgroundColor = if (isDark) {
            containerColor
        } else {
            containerColor
        }

        return this
            .background(backgroundColor)
            .padding(16.dp)
    }
}

/**
 * Background component for previews
 *
 * Provides a consistent background for component previews
 */
@Composable
fun NiaBackground(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // Use appropriate surface color based on theme
    val surfaceColor = when {
        containerColor != MaterialTheme.colorScheme.surface -> containerColor
        isDark -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor),
        contentAlignment = Alignment.TopStart,
        content = content
    )
}

/**
 * NiaThemePreview - Base preview component that provides theme context
 *
 * Usage:
 * ```
 * @ThemePreviews
 * @Composable
 * fun MyComponentPreview() {
 *     NiaThemePreview {
 *         MyComponent(...)
 *     }
 * }
 * ```
 */
@Composable
fun NiaThemePreview(
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val locale = LocalConfiguration.current.locales[0]

    // Get the appropriate background theme based on locale and dark mode
    val backgroundTheme = if (isDark) {
        ExpressiveBackgroundThemeDark
    } else {
        ExpressiveBackgroundThemeLight
    }

    val gradientColors = if (isDark) {
        ExpressiveGradientColorsDark
    } else {
        ExpressiveGradientColorsLight
    }

    val tintTheme = if (isDark) {
        ExpressiveTintThemeDark
    } else {
        ExpressiveTintThemeLight
    }

    // Apply locale to configuration for proper text rendering
    val updatedConfig = configuration.apply {
        setLocale(locale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier.background(backgroundTheme.containerColor)
                } else {
                    Modifier
                }
            )
    ) {
        // Provide theme context
        BackgroundThemeProvider(
            darkTheme = isDark,
            expressive = true,
            backgroundTheme = backgroundTheme
        ) {
            GradientColorsProvider(
                darkTheme = isDark,
                expressive = true,
                gradientColors = gradientColors
            ) {
                TintThemeProvider(
                    darkTheme = isDark,
                    expressive = true,
                    tintTheme = tintTheme
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Light theme preview only
 */
@Composable
fun NiaThemePreviewLight(
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundTheme = ExpressiveBackgroundThemeLight
    val gradientColors = ExpressiveGradientColorsLight
    val tintTheme = ExpressiveTintThemeLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier.background(backgroundTheme.containerColor)
                } else {
                    Modifier
                }
            )
    ) {
        BackgroundThemeProvider(
            darkTheme = false,
            expressive = true,
            backgroundTheme = backgroundTheme
        ) {
            GradientColorsProvider(
                darkTheme = false,
                expressive = true,
                gradientColors = gradientColors
            ) {
                TintThemeProvider(
                    darkTheme = false,
                    expressive = true,
                    tintTheme = tintTheme
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Dark theme preview only
 */
@Composable
fun NiaThemePreviewDark(
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundTheme = ExpressiveBackgroundThemeDark
    val gradientColors = ExpressiveGradientColorsDark
    val tintTheme = ExpressiveTintThemeDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showBackground) {
                    Modifier.background(backgroundTheme.containerColor)
                } else {
                    Modifier
                }
            )
    ) {
        BackgroundThemeProvider(
            darkTheme = true,
            expressive = true,
            backgroundTheme = backgroundTheme
        ) {
            GradientColorsProvider(
                darkTheme = true,
                expressive = true,
                gradientColors = gradientColors
            ) {
                TintThemeProvider(
                    darkTheme = true,
                    expressive = true,
                    tintTheme = tintTheme
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Convenience function to access background theme in composables
 */
@Composable
fun rememberBackgroundTheme(): BackgroundTheme {
    return LocalBackgroundTheme.current
}

/**
 * Convenience function to access gradient colors in composables
 */
@Composable
fun rememberGradientColors(): GradientColors {
    return LocalGradientColors.current
}

/**
 * Convenience function to access tint theme in composables
 */
@Composable
fun rememberTintTheme(): TintTheme {
    return LocalTintTheme.current
}

/**
 * Helper composable for displaying preview info
 */
@Composable
fun PreviewInfo(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp
        ),
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
