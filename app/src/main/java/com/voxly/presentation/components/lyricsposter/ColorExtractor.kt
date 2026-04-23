package com.voxly.presentation.components.lyricsposter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Simplified color extraction for lyrics poster background.
 * Provides MUTED (柔和) and VIBRANT (鲜艳) color options from album artwork.
 * Uses Android Palette API for color extraction.
 */
object ColorExtractor {

    private val lightColorFilter = Palette.Filter { color, _ ->
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsl)
        // 过滤过浅颜色（容易干扰）
        hsl[2] > 0.1f && hsl[2] < 0.95f
    }

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
    private fun extractPalette(bitmap: Bitmap, hasFaces: Boolean = false): Palette? {
        return try {
            val builder = Palette.from(bitmap)
            builder.maximumColorCount(if (hasFaces) 24 else 16)
            builder.addFilter(lightColorFilter)
            // 对于大于 200x200 的图片，resize 到 150x150 面积以提升性能
            val area = bitmap.width * bitmap.height
            if (area > 40000) { // 200x200
                builder.resizeBitmapArea(22500) // ~150x150
            }
            builder.generate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Suspended version of extractPalette that runs on a background thread.
     */
    private suspend fun extractPaletteSuspend(bitmap: Bitmap, hasFaces: Boolean = false): Palette? {
        return withContext(Dispatchers.Default) {
            extractPalette(bitmap, hasFaces)
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
     * 
     * M3E 色彩规范：
     * - Primary: 高饱和度强调色，用于主要操作点
     * - onPrimary: primary 背景上的文字色，需 WCAG AA 4.5:1 对比度
     * - primaryContainer: primary 的浅色变体，用于次要强调
     * - Secondary: 辅助强调色，应独立于 primary
     * - Tertiary: 第三强调色，用于补充 primary 和 secondary
     * - Surface: 表面色，用于卡片等组件背景
     * - surfaceContainer: 层级容器色（低→最高，5 级）
     * - Background: 页面背景色
     * - Outline: 边框色
     */
    data class M3EColors(
        val primary: Int = 0,
        val onPrimary: Int = 0,
        val primaryContainer: Int = 0,
        val onPrimaryContainer: Int = 0,
        
        val secondary: Int = 0,
        val onSecondary: Int = 0,
        val secondaryContainer: Int = 0,
        val onSecondaryContainer: Int = 0,
        
        val tertiary: Int = 0,
        val onTertiary: Int = 0,
        
        val surface: Int = 0,
        val onSurface: Int = 0,
        val surfaceContainerLowest: Int = 0,
        val surfaceContainerLow: Int = 0,
        val surfaceContainer: Int = 0,
        val surfaceContainerHigh: Int = 0,
        val surfaceContainerHighest: Int = 0,
        
        val background: Int = 0,
        val onBackground: Int = 0,
        
        val outline: Int = 0,
        val outlineVariant: Int = 0,
        
        val isValid: Boolean = false
    )

    /**
     * Extracts M3E-compliant color scheme from album artwork.
     * Maps to Material 3 Expressive color slots for immersive editing experience.
     *
     * M3E 色彩映射规范：
     * - Primary: vibrantSwatch → 高饱和度强调色，用于主要操作点
     * - onPrimary: 根据 primary 亮度选择白/深色，需 WCAG AA 4.5:1
     * - primaryContainer: primary 的浅色变体 (alpha=0.15)
     * - Secondary: darkMutedSwatch → 独立于 primary 的辅助色
     * - Tertiary: dominantSwatch → 第三强调色
     * - Surface: background 色用于主要表面
     * - surfaceContainer 层级: 从 background 计算的 5 级深浅变化
     * - Background: lightMutedSwatch → 页面背景
     * - Outline: primary 的半透明版本
     */
    suspend fun extractM3EColors(bitmap: Bitmap): M3EColors {
        Log.d("ColorExtractor", "Starting M3E color extraction from bitmap: ${bitmap.width}x${bitmap.height}")

        val palette = withContext(Dispatchers.Default) {
            extractPalette(bitmap)
        } ?: run {
            Log.w("ColorExtractor", "Palette extraction returned null")
            return M3EColors()
        }

        Log.d("ColorExtractor", "Palette swatches: " +
            "vibrant=${palette.vibrantSwatch?.rgb?.toString(16)}, " +
            "lightVibrant=${palette.lightVibrantSwatch?.rgb?.toString(16)}, " +
            "darkVibrant=${palette.darkVibrantSwatch?.rgb?.toString(16)}, " +
            "muted=${palette.mutedSwatch?.rgb?.toString(16)}, " +
            "lightMuted=${palette.lightMutedSwatch?.rgb?.toString(16)}, " +
            "darkMuted=${palette.darkMutedSwatch?.rgb?.toString(16)}, " +
            "dominant=${palette.dominantSwatch?.rgb?.toString(16)}")

        val defaultSurface = 0xFFF5F5F5.toInt()
        val defaultOnSurface = 0xFF1C1B1F.toInt()

        // 1. Primary: 使用增强的 vibrant swatch
        val rawPrimary = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0
        val enhancedPrimary = if (rawPrimary != 0) {
            enhanceColor(rawPrimary, minSaturation = 0.7f, minBrightness = 0.4f)
        } else {
            0
        }

        // 2. onPrimary: 根据 primary 的亮度智能选择白/深色
        val onPrimary = if (enhancedPrimary != 0) {
            smartOnColor(enhancedPrimary)
        } else {
            defaultOnSurface
        }

        // 3. primaryContainer: primary 的浅色变体
        val primaryContainer = if (enhancedPrimary != 0) {
            Color(enhancedPrimary).copy(alpha = 0.15f).toArgb()
        } else {
            0
        }
        val onPrimaryContainer = 0xFF1C1B1F.toInt() // 深色，在浅色容器上提供高对比度

        // 4. Secondary: 使用 darkMuted (独立于 primary)
        val rawSecondary = palette.darkMutedSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: defaultSurface
        val enhancedSecondary = if (rawSecondary != 0) {
            enhanceColor(rawSecondary, minSaturation = 0.3f, minBrightness = 0.5f)
        } else {
            rawSecondary
        }
        val onSecondary = smartOnColor(enhancedSecondary)
        val secondaryContainer = Color(enhancedSecondary).copy(alpha = 0.15f).toArgb()
        val onSecondaryContainer = 0xFF1C1B1F.toInt() // 深色，在浅色容器上提供高对比度

        // 5. Tertiary: 使用 dominant swatch，降低饱和度增强以保持平衡
        // 独立回退链：避免与 primary 相同
        val rawTertiary = palette.dominantSwatch?.rgb ?: 0
        val enhancedTertiary = if (rawTertiary != 0) {
            enhanceColor(rawTertiary, minSaturation = 0.5f, minBrightness = 0.4f, saturationBoost = 1.1f)
        } else {
            // 独立回退：使用 secondary 的变体而非 primary
            enhanceColor(rawSecondary, minSaturation = 0.4f, minBrightness = 0.45f, saturationBoost = 1.15f)
        }
        val onTertiary = smartOnColor(enhancedTertiary)

        // 6. Background: lightMuted 用于页面背景
        val rawBackground = palette.lightMutedSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.darkMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: defaultSurface
        val onBackground = smartOnColor(rawBackground)

        // 7. Surface 层级: 基于 background 计算 5 级深浅
        val surfaceBase = rawBackground
        val surfaceContainerLowest = lightenColor(surfaceBase, 0.95f)
        val surfaceContainerLow = lightenColor(surfaceBase, 0.80f)
        val surfaceContainer = lightenColor(surfaceBase, 0.60f)
        val surfaceContainerHigh = lightenColor(surfaceBase, 0.40f)
        val surfaceContainerHighest = lightenColor(surfaceBase, 0.20f)

        // 8. Surface 和 onSurface
        val surface = Color(rawBackground).copy(alpha = 1.0f).toArgb()
        val onSurface = onBackground

        // 9. Outline: 基于 primary 的半透明边框
        val outline = if (enhancedPrimary != 0) {
            Color(enhancedPrimary).copy(alpha = 0.30f).toArgb()
        } else {
            0xFF1C1B1F.toInt()
        }
        val outlineVariant = if (surfaceContainer != 0) {
            Color(surfaceContainer).copy(alpha = 0.20f).toArgb()
        } else {
            0xFF1C1B1F.toInt()
        }

        val result = M3EColors(
            primary = enhancedPrimary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,

            secondary = enhancedSecondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,

            tertiary = enhancedTertiary,
            onTertiary = onTertiary,

            surface = surface,
            onSurface = onSurface,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,

            background = rawBackground,
            onBackground = onBackground,

            outline = outline,
            outlineVariant = outlineVariant,

            isValid = true
        )

        Log.d("ColorExtractor", "Final M3EColors: primary=${result.primary.toString(16)}, " +
            "onPrimary=${result.onPrimary.toString(16)}, secondary=${result.secondary.toString(16)}")
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
     * @param saturationBoost Saturation multiplier (default 1.3f)
     */
    private fun enhanceColor(
        colorInt: Int,
        minSaturation: Float,
        minBrightness: Float,
        saturationBoost: Float = 1.3f
    ): Int {
        val color = Color(colorInt)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(colorInt, hsv)

        // Boost saturation
        hsv[1] = (hsv[1] * saturationBoost).coerceIn(minSaturation, 1.0f)

        // Ensure minimum brightness
        hsv[2] = hsv[2].coerceAtLeast(minBrightness)

        return android.graphics.Color.HSVToColor(hsv)
    }

    /**
     * Smart on-color selection based on background luminance.
     * Returns white for dark backgrounds, dark color for light backgrounds.
     * Ensures WCAG AA 4.5:1 contrast ratio for primary/secondary/tertiary colors.
     */
    private fun smartOnColor(backgroundColor: Int): Int {
        val bgColor = Color(backgroundColor)
        val luminance = bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f

        return if (luminance > 0.5f) {
            val black = Color(0xFF000000.toInt())
            if (calculateContrastRatio(black, bgColor) >= 4.5f) {
                0xFF1C1B1F.toInt()
            } else {
                0xFF000000.toInt()
            }
        } else {
            val white = Color(0xFFFFFFFF.toInt())
            if (calculateContrastRatio(white, bgColor) >= 4.5f) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF1C1B1F.toInt()
            }
        }
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
    suspend fun extractM3EColorsFromBytes(
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
        return withContext(Dispatchers.Default) {
            extractM3EColors(bitmap)
        }
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

/**
 * Local dynamic Material 3 theme wrapper that applies M3E colors extracted from album artwork.
 * Mixes extracted colors with the default color scheme to preserve Material 3 design language
 * while adding album-specific personality.
 */
@Composable
fun DynamicM3ETheme(
    m3eColors: ColorExtractor.M3EColors?,
    content: @Composable () -> Unit
) {
    val defaultScheme = MaterialTheme.colorScheme
    val dynamicScheme = remember(m3eColors) {
        if (m3eColors?.isValid != true) defaultScheme
        else {
            val primaryColor = Color(m3eColors.primary)
            val onPrimaryColor = Color(m3eColors.onPrimary)
            val surfaceContainerColor = Color(m3eColors.surfaceContainer)
            val onSurfaceColor = Color(m3eColors.onSurface)
            val backgroundColor = Color(m3eColors.background)
            val onBackgroundColor = Color(m3eColors.onBackground)

            defaultScheme.copy(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryColor.copy(alpha = 0.15f),
                onPrimaryContainer = primaryColor,
                secondary = surfaceContainerColor,
                onSecondary = onSurfaceColor,
                secondaryContainer = surfaceContainerColor.copy(alpha = 0.15f),
                onSecondaryContainer = onSurfaceColor,
                surface = backgroundColor.copy(alpha = 0.30f),
                onSurface = onSurfaceColor,
                surfaceContainer = surfaceContainerColor.copy(alpha = 0.20f),
                surfaceContainerHigh = surfaceContainerColor.copy(alpha = 0.30f),
                surfaceContainerHighest = surfaceContainerColor.copy(alpha = 1.0f),
                background = backgroundColor.copy(alpha = 0.30f),
                onBackground = onBackgroundColor,
                outline = primaryColor.copy(alpha = 0.30f),
                outlineVariant = surfaceContainerColor.copy(alpha = 0.20f),
            )
        }
    }

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

@Composable
fun m3eFloatingToolbarColors(
    containerColor: Color,
    contentColor: Color = Color.Unspecified,
    secondaryContainerColor: Color = Color.Unspecified,
    onSecondaryContainerColor: Color = Color.Unspecified,
    fabContainerColor: Color = Color.Unspecified,
    fabContentColor: Color = Color.Unspecified,
): FloatingToolbarColors {
    return FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
        toolbarContainerColor = containerColor,
        toolbarContentColor = if (contentColor != Color.Unspecified) contentColor else Color.Unspecified,
        fabContainerColor = if (fabContainerColor != Color.Unspecified) fabContainerColor else Color.Unspecified,
        fabContentColor = if (fabContentColor != Color.Unspecified) fabContentColor else Color.Unspecified,
    )
}
