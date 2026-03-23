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
 * Generates lyrics poster images with Spotify style only.
 */
object LyricsPosterGenerator {

    // ===== 布局配置常量 =====
    // 参考尺寸 (基于 1080 宽度的设计)
    private const val REFERENCE_WIDTH = 1080f
    private const val REFERENCE_HEIGHT = 1920f
    private const val MIN_POSTER_HEIGHT = 1440f  // 最小高度 3:4

    // ===== Spotify 风格布局配置 (重构版) =====
    private object Layout {
        // 边距
        const val MARGIN_HORIZONTAL = 72f        // 左右边距 24dp * 3
        const val MARGIN_VERTICAL = 96f          // 上下边距 32dp * 3
        
        // 竖屏封面
        const val PORTRAIT_COVER_SIZE = 520f     // 封面尺寸 (增大约8%)
        const val COVER_CORNER_RADIUS = 48f      // 圆角
        const val COVER_BOTTOM_MARGIN = 120f     // 封面距底部边距
        
        // 横屏封面
        const val LANDSCAPE_COVER_SIZE = 480f    // 横屏封面尺寸
        const val COVER_LEFT_MARGIN = 72f        // 封面距左边距
        
        // 标题区域
        const val TITLE_TOP_MARGIN = 120f        // 标题距顶部
        const val TITLE_ARTIST_GAP = 36f         // 标题与艺术家间距 (增大防止重叠)
        
        // 歌词区域
        const val LYRICS_TITLE_GAP = 80f         // 标题区与歌词区间距
        const val LYRICS_LINE_GAP_RATIO = 1.4f   // 行间距系数
        const val LYRICS_MAX_WIDTH_RATIO = 0.85f // 歌词最大宽度占比
        
        // 横屏布局
        const val LANDSCAPE_CONTENT_GAP = 72f    // 封面与内容间距
        const val LANDSCAPE_LYRICS_LEFT_RATIO = 0.45f // 歌词起始位置占比
        
        // 水印
        const val WATERMARK_SIZE = 36f
        const val WATERMARK_BOTTOM_MARGIN = 80f
    }

    // ===== 字体配置 (增大) =====
    private object Typography {
        // 竖屏
        const val PORTRAIT_TITLE_SIZE = 64f      // 标题 (原48f)
        const val PORTRAIT_ARTIST_SIZE = 48f     // 艺术家 (原36f)
        const val PORTRAIT_LYRICS_SIZE = 72f     // 歌词基础大小
        const val PORTRAIT_LYRICS_LINE_HEIGHT = 100f // 歌词行高
        
        // 横屏
        const val LANDSCAPE_TITLE_SIZE = 56f     // 标题 (原48f)
        const val LANDSCAPE_ARTIST_SIZE = 42f    // 艺术家 (原36f)
        const val LANDSCAPE_LYRICS_SIZE = 54f    // 歌词基础大小
        const val LANDSCAPE_LYRICS_LINE_HEIGHT = 78f // 歌词行高
        
        const val WATERMARK_SIZE = Layout.WATERMARK_SIZE
        const val TITLE_TYPEFACE = Typeface.BOLD
        const val ARTIST_TYPEFACE = Typeface.NORMAL
        const val LYRICS_TYPEFACE = Typeface.BOLD
    }

    /**
     * 海报方向
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
     * @param album Album name (optional)
     * @param lyricsText Raw lyrics text
     * @param albumArtBitmap Optional album art bitmap
     * @param backgroundColor Background color (fallback when no album art)
     * @param contentColor Optional content color for contrast
     * @param selectedLyrics List of selected lyric text for poster
     * @param selectedLyricIndices List of selected lyric indices (fallback)
     * @param fontSizeScale Font size scale factor
     * @param orientation Poster orientation
     * @param highlightedLineIndex Index of line to highlight
     * @param style Poster style (always SPOTIFY)
     * @param config Poster configuration
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
        style: PosterStyle = PosterStyle.SPOTIFY,
        config: PosterConfig = PosterConfig()
    ): Bitmap {
        // 1. 解析歌词
        val allLyrics = parseToLines(lyricsText)
        val lyricsLines = when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(10)
            selectedLyricIndices.isNotEmpty() -> selectedLyricIndices.mapNotNull { allLyrics.getOrNull(it) }.take(10)
            else -> allLyrics.take(10)
        }

        // 2. 计算画布尺寸
        val (posterWidth, posterHeight) = calculateCanvasSize(
            orientation, lyricsLines, fontSizeScale
        )

        // 3. 创建画布
        val bitmap = Bitmap.createBitmap(posterWidth, posterHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 4. 绘制Spotify风格海报
        drawSpotifyPoster(
            canvas, title, artist, albumArtBitmap, lyricsLines,
            posterWidth, posterHeight, orientation, fontSizeScale,
            backgroundColor, config
        )

        return bitmap
    }

    /**
     * 计算画布尺寸 - 确保完全填充
     */
    private fun calculateCanvasSize(
        orientation: PosterOrientation,
        lyricsLines: List<String>,
        fontSizeScale: Float
    ): Pair<Int, Int> {
        return when (orientation) {
            PosterOrientation.PORTRAIT -> {
                // 竖屏: 固定宽度 1080，高度自适应
                val width = REFERENCE_WIDTH.toInt()
                
                // 计算所需高度
                val titleHeight = Layout.TITLE_TOP_MARGIN + 
                    Typography.PORTRAIT_TITLE_SIZE + 
                    Layout.TITLE_ARTIST_GAP + 
                    Typography.PORTRAIT_ARTIST_SIZE +
                    Layout.LYRICS_TITLE_GAP
                
                val lyricsHeight = lyricsLines.size * 
                    Typography.PORTRAIT_LYRICS_LINE_HEIGHT * fontSizeScale
                
                val coverHeight = Layout.PORTRAIT_COVER_SIZE + Layout.COVER_BOTTOM_MARGIN
                
                val watermarkHeight = Layout.WATERMARK_BOTTOM_MARGIN + Typography.WATERMARK_SIZE
                
                // 总高度 = 标题区 + 歌词区 + 封面区 + 水印区 + 上下边距
                val contentHeight = titleHeight + lyricsHeight + coverHeight + watermarkHeight
                val minHeight = Layout.MARGIN_VERTICAL * 2 + contentHeight
                
                // 最小高度 3:4 比例
                val ratioHeight = (width * 4 / 3).toFloat()
                val height = maxOf(minHeight, ratioHeight).toInt().coerceAtMost(1920)
                
                width to height
            }
            
            PosterOrientation.LANDSCAPE -> {
                // 横屏: 固定高度 1080，宽度自适应
                val height = REFERENCE_WIDTH.toInt()
                
                // 计算所需宽度
                val coverWidth = Layout.COVER_LEFT_MARGIN + 
                    Layout.LANDSCAPE_COVER_SIZE + 
                    Layout.LANDSCAPE_CONTENT_GAP
                
                val lyricsWidth = height * Layout.LYRICS_MAX_WIDTH_RATIO * 0.6f // 歌词区域约占60%高度对应的宽度
                
                val marginRight = Layout.MARGIN_HORIZONTAL
                
                val contentWidth = coverWidth + lyricsWidth + marginRight
                
                // 最小宽度 16:9 比例
                val ratioWidth = (height * 16 / 9).toFloat()
                val width = maxOf(contentWidth, ratioWidth).toInt().coerceAtMost(1920)
                
                width to height
            }
        }
    }

    /**
     * 绘制Spotify风格海报
     */
    private fun drawSpotifyPoster(
        canvas: Canvas,
        title: String,
        artist: String,
        albumArtBitmap: Bitmap?,
        lyricsLines: List<String>,
        posterWidth: Int,
        posterHeight: Int,
        orientation: PosterOrientation,
        fontSizeScale: Float,
        backgroundColor: androidx.compose.ui.graphics.Color,
        config: PosterConfig
    ) {
        // 1. 绘制背景
        if (albumArtBitmap != null) {
            drawBlurredBackground(canvas, albumArtBitmap, posterWidth, posterHeight)
            drawGradientOverlay(canvas, posterWidth, posterHeight, orientation)
        } else {
            canvas.drawColor(backgroundColor.toArgb())
        }

        // 2. 绘制标题和艺术家
        when (orientation) {
            PosterOrientation.PORTRAIT -> drawPortraitTitleArtist(
                canvas, title, artist, posterWidth
            )
            PosterOrientation.LANDSCAPE -> drawLandscapeTitleArtist(
                canvas, title, artist, posterHeight
            )
        }

        // 3. 绘制歌词（带自动换行）
        when (orientation) {
            PosterOrientation.PORTRAIT -> drawPortraitLyrics(
                canvas, lyricsLines, posterWidth, posterHeight, fontSizeScale
            )
            PosterOrientation.LANDSCAPE -> drawLandscapeLyrics(
                canvas, lyricsLines, posterWidth, posterHeight, fontSizeScale
            )
        }

        // 4. 绘制封面
        if (albumArtBitmap != null) {
            drawCover(canvas, albumArtBitmap, posterWidth, posterHeight, orientation)
        }

        // 5. 绘制水印
        if (config.showWatermark) {
            drawWatermark(canvas, posterWidth, posterHeight)
        }
    }

    /**
     * 绘制模糊背景
     */
    private fun drawBlurredBackground(
        canvas: Canvas,
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ) {
        // 缩小以提高性能
        val smallWidth = maxOf(targetWidth / 8, 1)
        val smallHeight = maxOf(targetHeight / 8, 1)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        
        // 应用模糊
        val blurred = stackBlur(small, 25)
        
        // 放大回目标尺寸
        val scaled = Bitmap.createScaledBitmap(blurred, targetWidth, targetHeight, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)
    }

    /**
     * Stack Blur 算法
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
                    Color.argb(120, 0, 0, 0),    // 顶部 47% 黑色
                    Color.argb(60, 0, 0, 0),     // 中部 24% 黑色
                    Color.argb(60, 0, 0, 0),     // 中部 24% 黑色
                    Color.argb(140, 0, 0, 0),    // 底部 55% 黑色
                    Color.argb(200, 0, 0, 0)     // 底部 78% 黑色
                ),
                floatArrayOf(0f, 0.25f, 0.5f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
            PosterOrientation.LANDSCAPE -> LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.argb(160, 0, 0, 0),    // 左侧 63% 黑色
                    Color.argb(80, 0, 0, 0),     // 中部 31% 黑色
                    Color.argb(40, 0, 0, 0)      // 右侧 16% 黑色
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * 绘制竖屏标题和艺术家
     */
    private fun drawPortraitTitleArtist(
        canvas: Canvas,
        title: String,
        artist: String,
        posterWidth: Int
    ) {
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.PORTRAIT_TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typography.TITLE_TYPEFACE)
            isAntiAlias = true
        }
        
        val artistPaint = Paint().apply {
            color = Color.argb(180, 255, 255, 255)  // 70% 透明度
            textSize = Typography.PORTRAIT_ARTIST_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typography.ARTIST_TYPEFACE)
            isAntiAlias = true
        }

        val centerX = posterWidth / 2f
        val titleY = Layout.TITLE_TOP_MARGIN + Typography.PORTRAIT_TITLE_SIZE
        
        // 标题居中
        val titleWidth = titlePaint.measureText(title)
        canvas.drawText(title, centerX - titleWidth / 2, titleY, titlePaint)

        // 艺术家在标题下方
        val artistY = titleY + Layout.TITLE_ARTIST_GAP + Typography.PORTRAIT_ARTIST_SIZE
        val artistWidth = artistPaint.measureText(artist)
        canvas.drawText(artist, centerX - artistWidth / 2, artistY, artistPaint)
    }

    /**
     * 绘制横屏标题和艺术家
     */
    private fun drawLandscapeTitleArtist(
        canvas: Canvas,
        title: String,
        artist: String,
        posterHeight: Int
    ) {
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.LANDSCAPE_TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typography.TITLE_TYPEFACE)
            isAntiAlias = true
        }
        
        val artistPaint = Paint().apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = Typography.LANDSCAPE_ARTIST_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typography.ARTIST_TYPEFACE)
            isAntiAlias = true
        }

        // 横屏标题在右侧歌词区域上方
        val lyricsStartX = posterHeight * Layout.LANDSCAPE_LYRICS_LEFT_RATIO
        val titleY = Layout.TITLE_TOP_MARGIN + Typography.LANDSCAPE_TITLE_SIZE
        
        canvas.drawText(title, lyricsStartX, titleY, titlePaint)

        // 艺术家在标题下方
        val artistY = titleY + Layout.TITLE_ARTIST_GAP + Typography.LANDSCAPE_ARTIST_SIZE
        canvas.drawText(artist, lyricsStartX, artistY, artistPaint)
    }

    /**
     * 绘制竖屏歌词（带自动换行）
     */
    private fun drawPortraitLyrics(
        canvas: Canvas,
        lyricsLines: List<String>,
        posterWidth: Int,
        posterHeight: Int,
        fontSizeScale: Float
    ) {
        val lyricsPaint = Paint().apply {
            color = Color.WHITE
            textSize = Typography.PORTRAIT_LYRICS_SIZE * fontSizeScale
            typeface = Typeface.create(Typeface.DEFAULT, Typography.LYRICS_TYPEFACE)
            isAntiAlias = true
            letterSpacing = 0.02f
        }

        val centerX = posterWidth / 2f
        val maxLyricsWidth = posterWidth * Layout.LYRICS_MAX_WIDTH_RATIO
        
        // 歌词起始Y位置（标题区下方）
        val titleAreaHeight = Layout.TITLE_TOP_MARGIN + 
            Typography.PORTRAIT_TITLE_SIZE + 
            Layout.TITLE_ARTIST_GAP + 
            Typography.PORTRAIT_ARTIST_SIZE +
            Layout.LYRICS_TITLE_GAP
        val lyricsStartY = titleAreaHeight + Typography.PORTRAIT_LYRICS_LINE_HEIGHT * fontSizeScale
        
        val lineHeight = Typography.PORTRAIT_LYRICS_LINE_HEIGHT * fontSizeScale * Layout.LYRICS_LINE_GAP_RATIO
        
        // 封面顶部位置
        val coverTopY = posterHeight - Layout.PORTRAIT_COVER_SIZE - Layout.COVER_BOTTOM_MARGIN
        
        var currentY = lyricsStartY
        
        lyricsLines.forEach { line ->
            // 自动换行处理
            val wrappedLines = wrapTextToLines(line, lyricsPaint, maxLyricsWidth)
            
            wrappedLines.forEach { wrappedLine ->
                // 检查是否超出封面区域
                if (currentY < coverTopY - lineHeight) {
                    val textWidth = lyricsPaint.measureText(wrappedLine)
                    canvas.drawText(wrappedLine, centerX - textWidth / 2, currentY, lyricsPaint)
                    currentY += lineHeight
                }
            }
        }
    }

    /**
     * 绘制横屏歌词（带自动换行）
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
            textSize = Typography.LANDSCAPE_LYRICS_SIZE * fontSizeScale
            typeface = Typeface.create(Typeface.DEFAULT, Typography.LYRICS_TYPEFACE)
            isAntiAlias = true
            letterSpacing = 0.02f
        }

        // 歌词区域起始X（封面右侧）
        val lyricsStartX = posterHeight * Layout.LANDSCAPE_LYRICS_LEFT_RATIO
        val maxLyricsWidth = posterWidth - lyricsStartX - Layout.MARGIN_HORIZONTAL
        
        // 歌词起始Y位置
        val titleAreaHeight = Layout.TITLE_TOP_MARGIN + 
            Typography.LANDSCAPE_TITLE_SIZE + 
            Layout.TITLE_ARTIST_GAP + 
            Typography.LANDSCAPE_ARTIST_SIZE +
            Layout.LYRICS_TITLE_GAP
        val lyricsStartY = titleAreaHeight + Typography.LANDSCAPE_LYRICS_LINE_HEIGHT * fontSizeScale
        
        val lineHeight = Typography.LANDSCAPE_LYRICS_LINE_HEIGHT * fontSizeScale * Layout.LYRICS_LINE_GAP_RATIO
        
        var currentY = lyricsStartY
        
        lyricsLines.forEach { line ->
            // 自动换行处理
            val wrappedLines = wrapTextToLines(line, lyricsPaint, maxLyricsWidth)
            
            wrappedLines.forEach { wrappedLine ->
                // 检查是否超出底部
                if (currentY < posterHeight - Layout.WATERMARK_BOTTOM_MARGIN - lineHeight * 2) {
                    canvas.drawText(wrappedLine, lyricsStartX, currentY, lyricsPaint)
                    currentY += lineHeight
                }
            }
        }
    }

    /**
     * 文本自动换行
     */
    private fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        
        val measuredWidth = paint.measureText(text)
        if (measuredWidth <= maxWidth) return listOf(text)
        
        val lines = mutableListOf<String>()
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            // 二分查找最大可显示字符数
            var low = 0
            var high = remaining.length
            var bestLength = 0
            
            while (low <= high) {
                val mid = (low + high) / 2
                val testText = remaining.substring(0, mid.coerceAtMost(remaining.length))
                val testWidth = paint.measureText(testText)
                
                if (testWidth <= maxWidth) {
                    bestLength = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            
            if (bestLength <= 0) {
                // 单字符都放不下，强制截断
                bestLength = 1
            }
            
            // 寻找词边界
            val cutText = remaining.substring(0, bestLength.coerceAtMost(remaining.length))
            val lastSpaceIndex = cutText.lastIndexOf(' ')
            
            val actualLength = if (lastSpaceIndex > 0 && lastSpaceIndex > bestLength / 3) {
                lastSpaceIndex
            } else {
                bestLength
            }
            
            lines.add(remaining.substring(0, actualLength.coerceAtMost(remaining.length)))
            remaining = remaining.substring(actualLength.coerceAtMost(remaining.length)).trimStart()
        }
        
        return lines.ifEmpty { listOf("") }
    }

    /**
     * 绘制封面
     */
    private fun drawCover(
        canvas: Canvas,
        bitmap: Bitmap,
        posterWidth: Int,
        posterHeight: Int,
        orientation: PosterOrientation
    ) {
        val coverSize = when (orientation) {
            PosterOrientation.PORTRAIT -> Layout.PORTRAIT_COVER_SIZE
            PosterOrientation.LANDSCAPE -> Layout.LANDSCAPE_COVER_SIZE
        }

        val (x, y) = when (orientation) {
            PosterOrientation.PORTRAIT -> {
                val left = (posterWidth - coverSize) / 2
                val top = posterHeight - coverSize - Layout.COVER_BOTTOM_MARGIN
                left to top
            }
            PosterOrientation.LANDSCAPE -> {
                val left = Layout.COVER_LEFT_MARGIN
                val top = (posterHeight - coverSize) / 2
                left to top
            }
        }

        // 绘制阴影
        val shadowPaint = Paint().apply {
            color = Color.TRANSPARENT
            setShadowLayer(48f, 0f, 16f, Color.argb(180, 0, 0, 0))
        }
        val shadowRect = RectF(x, y, x + coverSize, y + coverSize)
        canvas.drawRoundRect(shadowRect, Layout.COVER_CORNER_RADIUS, Layout.COVER_CORNER_RADIUS, shadowPaint)

        // 绘制封面
        val scaledCover = Bitmap.createScaledBitmap(bitmap, coverSize.toInt(), coverSize.toInt(), true)
        
        // 创建圆角遮罩
        val coverRect = RectF(x, y, x + coverSize, y + coverSize)
        val coverPath = android.graphics.Path().apply {
            addRoundRect(coverRect, Layout.COVER_CORNER_RADIUS, Layout.COVER_CORNER_RADIUS, android.graphics.Path.Direction.CW)
        }
        
        canvas.save()
        canvas.clipPath(coverPath)
        canvas.drawBitmap(scaledCover, x, y, Paint().apply { isAntiAlias = true })
        canvas.restore()
    }

    /**
     * 绘制水印
     */
    private fun drawWatermark(canvas: Canvas, posterWidth: Int, posterHeight: Int) {
        val paint = Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            textSize = Typography.WATERMARK_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val text = "Voxly"
        val textWidth = paint.measureText(text)
        
        canvas.drawText(
            text,
            (posterWidth - textWidth) / 2,
            posterHeight - Layout.WATERMARK_BOTTOM_MARGIN,
            paint
        )
    }
}
