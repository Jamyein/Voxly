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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 极简文字主导歌词海报模板
 * 
 * 设计：文学杂志风
 * - 暖白背景（#FAF8F5）+ 深蓝灰色文字（#2C3E50）
 * - 封面 56dp 右上角印章
 * - 标题 36sp Bold 带红色下划线（#C0392B）
 * - 左侧竖线引用块，歌词斜体 + 大行高
 * - 底部水印 + 圆点装饰
 * - 修复 softWrap 导致的渲染 bug
 */
@Composable
fun LyricsPosterCardTypography(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(0xFFFAF8F5)
    val accentColor = Color(0xFFC0392B)
    val secondaryTextColor = Color(0xFF7F8C8D)
    val lyricColor = Color(0xFF34495E)

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(40.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部：标题 + 封面印章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 标题
                    Text(
                        text = title,
                        fontSize = (36f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A2E),
                        maxLines = 2,
                        lineHeight = (44f * config.fontSizeScale).sp
                    )
                    
                    // 红色下划线
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .width(80.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    
                    // 艺术家
                    Text(
                        text = artist,
                        fontSize = (16f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                
                // 封面印章（右上角）
                if (albumArt != null) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 歌词区域（左侧竖线引用块）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 左侧竖线
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFECF0F1))
                )
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // 歌词列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(
                        (28f * config.fontSizeScale * config.lineSpacingMultiplier).dp
                    )
                ) {
                    lyrics.take(4).forEach { line ->
                        Text(
                            text = line,
                            fontSize = (18f * config.fontSizeScale).sp,
                            fontWeight = FontWeight.Medium,
                            color = lyricColor,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = (36f * config.fontSizeScale * config.lineSpacingMultiplier).sp,
                            softWrap = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部：品牌 + 圆点装饰
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 水印
                if (config.showWatermark) {
                    Text(
                        text = "Voxly · Lyrics Poster",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFBDC3C7),
                        letterSpacing = 0.5.sp
                    )
                }
                
                // 圆点装饰
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val dotColor = if (index == 1) accentColor else Color(0xFFE74C3C)
                        val alpha = if (index == 1) 0.7f else 0.4f
                        Box(
                            modifier = Modifier
                                .size(if (index == 1) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}