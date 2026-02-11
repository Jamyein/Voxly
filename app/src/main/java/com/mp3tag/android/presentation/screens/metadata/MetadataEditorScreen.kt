package com.mp3tag.android.presentation.screens.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mp3tag.android.presentation.viewmodel.MetadataEditorUiState
import com.mp3tag.android.presentation.viewmodel.MetadataEditorViewModel

/**
 * Metadata editor screen for viewing and editing audio file metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    filePath: String,
    viewModel: MetadataEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToLyrics: (trackName: String, artistName: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val editedMetadata by viewModel.editedMetadata.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Handle save result
    LaunchedEffect(saveResult) {
        if (saveResult is com.mp3tag.android.presentation.viewmodel.SaveResult.Success) {
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Metadata") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveMetadata() },
                        enabled = hasUnsavedChanges && uiState !is MetadataEditorUiState.Saving
                    ) {
                        if (uiState is MetadataEditorUiState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToOnlineMetadata,
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                text = { Text("Fetch Online") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is MetadataEditorUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MetadataEditorUiState.Saving -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Saving metadata...")
                        }
                    }
                }
                is MetadataEditorUiState.Success -> {
                    MetadataForm(
                        metadata = state.editedMetadata,
                        audioFile = state.audioFile,
                        onTitleChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.TITLE, it) },
                        onArtistChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.ARTIST, it) },
                        onAlbumChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.ALBUM, it) },
                        onAlbumArtistChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.ALBUM_ARTIST, it) },
                        onYearChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.YEAR, it) },
                        onGenreChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.GENRE, it) },
                        onComposerChange = { viewModel.updateMetadataField(com.mp3tag.android.presentation.viewmodel.MetadataField.COMPOSER, it) },
                        onTrackNumberChange = { track, total ->
                            viewModel.updateTrackNumber(
                                track.toIntOrNull(),
                                total.toIntOrNull()
                            )
                        },
                        onDiscNumberChange = { disc, total ->
                            viewModel.updateDiscNumber(
                                disc.toIntOrNull(),
                                total.toIntOrNull()
                            )
                        },
                        onAlbumArtChange = { viewModel.updateAlbumArt(it) },
                        onEditLyrics = {
                            onNavigateToLyrics(
                                state.editedMetadata.title ?: state.audioFile.name,
                                state.editedMetadata.artist ?: "Unknown Artist"
                            )
                        }
                    )
                }
                is MetadataEditorUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardChanges()
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetadataForm(
    metadata: com.mp3tag.android.domain.model.AudioMetadata,
    audioFile: com.mp3tag.android.domain.model.AudioFile,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onTrackNumberChange: (String, String) -> Unit,
    onDiscNumberChange: (String, String) -> Unit,
    onAlbumArtChange: (ByteArray?) -> Unit,
    onEditLyrics: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Album Art Section
        AlbumArtSection(
            albumArt = metadata.albumArt,
            onAlbumArtChange = onAlbumArtChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Information
        SectionTitle("Basic Information")

        OutlinedTextField(
            value = metadata.title ?: "",
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.artist ?: "",
            onValueChange = onArtistChange,
            label = { Text("Artist") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.album ?: "",
            onValueChange = onAlbumChange,
            label = { Text("Album") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.albumArtist ?: "",
            onValueChange = onAlbumArtistChange,
            label = { Text("Album Artist") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Track Information
        SectionTitle("Track Information")

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.trackNumber?.toString() ?: "",
                onValueChange = { onTrackNumberChange(it, metadata.totalTracks?.toString() ?: "") },
                label = { Text("Track") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalTracks?.toString() ?: "",
                onValueChange = { onTrackNumberChange(metadata.trackNumber?.toString() ?: "", it) },
                label = { Text("Total") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.discNumber?.toString() ?: "",
                onValueChange = { onDiscNumberChange(it, metadata.totalDiscs?.toString() ?: "") },
                label = { Text("Disc") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalDiscs?.toString() ?: "",
                onValueChange = { onDiscNumberChange(metadata.discNumber?.toString() ?: "", it) },
                label = { Text("Total") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Information
        SectionTitle("Additional Information")

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.year ?: "",
                onValueChange = onYearChange,
                label = { Text("Year") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.genre ?: "",
                onValueChange = onGenreChange,
                label = { Text("Genre") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.composer ?: "",
            onValueChange = onComposerChange,
            label = { Text("Composer") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics Section
        SectionTitle("Lyrics")

        OutlinedCard(
            onClick = onEditLyrics,
            modifier = Modifier.fillMaxWidth()
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
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Edit Lyrics",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (metadata.lyrics.isNullOrBlank()) {
                                "No lyrics added"
                            } else {
                                "Lyrics available"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File Information (read-only)
        SectionTitle("File Information")

        FileInfoRow("Format", audioFile.format)
        FileInfoRow("Bitrate", "${audioFile.bitrate} kbps")
        FileInfoRow("Sample Rate", "${audioFile.sampleRate} Hz")
        FileInfoRow("Channels", audioFile.channels.toString())
        FileInfoRow("Duration", audioFile.getFormattedDuration())
        FileInfoRow("Size", audioFile.getFormattedSize())

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AlbumArtSection(
    albumArt: ByteArray?,
    onAlbumArtChange: (ByteArray?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (albumArt != null) {
                AsyncImage(
                    model = albumArt,
                    contentDescription = "Album art",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No album art",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
