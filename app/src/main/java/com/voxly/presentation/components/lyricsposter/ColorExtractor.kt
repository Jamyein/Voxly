package com.voxly.presentation.components.lyricsposter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
     * Extracts all available colors from palette for gradient generation.
     * Returns a list of vibrant and muted colors with high contrast and distinct hues.
     */
    fun extractAllColors(bitmap: Bitmap): List<Color> {
        val palette = extractPalette(bitmap) ?: return listOf(DEFAULT_COLOR)
        
        val allColors = mutableListOf<Color>()
        
        // Add all vibrant variants
        palette.vibrantSwatch?.rgb?.let { allColors.add(Color(it)) }
        palette.lightVibrantSwatch?.rgb?.let { allColors.add(Color(it)) }
        palette.darkVibrantSwatch?.rgb?.let { allColors.add(Color(it)) }
        
        // Add all muted variants
        palette.mutedSwatch?.rgb?.let { allColors.add(Color(it)) }
        palette.lightMutedSwatch?.rgb?.let { allColors.add(Color(it)) }
        palette.darkMutedSwatch?.rgb?.let { allColors.add(Color(it)) }
        
        // Add dominant color
        palette.dominantSwatch?.rgb?.let { allColors.add(Color(it)) }
        
        // Filter colors to ensure high contrast and distinct hues
        return filterDiverseColors(allColors).ifEmpty { listOf(DEFAULT_COLOR) }
    }
    
    /**
     * Filters colors to keep only those with significant differences in RGB space.
     * Minimum distance threshold ensures distinct colors for gradient.
     */
    private fun filterDiverseColors(colors: List<Color>): List<Color> {
        if (colors.size <= 2) return colors.distinct()
        
        val distinctColors = colors.distinct()
        val selectedColors = mutableListOf<Color>()
        val minDistance = 80f // Minimum RGB distance threshold
        
        for (color in distinctColors) {
            if (selectedColors.isEmpty()) {
                selectedColors.add(color)
            } else {
                // Check if this color is sufficiently different from all selected colors
                val isDistinct = selectedColors.all { selected ->
                    calculateColorDistance(color, selected) >= minDistance
                }
                if (isDistinct) {
                    selectedColors.add(color)
                }
            }
        }
        
        // Ensure we have at least 2 colors for gradient
        return if (selectedColors.size >= 2) selectedColors else distinctColors.take(3)
    }
    
    /**
     * Calculates Euclidean distance between two colors in RGB space.
     */
    private fun calculateColorDistance(c1: Color, c2: Color): Float {
        val rDiff = (c1.red - c2.red) * 255
        val gDiff = (c1.green - c2.green) * 255
        val bDiff = (c1.blue - c2.blue) * 255
        return kotlin.math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff)
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
     * M3E color scheme extracted from album artwork.
     * Maps Palette swatches to Material 3 Expressive color slots.
     */
    data class M3EColors(
        val primary: Int = 0,
        val onPrimary: Int = 0,
        val surfaceContainer: Int = 0,
        val onSurface: Int = 0,
        val secondary: Int = 0,
        val onSecondary: Int = 0,
        val background: Int = 0,
        val onBackground: Int = 0,
        val isValid: Boolean = false
    )

    /**
     * Extracts M3E-compliant color scheme from album artwork.
     * Maps to Material 3 Expressive color slots for immersive editing experience.
     *
     * - Primary: vibrantSwatch for core action points
     * - SurfaceContainer: lightMutedSwatch for large background areas
     * - Secondary: dominantSwatch for auxiliary information
     * - Background: darkMutedSwatch for subtle background tint
     */
    fun extractM3EColors(bitmap: Bitmap): M3EColors {
        Log.d("ColorExtractor", "Starting color extraction from bitmap: ${bitmap.width}x${bitmap.height}")
        
        val palette = extractPalette(bitmap)
        if (palette == null) {
            Log.w("ColorExtractor", "Palette extraction returned null")
            return M3EColors()
        }

        Log.d("ColorExtractor", "Palette extracted successfully. Swatches: " +
            "vibrant=${palette.vibrantSwatch?.rgb?.toString(16)}, " +
            "lightVibrant=${palette.lightVibrantSwatch?.rgb?.toString(16)}, " +
            "darkVibrant=${palette.darkVibrantSwatch?.rgb?.toString(16)}, " +
            "muted=${palette.mutedSwatch?.rgb?.toString(16)}, " +
            "lightMuted=${palette.lightMutedSwatch?.rgb?.toString(16)}, " +
            "darkMuted=${palette.darkMutedSwatch?.rgb?.toString(16)}, " +
            "dominant=${palette.dominantSwatch?.rgb?.toString(16)}")

        val defaultSurface = 0xFFF5F5F5.toInt()
        val defaultOnSurface = 0xFF1C1B1F.toInt()

        // Extract raw colors from palette with fallback chain
        val rawPrimary = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0
        val rawBackground = palette.lightMutedSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.darkMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: defaultSurface

        Log.d("ColorExtractor", "Raw colors - primary=${rawPrimary.toString(16)}, background=${rawBackground.toString(16)}")

        // Enhance colors: boost saturation and ensure minimum brightness
        val enhancedPrimary = enhanceColor(rawPrimary, minSaturation = 0.6f, minBrightness = 0.3f)
        // Background: use raw muted color directly without lightening
        val enhancedBackground = rawBackground

        Log.d("ColorExtractor", "Enhanced colors - primary=${enhancedPrimary.toString(16)}, background=${enhancedBackground.toString(16)}")

        // Ensure high contrast text colors (WCAG AA minimum 4.5:1)
        val onPrimary = ensureContrast(enhancedPrimary, 0xFFFFFFFF.toInt(), 4.5f)
        val onBackground = ensureContrast(enhancedBackground, 0xFF1C1B1F.toInt(), 4.5f)

        val result = M3EColors(
            primary = enhancedPrimary,
            onPrimary = onPrimary,
            surfaceContainer = enhancedBackground,
            onSurface = onBackground,
            secondary = enhancedPrimary,
            onSecondary = onPrimary,
            background = enhancedBackground,
            onBackground = onBackground,
            isValid = true
        )

        Log.d("ColorExtractor", "Final M3EColors: $result")
        return result
    }

    /**
     * Lightens a color by mixing it with white.
     * @param factor 0.0 = original, 1.0 = pure white
     */
    private fun lightenColor(colorInt: Int, factor: Float): Int {
        val color = Color(colorInt)
        val white = Color(0xFFFFFFFF.toInt())
        return Color(
            red = color.red + (white.red - color.red) * factor,
            green = color.green + (white.green - color.green) * factor,
            blue = color.blue + (white.blue - color.blue) * factor,
            alpha = color.alpha
        ).toArgb()
    }

    /**
     * Enhances a color by boosting saturation and ensuring minimum brightness.
     * Returns a more vibrant and readable color.
     */
    private fun enhanceColor(colorInt: Int, minSaturation: Float, minBrightness: Float): Int {
        val color = Color(colorInt)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(colorInt, hsv)

        // Boost saturation
        hsv[1] = (hsv[1] * 1.3f).coerceIn(minSaturation, 1.0f)

        // Ensure minimum brightness
        hsv[2] = hsv[2].coerceAtLeast(minBrightness)

        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * Ensures text color has sufficient contrast against background.
     * Returns white or black based on which provides better contrast.
     */
    private fun ensureContrast(backgroundColor: Int, defaultTextColor: Int, minRatio: Float): Int {
        val bgColor = Color(backgroundColor)
        val white = Color(0xFFFFFFFF.toInt())
        val black = Color(0xFF000000.toInt())

        val whiteContrast = calculateContrastRatio(white, bgColor)
        val blackContrast = calculateContrastRatio(black, bgColor)

        return if (whiteContrast >= minRatio || whiteContrast >= blackContrast) {
            0xFFFFFFFF.toInt()
        } else if (blackContrast >= minRatio) {
            0xFF000000.toInt()
        } else {
            // If neither meets minimum, return the better one
            if (whiteContrast > blackContrast) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
    }

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
     * Extracts M3E color scheme from album artwork bytes.
     * Uses BitmapFactory with ARGB_8888 config to ensure software bitmap for Palette pixel access.
     * This is the reliable approach that doesn't depend on Coil's suspend execute API.
     *
     * @param bytes Raw album art bytes
     * @param size Target size for sampling (smaller = faster extraction)
     */
    fun extractM3EColorsFromBytes(
        bytes: ByteArray,
        size: Int = 200
    ): M3EColors {
        Log.d("ColorExtractor", "Decoding byte array, size=${bytes.size}")
        
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        Log.d("ColorExtractor", "Image dimensions: ${options.outWidth}x${options.outHeight}")

        var sampleSize = 1
        while (options.outWidth / sampleSize > size || options.outHeight / sampleSize > size) {
            sampleSize *= 2
        }
        Log.d("ColorExtractor", "Using sampleSize=$sampleSize")

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        if (bitmap == null) {
            Log.e("ColorExtractor", "Failed to decode bitmap from bytes")
            return M3EColors()
        }
        
        Log.d("ColorExtractor", "Bitmap decoded: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
        return extractM3EColors(bitmap)
    }

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
