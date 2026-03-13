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

    // ===== Layout Configuration Constants =====
    // Fixed output dimensions
    private object OutputDimensions {
        const val PORTRAIT_WIDTH = 1080
        const val PORTRAIT_HEIGHT = 1920
        const val LANDSCAPE_WIDTH = 1920
        const val LANDSCAPE_HEIGHT = 1080
    }

    // Reference dimensions (based on original 720x1280 design)
    private const val REFERENCE_PORTRAIT_WIDTH = 720f
    private const val REFERENCE_PORTRAIT_HEIGHT = 1280f

    // Size constraints
    private const val PADDING = 80f
    private const val COVER_SIZE = 440
    private const val MARGIN_BOTTOM = 100f
    private const val INFO_SECTION_WIDTH = 900f

    // Typography scale constants
    private object Typography {
        const val PORTRAIT_TITLE_SIZE = 104f
        const val PORTRAIT_ARTIST_SIZE = 68f
        const val PORTRAIT_ALBUM_SIZE = 52f
        const val PORTRAIT_BASE_FONT_SIZE = 84f
        const val PORTRAIT_LINE_HEIGHT = 128f

        const val LANDSCAPE_TITLE_SIZE = 72f
        const val LANDSCAPE_ARTIST_SIZE = 48f
        const val LANDSCAPE_ALBUM_SIZE = 36f
        const val LANDSCAPE_BASE_FONT_SIZE = 64f
        const val LANDSCAPE_LINE_HEIGHT = 96f

        const val WATERMARK_SIZE = 56f
        const val HIGHLIGHT_SCALE = 1.0f
    }

    // Layout spacing constants
    private object Spacing {
        const val COVER_INFO_GAP = 64f
        const val TITLE_TOP_OFFSET = 100f
        const val TITLE_ARTIST_GAP = 112f
        const val ARTIST_ALBUM_GAP = 84f
        const val ALBUM_LYRICS_GAP = 40f
        const val COVER_AREA_EXTRA = 80f
        const val DIVIDER_AREA = 80f
        const val BOTTOM_EXTRA = 60f

        const val LANDSCAPE_COVER_SIZE = 280
        const val LANDSCAPE_CONTENT_GAP = 64f
        const val LANDSCAPE_TITLE_TOP = 60f
        const val LANDSCAPE_TITLE_ARTIST_GAP = 20f
        const val LANDSCAPE_ARTIST_ALBUM_GAP = 10f
        const val LANDSCAPE_ALBUM_LYRICS_GAP = 60f

        const val WATERMARK_BOTTOM_OFFSET = 50f
    }

    // P5: Text alignment options
    enum class TextAlignment {
        LEFT, CENTER, RIGHT
    }

    /**
     * Poster orientation
     */
    enum class PosterOrientation {
        PORTRAIT,   // 竖向 9:16
        LANDSCAPE   // 横向 16:9
    }

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
     * @param orientation Poster orientation (PORTRAIT or LANDSCAPE)
     * @param highlightedLineIndex Index of the line to highlight (-1 for no highlight, 0 for first line)
     * @param lyricsAlignment Text alignment for lyrics (LEFT, CENTER, RIGHT)
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
        fontSizeScale: Float = 1.0f,
        orientation: PosterOrientation = PosterOrientation.PORTRAIT,
        highlightedLineIndex: Int = 0,  // P4: Default to highlight first line
        lyricsAlignment: TextAlignment = TextAlignment.LEFT  // P5: Default to left alignment
    ): Bitmap {
        // Parse lyrics first to calculate dynamic height
        val allLyrics = parseToLines(lyricsText)
        val lyricsLines = when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(12)
            selectedLyricIndices.isNotEmpty() -> selectedLyricIndices.mapNotNull { allLyrics.getOrNull(it) }.take(12)
            else -> allLyrics.take(12)
        }

        // Calculate dimensions based on orientation
        val baseFontSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_BASE_FONT_SIZE * fontSizeScale
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_BASE_FONT_SIZE * fontSizeScale
        }

        val lineHeight = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_LINE_HEIGHT * fontSizeScale
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_LINE_HEIGHT * fontSizeScale
        }

        // P0: Create paint objects early for dynamic height calculation
        val titleSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_TITLE_SIZE
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_TITLE_SIZE
        }
        val artistSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_ARTIST_SIZE
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_ARTIST_SIZE
        }
        val albumSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_ALBUM_SIZE
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_ALBUM_SIZE
        }

        // Use fixed output size based on orientation
        val (posterWidth, posterHeight) = when (orientation) {
            PosterOrientation.PORTRAIT -> OutputDimensions.PORTRAIT_WIDTH to OutputDimensions.PORTRAIT_HEIGHT
            PosterOrientation.LANDSCAPE -> OutputDimensions.LANDSCAPE_WIDTH to OutputDimensions.LANDSCAPE_HEIGHT
        }

        // Calculate content scale factor based on fixed output size vs reference design
        val contentScaleFactor = when (orientation) {
            PosterOrientation.PORTRAIT -> posterWidth / REFERENCE_PORTRAIT_WIDTH
            PosterOrientation.LANDSCAPE -> {
                // For landscape, use the smaller scale to fit content
                minOf(
                    posterWidth / REFERENCE_PORTRAIT_WIDTH,
                    posterHeight / REFERENCE_PORTRAIT_HEIGHT
                )
            }
        }

        // Apply content scale factor to all size parameters
        val scaledBaseFontSize = baseFontSize * contentScaleFactor
        val scaledLineHeight = lineHeight * contentScaleFactor

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

        // P6: Add shadow color for text visibility
        val shadowColor = Color.argb(30, 0, 0, 0)

        // Define paint objects based on orientation
        val scaledTitleSize = titleSize * contentScaleFactor
        val scaledArtistSize = artistSize * contentScaleFactor
        val scaledAlbumSize = albumSize * contentScaleFactor

        val highlightSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Typography.PORTRAIT_TITLE_SIZE * fontSizeScale * contentScaleFactor
            PosterOrientation.LANDSCAPE -> Typography.LANDSCAPE_TITLE_SIZE * fontSizeScale * contentScaleFactor
        }

        // P1: Create paint objects with subpixel anti-aliasing
        val titlePaintRender = Paint().apply {
            color = defaultTextColor
            textSize = scaledTitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            isSubpixelText = true  // P1: Subpixel anti-aliasing for clearer text
            setShadowLayer(2f, 0f, 1f, shadowColor)  // P6: Subtle shadow for depth
        }

        val artistPaintRender = Paint().apply {
            color = secondaryTextColor
            textSize = scaledArtistSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            isSubpixelText = true  // P1: Subpixel anti-aliasing
            setShadowLayer(2f, 0f, 1f, shadowColor)  // P6: Subtle shadow
        }

        val albumPaintRender = Paint().apply {
            color = secondaryTextColor
            textSize = scaledAlbumSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            isSubpixelText = true  // P1: Subpixel anti-aliasing
            setShadowLayer(2f, 0f, 1f, shadowColor)  // P6: Subtle shadow
        }

        val lyricsPaintRender = Paint().apply {
            color = defaultTextColor
            textSize = scaledBaseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            isSubpixelText = true  // P1: Subpixel anti-aliasing
            letterSpacing = 0.05f
            setShadowLayer(2f, 0f, 1f, shadowColor)  // P6: Subtle shadow for lyrics
        }

        if (orientation == PosterOrientation.PORTRAIT) {
            // ===== PORTRAIT LAYOUT (竖向) =====

            // Draw album art at top
            if (albumArtBitmap != null) {
                // P2: FilterBitmap = true uses bilinear sampling for high-quality scaling
                val scaledCover = Bitmap.createScaledBitmap(albumArtBitmap, COVER_SIZE, COVER_SIZE, true)
                val coverPaint = Paint().apply {
                    isAntiAlias = true
                }
                canvas.drawBitmap(scaledCover, PADDING, PADDING, coverPaint)
            }

            // Draw song info to the right of cover
            val infoX = if (albumArtBitmap != null) PADDING + COVER_SIZE + Spacing.COVER_INFO_GAP else PADDING
            val titleY = PADDING + Spacing.TITLE_TOP_OFFSET

            canvas.drawText(breakText(title.take(40), titlePaintRender, INFO_SECTION_WIDTH), infoX, titleY, titlePaintRender)

            var artistY = titleY + Spacing.TITLE_ARTIST_GAP
            canvas.drawText(breakText(artist.take(50), artistPaintRender, INFO_SECTION_WIDTH), infoX, artistY, artistPaintRender)

            var albumY = artistY + Spacing.ARTIST_ALBUM_GAP
            if (album.isNotBlank()) {
                canvas.drawText(breakText(album.take(60), albumPaintRender, INFO_SECTION_WIDTH), infoX, albumY, albumPaintRender)
                albumY += Spacing.ARTIST_ALBUM_GAP - 16f
            } else {
                albumY = artistY
            }

            // Draw lyrics below
            val coverRowBottom = maxOf(if (albumArtBitmap != null) PADDING + COVER_SIZE else 0f, albumY + Spacing.ALBUM_LYRICS_GAP)
            val lyricsStartY = coverRowBottom + Spacing.ALBUM_LYRICS_GAP

            lyricsLines.forEachIndexed { index, line ->
                val wrappedLines = breakTextToLines(line, lyricsPaintRender, posterWidth.toFloat() - PADDING * 2)
                wrappedLines.forEachIndexed { wrapIndex, wrappedLine ->
                    val lineY = lyricsStartY + ((index + wrapIndex) * scaledLineHeight)
                    if (lineY < posterHeight - MARGIN_BOTTOM) {
                        // P4: Check if this line should be highlighted
                        val isHighlighted = index == highlightedLineIndex
                        lyricsPaintRender.textSize = if (isHighlighted) highlightSize else scaledBaseFontSize
                        // P5: Apply text alignment
                        val textX = when (lyricsAlignment) {
                            TextAlignment.LEFT -> PADDING
                            TextAlignment.CENTER -> (posterWidth - lyricsPaintRender.measureText(wrappedLine)) / 2
                            TextAlignment.RIGHT -> posterWidth - PADDING - lyricsPaintRender.measureText(wrappedLine)
                        }
                        canvas.drawText(wrappedLine, textX, lineY, lyricsPaintRender)
                    }
                }
            }
        } else {
            // ===== LANDSCAPE LAYOUT (横向) =====

            val coverSizeLandscape = Spacing.LANDSCAPE_COVER_SIZE
            val contentStartX = PADDING + coverSizeLandscape + Spacing.LANDSCAPE_CONTENT_GAP
            val contentWidth = posterWidth.toFloat() - contentStartX - PADDING

            // Draw album art on the left
            if (albumArtBitmap != null) {
                // P2: FilterBitmap = true uses bilinear sampling for high-quality scaling
                val scaledCover = Bitmap.createScaledBitmap(albumArtBitmap, coverSizeLandscape, coverSizeLandscape, true)
                val coverPaint = Paint().apply {
                    isAntiAlias = true
                }
                canvas.drawBitmap(scaledCover, PADDING, (posterHeight - coverSizeLandscape).toFloat() / 2, coverPaint)
            }

            // Draw title/artist at top right
            val titleY = PADDING + Spacing.LANDSCAPE_TITLE_TOP
            canvas.drawText(breakText(title.take(50), titlePaintRender, contentWidth), contentStartX, titleY, titlePaintRender)

            var artistY = titleY + titleSize + Spacing.LANDSCAPE_TITLE_ARTIST_GAP
            canvas.drawText(breakText(artist.take(60), artistPaintRender, contentWidth), contentStartX, artistY, artistPaintRender)

            val lyricsStartY: Float
            if (album.isNotBlank()) {
                artistY += artistSize + Spacing.LANDSCAPE_ARTIST_ALBUM_GAP
                canvas.drawText(breakText(album.take(70), albumPaintRender, contentWidth), contentStartX, artistY, albumPaintRender)
                lyricsStartY = artistY + Spacing.LANDSCAPE_ALBUM_LYRICS_GAP
            } else {
                lyricsStartY = artistY + Spacing.LANDSCAPE_ALBUM_LYRICS_GAP
            }

            // Draw lyrics below title/artist
            lyricsLines.forEachIndexed { index, line ->
                val wrappedLines = breakTextToLines(line, lyricsPaintRender, contentWidth)
                wrappedLines.forEachIndexed { wrapIndex, wrappedLine ->
                    val lineY = lyricsStartY + ((index + wrapIndex) * scaledLineHeight)
                    if (lineY < posterHeight - MARGIN_BOTTOM) {
                        // P4: Check if this line should be highlighted
                        val isHighlighted = index == highlightedLineIndex
                        lyricsPaintRender.textSize = if (isHighlighted) highlightSize else scaledBaseFontSize
                        // P5: Apply text alignment
                        val textX = when (lyricsAlignment) {
                            TextAlignment.LEFT -> contentStartX
                            TextAlignment.CENTER -> contentStartX + (contentWidth - lyricsPaintRender.measureText(wrappedLine)) / 2
                            TextAlignment.RIGHT -> contentStartX + contentWidth - lyricsPaintRender.measureText(wrappedLine)
                        }
                        canvas.drawText(wrappedLine, textX, lineY, lyricsPaintRender)
                    }
                }
            }
        }

        // Draw bottom info (Voxly watermark)
        val bottomPaint = Paint().apply {
            color = secondaryTextColor
            textSize = Typography.WATERMARK_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            isSubpixelText = true  // P1: Subpixel anti-aliasing for watermark
        }
        val bottomText = "Voxly"
        val bottomTextWidth = bottomPaint.measureText(bottomText)
        canvas.drawText(
            bottomText,
            (posterWidth - bottomTextWidth) / 2,
            posterHeight - Spacing.WATERMARK_BOTTOM_OFFSET,
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
