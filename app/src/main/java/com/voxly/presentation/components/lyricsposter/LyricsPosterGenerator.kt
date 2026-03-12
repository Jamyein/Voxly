package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.Lyrics.Companion.parseToLines

/**
 * Generates lyrics poster images.
 */
object LyricsPosterGenerator {

    private const val MIN_POSTER_WIDTH = 720   // Minimum width
    private const val MAX_POSTER_WIDTH = 1440  // Maximum width limit
    private const val MIN_POSTER_HEIGHT = 1920  // 16:9 minimum
    private const val PADDING = 80f
    private const val COVER_SIZE = 440
    private const val MARGIN_BOTTOM = 100f
    private const val INFO_SECTION_WIDTH = 900f  // Width reserved for title/artist/album

    /**
     * Generates a lyrics poster bitmap.
     *
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional, displayed below artist)
     * @param lyricsText Raw lyrics text (can be LRC format or plain text)
     * @param albumArtBitmap Optional album art bitmap
     * @param backgroundColor Background color
     * @param contentColor Optional content (text) color. If null, auto-calculated from background for contrast
     * @param selectedLyrics List of selected lyric text for poster (preferred over indices)
     * @param selectedLyricIndices List of selected lyric indices for non-contiguous multi-line selection (fallback)
     * @param fontSizeScale Font size scale factor (1.0 = default)
     * @return Generated poster bitmap
     */
    fun generatePoster(
        title: String,
        artist: String,
        album: String = "",
        lyricsText: String,
        albumArtBitmap: Bitmap?,
        backgroundColor: androidx.compose.ui.graphics.Color,
        contentColor: androidx.compose.ui.graphics.Color? = null,
        selectedLyrics: List<String> = emptyList(),
        selectedLyricIndices: List<Int> = emptyList(),
        fontSizeScale: Float = 1.0f
    ): Bitmap {
        // Parse lyrics first to calculate dynamic height
        val allLyrics = parseToLines(lyricsText)
        val lyricsLines = when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(12)
            selectedLyricIndices.isNotEmpty() -> selectedLyricIndices.mapNotNull { allLyrics.getOrNull(it) }.take(12)
            else -> allLyrics.take(12)
        }

        // Calculate dynamic width based on content
        val baseFontSize = 84f * fontSizeScale

        // Estimate the widest line in lyrics for width calculation
        val widthEstimatePaint = Paint().apply { textSize = baseFontSize }
        val maxLyricsWidth = lyricsLines.maxOfOrNull { line ->
            breakTextToLines(line, widthEstimatePaint, 10000f).maxOf { wrapped ->
                widthEstimatePaint.measureText(wrapped)
            }
        } ?: 0f

        // Calculate minimum required width
        val infoAreaWidth = if (albumArtBitmap != null) COVER_SIZE + 32f + INFO_SECTION_WIDTH else INFO_SECTION_WIDTH
        val minRequiredWidth = PADDING + maxOf(infoAreaWidth, maxLyricsWidth) + PADDING

        // Dynamic width: between MIN and MAX, with some padding for safety
        val posterWidth = maxOf(minRequiredWidth.toInt(), MIN_POSTER_WIDTH).coerceAtMost(MAX_POSTER_WIDTH)
        val maxWidth = posterWidth - (PADDING * 2)

        val lineHeight = (128f * fontSizeScale)

        // Estimate lyrics wrapped lines count
        val tempPaint = Paint().apply { textSize = baseFontSize }
        val totalLyricsWrappedLines = lyricsLines.sumOf { line ->
            breakTextToLines(line, tempPaint, maxWidth).size
        }

        // Calculate required height
        val coverAreaHeight = PADDING + COVER_SIZE + 80f  // Cover + padding
        val infoAreaHeight = 300f  // Title/artist/album area
        val dividerAreaHeight = 80f  // Spacing between info and lyrics
        val lyricsAreaHeight = totalLyricsWrappedLines * lineHeight
        val bottomAreaHeight = MARGIN_BOTTOM + 60f  // Voxly text spacing

        val requiredHeight = coverAreaHeight + infoAreaHeight + dividerAreaHeight + lyricsAreaHeight + bottomAreaHeight
        val posterHeight = maxOf(MIN_POSTER_HEIGHT, requiredHeight.toInt())

        val bitmap = Bitmap.createBitmap(posterWidth, posterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        val bgPaint = Paint().apply {
            color = backgroundColor.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, posterWidth.toFloat(), posterHeight.toFloat(), bgPaint)

        // Calculate text color: use provided contentColor or auto-calculate for contrast
        val providedContentColor = contentColor?.toArgb()
        val isDarkBackground = ColorExtractor.isDarkColor(backgroundColor)
        val defaultTextColor = providedContentColor
            ?: if (isDarkBackground) Color.WHITE else Color.BLACK
        val secondaryTextColor = providedContentColor?.let { Color.argb(180, Color.red(it), Color.green(it), Color.blue(it)) }
            ?: if (isDarkBackground) Color.argb(180, 255, 255, 255) else Color.argb(180, 0, 0, 0)

        // ===== LAYOUT: Horizontal 16:9 =====

        // Draw album art on the LEFT side (vertical layout: top)
        if (albumArtBitmap != null) {
            val scaledCover = Bitmap.createScaledBitmap(albumArtBitmap, COVER_SIZE, COVER_SIZE, true)
            val coverPaint = Paint().apply {
                isAntiAlias = true
            }
            canvas.drawBitmap(scaledCover, PADDING, PADDING, coverPaint)
        }

        // Draw song info to the RIGHT of cover (horizontal layout)
        val titlePaint = Paint().apply {
            color = defaultTextColor
            textSize = 104f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val artistPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val albumPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Info starts to the right of cover
        val infoX = if (albumArtBitmap != null) PADDING + COVER_SIZE + 64f else PADDING
        val titleY = PADDING + 100f

        // Draw title
        canvas.drawText(breakText(title.take(40), titlePaint, INFO_SECTION_WIDTH), infoX, titleY, titlePaint)

        // Draw artist
        var artistY = titleY + 112f
        canvas.drawText(breakText(artist.take(50), artistPaint, INFO_SECTION_WIDTH), infoX, artistY, artistPaint)

        // Draw album if available
        var albumY = artistY + 84f
        if (album.isNotBlank()) {
            canvas.drawText(breakText(album.take(60), albumPaint, INFO_SECTION_WIDTH), infoX, albumY, albumPaint)
            albumY += 68f
        } else {
            albumY = artistY
        }

        // Draw lyrics below the song info
        val highlightFontSize = 104f * fontSizeScale

        val lyricsPaint = Paint().apply {
            color = defaultTextColor
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.05f
        }

        val coverRowBottom = maxOf(if (albumArtBitmap != null) PADDING + COVER_SIZE else 0f, albumY + 40f)
        val lyricsStartY = coverRowBottom + 40f

        lyricsLines.forEachIndexed { index, line ->
            // Split long lines into multiple lines
            val wrappedLines = breakTextToLines(line, lyricsPaint, maxWidth)
            wrappedLines.forEachIndexed { wrapIndex, wrappedLine ->
                val lineY = lyricsStartY + ((index + wrapIndex) * lineHeight)
                if (lineY < posterHeight - MARGIN_BOTTOM) {
                    // Highlight first line of first lyric
                    if (index == 0 && wrapIndex == 0) {
                        lyricsPaint.textSize = highlightFontSize
                        lyricsPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else {
                        lyricsPaint.textSize = baseFontSize
                        lyricsPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(wrappedLine, PADDING, lineY, lyricsPaint)
                }
            }
        }

        // Draw bottom info (Voxly watermark)
        val bottomPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bottomText = "Voxly"
        val bottomTextWidth = bottomPaint.measureText(bottomText)
        canvas.drawText(
            bottomText,
            (posterWidth - bottomTextWidth) / 2,
            posterHeight - 50f,
            bottomPaint
        )

        return bitmap
    }

    /**
     * Breaks text to fit within max width, returning the best fitting text.
     */
    private fun breakText(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty()) return text

        val measuredWidth = paint.measureText(text)
        if (measuredWidth <= maxWidth) return text

        // Binary search for the best fit
        var low = 0
        var high = text.length
        var bestLength = 0

        while (low <= high) {
            val mid = (low + high) / 2
            val testText = text.substring(0, mid)
            val testWidth = paint.measureText(testText)

            if (testWidth <= maxWidth) {
                bestLength = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return if (bestLength > 0) text.substring(0, bestLength) else text
    }

    /**
     * Breaks text into multiple lines to fit within max width.
     */
    private fun breakTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")

        val lines = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val measuredWidth = paint.measureText(remaining)
            if (measuredWidth <= maxWidth) {
                lines.add(remaining)
                break
            }

            // Binary search for max characters that fit
            var low = 0
            var high = remaining.length
            var bestLength = 0

            while (low <= high) {
                val mid = (low + high) / 2
                val testText = remaining.substring(0, mid)
                val testWidth = paint.measureText(testText)

                if (testWidth <= maxWidth) {
                    bestLength = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            // Find word boundary if possible
            if (bestLength > 0) {
                val cutPoint = remaining.substring(0, bestLength).lastIndexOf(' ')
                if (cutPoint > bestLength / 2) {
                    bestLength = cutPoint
                }
            }

            lines.add(remaining.substring(0, bestLength.coerceAtLeast(1)))
            remaining = remaining.substring(bestLength.coerceAtLeast(1)).trimStart()
        }

        return lines.ifEmpty { listOf("") }
    }
}
