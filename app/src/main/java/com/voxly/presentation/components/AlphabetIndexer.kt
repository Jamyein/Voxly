package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import com.voxly.presentation.theme.ExpressiveMotion
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxly.domain.model.AudioFile
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Alphabet indexer sidebar for quick navigation through a sorted list.
 * Supports both English letters and Chinese pinyin initials.
 *
 * Implementation follows 字母侧边栏.md specification:
 * - Uses awaitEachGesture + awaitFirstDown for gesture detection
 * - Uses Column for letter layout
 * - Uses native ICU for Chinese pinyin (API 29+)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlphabetIndexer(
    groupedFiles: Map<Char, List<AudioFile>>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
    showAllLetters: Boolean = true,
    availableHeight: Dp
) {
    // Full alphabet: 0 for numbers, A-Z for letters, # for symbols
    val allLetters = listOf('0') + ('A'..'Z').toList() + '#'

    // Available letters (letters that have files)
    val availableLetters = remember(groupedFiles) {
        groupedFiles.keys.sorted()
    }

    // Letters to display: all letters if showAllLetters is true, otherwise only available
    val displayLetters = if (showAllLetters) allLetters else availableLetters

    if (displayLetters.isEmpty()) return

    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var touchOffsetY by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }

    // Fixed dimensions for the sidebar
    val sidebarWidth = 24.dp

    // Font size - use Material 3 labelSmall for readability
    val letterFontSize = 10.sp

    // 计算所有字母需要的最小高度（28个字母 × 紧凑行高）
    val minLetterHeight = letterFontSize.value * 0.8f
    val minRequiredHeight = minLetterHeight * displayLetters.size

    // 实际可用高度（确保最小高度能容纳所有字母）
    val actualAvailableHeight = availableHeight.coerceAtLeast(minRequiredHeight.dp)

    // 动态计算每个字母的高度，确保全部显示
    val adjustedLetterHeight = (actualAvailableHeight.value / displayLetters.size).dp

    Box(
        modifier = modifier
            .size(width = sidebarWidth, height = actualAvailableHeight)
            .padding(start = 2.dp)
            .pointerInput(displayLetters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isDragging = true

                    while (isDragging) {
                        val event = awaitPointerEvent()
                        val y = event.changes.first().position.y

                        // Calculate which letter index based on Y position
                        val index = ((y / size.height) * displayLetters.size)
                            .toInt()
                            .coerceIn(0, displayLetters.lastIndex)

                        val letter = displayLetters.getOrNull(index)

                        // Update state and trigger callback
                        if (letter != null && letter != selectedLetter) {
                            touchOffsetY = y
                            selectedLetter = letter
                            isTouching = true

                            // Only trigger callback if letter has files (or showAllLetters is false)
                            if (!showAllLetters || letter in availableLetters) {
                                onLetterSelected(letter)
                            }
                        }

                        // Check if gesture ended
                        if (!event.changes.any { it.pressed }) {
                            isDragging = false
                            isTouching = false
                            selectedLetter = null
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.height(actualAvailableHeight),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            displayLetters.forEach { letter ->
                val isAvailable = letter in availableLetters
                val isSelected = selectedLetter == letter

                val fontSize = if (isSelected) (letterFontSize.value + 2).sp else letterFontSize
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                val color by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "letter_color"
                )

                Text(
                    text = letter.uppercase(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color
                )
            }
        }
    }

    // Preview tooltip (with animation - follows finger movement)
    if (isTouching && selectedLetter != null) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = -56,
                        y = (touchOffsetY - 24.dp.toPx()).roundToInt()
                    )
                }
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val previewScale by animateDpAsState(
                targetValue = 20.dp,
                animationSpec = ExpressiveMotion.EmphasizedSpringDp,
                label = "preview_scale"
            )
            Text(
                text = selectedLetter!!.uppercase(),
                fontSize = previewScale.value.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Groups audio files by their first letter (supports Chinese pinyin).
 */
fun groupFilesByFirstLetter(files: List<AudioFile>): Map<Char, List<AudioFile>> {
    return files.groupBy { file ->
        getFirstLetter(file.name)
    }
}

/**
 * Gets the first letter of a filename, supporting both English and Chinese.
 * Uses native ICU Transliterator for Chinese pinyin (API 29+).
 *
 * - English: Returns uppercase first character
 * - Chinese: Returns pinyin initial (A-Z)
 * - Others: Returns '#' for symbols/numbers
 */
fun getFirstLetter(name: String): Char {
    if (name.isBlank()) return '#'

    val firstChar = name.trimStart().firstOrNull() ?: '#'

    // If already ASCII letter, return uppercase
    if (firstChar.isLetter() && firstChar.code in 0..127) {
        return firstChar.uppercaseChar()
    }

    // Digits - map to '0' for indexing
    if (firstChar.isDigit()) {
        return '0'
    }

    val upperChar = firstChar.uppercaseChar()

    // English letters A-Z
    if (upperChar in 'A'..'Z') {
        return upperChar
    }

    // Symbols and other characters
    if (!firstChar.isLetter()) {
        return '#'
    }

    // Chinese characters - use native ICU Transliterator (API 29+)
    return getChinesePinyinInitial(firstChar)
}

/**
 * Gets pinyin initial for a Chinese character using native ICU Transliterator.
 * Falls back to Collator-based approach if API < 29.
 */
private fun getChinesePinyinInitial(char: Char): Char {
    val charString = char.toString()

    // Try native ICU Transliterator first (API 29+)
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin; Latin-Ascii; Any-Upper")
            val pinyin = transliterator.transliterate(charString)
            if (pinyin.isNotEmpty() && pinyin.first().isLetter()) {
                pinyin.first().uppercaseChar()
            } else {
                fallbackPinyin(char)
            }
        } else {
            fallbackPinyin(char)
        }
    } catch (e: Exception) {
        fallbackPinyin(char)
    }
}

/**
 * Fallback pinyin lookup using Collator (for API < 29).
 */
private fun fallbackPinyin(char: Char): Char {
    val charString = char.toString()

    // Reference characters sorted by pinyin order
    val pinyinReference = listOf(
        'A' to "啊", 'B' to "八", 'C' to "嚓", 'D' to "大",
        'E' to "额", 'F' to "发", 'G' to "嘎", 'H' to "哈",
        'J' to "鸡", 'K' to "咖", 'L' to "拉", 'M' to "妈",
        'N' to "那", 'O' to "哦", 'P' to "七", 'Q' to "七",
        'R' to "日", 'S' to "撒", 'T' to "他", 'W' to "娃",
        'X' to "西", 'Y' to "呀", 'Z' to "扎"
    )

    val collator = Collator.getInstance(Locale.CHINA)
    collator.strength = Collator.PRIMARY

    for ((initial, reference) in pinyinReference) {
        if (collator.compare(charString, reference) <= 0) {
            return initial
        }
    }

    return 'Z'
}

/**
 * Creates a flat list of items with section headers for LazyColumn.
 */
fun createSectionedItems(
    groupedFiles: Map<Char, List<AudioFile>>
): List<SectionItem> {
    val sortedKeys = groupedFiles.keys.sorted()
    val items = mutableListOf<SectionItem>()

    for (letter in sortedKeys) {
        val files = groupedFiles[letter] ?: continue
        items.add(SectionItem.Header(letter))
        files.forEach { file ->
            items.add(SectionItem.FileItem(file))
        }
    }

    return items
}

/**
 * Sealed class representing items in a sectioned list.
 */
sealed class SectionItem {
    data class Header(val letter: Char) : SectionItem()
    data class FileItem(val file: AudioFile) : SectionItem()
}
