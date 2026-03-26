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
 * Enhanced Material 3 scrollbar with alphabet preview bubble for alphabetically sorted lists.
 *
 * Features:
 * - Auto-hide with configurable delay
 * - **Letter preview bubble**: Shows current section letter when dragging thumb
 * - **Enhanced visuals**: Thumb expands when dragging, track becomes more visible
 * - **Haptic feedback**: On drag start and letter change
 * - **Tap support**: Tap on track to jump to that position
 * - **Precise drag support**: Thumb follows finger position exactly with offset correction
 * - **Chinese support**: Pinyin transliteration for Chinese characters
 *
 * @param listState The LazyListState to track scroll position
 * @param letterToIndex Map of first letter to first occurrence index in the list
 * @param totalItems Total number of items in the list
 * @param modifier Modifier for the scrollbar container
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops
 */
@Composable
fun AlphabetScrollbarM3E(
    listState: LazyListState,
    letterToIndex: Map<Char, Int>,
    totalItems: Int,
    modifier: Modifier = Modifier,
    hideDelayMillis: Long = ScrollbarDefaults.DefaultHideDelayMillis
) {
    if (totalItems <= 0) return

    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Track if user is actively dragging
    var isDragging by remember { mutableStateOf(false) }

    // Track offset from touch point to thumb center for precise drag following
    var dragOffsetFromThumb by remember { mutableFloatStateOf(0f) }

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

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

    // Also show scrollbar when dragging
    LaunchedEffect(isDragging) {
        if (isDragging) {
            hideJob?.cancel()
            isScrollbarVisible = true
        }
    }

    // Calculate scrollbar progress (0.0 to 1.0)
    val scrollProgress by remember {
        derivedStateOf {
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
    val currentTotalItems = rememberUpdatedState(totalItems)
    val currentLetterToIndex = rememberUpdatedState(letterToIndex)

    // Get current letter based on scroll position
    val currentLetter by remember {
        derivedStateOf {
            val targetIndex = (scrollProgress * (totalItems - 1)).toInt()
            findLetterForIndex(targetIndex, letterToIndex)
        }
    }

    // Track previous letter for haptic feedback on change
    var previousLetter by remember { mutableStateOf(currentLetter) }

    // Trigger haptic feedback when letter changes during drag
    LaunchedEffect(currentLetter, isDragging) {
        if (isDragging && currentLetter != previousLetter) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            previousLetter = currentLetter
        }
    }

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
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_alpha"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.7f,
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
        val dynamicThumbHeightPx = (trackHeightPx * listState.layoutInfo.visibleItemsInfo.size.toFloat() / totalItems.coerceAtLeast(1))
            .coerceIn(minThumbHeightPx, trackHeightPx * 0.5f)

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
                        val targetIndex = (tapProgress * (currentTotalItems.value - 1)).toInt()
                            .coerceIn(0, currentTotalItems.value - 1)
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
                            val thumbCenterY = thumbOffsetPx + dynamicThumbHeightPx / 2
                            dragOffsetFromThumb = offset.y - thumbCenterY
                            isDragging = true
                            previousLetter = currentLetter
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

                            val targetIndex = (newProgress * (currentTotalItems.value - 1)).toInt()
                                .coerceIn(0, currentTotalItems.value - 1)

                            coroutineScope.launch {
                                currentListState.value.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        )

        // Visual thumb with vibrant color
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

        // Preview bubble - appears when dragging, positioned at thumb
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = -(ScrollbarDefaults.TouchAreaWidth + 8.dp).roundToPx(),
                        y = (thumbOffsetPx + dynamicThumbHeightPx / 2 - ScrollbarDefaults.BubbleSize.toPx() / 2).toInt()
                    )
                }
                .alpha(bubbleAlpha)
                .graphicsLayer(
                    scaleX = bubbleScale,
                    scaleY = bubbleScale
                )
                .size(ScrollbarDefaults.BubbleSize)
                .shadow(4.dp, CircleShape)
                .clip(RoundedCornerShape(ScrollbarDefaults.BubbleCornerRadius))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentLetter.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Find the letter for a given index by finding the closest letter
 * that is less than or equal to the target index.
 */
private fun findLetterForIndex(targetIndex: Int, letterToIndex: Map<Char, Int>): Char {
    if (letterToIndex.isEmpty()) return '#'

    // Sort by index
    val sorted = letterToIndex.toList().sortedBy { it.second }

    // Find the letter with the largest index <= targetIndex
    var result = sorted.first().first
    for ((letter, index) in sorted) {
        if (index <= targetIndex) {
            result = letter
        } else {
            break
        }
    }
    return result
}

/**
 * Get the first letter for indexing purposes.
 * Returns uppercase letter for alphabetic characters (supports Chinese pinyin),
 * '0' for digits, '#' for symbols and empty strings.
 */
fun getFirstLetter(text: String): Char {
    if (text.isBlank()) return '#'

    val firstChar = text.trim().first()

    return when {
        // ASCII letters (A-Z, a-z)
        firstChar.isLetter() && firstChar.code < 128 -> {
            firstChar.uppercaseChar()
        }
        // Chinese characters - use ICU Transliterator for pinyin
        firstChar.code > 127 -> {
            getPinyinInitial(firstChar)
        }
        // Digits
        firstChar.isDigit() -> '0'
        // Other symbols
        else -> '#'
    }
}

/**
 * Get pinyin initial for a Chinese character using ICU Transliterator.
 * Falls back to '#' if conversion fails.
 */
private fun getPinyinInitial(char: Char): Char {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Use ICU Transliterator to convert Chinese to Latin/Pinyin
            val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin")
            val pinyin = transliterator.transliterate(char.toString())

            // Get first letter and uppercase it
            pinyin.firstOrNull { it.isLetter() }
                ?.uppercaseChar()
                ?: '#'
        } else {
            // Fallback for older Android versions - return the char itself if it's a letter
            if (char.isLetter()) char.uppercaseChar() else '#'
        }
    } catch (e: Exception) {
        // If conversion fails, check if it's a letter and return it, otherwise '#'
        if (char.isLetter()) char.uppercaseChar() else '#'
    }
}
