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
import androidx.compose.foundation.lazy.LazyListState
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
import kotlin.math.max

/**
 * Material 3 Expressive scrollbar with alphabet preview for alphabetically sorted lists.
 *
 * Features:
 * - Letter preview bubble when dragging
 * - Haptic feedback on letter change
 * - Tap-to-jump to letter section
 * - Drag-to-scroll through alphabet
 * - Chinese pinyin support
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

    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetFromThumb by remember { mutableFloatStateOf(0f) }
    var previousLetter by remember { mutableStateOf('A') }

    val scrollbarState = remember(state, letterToIndex, totalItems) {
        AlphabetScrollbarState(state, letterToIndex, totalItems)
    }

    val contentSize = scrollbarState.contentSize
    val viewportSize = scrollbarState.viewportSize
    val scrollOffset = scrollbarState.scrollOffset

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
    val thumbOffsetPx = scrollProgress * maxThumbOffset

    val currentLetter = scrollbarState.getCurrentLetter()

    // Haptic feedback on letter change
    if (isDragging && currentLetter != previousLetter) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        previousLetter = currentLetter
    }

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
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_alpha"
    )

    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = config.visualFeedbackStiffness
        ),
        label = "bubble_scale"
    )

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
            .padding(end = 4.dp),
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

        // Interactive area
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(config.touchAreaWidth)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val tapProgress = (offset.y / size.height).coerceIn(0f, 1f)
                        coroutineScope.launch {
                            scrollbarState.scrollToProgress(tapProgress)
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
                            previousLetter = currentLetter
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val targetCenterY = change.position.y - dragOffsetFromThumb
                            val targetThumbTop = (targetCenterY - thumbHeightPx / 2)
                                .coerceIn(0f, maxThumbOffset)
                            val newProgress = if (maxThumbOffset > 0) targetThumbTop / maxThumbOffset else 0f
                            coroutineScope.launch {
                                scrollbarState.scrollToProgress(newProgress)
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
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = -(config.touchAreaWidth + 8.dp).roundToPx(),
                        y = (thumbOffsetPx + thumbHeightPx / 2 - config.bubbleSize.toPx() / 2).toInt()
                    )
                }
                .alpha(bubbleAlpha)
                .graphicsLayer(scaleX = bubbleScale, scaleY = bubbleScale)
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
 *
 * @param char The Chinese character
 * @return The pinyin initial letter or '#' if conversion fails
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
            // Fallback for older Android versions
            if (char.isLetter()) char.uppercaseChar() else '#'
        }
    } catch (e: Exception) {
        // If conversion fails, check if it's a letter
        if (char.isLetter()) char.uppercaseChar() else '#'
    }
}
