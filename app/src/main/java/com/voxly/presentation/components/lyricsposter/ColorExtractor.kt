package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

/**
 * Extracts colors from album artwork for lyrics poster background.
 */
object ColorExtractor {

    /**
     * Extracts the dominant color from a bitmap.
     * Falls back to a default color if extraction fails.
     */
    fun extractDominantColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getDominantColor(DEFAULT_COLOR.toArgb()).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Extracts a vibrant color from a bitmap.
     * Falls back to dominant color if no vibrant color is found.
     */
    fun extractVibrantColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getVibrantColor(
                palette.getDominantColor(DEFAULT_COLOR.toArgb())
            ).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Extracts a light vibrant color from a bitmap.
     * Useful for creating lighter backgrounds.
     */
    fun extractLightVibrantColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getLightVibrantColor(
                palette.getDominantColor(DEFAULT_COLOR.toArgb())
            ).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Extracts a dark vibrant color from a bitmap.
     * Useful for creating darker backgrounds.
     */
    fun extractDarkVibrantColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getDarkVibrantColor(
                palette.getDominantColor(DEFAULT_COLOR.toArgb())
            ).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Extracts a muted color from a bitmap.
     * Good for subtle backgrounds.
     */
    fun extractMutedColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getMutedColor(DEFAULT_COLOR.toArgb()).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Extracts a light muted color from a bitmap.
     * Good for creating light backgrounds.
     */
    fun extractLightMutedColor(bitmap: Bitmap): Color {
        return try {
            val palette = Palette.from(bitmap).generate()
            palette.getLightMutedColor(DEFAULT_COLOR.toArgb()).let { Color(it) }
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }

    /**
     * Determines if a color is dark (for contrast calculations).
     */
    fun isDarkColor(color: Color): Boolean {
        val red = color.red
        val green = color.green
        val blue = color.blue
        // Using luminance formula
        return (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5
    }

    /**
     * Adjusts a color for better background contrast.
     * If the background is dark, returns a lighter version of the color.
     * If the background is light, returns a darker version.
     */
    fun adjustColorForBackground(baseColor: Color, isDark: Boolean): Color {
        return if (isDark) {
            // Lighten the color for dark backgrounds
            Color(
                red = (baseColor.red + 0.3f).coerceAtMost(1f),
                green = (baseColor.green + 0.3f).coerceAtMost(1f),
                blue = (baseColor.blue + 0.3f).coerceAtMost(1f),
                alpha = baseColor.alpha
            )
        } else {
            // Darken the color for light backgrounds
            Color(
                red = (baseColor.red - 0.3f).coerceAtLeast(0f),
                green = (baseColor.green - 0.3f).coerceAtLeast(0f),
                blue = (baseColor.blue - 0.3f).coerceAtLeast(0f),
                alpha = baseColor.alpha
            )
        }
    }

    /**
     * Returns a contrasting text color (black or white) based on background.
     */
    fun getContrastingTextColor(backgroundColor: Color): Color {
        return if (isDarkColor(backgroundColor)) {
            Color.White
        } else {
            Color.Black
        }
    }

    /**
     * Default background color when no album art is available.
     */
    private val DEFAULT_COLOR = Color(0xFF1E1E2E) // Dark purple-gray

    /**
     * Predefined color options for manual selection.
     */
    val colorOptions = listOf(
        DEFAULT_COLOR,
        Color(0xFF6366F1), // Indigo
        Color(0xFFEC4899), // Pink
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEF4444), // Red
    )

    /**
     * Predefined text color options for lyrics.
     */
    val textColorOptions = listOf(
        Color.White,
        Color.Black,
        Color(0xFFFFFFFF), // White with slight transparency
        Color(0xFFFBBF24), // Amber
        Color(0xFFF472B6), // Pink
        Color(0xFF34D399), // Emerald
        Color(0xFF60A5FA), // Blue
        Color(0xFFA78BFA), // Violet
    )
}
