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

    private const val POSTER_WIDTH = 1080
    private const val POSTER_HEIGHT = 1920
    private const val PADDING = 60f
    private const val COVER_SIZE = 200
    private const val MARGIN_BOTTOM = 120f

    /**
     * Generates a lyrics poster bitmap.
     *
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional, displayed below artist)
     * @param lyricsText Raw lyrics text (can be LRC format or plain text)
     * @param albumArtBitmap Optional album art bitmap
     * @param backgroundColor Background color
     * @param textColor Custom text color (null for auto-detect)
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
        textColor: androidx.compose.ui.graphics.Color? = null,
        selectedLyrics: List<String> = emptyList(),
        selectedLyricIndices: List<Int> = emptyList(),
        fontSizeScale: Float = 1.0f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        val bgPaint = Paint().apply {
            color = backgroundColor.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, POSTER_WIDTH.toFloat(), POSTER_HEIGHT.toFloat(), bgPaint)

        // Calculate text color based on background or use custom color
        val isDarkBackground = ColorExtractor.isDarkColor(backgroundColor)
        val defaultTextColor = if (isDarkBackground) Color.WHITE else Color.BLACK
        val effectiveTextColor = textColor?.toArgb() ?: defaultTextColor
        val secondaryTextColor = if (isDarkBackground) Color.argb(180, 255, 255, 255) else Color.argb(180, 0, 0, 0)

        // Draw album art in corner if available
        if (albumArtBitmap != null) {
            val scaledCover = Bitmap.createScaledBitmap(albumArtBitmap, COVER_SIZE, COVER_SIZE, true)
            val coverPaint = Paint().apply {
                isAntiAlias = true
            }
            canvas.drawBitmap(scaledCover, PADDING, PADDING, coverPaint)
        }

        // Draw song info
        val titlePaint = Paint().apply {
            color = effectiveTextColor
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val artistPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 36f
            isAntiAlias = true
        }

        val albumPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 28f
            isAntiAlias = true
        }

        val infoX = if (albumArtBitmap != null) PADDING + COVER_SIZE + 24f else PADDING
        val titleY = PADDING + 60f

        // Draw title
        canvas.drawText(breakText(title.take(30), titlePaint, POSTER_WIDTH - infoX - PADDING), infoX, titleY, titlePaint)

        // Draw artist
        var artistY = titleY + 50f
        canvas.drawText(breakText(artist.take(40), artistPaint, POSTER_WIDTH - infoX - PADDING), infoX, artistY, artistPaint)

        // Draw album if available
        var albumY = artistY + 40f
        if (album.isNotBlank()) {
            canvas.drawText(breakText(album.take(50), albumPaint, POSTER_WIDTH - infoX - PADDING), infoX, albumY, albumPaint)
            albumY += 36f
        } else {
            albumY = artistY
        }

        // Draw divider
        val dividerPaint = Paint().apply {
            color = secondaryTextColor
            strokeWidth = 2f
        }
        val dividerY = albumY + 20f
        canvas.drawLine(infoX, dividerY, POSTER_WIDTH - PADDING, dividerY, dividerPaint)

        // Parse lyrics (handle LRC format)
        val allLyrics = parseToLines(lyricsText)
        // Use selected lyrics text directly if provided, otherwise use indices or default to first 12 lines
        val lyricsLines = when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(12)
            selectedLyricIndices.isNotEmpty() -> selectedLyricIndices.mapNotNull { allLyrics.getOrNull(it) }.take(12)
            else -> allLyrics.take(12)
        }

        // Draw lyrics with font size scaling
        val baseFontSize = 42f * fontSizeScale
        val highlightFontSize = 48f * fontSizeScale
        val lineHeight = (64f * fontSizeScale)

        val lyricsPaint = Paint().apply {
            color = effectiveTextColor
            textSize = baseFontSize
            isAntiAlias = true
            letterSpacing = 0.05f
        }

        val lyricsStartY = dividerY + 60f
        val maxWidth = POSTER_WIDTH - (PADDING * 2)

        lyricsLines.forEachIndexed { index, line ->
            // Split long lines into multiple lines
            val wrappedLines = breakTextToLines(line, lyricsPaint, maxWidth)
            wrappedLines.forEachIndexed { wrapIndex, wrappedLine ->
                val lineY = lyricsStartY + ((index + wrapIndex) * lineHeight)
                if (lineY < POSTER_HEIGHT - MARGIN_BOTTOM - 100f) {
                    // Highlight first line of first lyric
                    if (index == 0 && wrapIndex == 0) {
                        lyricsPaint.textSize = highlightFontSize
                        lyricsPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else {
                        lyricsPaint.textSize = baseFontSize
                        lyricsPaint.typeface = Typeface.DEFAULT
                    }
                    canvas.drawText(wrappedLine, PADDING, lineY, lyricsPaint)
                }
            }
        }

        // Draw bottom divider
        val bottomDividerY = POSTER_HEIGHT - MARGIN_BOTTOM - 40f
        canvas.drawLine(PADDING, bottomDividerY, POSTER_WIDTH - PADDING, bottomDividerY, dividerPaint)

        // Draw bottom info
        val bottomPaint = Paint().apply {
            color = secondaryTextColor
            textSize = 28f
            isAntiAlias = true
        }
        val bottomText = "Voxly"
        val bottomTextWidth = bottomPaint.measureText(bottomText)
        canvas.drawText(
            bottomText,
            (POSTER_WIDTH - bottomTextWidth) / 2,
            POSTER_HEIGHT - 60f,
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
