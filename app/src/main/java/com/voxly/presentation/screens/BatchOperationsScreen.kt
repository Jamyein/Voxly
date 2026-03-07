package com.voxly.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.usecase.BatchStatus
import com.voxly.domain.usecase.MetadataField
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.BatchOperationsViewModel

/**
 * Batch operations screen for performing operations on multiple files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOperationsScreen(
    viewModel: BatchOperationsViewModel = hiltViewModel(),
    onNavigateToReplayGain: (List<String>) -> Unit
) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val operationComplete by viewModel.operationComplete.collectAsState()
    val error by viewModel.error.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showAlbumArtDialog by remember { mutableStateOf(false) }
    var selectedOperation by remember { mutableStateOf<BatchOperation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_operations_title)) },
                colors = TopAppBarDefaults.topAppBarColors(),
                actions = {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_selection))
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // File count card
            FileCountCard(fileCount = selectedFiles.size)

            Spacer(modifier = Modifier.height(16.dp))

            // Available operations
            if (selectedFiles.isEmpty()) {
                EmptySelectionContent()
            } else if (isProcessing) {
                ProcessingContent(
                    progress = batchProgress,
                    onCancel = { viewModel.cancelOperation() }
                )
            } else if (operationComplete) {
                CompletionContent(
                    progress = batchProgress,
                    onReset = { viewModel.resetOperation() }
                )
            } else {
                OperationsList(
                    onEditMetadata = { showEditDialog = true },
                    onReplayGain = { onNavigateToReplayGain(selectedFiles) },
                    onSetAlbumArt = { showAlbumArtDialog = true },
                    onRemoveAlbumArt = {
                        selectedOperation = BatchOperation.REMOVE_ALBUM_ART
                        viewModel.removeBatchAlbumArt()
                    }
                )
            }

            // Error snackbar
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(error = error!!, onDismiss = { viewModel.clearError() })
            }
        }
    }

    // Edit Metadata Dialog
    if (showEditDialog) {
        BatchEditDialog(
            onDismiss = { showEditDialog = false },
            onConfirm = { metadata, fields ->
                viewModel.startBatchEdit(metadata, fields)
                showEditDialog = false
            }
        )
    }

    // Album Art Dialog
    if (showAlbumArtDialog) {
        AlbumArtPickerDialog(
            onDismiss = { showAlbumArtDialog = false },
            onConfirm = { albumArtBytes ->
                viewModel.startBatchAlbumArt(albumArtBytes)
                showAlbumArtDialog = false
            }
        )
    }
}

@Composable
private fun FileCountCard(fileCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = appIconPainter(AppIcon.AudioFile),
                    contentDescription = null,
                    tint = if (fileCount > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (fileCount > 0) {
                        stringResource(R.string.files_selected, fileCount)
                    } else {
                        stringResource(R.string.no_files_selected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (fileCount > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (fileCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(fileCount.toString()) }
                    }
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySelectionContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = appIconPainter(AppIcon.FolderOpen),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.select_files_first),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.select_files_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun ProcessingContent(
    progress: com.voxly.domain.usecase.BatchProgress?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.processing),
            style = MaterialTheme.typography.titleMedium
        )

        if (progress != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.batch_progress, progress.currentFile, progress.totalFiles),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = progress.currentFilePath.substringAfterLast("/"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.dialog_cancel))
        }
    }
}

@Composable
private fun CompletionContent(
    progress: com.voxly.domain.usecase.BatchProgress?,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.operation_complete),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (progress != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    title = stringResource(R.string.success),
                    value = progress.successCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    title = stringResource(R.string.failed),
                    value = progress.failureCount.toString(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onReset) {
            Text(stringResource(R.string.perform_another_operation))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun OperationsList(
    onEditMetadata: () -> Unit,
    onReplayGain: () -> Unit,
    onSetAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit
) {
    Text(
        text = stringResource(R.string.available_operations),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationCard(
            icon = Icons.Default.Edit,
            title = stringResource(R.string.edit_metadata),
            description = stringResource(R.string.operation_edit_metadata_description),
            onClick = onEditMetadata
        )

        OperationCard(
            icon = AppIcon.Equalizer,
            title = stringResource(R.string.scan_replay_gain),
            description = stringResource(R.string.operation_scan_replay_gain_description),
            onClick = onReplayGain
        )

        OperationCard(
            icon = AppIcon.Image,
            title = stringResource(R.string.set_album_art),
            description = stringResource(R.string.operation_set_album_art_description),
            onClick = onSetAlbumArt
        )

        OperationCard(
            icon = AppIcon.HideImage,
            title = stringResource(R.string.remove_album_art),
            description = stringResource(R.string.operation_remove_album_art_description),
            onClick = onRemoveAlbumArt,
            isDestructive = true
        )
    }
}

@Composable
private fun OperationCard(
    icon: Any,
    title: String,
    description: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                is AppIcon -> Icon(
                    painter = appIconPainter(icon),
                    contentDescription = null,
                    tint = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                painter = appIconPainter(AppIcon.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = appIconPainter(AppIcon.Error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = error,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun BatchEditDialog(
    onDismiss: () -> Unit,
    onConfirm: (com.voxly.domain.model.AudioMetadata, Set<MetadataField>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    var titleEnabled by remember { mutableStateOf(false) }
    var artistEnabled by remember { mutableStateOf(false) }
    var albumEnabled by remember { mutableStateOf(false) }
    var albumArtistEnabled by remember { mutableStateOf(false) }
    var yearEnabled by remember { mutableStateOf(false) }
    var genreEnabled by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.batch_edit_metadata_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = titleEnabled,
                        onCheckedChange = { titleEnabled = it }
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; titleEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_title)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Artist field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = artistEnabled,
                        onCheckedChange = { artistEnabled = it }
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it; artistEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_artist)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Album field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = albumEnabled,
                        onCheckedChange = { albumEnabled = it }
                    )
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it; albumEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_album)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Album Artist field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = albumArtistEnabled,
                        onCheckedChange = { albumArtistEnabled = it }
                    )
                    OutlinedTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it; albumArtistEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_album_artist)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Year field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = yearEnabled,
                        onCheckedChange = { yearEnabled = it }
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it; yearEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_year)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Genre field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = genreEnabled,
                        onCheckedChange = { genreEnabled = it }
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it; genreEnabled = it.isNotEmpty() },
                        label = { Text(stringResource(R.string.metadata_genre)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val metadata = com.voxly.domain.model.AudioMetadata(
                        title = title.takeIf { it.isNotEmpty() },
                        artist = artist.takeIf { it.isNotEmpty() },
                        album = album.takeIf { it.isNotEmpty() },
                        albumArtist = albumArtist.takeIf { it.isNotEmpty() },
                        year = year.takeIf { it.isNotEmpty() },
                        genre = genre.takeIf { it.isNotEmpty() }
                    )
                    val enabledFields = buildSet {
                        if (titleEnabled) add(MetadataField.TITLE)
                        if (artistEnabled) add(MetadataField.ARTIST)
                        if (albumEnabled) add(MetadataField.ALBUM)
                        if (albumArtistEnabled) add(MetadataField.ALBUM_ARTIST)
                        if (yearEnabled) add(MetadataField.YEAR)
                        if (genreEnabled) add(MetadataField.GENRE)
                    }
                    if (enabledFields.isNotEmpty()) {
                        onConfirm(metadata, enabledFields)
                        onDismiss()
                    }
                }
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

@Composable
private fun AlbumArtPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.album_art_picker_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedImageBytes != null) {
                    // Show selected image preview placeholder
                    Surface(
                        modifier = Modifier.size(150.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.album_art_selected),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = stringResource(R.string.album_art_picker_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                // Note: Image picker requires ActivityResultLauncher injection
                // For full implementation, pass galleryLauncher from parent screen
                Text(
                    text = stringResource(R.string.album_art_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = selectedImageBytes != null
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
 * Enum representing batch operation types.
 */
enum class BatchOperation {
    EDIT_METADATA,
    REPLAY_GAIN,
    SET_ALBUM_ART,
    REMOVE_ALBUM_ART
}
