package com.mp3tag.android.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mp3tag.android.domain.usecase.BatchStatus
import com.mp3tag.android.domain.usecase.MetadataField
import com.mp3tag.android.presentation.viewmodel.BatchOperationsViewModel

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
                title = { Text("Batch Operations") },
                actions = {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    onCancel = { /* TODO: Implement cancellation */ }
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
        colors = CardDefaults.cardColors(
            containerColor = if (fileCount > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
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
                    Icons.Default.AudioFile,
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
                        "$fileCount files selected"
                    } else {
                        "No files selected"
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
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Select Files First",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Go to the Files tab and select multiple files to perform batch operations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun ProcessingContent(
    progress: com.mp3tag.android.domain.usecase.BatchProgress?,
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
            text = "Processing...",
            style = MaterialTheme.typography.titleMedium
        )

        if (progress != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${progress.currentFile} of ${progress.totalFiles}",
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
            Text("Cancel")
        }
    }
}

@Composable
private fun CompletionContent(
    progress: com.mp3tag.android.domain.usecase.BatchProgress?,
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
            text = "Operation Complete!",
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
                    title = "Success",
                    value = progress.successCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    title = "Failed",
                    value = progress.failureCount.toString(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onReset) {
            Text("Perform Another Operation")
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
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
        text = "Available Operations",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationCard(
            icon = Icons.Default.Edit,
            title = "Edit Metadata",
            description = "Apply the same metadata fields to all selected files",
            onClick = onEditMetadata
        )

        OperationCard(
            icon = Icons.Default.Equalizer,
            title = "Scan ReplayGain",
            description = "Calculate and apply ReplayGain values for consistent volume",
            onClick = onReplayGain
        )

        OperationCard(
            icon = Icons.Default.Image,
            title = "Set Album Art",
            description = "Apply the same album artwork to all selected files",
            onClick = onSetAlbumArt
        )

        OperationCard(
            icon = Icons.Default.HideImage,
            title = "Remove Album Art",
            description = "Remove album artwork from all selected files",
            onClick = onRemoveAlbumArt,
            isDestructive = true
        )
    }
}

@Composable
private fun OperationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            Icon(
                icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

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
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
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
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun BatchEditDialog(
    onDismiss: () -> Unit,
    onConfirm: (com.mp3tag.android.domain.model.AudioMetadata, Set<MetadataField>) -> Unit
) {
    // TODO: Implement batch edit dialog with field selection
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Edit Metadata") },
        text = { Text("Batch metadata editor will be implemented here") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AlbumArtPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    // TODO: Implement album art picker dialog
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Album Art") },
        text = { Text("Album art picker will be implemented here") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
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
