package com.voxly.presentation.screens.filebrowser

import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.FileBrowserUiState
import com.voxly.presentation.viewmodel.FileBrowserViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * File browser screen for browsing and selecting audio files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = hiltViewModel(),
    onNavigateToMetadata: (String) -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    onNavigateToSettings: () -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
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
    val visibleFilesRaw = when (val state = uiState) {
        is FileBrowserUiState.Success -> if (openedDirectory != null) openedDirectoryFiles else state.files
        else -> emptyList()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOption by rememberSaveable { mutableStateOf(FileSortOption.NAME_ASC.name) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var isSortExpanded by rememberSaveable { mutableStateOf(false) }
    val visibleFiles = remember(visibleFilesRaw, searchQuery, sortOption) {
        applySearchAndSort(
            files = visibleFilesRaw,
            query = searchQuery,
            sortOption = FileSortOption.valueOf(sortOption)
        )
    }
    val currentListKey = openedDirectoryUri ?: "__global__"
    val initialScrollPosition = remember(currentListKey) {
        viewModel.getScrollPosition(currentListKey)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.index,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.offset
    )
    val coroutineScope = rememberCoroutineScope()

    // Scroll detection for hiding/showing top bar and bottom bar
    var isTopBarVisible by rememberSaveable { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    val canScrollToTop = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0

    // Reset previousScrollOffset when switching directories
    LaunchedEffect(currentListKey) {
        previousScrollOffset = listState.firstVisibleItemScrollOffset
        isTopBarVisible = true
        onBottomBarVisibilityChange(true)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .collect { currentOffset ->
                // Skip if no files to avoid unnecessary calculations
                if (visibleFiles.isEmpty()) return@collect
                
                val scrollDelta = currentOffset - previousScrollOffset
                // Hide when scrolling down (positive delta), show when scrolling up (negative delta)
                // Only trigger when delta is significant to avoid jitter
                if (scrollDelta > 10) {
                    isTopBarVisible = false
                    onBottomBarVisibilityChange(false)
                } else if (scrollDelta < -10) {
                    isTopBarVisible = true
                    onBottomBarVisibilityChange(true)
                }
                // Always show bars when at the top
                if (listState.firstVisibleItemIndex == 0 && currentOffset == 0) {
                    isTopBarVisible = true
                    onBottomBarVisibilityChange(true)
                }
                previousScrollOffset = currentOffset
            }
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
    }

    LaunchedEffect(hasReadPermission) {
        if (hasReadPermission) {
            viewModel.loadAudioFiles()
        } else {
            readPermissionLauncher.launch(readPermission)
        }
    }
    LaunchedEffect(currentListKey, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) -> Triple(currentListKey, index, offset) }
            .distinctUntilChanged()
            .collect { (key, index, offset) ->
                viewModel.saveScrollPosition(key, index, offset)
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
            } else {
                AnimatedVisibility(
                    visible = isTopBarVisible,
                    enter = slideInVertically(initialOffsetY = { -it }),
                    exit = slideOutVertically(targetOffsetY = { -it })
                ) {
                    if (openedDirectory != null) {
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
                                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search),
                                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                IconButton(onClick = { isSortExpanded = !isSortExpanded }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.file_sort_label),
                                        tint = if (isSortExpanded) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                IconButton(onClick = { viewModel.loadAudioFiles(forceRefresh = true) }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_files)
                                    )
                                }
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
                            ),
                            windowInsets = TopAppBarDefaults.windowInsets
                        )
                    } else {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search),
                                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                IconButton(onClick = { isSortExpanded = !isSortExpanded }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.file_sort_label),
                                        tint = if (isSortExpanded) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                IconButton(onClick = { viewModel.loadAudioFiles(forceRefresh = true) }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_files)
                                    )
                                }
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
                            ),
                            windowInsets = TopAppBarDefaults.windowInsets
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedFiles.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (canScrollToTop && visibleFiles.isNotEmpty()) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.back_to_top)
                            )
                        }
                    }

                }
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
                    onOpenDirectory = viewModel::openDirectory
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
                        val filesToShow = visibleFiles
                        if (filesToShow.isEmpty()) {
                            EmptyContent()
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SearchBar(
                                    isExpanded = isSearchExpanded,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it }
                                )
                                SortMenu(
                                    isExpanded = isSortExpanded,
                                    currentSortOption = FileSortOption.valueOf(sortOption),
                                    onSortOptionChange = { sortOption = it.name },
                                    onDismiss = { isSortExpanded = false }
                                )
                                AudioFileList(
                                    files = filesToShow,
                                    listState = listState,
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

private enum class FileSortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}

private fun applySearchAndSort(
    files: List<AudioFile>,
    query: String,
    sortOption: FileSortOption
): List<AudioFile> {
    val normalizedQuery = query.trim().lowercase()
    val filtered = if (normalizedQuery.isBlank()) {
        files
    } else {
        files.filter { audioFile ->
            val title = audioFile.metadata.title.orEmpty()
            val artist = audioFile.metadata.artist.orEmpty()
            val album = audioFile.metadata.album.orEmpty()
            listOf(audioFile.name, title, artist, album).any { text ->
                text.lowercase().contains(normalizedQuery)
            }
        }
    }

    return when (sortOption) {
        FileSortOption.NAME_ASC -> filtered.sortedBy { it.metadata.getDisplayTitle(it.name).lowercase() }
        FileSortOption.NAME_DESC -> filtered.sortedByDescending { it.metadata.getDisplayTitle(it.name).lowercase() }
        FileSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    isExpanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search field - only shown when expanded
        androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_selection)
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.file_search_hint)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(
    isExpanded: Boolean,
    currentSortOption: FileSortOption,
    onSortOptionChange: (FileSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            FileSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelResId())) },
                    leadingIcon = if (option == currentSortOption) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onSortOptionChange(option)
                        onDismiss()
                    }
                )
            }
        }
    }
}

private fun FileSortOption.labelResId(): Int = when (this) {
    FileSortOption.NAME_ASC -> R.string.file_sort_name_asc
    FileSortOption.NAME_DESC -> R.string.file_sort_name_desc
    FileSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    FileSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
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
                Icon(painter = appIconPainter(AppIcon.Equalizer), contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.replay_gain))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}

@Composable
private fun DirectoryOverviewContent(
    directories: List<SelectedDirectory>,
    directoryFiles: Map<String, List<AudioFile>>,
    onOpenDirectory: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.selected_directories_count, directories.size),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(directories, key = { it.uri }) { directory ->
            DirectoryItem(
                directory = directory,
                fileCount = directoryFiles[directory.uri]?.size ?: 0,
                onClick = { onOpenDirectory(directory.uri) },
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
                painter = appIconPainter(AppIcon.MusicNote),
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
                painter = appIconPainter(AppIcon.Error),
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedFiles: Set<String>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit
) {
    LazyColumn(
        state = listState,
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
    val context = LocalContext.current
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
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val mediaStoreBitmap = remember(audioFile.mediaStoreAlbumId) {
                    loadMediaStoreAlbumBitmap(context, audioFile.mediaStoreAlbumId)
                }
                if (audioFile.metadata.albumArt != null) {
                    val bitmap = remember(audioFile.metadata.albumArt) {
                        audioFile.metadata.albumArt?.let { bytes ->
                            decodeThumbnailBitmap(bytes)
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = appIconPainter(AppIcon.MusicNote),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                } else if (mediaStoreBitmap != null) {
                    Image(
                        bitmap = mediaStoreBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = appIconPainter(AppIcon.MusicNote),
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

private fun loadMediaStoreAlbumBitmap(
    context: android.content.Context,
    albumId: Long?
): android.graphics.Bitmap? {
    if (albumId == null || albumId <= 0L) return null
    val uri = Uri.withAppendedPath(
        Uri.parse("content://media/external/audio/albumart"),
        albumId.toString()
    )
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            decodeThumbnailBitmap(bytes)
        }
    }.getOrNull()
}

private fun decodeThumbnailBitmap(
    bytes: ByteArray,
    targetSizePx: Int = 96
): android.graphics.Bitmap? {
    val bounds = Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val rawWidth = bounds.outWidth.coerceAtLeast(1)
    val rawHeight = bounds.outHeight.coerceAtLeast(1)
    var inSampleSize = 1
    while ((rawWidth / inSampleSize) > targetSizePx || (rawHeight / inSampleSize) > targetSizePx) {
        inSampleSize *= 2
    }

    val opts = Options().apply {
        inSampleSize = inSampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

@Composable
private fun DirectoryItem(
    directory: SelectedDirectory,
    fileCount: Int,
    onClick: () -> Unit,
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
                painter = appIconPainter(AppIcon.Folder),
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
        }
    }
}
