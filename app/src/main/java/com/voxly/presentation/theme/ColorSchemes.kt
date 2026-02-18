package com.voxly.presentation.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 Expressive Color Schemes
 * 
 * MD3 Expressive is a playful theme where the source color's hue does not appear directly in the theme.
 * These color schemes include the new surface container colors (surfaceContainerLowest,
 * surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
 * which replace the old elevation-based surface colors.
 */

// Expressive Light Color Scheme (public for use in Theme.kt)
// Based on MD3 Expressive color system with surface container colors
val ExpressiveLightColorScheme = lightColorScheme(
    // Primary colors
    primary = Color(0xFF6B5E95),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0F0),
    onPrimaryContainer = Color(0xFF251F3A),
    
    // Secondary colors
    secondary = Color(0xFF745D69),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E5),
    onSecondaryContainer = Color(0xFF2C1B22),
    
    // Tertiary colors
    tertiary = Color(0xFF7E5F58),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3B1A14),
    
    // Error colors
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    // Background & Surface
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B20),
    
    // Surface Container Colors (MD3 Expressive tonal surface system)
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2F8),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFEDE6F2),
    surfaceContainerHighest = Color(0xFFE8E2EC),
    
    // Surface variant
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    
    // Outline colors
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    
    // Inverse colors
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF4),
    inversePrimary = Color(0xFFD4C1E8),
    
    // Surface tint
    surfaceTint = Color(0xFF6B5E95),
    
    // Scrim
    scrim = Color(0xFF000000)
)

// Expressive Dark Color Scheme (public for use in Theme.kt)
val ExpressiveDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = Color(0xFFD4C1E8),
    onPrimary = Color(0xFF3B3063),
    primaryContainer = Color(0xFF52467B),
    onPrimaryContainer = Color(0xFFE8E0F0),
    
    // Secondary colors
    secondary = Color(0xFFE4BDC4),
    onSecondary = Color(0xFF402F39),
    secondaryContainer = Color(0xFF584550),
    onSecondaryContainer = Color(0xFFFFD9E5),
    
    // Tertiary colors
    tertiary = Color(0xFFEFC4BC),
    onTertiary = Color(0xFF462A26),
    tertiaryContainer = Color(0xFF5E403B),
    onTertiaryContainer = Color(0xFFFFDAD2),
    
    // Error colors
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    // Background & Surface
    background = Color(0xFF1D1B20),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E1E5),
    
    // Surface Container Colors (MD3 Expressive tonal surface system)
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF363339),
    
    // Surface variant
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    
    // Outline colors
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    
    // Inverse colors
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6B5E95),
    
    // Surface tint
    surfaceTint = Color(0xFFD4C1E8),
    
    // Scrim
    scrim = Color(0xFF000000)
)
