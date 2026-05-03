@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * 设计：电影感叙事
 * - 全屏模糊封面 + 径向光晕 + 暗角遮罩
 * - 歌词绝对主角居中显示（26px Bold）
 * - 引号装饰 + 电影字幕式底部信息
 * - 修复歌词截断问题
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
    Box(modifier = modifier.fillMaxSize()) {
        // 背景层：模糊封面
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A3A4A),
                                Color(0xFF0D1B2A),
                                Color(0xFF1B1B2F)
                            )
                        )
                    )
            )
        }

        // 径向光晕层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF667EEA).copy(alpha = 0.4f),
                            Color(0xFF764BA2).copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        // 暗角遮罩层
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // 底部渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
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

        // 内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 歌词区域：垂直居中，绝对主角
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 开场引号
                Text(
                    text = "❝",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                // 歌词
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    lyrics.take(3).forEach { line ->
                        Text(
                            text = line,
                            fontSize = (26f * config.fontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (36f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                        )
                    }
                }
                
                // 结束引号
                Text(
                    text = "❞",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(top = 20.dp, bottom = 40.dp)
                )
            }

            // 底部：电影字幕式信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 装饰线
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                )
                
                // 歌曲信息
                Text(
                    text = "$title · $artist",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                // 水印
                if (config.showWatermark) {
                    Text(
                        text = "VOXLY · LYRICS POSTER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}