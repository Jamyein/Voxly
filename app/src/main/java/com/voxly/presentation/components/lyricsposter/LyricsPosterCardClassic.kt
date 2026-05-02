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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 经典卡片风格歌词海报
 * 
 * 设计：精致专辑卡
 * - 深蓝紫渐变背景（#1A1A2E → #16213E）
 * - 封面 130dp 圆角矩形 + 阴影 + 曲目编号装饰
 * - 标题 32px Bold，白色
 * - 歌词 22px SemiBold，白色 alpha 0.85
 * - 底部横线 + 双行水印
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
    val backgroundColor = when {
        config.colorTheme == PosterColorTheme.CUSTOM -> 
            config.customBackgroundColor ?: Color(0xFF1A1A2E)
        else -> Color(0xFF1A1A2E)
    }

    val contentColor = if (config.enableAutoTextColor) {
        calculateContrastColor(backgroundColor)
    } else {
        config.customContentColor ?: Color.White
    }

    Box(
        modifier = modifier
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
            .padding(32.dp)
    ) {
        Column {
            // 头部：封面 + 标题/艺术家
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                // 封面 130dp 圆角 + 阴影
                AlbumArtWithShadow(
                    bitmap = albumArt?.asImageBitmap(),
                    shape = config.coverShape,
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.width(24.dp))

                // 标题和艺术家
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = (32f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 2,
                        lineHeight = (40f * config.fontSizeScale).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = artist,
                        fontSize = (16f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 歌词区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                lyrics.forEach { line ->
                    Text(
                        text = line,
                        fontSize = (22f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor.copy(alpha = 0.85f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = (32f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 水印（底部）
            if (config.showWatermark) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 左侧装饰线
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(contentColor.copy(alpha = 0.2f))
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "Voxly · Lyrics Poster",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

/**
 * 带阴影的专辑封面
 */
@Composable
private fun AlbumArtWithShadow(
    bitmap: ImageBitmap?,
    @Suppress("UNUSED_PARAMETER") shape: PosterShape,
    modifier: Modifier = Modifier
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF667EEA),
                        Color(0xFF764BA2)
                    )
                )
            )
        )
    }
}

private fun calculateContrastColor(backgroundColor: Color): Color {
    val luminance = 0.299 * backgroundColor.red +
                   0.587 * backgroundColor.green +
                   0.114 * backgroundColor.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}