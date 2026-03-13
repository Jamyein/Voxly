package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Simplified color extraction for lyrics poster background.
 * Provides MUTED (柔和) and VIBRANT (鲜艳) color options from album artwork.
 * Uses Android Palette API for color extraction.
 */
object ColorExtractor {

    /**
     * Extracted colors from album artwork
     * Similar to Rush's ExtractedColors data class
     */
    data class ExtractedColors(
        val backgroundDominant: Int = Color.DarkGray.toArgb(),    // 鲜艳背景色 (Vibrant)
        val contentDominant: Int = 0xFFFFFF,                     // 鲜艳文字色
        val backgroundMuted: Int = Color.DarkGray.toArgb(),       // 柔和背景色 (Muted)
        val contentMuted: Int = 0xFFFFFF                          // 柔和文字色
    )

    /**
     * Color theme options for poster - matching Rush's CardColors enum
     */
    enum class PosterColorTheme {
        MUTED,    // 柔和 - 使用 muted 颜色
        VIBRANT,  // 鲜艳 - 使用 vibrant 颜色
        CUSTOM    // 自定义 - 用户选择颜色
    }

    /**
     * Extracts Palette from bitmap - simplified version similar to Rush
     */
    private fun extractPalette(bitmap: Bitmap): Palette? {
        return try {
            Palette.from(bitmap).generate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts colors from album artwork - Rush's approach
     * Returns both vibrant and muted color sets with content colors for text contrast
     */
    fun extractColors(bitmap: Bitmap): ExtractedColors {
        val palette = extractPalette(bitmap)

        return palette?.let { p ->
            ExtractedColors(
                // Vibrant colors (鲜艳)
                backgroundDominant = Color(
                    p.vibrantSwatch?.rgb
                        ?: p.lightVibrantSwatch?.rgb
                        ?: p.darkVibrantSwatch?.rgb
                        ?: p.dominantSwatch?.rgb
                        ?: Color.DarkGray.toArgb()
                ).copy(alpha = 1f).toArgb(),

                contentDominant = Color(
                    p.vibrantSwatch?.bodyTextColor
                        ?: p.lightVibrantSwatch?.bodyTextColor
                        ?: p.darkVibrantSwatch?.bodyTextColor
                        ?: p.dominantSwatch?.bodyTextColor
                        ?: Color.White.toArgb()
                ).copy(alpha = 1f).toArgb(),

                // Muted colors (柔和) - 优先使用 lightMutedSwatch 以获得更柔和、更亮的效果
                backgroundMuted = Color(
                    p.lightMutedSwatch?.rgb   // 优先使用 lightMuted
                        ?: p.mutedSwatch?.rgb
                        ?: p.darkMutedSwatch?.rgb
                        ?: Color.DarkGray.toArgb()
                ).copy(alpha = 1f).toArgb(),

                contentMuted = Color(
                    p.lightMutedSwatch?.bodyTextColor   // 优先使用 lightMuted
                        ?: p.mutedSwatch?.bodyTextColor
                        ?: p.darkMutedSwatch?.bodyTextColor
                        ?: Color.White.toArgb()
                ).copy(alpha = 1f).toArgb()
            )
        } ?: ExtractedColors()
    }

    /**
     * Extracts the dominant color from a bitmap.
     */
    fun extractDominantColor(bitmap: Bitmap): Color {
        val palette = extractPalette(bitmap)
        return palette?.getDominantColor(DEFAULT_COLOR.toArgb())?.let { Color(it) }
            ?: DEFAULT_COLOR
    }

    /**
     * Determines if a color is dark (for contrast calculations).
     */
    fun isDarkColor(color: Color): Boolean {
        return getLightness(color) < 0.5f
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
     * Calculates the contrast ratio between two colors using WCAG 2.1 formula.
     * @return Contrast ratio (1-21)
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val l1 = getRelativeLuminance(foreground)
        val l2 = getRelativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Gets the relative luminance of a color (WCAG 2.1).
     */
    private fun getRelativeLuminance(color: Color): Float {
        val r = if (color.red <= 0.03928f) color.red / 12.92f else ((color.red + 0.055f) / 1.055f).let { (it.toDouble()).pow(2.4).toFloat() }
        val g = if (color.green <= 0.03928f) color.green / 12.92f else ((color.green + 0.055f) / 1.055f).let { (it.toDouble()).pow(2.4).toFloat() }
        val b = if (color.blue <= 0.03928f) color.blue / 12.92f else ((color.blue + 0.055f) / 1.055f).let { (it.toDouble()).pow(2.4).toFloat() }
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Gets the lightness component of a color (0-1).
     */
    private fun getLightness(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        return (max + min) / 2f
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
