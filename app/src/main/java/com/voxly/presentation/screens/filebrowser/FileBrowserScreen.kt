package com.voxly.presentation.screens.filebrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.viewmodel.FileBrowserUiState
import com.voxly.presentation.viewmodel.FileBrowserViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory

/**
 * File browser screen for browsing and selecting audio files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = hiltViewModel(),
    onNavigateToMetadata: (String) -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val selectedDirectories by viewModel.selectedDirectories.collectAsState()
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val openedDirectoryUri by viewModel.openedDirectoryUri.collectAsState()
    val openedDirectory = selectedDirectories.firstOrNull { it.uri == openedDirectoryUri }
    val openedDirectoryFiles = openedDirectoryUri?.let { directoryFiles[it].orEmpty() }.orEmpty()
    val isDirectoryListLevel = selectedDirectories.isNotEmpty() && openedDirectory == null
    val visibleFiles = when (val state = uiState) {
        is FileBrowserUiState.Success -> if (openedDirectory != null) openedDirectoryFiles else state.files
        else -> emptyList()
    }
    val readPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPermission = granted
        if (granted) {
            viewModel.loadAudioFiles()
        }
    }

    // 文件夹选择器启动器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化 URI 权限
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addDirectory(it)
        }
    }

    LaunchedEffect(selectedDirectories, hasReadPermission) {
        if (selectedDirectories.isNotEmpty() || hasReadPermission) {
            viewModel.loadAudioFiles()
        } else {
            readPermissionLauncher.launch(readPermission)
        }
    }
    BackHandler(enabled = openedDirectory != null && selectedFiles.isEmpty()) {
        viewModel.closeOpenedDirectory()
    }

    Scaffold(
        topBar = {
            if (selectedFiles.isNotEmpty()) {
                SelectionTopBar(
                    selectedCount = selectedFiles.size,
                    onSelectAll = { viewModel.selectFilePaths(visibleFiles.map { it.path }) },
                    onClearSelection = { viewModel.clearSelection() },
                    onNavigateToReplayGain = {
                        onNavigateToReplayGain(viewModel.getSelectedFilePaths())
                    }
                )
            } else if (openedDirectory != null) {
                TopAppBar(
                    title = {
                        Text(
                            openedDirectory.path.substringAfterLast('/').ifBlank { openedDirectory.path }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::closeOpenedDirectory) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedFiles.isEmpty() && openedDirectory == null) {
                ExtendedFloatingActionButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_directory)) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isDirectoryListLevel) {
                DirectoryOverviewContent(
                    directories = selectedDirectories,
                    directoryFiles = directoryFiles,
                    onOpenDirectory = viewModel::openDirectory,
                    onRemoveDirectory = viewModel::removeDirectory,
                    onClearDirectories = viewModel::clearDirectories
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is FileBrowserUiState.Loading -> {
                        LoadingContent()
                    }
                    is FileBrowserUiState.Empty -> {
                        EmptyContent()
                    }
                    is FileBrowserUiState.Success -> {
                        val filesToShow = if (openedDirectory != null) {
                            openedDirectoryFiles
                        } else {
                            state.files
                        }
                        if (filesToShow.isEmpty()) {
                            EmptyContent()
                        } else {
                            AudioFileList(
                                files = filesToShow,
                                selectedFiles = selectedFiles,
                                onFileClick = { audioFile ->
                                    if (selectedFiles.isNotEmpty()) {
                                        viewModel.toggleFileSelection(audioFile.path)
                                    } else {
                                        onNavigateToMetadata(audioFile.path)
                                    }
                                },
                                onFileLongClick = { audioFile ->
                                    viewModel.toggleFileSelection(audioFile.path)
                                }
                            )
                        }
                    }
                    is FileBrowserUiState.Error -> {
                        ErrorContent(message = state.message)
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onNavigateToReplayGain: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all))
            }
            FilledTonalButton(
                onClick = onNavigateToReplayGain,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Equalizer, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.replay_gain))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

@Composable
private fun DirectoryOverviewContent(
    directories: List<SelectedDirectory>,
    directoryFiles: Map<String, List<AudioFile>>,
    onOpenDirectory: (String) -> Unit,
    onRemoveDirectory: (String) -> Unit,
    onClearDirectories: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.selected_directories_count, directories.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClearDirectories) {
            Text(stringResource(R.string.clear_directories))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(directories, key = { it.uri }) { directory ->
            DirectoryItem(
                directory = directory,
                fileCount = directoryFiles[directory.uri]?.size ?: 0,
                onClick = { onOpenDirectory(directory.uri) },
                onRemove = { onRemoveDirectory(directory.uri) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
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
            Text(stringResource(R.string.loading_audio_files))
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_audio_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_audio_files_or_select_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.error_loading_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudioFileList(
    files: List<AudioFile>,
    selectedFiles: Set<String>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files, key = { it.path }) { audioFile ->
            AudioFileItem(
                audioFile = audioFile,
                isSelected = audioFile.path in selectedFiles,
                onClick = { onFileClick(audioFile) },
                onLongClick = { onFileLongClick(audioFile) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AudioFileItem(
    audioFile: AudioFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (audioFile.metadata.albumArt != null) {
                    AsyncImage(
                        model = audioFile.metadata.albumArt,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(audioFile.metadata.artist ?: stringResource(R.string.unknown_artist))
                        audioFile.metadata.album?.let { append(" - $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(audioFile.format)
                        append(" • ")
                        append(audioFile.getFormattedDuration())
                        append(" • ")
                        append(audioFile.getFormattedSize())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DirectoryItem(
    directory: SelectedDirectory,
    fileCount: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = directory.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$fileCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.clear_selection)
                )
            }
        }
    }
}
