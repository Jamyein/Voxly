package com.voxly.presentation.components.lyricsposter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 水平装饰线
 */
@Composable
fun HorizontalDividerLine(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 1.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth)
            .background(color)
    )
}

/**
 * 有厚度的装饰线
 * 支持自定义粗细和端点圆角
 */
@Composable
fun AccentLine(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .padding(start = startPadding, end = endPadding)
            .fillMaxWidth()
            .height(strokeWidth)
            .clip(RoundedCornerShape(strokeWidth / 2))
            .background(color)
    )
}

/**
 * 角落圆点装饰
 */
@Composable
fun CornerDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 圆点组合装饰
 * 3个小圆点水平排列，形成视觉节奏点
 */
@Composable
fun AccentDotCluster(
    color: Color,
    dotSize: Dp = 4.dp,
    spacing: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize + if (index == 1) 2.dp else 0.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f + index * 0.1f))
            )
        }
    }
}

/**
 * 大号引号装饰
 * 用于歌词区域，增加文学感和视觉锚点
 */
@Composable
fun QuoteMark(
    color: Color,
    size: Dp = 48.dp,
    isOpening: Boolean = true,
    modifier: Modifier = Modifier
) {
    val text = if (isOpening) "❝" else "❞"
    Text(
        text = text,
        fontSize = (size.value * 1.2).sp,
        fontWeight = FontWeight.Light,
        color = color.copy(alpha = 0.35f),
        lineHeight = size.value.sp,
        modifier = modifier
    )
}

/**
 * 装饰色块（矩形/圆角矩形）
 */
@Composable
fun DecorativeBlock(
    color: Color,
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 0.dp,
    gradientBrush: Brush? = null,
    modifier: Modifier = Modifier
) {
    val shape = if (cornerRadius > 0.dp) {
        RoundedCornerShape(cornerRadius)
    } else {
        androidx.compose.ui.graphics.RectangleShape
    }
    
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(gradientBrush ?: SolidColor(color))
    )
}

/**
 * 渐变遮罩（用于全屏沉浸式模板）
 * 从底部向上渐变变暗
 */
@Composable
fun VignetteOverlay(
    modifier: Modifier = Modifier,
    startAlpha: Float = 0.6f,
    endAlpha: Float = 0.0f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = startAlpha),
                        Color.Black.copy(alpha = endAlpha)
                    )
                )
            )
    )
}
