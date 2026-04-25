package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class MetadataEditorDynamicPalette(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int
)

private data class PaletteCandidate(
    val color: Int,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val population: Int
)

internal object MetadataEditorDynamicPaletteResolver {
    suspend fun resolve(
        albumArtBytes: ByteArray?,
        fallbackBitmap: Bitmap?,
        isDarkTheme: Boolean
    ): MetadataEditorDynamicPalette? {
        return withContext(Dispatchers.Default) {
            val bitmap = decodeBitmap(albumArtBytes) ?: fallbackBitmap ?: return@withContext null
            val palette = buildPalette(bitmap) ?: return@withContext null
            val candidates = extractCandidates(palette).ifEmpty { return@withContext null }

            val primaryCandidate = candidates.maxByOrNull { scorePrimary(it, candidates, isDarkTheme) } ?: return@withContext null
            val secondaryCandidate = chooseSupportingCandidate(
                candidates = candidates,
                anchorHues = listOf(primaryCandidate.hue),
                minHueDistance = 24f
            ) ?: primaryCandidate
            val tertiaryCandidate = chooseSupportingCandidate(
                candidates = candidates,
                anchorHues = listOf(primaryCandidate.hue, secondaryCandidate.hue),
                minHueDistance = 36f
            ) ?: secondaryCandidate

            MetadataEditorDynamicPalette(
                primary = tuneAccent(primaryCandidate.color, isDarkTheme, isPrimary = true),
                secondary = tuneAccent(secondaryCandidate.color, isDarkTheme, isPrimary = false),
                tertiary = tuneAccent(tertiaryCandidate.color, isDarkTheme, isPrimary = false)
            )
        }
    }

    private fun decodeBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        var sampleSize = 1
        val targetSize = 280
        while (
            boundsOptions.outWidth / sampleSize > targetSize ||
            boundsOptions.outHeight / sampleSize > targetSize
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun buildPalette(bitmap: Bitmap): Palette? {
        return runCatching {
            Palette.from(bitmap)
                .maximumColorCount(24)
                .resizeBitmapArea(40_000)
                .addFilter { color, _ ->
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(color, hsl)
                    val saturation = hsl[1]
                    val lightness = hsl[2]
                    saturation >= 0.08f && lightness in 0.08f..0.93f
                }
                .generate()
        }.getOrNull()
    }

    private fun extractCandidates(palette: Palette): List<PaletteCandidate> {
        val swatches = buildList {
            palette.vibrantSwatch?.let(::add)
            palette.lightVibrantSwatch?.let(::add)
            palette.darkVibrantSwatch?.let(::add)
            palette.mutedSwatch?.let(::add)
            palette.lightMutedSwatch?.let(::add)
            palette.darkMutedSwatch?.let(::add)
            palette.dominantSwatch?.let(::add)
        }.distinctBy { it.rgb }

        return swatches.map { swatch ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(swatch.rgb, hsl)
            PaletteCandidate(
                color = swatch.rgb,
                hue = hsl[0],
                saturation = hsl[1],
                lightness = hsl[2],
                population = swatch.population
            )
        }
    }

    private fun scorePrimary(
        candidate: PaletteCandidate,
        all: List<PaletteCandidate>,
        isDarkTheme: Boolean
    ): Float {
        val maxPopulation = all.maxOfOrNull { it.population }?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val normalizedPopulation = candidate.population / maxPopulation
        val targetLightness = if (isDarkTheme) 0.68f else 0.42f
        val lightnessFitness = (1f - (abs(candidate.lightness - targetLightness) / 0.5f)).coerceIn(0f, 1f)
        return (normalizedPopulation * 0.45f) + (candidate.saturation * 0.35f) + (lightnessFitness * 0.20f)
    }

    private fun chooseSupportingCandidate(
        candidates: List<PaletteCandidate>,
        anchorHues: List<Float>,
        minHueDistance: Float
    ): PaletteCandidate? {
        return candidates
            .filter { candidate ->
                anchorHues.all { anchor -> hueDistance(candidate.hue, anchor) >= minHueDistance }
            }
            .maxByOrNull { it.saturation * 0.6f + it.population * 0.4f }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val diff = abs(a - b)
        return if (diff > 180f) 360f - diff else diff
    }

    private fun tuneAccent(
        color: Int,
        isDarkTheme: Boolean,
        isPrimary: Boolean
    ): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val targetLightness = when {
            isPrimary && isDarkTheme -> 0.70f
            isPrimary && !isDarkTheme -> 0.42f
            !isPrimary && isDarkTheme -> 0.66f
            else -> 0.46f
        }
        val minSaturation = if (isPrimary) 0.40f else 0.26f
        val maxSaturation = if (isPrimary) 0.86f else 0.72f

        hsl[1] = hsl[1].coerceIn(minSaturation, maxSaturation)
        hsl[2] = (hsl[2] * 0.45f + targetLightness * 0.55f).coerceIn(0.20f, 0.84f)
        return ColorUtils.HSLToColor(hsl)
    }
}

@Composable
internal fun MetadataEditorDynamicTheme(
    dynamicPalette: MetadataEditorDynamicPalette?,
    content: @Composable () -> Unit
) {
    val base = MaterialTheme.colorScheme
    val dynamicColorScheme = remember(base, dynamicPalette) {
        if (dynamicPalette == null) {
            base
        } else {
            val primary = dynamicPalette.primary
            val secondary = dynamicPalette.secondary
            val tertiary = dynamicPalette.tertiary

            val primaryContainer = ColorUtils.blendARGB(primary, base.surface.toArgb(), 0.76f)
            val secondaryContainer = ColorUtils.blendARGB(secondary, base.surface.toArgb(), 0.78f)
            val surfaceTint = ColorUtils.blendARGB(base.surface.toArgb(), primary, 0.07f)
            val backgroundTint = ColorUtils.blendARGB(base.background.toArgb(), primary, 0.05f)

            base.copy(
                primary = Color(primary),
                onPrimary = Color(bestContentColor(primary)),
                primaryContainer = Color(primaryContainer),
                onPrimaryContainer = Color(bestContentColor(primaryContainer)),
                secondary = Color(secondary),
                onSecondary = Color(bestContentColor(secondary)),
                secondaryContainer = Color(secondaryContainer),
                onSecondaryContainer = Color(bestContentColor(secondaryContainer)),
                tertiary = Color(tertiary),
                onTertiary = Color(bestContentColor(tertiary)),
                surface = Color(surfaceTint),
                surfaceContainerLowest = Color(ColorUtils.blendARGB(base.surfaceContainerLowest.toArgb(), primary, 0.04f)),
                surfaceContainerLow = Color(ColorUtils.blendARGB(base.surfaceContainerLow.toArgb(), primary, 0.05f)),
                surfaceContainer = Color(ColorUtils.blendARGB(base.surfaceContainer.toArgb(), primary, 0.06f)),
                surfaceContainerHigh = Color(ColorUtils.blendARGB(base.surfaceContainerHigh.toArgb(), primary, 0.07f)),
                surfaceContainerHighest = Color(ColorUtils.blendARGB(base.surfaceContainerHighest.toArgb(), primary, 0.08f)),
                background = Color(backgroundTint),
                outline = Color(ColorUtils.blendARGB(base.outline.toArgb(), secondary, 0.28f)),
                outlineVariant = Color(ColorUtils.blendARGB(base.outlineVariant.toArgb(), secondary, 0.22f))
            )
        }
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

private fun bestContentColor(backgroundColor: Int): Int {
    val whiteContrast = ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), backgroundColor)
    val darkContrast = ColorUtils.calculateContrast(0xFF1B1B1F.toInt(), backgroundColor)
    return if (whiteContrast >= darkContrast) 0xFFFFFFFF.toInt() else 0xFF1B1B1F.toInt()
}
