@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.random.Random
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * 歌词海报卡片组件（路由入口）
 * 
 * 根据 config.style 分发到对应的模板实现
 */
@Composable
fun LyricsPosterCard(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    when (config.style) {
        PosterStyle.CARD -> LyricsPosterCardClassic(
            title = title,
            artist = artist,
            albumArt = albumArt,
            lyrics = lyrics,
            config = config,
            modifier = modifier
        )
        PosterStyle.IMMERSIVE -> LyricsPosterCardImmersive(
            title = title,
            artist = artist,
            albumArt = albumArt,
            lyrics = lyrics,
            config = config,
            modifier = modifier
        )
        PosterStyle.TYPOGRAPHY -> LyricsPosterCardTypography(
            title = title,
            artist = artist,
            albumArt = albumArt,
            lyrics = lyrics,
            config = config,
            modifier = modifier
        )
        PosterStyle.COLLAGE -> LyricsPosterCardCollage(
            title = title,
            artist = artist,
            albumArt = albumArt,
            lyrics = lyrics,
            config = config,
            modifier = modifier
        )
    }
}

/**
 * 带模糊背景的海报卡片
 *
 * 关键修复：使用 wrapContentHeight 代替 matchParentSize，确保动态高度
 * 支持多种背景模式：
 * - BLURRED_COVER: 使用50.dp高斯模糊专辑封面作为背景，带轻微暗角遮罩
 * - GRADIENT: 使用从封面提取的颜色随机组合成径向渐变，带50.dp高斯模糊
 * - 其他主题: 使用纯色背景
 */
@Composable
fun LyricsPosterCardWithBlurBackground(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    val isBlurredCoverTheme = config.colorTheme == PosterColorTheme.BLURRED_COVER
    val isGradientTheme = config.colorTheme == PosterColorTheme.GRADIENT

    // 为渐变色主题生成随机渐变色和方向 - 使用 LaunchedEffect 在后台执行
    var gradientBrush by remember { mutableStateOf<Brush?>(null) }
    
    LaunchedEffect(albumArt, config.colorTheme) {
        if (isGradientTheme && albumArt != null) {
            val allColors = ColorExtractor.extractAllColorsSuspend(albumArt)
            if (allColors.size >= 2) {
                val shuffled = allColors.shuffled()
                val colorCount = Random.nextInt(2, 4)
                val selectedColors = shuffled.take(colorCount)
                
                val centerX = Random.nextFloat() * 0.2f + 0.4f
                val centerY = Random.nextFloat() * 0.2f + 0.4f
                val radius = 2.5f
                
                gradientBrush = Brush.radialGradient(
                    colors = selectedColors,
                    center = Offset(centerX, centerY),
                    radius = radius,
                    tileMode = TileMode.Clamp
                )
            } else {
                gradientBrush = null
            }
        } else {
            gradientBrush = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        when {
            // 模糊封面背景模式
            isBlurredCoverTheme && albumArt != null -> {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .blur(50.dp),
                    contentScale = ContentScale.Crop
                )

                // 轻微暗角遮罩层
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
            
            // 渐变色背景模式
            isGradientTheme && gradientBrush != null -> {
                // 应用径向渐变背景，添加高斯模糊创造柔和效果
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(gradientBrush!!)
                        .blur(30.dp)
                )

                // 轻微暗角遮罩层增强文字可读性
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.12f))
                )
            }
        }

        // 海报内容 - 前景层
        LyricsPosterCard(
            title = title,
            artist = artist,
            albumArt = albumArt,
            lyrics = lyrics,
            config = config,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
