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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Material 3 Expressive scrollbar composable with enhanced responsiveness and styling.
 *
 * M3E features:
 * - Spring-based bouncy animations for thumb width and bubble scale
 * - VelocityTracker-based inertia on drag release
 * - Compose HapticFeedback for grab and per-item tick sensations
 * - Auto-hide with configurable delay
 * - 48dp touch target area following M3 accessibility guidelines
 * - CircleShape pill thumb for M3E aesthetic
 *
 * @param state The scrollbar state providing scroll information
 * @param modifier Modifier for the scrollbar container
 * @param config Scrollbar appearance configuration
 * @param showBubble Whether to show the preview bubble when dragging
 * @param bubbleFormatter Optional formatter for bubble text (receives item index)
 */
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
    var lastDragOffset by remember { mutableFloatStateOf(0f) }

    // Direct access to state properties - no redundant derivedStateOf wrapping
    // Note: These are already derivedStateOf in ScrollbarState implementations
    val contentSize = state.contentSize
    val scrollOffset = state.scrollOffset

    if (contentSize <= 0 || state.viewportSize <= 0) return

    // Inline viewportSize to reduce function call overhead
    val scrollRange = (contentSize - state.viewportSize).coerceAtLeast(1)
    
    // Scroll fraction - derivedStateOf handles dependency tracking internally
    // No need for remember keys as derivedStateOf manages its own memoization
    val scrollFraction by derivedStateOf {
        if (scrollRange > 0) (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f) else 0f
    }

    // rememberUpdatedState for pointerInput optimization - avoids lambda re-creation
    val scrollRangeState = rememberUpdatedState(scrollRange)
    val coroutineScopeState = rememberUpdatedState(coroutineScope)
    val hapticState = rememberUpdatedState(haptic)
    val stateState = rememberUpdatedState(state)
    val configState = rememberUpdatedState(config)
    
    // Real-time scroll progress based on actual scroll fraction
    val scrollProgress = scrollFraction

    // Smart auto-hide
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(config.hideDelayMillis)
            isVisible = false
        }
    }

    // Thumb dimensions (viewportSize inlined directly)
    val thumbHeightPx = if (contentSize > 0) {
        (state.viewportSize.toFloat() / contentSize * state.viewportSize)
            .coerceIn(
                with(density) { config.minThumbHeight.toPx() },
                state.viewportSize * 0.5f
            )
    } else {
        with(density) { config.thumbHeight.toPx() }
    }

    val maxThumbOffset = (state.viewportSize - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = if (isDragging) {
        dragY.coerceIn(0f, maxThumbOffset)
    } else {
        scrollProgress * maxThumbOffset
    }

    // --- M3E spring animations ---
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) config.thumbWidthDragging else config.thumbWidth,
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = config.thumbStiffness
        ),
        label = "thumb_width"
    )

    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) config.trackAlphaDragging else config.trackAlpha,
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "track_alpha"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_scale"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_alpha"
    )

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 1.0f,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "scrollbar_alpha"
    )

    // Current item index - during drag, calculate directly from dragY to avoid
    // recomputing derived state on every pointer move
    val currentItemIndex: Int = if (isDragging && maxThumbOffset > 0) {
        val fraction = (dragY / maxThumbOffset).coerceIn(0f, 1f)
        (fraction * (state.totalItemsCount - 1)).toInt().coerceIn(0, (state.totalItemsCount - 1).coerceAtLeast(0))
    } else {
        when (state) {
            is LazyListScrollbarState -> state.currentItemIndex
            is LazyGridScrollbarState -> state.currentItemIndex
            else -> 0
        }
    }

    val bubbleText: String = remember(currentItemIndex, bubbleFormatter) {
        bubbleFormatter?.invoke(currentItemIndex) ?: currentItemIndex.toString()
    }

    // M3E colors
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer
    val bubbleTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .width(config.touchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .alpha(scrollbarAlpha),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track background
        Box(
            modifier = Modifier
                .width(config.thumbWidth)
                .fillMaxHeight()
                .alpha(trackAlpha)
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(trackColor)
        )

        // Touch area: tap to jump + drag to scroll
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(config.touchAreaWidth)
                .pointerInput(Unit) {
                    // Use rememberUpdatedState values to avoid lambda re-creation
                    val coroutineScope by coroutineScopeState
                    val haptic by hapticState
                    val state by stateState

                    detectTapGestures { offset ->
                        if (!isDragging) {
                            val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)
                            when (val s = state) {
                                is LazyListScrollbarState -> {
                                    coroutineScope.launch { s.scrollToProgress(tapProgress) }
                                }
                                is LazyGridScrollbarState -> {
                                    coroutineScope.launch { s.scrollToProgress(tapProgress) }
                                }
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                .pointerInput(contentSize) {
                    // Use rememberUpdatedState values to avoid lambda re-creation
                    val scrollRange by scrollRangeState
                    val coroutineScope by coroutineScopeState
                    val haptic by hapticState
                    val state by stateState

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragY = (scrollFraction * maxThumbOffset).coerceIn(0f, maxThumbOffset)
                            lastDragOffset = scrollFraction * scrollRange
                            velocityTracker.resetTracking()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPointerInputChange(change)
                            dragY = (dragY + dragAmount.y).coerceIn(0f, maxThumbOffset)

                            val fraction = if (maxThumbOffset > 0) {
                                (dragY / maxThumbOffset).coerceIn(0f, 1f)
                            } else 0f
                            
                            val targetIndex = (fraction * (state.totalItemsCount - 1))
                                .toInt()
                                .coerceIn(0, (state.totalItemsCount - 1).coerceAtLeast(0))

                            if (targetIndex != lastHapticIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticIndex = targetIndex
                            }

                            val targetOffset = (fraction * scrollRange).toInt()
                            val delta = targetOffset - lastDragOffset
                            if (delta != 0f) {
                                when (val s = state) {
                                    is LazyListScrollbarState -> {
                                        coroutineScope.launch { s.scrollByDelta(delta) }
                                    }
                                    is LazyGridScrollbarState -> {
                                        coroutineScope.launch { s.scrollByDelta(delta) }
                                    }
                                }
                                lastDragOffset = targetOffset.toFloat()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            lastDragOffset = 0f
                            lastHapticIndex = -1
                            val velocity = velocityTracker.calculateVelocity().y
                            if (abs(velocity) > 500f) {
                                coroutineScope.launch {
                                    state.scrollByVelocity(velocity)
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            lastDragOffset = 0f
                            lastHapticIndex = -1
                        }
                    )
                }
        )

        // Thumb (pill shape for M3E)
        val thumbHeight = with(density) { thumbHeightPx.toDp() }
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .shadow(
                    elevation = if (isDragging) config.thumbElevation * 1.5f else config.thumbElevation,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(thumbColor)
        )

        // Preview bubble (M3E spring bounce)
        if (showBubble) {
            val bubbleSizePx = with(density) { config.bubbleSize.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -(config.touchAreaWidth + 8.dp).roundToPx(),
                            y = (thumbOffsetPx + thumbHeightPx / 2 - bubbleSizePx / 2).roundToInt()
                        )
                    }
                    .alpha(bubbleAlpha)
                    .size(config.bubbleSize * bubbleScale)
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
