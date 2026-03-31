package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.DirFileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import kotlinx.coroutines.launch

/**
 * Adaptive DirectoryContent screen using Material3 ListDetailPaneScaffold.
 *
 * This is true adaptive design - Material3 automatically manages:
 * - Small screens: Single pane with full-screen navigation
 * - Medium screens: Dual pane with adjustable ratio
 * - Large screens: Dual pane with 40:60 split
 *
 * No conditional logic needed - Material3 handles all screen sizes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DirectoryContentAdaptiveScreen(
    directoryUri: String,
    directoryName: String,
    onNavigateBack: () -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // Material3 Adaptive Navigator - automatically handles all screen sizes
    val navigator = rememberListDetailPaneScaffoldNavigator<AudioFile>()

    // Load directory files
    LaunchedEffect(directoryUri) {
        if (directoryUri.isNotEmpty()) {
            viewModel.loadFromDirectory(android.net.Uri.parse(directoryUri))
        }
    }

    // State collections
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadingDirectories by viewModel.directoryLoadingState.collectAsState()

    val isDirectoryLoading = remember(directoryUri, loadingDirectories) {
        directoryUri in loadingDirectories
    }

    val files = remember(directoryUri, directoryFiles) {
        directoryFiles[directoryUri] ?: emptyList()
    }

    // Search and sort
    var searchQuery by remember { mutableStateOf("") }
    var isSortExpanded by remember { mutableStateOf(false) }
    val sortOption by viewModel.directoryFileSortOption.collectAsState(initial = DirFileSortOption.NAME_ASC.name)
    val currentSortOption = remember(sortOption) {
        try {
            DirFileSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            DirFileSortOption.NAME_ASC
        }
    }

    // Apply search and sort
    val displayedFiles = remember(files, searchQuery, currentSortOption) {
        applySearchAndSort(files, searchQuery, currentSortOption)
    }

    // List pane state
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isSelectionMode = selectedFiles.isNotEmpty()
    val canScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Material3 ListDetailPaneScaffold - handles all screen sizes automatically
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // List pane TopAppBar
                    TopAppBar(
                        title = {
                            if (isSelectionMode) {
                                Text("${selectedFiles.size} selected")
                            } else {
                                Text(
                                    text = directoryName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isSelectionMode) {
                                    viewModel.clearSelection()
                                } else {
                                    onNavigateBack()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            if (isSelectionMode) {
                                // Select/Deselect all
                                IconButton(onClick = {
                                    if (selectedFiles.size == files.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll()
                                    }
                                }) {
                                    Text(
                                        if (selectedFiles.size == files.size) {
                                            stringResource(R.string.deselect_all)
                                        } else {
                                            stringResource(R.string.select_all)
                                        }
                                    )
                                }
                            } else {
                                // Search and Sort buttons
                                IconButton(onClick = { /* Show search */ }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                }
                                SortMenuButton(
                                    expanded = isSortExpanded,
                                    onExpandedChange = { isSortExpanded = it },
                                    currentSortOption = currentSortOption,
                                    options = DirFileSortOption.entries,
                                    optionLabelResId = { it.labelResId() },
                                    contentDescription = "Sort",
                                    onSortOptionChange = { viewModel.setDirectoryFileSortOption(it.name) }
                                )
                                IconButton(onClick = { viewModel.refresh() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_files)
                                    )
                                }
                            }
                        }
                    )

                    // File list content
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (files.isEmpty() && isDirectoryLoading) {
                                LoadingContent()
                            } else if (files.isEmpty()) {
                                DirectoryEmptyContent(
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AudioFileListWithIndexer(
                                    files = displayedFiles,
                                    listState = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    selectedFiles = selectedFiles,
                                    onFileClick = { audioFile ->
                                        if (isSelectionMode) {
                                            viewModel.toggleFileSelection(audioFile.path)
                                        } else {
                                            // Navigate to detail - use coroutine for suspend function
                                            coroutineScope.launch {
                                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                                            }
                                        }
                                    },
                                    onFileLongClick = { audioFile ->
                                        viewModel.toggleFileSelection(audioFile.path)
                                    },
                                    onEditFileMetadata = { /* Not used in adaptive mode */ },
                                    onRenameFile = { /* Not used in adaptive mode */ },
                                    onDeleteFile = { /* Not used in adaptive mode */ },
                                    onFetchOnlineMetadata = { /* Not used in adaptive mode */ },
                                    onFixMetadata = { /* Not used in adaptive mode */ },
                                    bottomPadding = 16.dp
                                )
                            }

                            // Back to top FAB
                            if (canScrollToTop && displayedFiles.isNotEmpty()) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Back to top"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val currentFile = navigator.currentDestination?.contentKey
                if (currentFile != null) {
                    // Create navKey for MetadataEditor
                    val navKey = MetadataEditor(
                        filePath = currentFile.path,
                        coverTag = createAlbumArtSharedElementKey(currentFile.path)
                    )
                    // Create ViewModel with proper factory
                    val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                        key = currentFile.path,
                        creationCallback = { factory -> factory.create(navKey) }
                    )
                    AdaptiveMetadataEditorContainer(
                        filePath = currentFile.path,
                        viewModel = metadataViewModel,
                        coverTag = createAlbumArtSharedElementKey(currentFile.path),
                        sharedElementKey = null,
                        onNavigateBack = {
                            // Use coroutine for suspend function
                            coroutineScope.launch {
                                navigator.navigateBack()
                            }
                        },
                        onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                        onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                        onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
                        onNavigateToLyricsSelector = onNavigateToLyricsSelector
                    )
                } else {
                    EmptyDetailPane()
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Applies search and sort to directory files.
 */
private fun applySearchAndSort(
    files: List<AudioFile>,
    query: String,
    sortOption: DirFileSortOption
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

    return applySort(filtered, sortOption)
}

/**
 * Applies sorting to audio files.
 */
private fun applySort(files: List<AudioFile>, sortOption: DirFileSortOption): List<AudioFile> {
    return when (sortOption) {
        DirFileSortOption.NAME_ASC -> files.sortedWith(
            compareBy { com.voxly.core.util.SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) }
        )
        DirFileSortOption.NAME_DESC -> files.sortedWith(
            compareByDescending { com.voxly.core.util.SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) }
        )
        DirFileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        DirFileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
    }
}

private fun DirFileSortOption.labelResId(): Int = when (this) {
    DirFileSortOption.NAME_ASC -> R.string.file_sort_name_asc
    DirFileSortOption.NAME_DESC -> R.string.file_sort_name_desc
    DirFileSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    DirFileSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
}
