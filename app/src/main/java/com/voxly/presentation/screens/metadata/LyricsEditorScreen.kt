package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.LyricsEditorUiState
import com.voxly.presentation.viewmodel.LyricsEditorViewModel

/**
 * Lyrics editor screen for viewing and editing song lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorScreen(
    filePath: String,
    trackName: String,
    artistName: String,
    viewModel: LyricsEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val editedText by viewModel.editedLyricsText.collectAsState()
    val isSynced by viewModel.isSynced.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()
    val showOnlineSearch by viewModel.showOnlineSearch.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_lyrics)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (hasChanges) {
                        IconButton(onClick = { viewModel.saveLyrics() }) {
                            Icon(
                                painter = appIconPainter(AppIcon.Save),
                                contentDescription = stringResource(R.string.dialog_save)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.searchOnlineLyrics() }) {
                        Icon(
                            painter = appIconPainter(AppIcon.CloudDownload),
                            contentDescription = stringResource(R.string.search_online)
                        )
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            if (editedText.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveLyrics() },
                    icon = { Icon(painter = appIconPainter(AppIcon.Save), contentDescription = null) },
                    text = { Text(stringResource(R.string.save_lyrics)) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is LyricsEditorUiState.Loading -> {
                    LoadingContent()
                }
                is LyricsEditorUiState.Saving -> {
                    SavingContent()
                }
                is LyricsEditorUiState.Error -> {
                    ErrorContent(message = state.message)
                }
                is LyricsEditorUiState.Success -> {
                    LyricsEditorContent(
                        trackName = trackName,
                        artistName = artistName,
                        lyrics = editedText,
                        isSynced = isSynced,
                        hasExistingLyrics = lyrics != null,
                        onLyricsChange = { viewModel.updateLyricsText(it) },
                        onSyncedChange = { viewModel.toggleSyncedMode(it) },
                        onRemoveLyrics = { viewModel.removeLyrics() },
                        onFormatLrc = { viewModel.formatAsLrc() }
                    )
                }
            }
        }
    }

    // Online Search Dialog
    if (showOnlineSearch) {
        OnlineLyricsSearchDialog(
            results = onlineResults,
            isSearching = isSearching,
            trackName = trackName,
            artistName = artistName,
            onDismiss = { viewModel.closeOnlineSearch() },
            onResultSelected = { viewModel.fetchOnlineLyrics(it.id) }
        )
    }

    // Discard Changes Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_unsaved_changes)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardChanges()
                    showDiscardDialog = false
                }) {
                    Text(stringResource(R.string.dialog_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

@Composable
private fun LyricsEditorContent(
    trackName: String,
    artistName: String,
    lyrics: String,
    isSynced: Boolean,
    hasExistingLyrics: Boolean,
    onLyricsChange: (String) -> Unit,
    onSyncedChange: (Boolean) -> Unit,
    onRemoveLyrics: () -> Unit,
    onFormatLrc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Song info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = trackName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Synced toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.synchronized_lyrics),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isSynced,
                    onCheckedChange = onSyncedChange
                )
            }

            // Actions
            Row {
                if (isSynced && lyrics.isNotBlank()) {
                    IconButton(onClick = onFormatLrc) {
                        Icon(
                            painter = appIconPainter(AppIcon.Schedule),
                            contentDescription = stringResource(R.string.auto_format_timestamps)
                        )
                    }
                }
                if (hasExistingLyrics) {
                    IconButton(onClick = onRemoveLyrics) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove_lyrics),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hint text
        Text(
            text = if (isSynced) {
                stringResource(R.string.lyrics_format_hint_synced)
            } else {
                stringResource(R.string.lyrics_format_hint_plain)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lyrics text field
        OutlinedTextField(
            value = lyrics,
            onValueChange = onLyricsChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    if (isSynced) {
                        stringResource(R.string.lyrics_placeholder_synced)
                    } else {
                        stringResource(R.string.lyrics_placeholder_plain)
                    }
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default,
                keyboardType = KeyboardType.Text
            ),
            minLines = 10
        )

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.loading_lyrics))
        }
    }
}

@Composable
private fun SavingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.saving_lyrics))
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = appIconPainter(AppIcon.Error),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineLyricsSearchDialog(
    results: List<com.voxly.domain.repository.OnlineLyricsResult>,
    isSearching: Boolean,
    trackName: String,
    artistName: String,
    onDismiss: () -> Unit,
    onResultSelected: (com.voxly.domain.repository.OnlineLyricsResult) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_online_lyrics)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.searching_for, trackName, artistName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_lyrics_found_online),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(results) { result ->
                            OnlineLyricsResultItem(
                                result = result,
                                onClick = { onResultSelected(result) }
                            )
                        }
                    }
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

@Composable
private fun OnlineLyricsResultItem(
    result: com.voxly.domain.repository.OnlineLyricsResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = result.trackName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = result.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.preview != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                if (result.hasSyncedLyrics) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.synced)) },
                        leadingIcon = {
                            Icon(
                                painter = appIconPainter(AppIcon.Schedule),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (result.isInstrumental) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.instrumental)) }
                    )
                }
            }
        }
    }
}
