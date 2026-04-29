package com.voxly.presentation.components.scrollbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// -- Constants to replace magic numbers --

/** Threshold (in px) at which settling is considered complete. */
private const val SETTLING_THRESHOLD_PX = 0.5f

/** Fraction of velocity applied during inertia scroll. */
private const val VELOCITY_DIVISOR = 5f

/** Spring damping ratio for thumb animations. */
private const val THUMB_DAMPING_RATIO = 1.0f

/** Elevation multiplier when thumb is being dragged. */
private const val THUMB_DRAG_ELEVATION_MULTIPLIER = 1.5f

// ------------------------------------------------------------------------------
// Public API
// ------------------------------------------------------------------------------

/**
 * Material 3 Expressive scrollbar for LazyColumn / LazyVerticalGrid.
 *
 * @param state Read-only scroll state from the list/grid.
 * @param holder Mutable scrollbar state (drag, visibility, settling). Hoisted so callers can observe.
 * @param modifier Modifier applied to the root layout.
 * @param config Visual / behavioural configuration.
 * @param showBubble Whether to show the preview bubble while dragging.
 * @param bubbleFormatter Optional formatter for bubble text. Receives the current item index.
 * @param trackModifier Modifier applied to the scrollbar track.
 * @param thumbModifier Modifier applied to the scrollbar thumb.
 * @param bubbleModifier Modifier applied to the preview bubble.
 */
@Composable
fun M3EScrollbar(
    state: ScrollbarState,
    holder: ScrollbarStateHolder = rememberScrollbarStateHolder(),
    modifier: Modifier = Modifier,
    config: ScrollbarConfig = ScrollbarConfig.Default,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    trackModifier: Modifier = Modifier,
    thumbModifier: Modifier = Modifier,
    bubbleModifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }

    val totalItemsCount = state.totalItemsCount

    // Early return: nothing to scroll.
    if (!state.isScrollable) return

    // -- Thumb height (dynamic based on visible ratio) --
    val thumbHeightPx = with(density) {
        val minHeight = config.minThumbHeight.toPx()
        if (holder.containerHeight <= 0f) return@with minHeight
        val visibleCount = (state.lastVisibleIndex - state.firstVisibleIndex + 1).coerceAtLeast(1)
        val dynamicHeight = (holder.containerHeight * visibleCount / totalItemsCount).coerceAtLeast(minHeight)
        dynamicHeight.coerceIn(minHeight, holder.containerHeight)
    }

    // -- Auto-hide logic --
    LaunchedEffect(state.isScrollInProgress, holder.isDragging) {
        if (state.isScrollInProgress || holder.isDragging) {
            holder.updateVisibility(true)
        } else {
            delay(config.hideDelayMillis)
            holder.updateVisibility(false)
        }
    }

    // -- Track alpha animation --
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (holder.isVisible || holder.isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = THUMB_DAMPING_RATIO,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "scrollbar_alpha"
    )

    // -- Container height is written during draw phase --
    Box(
        modifier = modifier
            .width(config.touchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .alpha(scrollbarAlpha)
            .onGloballyPositioned { coordinates ->
                holder.containerHeight = coordinates.size.height.toFloat()
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        val containerHeight = holder.containerHeight
        if (containerHeight <= 0f) return@Box

        val maxOffset = (containerHeight - thumbHeightPx).coerceAtLeast(0f)

        // -- Target thumb offset (performance-critical: derivedStateOf) --
        val targetThumbOffset by remember(maxOffset) {
            derivedStateOf {
                when {
                    holder.isDragging -> holder.dragY.coerceIn(0f, maxOffset)
                    holder.isSettling && !state.isScrollInProgress -> holder.dragY.coerceIn(0f, maxOffset)
                    else -> state.normalizedThumbOffset * maxOffset
                }
            }
        }

        // -- Animated thumb offset --
        val displayThumbOffset by animateFloatAsState(
            targetValue = targetThumbOffset,
            animationSpec = spring(
                dampingRatio = THUMB_DAMPING_RATIO,
                stiffness = config.thumbOffsetStiffness
            ),
            label = "thumb_offset"
        )

        // -- Settling completion check --
        if (holder.isSettling) {
            val listPos = state.normalizedThumbOffset * maxOffset
            if (abs(listPos - holder.dragY) < SETTLING_THRESHOLD_PX) {
                holder.markSettled()
            }
        }

        // -- Thumb width animation --
        val thumbWidth by animateDpAsState(
            targetValue = if (holder.isDragging) config.thumbWidthDragging else config.thumbWidth,
            animationSpec = spring(
                dampingRatio = THUMB_DAMPING_RATIO,
                stiffness = config.thumbStiffness
            ),
            label = "thumb_width"
        )

        // -- Bubble visibility --
        val bubbleScale by animateFloatAsState(
            targetValue = if (holder.isDragging && showBubble) 1f else 0f,
            animationSpec = spring(
                dampingRatio = THUMB_DAMPING_RATIO,
                stiffness = config.visualFeedbackStiffness
            ),
            label = "bubble_scale"
        )
        val bubbleAlpha by animateFloatAsState(
            targetValue = if (holder.isDragging && showBubble) 1f else 0f,
            animationSpec = spring(
                dampingRatio = THUMB_DAMPING_RATIO,
                stiffness = config.visualFeedbackStiffness
            ),
            label = "bubble_alpha"
        )

        // -- Bubble text (split: static vs drag) --
        val bubbleText = if (bubbleFormatter != null) {
            remember(state.firstVisibleIndex) {
                bubbleFormatter.invoke(state.firstVisibleIndex)
            }
        } else if (holder.isDragging && totalItemsCount > 0) {
            "${state.firstVisibleIndex + 1}-${state.lastVisibleIndex + 1} of $totalItemsCount"
        } else {
            state.firstVisibleIndex.toString()
        }

        // -- Derived colors --
        val thumbColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val bubbleColor = MaterialTheme.colorScheme.primaryContainer
        val bubbleTextColor = MaterialTheme.colorScheme.onPrimaryContainer

        // -- Track --
        ScrollbarTrack(
            config = config,
            isDragging = holder.isDragging,
            modifier = trackModifier
        )

        // -- Gesture area (tap + drag) --
        ScrollbarGestureArea(
            holder = holder,
            state = state,
            config = config,
            thumbHeightPx = thumbHeightPx,
            velocityTracker = velocityTracker,
            haptic = haptic,
            coroutineScope = coroutineScope
        )

        // -- Thumb --
        ScrollbarThumb(
            offset = displayThumbOffset,
            width = thumbWidth,
            height = with(density) { thumbHeightPx.toDp() },
            isDragging = holder.isDragging,
            color = thumbColor,
            config = config,
            modifier = thumbModifier
        )

        // -- Bubble --
        if (showBubble && bubbleAlpha > 0f) {
            ScrollbarBubble(
                text = bubbleText,
                offset = displayThumbOffset,
                thumbHeight = thumbHeightPx,
                config = config,
                scale = bubbleScale,
                alpha = bubbleAlpha,
                bubbleColor = bubbleColor,
                textColor = bubbleTextColor,
                modifier = bubbleModifier
            )
        }
    }
}

// ------------------------------------------------------------------------------
// Sub-composables (SRP)
// ------------------------------------------------------------------------------

@Composable
private fun ScrollbarTrack(
    config: ScrollbarConfig,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val trackAlpha = if (isDragging) config.trackAlphaDragging else config.trackAlpha
    Box(
        modifier = modifier
            .width(config.thumbWidth)
            .fillMaxHeight()
            .alpha(trackAlpha)
            .clip(RoundedCornerShape(config.thumbCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun BoxScope.ScrollbarThumb(
    offset: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isDragging: Boolean,
    color: androidx.compose.ui.graphics.Color,
    config: ScrollbarConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .align(Alignment.TopEnd)
            .graphicsLayer {
                translationY = offset
            }
            .shadow(
                elevation = if (isDragging) config.thumbElevation * THUMB_DRAG_ELEVATION_MULTIPLIER else config.thumbElevation,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun BoxScope.ScrollbarBubble(
    text: String,
    offset: Float,
    thumbHeight: Float,
    config: ScrollbarConfig,
    scale: Float,
    alpha: Float,
    bubbleColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bubbleSizePx = with(density) { config.bubbleSize.toPx() }

    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .offset {
                IntOffset(
                    x = -(config.touchAreaWidth + config.bubbleHorizontalOffset).roundToPx(),
                    y = (offset + thumbHeight / 2 - bubbleSizePx / 2).roundToInt()
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .size(config.bubbleSize)
            .shadow(elevation = config.bubbleElevation, shape = CircleShape)
            .clip(CircleShape)
            .background(bubbleColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = textColor
        )
    }
}

@Composable
private fun ScrollbarGestureArea(
    holder: ScrollbarStateHolder,
    state: ScrollbarState,
    config: ScrollbarConfig,
    thumbHeightPx: Float,
    velocityTracker: VelocityTracker,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val totalItemsCount = state.totalItemsCount
    val containerHeight = holder.containerHeight
    if (containerHeight <= 0f) return

    val maxOffset = (containerHeight - thumbHeightPx).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(config.touchAreaWidth)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (totalItemsCount <= 1) return@detectTapGestures
                    val tapFraction = (offset.y / size.height).coerceIn(0f, 1f)
                    val targetIndex = (tapFraction * (totalItemsCount - 1)).toInt().coerceIn(0, totalItemsCount - 1)
                    coroutineScope.launch {
                        state.animateScrollToItem(targetIndex)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val currentNormalized = state.normalizedThumbOffset
                        val currentThumbY = currentNormalized * maxOffset
                        val touchOffsetInThumb = offset.y - currentThumbY
                        val dragY = (offset.y - touchOffsetInThumb).coerceIn(0f, maxOffset)

                        holder.onDragStart(
                            y = dragY,
                            atStart = state.firstVisibleIndex == 0,
                            atEnd = state.lastVisibleIndex + 1 >= totalItemsCount
                        )

                        velocityTracker.resetTracking()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPointerInputChange(change)
                        val newDragY = (holder.dragY + dragAmount.y).coerceIn(0f, maxOffset)
                        holder.onDrag(newDragY)

                        if (totalItemsCount > 1) {
                            val fraction = (newDragY / maxOffset).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (totalItemsCount - 1)).toInt().coerceIn(0, totalItemsCount - 1)

                            if (targetIndex != holder.lastHapticIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                holder.onHapticTick(targetIndex)
                            }

                            // Boundary haptics
                            when {
                                newDragY <= 0f && !holder.wasAtStart -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    holder.updateBoundaries(atStart = true, atEnd = false)
                                }
                                newDragY >= maxOffset && !holder.wasAtEnd -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    holder.updateBoundaries(atStart = false, atEnd = true)
                                }
                                newDragY > 0f && newDragY < maxOffset -> {
                                    holder.updateBoundaries(atStart = false, atEnd = false)
                                }
                            }

                            coroutineScope.launch {
                                state.scrollToItem(targetIndex)
                            }
                        }
                    },
                    onDragEnd = {
                        holder.onDragEnd()
                        val velocity = velocityTracker.calculateVelocity().y
                        if (abs(velocity) > config.velocityThreshold) {
                            coroutineScope.launch {
                                state.scrollByVelocity(velocity)
                            }
                        }
                        velocityTracker.resetTracking()
                    },
                    onDragCancel = {
                        holder.onDragCancel()
                        velocityTracker.resetTracking()
                    }
                )
            }
    )
}
