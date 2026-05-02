@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxly.presentation.theme.ExpressiveShapes

/**
 * 极简文字主导歌词海报模板
 * 
 * 特点：
 * - 纯色背景（无模糊）
 * - 超大标题字号（绝对主角）
 * - 封面作为右上角点缀
 * - 粗装饰线分隔
 * - 引号装饰 + 圆点组合
 * - 不对称布局
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
    // 背景色：纯色，不使用模糊
    val backgroundColor = when {
        config.colorTheme == PosterColorTheme.CUSTOM -> config.customBackgroundColor ?: Color(0xFF1E1E2E)
        else -> Color(0xFF1E1E2E)
    }

    val contentColor = if (config.enableAutoTextColor) {
        calculateContrastColor(backgroundColor)
    } else {
        config.customContentColor ?: Color.White
    }

    // 提取主色用于装饰
    val accentColor = if (albumArt != null) {
        val extracted = ColorExtractor.extractColors(albumArt)
        Color(extracted.backgroundDominant)
    } else {
        contentColor
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 顶部行：标题（左）+ 封面（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 左侧：标题区域
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = (44f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 3,
                        lineHeight = (52f * config.fontSizeScale).sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = artist,
                        fontSize = (16f * config.fontSizeScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                
                // 右侧：封面（小尺寸，右上角点缀）
                if (albumArt != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                    AlbumArtWithShape(
                        bitmap = albumArt.asImageBitmap(),
                        shape = config.coverShape,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 装饰线（粗线，40%宽度，左对齐）
            AccentLine(
                color = contentColor.copy(alpha = 0.25f),
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxWidth(0.4f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 歌词区域
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
                color = contentColor,
                size = 28.dp,
                isOpening = true,
                modifier = Modifier.padding(bottom = 12.dp)
            )

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

            // 结束引号
            QuoteMark(
                color = contentColor,
                size = 28.dp,
                isOpening = false,
                modifier = Modifier.padding(top = 12.dp)
            )

            // 底部留白 + 右下角装饰
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentDotCluster(
                        color = accentColor,
                        dotSize = 4.dp,
                        spacing = 6.dp
                    )
                    
                    if (config.showWatermark) {
                        Text(
                            text = "Voxly",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 简化的专辑封面组件（仅支持基本形状）
 */
@Composable
private fun AlbumArtWithShape(
    bitmap: ImageBitmap?,
    shape: PosterShape,
    modifier: Modifier = Modifier
) {
    val shapeModifier = when (shape) {
        PosterShape.ROUNDED_16 -> Modifier.clip(RoundedCornerShape(16.dp))
        PosterShape.ROUNDED_28 -> Modifier.clip(ExpressiveShapes.ExtraLarge)
        PosterShape.CIRCLE -> Modifier.clip(CircleShape)
        PosterShape.SQUARE -> Modifier
        else -> Modifier.clip(RoundedCornerShape(16.dp))
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