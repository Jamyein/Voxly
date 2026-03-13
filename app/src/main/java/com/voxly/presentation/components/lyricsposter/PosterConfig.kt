package com.voxly.presentation.components.lyricsposter

/**
 * 歌词海报风格枚举
 */
enum class PosterStyle {
    SPOTIFY,    // Spotify 风格 - 封面主导，模糊背景
    RUSHED,     // Rush 风格 - 封面图片 + 歌词卡片悬浮
    MINIMAL,    // 极简暗黑 - 深色渐变，无封面
    ARTISTIC    // 艺术几何 - 紫粉渐变 + 几何装饰
}

/**
 * 歌词海报配置数据类
 */
data class PosterConfig(
    val style: PosterStyle = PosterStyle.SPOTIFY,
    val fontSizeScale: Float = 1.0f,
    val showWatermark: Boolean = true,
    val cardCornerRadius: Float = 16f,  // Rushed 风格卡片圆角
    val gradientDirection: GradientDirection = GradientDirection.VERTICAL,  // 极简风格渐变方向
    val artisticColorScheme: ArtisticColorScheme = ArtisticColorScheme.PURPLE  // 艺术风格主色调
)

enum class GradientDirection {
    VERTICAL,
    HORIZONTAL,
    DIAGONAL
}

enum class ArtisticColorScheme {
    PURPLE,    // 紫色系
    PINK,      // 粉红色系
    BLUE       // 蓝色系
}
