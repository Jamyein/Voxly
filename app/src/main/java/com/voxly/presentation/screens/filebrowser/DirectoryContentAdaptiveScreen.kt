package com.voxly.presentation.screens.filebrowser

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.DirFileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.SearchBottomSheet
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.screens.filebrowser.LoadingContent
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Adaptive DirectoryContent screen using Material3 ListDetailPaneScaffold.
 *
 * This is true adaptive design - Material3 automatically manages:
 * - Small screens: Single pane with full-screen navigation (delegates to NavHost)
 * - Medium screens: Dual pane with adjustable ratio
 * - Large screens: Dual pane with 40:60 split
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectoryContentAdaptiveScreen(
    directoryUri: String,
    directoryName: String,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
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

    // Track file switch counter for proper ViewModel recreation
    var fileSwitchCounter by remember { mutableIntStateOf(0) }

    // Determine if we're in single-pane mode (small screens)
    val isSinglePane = navigator.scaffoldValue.primary == PaneAdaptedValue.Hidden

    val canCloseDetailPane = !isSinglePane && navigator.currentDestination != null

    // Load directory files
    LaunchedEffect(directoryUri) {
        if (directoryUri.isNotEmpty()) {
            viewModel.loadFromDirectory(android.net.Uri.parse(directoryUri))
        }
    }

    // State collections
    val directoryFiles by viewModel.directoryFiles.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingDirectories by viewModel.directoryLoadingState.collectAsStateWithLifecycle()

    // Get saved scroll position for this directory
    val savedScrollPosition = remember(directoryUri) {
        viewModel.getScrollPosition("directory_$directoryUri")
    }

    val isDirectoryLoading = remember(directoryUri, loadingDirectories) {
        directoryUri in loadingDirectories
    }

    val files = remember(directoryUri, directoryFiles) {
        directoryFiles[directoryUri] ?: emptyList()
    }

    // Search and sort
    var showSearchSheet by remember { mutableStateOf(false) }
    var isSortExpanded by remember { mutableStateOf(false) }
    val sortOption by viewModel.directoryFileSortOption.collectAsStateWithLifecycle(initialValue = DirFileSortOption.NAME_ASC.name)
    val currentSortOption = remember(sortOption) {
        try {
            DirFileSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            DirFileSortOption.NAME_ASC
        }
    }

    // Apply sort only (search handled by SearchBottomSheet)
    val displayedFiles = remember(files, currentSortOption) {
        applySort(files, currentSortOption)
    }

    // List pane state - restore saved scroll position
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollPosition.index,
        initialFirstVisibleItemScrollOffset = savedScrollPosition.offset
    )
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(isSinglePane, isSelectionMode) {
        if (isSinglePane && isSelectionMode) {
            viewModel.clearSelection()
        }
    }

    val canScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Save scroll position when leaving the screen
    DisposableEffect(directoryUri) {
        onDispose {
            viewModel.saveScrollPosition(
                "directory_$directoryUri",
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    PredictiveBackHandler(enabled = isSelectionMode || canCloseDetailPane) { progress ->
        try {
            progress.collect { }
            when {
                isSelectionMode -> viewModel.clearSelection()
                canCloseDetailPane -> {
                    coroutineScope.launch {
                        navigator.navigateBack()
                    }
                }
            }
        } catch (e: CancellationException) {
            // Gesture cancelled - no action
        }
    }

    // FloatingToolbar scroll behavior for batch operations
    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    // Dialog states for batch operations
    var showBatchOperationsMenu by remember { mutableStateOf(false) }
    var showUnifiedFieldDialog by remember { mutableStateOf(false) }
    var showReplaceTextDialog by remember { mutableStateOf(false) }
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var showRenameFilesDialog by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showOnlineMetadataDialog by remember { mutableStateOf(false) }

    // Material3 ListDetailPaneScaffold - handles all screen sizes automatically
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.nestedScroll(floatingToolbarScrollBehavior),
        listPane = {
            AnimatedPane(
                modifier = Modifier.fillMaxSize()
            ) {
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
                                } else if (!isSinglePane && navigator.currentDestination != null) {
                                    // Multi-pane with detail showing: go back to list
                                    coroutineScope.launch {
                                        navigator.navigateBack()
                                    }
                                } else {
                                    // Single-pane or no detail: exit directory
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
                                IconButton(onClick = { showSearchSheet = true }) {
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

                    // File list content with PullToRefresh
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val pullToRefreshState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refresh() },
                                state = pullToRefreshState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {
                                    LoadingIndicator(
                                        state = pullToRefreshState,
                                        isRefreshing = isRefreshing,
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    )
                                }
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
                                                } else if (isSinglePane) {
                                                    onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path))
                                                } else {
                                                    coroutineScope.launch {
                                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                                                    }
                                                }
                                            },
                                            onFileLongClick = { audioFile ->
                                                viewModel.toggleFileSelection(audioFile.path)
                                            },
                                            onEditFileMetadata = { },
                                            onRenameFile = { },
                                            onDeleteFile = { },
                                            onFetchOnlineMetadata = { },
                                            onFixMetadata = { },
                                            bottomPadding = if (isSelectionMode) 80.dp else 16.dp
                                        )
                                    }
                                }
                            }
                        }

                                // Batch Operations FloatingToolbar (only in selection mode)
                                if (isSelectionMode) {
                                    BatchOperationsToolbar(
                                        expanded = true,
                                        scrollBehavior = floatingToolbarScrollBehavior,
                                        onOnlineMetadata = { showOnlineMetadataDialog = true },
                                        onUnifiedField = { showUnifiedFieldDialog = true },
                                        onReplaceText = { showReplaceTextDialog = true },
                                        onAutoNumber = { showAutoNumberDialog = true },
                                        onRenameFiles = { showRenameFilesDialog = true },
                                        onFixMetadata = { showFixMetadataDialog = true },
                                        onReplayGain = { onNavigateToReplayGain(selectedFiles.toList()) }
                                    )
                                }

                        // Back to top FAB
                        if (canScrollToTop && displayedFiles.isNotEmpty() && !isSelectionMode) {
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
                        sharedElementKey = createAlbumArtSharedElementKey(currentFile.path),
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
        }
    )

    // Batch Operation Dialogs
    if (showBatchOperationsMenu) {
        BatchOperationsMenuDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showBatchOperationsMenu = false },
            onOnlineMetadata = {
                showBatchOperationsMenu = false
                showOnlineMetadataDialog = true
            },
            onUnifiedField = {
                showBatchOperationsMenu = false
                showUnifiedFieldDialog = true
            },
            onReplaceText = {
                showBatchOperationsMenu = false
                showReplaceTextDialog = true
            },
            onAutoNumber = {
                showBatchOperationsMenu = false
                showAutoNumberDialog = true
            },
            onRenameFiles = {
                showBatchOperationsMenu = false
                showRenameFilesDialog = true
            },
            onFixMetadata = {
                showBatchOperationsMenu = false
                showFixMetadataDialog = true
            }
        )
    }

    if (showOnlineMetadataDialog) {
        BatchOnlineMetadataDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showOnlineMetadataDialog = false },
            onConfirm = { /* TODO: Implement batch online metadata */ }
        )
    }

    if (showUnifiedFieldDialog) {
        UnifiedFieldDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showUnifiedFieldDialog = false },
            onConfirm = { field, value ->
                showUnifiedFieldDialog = false
                // TODO: Implement batch unified field edit
            }
        )
    }

    if (showReplaceTextDialog) {
        ReplaceTextDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showReplaceTextDialog = false },
            onConfirm = { field, searchText, replaceText, useRegex ->
                showReplaceTextDialog = false
                // TODO: Implement batch replace text
            }
        )
    }

    if (showAutoNumberDialog) {
        AutoNumberDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showAutoNumberDialog = false },
            onConfirm = { startNumber, step, totalTracks ->
                showAutoNumberDialog = false
                // TODO: Implement batch auto number
            }
        )
    }

    if (showRenameFilesDialog) {
        BatchRenameDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showRenameFilesDialog = false },
            onConfirm = { pattern, startNumber ->
                showRenameFilesDialog = false
                // TODO: Implement batch rename
            }
        )
    }

    if (showFixMetadataDialog) {
        BatchFixMetadataDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showFixMetadataDialog = false },
            onConfirm = { options ->
                showFixMetadataDialog = false
                // TODO: Implement batch fix metadata
            }
        )
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(),
            onDismiss = { showSearchSheet = false },
            allFiles = files,
            onFileClick = { audioFile ->
                showSearchSheet = false
                if (isSinglePane) {
                    onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path))
                } else {
                    coroutineScope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                    }
                }
            }
        )
    }
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

@Composable
fun DirectoryEmptyContent(modifier: Modifier = Modifier) {
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
