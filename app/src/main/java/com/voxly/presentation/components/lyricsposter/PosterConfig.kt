@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.voxly.presentation.theme.ExpressiveShapes

/**
 * 歌词海报风格 - 仅保留单一卡片风格
 */
enum class PosterStyle {
    CARD    // 卡片风格 - 固定宽度，动态高度
}

/**
 * 歌词海报颜色主题
 */
enum class PosterColorTheme {
    MUTED,          // 柔和 - 使用 muted 颜色
    VIBRANT,        // 鲜艳 - 使用 vibrant 颜色
    BLURRED_COVER,  // 模糊封面 - 使用25.dp高斯模糊专辑封面作为背景
    GRADIENT,       // 渐变色 - 提取封面颜色随机组合成径向渐变
    CUSTOM          // 自定义 - 用户选择颜色
}

/**
 * 字体粗细选项
 */
enum class PosterFontWeight {
    REGULAR,    // 正常粗细
    BOLD        // 粗体
}

/**
 * 歌词对齐方式
 */
enum class LyricsAlignment {
    START,      // 左对齐
    CENTER      // 居中对齐
}

/**
 * 水印位置
 */
enum class WatermarkPosition {
    START,      // 左下角
    END         // 右下角
}

/**
 * 封面形状选项
 * 包含标准形状和 Material3 Expressive 形状
 */
enum class PosterShape {
    // 标准形状
    ROUNDED_16,      // 16dp 圆角
    ROUNDED_28,      // 28dp 圆角（MD3 Expressive）
    CIRCLE,          // 圆形
    SQUARE,          // 方形（无圆角）
    
    // Material3 Expressive 形状
    SUNNY,           // 12边形太阳
    COOKIE_9_SIDED,  // 9边形饼干
    SOFT_BURST,      // 柔和爆炸/星形
    OVAL,            // 椭圆形
    HEXAGON,         // 六边形
    DIAMOND,         // 菱形
    PILL,            // 药丸形
    HEART,           // 心形
    FLOWER,          // 花朵
    GEM;             // 宝石
    
    /**
     * 转换为 Compose Shape
     */
    fun toComposeShape(): Shape {
        return when (this) {
            ROUNDED_16 -> RoundedCornerShape(16.dp)
            ROUNDED_28 -> ExpressiveShapes.ExtraLarge
            CIRCLE -> CircleShape
            SQUARE -> RoundedCornerShape(0.dp)
            // Material3 Expressive 形状 - 这些需要通过其他方式处理
            // 在 Canvas 绘制时使用 toRoundedPolygon()
            else -> RoundedCornerShape(16.dp) // 默认回退
        }
    }
    
    /**
     * 获取 Material3 Expressive RoundedPolygon（仅对 Expressive 形状有效）
     */
    fun toRoundedPolygon(): androidx.graphics.shapes.RoundedPolygon? {
        return when (this) {
            SUNNY -> MaterialShapes.Sunny
            COOKIE_9_SIDED -> MaterialShapes.Cookie9Sided
            SOFT_BURST -> MaterialShapes.SoftBurst
            OVAL -> MaterialShapes.Oval
            HEXAGON -> MaterialShapes.Cookie6Sided
            DIAMOND -> MaterialShapes.Diamond
            PILL -> MaterialShapes.Pill
            HEART -> MaterialShapes.Heart
            FLOWER -> MaterialShapes.Flower
            GEM -> MaterialShapes.Gem
            else -> null
        }
    }
    
    /**
     * 判断是否为 Material3 Expressive 形状
     */
    fun isExpressiveShape(): Boolean {
        return this in setOf(
            SUNNY, COOKIE_9_SIDED, SOFT_BURST, OVAL, HEXAGON, 
            DIAMOND, PILL, HEART, FLOWER, GEM
        )
    }
    
    /**
     * 获取显示名称
     */
    fun getDisplayName(): String {
        return when (this) {
            ROUNDED_16 -> "圆角 16"
            ROUNDED_28 -> "圆角 28"
            CIRCLE -> "圆形"
            SQUARE -> "方形"
            SUNNY -> "太阳"
            COOKIE_9_SIDED -> "饼干"
            SOFT_BURST -> "星形"
            OVAL -> "椭圆"
            HEXAGON -> "六边形"
            DIAMOND -> "菱形"
            PILL -> "药丸"
            HEART -> "心形"
            FLOWER -> "花朵"
            GEM -> "宝石"
        }
    }
    
    companion object {
        /**
         * 获取所有标准形状
         */
        fun standardShapes(): List<PosterShape> = listOf(
            ROUNDED_16, ROUNDED_28, CIRCLE, SQUARE
        )
        
        /**
         * 获取所有 Expressive 形状
         */
        fun expressiveShapes(): List<PosterShape> = listOf(
            SUNNY, COOKIE_9_SIDED, SOFT_BURST, OVAL, HEXAGON,
            DIAMOND, PILL, HEART, FLOWER, GEM
        )
        
        /**
         * 获取所有可用形状
         */
        fun allShapes(): List<PosterShape> = values().toList()
    }
}

/**
 * 歌词海报配置数据类（重构版）
 * 包含完整的自定义选项
 */
data class PosterConfig(
    // 海报风格
    val style: PosterStyle = PosterStyle.CARD,
    
    // 封面形状
    val coverShape: PosterShape = PosterShape.ROUNDED_16,
    
    // 颜色主题
    val colorTheme: PosterColorTheme = PosterColorTheme.VIBRANT,
    val customBackgroundColor: Color? = null,
    val customContentColor: Color? = null,
    
    // 字体设置
    val fontWeight: PosterFontWeight = PosterFontWeight.BOLD,
    val fontSizeScale: Float = 1.0f,
    
    // 布局设置
    val lyricsAlignment: LyricsAlignment = LyricsAlignment.START,
    val watermarkPosition: WatermarkPosition = WatermarkPosition.START,
    val lineSpacingMultiplier: Float = 1.4f,
    
    // 功能开关
    val showWatermark: Boolean = true,
    val enableAutoTextColor: Boolean = true
) {
    init {
        // 验证参数范围
        require(fontSizeScale in 0.5f..1.5f) { "fontSizeScale 必须在 0.5f 到 1.5f 之间" }
        require(lineSpacingMultiplier in 1.2f..1.6f) { "lineSpacingMultiplier 必须在 1.2f 到 1.6f 之间" }
    }
    
    /**
     * 创建副本并更新指定字段
     */
    fun copyWith(
        style: PosterStyle? = null,
        coverShape: PosterShape? = null,
        colorTheme: PosterColorTheme? = null,
        customBackgroundColor: Color? = null,
        customContentColor: Color? = null,
        fontWeight: PosterFontWeight? = null,
        fontSizeScale: Float? = null,
        lyricsAlignment: LyricsAlignment? = null,
        watermarkPosition: WatermarkPosition? = null,
        lineSpacingMultiplier: Float? = null,
        showWatermark: Boolean? = null,
        enableAutoTextColor: Boolean? = null
    ): PosterConfig {
        return PosterConfig(
            style = style ?: this.style,
            coverShape = coverShape ?: this.coverShape,
            colorTheme = colorTheme ?: this.colorTheme,
            customBackgroundColor = customBackgroundColor ?: this.customBackgroundColor,
            customContentColor = customContentColor ?: this.customContentColor,
            fontWeight = fontWeight ?: this.fontWeight,
            fontSizeScale = fontSizeScale ?: this.fontSizeScale,
            lyricsAlignment = lyricsAlignment ?: this.lyricsAlignment,
            watermarkPosition = watermarkPosition ?: this.watermarkPosition,
            lineSpacingMultiplier = lineSpacingMultiplier ?: this.lineSpacingMultiplier,
            showWatermark = showWatermark ?: this.showWatermark,
            enableAutoTextColor = enableAutoTextColor ?: this.enableAutoTextColor
        )
    }
}
