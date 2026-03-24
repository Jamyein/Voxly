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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

/**
 * Material 3 Expressive style scrollbar with alphabet preview.
 *
 * Features:
 * - M3E style capsule scrollbar (6dp width, rounded corners)
 * - Auto-hide: only visible when scrolling
 * - Preview bubble appears only when dragging thumb
 * - Bubble follows thumb position and displays current letter
 * - Haptic feedback on letter change
 * - Vibrant thumb color using primary color scheme
 *
 * @param listState The LazyListState to track scroll position
 * @param letterToIndex Map of first letter to first occurrence index
 * @param totalItems Total number of items in the list
 * @param modifier Modifier for the scrollbar container
 * @param hideDelayMillis Delay before hiding scrollbar after scroll stops (default: 1500ms)
 */
@Composable
fun AlphabetScrollbarM3E(
    listState: LazyListState,
    letterToIndex: Map<Char, Int>,
    totalItems: Int,
    modifier: Modifier = Modifier,
    hideDelayMillis: Long = 1500L
) {
    if (totalItems <= 0) return

    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Track if user is actively dragging
    var isDragging by remember { mutableStateOf(false) }

    // Auto-hide scrollbar state
    var isScrollbarVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    // Monitor scroll state to show/hide scrollbar
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            // Cancel any pending hide job
            hideJob?.cancel()
            isScrollbarVisible = true
        } else {
            // Start hide delay when scroll stops
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

    // Get current letter based on scroll position
    val currentLetter by remember {
        derivedStateOf {
            val targetIndex = (scrollProgress * (totalItems - 1)).toInt()
            findLetterForIndex(targetIndex, letterToIndex)
        }
    }

    // Animation for scrollbar visibility
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrollbar_alpha"
    )

    // Animation for bubble appearance
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_alpha"
    )

    // Bubble scale animation
    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_scale"
    )

    Box(
        modifier = modifier
            .width(40.dp)
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

        // Scrollbar thumb container with drag detection
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(Unit) {
                    trackHeightPx = size.height.toFloat()

                    detectVerticalDragGestures(
                        onDragStart = {
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
                            change.consume()

                            // Calculate new scroll progress based on drag
                            val newOffsetPx = (thumbOffsetPx + dragAmount).coerceIn(0f, maxThumbOffset)
                            val newProgress = if (maxThumbOffset > 0) {
                                newOffsetPx / maxThumbOffset
                            } else 0f

                            val targetIndex = (newProgress * (totalItems - 1)).toInt()
                                .coerceIn(0, totalItems - 1)

                            val newLetter = findLetterForIndex(targetIndex, letterToIndex)

                            // Haptic feedback on letter change
                            if (newLetter != currentLetter && isDragging) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }

                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        ) {
            // Visual thumb with vibrant color
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(48.dp)
                    .alpha(scrollbarAlpha)
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        // Preview bubble - appears when dragging, positioned at thumb
        Box(
            modifier = Modifier
                .offset(
                    x = (-28).dp, // Position to the left of scrollbar
                    y = thumbOffset + 24.dp - 20.dp // Center vertically with thumb
                )
                .alpha(bubbleAlpha)
                .graphicsLayer(
                    scaleX = bubbleScale,
                    scaleY = bubbleScale
                )
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentLetter.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
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
