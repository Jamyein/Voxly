package com.voxly.presentation.components.scrollbar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive scrollbar composable with enhanced responsiveness and styling.
 *
 * Key improvements:
 * - **Snappy animations**: High stiffness for immediate visual feedback
 * - **Shadow effects**: Depth for better visual hierarchy
 * - **Smooth color transitions**: Gradient-like feel without actual gradients
 * - **Optimized touch response**: Direct manipulation feel
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
    
    // Auto-hide state - track previous scroll offset to detect scroll changes
    var previousScrollOffset by remember { mutableStateOf(state.scrollOffset) }
    var isVisible by remember { mutableStateOf(false) }
    
    // Monitor scroll changes to show scrollbar
    LaunchedEffect(state.scrollOffset) {
        val currentOffset = state.scrollOffset
        if (currentOffset != previousScrollOffset) {
            // Scroll position changed, show scrollbar
            isVisible = true
            previousScrollOffset = currentOffset
            
            // Hide after delay
            delay(config.hideDelayMillis)
            if (!isDragging) {
                isVisible = false
            }
        }
    }
    
    // Keep visible while dragging
    LaunchedEffect(isDragging) {
        if (isDragging) {
            isVisible = true
        } else if (state.scrollOffset == previousScrollOffset) {
            // Drag ended, start hide timer
            delay(config.hideDelayMillis)
            isVisible = false
        }
    }

    // Calculate dimensions - using derivedStateOf for proper recomposition
    val contentSize by remember { derivedStateOf { state.contentSize } }
    val viewportSize by remember { derivedStateOf { state.viewportSize } }
    val scrollOffset by remember { derivedStateOf { state.scrollOffset } }

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

    // Animations - using high stiffness for snappy response
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) config.thumbWidthDragging else config.thumbWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = config.thumbStiffness
        ),
        label = "thumb_width"
    )

    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) config.trackAlphaDragging else config.trackAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "track_alpha"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_alpha"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging && showBubble) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_scale"
    )
    
    // Overall scrollbar visibility animation
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    // Get current item index for bubble
    val currentIndex by remember(state) {
        derivedStateOf {
            when (state) {
                is LazyListScrollbarState -> state.getCurrentItemIndex()
                is LazyGridScrollbarState -> state.getCurrentItemIndex()
                else -> 0
            }
        }
    }

    val bubbleText by remember(currentIndex) {
        derivedStateOf {
            bubbleFormatter?.invoke(currentIndex) ?: (currentIndex + 1).toString()
        }
    }

    // Colors - using Material3 color scheme with enhanced contrast
    val thumbColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    }

    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
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
        // Track background with subtle styling
        Box(
            modifier = Modifier
                .width(config.thumbWidth)
                .fillMaxHeight()
                .alpha(trackAlpha)
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(trackColor)
        )

        // Interactive area with tap and drag
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(config.touchAreaWidth)
                .pointerInput(Unit) {
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
                .pointerInput(Unit) {
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

        // Visual thumb with shadow and enhanced styling
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
                    shape = RoundedCornerShape(config.thumbCornerRadius),
                    clip = false
                )
                .clip(RoundedCornerShape(config.thumbCornerRadius))
                .background(thumbColor)
        )

        // Preview bubble with enhanced styling
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
                    .shadow(
                        elevation = config.bubbleElevation,
                        shape = RoundedCornerShape(config.bubbleCornerRadius),
                        clip = false
                    )
                    .clip(RoundedCornerShape(config.bubbleCornerRadius))
                    .background(bubbleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = bubbleTextColor
                )
            }
        }
    }
}
