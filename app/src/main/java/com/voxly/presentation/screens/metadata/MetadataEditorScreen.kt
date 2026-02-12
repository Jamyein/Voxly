package com.voxly.presentation.screens.metadata

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.MetadataEditorUiState
import com.voxly.presentation.viewmodel.MetadataEditorViewModel

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
    val unknownArtist = stringResource(R.string.unknown_artist)

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Handle save result
    LaunchedEffect(saveResult) {
        if (saveResult is com.voxly.presentation.viewmodel.SaveResult.Success) {
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_metadata)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                            Text(stringResource(R.string.dialog_save))
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToOnlineMetadata,
                icon = { Icon(painter = appIconPainter(AppIcon.CloudDownload), contentDescription = null) },
                text = { Text(stringResource(R.string.fetch_online_metadata)) }
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
                            Text(stringResource(R.string.saving_metadata))
                        }
                    }
                }
                is MetadataEditorUiState.Success -> {
                    MetadataForm(
                        metadata = state.editedMetadata,
                        audioFile = state.audioFile,
                        onTitleChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.TITLE, it) },
                        onArtistChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ARTIST, it) },
                        onAlbumChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ALBUM, it) },
                        onAlbumArtistChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ALBUM_ARTIST, it) },
                        onYearChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.YEAR, it) },
                        onGenreChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.GENRE, it) },
                        onComposerChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.COMPOSER, it) },
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
                                state.editedMetadata.artist ?: unknownArtist
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
                                painter = appIconPainter(AppIcon.Error),
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
            title = { Text(stringResource(R.string.dialog_unsaved_changes)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardChanges()
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.dialog_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun MetadataForm(
    metadata: com.voxly.domain.model.AudioMetadata,
    audioFile: com.voxly.domain.model.AudioFile,
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
        SectionTitle(stringResource(R.string.basic_information))

        OutlinedTextField(
            value = metadata.title ?: "",
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.metadata_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.artist ?: "",
            onValueChange = onArtistChange,
            label = { Text(stringResource(R.string.metadata_artist)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.album ?: "",
            onValueChange = onAlbumChange,
            label = { Text(stringResource(R.string.metadata_album)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.albumArtist ?: "",
            onValueChange = onAlbumArtistChange,
            label = { Text(stringResource(R.string.metadata_album_artist)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Track Information
        SectionTitle(stringResource(R.string.track_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.trackNumber?.toString() ?: "",
                onValueChange = { onTrackNumberChange(it, metadata.totalTracks?.toString() ?: "") },
                label = { Text(stringResource(R.string.label_track)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalTracks?.toString() ?: "",
                onValueChange = { onTrackNumberChange(metadata.trackNumber?.toString() ?: "", it) },
                label = { Text(stringResource(R.string.label_total)) },
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
                label = { Text(stringResource(R.string.label_disc)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalDiscs?.toString() ?: "",
                onValueChange = { onDiscNumberChange(metadata.discNumber?.toString() ?: "", it) },
                label = { Text(stringResource(R.string.label_total)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Information
        SectionTitle(stringResource(R.string.additional_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.year ?: "",
                onValueChange = onYearChange,
                label = { Text(stringResource(R.string.metadata_year)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.genre ?: "",
                onValueChange = onGenreChange,
                label = { Text(stringResource(R.string.metadata_genre)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.composer ?: "",
            onValueChange = onComposerChange,
            label = { Text(stringResource(R.string.metadata_composer)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics Section
        SectionTitle(stringResource(R.string.lyrics_section_title))

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
                        painter = appIconPainter(AppIcon.MusicNote),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.edit_lyrics),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (metadata.lyrics.isNullOrBlank()) {
                                stringResource(R.string.no_lyrics_added)
                            } else {
                                stringResource(R.string.lyrics_available)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    painter = appIconPainter(AppIcon.ChevronRight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File Information (read-only)
        SectionTitle(stringResource(R.string.file_information))

        FileInfoRow(stringResource(R.string.file_info_format), audioFile.format)
        FileInfoRow(stringResource(R.string.metadata_bitrate), "${audioFile.bitrate} kbps")
        FileInfoRow(stringResource(R.string.metadata_sample_rate), "${audioFile.sampleRate} Hz")
        FileInfoRow(stringResource(R.string.file_info_channels), audioFile.channels.toString())
        FileInfoRow(stringResource(R.string.metadata_duration), audioFile.getFormattedDuration())
        FileInfoRow(stringResource(R.string.file_info_size), audioFile.getFormattedSize())

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
                val bitmap = remember(albumArt) {
                    BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = appIconPainter(AppIcon.Image),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.no_album_art),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = appIconPainter(AppIcon.Image),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.no_album_art),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
