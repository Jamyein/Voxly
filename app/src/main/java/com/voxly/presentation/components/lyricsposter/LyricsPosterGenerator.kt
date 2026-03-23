@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.Lyrics.Companion.parseToLines

/**
 * 歌词海报生成器（Compose UI 版本）
 * 
 * 使用 Compose UI 生成海报，确保预览和实际生成完全一致
 * 
 * 特性：
 * - 使用 LyricsPosterCard 进行渲染
 * - 支持完整的 Material3 Expressive 形状
 * - 动态高度计算
 * - 统一的颜色主题系统
 */
object LyricsPosterGenerator {

    private const val CANVAS_WIDTH = 800
    private const val MAX_LYRICS_LINES = 6

    /**
     * 生成歌词海报
     * 
     * 使用 Compose UI 渲染，确保与预览完全一致
     * 
     * @param context Android Context
     * @param title 歌曲标题
     * @param artist 艺术家名
     * @param album 专辑名
     * @param lyricsText 完整歌词文本
     * @param albumArtBitmap 封面图片
     * @param backgroundColor 背景色（保留参数，实际从 config 读取）
     * @param contentColor 内容色（保留参数，实际从 config 读取）
     * @param selectedLyrics 选中的歌词行列表
     * @param fontSizeScale 字体缩放比例（保留参数，配置中已包含）
     * @param config 海报配置
     * @return 生成的海报 Bitmap
     */
    suspend fun generatePoster(
        context: Context,
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
        // 处理歌词
        val lyricsLines = processLyrics(lyricsText, selectedLyrics)
        
        // 创建统一的配置（使用传入的颜色）
        val unifiedConfig = config.copy(
            colorTheme = PosterColorTheme.CUSTOM,
            customBackgroundColor = backgroundColor,
            customContentColor = contentColor
        )
        
        // 使用 Compose UI 生成海报
        return PosterCaptureUtil.captureToBitmap(
            context = context,
            content = {
                LyricsPosterCardWithBlurBackground(
                    title = title,
                    artist = artist,
                    albumArt = albumArtBitmap,
                    lyrics = lyricsLines,
                    config = unifiedConfig
                )
            },
            params = PosterCaptureUtil.CaptureParams(
                width = CANVAS_WIDTH,
                maxHeight = 2400
            )
        )
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
     * 预加载形状（优化性能）
     * 
     * 在使用前调用，缓存形状 Path
     */
    fun preloadShapes() {
        // Material3 Expressive 形状会在首次使用时自动缓存
        PosterShape.values().forEach { shape ->
            if (shape.isExpressiveShape()) {
                // 触发形状初始化
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
