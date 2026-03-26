package com.voxly.presentation.components.scrollbar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive scrollbar composable.
 *
 * A complete scrollbar implementation with:
 * - Dynamic thumb sizing based on content ratio
 * - Preview bubble showing current position
 * - Tap-to-jump and drag-to-scroll interactions
 * - Smooth animations and haptic feedback
 * - Material Design 3 styling
 *
 * This component follows the architecture of the future official
 * ScrollIndicator API for easy migration when it becomes available.
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
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // Drag state
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetFromThumb by remember { mutableFloatStateOf(0f) }

    // Calculate dimensions
    val contentSize = state.contentSize
    val viewportSize = state.viewportSize
    val scrollOffset = state.scrollOffset

    // Skip if not ready
    if (contentSize <= 0 || viewportSize <= 0) {
        return
    }

    val scrollRange = (contentSize - viewportSize).coerceAtLeast(1)
    val scrollProgress = (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)

    // Calculate thumb dimensions
    val thumbHeightPx = if (contentSize > 0) {
        (viewportSize.toFloat() / contentSize * viewportSize)
            .coerceIn(
                with(density) { config.minThumbHeight.toPx() },
                viewportSize * 0.5f
            )
    } else {
        with(density) { config.thumbHeight.toPx() }
    }

    val maxThumbOffset = (viewportSize - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = scrollProgress * maxThumbOffset

    // Animations
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) config.thumbWidthDragging else config.thumbWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "thumb_width"
    )

    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) config.trackAlphaDragging else config.trackAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "track_alpha"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_alpha"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_scale"
    )

    // Get current item index for bubble
    val currentIndex = remember(scrollProgress, state) {
        when (state) {
            is LazyListScrollbarState -> state.getCurrentItemIndex()
            is LazyGridScrollbarState -> state.getCurrentItemIndex()
            else -> 0
        }
    }

    val bubbleText = remember(currentIndex) {
        bubbleFormatter?.invoke(currentIndex) ?: (currentIndex + 1).toString()
    }

    Box(
        modifier = modifier
            .width(config.touchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track background
        Box(
            modifier = Modifier
                .width(config.thumbWidth)
                .fillMaxHeight()
                .alpha(trackAlpha)
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(MaterialTheme.colorScheme.outline)
        )

        // Interactive area with tap and drag
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(config.touchAreaWidth)
                .pointerInput(state) {
                    detectTapGestures { offset ->
                        val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)

                        when (val s = state) {
                            is LazyListScrollbarState -> {
                                coroutineScope.launch {
                                    s.scrollToProgress(tapProgress)
                                }
                            }
                            is LazyGridScrollbarState -> {
                                coroutineScope.launch {
                                    s.scrollToProgress(tapProgress)
                                }
                            }
                        }

                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val thumbCenterY = thumbOffsetPx + thumbHeightPx / 2
                            dragOffsetFromThumb = offset.y - thumbCenterY
                            isDragging = true
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()

                            val targetCenterY = change.position.y - dragOffsetFromThumb
                            val targetThumbTop = (targetCenterY - thumbHeightPx / 2)
                                .coerceIn(0f, maxThumbOffset)

                            val newProgress = if (maxThumbOffset > 0) {
                                targetThumbTop / maxThumbOffset
                            } else 0f

                            when (val s = state) {
                                is LazyListScrollbarState -> {
                                    coroutineScope.launch {
                                        s.scrollToProgress(newProgress)
                                    }
                                }
                                is LazyGridScrollbarState -> {
                                    coroutineScope.launch {
                                        s.scrollToProgress(newProgress)
                                    }
                                }
                            }
                        }
                    )
                }
        )

        // Visual thumb
        val thumbHeight = with(density) { thumbHeightPx.toDp() }
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(
                    if (isDragging) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
        )

        // Preview bubble
        if (showBubble) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = -(config.touchAreaWidth + 8.dp).roundToPx(),
                            y = (thumbOffsetPx + thumbHeightPx / 2 - config.bubbleSize.toPx() / 2).toInt()
                        )
                    }
                    .alpha(bubbleAlpha)
                    .graphicsLayer {
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .size(config.bubbleSize)
                    .shadow(4.dp, CircleShape)
                    .clip(RoundedCornerShape(config.bubbleCornerRadius))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
