package com.voxly.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
 * IMPORTANT: This component should be positioned at the right edge of the screen
 * using parent Box with align(Alignment.CenterEnd). It should NOT fill the full
 * height of the parent, but should use wrapContentHeight() instead.
 */
@Composable
fun AlphabetIndexer(
    groupedFiles: Map<Char, List<AudioFile>>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
    showAllLetters: Boolean = true
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
    val sidebarWidth = 20.dp
    val letterSpacing = 2.dp

    // Static font sizes
    val selectedFontSize = 11.sp
    val unselectedFontSize = 8.sp

    // Calculate expected height based on letter count
    val expectedHeight = remember(displayLetters.size) {
        (displayLetters.size * 16).dp // Approximate height per letter
    }

    Box(
        modifier = modifier
            .size(width = sidebarWidth, height = expectedHeight)
            .padding(start = 2.dp)
            .pointerInput(displayLetters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        touchOffsetY = offset.y
                        val index = (touchOffsetY / (size.height.toFloat() / displayLetters.size)).toInt()
                            .coerceIn(0, displayLetters.lastIndex)
                        selectedLetter = displayLetters.getOrNull(index)
                        if (selectedLetter != null && (!showAllLetters || selectedLetter in availableLetters)) {
                            onLetterSelected(selectedLetter!!)
                        }
                    },
                    onDrag = { change, _ ->
                        touchOffsetY = change.position.y
                        val index = (touchOffsetY / (size.height.toFloat() / displayLetters.size)).toInt()
                            .coerceIn(0, displayLetters.lastIndex)
                        val newLetter = displayLetters.getOrNull(index)
                        if (newLetter != selectedLetter) {
                            selectedLetter = newLetter
                            if (newLetter != null && (!showAllLetters || newLetter in availableLetters)) {
                                onLetterSelected(newLetter)
                            }
                        }
                    },
                    onDragEnd = {
                        isTouching = false
                        selectedLetter = null
                    },
                    onDragCancel = {
                        isTouching = false
                        selectedLetter = null
                    }
                )
            }
            .pointerInput(displayLetters) {
                detectTapGestures(
                    onTap = { offset ->
                        val index = (offset.y / (size.height.toFloat() / displayLetters.size)).toInt()
                            .coerceIn(0, displayLetters.lastIndex)
                        val tappedLetter = displayLetters.getOrNull(index)
                        if (tappedLetter != null && tappedLetter in availableLetters) {
                            selectedLetter = tappedLetter
                            onLetterSelected(tappedLetter)
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            displayLetters.forEach { letter ->
                val isAvailable = letter in availableLetters
                val isSelected = selectedLetter == letter

                val fontSize = if (isSelected) selectedFontSize else unselectedFontSize
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
                    color = color,
                    modifier = Modifier.padding(vertical = letterSpacing)
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
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
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
 * - English: Returns uppercase first character
 * - Chinese: Returns pinyin initial (A-Z)
 * - Others: Returns '#' for symbols/numbers
 */
fun getFirstLetter(name: String): Char {
    if (name.isBlank()) return '#'

    val firstChar = name.trimStart().firstOrNull() ?: '#'

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

    // Chinese characters - use Collator to get pinyin
    return getChinesePinyinInitial(firstChar)
}

/**
 * Gets the pinyin initial for a Chinese character.
 * Uses a reference list sorted by pinyin order and binary search to find the correct initial.
 */
private fun getChinesePinyinInitial(char: Char): Char {
    // Convert character to string for comparison
    val charString = char.toString()

    // Reference characters sorted by pinyin order (using Collator)
    // Each entry: (pinyin initial, reference character in that group)
    val pinyinReference = listOf(
        'A' to "啊",
        'B' to "八",
        'C' to "嚓",
        'D' to "大",
        'E' to "额",
        'F' to "发",
        'G' to "嘎",
        'H' to "哈",
        'J' to "鸡",
        'K' to "咖",
        'L' to "拉",
        'M' to "妈",
        'N' to "那",
        'O' to "哦",
        'P' to "七",
        'Q' to "七",
        'R' to "日",
        'S' to "撒",
        'T' to "他",
        'W' to "娃",
        'X' to "西",
        'Y' to "呀",
        'Z' to "扎"
    )

    // Use Collator with Chinese locale for pinyin-aware comparison
    val collator = Collator.getInstance(Locale.CHINA)
    collator.strength = Collator.PRIMARY  // Only compare base characters, ignore diacritics

    // Find the first group where our character comes before (or equals) the reference character
    for ((initial, reference) in pinyinReference) {
        if (collator.compare(charString, reference) <= 0) {
            return initial
        }
    }

    return 'Z' // Default to Z if not found
}

/**
 * Creates a flat list of items with section headers for LazyColumn.
 * Returns pairs of (isHeader: Boolean, data: Any)
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
