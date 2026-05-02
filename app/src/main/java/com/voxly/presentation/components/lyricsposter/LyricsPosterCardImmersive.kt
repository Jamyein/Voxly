@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全屏沉浸式歌词海报模板
 * 
 * 特点：
 * - 全屏模糊封面背景
 * - 上下双渐变暗角遮罩
 * - 歌词垂直居中（绝对主角）
 * - 标题艺术家合并为小字
 * - 引号装饰 + 圆点收束
 */
@Composable
fun LyricsPosterCardImmersive(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // 背景层：模糊封面
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .blur(50.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .background(Color.DarkGray)
            )
        }

        // 增强渐变：上下双暗角
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            // 顶部渐变（确保左上角标题可读）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // 底部渐变（确保歌词可读）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        // 内容层 - 填满整个空间
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 40.dp)
        ) {
            // 顶部：合并标题和艺术家为一行（小字，不抢夺焦点）
            Text(
                text = "$title · $artist",
                fontSize = (18f * config.fontSizeScale).sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                lineHeight = (24f * config.fontSizeScale).sp
            )

            // 歌词区域：垂直居中，占据主要空间
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val textAlign = when (config.lyricsAlignment) {
                    LyricsAlignment.START -> TextAlign.Start
                    LyricsAlignment.CENTER -> TextAlign.Center
                }

                val horizontalAlignment = when (config.lyricsAlignment) {
                    LyricsAlignment.START -> Alignment.Start
                    LyricsAlignment.CENTER -> Alignment.CenterHorizontally
                }

                // 开场引号
                QuoteMark(
                    color = Color.White,
                    size = 32.dp,
                    isOpening = true,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 歌词
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
                            fontSize = (28f * config.fontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (40f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                        )
                    }
                }
                
                // 结束引号
                QuoteMark(
                    color = Color.White,
                    size = 32.dp,
                    isOpening = false,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // 底部：装饰圆点 + 水印
            if (config.showWatermark) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AccentDotCluster(
                            color = Color.White,
                            dotSize = 4.dp,
                            spacing = 6.dp
                        )
                        Text(
                            text = "Voxly",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}