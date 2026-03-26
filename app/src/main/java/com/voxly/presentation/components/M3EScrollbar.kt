package com.voxly.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState

/**
 * Material 3 Expressive style standard scrollbar for LazyColumn with drag support.
 *
 * Features:
 * - Auto-hide: only visible when scrolling (1.5s delay)
 * - M3E style capsule scrollbar (6dp width, rounded corners)
 * - Vibrant thumb color using primary color
 * - Spring animations for smooth appearance/disappearance
 * - DRAG SUPPORT: Pull thumb to quickly navigate through list
 *
 * @param listState The LazyListState to track scroll position
 * @param modifier Modifier for the scrollbar container
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops (default: 1500ms)
 */
@Composable
fun M3EScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    hideDelayMillis: Long = 1500L
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }

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
    
    // Use rememberUpdatedState to ensure lambdas always access latest values
    val currentScrollProgress = rememberUpdatedState(scrollProgress)
    val currentListState = rememberUpdatedState(listState)

    // Animation for scrollbar visibility
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    Box(
        modifier = modifier
            .width(24.dp)  // Wider touch area for easier dragging
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track height for calculations
        var trackHeightPx by remember { mutableFloatStateOf(0f) }

        // Scrollbar track
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .alpha(scrollbarAlpha)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        // Calculate thumb position
        val thumbHeightPx = with(density) { 48.dp.toPx() }
        val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = scrollProgress * maxThumbOffset
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }
        
        // Store values that need to be accessed in pointerInput lambda
        val currentThumbOffsetPx = rememberUpdatedState(thumbOffsetPx)
        val currentMaxThumbOffset = rememberUpdatedState(maxThumbOffset)
        val currentTrackHeightPx = rememberUpdatedState(trackHeightPx)
        val currentIsDragging = rememberUpdatedState(isDragging)

        // Full height draggable area
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(Unit) {
                    val updatedTrackHeight = size.height.toFloat()
                    trackHeightPx = updatedTrackHeight

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val updatedThumbOffset = currentScrollProgress.value * currentMaxThumbOffset.value
                            val thumbTop = updatedThumbOffset
                            val thumbBottom = updatedThumbOffset + thumbHeightPx
                            
                            // Allow drag if touching anywhere on the scrollbar area
                            isDragging = true
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!currentIsDragging.value) return@detectVerticalDragGestures
                            
                            change.consume()
                            
                            // Use updated values for calculations
                            val updatedMaxOffset = currentMaxThumbOffset.value
                            val updatedThumbOffset = currentScrollProgress.value * updatedMaxOffset
                            
                            // Calculate new position based on drag
                            val newOffsetPx = (updatedThumbOffset + dragAmount).coerceIn(0f, updatedMaxOffset)
                            val newProgress = if (updatedMaxOffset > 0) {
                                newOffsetPx / updatedMaxOffset
                            } else 0f

                            val totalItems = currentListState.value.layoutInfo.totalItemsCount
                            val targetIndex = (newProgress * (totalItems - 1)).toInt()
                                .coerceIn(0, totalItems - 1)

                            coroutineScope.launch {
                                currentListState.value.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        ) {
            // Visual thumb
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(48.dp)
                    .alpha(scrollbarAlpha)
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}

/**
 * Material 3 Expressive style standard scrollbar for LazyVerticalGrid with drag support.
 *
 * Features:
 * - Auto-hide: only visible when scrolling (1.5s delay)
 * - M3E style capsule scrollbar (6dp width, rounded corners)
 * - Vibrant thumb color using primary color
 * - Spring animations for smooth appearance/disappearance
 * - DRAG SUPPORT: Pull thumb to quickly navigate through grid
 *
 * @param gridState The LazyGridState to track scroll position
 * @param modifier Modifier for the scrollbar container
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops (default: 1500ms)
 */
@Composable
fun M3EGridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    hideDelayMillis: Long = 1500L
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragging by remember { mutableStateOf(false) }

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
    
    // Use rememberUpdatedState to ensure lambdas always access latest values
    val currentScrollProgress = rememberUpdatedState(scrollProgress)
    val currentGridState = rememberUpdatedState(gridState)

    // Animation for scrollbar visibility
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    Box(
        modifier = modifier
            .width(24.dp)  // Wider touch area for easier dragging
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Track height for calculations
        var trackHeightPx by remember { mutableFloatStateOf(0f) }

        // Scrollbar track
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .alpha(scrollbarAlpha)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        // Calculate thumb position
        val thumbHeightPx = with(density) { 48.dp.toPx() }
        val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = scrollProgress * maxThumbOffset
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }
        
        // Store values that need to be accessed in pointerInput lambda
        val currentThumbOffsetPx = rememberUpdatedState(thumbOffsetPx)
        val currentMaxThumbOffset = rememberUpdatedState(maxThumbOffset)
        val currentTrackHeightPx = rememberUpdatedState(trackHeightPx)
        val currentIsDragging = rememberUpdatedState(isDragging)

        // Full height draggable area
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(Unit) {
                    val updatedTrackHeight = size.height.toFloat()
                    trackHeightPx = updatedTrackHeight

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val updatedThumbOffset = currentScrollProgress.value * currentMaxThumbOffset.value
                            val thumbTop = updatedThumbOffset
                            val thumbBottom = updatedThumbOffset + thumbHeightPx
                            
                            // Allow drag if touching anywhere on the scrollbar area
                            isDragging = true
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!currentIsDragging.value) return@detectVerticalDragGestures
                            
                            change.consume()
                            
                            // Use updated values for calculations
                            val updatedMaxOffset = currentMaxThumbOffset.value
                            val updatedThumbOffset = currentScrollProgress.value * updatedMaxOffset
                            
                            // Calculate new position based on drag
                            val newOffsetPx = (updatedThumbOffset + dragAmount).coerceIn(0f, updatedMaxOffset)
                            val newProgress = if (updatedMaxOffset > 0) {
                                newOffsetPx / updatedMaxOffset
                            } else 0f

                            val totalItems = currentGridState.value.layoutInfo.totalItemsCount
                            val targetIndex = (newProgress * (totalItems - 1)).toInt()
                                .coerceIn(0, totalItems - 1)

                            coroutineScope.launch {
                                currentGridState.value.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        ) {
            // Visual thumb
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(48.dp)
                    .alpha(scrollbarAlpha)
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}
