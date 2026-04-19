package com.voxly.presentation.components.scrollbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun M3EScrollbar(
    state: ScrollbarState,
    modifier: Modifier = Modifier,
    config: ScrollbarConfig = ScrollbarConfig.Default,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }

    var isDragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var lastHapticIndex by remember { mutableIntStateOf(-1) }

    val totalItemsCount = state.totalItemsCount
    val currentItemIndex = state.currentItemIndex

    if (totalItemsCount <= 0) return

    val coroutineScopeState = rememberUpdatedState(coroutineScope)
    val hapticState = rememberUpdatedState(haptic)
    val stateState = rememberUpdatedState(state)
    val configState = rememberUpdatedState(config)

    val thumbHeightPx = with(density) { config.thumbHeight.toPx() }

    val normalizedThumbOffset by derivedStateOf {
        if (totalItemsCount <= 1) 0f
        else currentItemIndex.toFloat() / (totalItemsCount - 1)
    }

    val thumbOffsetPx by derivedStateOf {
        if (containerHeight <= 0f) 0f
        else normalizedThumbOffset * (containerHeight - thumbHeightPx)
    }

    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(config.hideDelayMillis)
            isVisible = false
        }
    }

    val displayThumbOffset = if (isDragging) {
        dragY.coerceIn(0f, (containerHeight - thumbHeightPx).coerceAtLeast(0f))
    } else {
        thumbOffsetPx
    }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) config.thumbWidthDragging else config.thumbWidth,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = config.thumbStiffness),
        label = "thumb_width"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = config.visualFeedbackStiffness),
        label = "bubble_scale"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = config.visualFeedbackStiffness),
        label = "bubble_alpha"
    )

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = config.visualFeedbackStiffness),
        label = "scrollbar_alpha"
    )

    val bubbleText: String = remember(currentItemIndex, bubbleFormatter) {
        bubbleFormatter?.invoke(currentItemIndex) ?: currentItemIndex.toString()
    }

    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer
    val bubbleTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .width(config.touchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .alpha(scrollbarAlpha)
            .drawBehind {
                containerHeight = size.height
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(config.thumbWidth)
                .fillMaxHeight()
                .alpha(0.3f)
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(trackColor)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(config.touchAreaWidth)
                .pointerInput(Unit) {
                    val haptic by hapticState
                    val state by stateState
                    val scope by coroutineScopeState

                    detectTapGestures { offset ->
                        if (totalItemsCount <= 1) return@detectTapGestures
                        val tapFraction = (offset.y / size.height).coerceIn(0f, 1f)
                        val targetIndex = (tapFraction * (totalItemsCount - 1)).toInt().coerceIn(0, totalItemsCount - 1)
                        when (val s = state) {
                            is LazyListScrollbarState -> scope.launch { s.animateScrollToItem(targetIndex) }
                            is LazyGridScrollbarState -> scope.launch { s.animateScrollToItem(targetIndex) }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                .pointerInput(Unit) {
                    val haptic by hapticState
                    val state by stateState
                    val scope by coroutineScopeState

                    detectDragGestures(
                        onDragStart = { offset ->
                            val touchOffsetInThumb = offset.y - displayThumbOffset
                            isDragging = true
                            dragY = (offset.y - touchOffsetInThumb).coerceIn(0f, (containerHeight - thumbHeightPx).coerceAtLeast(0f))
                            velocityTracker.resetTracking()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPointerInputChange(change)
                            val maxOffset = (containerHeight - thumbHeightPx).coerceAtLeast(0f)
                            dragY = (dragY + dragAmount.y).coerceIn(0f, maxOffset)

                            if (totalItemsCount > 1) {
                                val fraction = (dragY / maxOffset).coerceIn(0f, 1f)
                                val targetIndex = (fraction * (totalItemsCount - 1)).toInt().coerceIn(0, totalItemsCount - 1)

                                if (targetIndex != lastHapticIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticIndex = targetIndex
                                }

                                when (val s = state) {
                                    is LazyListScrollbarState -> scope.launch { s.scrollToItem(targetIndex) }
                                    is LazyGridScrollbarState -> scope.launch { s.scrollToItem(targetIndex) }
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            lastHapticIndex = -1
                            val velocity = velocityTracker.calculateVelocity().y
                            if (abs(velocity) > configState.value.velocityThreshold) {
                                scope.launch { state.scrollByVelocity(velocity) }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            lastHapticIndex = -1
                        }
                    )
                }
        )

        val thumbHeight = with(density) { thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationY = displayThumbOffset
                }
                .shadow(
                    elevation = if (isDragging) config.thumbElevation * 1.5f else config.thumbElevation,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(thumbColor)
        )

        if (showBubble && bubbleAlpha > 0f) {
            val bubbleSizePx = with(density) { config.bubbleSize.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -(config.touchAreaWidth + 8.dp).roundToPx(),
                            y = (displayThumbOffset + thumbHeightPx / 2 - bubbleSizePx / 2).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                        alpha = bubbleAlpha
                    }
                    .size(config.bubbleSize)
                    .shadow(elevation = config.bubbleElevation, shape = CircleShape)
                    .clip(CircleShape)
                    .background(bubbleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = bubbleTextColor
                )
            }
        }
    }
}
