@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
 * - 真正的拼贴布局：元素分布在画布不同位置
 * - 封面作为半透明纹理背景
 * - 歌词分为3个文本块，不同字号
 * - 渐变装饰色块
 * - 底部居中歌曲信息 + 装饰线
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
        config.colorTheme == PosterColorTheme.CUSTOM -> 
            config.customBackgroundColor ?: Color(0xFF1E1E2E)
        else -> Color(0xFF1E1E2E)
    }

    val contentColor = if (config.enableAutoTextColor) {
        calculateContrastColor(backgroundColor)
    } else {
        config.customContentColor ?: Color.White
    }

    // 提取装饰色
    val accentColor = if (albumArt != null) {
        val extracted = ColorExtractor.extractColors(albumArt)
        Color(extracted.backgroundDominant)
    } else {
        contentColor
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(40.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 区块1：前2行歌词（最大字号，左上）
            if (lyrics.isNotEmpty()) {
                LyricsBlock(
                    lines = lyrics.take(2),
                    fontSize = 26f,
                    lineHeight = 36f,
                    contentColor = contentColor,
                    config = config,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }

            // 区块2：封面 + 第3-4行歌词并排
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：半透明封面作为纹理
                if (albumArt != null) {
                    Box(
                        modifier = Modifier.size(100.dp)
                    ) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            alpha = 0.3f
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                }

                // 右侧：第3-4行歌词
                if (lyrics.size > 2) {
                    LyricsBlock(
                        lines = lyrics.drop(2).take(2),
                        fontSize = 20f,
                        lineHeight = 28f,
                        contentColor = contentColor,
                        config = config,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 24.dp)
                    )
                }
            }

            // 装饰色块（渐变）
            if (albumArt != null) {
                DecorativeBlock(
                    color = accentColor.copy(alpha = 0.15f),
                    width = 120.dp,
                    height = 48.dp,
                    cornerRadius = 24.dp,
                    gradientBrush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.2f),
                            accentColor.copy(alpha = 0.05f)
                        )
                    )
                )
            }

            // 区块3：剩余歌词（右下对齐）
            if (lyrics.size > 4) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    LyricsBlock(
                        lines = lyrics.drop(4),
                        fontSize = 18f,
                        lineHeight = 26f,
                        contentColor = contentColor,
                        config = config,
                        modifier = Modifier.fillMaxWidth(0.75f),
                        textAlign = TextAlign.End
                    )
                }
            }

            // 底部：歌曲信息（居中，带装饰线）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentLine(
                    color = contentColor.copy(alpha = 0.2f),
                    strokeWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth(0.3f)
                )
                
                Text(
                    text = "$title · $artist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
                
                AccentDotCluster(
                    color = accentColor,
                    dotSize = 3.dp,
                    spacing = 4.dp
                )
            }
        }
    }
}

/**
 * 可复用的歌词文本块
 */
@Composable
private fun LyricsBlock(
    lines: List<String>,
    fontSize: Float,
    lineHeight: Float,
    contentColor: Color,
    config: PosterConfig,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            (12f * config.fontSizeScale * (config.lineSpacingMultiplier - 1)).dp
        )
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                fontSize = (fontSize * config.fontSizeScale).sp,
                fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                    FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = (lineHeight * config.fontSizeScale * config.lineSpacingMultiplier).sp
            )
        }
    }
}