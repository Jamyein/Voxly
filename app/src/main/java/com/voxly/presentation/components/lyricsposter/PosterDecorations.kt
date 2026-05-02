package com.voxly.presentation.components.lyricsposter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * 装饰色块（矩形/圆角矩形）
 */
@Composable
fun DecorativeBlock(
    color: Color,
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 0.dp,
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
            .background(color)
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
