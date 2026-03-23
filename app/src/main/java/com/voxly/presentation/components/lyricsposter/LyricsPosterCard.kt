@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.random.Random
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.voxly.presentation.theme.ExpressiveShapes

/**
 * 歌词海报卡片组件（Compose UI 实现）
 * 
 * 使用 Compose 声明式 UI 构建海报，支持完整的形状系统和动态高度
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
    // 判断是否为特殊背景主题（模糊封面或渐变色）
    val isSpecialBackgroundTheme = config.colorTheme == PosterColorTheme.BLURRED_COVER ||
            config.colorTheme == PosterColorTheme.GRADIENT

    // 计算背景色：特殊背景主题使用透明背景（背景由父组件处理）
    val backgroundColor = when {
        isSpecialBackgroundTheme -> Color.Transparent
        config.colorTheme == PosterColorTheme.CUSTOM -> config.customBackgroundColor ?: Color.DarkGray
        else -> Color.DarkGray
    }

    // 计算内容颜色：特殊背景主题优先使用白色文字以确保可读性
    val contentColor = when {
        isSpecialBackgroundTheme -> Color.White
        config.enableAutoTextColor -> calculateContrastColor(backgroundColor)
        else -> config.customContentColor ?: Color.White
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(32.dp)
    ) {
        Column {
            // 头部：封面 + 标题/艺术家
            HeaderRow(
                title = title,
                artist = artist,
                albumArt = albumArt,
                config = config,
                contentColor = contentColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 歌词区域
            LyricsSection(
                lyrics = lyrics,
                config = config,
                contentColor = contentColor
            )

            // 水印（根据配置显示/隐藏）
            if (config.showWatermark) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Watermark(
                    config = config,
                    contentColor = contentColor
                )
            }
        }
    }
}

/**
 * 头部行：封面 + 标题/艺术家
 */
@Composable
private fun HeaderRow(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    config: PosterConfig,
    contentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // 封面
        AlbumArtWithShape(
            bitmap = albumArt?.asImageBitmap(),
            shape = config.coverShape,
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.width(24.dp))

        // 标题和艺术家
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.weight(1f)
        ) {
            // 标题
            Text(
                text = title,
                fontSize = (28f * config.fontSizeScale).sp,
                fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                    FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 2,
                lineHeight = (36f * config.fontSizeScale).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 艺术家
            Text(
                text = artist,
                fontSize = (20f * config.fontSizeScale).sp,
                fontWeight = FontWeight.Normal,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/**
 * 带形状的专辑封面
 * 
 * 使用 Shape Library 中的所有形状
 */
@Composable
private fun AlbumArtWithShape(
    bitmap: ImageBitmap?,
    shape: PosterShape,
    modifier: Modifier = Modifier
) {
    val shapeModifier = when (shape) {
        // 标准形状
        PosterShape.ROUNDED_16 -> Modifier.clip(RoundedCornerShape(16.dp))
        PosterShape.ROUNDED_28 -> Modifier.clip(RoundedCornerShape(28.dp))
        PosterShape.CIRCLE -> Modifier.clip(CircleShape)
        PosterShape.SQUARE -> Modifier
        
        // Material3 Expressive 形状 - 使用 Shape Library
        PosterShape.SUNNY -> Modifier.clip(MaterialShapes.Sunny.toShape())
        PosterShape.COOKIE_9_SIDED -> Modifier.clip(MaterialShapes.Cookie9Sided.toShape())
        PosterShape.SOFT_BURST -> Modifier.clip(MaterialShapes.SoftBurst.toShape())
        PosterShape.OVAL -> Modifier.clip(MaterialShapes.Oval.toShape())
        PosterShape.HEXAGON -> Modifier.clip(MaterialShapes.Cookie6Sided.toShape())
        PosterShape.DIAMOND -> Modifier.clip(MaterialShapes.Diamond.toShape())
        PosterShape.PILL -> Modifier.clip(MaterialShapes.Pill.toShape())
        PosterShape.HEART -> Modifier.clip(MaterialShapes.Heart.toShape())
        PosterShape.FLOWER -> Modifier.clip(MaterialShapes.Flower.toShape())
        PosterShape.GEM -> Modifier.clip(MaterialShapes.Gem.toShape())
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.then(shapeModifier),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .then(shapeModifier)
                .background(Color.Gray)
        )
    }
}

/**
 * 歌词区域
 */
@Composable
private fun LyricsSection(
    lyrics: List<String>,
    config: PosterConfig,
    contentColor: Color
) {
    val textAlign = when (config.lyricsAlignment) {
        LyricsAlignment.START -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
    }

    val horizontalAlignment = when (config.lyricsAlignment) {
        LyricsAlignment.START -> Alignment.Start
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(
            (24f * config.fontSizeScale * (config.lineSpacingMultiplier - 1)).dp
        )
    ) {
        lyrics.forEach { line ->
            Text(
                text = line,
                fontSize = (24f * config.fontSizeScale).sp,
                fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                    FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = (34f * config.fontSizeScale * config.lineSpacingMultiplier).sp
            )
        }
    }
}

/**
 * 水印
 */
@Composable
private fun Watermark(
    config: PosterConfig,
    contentColor: Color
) {
    val alignment = when (config.watermarkPosition) {
        WatermarkPosition.START -> Alignment.CenterStart
        WatermarkPosition.END -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Text(
            text = "Voxly",
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = contentColor.copy(alpha = 0.5f)
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

    // 为渐变色主题生成随机渐变色和方向
    val gradientBrush = remember(albumArt, config.colorTheme) {
        if (isGradientTheme && albumArt != null) {
            val allColors = ColorExtractor.extractAllColors(albumArt)
            if (allColors.size >= 2) {
                // 随机选择2-3个颜色
                val shuffled = allColors.shuffled()
                val colorCount = Random.nextInt(2, 4) // 2 or 3 colors
                val selectedColors = shuffled.take(colorCount)
                
                // 使用径向渐变，从中心向外扩散
                val centerX = Random.nextFloat() * 0.4f + 0.3f // 0.3-0.7，偏向中心
                val centerY = Random.nextFloat() * 0.4f + 0.3f // 0.3-0.7，偏向中心
                val radius = Random.nextFloat() * 0.6f + 0.7f // 0.7-1.3，适中的半径
                
                Brush.radialGradient(
                    colors = selectedColors,
                    center = Offset(centerX, centerY),
                    radius = radius
                )
            } else {
                null
            }
        } else {
            null
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
                        .background(gradientBrush)
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

/**
 * 计算对比色
 */
private fun calculateContrastColor(backgroundColor: Color): Color {
    val luminance = 0.299 * backgroundColor.red +
                   0.587 * backgroundColor.green +
                   0.114 * backgroundColor.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}
