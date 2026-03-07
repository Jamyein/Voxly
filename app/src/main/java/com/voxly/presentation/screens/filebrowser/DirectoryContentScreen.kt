package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.viewmodel.FileBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryContentScreen(
    directoryUri: String,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Get directory files from the ViewModel
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val selectedDirectories by viewModel.selectedDirectories.collectAsState()

    // Find the directory info
    val directory = remember(directoryUri, selectedDirectories) {
        selectedDirectories.firstOrNull { it.uri == directoryUri }
    }

    // Get files for this directory
    val files = remember(directoryUri, directoryFiles) {
        directoryFiles[directoryUri].orEmpty()
    }

    val listState = rememberLazyListState()

    // Get directory name from path
    val directoryName = remember(directory) {
        directory?.path?.substringAfterLast("/") ?: directory?.path?.substringAfterLast(":") ?: "Unknown"
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = directoryName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        if (files.isEmpty()) {
            EmptyDirectoryContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                items(files, key = { it.path }) { audioFile ->
                    SimpleAudioFileItem(
                        audioFile = audioFile,
                        isSelected = false,
                        onClick = { onNavigateToMetadata(audioFile.path) },
                        onLongClick = { /* No-op for this screen */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDirectoryContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_audio_files_in_directory),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
