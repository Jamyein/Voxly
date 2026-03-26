package com.voxly.presentation.screens.filebrowser

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.voxly.R
import com.voxly.data.local.FileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.RootTab
import com.voxly.domain.usecase.BatchProgress
import com.voxly.domain.model.BatchStatus
import com.voxly.core.util.SortUtil
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.SearchBottomSheet
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotion
import com.voxly.presentation.ui.decodeBitmapFromBytes
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import com.voxly.presentation.viewmodel.FileBrowserUiState
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * File browser screen for browsing and selecting audio files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    outerPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = hiltViewModel(),
    listState: LazyListState = rememberLazyListState(),
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    onNavigateToDirectory: (String, String) -> Unit,
    onNavigateToSearch: (List<AudioFile>) -> Unit = {},
    onNavigateToAlbum: (String, String?) -> Unit = { _, _ -> },
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Tab states
    val allAudios by viewModel.allAudios.collectAsState()
    var selectedRootTab by rememberSaveable { mutableStateOf(RootTab.DIRECTORIES) }
    val isAudioFileView = !isDirectoryListLevel || selectedRootTab == RootTab.ALL

    // Pull-to-refresh state
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val onRefresh: () -> Unit = {
        viewModel.refresh()
    }

    // Sort option from persistent storage
    val sortOption by viewModel.fileBrowserSortOption.collectAsState(initial = FileSortOption.NAME_ASC.name)
    val currentSortOption = remember(sortOption) {
        try {
            FileSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            FileSortOption.NAME_ASC
        }
    }
    var isSortExpanded by remember { mutableStateOf(false) }

    // Sort All Tab audios
    val sortedAllAudios = remember(allAudios, currentSortOption) {
        applySort(allAudios, currentSortOption)
    }

    // Dialog states
    var renameTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var deleteTargetFile by remember { mutableStateOf<AudioFile?>(null) }

    // Search bottom sheet state
    var showSearchSheet by remember { mutableStateOf(false) }
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val visibleFiles = remember(visibleFilesRaw, currentSortOption) {
        derivedStateOf {
            applySort(
                files = visibleFilesRaw,
                sortOption = currentSortOption
            )
        }
    }.value
    val currentListKey = openedDirectoryUri ?: "__global__"
    val initialScrollPosition = remember(currentListKey) {
        viewModel.getScrollPosition(currentListKey)
    }
    // listState is now passed from parent (MP3TagNavHost) to support scroll-to-hide
    // Note: FileBrowserScreen manages scroll positions per directory via ViewModel

    val canScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Top bar visibility state (controlled by scroll)
    var isTopBarVisible by remember { mutableStateOf(true) }

    // Scroll detection threshold for top bar visibility
    val scrollHideThreshold = 100

    // Track scroll state for hiding top bar only (bottom bar scroll-to-hide is handled by FlexibleBottomAppBar)
    var lastScrollIndex by remember(currentListKey) { mutableIntStateOf(initialScrollPosition.index) }
    var lastScrollOffset by remember(currentListKey) { mutableIntStateOf(initialScrollPosition.offset) }
    var accumulatedScrollDelta by remember(currentListKey) { mutableIntStateOf(0) }

    // Track scroll progress for top bar visibility only
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.distinctUntilChanged().collect { (isScrolling, index, offset) ->
            if (!isScrolling) {
                lastScrollIndex = index
                lastScrollOffset = offset
                return@collect
            }

            val scrollDelta = when {
                index > lastScrollIndex -> scrollHideThreshold + 1
                index < lastScrollIndex -> -(scrollHideThreshold + 1)
                else -> offset - lastScrollOffset
            }

            if (scrollDelta != 0) {
                accumulatedScrollDelta = if (
                    (accumulatedScrollDelta >= 0 && scrollDelta > 0) ||
                    (accumulatedScrollDelta <= 0 && scrollDelta < 0)
                ) {
                    accumulatedScrollDelta + scrollDelta
                } else {
                    scrollDelta
                }
            }

            when {
                index == 0 && offset == 0 -> {
                    isTopBarVisible = true
                    accumulatedScrollDelta = 0
                }
                accumulatedScrollDelta > scrollHideThreshold * 2 -> {
                    isTopBarVisible = false
                }
                accumulatedScrollDelta < -scrollHideThreshold -> {
                    isTopBarVisible = true
                    accumulatedScrollDelta = 0
                }
            }

            lastScrollIndex = index
            lastScrollOffset = offset
        }
    }

    // Reset visibility when switching directories
    LaunchedEffect(currentListKey) {
        lastScrollIndex = initialScrollPosition.index
        lastScrollOffset = initialScrollPosition.offset
        accumulatedScrollDelta = 0
        isTopBarVisible = true
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

    LaunchedEffect(hasReadPermission, lifecycleOwner) {
        if (!hasReadPermission) {
            readPermissionLauncher.launch(readPermission)
            return@LaunchedEffect
        }

        // Initial load when permission is granted
        viewModel.loadAudioFiles()

        // Refresh on resume with incremental scanning
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadAudioFiles(forceRefresh = false)
        }
    }
    LaunchedEffect(currentListKey, listState) {
        var lastSavedIndex = initialScrollPosition.index
        var lastSavedOffset = initialScrollPosition.offset
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.distinctUntilChanged().collect { (isScrolling, index, offset) ->
            val needToSavePosition = !isScrolling ||
                index != lastSavedIndex ||
                abs(offset - lastSavedOffset) >= 48
            if (needToSavePosition) {
                viewModel.saveScrollPosition(currentListKey, index, offset)
                lastSavedIndex = index
                lastSavedOffset = offset
            }
        }
    }
    BackHandler(enabled = openedDirectory != null) {
        if (selectedFiles.isNotEmpty()) {
            viewModel.clearSelection()
        } else {
            viewModel.closeOpenedDirectory()
        }
    }


    val directoriesListState = rememberLazyListState()
    val allAudiosListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selectedFiles.isNotEmpty() && openedDirectory != null) {
                SelectionTopBar(
                    selectedCount = selectedFiles.size,
                    visibleFilesCount = visibleFiles.size,
                    onSelectAll = { viewModel.selectFilePaths(visibleFiles.map { it.path }) },
                    onClearSelection = { viewModel.clearSelection() },
                    onNavigateToReplayGain = {
                        onNavigateToReplayGain(viewModel.getSelectedFilePaths())
                    }
                )
            } else {
                AnimatedVisibility(
                    visible = isTopBarVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = ExpressiveMotion.DefaultSpringSize
                            )
                    ) {
                        if (openedDirectory != null) {
                            TopAppBar(
                                title = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(onDoubleTap = {
                                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                                })
                                            }
                                    ) {
                                        Text(openedDirectory.path.substringAfterLast('/').ifBlank { openedDirectory.path })
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                navigationIcon = {
                                    IconButton(onClick = viewModel::closeOpenedDirectory) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.cd_back)
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { showSearchSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.cd_search)
                                        )
                                    }
                                    // 只在"所有音频"Tab下显示排序按钮
                                    if (selectedRootTab == RootTab.ALL) {
                                        SortMenuButton(
                                            expanded = isSortExpanded,
                                            onExpandedChange = { isSortExpanded = it },
                                            currentSortOption = currentSortOption,
                                            options = FileSortOption.entries,
                                            optionLabelResId = { it.labelResId() },
                                            contentDescription = stringResource(R.string.file_sort_label),
                                            onSortOptionChange = { viewModel.setFileBrowserSortOption(it.name) }
                                        )
                                    }
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.refresh_files)
                                        )
                                    }
                                }
                            )
                        } else {
                            MediumTopAppBar(
                                title = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(onDoubleTap = {
                                                    coroutineScope.launch {
                                                        when {
                                                            isDirectoryListLevel && selectedRootTab == RootTab.ALL ->
                                                                allAudiosListState.animateScrollToItem(0)
                                                            isDirectoryListLevel ->
                                                                directoriesListState.animateScrollToItem(0)
                                                            else -> listState.animateScrollToItem(0)
                                                        }
                                                    }
                                                })
                                            }
                                    ) {
                                        Text(stringResource(R.string.nav_file_browser))
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                actions = {
                                    IconButton(onClick = { showSearchSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.cd_search)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedRootTab = if (selectedRootTab == RootTab.DIRECTORIES)
                                                RootTab.ALL
                                            else
                                                RootTab.DIRECTORIES
                                        }
                                    ) {
                                        Icon(
                                            painter = appIconPainter(
                                                if (selectedRootTab == RootTab.DIRECTORIES)
                                                    AppIcon.MusicNote
                                                else
                                                    AppIcon.Folder
                                            ),
                                            contentDescription = stringResource(
                                                if (selectedRootTab == RootTab.DIRECTORIES)
                                                    R.string.switch_to_all_audios
                                                else
                                                    R.string.switch_to_directories
                                            )
                                        )
                                    }
                                    // 只在"所有音频"Tab下显示排序按钮
                                    if (selectedRootTab == RootTab.ALL) {
                                        SortMenuButton(
                                            expanded = isSortExpanded,
                                            onExpandedChange = { isSortExpanded = it },
                                            currentSortOption = currentSortOption,
                                            options = FileSortOption.entries,
                                            optionLabelResId = { it.labelResId() },
                                            contentDescription = stringResource(R.string.file_sort_label),
                                            onSortOptionChange = { viewModel.setFileBrowserSortOption(it.name) }
                                        )
                                    }
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.refresh_files)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isAudioFileView && selectedFiles.isEmpty() && canScrollToTop && visibleFiles.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.back_to_top)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content with innerPadding from Scaffold and outerPadding from bottom nav
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = outerPadding.calculateBottomPadding()
                    )
            ) {
            if (isDirectoryListLevel) {
                // Root directory - show TabRow with tabs
                Column(modifier = Modifier.weight(1f)) {
                    // Conditional content based on selected root tab
                    if (selectedRootTab == RootTab.DIRECTORIES) {
                        DirectoryOverviewContent(
                            directories = selectedDirectories,
                            directoryFiles = directoryFiles,
                            onOpenDirectory = onNavigateToDirectory,
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            listState = directoriesListState,
                            bottomPadding = outerPadding.calculateBottomPadding()
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            AllAudiosTabContent(
                                audios = sortedAllAudios,
                                selectedFiles = selectedFiles,
                                onFileClick = { audioFile ->
                                    if (selectedFiles.isNotEmpty()) {
                                        viewModel.toggleFileSelection(audioFile.path)
                                    } else {
                                        onNavigateToMetadata(audioFile.path, "cover_${audioFile.path.hashCode()}")
                                    }
                                },
                                onFileLongClick = { audioFile ->
                                    viewModel.toggleFileSelection(audioFile.path)
                                },
                                isRefreshing = isRefreshing,
                                onRefresh = onRefresh,
                                listState = allAudiosListState
                            )
                        }
                    }
                }
            } else {
                // Inside directory - show file list without Tab
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
                            Box(modifier = Modifier.fillMaxSize()) {
                                PullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = onRefresh,
                                    modifier = Modifier.fillMaxSize(),
                                    indicator = {
                                        val pullToRefreshState = rememberPullToRefreshState()
                                        LoadingIndicator(
                                            state = pullToRefreshState,
                                            isRefreshing = isRefreshing,
                                            modifier = Modifier
                                        )
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        AudioFileListWithIndexer(
                                            files = filesToShow,
                                            listState = listState,
                                            selectedFiles = selectedFiles,
                                            onFileClick = { audioFile ->
                                                if (selectedFiles.isNotEmpty()) {
                                                    viewModel.toggleFileSelection(audioFile.path)
                                                } else {
                                                    onNavigateToMetadata(audioFile.path, "cover_${audioFile.path.hashCode()}")
                                                }
                                            },
                                            onFileLongClick = { audioFile ->
                                                viewModel.toggleFileSelection(audioFile.path)
                                            },
                                            onEditFileMetadata = { audioFile ->
                                                onNavigateToMetadata(audioFile.path, "cover_${audioFile.path.hashCode()}")
                                            },
                                            onRenameFile = { audioFile ->
                                                renameTargetFile = audioFile
                                            },
                                            onDeleteFile = { audioFile ->
                                                deleteTargetFile = audioFile
                                            },
                                            onFetchOnlineMetadata = { _ -> },
                                            onFixMetadata = { _ -> }
                                        )
                                    }
                                }
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

    if (renameTargetFile != null) {
        SingleFileRenameDialog(
            audioFile = renameTargetFile!!,
            onDismiss = { renameTargetFile = null },
            onConfirm = { newName ->
                val target = renameTargetFile ?: return@SingleFileRenameDialog
                viewModel.renameSingleFile(target.path, newName) { success, message ->
                    if (!success) {
                        Toast.makeText(
                            context,
                            message ?: context.getString(R.string.rename_file_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                renameTargetFile = null
            }
        )
    }

    if (deleteTargetFile != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetFile = null },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.dialog_confirm_delete)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_confirm_delete_single_file_message,
                        deleteTargetFile?.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTargetFile ?: return@TextButton
                        viewModel.deleteSingleFile(target.path) { success, message ->
                            if (!success) {
                                Toast.makeText(
                                    context,
                                    message ?: context.getString(R.string.delete_file_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        deleteTargetFile = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetFile = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // Search bottom sheet
    if (showSearchSheet) {
        SearchBottomSheet(
            sheetState = searchSheetState,
            onDismiss = { showSearchSheet = false },
            allFiles = visibleFilesRaw,
            onFileClick = { audioFile ->
                showSearchSheet = false
                onNavigateToMetadata(audioFile.path, null)
            }
        )
    }
}

/**
 * Batch Fix Metadata Dialog - extracted to BatchFixMetadataDialog.kt
 */

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
        FileSortOption.NAME_ASC -> filtered.sortedWith(compareBy { SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) })
        FileSortOption.NAME_DESC -> filtered.sortedWith(compareByDescending { SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) })
        FileSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
    }
}

private fun applySort(
    files: List<AudioFile>,
    sortOption: FileSortOption
): List<AudioFile> {
    return when (sortOption) {
        FileSortOption.NAME_ASC -> files.sortedWith(compareBy { SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) })
        FileSortOption.NAME_DESC -> files.sortedWith(compareByDescending { SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name)) })
        FileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
    }
}
