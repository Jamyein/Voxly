package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Options for batch fix metadata.
 */
data class FixMetadataOptions(
    val autoTitleCase: Boolean,
    val removeExtraSpaces: Boolean,
    val fixTrackNumbers: Boolean,
    val removeEmptyTags: Boolean
)

/**
 * Dialog for configuring batch metadata fix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFixMetadataDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (FixMetadataOptions) -> Unit
) {
    var autoTitleCase by remember { mutableStateOf(true) }
    var removeExtraSpaces by remember { mutableStateOf(true) }
    var fixTrackNumbers by remember { mutableStateOf(true) }
    var removeEmptyTags by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.AutoFix), contentDescription = stringResource(R.string.cd_batch_fix)) },
        title = { Text(stringResource(R.string.batch_fix_metadata_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Options
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoTitleCase = !autoTitleCase }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = autoTitleCase,
                            onCheckedChange = { autoTitleCase = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_auto_title_case),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { removeExtraSpaces = !removeExtraSpaces }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = removeExtraSpaces,
                            onCheckedChange = { removeExtraSpaces = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_remove_extra_spaces),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fixTrackNumbers = !fixTrackNumbers }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fixTrackNumbers,
                            onCheckedChange = { fixTrackNumbers = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_track_numbers),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { removeEmptyTags = !removeEmptyTags }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = removeEmptyTags,
                            onCheckedChange = { removeEmptyTags = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_remove_empty_tags),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(FixMetadataOptions(autoTitleCase, removeExtraSpaces, fixTrackNumbers, removeEmptyTags))
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
