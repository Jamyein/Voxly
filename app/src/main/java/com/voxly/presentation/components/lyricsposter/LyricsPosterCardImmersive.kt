@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
 * - 渐变暗角遮罩
 * - 歌词垂直居中
 * - 简约水印
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

        // 渐变遮罩层
        VignetteOverlay(
            modifier = Modifier.matchParentSize(),
            startAlpha = 0.65f,
            endAlpha = 0.1f
        )

        // 内容层
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 48.dp)
        ) {
            // 顶部：标题和艺术家
            Column {
                Text(
                    text = title,
                    fontSize = (22f * config.fontSizeScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    lineHeight = (28f * config.fontSizeScale).sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = artist,
                    fontSize = (16f * config.fontSizeScale).sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            // 歌词区域：垂直居中
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = horizontalAlignment,
                    verticalArrangement = Arrangement.spacedBy(
                        (20f * config.fontSizeScale * (config.lineSpacingMultiplier - 1)).dp
                    )
                ) {
                    lyrics.forEach { line ->
                        Text(
                            text = line,
                            fontSize = (26f * config.fontSizeScale).sp,
                            fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                                FontWeight.Bold else FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (36f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                        )
                    }
                }
            }

            // 底部：水印
            if (config.showWatermark) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = "Voxly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
