package com.voxly.presentation.components.lyricsposter

/**
 * 歌词海报风格 - 仅保留Spotify风格
 */
enum class PosterStyle {
    SPOTIFY    // Spotify风格 - 封面主导，模糊背景
}

/**
 * 歌词海报配置数据类
 */
data class PosterConfig(
    val style: PosterStyle = PosterStyle.SPOTIFY,
    val fontSizeScale: Float = 1.0f,
    val showWatermark: Boolean = true
)
