package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Options for batch online metadata fetch.
 */
data class OnlineMetadataOptions(
    val overwriteExisting: Boolean,
    val fetchAlbumArt: Boolean,
    val fetchLyrics: Boolean
)

/**
 * Dialog for configuring batch online metadata fetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOnlineMetadataDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (OnlineMetadataOptions) -> Unit
) {
    var overwriteExisting by remember { mutableStateOf(false) }
    var fetchAlbumArt by remember { mutableStateOf(true) }
    var fetchLyrics by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.CloudDownload), contentDescription = stringResource(R.string.cd_online_metadata)) },
        title = { Text(stringResource(R.string.batch_online_metadata_title)) },
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
                            .clickable { overwriteExisting = !overwriteExisting }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = overwriteExisting,
                            onCheckedChange = { overwriteExisting = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_overwrite_existing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fetchAlbumArt = !fetchAlbumArt }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fetchAlbumArt,
                            onCheckedChange = { fetchAlbumArt = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_fetch_album_art),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fetchLyrics = !fetchLyrics }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fetchLyrics,
                            onCheckedChange = { fetchLyrics = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_fetch_lyrics),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(OnlineMetadataOptions(overwriteExisting, fetchAlbumArt, fetchLyrics))
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
