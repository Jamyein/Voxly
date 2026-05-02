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
import androidx.compose.material3.MaterialTheme
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
 * - 超大标题字号
 * - 小尺寸封面（可选）
 * - 装饰分隔线
 * - 角落装饰圆点
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
        else -> Color(0xFF1E1E2E) // 默认暗色，实际颜色由外层传入
    }

    val contentColor = if (config.enableAutoTextColor) {
        calculateContrastColor(backgroundColor)
    } else {
        config.customContentColor ?: Color.White
    }

    // 提取主色用于装饰圆点
    val accentColor = if (albumArt != null) {
        val extracted = ColorExtractor.extractColors(albumArt)
        Color(extracted.backgroundDominant).copy(alpha = 0.4f)
    } else {
        contentColor.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 封面（可选，小尺寸）
            if (albumArt != null) {
                AlbumArtWithShape(
                    bitmap = albumArt.asImageBitmap(),
                    shape = config.coverShape,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 标题（超大字号）
            Text(
                text = title,
                fontSize = (40f * config.fontSizeScale).sp,
                fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                    FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 3,
                lineHeight = (48f * config.fontSizeScale).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 艺术家
            Text(
                text = artist,
                fontSize = (18f * config.fontSizeScale).sp,
                fontWeight = FontWeight.Normal,
                color = contentColor.copy(alpha = 0.6f),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 装饰线
            HorizontalDividerLine(
                color = contentColor.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 歌词
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
                        fontSize = (24f * config.fontSizeScale).sp,
                        fontWeight = if (config.fontWeight == PosterFontWeight.BOLD) 
                            FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = (34f * config.fontSizeScale * config.lineSpacingMultiplier).sp
                    )
                }
            }

            // 底部留白 + 装饰圆点
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd
            ) {
                CornerDot(color = accentColor, size = 8.dp)
            }

            // 水印
            if (config.showWatermark) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = when (config.watermarkPosition) {
                        WatermarkPosition.START -> Alignment.CenterStart
                        WatermarkPosition.END -> Alignment.CenterEnd
                    }
                ) {
                    Text(
                        text = "Voxly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.4f)
                    )
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
