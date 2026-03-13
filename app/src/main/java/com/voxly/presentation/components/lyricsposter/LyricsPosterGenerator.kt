package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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

    // ===== Spotify 风格布局配置 =====
    // 封面尺寸 (像素值，基于 1080x1920 画布)
    private object SpotifyLayout {
        const val PORTRAIT_COVER_SIZE = 480f   // 160dp * 3
        const val LANDSCAPE_COVER_SIZE = 540f  // 180dp * 3
        const val COVER_CORNER_RADIUS = 48f    // 16dp * 3

        // 位置参数 (基于 1080x1920 画布)
        const val PORTRAIT_TITLE_TOP = 120f    // 40dp * 3
        const val PORTRAIT_TITLE_ARTIST_GAP = 12f // 4dp * 3
        const val PORTRAIT_COVER_BOTTOM = 180f  // 60dp * 3
        const val PORTRAIT_LYRICS_TOP_RATIO = 0.20f // 歌词起始位置 (画布高度百分比)

        const val LANDSCAPE_COVER_LEFT = 72f   // 24dp * 3
        const val LANDSCAPE_LYRICS_LEFT = 720f // 240dp * 3
        const val LANDSCAPE_TITLE_TOP = 120f
        const val LANDSCAPE_TITLE_ARTIST_GAP = 12f

        const val WATERMARK_BOTTOM = 180f     // 60dp * 3
    }

    // Typography scale constants (Spotify 风格 - 更大的歌词字体)
    private object Typography {
        // 竖屏
        const val PORTRAIT_TITLE_SIZE = 48f    // 16sp * 3
        const val PORTRAIT_ARTIST_SIZE = 36f   // 12sp * 3
        const val PORTRAIT_BASE_FONT_SIZE = 96f // 32sp * 3
        const val PORTRAIT_LINE_HEIGHT = 140f  // 行高

        // 横屏
        const val LANDSCAPE_TITLE_SIZE = 48f
        const val LANDSCAPE_ARTIST_SIZE = 36f
        const val LANDSCAPE_BASE_FONT_SIZE = 66f // 22sp * 3
        const val LANDSCAPE_LINE_HEIGHT = 96f

        const val WATERMARK_SIZE = 36f
        const val HIGHLIGHT_SCALE = 1.0f
    }

    // Layout spacing constants (简化版，用于兼容性)
    private object Spacing {
        const val PADDING = 80f
        const val COVER_SIZE = 440
        const val MARGIN_BOTTOM = 100f
        const val INFO_SECTION_WIDTH = 900f

        const val COVER_INFO_GAP = 64f
        const val TITLE_TOP_OFFSET = 100f
        const val TITLE_ARTIST_GAP = 112f
        const val ARTIST_ALBUM_GAP = 84f
        const val ALBUM_LYRICS_GAP = 40f

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
        highlightedLineIndex: Int = 0,
        lyricsAlignment: TextAlignment = TextAlignment.LEFT
    ): Bitmap {
        // 1. 解析歌词
        val allLyrics = parseToLines(lyricsText)
        val lyricsLines = when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(12)
            selectedLyricIndices.isNotEmpty() -> selectedLyricIndices.mapNotNull { allLyrics.getOrNull(it) }.take(12)
            else -> allLyrics.take(12)
        }

        // 2. 计算画布尺寸
        val (posterWidth, posterHeight) = when (orientation) {
            PosterOrientation.PORTRAIT -> OutputDimensions.PORTRAIT_WIDTH to OutputDimensions.PORTRAIT_HEIGHT
            PosterOrientation.LANDSCAPE -> OutputDimensions.LANDSCAPE_WIDTH to OutputDimensions.LANDSCAPE_HEIGHT
        }

        // 3. 创建画布
        val bitmap = Bitmap.createBitmap(posterWidth, posterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 4. 绘制背景 (有封面时使用氛围背景，无封面时使用纯色)
        if (albumArtBitmap != null) {
            drawSpotifyBackground(canvas, albumArtBitmap, posterWidth, posterHeight, orientation)
        } else {
            // 无封面时使用纯色背景
            val bgPaint = Paint().apply {
                color = backgroundColor.toArgb()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, posterWidth.toFloat(), posterHeight.toFloat(), bgPaint)
        }

        // 5. 文字颜色 (Spotify 风格：白色为主)
        val defaultTextColor = Color.WHITE
        val secondaryTextColor = Color.argb(153, 255, 255, 255) // 60% 白色

        // 6. 绘制标题和艺术家 (竖屏顶部居中，横屏左侧)
        when (orientation) {
            PosterOrientation.PORTRAIT -> drawPortraitTitleArtist(canvas, title, artist, posterWidth)
            PosterOrientation.LANDSCAPE -> drawLandscapeTitleArtist(canvas, title, artist)
        }

        // 7. 绘制歌词
        val coverSize = when (orientation) {
            PosterOrientation.PORTRAIT -> SpotifyLayout.PORTRAIT_COVER_SIZE
            PosterOrientation.LANDSCAPE -> SpotifyLayout.LANDSCAPE_COVER_SIZE
        }
        when (orientation) {
            PosterOrientation.PORTRAIT -> drawPortraitLyrics(canvas, lyricsLines, posterWidth, posterHeight, coverSize, fontSizeScale)
            PosterOrientation.LANDSCAPE -> drawLandscapeLyrics(canvas, lyricsLines, posterWidth, posterHeight, fontSizeScale)
        }

        // 8. 绘制封面
        if (albumArtBitmap != null) {
            drawSpotifyCover(canvas, albumArtBitmap, posterWidth, posterHeight, orientation, coverSize)
        }

        // 9. 绘制水印
        drawWatermark(canvas, posterWidth, posterHeight, secondaryTextColor)

        return bitmap
    }

    // ===== Spotify 风格绘制函数 =====

    /**
     * 绘制 Spotify 风格氛围背景
     * - 使用封面图片模糊作为背景
     * - 添加渐变遮罩
     */
    private fun drawSpotifyBackground(
        canvas: Canvas,
        bitmap: Bitmap,
        posterWidth: Int,
        posterHeight: Int,
        orientation: PosterOrientation
    ) {
        // 1. 绘制模糊背景 (使用原始封面缩小后放大)
        val scaledBg = createBlurredBackground(bitmap, posterWidth, posterHeight)
        canvas.drawBitmap(scaledBg, 0f, 0f, null)

        // 2. 绘制渐变遮罩
        drawGradientOverlay(canvas, posterWidth, posterHeight, orientation)
    }

    /**
     * 创建模糊背景图片 - 使用 Stack Blur 算法实现真正的模糊效果
     */
    private fun createBlurredBackground(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        // 先缩小到 1/8 以提高模糊性能
        val smallWidth = maxOf(targetWidth / 8, 1)
        val smallHeight = maxOf(targetHeight / 8, 1)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)

        // 应用 Stack Blur 模糊算法 (半径 25)
        val blurredSmall = stackBlur(small, 25)

        // 放大回目标尺寸
        return Bitmap.createScaledBitmap(blurredSmall, targetWidth, targetHeight, true)
    }

    /**
     * Stack Blur 算法 - 快速高效的模糊实现
     * @param radius 模糊半径 (建议 10-50)
     */
    private fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = (i / divsum)
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + minOf(maxOf(i, 0), wm)]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[(stackpointer) % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = maxOf(0, yp) + x

                sir = stack[i + radius]

                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - kotlin.math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 绘制渐变遮罩
     */
    private fun drawGradientOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        orientation: PosterOrientation
    ) {
        val paint = Paint()
        val shader = when (orientation) {
            PosterOrientation.PORTRAIT -> LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.argb(102, 0, 0, 0),    // 40% 黑色
                    Color.argb(25, 0, 0, 0),     // 10% 黑色
                    Color.argb(25, 0, 0, 0),     // 10% 黑色
                    Color.argb(128, 0, 0, 0),    // 50% 黑色
                    Color.argb(204, 0, 0, 0)     // 80% 黑色
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
            PosterOrientation.LANDSCAPE -> LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.argb(153, 0, 0, 0),    // 60% 黑色
                    Color.argb(51, 0, 0, 0),     // 20% 黑色
                    Color.argb(25, 0, 0, 0)      // 10% 黑色
                ),
                floatArrayOf(0f, 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * 绘制 Spotify 风格封面
     */
    private fun drawSpotifyCover(
        canvas: Canvas,
        bitmap: Bitmap,
        posterWidth: Int,
        posterHeight: Int,
        orientation: PosterOrientation,
        coverSize: Float
    ) {
        // 计算封面位置
        val (x, y) = when (orientation) {
            PosterOrientation.PORTRAIT -> {
                val left = (posterWidth - coverSize) / 2
                val top = posterHeight - coverSize - SpotifyLayout.PORTRAIT_COVER_BOTTOM
                left to top
            }
            PosterOrientation.LANDSCAPE -> {
                val left = SpotifyLayout.LANDSCAPE_COVER_LEFT
                val top = (posterHeight - coverSize) / 2
                left to top
            }
        }

        // 绘制封面阴影
        val shadowPaint = Paint().apply {
            color = Color.TRANSPARENT
            setShadowLayer(60f, 0f, 20f, Color.argb(204, 0, 0, 0))
        }
        val destRect = RectF(x, y, x + coverSize, y + coverSize)
        canvas.drawRoundRect(destRect, SpotifyLayout.COVER_CORNER_RADIUS, SpotifyLayout.COVER_CORNER_RADIUS, shadowPaint)

        // 绘制封面
        val scaledCover = Bitmap.createScaledBitmap(bitmap, coverSize.toInt(), coverSize.toInt(), true)
        canvas.drawBitmap(scaledCover, x, y, Paint().apply { isAntiAlias = true })
    }

    /**
     * 绘制竖屏标题和艺术家
     */
    private fun drawPortraitTitleArtist(canvas: Canvas, title: String, artist: String, posterWidth: Int) {
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.PORTRAIT_TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val artistPaint = Paint().apply {
            color = Color.argb(153, 255, 255, 255)
            textSize = Typography.PORTRAIT_ARTIST_SIZE
            isAntiAlias = true
        }

        val centerX = posterWidth / 2f
        val titleY = SpotifyLayout.PORTRAIT_TITLE_TOP

        // 标题居中
        val titleWidth = titlePaint.measureText(title)
        canvas.drawText(title, centerX - titleWidth / 2, titleY, titlePaint)

        // 艺术家居中
        val artistY = titleY + SpotifyLayout.PORTRAIT_TITLE_ARTIST_GAP
        val artistWidth = artistPaint.measureText(artist)
        canvas.drawText(artist, centerX - artistWidth / 2, artistY, artistPaint)
    }

    /**
     * 绘制横屏标题和艺术家
     */
    private fun drawLandscapeTitleArtist(canvas: Canvas, title: String, artist: String) {
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.LANDSCAPE_TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val artistPaint = Paint().apply {
            color = Color.argb(153, 255, 255, 255)
            textSize = Typography.LANDSCAPE_ARTIST_SIZE
            isAntiAlias = true
        }

        val titleY = SpotifyLayout.LANDSCAPE_TITLE_TOP
        canvas.drawText(title, SpotifyLayout.LANDSCAPE_COVER_LEFT + SpotifyLayout.LANDSCAPE_COVER_SIZE + 192, titleY, titlePaint)

        val artistY = titleY + Typography.LANDSCAPE_TITLE_SIZE + SpotifyLayout.LANDSCAPE_TITLE_ARTIST_GAP
        canvas.drawText(artist, SpotifyLayout.LANDSCAPE_COVER_LEFT + SpotifyLayout.LANDSCAPE_COVER_SIZE + 192, artistY, artistPaint)
    }

    /**
     * 绘制竖屏歌词
     */
    private fun drawPortraitLyrics(
        canvas: Canvas,
        lyricsLines: List<String>,
        posterWidth: Int,
        posterHeight: Int,
        coverSize: Float,
        fontSizeScale: Float
    ) {
        val lyricsPaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.PORTRAIT_BASE_FONT_SIZE * fontSizeScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
        }

        // 歌词区域起始 Y (画布高度的 20% 处开始)
        val lyricsStartY = posterHeight * SpotifyLayout.PORTRAIT_LYRICS_TOP_RATIO
        val lineHeight = Typography.PORTRAIT_LINE_HEIGHT * fontSizeScale
        val centerX = posterWidth / 2f

        lyricsLines.forEachIndexed { index, line ->
            val lineY = lyricsStartY + (index * lineHeight)
            // 避免与封面重叠
            if (lineY < posterHeight - coverSize - 200) {
                val textWidth = lyricsPaint.measureText(line)
                canvas.drawText(line, centerX - textWidth / 2, lineY, lyricsPaint)
            }
        }
    }

    /**
     * 绘制横屏歌词
     */
    private fun drawLandscapeLyrics(
        canvas: Canvas,
        lyricsLines: List<String>,
        posterWidth: Int,
        posterHeight: Int,
        fontSizeScale: Float
    ) {
        val lyricsPaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.LANDSCAPE_BASE_FONT_SIZE * fontSizeScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
        }

        val lyricsStartX = SpotifyLayout.LANDSCAPE_LYRICS_LEFT
        val centerY = posterHeight / 2f
        val lineHeight = Typography.LANDSCAPE_LINE_HEIGHT * fontSizeScale

        lyricsLines.forEachIndexed { index, line ->
            val lineY = centerY + (index * lineHeight) - ((lyricsLines.size - 1) * lineHeight) / 2
            if (lineY > 0 && lineY < posterHeight - 100) {
                canvas.drawText(line, lyricsStartX, lineY, lyricsPaint)
            }
        }
    }

    /**
     * 绘制水印
     */
    private fun drawWatermark(canvas: Canvas, posterWidth: Int, posterHeight: Int, color: Int) {
        val paint = Paint().apply {
            this.color = color
            textSize = Typography.WATERMARK_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val text = "Voxly"
        val textWidth = paint.measureText(text)
        canvas.drawText(
            text,
            (posterWidth - textWidth) / 2,
            posterHeight - SpotifyLayout.WATERMARK_BOTTOM,
            paint
        )
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
