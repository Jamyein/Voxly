package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Dialog for batch renaming files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var pattern by remember { mutableStateOf("{artist} - {title}") }
    var startNumber by remember { mutableIntStateOf(1) }
    var expanded by remember { mutableStateOf(false) }

    val patterns = listOf(
        "{artist} - {title}" to stringResource(R.string.pattern_artist_title),
        "{title}" to stringResource(R.string.pattern_title),
        "{track}. {title}" to stringResource(R.string.pattern_track_title),
        "{artist} - {album} - {track}. {title}" to stringResource(R.string.pattern_full)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Rename), contentDescription = stringResource(R.string.cd_batch_rename)) },
        title = { Text(stringResource(R.string.batch_rename_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Pattern selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = patterns.find { it.first == pattern }?.second ?: pattern,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rename_pattern)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        patterns.forEach { (pat, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    pattern = pat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start number
                OutlinedTextField(
                    value = startNumber.toString(),
                    onValueChange = {
                        startNumber = it.toIntOrNull() ?: 1
                    },
                    label = { Text(stringResource(R.string.start_number)) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Help text
                Text(
                    text = stringResource(R.string.rename_pattern_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pattern, startNumber) }) {
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
