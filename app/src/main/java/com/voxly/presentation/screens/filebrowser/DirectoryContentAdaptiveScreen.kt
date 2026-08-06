package com.voxly.presentation.screens.filebrowser

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.State
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
import com.voxly.presentation.components.LibraryRefreshBox
import com.voxly.presentation.components.InlineLibrarySearchOverlay
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.components.navBarsBottomInset
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.openMetadataFor
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.LibraryBatchViewModel
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import com.voxly.presentation.viewmodel.LibrarySettingsViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Adaptive DirectoryContent screen using Material3 ListDetailPaneScaffold.
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
    viewModel: LibraryViewModel = hiltViewModel(),
    scanViewModel: LibraryScanViewModel = hiltViewModel(),
    settingsViewModel: LibrarySettingsViewModel = hiltViewModel(),
    batchViewModel: LibraryBatchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<AudioFile>()
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    val isSinglePane = navigator.scaffoldValue.primary == PaneAdaptedValue.Hidden
    val canCloseDetailPane = !isSinglePane && navigator.currentDestination != null

    val directoryFiles by scanViewModel.directoryFiles.collectAsStateWithLifecycle()
    val sortedDirectoryFiles by scanViewModel.sortedDirectoryFiles.collectAsStateWithLifecycle()
    val selectedFilesState = viewModel.selectedFiles.collectAsStateWithLifecycle()
    val selectedFiles = selectedFilesState.value
    val isRefreshing by scanViewModel.isRefreshing.collectAsStateWithLifecycle()
    val loadingDirectories by scanViewModel.directoryLoadingState.collectAsStateWithLifecycle()
    val currentSortOption by scanViewModel.currentDirectorySortOption.collectAsStateWithLifecycle()

    val savedScrollPosition = remember(directoryUri) {
        viewModel.getScrollPosition("directory_$directoryUri")
    }

    val isDirectoryLoading = remember(directoryUri, loadingDirectories) {
        directoryUri in loadingDirectories
    }

    val files = remember(directoryUri, directoryFiles) {
        directoryFiles[directoryUri] ?: emptyList()
    }
    val displayedFiles = remember(directoryUri, sortedDirectoryFiles) {
        sortedDirectoryFiles[directoryUri] ?: emptyList()
    }

    var showSearchSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollPosition.index,
        initialFirstVisibleItemScrollOffset = savedScrollPosition.offset
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isSelectionMode = selectedFiles.isNotEmpty()

    LaunchedEffect(isSinglePane) {
        if (isSinglePane && isSelectionMode) {
            viewModel.clearSelection()
        }
    }

    val canScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    DisposableEffect(directoryUri) {
        onDispose {
            viewModel.saveScrollPosition(
                "directory_$directoryUri",
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    val detailPaneState = remember { SeekableTransitionState(initialState = false) }

    PredictiveBackHandler(enabled = isSelectionMode || canCloseDetailPane) { progress ->
        try {
            progress.collect { backEvent ->
                detailPaneState.seekTo(fraction = backEvent.progress)
            }
            when {
                isSelectionMode -> {
                    detailPaneState.animateTo(targetState = true)
                    viewModel.clearSelection()
                }
                canCloseDetailPane -> {
                    detailPaneState.animateTo(targetState = true)
                    coroutineScope.launch {
                        navigator.navigateBack()
                    }
                }
            }
        } catch (e: CancellationException) {
            detailPaneState.snapTo(targetState = false)
        }
    }

    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    var showBatchOperationsMenu by remember { mutableStateOf(false) }
    var showUnifiedFieldDialog by remember { mutableStateOf(false) }
    var showReplaceTextDialog by remember { mutableStateOf(false) }
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var showRenameFilesDialog by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showOnlineMetadataDialog by remember { mutableStateOf(false) }
    var showSingleFileRenameDialog by remember { mutableStateOf(false) }
    var showSingleFileDeleteDialog by remember { mutableStateOf(false) }
    var currentSingleFile by remember { mutableStateOf<AudioFile?>(null) }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).nestedScroll(floatingToolbarScrollBehavior),
        listPane = {
            AnimatedPane(
                modifier = Modifier.fillMaxSize()
            ) {
                DirectoryListPane(
                    directoryName = directoryName,
                    files = files,
                    displayedFiles = displayedFiles,
                    selectedFilesState = selectedFilesState,
                    isSelectionMode = isSelectionMode,
                    isRefreshing = isRefreshing,
                    isDirectoryLoading = isDirectoryLoading,
                    canScrollToTop = canScrollToTop,
                    canCloseDetailPane = canCloseDetailPane,
                    isSinglePane = isSinglePane,
                    listState = listState,
                    currentSortOption = currentSortOption,
                    scrollBehavior = scrollBehavior,
                    floatingToolbarScrollBehavior = floatingToolbarScrollBehavior,
                    onNavigateBack = onNavigateBack,
                    onNavigateToMetadata = remember(viewModel, isSelectionMode, isSinglePane, coroutineScope, navigator, onNavigateToMetadata) {
                        { audioFile ->
                            if (isSelectionMode) {
                                viewModel.toggleFileSelection(audioFile.path)
                            } else if (isSinglePane) {
                                openMetadataFor(onNavigateToMetadata, audioFile)
                            } else {
                                coroutineScope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                                }
                            }
                        }
                    },
                    onFileLongClick = remember(viewModel) {
                        { audioFile ->
                            viewModel.toggleFileSelection(audioFile.path)
                        }
                    },
                    onClearSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onShowSearchSheet = { showSearchSheet = true },
                    searchActive = showSearchSheet,
                    onSearchDismiss = { showSearchSheet = false },
                    onSearchFileClick = { audioFile ->
                        showSearchSheet = false
                        if (isSinglePane) {
                            openMetadataFor(onNavigateToMetadata, audioFile)
                        } else {
                            coroutineScope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                            }
                        }
                    },
                    onSortOptionChange = { settingsViewModel.setDirectoryFileSortOption(it.name) },
                    onRefresh = { scanViewModel.refreshDirectoryIncremental(directoryUri) },
                    onNavigateBackWithPane = {
                        coroutineScope.launch { navigator.navigateBack() }
                    },
                    onOnlineMetadata = { showOnlineMetadataDialog = true },
                    onUnifiedField = { showUnifiedFieldDialog = true },
                    onReplaceText = { showReplaceTextDialog = true },
                    onAutoNumber = { showAutoNumberDialog = true },
                    onRenameFiles = { showRenameFilesDialog = true },
                    onFixMetadata = { showFixMetadataDialog = true },
                    onReplayGain = { onNavigateToReplayGain(selectedFiles.toList()) },
                    onSingleFileRename = { audioFile ->
                        currentSingleFile = audioFile
                        showSingleFileRenameDialog = true
                    },
                    onSingleFileDelete = { audioFile ->
                        currentSingleFile = audioFile
                        showSingleFileDeleteDialog = true
                    },
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        },
        detailPane = {
            AnimatedPane {
                DirectoryDetailPane(
                    currentFile = navigator.currentDestination?.contentKey,
                    fileSwitchCounter = fileSwitchCounter,
                    onFileSwitch = { fileSwitchCounter++ },
                    onNavigateBack = {
                        coroutineScope.launch { navigator.navigateBack() }
                    },
                    onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                    onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                    onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
                    onNavigateToLyricsSelector = onNavigateToLyricsSelector,
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    )

    DirectoryDialogsAndSheets(
        showBatchOperationsMenu = showBatchOperationsMenu,
        onShowBatchOperationsMenuChange = { showBatchOperationsMenu = it },
        showOnlineMetadataDialog = showOnlineMetadataDialog,
        onShowOnlineMetadataDialogChange = { showOnlineMetadataDialog = it },
        showUnifiedFieldDialog = showUnifiedFieldDialog,
        onShowUnifiedFieldDialogChange = { showUnifiedFieldDialog = it },
        showReplaceTextDialog = showReplaceTextDialog,
        onShowReplaceTextDialogChange = { showReplaceTextDialog = it },
        showAutoNumberDialog = showAutoNumberDialog,
        onShowAutoNumberDialogChange = { showAutoNumberDialog = it },
        showRenameFilesDialog = showRenameFilesDialog,
        onShowRenameFilesDialogChange = { showRenameFilesDialog = it },
        showFixMetadataDialog = showFixMetadataDialog,
        onShowFixMetadataDialogChange = { showFixMetadataDialog = it },
        showSingleFileRenameDialog = showSingleFileRenameDialog,
        onShowSingleFileRenameDialogChange = { showSingleFileRenameDialog = it },
        showSingleFileDeleteDialog = showSingleFileDeleteDialog,
        onShowSingleFileDeleteDialogChange = { showSingleFileDeleteDialog = it },
        currentSingleFile = currentSingleFile,
        selectedFilesCount = selectedFiles.size
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectoryListPane(
    directoryName: String,
    files: List<AudioFile>,
    displayedFiles: List<AudioFile>,
    selectedFilesState: State<Set<String>>,
    isSelectionMode: Boolean,
    isRefreshing: Boolean,
    isDirectoryLoading: Boolean,
    canScrollToTop: Boolean,
    canCloseDetailPane: Boolean,
    isSinglePane: Boolean,
    listState: LazyListState,
    currentSortOption: DirFileSortOption,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    floatingToolbarScrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShowSearchSheet: () -> Unit,
    searchActive: Boolean = false,
    onSearchDismiss: () -> Unit = {},
    onSearchFileClick: (AudioFile) -> Unit = {},
    onSortOptionChange: (DirFileSortOption) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBackWithPane: () -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit,
    onReplayGain: () -> Unit,
    onSingleFileRename: (AudioFile) -> Unit,
    onSingleFileDelete: (AudioFile) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val selectedFiles = selectedFilesState.value

    // Force the top bar fully expanded while the search panel is open, so the
    // panel position under it never jumps when the bar was previously collapsed.
    LaunchedEffect(searchActive) {
        if (searchActive) scrollBehavior.state.heightOffset = 0f
    }

    Column(modifier = modifier) {
        DirectoryListTopAppBar(
            directoryName = directoryName,
            selectedFilesSize = selectedFiles.size,
            totalFilesSize = files.size,
            isSelectionMode = isSelectionMode,
            canCloseDetailPane = canCloseDetailPane,
            isSinglePane = isSinglePane,
            scrollBehavior = scrollBehavior,
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll,
            onShowSearchSheet = onShowSearchSheet,
            currentSortOption = currentSortOption,
            onSortOptionChange = onSortOptionChange,
            onRefresh = onRefresh,
            onNavigateBack = onNavigateBack,
            onNavigateBackWithPane = onNavigateBackWithPane
        )

        Box(modifier = Modifier.fillMaxSize()) {
            DirectoryListBody(
                files = files,
                displayedFiles = displayedFiles,
                isDirectoryLoading = isDirectoryLoading,
                isRefreshing = isRefreshing,
                isSelectionMode = isSelectionMode,
                selectedFilesState = selectedFilesState,
                canScrollToTop = canScrollToTop,
                listState = listState,
                scrollBehavior = scrollBehavior,
                floatingToolbarScrollBehavior = floatingToolbarScrollBehavior,
                onFileClick = onNavigateToMetadata,
                onFileLongClick = onFileLongClick,
                onRefresh = onRefresh,
                onOnlineMetadata = onOnlineMetadata,
                onUnifiedField = onUnifiedField,
                onReplaceText = onReplaceText,
                onAutoNumber = onAutoNumber,
                onRenameFiles = onRenameFiles,
                onFixMetadata = onFixMetadata,
                onReplayGain = onReplayGain,
                onSingleFileRename = onSingleFileRename,
                onSingleFileDelete = onSingleFileDelete,
                modifier = Modifier.fillMaxSize(),
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )

            InlineLibrarySearchOverlay(
                visible = searchActive,
                onDismiss = onSearchDismiss,
                onFileClick = onSearchFileClick,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryListTopAppBar(
    directoryName: String,
    selectedFilesSize: Int,
    totalFilesSize: Int,
    isSelectionMode: Boolean,
    canCloseDetailPane: Boolean,
    isSinglePane: Boolean,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShowSearchSheet: () -> Unit,
    currentSortOption: DirFileSortOption,
    onSortOptionChange: (DirFileSortOption) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateBackWithPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSortExpanded by remember { mutableStateOf(false) }

    VoxlyTopAppBar(
        large = true,
        theme = TopBarTheme.Library,
        title = {
            if (isSelectionMode) {
                Text("$selectedFilesSize selected")
            } else {
                Text(
                    text = directoryName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        scrollBehavior = scrollBehavior,
        onBack = {
            if (isSelectionMode) {
                onClearSelection()
            } else if (!isSinglePane && canCloseDetailPane) {
                onNavigateBackWithPane()
            } else {
                onNavigateBack()
            }
        },
        actions = {
            if (isSelectionMode) {
                IconButton(onClick = {
                    if (selectedFilesSize == totalFilesSize) {
                        onClearSelection()
                    } else {
                        onSelectAll()
                    }
                }) {
                    Text(
                        if (selectedFilesSize == totalFilesSize) {
                            stringResource(R.string.deselect_all)
                        } else {
                            stringResource(R.string.select_all)
                        }
                    )
                }
            } else {
                IconButton(onClick = onShowSearchSheet) {
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
                    onSortOptionChange = onSortOptionChange
                )
                }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectoryListBody(
    files: List<AudioFile>,
    displayedFiles: List<AudioFile>,
    isDirectoryLoading: Boolean,
    isRefreshing: Boolean,
    isSelectionMode: Boolean,
    selectedFilesState: State<Set<String>>,
    canScrollToTop: Boolean,
    listState: LazyListState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    floatingToolbarScrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onRefresh: () -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit,
    onReplayGain: () -> Unit,
    onSingleFileRename: (AudioFile) -> Unit,
    onSingleFileDelete: (AudioFile) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DirectoryFileListContent(
                        files = files,
                        displayedFiles = displayedFiles,
                        isDirectoryLoading = isDirectoryLoading,
                        isSelectionMode = isSelectionMode,
                        selectedFilesState = selectedFilesState,
                        listState = listState,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        onSingleFileRename = onSingleFileRename,
                        onSingleFileDelete = onSingleFileDelete,
                        bottomPadding = if (isSelectionMode) 80.dp else 16.dp + navBarsBottomInset(),
                        modifier = Modifier.fillMaxSize(),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }

        if (isSelectionMode) {
            BatchOperationsToolbar(
                expanded = true,
                scrollBehavior = floatingToolbarScrollBehavior,
                onOnlineMetadata = onOnlineMetadata,
                onUnifiedField = onUnifiedField,
                onReplaceText = onReplaceText,
                onAutoNumber = onAutoNumber,
                onRenameFiles = onRenameFiles,
                onFixMetadata = onFixMetadata,
                onReplayGain = onReplayGain
            )
        }

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
                    .navigationBarsPadding()
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

@Composable
private fun DirectoryFileListContent(
    files: List<AudioFile>,
    displayedFiles: List<AudioFile>,
    isDirectoryLoading: Boolean,
    isSelectionMode: Boolean,
    selectedFilesState: State<Set<String>>,
    listState: LazyListState,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onSingleFileRename: (AudioFile) -> Unit,
    onSingleFileDelete: (AudioFile) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Box(modifier = modifier) {
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
                selectedFilesState = selectedFilesState,
                onFileClick = onFileClick,
                onFileLongClick = onFileLongClick,
                onEditFileMetadata = onFileClick,
                onRenameFile = onSingleFileRename,
                onDeleteFile = onSingleFileDelete,
                onFetchOnlineMetadata = onFileClick,
                onFixMetadata = onFileClick,
                bottomPadding = bottomPadding,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun DirectoryDetailPane(
    currentFile: AudioFile?,
    fileSwitchCounter: Int,
    onFileSwitch: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    if (currentFile != null) {
        val navKey = MetadataEditor(
            filePath = currentFile.path,
            coverTag = createAlbumArtSharedElementKey(currentFile.path)
        )
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
                onFileSwitch()
                onNavigateBack()
            },
            onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
            onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
            onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
            onNavigateToLyricsSelector = onNavigateToLyricsSelector,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    } else {
        EmptyDetailPane()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryDialogsAndSheets(
    showBatchOperationsMenu: Boolean,
    onShowBatchOperationsMenuChange: (Boolean) -> Unit,
    showOnlineMetadataDialog: Boolean,
    onShowOnlineMetadataDialogChange: (Boolean) -> Unit,
    showUnifiedFieldDialog: Boolean,
    onShowUnifiedFieldDialogChange: (Boolean) -> Unit,
    showReplaceTextDialog: Boolean,
    onShowReplaceTextDialogChange: (Boolean) -> Unit,
    showAutoNumberDialog: Boolean,
    onShowAutoNumberDialogChange: (Boolean) -> Unit,
    showRenameFilesDialog: Boolean,
    onShowRenameFilesDialogChange: (Boolean) -> Unit,
    showFixMetadataDialog: Boolean,
    onShowFixMetadataDialogChange: (Boolean) -> Unit,
    showSingleFileRenameDialog: Boolean,
    onShowSingleFileRenameDialogChange: (Boolean) -> Unit,
    showSingleFileDeleteDialog: Boolean,
    onShowSingleFileDeleteDialogChange: (Boolean) -> Unit,
    currentSingleFile: AudioFile?,
    selectedFilesCount: Int
) {
    if (showBatchOperationsMenu) {
        BatchOperationsMenuDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowBatchOperationsMenuChange(false) },
            onOnlineMetadata = {
                onShowBatchOperationsMenuChange(false)
                onShowOnlineMetadataDialogChange(true)
            },
            onUnifiedField = {
                onShowBatchOperationsMenuChange(false)
                onShowUnifiedFieldDialogChange(true)
            },
            onReplaceText = {
                onShowBatchOperationsMenuChange(false)
                onShowReplaceTextDialogChange(true)
            },
            onAutoNumber = {
                onShowBatchOperationsMenuChange(false)
                onShowAutoNumberDialogChange(true)
            },
            onRenameFiles = {
                onShowBatchOperationsMenuChange(false)
                onShowRenameFilesDialogChange(true)
            },
            onFixMetadata = {
                onShowBatchOperationsMenuChange(false)
                onShowFixMetadataDialogChange(true)
            }
        )
    }

    if (showOnlineMetadataDialog) {
        BatchOnlineMetadataDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowOnlineMetadataDialogChange(false) },
            onConfirm = { }
        )
    }

    if (showUnifiedFieldDialog) {
        UnifiedFieldDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowUnifiedFieldDialogChange(false) },
            onConfirm = { _, _ -> }
        )
    }

    if (showReplaceTextDialog) {
        ReplaceTextDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowReplaceTextDialogChange(false) },
            onConfirm = { _, _, _, _ -> }
        )
    }

    if (showAutoNumberDialog) {
        AutoNumberDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowAutoNumberDialogChange(false) },
            onConfirm = { _, _, _ -> }
        )
    }

    if (showRenameFilesDialog) {
        BatchRenameDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowRenameFilesDialogChange(false) },
            onConfirm = { _, _ -> }
        )
    }

    if (showFixMetadataDialog) {
        BatchFixMetadataDialog(
            targetFilesCount = selectedFilesCount,
            onDismiss = { onShowFixMetadataDialogChange(false) },
            onConfirm = { }
        )
    }

    if (showSingleFileRenameDialog && currentSingleFile != null) {
        SingleFileRenameDialog(
            audioFile = currentSingleFile,
            onDismiss = { onShowSingleFileRenameDialogChange(false) },
            onConfirm = { _ -> }
        )
    }

    if (showSingleFileDeleteDialog && currentSingleFile != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onShowSingleFileDeleteDialogChange(false) },
            title = { Text(stringResource(R.string.dialog_confirm_delete)) },
            text = { Text(stringResource(R.string.dialog_confirm_delete_single_file_message, currentSingleFile.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onShowSingleFileDeleteDialogChange(false) }) {
                    Text(stringResource(R.string.dialog_confirm), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onShowSingleFileDeleteDialogChange(false) }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
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
        modifier = modifier.fillMaxSize(),
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
