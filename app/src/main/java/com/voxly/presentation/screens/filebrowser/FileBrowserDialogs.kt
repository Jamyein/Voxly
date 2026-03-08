package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingleFileRenameDialog(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(audioFile.path) { mutableStateOf(audioFile.name.substringBeforeLast(".")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.rename_file)) },
        text = {
            Column {
                Text(
                    text = audioFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Add extension back
                    val extension = audioFile.name.substringAfterLast(".", "")
                    val fullName = if (extension.isNotEmpty()) "$newName.$extension" else newName
                    onConfirm(fullName)
                },
                enabled = newName.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Batch Operations Menu Dialog (when files are selected)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchOperationsMenuDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Edit), contentDescription = stringResource(R.string.cd_edit_file)) },
        title = { Text(stringResource(R.string.batch_operations)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Menu items
                Column {
                    BatchMenuItem(
                        icon = AppIcon.CloudDownload,
                        label = stringResource(R.string.batch_online_metadata),
                        onClick = onOnlineMetadata
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BatchMenuItem(
                        icon = AppIcon.Edit,
                        label = stringResource(R.string.batch_unified_field),
                        onClick = onUnifiedField
                    )

                    BatchMenuItem(
                        icon = AppIcon.AutoFix,
                        label = stringResource(R.string.batch_replace_text),
                        onClick = onReplaceText
                    )

                    BatchMenuItem(
                        icon = AppIcon.Schedule,
                        label = stringResource(R.string.batch_auto_number),
                        onClick = onAutoNumber
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BatchMenuItem(
                        icon = AppIcon.Rename,
                        label = stringResource(R.string.batch_rename_files),
                        onClick = onRenameFiles
                    )

                    BatchMenuItem(
                        icon = AppIcon.Check,
                        label = stringResource(R.string.batch_fix_metadata),
                        onClick = onFixMetadata
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Unified Field Dialog - Set a field to the same value for all selected files
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedFieldDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (field: String, value: String) -> Unit
) {
    var selectedField by remember { mutableStateOf("artist") }
    var fieldValue by remember { mutableStateOf("") }

    val fields = listOf(
        "artist" to stringResource(R.string.metadata_artist),
        "album" to stringResource(R.string.metadata_album),
        "albumArtist" to stringResource(R.string.metadata_album_artist),
        "year" to stringResource(R.string.metadata_year),
        "genre" to stringResource(R.string.metadata_genre),
        "composer" to stringResource(R.string.metadata_composer)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Edit), contentDescription = stringResource(R.string.cd_edit_file)) },
        title = { Text(stringResource(R.string.batch_unified_field_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Field selector
                Text(
                    text = stringResource(R.string.select_field),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column {
                    fields.forEach { (fieldKey, fieldLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedField = fieldKey }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedField == fieldKey,
                                onClick = { selectedField = fieldKey }
                            )
                            Text(
                                text = fieldLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Value input
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = { Text(stringResource(R.string.field_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedField, fieldValue) },
                enabled = fieldValue.isNotBlank()
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Replace Text Dialog - Find and replace text in fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplaceTextDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (field: String, searchText: String, replaceText: String, useRegex: Boolean) -> Unit
) {
    var selectedField by remember { mutableStateOf("title") }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }

    val fields = listOf(
        "title" to stringResource(R.string.metadata_title),
        "artist" to stringResource(R.string.metadata_artist),
        "album" to stringResource(R.string.metadata_album),
        "all" to stringResource(R.string.all_fields)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.AutoFix), contentDescription = stringResource(R.string.cd_batch_fix)) },
        title = { Text(stringResource(R.string.batch_replace_text_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Field selector
                Text(
                    text = stringResource(R.string.select_field),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column {
                    fields.forEach { (fieldKey, fieldLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedField = fieldKey }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedField == fieldKey,
                                onClick = { selectedField = fieldKey }
                            )
                            Text(
                                text = fieldLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search text
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.search_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Replace text
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text(stringResource(R.string.replace_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Use regex option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useRegex = !useRegex }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = useRegex,
                        onCheckedChange = { useRegex = it }
                    )
                    Text(
                        text = stringResource(R.string.use_regex),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedField, searchText, replaceText, useRegex) },
                enabled = searchText.isNotBlank()
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Auto Number Dialog - Generate sequential track numbers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoNumberDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (startNumber: Int, step: Int, totalTracks: Int?) -> Unit
) {
    var startNumber by remember { mutableIntStateOf(1) }
    var step by remember { mutableIntStateOf(1) }
    var setTotalTracks by remember { mutableStateOf(false) }
    var totalTracks by remember { mutableIntStateOf(targetFilesCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Schedule), contentDescription = stringResource(R.string.replay_gain_scan)) },
        title = { Text(stringResource(R.string.batch_auto_number_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Start number
                OutlinedTextField(
                    value = startNumber.toString(),
                    onValueChange = { startNumber = it.toIntOrNull() ?: 1 },
                    label = { Text(stringResource(R.string.start_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step
                OutlinedTextField(
                    value = step.toString(),
                    onValueChange = { step = it.toIntOrNull() ?: 1 },
                    label = { Text(stringResource(R.string.step)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Set total tracks option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setTotalTracks = !setTotalTracks }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = setTotalTracks,
                        onCheckedChange = { setTotalTracks = it }
                    )
                    Text(
                        text = stringResource(R.string.set_total_tracks),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Total tracks input
                if (setTotalTracks) {
                    OutlinedTextField(
                        value = totalTracks.toString(),
                        onValueChange = { totalTracks = it.toIntOrNull() ?: targetFilesCount },
                        label = { Text(stringResource(R.string.total_tracks)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview
                val preview = (0..minOf(2, targetFilesCount - 1)).joinToString(", ") { index ->
                    (startNumber + index * step).toString()
                } + if (targetFilesCount > 3) ", ..." else ""

                Text(
                    text = stringResource(R.string.number_preview, preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(startNumber, step, if (setTotalTracks) totalTracks else null)
                }
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
