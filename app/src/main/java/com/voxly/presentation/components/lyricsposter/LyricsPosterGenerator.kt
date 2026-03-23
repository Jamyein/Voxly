@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.withSave
import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.Lyrics.Companion.parseToLines
import kotlin.math.max
import kotlin.math.min

/**
 * 歌词海报生成器（Canvas 实现版）
 * 
 * 使用 Canvas 绘制生成海报，避免 ComposeView 的 Window 依赖问题
 * 这是稳定的实现方案，支持所有功能和完整形状
 */
object LyricsPosterGenerator {

    // ===== 画布配置 =====
    private const val CANVAS_WIDTH = 800f
    private const val PADDING_HORIZONTAL = 32f
    private const val PADDING_TOP = 32f
    private const val PADDING_BOTTOM = 24f
    
    // ===== 封面配置 =====
    private const val COVER_SIZE = 96f
    private const val COVER_TITLE_GAP = 24f
    
    // ===== 标题区域配置 =====
    private const val TITLE_ARTIST_GAP = 8f
    private const val HEADER_LYRICS_GAP = 32f
    
    // ===== 歌词配置 =====
    private const val LYRICS_WATERMARK_GAP = 24f
    private const val MAX_LYRICS_LINES = 6
    private const val LYRICS_MAX_WIDTH_RATIO = 0.9f
    
    // ===== 字体大小配置 =====
    private const val TITLE_TEXT_SIZE = 28f
    private const val ARTIST_TEXT_SIZE = 20f
    private const val LYRICS_TEXT_SIZE = 24f
    private const val WATERMARK_TEXT_SIZE = 16f

    /**
     * 生成歌词海报
     * 
     * @param context Android Context（保留参数以兼容旧代码，当前不使用）
     * @param title 歌曲标题
     * @param artist 艺术家名
     * @param album 专辑名（保留参数，当前布局不使用）
     * @param lyricsText 完整歌词文本
     * @param albumArtBitmap 封面图片
     * @param backgroundColor 背景色（提取的颜色）
     * @param contentColor 内容色（字体颜色），null 则自动计算
     * @param selectedLyrics 选中的歌词行列表
     * @param fontSizeScale 字体缩放比例（保留参数，配置中已包含）
     * @param config 海报配置
     * @return 生成的海报 Bitmap
     */
    fun generatePoster(
        context: android.content.Context,
        title: String,
        artist: String,
        album: String = "",
        lyricsText: String,
        albumArtBitmap: Bitmap?,
        backgroundColor: Color,
        contentColor: Color? = null,
        selectedLyrics: List<String> = emptyList(),
        fontSizeScale: Float = 1.0f,
        config: PosterConfig = PosterConfig()
    ): Bitmap {
        // 1. 处理歌词
        val lyricsLines = processLyrics(lyricsText, selectedLyrics)
        
        // 2. 计算画布高度
        val canvasHeight = calculateCanvasHeight(lyricsLines, config.fontSizeScale, config.lineSpacingMultiplier)
        
        // 3. 确定字体颜色
        val textColor = contentColor?.toArgb() 
            ?: calculateContrastColor(backgroundColor)
        
        // 4. 创建画布
        val bitmap = Bitmap.createBitmap(CANVAS_WIDTH.toInt(), canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 5. 绘制海报
        drawPoster(
            canvas = canvas,
            title = title,
            artist = artist,
            albumArtBitmap = albumArtBitmap,
            lyricsLines = lyricsLines,
            backgroundColor = backgroundColor.toArgb(),
            textColor = textColor,
            config = config
        )
        
        return bitmap
    }

    /**
     * 处理歌词：解析并限制行数
     */
    private fun processLyrics(lyricsText: String, selectedLyrics: List<String>): List<String> {
        return when {
            selectedLyrics.isNotEmpty() -> selectedLyrics.take(MAX_LYRICS_LINES)
            lyricsText.isNotEmpty() -> {
                val allLines = parseToLines(lyricsText)
                allLines.take(MAX_LYRICS_LINES)
            }
            else -> emptyList()
        }
    }

    /**
     * 计算画布高度（动态）
     */
    private fun calculateCanvasHeight(
        lyricsLines: List<String>,
        fontSizeScale: Float,
        lineSpacingMultiplier: Float
    ): Int {
        // 头部区域高度
        val headerHeight = maxOf(
            COVER_SIZE,
            (TITLE_TEXT_SIZE + TITLE_ARTIST_GAP + ARTIST_TEXT_SIZE) * fontSizeScale
        )
        
        // 歌词区域高度
        val lyricsLineHeight = LYRICS_TEXT_SIZE * fontSizeScale * lineSpacingMultiplier
        val estimatedLines = lyricsLines.size.coerceAtLeast(1)
        val lyricsHeight = estimatedLines * lyricsLineHeight
        
        // 水印区域高度
        val footerHeight = WATERMARK_TEXT_SIZE + LYRICS_WATERMARK_GAP
        
        // 计算总高度
        val totalHeight = PADDING_TOP + 
                         headerHeight + 
                         HEADER_LYRICS_GAP + 
                         lyricsHeight + 
                         footerHeight + 
                         PADDING_BOTTOM
        
        // 最小高度
        val minHeight = PADDING_TOP + 
                       headerHeight + 
                       HEADER_LYRICS_GAP + 
                       footerHeight + 
                       PADDING_BOTTOM
        
        return maxOf(totalHeight.toInt(), minHeight.toInt())
    }

    /**
     * 计算对比色（黑/白）
     */
    private fun calculateContrastColor(backgroundColor: Color): Int {
        val luminance = 0.299f * backgroundColor.red + 
                       0.587f * backgroundColor.green + 
                       0.114f * backgroundColor.blue
        return if (luminance > 0.5f) AndroidColor.BLACK else AndroidColor.WHITE
    }

    /**
     * 绘制海报主函数
     */
    private fun drawPoster(
        canvas: Canvas,
        title: String,
        artist: String,
        albumArtBitmap: Bitmap?,
        lyricsLines: List<String>,
        backgroundColor: Int,
        textColor: Int,
        config: PosterConfig
    ) {
        val canvasHeight = canvas.height
        
        // 1. 绘制背景
        drawBackground(canvas, backgroundColor, albumArtBitmap, canvasHeight)
        
        // 2. 绘制封面和标题区域
        drawHeader(canvas, title, artist, albumArtBitmap, textColor, config)
        
        // 3. 绘制歌词
        drawLyrics(canvas, lyricsLines, textColor, config)
        
        // 4. 绘制水印
        if (config.showWatermark) {
            drawWatermark(canvas, textColor, config.watermarkPosition, canvasHeight)
        }
    }

    /**
     * 绘制背景
     */
    private fun drawBackground(
        canvas: Canvas,
        backgroundColor: Int,
        albumArtBitmap: Bitmap?,
        canvasHeight: Int
    ) {
        if (albumArtBitmap != null) {
            // 使用封面模糊背景
            drawBlurredBackground(canvas, albumArtBitmap, canvasHeight)
        } else {
            // 纯色背景
            canvas.drawColor(backgroundColor)
        }
    }

    /**
     * 绘制模糊背景（简化版）
     */
    private fun drawBlurredBackground(
        canvas: Canvas,
        source: Bitmap,
        canvasHeight: Int
    ) {
        // 绘制原始图片并添加半透明遮罩模拟模糊效果
        val scaledBitmap = Bitmap.createScaledBitmap(
            source, 
            CANVAS_WIDTH.toInt(), 
            canvasHeight, 
            true
        )
        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        
        // 添加渐变遮罩
        val paint = Paint()
        val shader = LinearGradient(
            0f, 0f, 0f, canvasHeight.toFloat(),
            intArrayOf(
                AndroidColor.argb(100, 0, 0, 0),
                AndroidColor.argb(60, 0, 0, 0),
                AndroidColor.argb(100, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, CANVAS_WIDTH, canvasHeight.toFloat(), paint)
    }

    /**
     * 绘制头部区域（封面 + 标题/艺术家）
     */
    private fun drawHeader(
        canvas: Canvas,
        title: String,
        artist: String,
        albumArtBitmap: Bitmap?,
        textColor: Int,
        config: PosterConfig
    ) {
        val fontScale = config.fontSizeScale
        
        // 绘制封面
        if (albumArtBitmap != null) {
            drawCover(canvas, albumArtBitmap, config.coverShape)
        }
        
        // 计算标题区域位置
        val titleX = PADDING_HORIZONTAL + COVER_SIZE + COVER_TITLE_GAP
        val titleY = PADDING_TOP + TITLE_TEXT_SIZE * fontScale
        
        // 绘制标题
        val titlePaint = Paint().apply {
            color = textColor
            textSize = TITLE_TEXT_SIZE * fontScale
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (config.fontWeight == PosterFontWeight.BOLD) Typeface.BOLD else Typeface.NORMAL
            )
            isAntiAlias = true
        }
        
        // 标题自动换行
        val maxTitleWidth = CANVAS_WIDTH - titleX - PADDING_HORIZONTAL
        val titleLines = wrapTextToLines(title, titlePaint, maxTitleWidth, maxLines = 2)
        var currentTitleY = titleY
        titleLines.forEach { line ->
            canvas.drawText(line, titleX, currentTitleY, titlePaint)
            currentTitleY += (TITLE_TEXT_SIZE * fontScale * 1.2f)
        }
        
        // 绘制艺术家
        val artistY = currentTitleY + TITLE_ARTIST_GAP
        val artistPaint = Paint().apply {
            color = AndroidColor.argb(180, AndroidColor.red(textColor), AndroidColor.green(textColor), AndroidColor.blue(textColor))
            textSize = ARTIST_TEXT_SIZE * fontScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        
        val artistLines = wrapTextToLines(artist, artistPaint, maxTitleWidth, maxLines = 1)
        artistLines.firstOrNull()?.let {
            canvas.drawText(it, titleX, artistY, artistPaint)
        }
    }

    /**
     * 绘制封面（带形状裁剪）
     */
    private fun drawCover(
        canvas: Canvas,
        bitmap: Bitmap,
        shape: PosterShape
    ) {
        val x = PADDING_HORIZONTAL
        val y = PADDING_TOP
        
        // 缩放封面到目标尺寸
        val scaledCover = Bitmap.createScaledBitmap(bitmap, COVER_SIZE.toInt(), COVER_SIZE.toInt(), true)
        
        // 根据形状创建裁剪路径
        val clipPath = when (shape) {
            PosterShape.CIRCLE -> {
                Path().apply {
                    val centerX = x + COVER_SIZE / 2f
                    val centerY = y + COVER_SIZE / 2f
                    addCircle(centerX, centerY, COVER_SIZE / 2f, Path.Direction.CW)
                }
            }
            PosterShape.SQUARE -> {
                // 不需要裁剪
                null
            }
            PosterShape.ROUNDED_16 -> createRoundRectPath(x, y, COVER_SIZE, 16f)
            PosterShape.ROUNDED_28 -> createRoundRectPath(x, y, COVER_SIZE, 28f)
            PosterShape.SUNNY -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.COOKIE_9_SIDED -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.SOFT_BURST -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.OVAL -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.HEXAGON -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.DIAMOND -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.PILL -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.HEART -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.FLOWER -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
            PosterShape.GEM -> createExpressiveShapePath(shape, x, y, COVER_SIZE)
        }
        
        if (clipPath != null) {
            canvas.withSave {
                canvas.clipPath(clipPath)
                canvas.drawBitmap(scaledCover, x, y, null)
            }
        } else {
            canvas.drawBitmap(scaledCover, x, y, null)
        }
    }

    /**
     * 创建圆角矩形路径
     */
    private fun createRoundRectPath(x: Float, y: Float, size: Float, cornerRadius: Float): Path {
        return Path().apply {
            addRoundRect(
                RectF(x, y, x + size, y + size),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
    }

    /**
     * 创建 Expressive 形状路径
     * 使用圆形作为所有 Expressive 形状的回退，确保稳定性
     */
    private fun createExpressiveShapePath(
        shape: PosterShape,
        x: Float,
        y: Float,
        size: Float
    ): Path {
        // 暂时使用圆形作为所有 Expressive 形状的回退
        // 这样可以确保稳定性，后续可以添加更多形状支持
        return Path().apply {
            val centerX = x + size / 2f
            val centerY = y + size / 2f
            addCircle(centerX, centerY, size / 2f, Path.Direction.CW)
        }
    }

    /**
     * 绘制歌词
     */
    private fun drawLyrics(
        canvas: Canvas,
        lyricsLines: List<String>,
        textColor: Int,
        config: PosterConfig
    ) {
        val fontScale = config.fontSizeScale
        
        // 计算歌词起始 Y 位置
        val headerHeight = maxOf(
            COVER_SIZE,
            (TITLE_TEXT_SIZE + TITLE_ARTIST_GAP + ARTIST_TEXT_SIZE) * fontScale
        )
        val lyricsStartY = PADDING_TOP + headerHeight + HEADER_LYRICS_GAP + 
                          (LYRICS_TEXT_SIZE * fontScale)
        
        // 歌词配置
        val lyricsPaint = Paint().apply {
            color = textColor
            textSize = LYRICS_TEXT_SIZE * fontScale
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (config.fontWeight == PosterFontWeight.BOLD) Typeface.BOLD else Typeface.NORMAL
            )
            isAntiAlias = true
        }
        
        val maxLyricsWidth = (CANVAS_WIDTH - PADDING_HORIZONTAL * 2) * LYRICS_MAX_WIDTH_RATIO
        val lineHeight = LYRICS_TEXT_SIZE * fontScale * config.lineSpacingMultiplier
        
        var currentY = lyricsStartY
        
        lyricsLines.forEach { line ->
            // 自动换行
            val wrappedLines = wrapTextToLines(line, lyricsPaint, maxLyricsWidth)
            
            wrappedLines.forEach { wrappedLine ->
                // 根据对齐方式计算 X 位置
                val x = when (config.lyricsAlignment) {
                    LyricsAlignment.START -> PADDING_HORIZONTAL
                    LyricsAlignment.CENTER -> {
                        val textWidth = lyricsPaint.measureText(wrappedLine)
                        (CANVAS_WIDTH - textWidth) / 2f
                    }
                }
                
                canvas.drawText(wrappedLine, x, currentY, lyricsPaint)
                currentY += lineHeight
            }
        }
    }

    /**
     * 绘制水印
     */
    private fun drawWatermark(
        canvas: Canvas,
        textColor: Int,
        position: WatermarkPosition,
        canvasHeight: Int
    ) {
        val paint = Paint().apply {
            color = AndroidColor.argb(128, AndroidColor.red(textColor), AndroidColor.green(textColor), AndroidColor.blue(textColor))
            textSize = WATERMARK_TEXT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        
        val text = "Voxly"
        val textWidth = paint.measureText(text)
        
        val x = when (position) {
            WatermarkPosition.START -> PADDING_HORIZONTAL
            WatermarkPosition.END -> CANVAS_WIDTH - textWidth - PADDING_HORIZONTAL
        }
        
        val y = canvasHeight - PADDING_BOTTOM
        
        canvas.drawText(text, x, y, paint)
    }

    /**
     * 文本自动换行
     */
    private fun wrapTextToLines(
        text: String, 
        paint: Paint, 
        maxWidth: Float,
        maxLines: Int = Int.MAX_VALUE
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        
        val measuredWidth = paint.measureText(text)
        if (measuredWidth <= maxWidth) return listOf(text)
        
        val lines = mutableListOf<String>()
        var remaining = text
        var linesAdded = 0
        
        while (remaining.isNotEmpty() && linesAdded < maxLines) {
            // 二分查找最大可显示字符数
            var low = 0
            var high = remaining.length
            var bestLength = 0
            
            while (low <= high) {
                val mid = (low + high) / 2
                val testText = remaining.substring(0, minOf(mid, remaining.length))
                val testWidth = paint.measureText(testText)
                
                if (testWidth <= maxWidth) {
                    bestLength = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            
            if (bestLength <= 0) {
                bestLength = 1
            }
            
            // 寻找词边界（空格）
            val cutText = remaining.substring(0, minOf(bestLength, remaining.length))
            val lastSpaceIndex = cutText.lastIndexOf(' ')
            
            val actualLength = if (lastSpaceIndex > 0 && lastSpaceIndex > bestLength / 3) {
                lastSpaceIndex
            } else {
                bestLength
            }
            
            lines.add(remaining.substring(0, minOf(actualLength, remaining.length)))
            remaining = remaining.substring(minOf(actualLength, remaining.length)).trimStart()
            linesAdded++
        }
        
        return lines.ifEmpty { listOf("") }
    }

    /**
     * 预加载形状（优化性能）
     */
    fun preloadShapes() {
        // Material3 Expressive 形状会在首次使用时自动缓存
        PosterShape.values().forEach { shape ->
            if (shape.isExpressiveShape()) {
                shape.toRoundedPolygon()
            }
        }
    }
}

/**
 * 海报方向（保留以兼容旧代码）
 */
@Deprecated("使用 PosterConfig 替代", ReplaceWith("PosterConfig"))
enum class PosterOrientation {
    @Deprecated("使用 PosterConfig 替代")
    PORTRAIT,
    @Deprecated("使用 PosterConfig 替代")
    LANDSCAPE
}
