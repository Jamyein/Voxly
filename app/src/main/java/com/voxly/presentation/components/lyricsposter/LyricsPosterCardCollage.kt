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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 网格拼贴歌词海报模板
 * 
 * 设计：波普艺术拼贴
 * - 蓝紫渐变背景（#1E3C72 → #2A5298 → #7E8BA3）
 * - 几何色块作为背景装饰
 * - 封面 120dp 旋转 -5°
 * - 歌词分3个文本块，自由定位散布
 * - 移除截断和线性堆叠
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
    val backgroundColor = Color(0xFF1E3C72)
    val contentColor = Color.White

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3C72),
                        Color(0xFF2A5298),
                        Color(0xFF7E8BA3)
                    )
                )
            )
            .padding(32.dp)
    ) {
        // 背景几何装饰
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 右上角大圆
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = 80.dp, y = (-60).dp)
                    .background(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = CircleShape
                    )
            )
            
            // 左下角红色渐变圆
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(x = (-50).dp, y = 280.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE74C3C).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // 中间黄色小圆
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = 180.dp, y = 200.dp)
                    .background(
                        color = Color(0xFFF1C40F).copy(alpha = 0.12f),
                        shape = CircleShape
                    )
            )
        }

        // 内容层
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 区块1：前2行歌词（最大字号）
            if (lyrics.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lyrics[0],
                        fontSize = (28f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        lineHeight = (40f * config.fontSizeScale * config.lineSpacingMultiplier).sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (lyrics.size > 1) {
                        Text(
                            text = lyrics[1],
                            fontSize = (28f * config.fontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            lineHeight = (40f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 区块2：封面 + 第3-5行歌词并排
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 封面（旋转 -5°）
                if (albumArt != null) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer { rotationZ = -5f }
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer { rotationZ = -5f }
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF667EEA),
                                        Color(0xFF764BA2)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 右侧歌词
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    lyrics.drop(2).take(3).forEach { line ->
                        Text(
                            text = line,
                            fontSize = (20f * config.fontSizeScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor.copy(alpha = 0.9f),
                            lineHeight = (28f * config.fontSizeScale * config.lineSpacingMultiplier).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 区块3：剩余歌词
            if (lyrics.size > 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lyrics.drop(5).take(1).firstOrNull() ?: "",
                        fontSize = (18f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.7f),
                        lineHeight = (26f * config.fontSizeScale * config.lineSpacingMultiplier).sp,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部：歌曲信息
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 装饰线
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                
                Text(
                    text = "$title · $artist",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.6f)
                )
                
                if (config.showWatermark) {
                    Text(
                        text = "VOXLY · LYRICS POSTER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.35f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}