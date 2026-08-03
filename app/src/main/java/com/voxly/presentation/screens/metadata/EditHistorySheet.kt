package com.voxly.presentation.screens.metadata

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.RecentEdit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistorySheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    recentEdits: List<RecentEdit>,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.edit_history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            )

            if (recentEdits.isEmpty()) {
                EmptyEditHistory()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(recentEdits, key = { it.filePath + it.timestamp }) { edit ->
                        EditHistoryItem(edit = edit)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditHistory() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_edit_history),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditHistoryItem(
    edit: RecentEdit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val changedFields = remember(edit.originalMetadata, edit.newMetadata) {
        computeChangedFields(edit.originalMetadata, edit.newMetadata)
    }

    Surface(
        onClick = { isExpanded = !isExpanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = edit.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimestamp(edit.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.modified_fields, changedFields.keys.joinToString("、")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "editHistoryChevron"
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ),
                exit = shrinkVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                )
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    changedFields.forEach { (field, pair) ->
                        val (oldValue, newValue) = pair
                        DiffRow(
                            fieldName = field,
                            oldValue = oldValue,
                            newValue = newValue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffRow(
    fieldName: String,
    oldValue: String?,
    newValue: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = fieldName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (oldValue != null) {
            Text(
                text = stringResource(R.string.original_value, oldValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
        if (newValue != null) {
            Text(
                text = stringResource(R.string.new_value, newValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun computeChangedFields(
    original: AudioMetadata,
    new: AudioMetadata
): Map<String, Pair<String?, String?>> {
    val changes = mutableMapOf<String, Pair<String?, String?>>()

    if (original.title != new.title) {
        changes["标题"] = original.title to new.title
    }
    if (original.artist != new.artist) {
        changes["艺术家"] = original.artist to new.artist
    }
    if (original.album != new.album) {
        changes["专辑"] = original.album to new.album
    }
    if (original.albumArtist != new.albumArtist) {
        changes["专辑艺术家"] = original.albumArtist to new.albumArtist
    }
    if (original.year != new.year) {
        changes["年份"] = original.year to new.year
    }
    if (original.genre != new.genre) {
        changes["流派"] = original.genre to new.genre
    }
    if (original.trackNumber != new.trackNumber) {
        changes["曲目号"] = original.trackNumber?.toString() to new.trackNumber?.toString()
    }
    if (original.discNumber != new.discNumber) {
        changes["碟片号"] = original.discNumber?.toString() to new.discNumber?.toString()
    }
    if (original.composer != new.composer) {
        changes["作曲家"] = original.composer to new.composer
    }
    if (original.lyricist != new.lyricist) {
        changes["作词人"] = original.lyricist to new.lyricist
    }
    if (original.comment != new.comment) {
        changes["注释"] = original.comment to new.comment
    }
    if (original.lyrics != new.lyrics) {
        changes["歌词"] = (if (original.lyrics.isNullOrBlank()) "无" else "有歌词") to
                        (if (new.lyrics.isNullOrBlank()) "无" else "有歌词")
    }
    if (original.albumArt != null && new.albumArt == null) {
        changes["封面"] = "有封面" to "无封面"
    } else if (original.albumArt == null && new.albumArt != null) {
        changes["封面"] = "无封面" to "有封面"
    }

    return changes
}

@Composable
private fun formatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        isSameDay(now, date) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)).let { "今天 $it" }
        }
        isYesterday(now, date) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)).let { "昨天 $it" }
        }
        isSameYear(now, date) -> {
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, date: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, date)
}

private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}
