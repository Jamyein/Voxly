package com.voxly.presentation.components.lyricsposter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.Lyrics.Companion.parseToLines

/**
 * Maximum number of lyrics lines that can be selected.
 */
private const val MAX_SELECTIONS = 6

/**
 * Bottom sheet for selecting lyrics lines to include in the poster.
 * Allows selecting up to 6 lines of lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSelectorBottomSheet(
    lyricsText: String,
    onDismiss: () -> Unit,
    onConfirm: (Map<Int, String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Parse lyrics to get all available lines
    val allLyricsLines = remember(lyricsText) {
        parseToLines(lyricsText)
    }

    // Selected lines state - Map of index to lyrics text
    var selectedLines by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.select_lyrics_for_poster),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Selection info
            Text(
                text = stringResource(R.string.selected_lines_count, selectedLines.size, MAX_SELECTIONS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics selection list
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                if (allLyricsLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_lyrics_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        itemsIndexed(allLyricsLines) { index, line ->
                            val isSelected = index in selectedLines.keys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        selectedLines = updateSelectedLines(
                                            selectedLines,
                                            index,
                                            line,
                                            MAX_SELECTIONS
                                        )
                                    }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedLines = updateSelectedLines(
                                            selectedLines,
                                            index,
                                            line,
                                            MAX_SELECTIONS
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        onConfirm(selectedLines)
                    },
                    enabled = selectedLines.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.confirm_selection))
                }
            }
        }
    }
}

/**
 * Updates the selected lines map with toggle logic.
 */
private fun updateSelectedLines(
    selectedLines: Map<Int, String>,
    key: Int,
    value: String,
    maxSelections: Int
): Map<Int, String> {
    return if (selectedLines.containsKey(key)) {
        // Deselect if already selected
        selectedLines.minus(key)
    } else if (selectedLines.size < maxSelections) {
        // Add new selection if under limit
        selectedLines.plus(key to value)
    } else {
        // Already at max, do nothing
        selectedLines
    }
}
