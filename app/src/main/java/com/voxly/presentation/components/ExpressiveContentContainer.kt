package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.presentation.theme.ContainerLevel

// 默认内容间距
private val DefaultContentPadding = 16.dp
// 列表项间距（更紧凑）
private val DefaultListItemPadding = 12.dp

/**
 * Material Design 3 Expressive Content Container Component
 * 
 * MD3 Expressive Content Container特点：
 * 1. 使用Surface Container颜色系统 - 主要内容区域使用surfaceContainer
 * 2. 支持ContainerLevel参数 - 控制背景色层级
 * 3. 支持滚动和非滚动版本
 * 4. 优化的内边距和圆角
 */

/**
 * Expressive Content Container - 基础内容容器
 */
@Composable
fun ExpressiveContentContainer(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Medium,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = getContainerColor(containerLevel),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "contentContainerColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        content = content
    )
}

/**
 * Expressive Scrollable Content Container - 可滚动内容容器
 */
@Composable
fun ExpressiveScrollableContentContainer(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Medium,
    contentPadding: Dp = DefaultContentPadding,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = getContainerColor(containerLevel),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrollableContainerColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        content = content
    )
}

/**
 * Expressive Section Container - 内容区块容器
 */
@Composable
fun ExpressiveSectionContainer(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Low,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = getContainerColor(containerLevel),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sectionContainerColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backgroundColor)
            .padding(DefaultContentPadding),
        content = content
    )
}

/**
 * Expressive Card-like Container - 卡片式容器
 */
@Composable
fun ExpressiveCardContainer(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Low,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = getContainerColor(containerLevel),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardContainerColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backgroundColor)
            .padding(DefaultContentPadding),
        content = content
    )
}

/**
 * Expressive Elevated Card Container - 带有更高层级的卡片容器
 */
@Composable
fun ExpressiveElevatedCardContainer(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (elevated) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevatedCardColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backgroundColor)
            .padding(DefaultContentPadding),
        content = content
    )
}

/**
 * Expressive List Item Container - 列表项容器
 */
@Composable
fun ExpressiveListItemContainer(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "listItemColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .padding(horizontal = DefaultContentPadding, vertical = DefaultListItemPadding),
        content = content
    )
}

/**
 * Expressive Highlight Container - 高亮容器
 */
@Composable
fun ExpressiveHighlightContainer(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "highlightColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backgroundColor)
            .padding(DefaultContentPadding),
        content = content
    )
}

/**
 * 获取Surface Container颜色
 */
@Composable
private fun getContainerColor(level: ContainerLevel): Color {
    return when (level) {
        ContainerLevel.Lowest -> MaterialTheme.colorScheme.surfaceContainerLowest
        ContainerLevel.Low -> MaterialTheme.colorScheme.surfaceContainerLow
        ContainerLevel.Medium -> MaterialTheme.colorScheme.surfaceContainer
        ContainerLevel.High -> MaterialTheme.colorScheme.surfaceContainerHigh
        ContainerLevel.Highest -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
}

/**
 * 便捷方法：创建带有边距的Expressive Content Container
 */
@Composable
fun ExpressivePaddedContentContainer(
    modifier: Modifier = Modifier,
    containerLevel: ContainerLevel = ContainerLevel.Medium,
    padding: Dp = DefaultContentPadding,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveContentContainer(
        modifier = modifier,
        containerLevel = containerLevel
    ) {
        Box(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}
