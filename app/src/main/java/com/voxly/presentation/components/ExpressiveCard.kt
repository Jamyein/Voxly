package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.presentation.theme.ContainerLevel

/**
 * Material Design 3 Expressive Card Component
 * 
 * MD3 Expressive Card特点：
 * 1. 使用Surface Container颜色系统 - 通过色调而非阴影区分层级
 * 2. 支持ContainerLevel参数 - 控制背景色层级
 * 3. 支持containerColor参数 - 直接指定背景色（优先于containerLevel）
 * 4. 物理动画 - 按压时有弹性反馈
 * 5. 使用extraLarge圆角 (28dp) - 更友好的视觉效果
 * 
 * @param containerLevel Surface Container层级
 * @param containerColor 自定义背景色（优先于containerLevel）
 * @param onClick 点击事件
 * @param enabled 是否启用点击
 * @param shape 形状（默认使用extraLarge圆角）
 * @param content 内容
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Low,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 物理动画 - 按压时的弹性缩放
    val scale by animateDpAsState(
        targetValue = if (isPressed) (-2).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    
    // 背景色动画 - 优先使用containerColor
    val backgroundColor by animateColorAsState(
        targetValue = containerColor ?: getContainerColor(containerLevel, isPressed),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardBackground"
    )
    
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(scale)
        .clip(shape)
        .background(backgroundColor)
    
    if (onClick != null) {
        Card(
            modifier = cardModifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * 获取Surface Container颜色
 */
@Composable
private fun getContainerColor(
    level: ContainerLevel,
    isPressed: Boolean
): Color {
    val baseColor = when (level) {
        ContainerLevel.Lowest -> MaterialTheme.colorScheme.surfaceContainerLowest
        ContainerLevel.Low -> MaterialTheme.colorScheme.surfaceContainerLow
        ContainerLevel.Medium -> MaterialTheme.colorScheme.surfaceContainer
        ContainerLevel.High -> MaterialTheme.colorScheme.surfaceContainerHigh
        ContainerLevel.Highest -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    
    // 按压时使用更高层级的颜色
    return if (isPressed) {
        when (level) {
            ContainerLevel.Lowest -> MaterialTheme.colorScheme.surfaceContainerLow
            ContainerLevel.Low -> MaterialTheme.colorScheme.surfaceContainer
            ContainerLevel.Medium -> MaterialTheme.colorScheme.surfaceContainerHigh
            ContainerLevel.High -> MaterialTheme.colorScheme.surfaceContainerHighest
            ContainerLevel.Highest -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
    } else {
        baseColor
    }
}

/**
 * 便捷方法：创建带有标题的Express Card
 */
@Composable
fun ExpressiveCardWithTitle(
    title: String,
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Low,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveCard(
        modifier = modifier,
        containerLevel = containerLevel,
        onClick = onClick
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
        content()
    }
}

/**
 * 便捷方法：创建可展开的Expressive Card
 */
@Composable
fun ExpressiveExpandableCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Low,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveCard(
        modifier = modifier,
        containerLevel = containerLevel,
        onClick = { onExpandedChange(!expanded) }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (expanded) {
            Box(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

/**
 * 兼容现有Card API的ExpressiveCard重载版本
 * 
 * 这个版本支持与标准Card相似的参数，便于从现有Card迁移到ExpressiveCard。
 * 
 * @param modifier 修饰符
 * @param shape 形状（默认使用medium圆角，与标准Card一致）
 * @param containerColor 背景色
 * @param onClick 点击事件
 * @param enabled 是否启用点击
 * @param content 内容
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveCard(
        modifier = modifier,
        containerLevel = ContainerLevel.Low,
        containerColor = containerColor,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        content = content
    )
}
