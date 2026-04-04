package com.voxly.presentation.screens.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.decodeBitmapFromBytes
import com.voxly.presentation.ui.loadAlbumArtOriginalBitmap
import com.voxly.presentation.viewmodel.ConvertibleField
import com.voxly.presentation.viewmodel.MetadataEditorUiState
import java.io.ByteArrayOutputStream

/**
 * Dialog for confirming discard of unsaved changes.
 */
@Composable
fun DiscardChangesDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.dialog_unsaved_changes)) },
        text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.dialog_discard))
            }
        }
    )
}

/**
 * Dialog for re-authorizing SAF permissions.
 */
@Composable
fun ReauthorizeDialog(
    onDismiss: () -> Unit,
    onSelectDirectory: () -> Unit,
    onSelectFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Write permission required") },
        text = {
            Text("Select the current file or its parent directory to restore SAF write access.")
        },
        confirmButton = {
            TextButton(onClick = onSelectDirectory) {
                Text("Select directory")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSelectFile) {
                    Text("Select file")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    )
}

/**
 * Bottom sheet for album art options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumArtOptionsSheet(
    hasAlbumArt: Boolean,
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onFetchOnline: () -> Unit,
    onViewArt: () -> Unit,
    onRotateArt: () -> Unit,
    onRemoveArt: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            VerticalDragHandle(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
            )

            Text(
                text = stringResource(R.string.album_art_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.select_album_art)) },
                leadingContent = {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_select_album_art)
                    )
                },
                modifier = Modifier.clickable(onClick = onPickFromGallery)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.take_photo)) },
                leadingContent = {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.cd_take_photo)
                    )
                },
                modifier = Modifier.clickable(onClick = onTakePhoto)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.fetch_online_cover_art)) },
                leadingContent = {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = stringResource(R.string.cd_fetch_online_cover)
                    )
                },
                modifier = Modifier.clickable(onClick = onFetchOnline)
            )

            if (hasAlbumArt) {
                Spacer(modifier = Modifier.height(4.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.album_art_view)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = stringResource(R.string.cd_zoom_album_art)
                        )
                    },
                    modifier = Modifier.clickable(onClick = onViewArt)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.album_art_rotate)) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.cd_rotate_album_art)
                        )
                    },
                    modifier = Modifier.clickable(onClick = onRotateArt)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.remove_album_art)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_remove_album_art)
                        )
                    },
                    modifier = Modifier.clickable(onClick = onRemoveArt)
                )
            }
        }
    }
}

/**
 * Dialog for previewing album art.
 * Supports both edited album art bytes and file-based original art.
 */
@Composable
fun AlbumArtPreviewDialog(
    albumArt: ByteArray?,
    filePath: String? = null,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val previewBitmap = remember(albumArt?.contentHashCode(), filePath) {
        when {
            albumArt != null -> decodeAlbumArtPreview(albumArt, 2048)
            !filePath.isNullOrBlank() -> {
                val path = filePath!!
                runCatching<android.graphics.Bitmap?> {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        com.voxly.presentation.ui.loadAlbumArtOriginalBitmap(context, path, 2048)
                    }
                }.getOrNull()
            }
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.metadata_album_art)) },
        text = {
            if (previewBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(R.string.no_album_art))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        }
    )
}

/**
 * Conversion type for Chinese character conversion.
 */
enum class ConversionType {
    TO_SIMPLIFIED,
    TO_TRADITIONAL
}

/**
 * Dialog for selecting metadata fields to convert.
 */
@Composable
fun ConversionDialog(
    conversionType: ConversionType,
    onDismiss: () -> Unit,
    onConfirm: (Set<ConvertibleField>) -> Unit
) {
    var selectedFields by remember {
        mutableStateOf(
            setOf(
                ConvertibleField.TITLE,
                ConvertibleField.ARTIST,
                ConvertibleField.ALBUM,
                ConvertibleField.ALBUM_ARTIST,
                ConvertibleField.GENRE,
                ConvertibleField.COMPOSER,
                ConvertibleField.LYRICIST,
                ConvertibleField.COMMENT,
                ConvertibleField.LYRICS
            )
        )
    }

    val title = if (conversionType == ConversionType.TO_SIMPLIFIED) {
        stringResource(R.string.convert_to_simplified)
    } else {
        stringResource(R.string.convert_to_traditional)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_fields_to_convert),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { selectedFields = ConvertibleField.entries.toSet() }
                    ) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(
                        onClick = { selectedFields = emptySet() }
                    ) {
                        Text(stringResource(R.string.deselect_all))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(ConvertibleField.entries.toList()) { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFields = if (field in selectedFields) {
                                        selectedFields - field
                                    } else {
                                        selectedFields + field
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = field in selectedFields,
                                onCheckedChange = { checked ->
                                    selectedFields = if (checked) {
                                        selectedFields + field
                                    } else {
                                        selectedFields - field
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(field.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedFields) },
                enabled = selectedFields.isNotEmpty()
            ) {
                Text(stringResource(R.string.dialog_save))
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
 * Utility function to decode album art bytes to bitmap.
 */
fun decodeAlbumArtPreview(
    bytes: ByteArray,
    targetSizePx: Int = 1024
): android.graphics.Bitmap? {
    return decodeBitmapFromBytes(bytes, targetSizePx)
}

/**
 * Utility function to read bytes from URI.
 */
fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}

/**
 * Utility function to convert bitmap to JPEG bytes.
 */
fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray? {
    return runCatching {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), output)
        output.toByteArray()
    }.getOrNull()
}

/**
 * Utility function to rotate JPEG bytes.
 */
fun rotateJpegBytes(bytes: ByteArray, degrees: Float): ByteArray? {
    return runCatching {
        val src = decodeBitmapFromBytes(bytes)
            ?: throw IllegalArgumentException("Invalid image bytes")
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        bitmapToJpegBytes(rotated) ?: throw IllegalStateException("Failed to encode rotated image")
    }.getOrNull()
}
