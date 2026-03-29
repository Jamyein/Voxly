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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Material 3 Expressive scrollbar with alphabet preview for alphabetically sorted lists.
 *
 * M3E features:
 * - Letter preview bubble with spring bounce animation
 * - Per-letter haptic feedback (CLOCK_TICK) on letter change
 * - VelocityTracker-based inertia on drag release
 * - Tap-to-jump and drag-to-scroll through alphabet
 * - Chinese pinyin initial support via ICU Transliterator
 *
 * @param state The LazyListState of the LazyColumn
 * @param letterToIndex Map of first letter to first occurrence index
 * @param totalItems Total number of items in the list
 * @param modifier Modifier for the scrollbar container
 * @param config Optional scrollbar configuration
 */
@Composable
fun AlphabetScrollbarM3E(
    state: LazyListState,
    letterToIndex: Map<Char, Int>,
    totalItems: Int,
    modifier: Modifier = Modifier,
    config: ScrollbarConfig = ScrollbarConfig.Default
) {
    if (totalItems <= 0) return

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val velocityTracker = remember { VelocityTracker() }

    var isDragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var previousLetter by remember { mutableStateOf(' ') }
    var isVisible by remember { mutableStateOf(false) }

    // Smart auto-hide using scroll progress
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(config.hideDelayMillis)
            isVisible = false
        }
    }

    val scrollbarState = remember(state, letterToIndex, totalItems) {
        AlphabetScrollbarState(state, letterToIndex, totalItems)
    }

    val contentSize by remember { derivedStateOf { scrollbarState.contentSize } }
    val viewportSize by remember { derivedStateOf { scrollbarState.viewportSize } }
    val scrollOffset by remember { derivedStateOf { scrollbarState.scrollOffset } }

    if (contentSize <= 0 || viewportSize <= 0) return

    val scrollRange = (contentSize - viewportSize).coerceAtLeast(1)
    val scrollProgress = (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)

    val minThumbHeightPx = with(density) { config.minThumbHeight.toPx() }
    val maxThumbHeightPx = (viewportSize * 0.5f).coerceAtLeast(minThumbHeightPx)

    val thumbHeightPx = if (contentSize > 0 && viewportSize > 0) {
        (viewportSize.toFloat() / contentSize * viewportSize)
            .coerceIn(minThumbHeightPx, maxThumbHeightPx)
    } else {
        with(density) { config.thumbHeight.toPx() }
    }

    val maxThumbOffset = (viewportSize - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = if (isDragging) {
        dragY.coerceIn(0f, maxThumbOffset)
    } else {
        scrollProgress * maxThumbOffset
    }

    val currentLetter by remember { derivedStateOf { scrollbarState.getCurrentLetter() } }

    // Per-letter haptic tick
    if (isDragging && currentLetter != previousLetter) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        previousLetter = currentLetter
    }

    // --- M3E spring animations ---
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) config.thumbWidthDragging else config.thumbWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
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

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubble_scale"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        label = "bubble_alpha"
    )

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrollbar_alpha"
    )

    // M3E colors
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
                    detectTapGestures { offset ->
                        if (!isDragging) {
                            val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                scrollbarState.scrollToProgress(tapProgress)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                .pointerInput(maxThumbOffset) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragY = offset.y
                            velocityTracker.resetTracking()
                            previousLetter = currentLetter
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            velocityTracker.addPointerInputChange(change)
                            dragY = change.position.y.coerceIn(0f, maxThumbOffset)

                            val fraction = (dragY / maxThumbOffset).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                scrollbarState.scrollToProgress(fraction)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val velocity = velocityTracker.calculateVelocity().y
                            if (abs(velocity) > 500f) {
                                coroutineScope.launch {
                                    state.scroll {
                                        scrollBy(velocity / 5f)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        )

        // Thumb (pill shape)
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

        // Alphabet preview bubble
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = -(config.touchAreaWidth + 8.dp).roundToPx(),
                        y = (thumbOffsetPx + thumbHeightPx / 2 - config.bubbleSize.toPx() / 2).roundToInt()
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
                text = currentLetter.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = bubbleTextColor
            )
        }
    }
}

/**
 * ScrollbarState implementation for alphabet-based navigation.
 */
private class AlphabetScrollbarState(
    private val listState: LazyListState,
    private val letterToIndex: Map<Char, Int>,
    private val totalItems: Int
) : ScrollbarState {

    override val contentSize: Int
        get() = calculateContentSize()

    override val scrollOffset: Int
        get() = calculateScrollOffset()

    override val viewportSize: Int
        get() = calculateViewportSize()

    override val isScrollInProgress: Boolean
        get() = listState.isScrollInProgress

    override val totalItemsCount: Int
        get() = totalItems

    private fun calculateContentSize(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || totalItems <= 0) return 0

        val avgItemSize = if (visibleItems.size > 1) {
            val first = visibleItems.first()
            val last = visibleItems.last()
            (last.offset + last.size - first.offset) / visibleItems.size
        } else {
            visibleItems.firstOrNull()?.size ?: 100
        }

        return avgItemSize * totalItems
    }

    private fun calculateScrollOffset(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return 0
        val firstVisible = visibleItems.first()
        return firstVisible.offset + listState.firstVisibleItemScrollOffset
    }

    private fun calculateViewportSize(): Int {
        val layoutInfo = listState.layoutInfo
        return max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    fun getCurrentLetter(): Char {
        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) scrollOffset.toFloat() / scrollRange else 0f
        val targetIndex = (progress * (totalItems - 1)).toInt()
        return findLetterForIndex(targetIndex)
    }

    suspend fun scrollToProgress(progress: Float) {
        if (totalItems <= 0) return
        val targetIndex = (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
        listState.scrollToItem(targetIndex)
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        listState.scroll {
            scrollBy(velocity / 5f)
        }
    }

    private fun findLetterForIndex(targetIndex: Int): Char {
        if (letterToIndex.isEmpty()) return '#'
        val sorted = letterToIndex.toList().sortedBy { it.second }
        var result = sorted.first().first
        for ((letter, index) in sorted) {
            if (index <= targetIndex) result = letter else break
        }
        return result
    }
}

/**
 * Get the first letter for indexing purposes.
 * Returns uppercase letter for alphabetic characters (supports Chinese pinyin),
 * '0' for digits, '#' for symbols and empty strings.
 *
 * @param text The text to extract the first letter from
 * @return The first letter for indexing ('A'-'Z', '0', or '#')
 */
fun getFirstLetter(text: String): Char {
    if (text.isBlank()) return '#'

    val firstChar = text.trim().first()

    return when {
        firstChar.isLetter() && firstChar.code < 128 -> firstChar.uppercaseChar()
        firstChar.code > 127 -> getPinyinInitial(firstChar)
        firstChar.isDigit() -> '0'
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
            val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin")
            val pinyin = transliterator.transliterate(char.toString())
            pinyin.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
        } else {
            if (char.isLetter()) char.uppercaseChar() else '#'
        }
    } catch (e: Exception) {
        if (char.isLetter()) char.uppercaseChar() else '#'
    }
}
