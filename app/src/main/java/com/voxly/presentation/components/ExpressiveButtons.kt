package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Button Emphasis Levels (MD3 Expressive)
 * 
 * - High: Primary accent - 使用primary颜色，最突出
 * - Medium: Secondary - 使用secondaryContainer
 * - Low: Tertiary - 最小强调，使用text样式
 */
enum class ButtonEmphasis {
    High,   // Primary - Filled button with primary color
    Medium, // Secondary - Filled tonal button
    Low     // Tertiary - Text button
}

/**
 * Material Design 3 Expressive Button Components
 * 
 * MD3 Expressive Button特点：
 * 1. 使用extraLarge圆角 (28dp) - 更友好的视觉效果
 * 2. 支持ButtonEmphasis级别 - 控制强调程度
 * 3. 物理动画 - 按压时有弹性反馈
 * 4. 加载状态支持 - 显示进度指示器
 */

/**
 * Expressive Primary Button - 高强调按钮
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            content()
        }
    }
}

/**
 * Expressive Secondary Button - 中等强调按钮
 */
@Composable
fun ExpressiveSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            content()
        }
    }
}

/**
 * Expressive Text Button - 低强调按钮
 */
@Composable
fun ExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            content()
        }
    }
}

/**
 * Expressive Outlined Button
 */
@Composable
fun ExpressiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            content()
        }
    }
}

/**
 * Expressive Icon Button - 图标按钮
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String?,
    emphasis: ButtonEmphasis = ButtonEmphasis.Medium
) {
    val containerColor by animateColorAsState(
        targetValue = when (emphasis) {
            ButtonEmphasis.High -> MaterialTheme.colorScheme.primaryContainer
            ButtonEmphasis.Medium -> MaterialTheme.colorScheme.surfaceContainerHigh
            ButtonEmphasis.Low -> Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconButtonColor"
    )
    
    val contentColor = when (emphasis) {
        ButtonEmphasis.High -> MaterialTheme.colorScheme.onPrimaryContainer
        ButtonEmphasis.Medium -> MaterialTheme.colorScheme.onSurfaceVariant
        ButtonEmphasis.Low -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val size by animateDpAsState(
        targetValue = when (emphasis) {
            ButtonEmphasis.High -> 56.dp
            ButtonEmphasis.Medium -> 48.dp
            ButtonEmphasis.Low -> 40.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconButtonSize"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

/**
 * Expressive FAB - 悬浮操作按钮
 */
@Composable
fun ExpressiveFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String?,
    expanded: Boolean = true
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

/**
 * 通用Expressive按钮 - 根据emphasis级别选择样式
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    emphasis: ButtonEmphasis,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    when (emphasis) {
        ButtonEmphasis.High -> ExpressiveButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            icon = icon,
            content = content
        )
        ButtonEmphasis.Medium -> ExpressiveSecondaryButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            icon = icon,
            content = content
        )
        ButtonEmphasis.Low -> ExpressiveTextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            icon = icon,
            content = content
        )
    }
}
