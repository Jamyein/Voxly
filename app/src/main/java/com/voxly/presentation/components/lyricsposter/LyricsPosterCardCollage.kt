@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 网格拼贴歌词海报模板
 * 
 * 特点：
 * - 网格布局：封面 + 歌词前2行并排
 * - 装饰色块
 * - 剩余歌词下方排列
 * - 底部装饰 + 歌曲信息
 */
@Composable
fun LyricsPosterCardCollage(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        config.colorTheme == PosterColorTheme.CUSTOM -> config.customBackgroundColor ?: Color(0xFF1E1E2E)
        else -> Color(0xFF1E1E2E)
    }

    val contentColor = if (config.enableAutoTextColor) {
        calculateContrastColor(backgroundColor)
    } else {
        config.customContentColor ?: Color.White
    }

    // 提取装饰色
    val accentColor1 = if (albumArt != null) {
        val extracted = ColorExtractor.extractColors(albumArt)
        Color(extracted.backgroundDominant).copy(alpha = 0.2f)
    } else {
        contentColor.copy(alpha = 0.15f)
    }

    val accentColor2 = if (albumArt != null) {
        val allColors = ColorExtractor.extractAllColors(albumArt)
        if (allColors.size > 1) allColors[1].copy(alpha = 0.15f) else accentColor1
    } else {
        contentColor.copy(alpha = 0.1f)
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(36.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 第一行：封面 + 歌词（前2行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 左侧：封面
                if (albumArt != null) {
                    val shapeModifier = when (config.coverShape) {
                        PosterShape.CIRCLE -> Modifier.clip(CircleShape)
                        else -> Modifier.clip(RoundedCornerShape(12.dp))
                    }
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .then(shapeModifier),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.Gray)
                    )
                }

                // 右侧：歌词前2行
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lyrics.take(2).forEach { line ->
                        Text(
                            text = line,
                            fontSize = (22f * config.fontSizeScale).sp,
                            fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                                FontWeight.Bold else FontWeight.Normal,
                            color = contentColor,
                            lineHeight = (30f * config.fontSizeScale).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 装饰色块区域
            Box(modifier = Modifier.fillMaxWidth()) {
                DecorativeBlock(
                    color = accentColor1,
                    width = 160.dp,
                    height = 60.dp,
                    cornerRadius = 8.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 剩余歌词
            if (lyrics.size > 2) {
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
                    lyrics.drop(2).forEach { line ->
                        Text(
                            text = line,
                            fontSize = (22f * config.fontSizeScale).sp,
                            fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                                FontWeight.Bold else FontWeight.Normal,
                            color = contentColor,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (30f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 底部装饰色块 + 歌曲信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                DecorativeBlock(
                    color = accentColor2,
                    width = 100.dp,
                    height = 40.dp,
                    cornerRadius = 20.dp
                )

                Text(
                    text = "$title - $artist",
                    fontSize = (16f * config.fontSizeScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}
