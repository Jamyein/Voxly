package com.voxly.presentation.components

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scrollbar configuration for consistent theming across all scrollbar types.
 */
object ScrollbarDefaults {
    /** Width of the scrollbar thumb in its default state */
    val ThumbWidth = 6.dp

    /** Width of the scrollbar thumb when being dragged */
    val ThumbWidthDragging = 12.dp

    /** Height of the scrollbar thumb */
    val ThumbHeight = 48.dp

    /** Minimum thumb height to ensure it's always tappable */
    val MinThumbHeight = 32.dp

    /** Width of the touch area for easier interaction */
    val TouchAreaWidth = 24.dp

    /** Corner radius for rounded scrollbar thumb */
    val ThumbCornerRadius = 3.dp

    /** Size of the preview bubble */
    val BubbleSize = 56.dp

    /** Corner radius of the preview bubble */
    val BubbleCornerRadius = 16.dp

    /** Default delay before hiding scrollbar after scroll stops */
    const val DefaultHideDelayMillis = 1200L

    /** Alpha value for the scrollbar track */
    const val TrackAlpha = 0.15f

    /** Alpha value for the scrollbar track when dragging */
    const val TrackAlphaDragging = 0.35f
}

/**
 * Enhanced Material 3 scrollbar for LazyColumn with preview bubble and improved drag support.
 *
 * Features:
 * - Auto-hide with configurable delay
 * - **Preview bubble**: Shows current item index when dragging thumb
 * - **Enhanced visuals**: Thumb expands when dragging, track becomes more visible
 * - **Spring animations**: Smooth appearance/disappearance
 * - **Tap support**: Tap on track to jump to that position
 * - **Precise drag support**: Thumb follows finger position exactly with offset correction
 *
 * @param listState The LazyListState to track scroll position
 * @param modifier Modifier for the scrollbar container
 * @param showBubble Whether to show the preview bubble when dragging
 * @param bubbleFormatter Optional formatter for bubble text (default: shows item index)
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops
 */
@Composable
fun M3EScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    hideDelayMillis: Long = ScrollbarDefaults.DefaultHideDelayMillis
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Track offset from touch point to thumb center for precise drag following
    var dragOffsetFromThumb by remember { mutableFloatStateOf(0f) }

    // Monitor scroll state to show/hide scrollbar
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            hideJob?.cancel()
            isScrollbarVisible = true
        } else if (!isDragging) {
            hideJob = coroutineScope.launch {
                delay(hideDelayMillis)
                isScrollbarVisible = false
            }
        }
    }

    // Keep visible while dragging
    LaunchedEffect(isDragging) {
        if (isDragging) {
            hideJob?.cancel()
            isScrollbarVisible = true
        }
    }

    // Calculate scrollbar progress (0.0 to 1.0)
    val scrollProgress by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f else {
                val firstVisible = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                val avgItemSize = if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    listState.layoutInfo.visibleItemsInfo.first().size.toFloat()
                } else 100f

                ((firstVisible + offset / avgItemSize.coerceAtLeast(1f)) / (totalItems - 1))
                    .coerceIn(0f, 1f)
            }
        }
    }

    // Get current item index for bubble display
    val currentItemIndex by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 0) 0 else {
                (scrollProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
            }
        }
    }

    val bubbleText = remember(currentItemIndex) {
        bubbleFormatter?.invoke(currentItemIndex) ?: (currentItemIndex + 1).toString()
    }

    // Use rememberUpdatedState to ensure lambdas always access latest values
    val currentScrollProgress = rememberUpdatedState(scrollProgress)
    val currentListState = rememberUpdatedState(listState)

    // Animations
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) ScrollbarDefaults.TrackAlphaDragging else ScrollbarDefaults.TrackAlpha,
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

    Box(
        modifier = modifier
            .width(ScrollbarDefaults.TouchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track height for calculations
        var trackHeightPx by remember { mutableFloatStateOf(0f) }

        // Calculate thumb metrics
        val thumbHeightPx = with(density) { ScrollbarDefaults.ThumbHeight.toPx() }
        val minThumbHeightPx = with(density) { ScrollbarDefaults.MinThumbHeight.toPx() }

        // Dynamic thumb height based on content (clamp to min/max)
        val totalItems = listState.layoutInfo.totalItemsCount
        val dynamicThumbHeightPx = if (totalItems > 0) {
            val viewportRatio = listState.layoutInfo.visibleItemsInfo.size.toFloat() / totalItems.coerceAtLeast(1)
            (trackHeightPx * viewportRatio).coerceIn(minThumbHeightPx, trackHeightPx * 0.5f)
        } else thumbHeightPx

        val maxThumbOffset = (trackHeightPx - dynamicThumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = scrollProgress * maxThumbOffset
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }
        val thumbHeight = with(density) { dynamicThumbHeightPx.toDp() }

        // Animated thumb width
        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) ScrollbarDefaults.ThumbWidthDragging else ScrollbarDefaults.ThumbWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "thumb_width"
        )

        // Store values that need to be accessed in pointerInput lambda
        val currentMaxThumbOffset = rememberUpdatedState(maxThumbOffset)
        val currentThumbHeightPx = rememberUpdatedState(dynamicThumbHeightPx)

        // Scrollbar track (background)
        Box(
            modifier = Modifier
                .width(ScrollbarDefaults.ThumbWidth)
                .fillMaxHeight()
                .alpha(scrollbarAlpha * trackAlpha)
                .clip(RoundedCornerShape(ScrollbarDefaults.ThumbCornerRadius))
                .background(MaterialTheme.colorScheme.outline)
        )

        // Full height interactive area with precise drag handling
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(ScrollbarDefaults.TouchAreaWidth)
                .onSizeChanged { size ->
                    trackHeightPx = size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Tap on track to jump to that position
                        val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)
                        val totalItemsCount = currentListState.value.layoutInfo.totalItemsCount
                        val targetIndex = (tapProgress * (totalItemsCount - 1)).toInt()
                            .coerceIn(0, totalItemsCount - 1)
                        coroutineScope.launch {
                            currentListState.value.scrollToItem(targetIndex)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // Calculate the offset from touch point to thumb center
                            // This ensures thumb follows finger precisely
                            val thumbCenterY = thumbOffsetPx + dynamicThumbHeightPx / 2
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
                            if (!isDragging) return@detectVerticalDragGestures
                            change.consume()

                            val updatedMaxOffset = currentMaxThumbOffset.value
                            val updatedThumbHeight = currentThumbHeightPx.value

                            // Calculate target position based on finger position minus the initial offset
                            // This makes the thumb follow the finger exactly
                            val targetCenterY = change.position.y - dragOffsetFromThumb
                            val targetThumbTop = (targetCenterY - updatedThumbHeight / 2)
                                .coerceIn(0f, updatedMaxOffset)

                            // Convert thumb position to scroll progress
                            val newProgress = if (updatedMaxOffset > 0) {
                                targetThumbTop / updatedMaxOffset
                            } else 0f

                            val totalItemsCount = currentListState.value.layoutInfo.totalItemsCount
                            val targetIndex = (newProgress * (totalItemsCount - 1)).toInt()
                                .coerceIn(0, totalItemsCount - 1)

                            coroutineScope.launch {
                                currentListState.value.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        )

        // Visual thumb with enhanced styling
        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .alpha(scrollbarAlpha)
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .clip(RoundedCornerShape(ScrollbarDefaults.ThumbCornerRadius))
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
                            x = -(ScrollbarDefaults.TouchAreaWidth + 8.dp).roundToPx(),
                            y = (thumbOffsetPx + dynamicThumbHeightPx / 2 - ScrollbarDefaults.BubbleSize.toPx() / 2).toInt()
                        )
                    }
                    .alpha(bubbleAlpha)
                    .graphicsLayer {
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .size(ScrollbarDefaults.BubbleSize)
                    .shadow(4.dp, CircleShape)
                    .clip(RoundedCornerShape(ScrollbarDefaults.BubbleCornerRadius))
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

/**
 * Enhanced Material 3 scrollbar for LazyVerticalGrid with preview bubble and improved drag support.
 *
 * @param gridState The LazyGridState to track scroll position
 * @param modifier Modifier for the scrollbar container
 * @param showBubble Whether to show the preview bubble when dragging
 * @param bubbleFormatter Optional formatter for bubble text
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops
 */
@Composable
fun M3EGridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    hideDelayMillis: Long = ScrollbarDefaults.DefaultHideDelayMillis
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Track offset from touch point to thumb center for precise drag following
    var dragOffsetFromThumb by remember { mutableFloatStateOf(0f) }

    // Monitor scroll state to show/hide scrollbar
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            hideJob?.cancel()
            isScrollbarVisible = true
        } else if (!isDragging) {
            hideJob = coroutineScope.launch {
                delay(hideDelayMillis)
                isScrollbarVisible = false
            }
        }
    }

    // Keep visible while dragging
    LaunchedEffect(isDragging) {
        if (isDragging) {
            hideJob?.cancel()
            isScrollbarVisible = true
        }
    }

    // Calculate scrollbar progress (0.0 to 1.0)
    val scrollProgress by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f else {
                val firstVisible = gridState.firstVisibleItemIndex
                val offset = gridState.firstVisibleItemScrollOffset
                val avgItemSize = if (gridState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    gridState.layoutInfo.visibleItemsInfo.first().size.height.toFloat()
                } else 100f

                ((firstVisible + offset / avgItemSize.coerceAtLeast(1f)) / (totalItems - 1))
                    .coerceIn(0f, 1f)
            }
        }
    }

    // Get current item index for bubble display
    val currentItemIndex by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            if (totalItems <= 0) 0 else {
                (scrollProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
            }
        }
    }

    val bubbleText = remember(currentItemIndex) {
        bubbleFormatter?.invoke(currentItemIndex) ?: (currentItemIndex + 1).toString()
    }

    // Use rememberUpdatedState to ensure lambdas always access latest values
    val currentScrollProgress = rememberUpdatedState(scrollProgress)
    val currentGridState = rememberUpdatedState(gridState)

    // Animations
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) ScrollbarDefaults.TrackAlphaDragging else ScrollbarDefaults.TrackAlpha,
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

    Box(
        modifier = modifier
            .width(ScrollbarDefaults.TouchAreaWidth)
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track height for calculations
        var trackHeightPx by remember { mutableFloatStateOf(0f) }

        // Calculate thumb metrics
        val thumbHeightPx = with(density) { ScrollbarDefaults.ThumbHeight.toPx() }
        val minThumbHeightPx = with(density) { ScrollbarDefaults.MinThumbHeight.toPx() }

        // Dynamic thumb height based on content
        val totalItems = gridState.layoutInfo.totalItemsCount
        val dynamicThumbHeightPx = if (totalItems > 0) {
            val viewportRatio = gridState.layoutInfo.visibleItemsInfo.size.toFloat() / totalItems.coerceAtLeast(1)
            (trackHeightPx * viewportRatio).coerceIn(minThumbHeightPx, trackHeightPx * 0.5f)
        } else thumbHeightPx

        val maxThumbOffset = (trackHeightPx - dynamicThumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = scrollProgress * maxThumbOffset
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }
        val thumbHeight = with(density) { dynamicThumbHeightPx.toDp() }

        // Animated thumb width
        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) ScrollbarDefaults.ThumbWidthDragging else ScrollbarDefaults.ThumbWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "thumb_width"
        )

        // Store values that need to be accessed in pointerInput lambda
        val currentMaxThumbOffset = rememberUpdatedState(maxThumbOffset)
        val currentThumbHeightPx = rememberUpdatedState(dynamicThumbHeightPx)

        // Scrollbar track (background)
        Box(
            modifier = Modifier
                .width(ScrollbarDefaults.ThumbWidth)
                .fillMaxHeight()
                .alpha(scrollbarAlpha * trackAlpha)
                .clip(RoundedCornerShape(ScrollbarDefaults.ThumbCornerRadius))
                .background(MaterialTheme.colorScheme.outline)
        )

        // Full height interactive area with precise drag handling
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(ScrollbarDefaults.TouchAreaWidth)
                .onSizeChanged { size ->
                    trackHeightPx = size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Tap on track to jump to that position
                        val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)
                        val totalItemsCount = currentGridState.value.layoutInfo.totalItemsCount
                        val targetIndex = (tapProgress * (totalItemsCount - 1)).toInt()
                            .coerceIn(0, totalItemsCount - 1)
                        coroutineScope.launch {
                            currentGridState.value.scrollToItem(targetIndex)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // Calculate the offset from touch point to thumb center
                            val thumbCenterY = thumbOffsetPx + dynamicThumbHeightPx / 2
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
                            if (!isDragging) return@detectVerticalDragGestures
                            change.consume()

                            val updatedMaxOffset = currentMaxThumbOffset.value
                            val updatedThumbHeight = currentThumbHeightPx.value

                            // Calculate target position based on finger position minus the initial offset
                            val targetCenterY = change.position.y - dragOffsetFromThumb
                            val targetThumbTop = (targetCenterY - updatedThumbHeight / 2)
                                .coerceIn(0f, updatedMaxOffset)

                            // Convert thumb position to scroll progress
                            val newProgress = if (updatedMaxOffset > 0) {
                                targetThumbTop / updatedMaxOffset
                            } else 0f

                            val totalItemsCount = currentGridState.value.layoutInfo.totalItemsCount
                            val targetIndex = (newProgress * (totalItemsCount - 1)).toInt()
                                .coerceIn(0, totalItemsCount - 1)

                            coroutineScope.launch {
                                currentGridState.value.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        )

        // Visual thumb with enhanced styling
        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .alpha(scrollbarAlpha)
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .clip(RoundedCornerShape(ScrollbarDefaults.ThumbCornerRadius))
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
                            x = -(ScrollbarDefaults.TouchAreaWidth + 8.dp).roundToPx(),
                            y = (thumbOffsetPx + dynamicThumbHeightPx / 2 - ScrollbarDefaults.BubbleSize.toPx() / 2).toInt()
                        )
                    }
                    .alpha(bubbleAlpha)
                    .graphicsLayer {
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .size(ScrollbarDefaults.BubbleSize)
                    .shadow(4.dp, CircleShape)
                    .clip(RoundedCornerShape(ScrollbarDefaults.BubbleCornerRadius))
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
