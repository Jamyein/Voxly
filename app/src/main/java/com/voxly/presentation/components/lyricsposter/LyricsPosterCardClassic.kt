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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.random.Random
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
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
 * 经典卡片风格歌词海报
 * 
 * 从原有 LyricsPosterCard 提取的卡片布局实现
 */
@Composable
fun LyricsPosterCardClassic(
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
            modifier = Modifier.size(120.dp)
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
                fontSize = (32f * config.fontSizeScale).sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                lineHeight = (40f * config.fontSizeScale).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 艺术家
            Text(
                text = artist,
                fontSize = (16f * config.fontSizeScale).sp,
                fontWeight = FontWeight.Medium,
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
            (16f * config.fontSizeScale * (config.lineSpacingMultiplier - 1)).dp
        )
    ) {
        lyrics.forEach { line ->
            Text(
                text = line,
                fontSize = (22f * config.fontSizeScale).sp,
                fontWeight = if (config.fontWeight == PosterFontWeight.BOLD)
                    FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = (32f * config.fontSizeScale * config.lineSpacingMultiplier).sp
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 左侧装饰线
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(1.dp)
                    .background(contentColor.copy(alpha = 0.3f))
            )
            Text(
                text = "Voxly · Lyrics Poster",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 计算对比色
 */
internal fun calculateContrastColor(backgroundColor: Color): Color {
    val luminance = 0.299 * backgroundColor.red +
                   0.587 * backgroundColor.green +
                   0.114 * backgroundColor.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}
