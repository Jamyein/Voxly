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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.voxly.domain.model.AudioFile
import com.voxly.domain.usecase.BatchProgress
import com.voxly.domain.usecase.BatchStatus
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotionTokens
import com.voxly.presentation.ui.decodeBitmapFromBytes
import com.voxly.presentation.viewmodel.FileBrowserUiState
import com.voxly.presentation.viewmodel.FileBrowserViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.Collator
import java.util.Locale

/** Collator for Chinese pinyin sorting */
private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
    strength = Collator.PRIMARY
}

/**
 * File browser screen for browsing and selecting audio files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = hiltViewModel(),
    onNavigateToMetadata: (String) -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    onNavigateToSearch: (List<AudioFile>) -> Unit = {},
    onBottomBarScrollProgressChange: (Float) -> Unit = {}
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
    
    // Batch operation states
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchError by viewModel.batchError.collectAsState()
    
    var sortOption by rememberSaveable { mutableStateOf(FileSortOption.NAME_ASC.name) }
    var isSortExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog states
    var showBatchMenu by remember { mutableStateOf(false) }
    var showBatchOperationsMenu by remember { mutableStateOf(false) }
    var showOnlineMetadataDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showUnifiedFieldDialog by remember { mutableStateOf(false) }
    var showReplaceTextDialog by remember { mutableStateOf(false) }
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var showBatchProgress by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var deleteTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var selectedFilesForBatch by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val visibleFiles = remember(visibleFilesRaw, sortOption) {
        applySort(
            files = visibleFilesRaw,
            sortOption = FileSortOption.valueOf(sortOption)
        )
    }
    val currentListKey = openedDirectoryUri ?: "__global__"
    val initialScrollPosition = remember(currentListKey) {
        viewModel.getScrollPosition(currentListKey)
    }
    val albumArtCache = remember { mutableMapOf<String, android.graphics.Bitmap?>() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.index,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.offset
    )

    val canScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Top bar visibility state (controlled by scroll)
    var isTopBarVisible by remember { mutableStateOf(true) }

    // Scroll detection threshold for top bar visibility
    val scrollHideThreshold = 56

    // Track scroll state for hiding top bar and bottom bar
    var lastScrollIndex by remember(currentListKey) { mutableIntStateOf(initialScrollPosition.index) }
    var lastScrollOffset by remember(currentListKey) { mutableIntStateOf(initialScrollPosition.offset) }
    var accumulatedScrollDelta by remember(currentListKey) { mutableIntStateOf(0) }

    // Track scroll progress using LazyColumn state
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
                accumulatedScrollDelta = 0
                if (index == 0 && offset == 0) {
                    onBottomBarScrollProgressChange(0f)
                }
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

            // Calculate progress (0 = fully visible, 1 = fully hidden)
            val progress = (kotlin.math.abs(accumulatedScrollDelta).toFloat() / (scrollHideThreshold * 8f))
                .coerceIn(0f, 1f)

            when {
                index == 0 && offset == 0 -> {
                    onBottomBarScrollProgressChange(0f)
                    isTopBarVisible = true
                    accumulatedScrollDelta = 0
                }
                accumulatedScrollDelta > scrollHideThreshold -> {
                    onBottomBarScrollProgressChange(1f)
                    isTopBarVisible = false
                    accumulatedScrollDelta = 0
                }
                accumulatedScrollDelta < -scrollHideThreshold -> {
                    onBottomBarScrollProgressChange(0f)
                    isTopBarVisible = true
                    accumulatedScrollDelta = 0
                }
                else -> {
                    onBottomBarScrollProgressChange(progress)
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
        onBottomBarScrollProgressChange(0f)
    }
    // Show progress dialog when batch processing starts
    LaunchedEffect(isBatchProcessing) {
        if (isBatchProcessing) {
            showBatchProgress = true
        }
    }
    
    // Auto-hide progress dialog when complete
    LaunchedEffect(batchProgress) {
        if (batchProgress?.status == BatchStatus.COMPLETED) {
            kotlinx.coroutines.delay(1500)
            showBatchProgress = false
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
        } else if (!hasReadPermission) {
            readPermissionLauncher.launch(readPermission)
        }
    }
    LaunchedEffect(hasReadPermission, lifecycleOwner) {
        if (!hasReadPermission) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 改用增量扫描，只检测变化的文件，避免全量刷新
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
            val shouldSave = !isScrolling ||
                index != lastSavedIndex ||
                abs(offset - lastSavedOffset) >= 48
            if (shouldSave) {
                viewModel.saveScrollPosition(currentListKey, index, offset)
                lastSavedIndex = index
                lastSavedOffset = offset
            }
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
                    },
                    onBatchOperations = {
                        showBatchOperationsMenu = true
                    }
                )
            } else {
                AnimatedVisibility(
                    visible = isTopBarVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (openedDirectory != null) {
                        LargeTopAppBar(
                            title = { Text(openedDirectory.path.substringAfterLast('/').ifBlank { openedDirectory.path }) },
                            colors = TopAppBarDefaults.topAppBarColors(),
                            windowInsets = WindowInsets(0.dp),
                            navigationIcon = {
                                IconButton(onClick = viewModel::closeOpenedDirectory) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { onNavigateToSearch(visibleFilesRaw) }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search)
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
                            }
                        )
                    } else {
                        LargeTopAppBar(
                            title = { Text(stringResource(R.string.nav_file_browser)) },
                            colors = TopAppBarDefaults.topAppBarColors(),
                            windowInsets = WindowInsets(0.dp),
                            actions = {
                                IconButton(onClick = { onNavigateToSearch(visibleFilesRaw) }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search)
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
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = {
            // Show batch operation FAB only when in directory view
            if (selectedFiles.isEmpty() && !isBatchProcessing) {
                BatchOperationsFAB(
                    expanded = showBatchMenu,
                    onExpandChange = { showBatchMenu = it },
                    onOnlineMetadata = { 
                        showBatchMenu = false
                        showOnlineMetadataDialog = true 
                    },
                    onRenameFiles = { 
                        showBatchMenu = false
                        showRenameDialog = true 
                    },
                    onFixMetadata = { 
                        showBatchMenu = false
                        showFixMetadataDialog = true 
                    }
                )
            } else if (selectedFiles.isEmpty() && canScrollToTop && visibleFiles.isNotEmpty()) {
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                SortMenu(
                                    isExpanded = isSortExpanded,
                                    currentSortOption = FileSortOption.valueOf(sortOption),
                                    onSortOptionChange = { sortOption = it.name },
                                    onDismiss = { isSortExpanded = false }
                                )
                                AudioFileList(
                                    files = filesToShow,
                                    listState = listState,
                                    albumArtCache = albumArtCache,
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
                                    },
                                    onEditFileMetadata = { audioFile ->
                                        onNavigateToMetadata(audioFile.path)
                                    },
                                    onRenameFile = { audioFile ->
                                        renameTargetFile = audioFile
                                    },
                                    onDeleteFile = { audioFile ->
                                        deleteTargetFile = audioFile
                                    },
                                    onFetchOnlineMetadata = { audioFile ->
                                        selectedFilesForBatch = setOf(audioFile.path)
                                        showOnlineMetadataDialog = true
                                    },
                                    onFixMetadata = { audioFile ->
                                        selectedFilesForBatch = setOf(audioFile.path)
                                        showFixMetadataDialog = true
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
    
    // Batch Progress Dialog
    if (showBatchProgress && batchProgress != null) {
        BatchProgressDialog(
            progress = batchProgress!!,
            onDismiss = { 
                if (batchProgress?.status == BatchStatus.COMPLETED || batchProgress?.status == BatchStatus.CANCELLED) {
                    showBatchProgress = false
                    viewModel.resetBatchOperation()
                }
            }
        )
    }
    
    // Error Snackbar
    if (batchError != null) {
        LaunchedEffect(batchError) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearBatchError()
        }
    }
    
    // Online Metadata Dialog
    if (showOnlineMetadataDialog) {
        BatchOnlineMetadataDialog(
            targetFilesCount = when {
                selectedFilesForBatch.isNotEmpty() -> selectedFilesForBatch.size
                selectedFiles.isNotEmpty() -> selectedFiles.size
                else -> visibleFiles.size
            },
            onDismiss = { 
                showOnlineMetadataDialog = false
                selectedFilesForBatch = emptySet()
            },
            onConfirm = { options ->
                val targetFiles = when {
                    selectedFilesForBatch.isNotEmpty() -> selectedFilesForBatch.toList()
                    selectedFiles.isNotEmpty() -> selectedFiles.toList()
                    else -> visibleFiles.map { it.path }
                }
                viewModel.batchFetchOnlineMetadata(targetFiles, options)
                showOnlineMetadataDialog = false
                selectedFilesForBatch = emptySet()
            }
        )
    }
    
    // Rename Dialog
    if (showRenameDialog) {
        BatchRenameDialog(
            targetFilesCount = if (selectedFiles.isEmpty()) visibleFiles.size else selectedFiles.size,
            onDismiss = { showRenameDialog = false },
            onConfirm = { pattern, startNumber ->
                val targetFiles = if (selectedFiles.isEmpty()) {
                    visibleFiles.map { it.path }
                } else {
                    selectedFiles.toList()
                }
                viewModel.batchRenameFiles(targetFiles, pattern, startNumber)
                showRenameDialog = false
            }
        )
    }
    
    // Fix Metadata Dialog
    if (showFixMetadataDialog) {
        BatchFixMetadataDialog(
            targetFilesCount = when {
                selectedFilesForBatch.isNotEmpty() -> selectedFilesForBatch.size
                selectedFiles.isNotEmpty() -> selectedFiles.size
                else -> visibleFiles.size
            },
            onDismiss = { 
                showFixMetadataDialog = false
                selectedFilesForBatch = emptySet()
            },
            onConfirm = { options ->
                val targetFiles = when {
                    selectedFilesForBatch.isNotEmpty() -> selectedFilesForBatch.toList()
                    selectedFiles.isNotEmpty() -> selectedFiles.toList()
                    else -> visibleFiles.map { it.path }
                }
                viewModel.batchFixMetadata(targetFiles, options)
                showFixMetadataDialog = false
                selectedFilesForBatch = emptySet()
            }
        )
    }

    // Batch Operations Menu Dialog (when files are selected)
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
                showRenameDialog = true
            },
            onFixMetadata = {
                showBatchOperationsMenu = false
                showFixMetadataDialog = true
            }
        )
    }

    // Unified Field Dialog
    if (showUnifiedFieldDialog) {
        UnifiedFieldDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showUnifiedFieldDialog = false },
            onConfirm = { field, value ->
                viewModel.batchSetUnifiedField(selectedFiles.toList(), field, value)
                showUnifiedFieldDialog = false
            }
        )
    }

    // Replace Text Dialog
    if (showReplaceTextDialog) {
        ReplaceTextDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showReplaceTextDialog = false },
            onConfirm = { field, searchText, replaceText, useRegex ->
                viewModel.batchReplaceText(selectedFiles.toList(), field, searchText, replaceText, useRegex)
                showReplaceTextDialog = false
            }
        )
    }

    // Auto Number Dialog
    if (showAutoNumberDialog) {
        AutoNumberDialog(
            targetFilesCount = selectedFiles.size,
            onDismiss = { showAutoNumberDialog = false },
            onConfirm = { startNumber, step, totalTracks ->
                viewModel.batchAutoNumberTracks(selectedFiles.toList(), startNumber, step, totalTracks)
                showAutoNumberDialog = false
            }
        )
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
}

/**
 * Batch Operations FAB with expandable menu (Speed Dial style)
 */
@Composable
private fun BatchOperationsFAB(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onOnlineMetadata: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
            stiffness = ExpressiveMotionTokens.Emphasized.stiffness
        ),
        label = "fab_rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Menu items
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
            )) + expandVertically(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
            )),
            exit = fadeOut(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
            )) + shrinkVertically(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
            ))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // Online Metadata
                MenuItem(
                    label = stringResource(R.string.batch_online_metadata),
                    icon = AppIcon.CloudDownload,
                    onClick = onOnlineMetadata
                )
                
                // Rename Files
                MenuItem(
                    label = stringResource(R.string.batch_rename_files),
                    icon = AppIcon.Rename,
                    onClick = onRenameFiles
                )
                
                // Fix Metadata
                MenuItem(
                    label = stringResource(R.string.batch_fix_metadata),
                    icon = AppIcon.AutoFix,
                    onClick = onFixMetadata
                )
            }
        }
        
        // Main FAB
        FloatingActionButton(
            onClick = { onExpandChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.batch_operations),
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        // Label
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Icon
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                painter = appIconPainter(icon),
                contentDescription = label
            )
        }
    }
}

/**
 * Batch Progress Dialog (MD3 compliant)
 */
@Composable
private fun BatchProgressDialog(
    progress: BatchProgress,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = progress.status != BatchStatus.PROCESSING,
            dismissOnClickOutside = progress.status != BatchStatus.PROCESSING
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon/Progress indicator
                when (progress.status) {
                    BatchStatus.PROCESSING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }
                    BatchStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.batch_complete),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    BatchStatus.CANCELLED -> {
                        Icon(
                            painter = appIconPainter(AppIcon.Close),
                            contentDescription = stringResource(R.string.batch_cancelled),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = when (progress.status) {
                        BatchStatus.PROCESSING -> stringResource(R.string.batch_processing)
                        BatchStatus.COMPLETED -> stringResource(R.string.batch_complete)
                        BatchStatus.CANCELLED -> stringResource(R.string.batch_cancelled)
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress text
                Text(
                    text = stringResource(R.string.batch_progress_format, progress.currentFile, progress.totalFiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Current file
                if (progress.currentFilePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentFilePath.substringAfterLast("/"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress indicator
                LinearProgressIndicator(
                    progress = { progress.percentage },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Percentage
                Text(
                    text = "${(progress.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats
                if (progress.status != BatchStatus.PROCESSING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BatchStatItem(
                            label = stringResource(R.string.success),
                            value = progress.successCount,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BatchStatItem(
                            label = stringResource(R.string.failed),
                            value = progress.failureCount,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Action buttons
                if (progress.status != BatchStatus.PROCESSING) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchStatItem(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.8f)
        )
    }
}

/**
 * Batch Online Metadata Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchOnlineMetadataDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (OnlineMetadataOptions) -> Unit
) {
    var overwriteExisting by remember { mutableStateOf(false) }
    var fetchAlbumArt by remember { mutableStateOf(true) }
    var fetchLyrics by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.CloudDownload), contentDescription = stringResource(R.string.cd_online_metadata)) },
        title = { Text(stringResource(R.string.batch_online_metadata_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Options
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { overwriteExisting = !overwriteExisting }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = overwriteExisting,
                            onCheckedChange = { overwriteExisting = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_overwrite_existing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fetchAlbumArt = !fetchAlbumArt }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fetchAlbumArt,
                            onCheckedChange = { fetchAlbumArt = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_fetch_album_art),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fetchLyrics = !fetchLyrics }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fetchLyrics,
                            onCheckedChange = { fetchLyrics = it }
                        )
                        Text(
                            text = stringResource(R.string.batch_fetch_lyrics),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(OnlineMetadataOptions(overwriteExisting, fetchAlbumArt, fetchLyrics))
                }
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

data class OnlineMetadataOptions(
    val overwriteExisting: Boolean,
    val fetchAlbumArt: Boolean,
    val fetchLyrics: Boolean
)

/**
 * Batch Rename Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchRenameDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var pattern by remember { mutableStateOf("{artist} - {title}") }
    var startNumber by remember { mutableIntStateOf(1) }
    var expanded by remember { mutableStateOf(false) }
    
    val patterns = listOf(
        "{artist} - {title}" to stringResource(R.string.pattern_artist_title),
        "{title}" to stringResource(R.string.pattern_title),
        "{track}. {title}" to stringResource(R.string.pattern_track_title),
        "{artist} - {album} - {track}. {title}" to stringResource(R.string.pattern_full)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Rename), contentDescription = stringResource(R.string.cd_batch_rename)) },
        title = { Text(stringResource(R.string.batch_rename_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Pattern selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = patterns.find { it.first == pattern }?.second ?: pattern,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rename_pattern)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        patterns.forEach { (pat, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    pattern = pat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Start number
                OutlinedTextField(
                    value = startNumber.toString(),
                    onValueChange = { 
                        startNumber = it.toIntOrNull() ?: 1
                    },
                    label = { Text(stringResource(R.string.start_number)) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Help text
                Text(
                    text = stringResource(R.string.rename_pattern_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pattern, startNumber) }) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Batch Fix Metadata Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchFixMetadataDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (FixMetadataOptions) -> Unit
) {
    var autoTitleCase by remember { mutableStateOf(true) }
    var removeExtraSpaces by remember { mutableStateOf(true) }
    var fixTrackNumbers by remember { mutableStateOf(true) }
    var removeEmptyTags by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.AutoFix), contentDescription = stringResource(R.string.cd_batch_fix)) },
        title = { Text(stringResource(R.string.batch_fix_metadata_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Options
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoTitleCase = !autoTitleCase }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = autoTitleCase,
                            onCheckedChange = { autoTitleCase = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_auto_title_case),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { removeExtraSpaces = !removeExtraSpaces }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = removeExtraSpaces,
                            onCheckedChange = { removeExtraSpaces = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_remove_extra_spaces),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fixTrackNumbers = !fixTrackNumbers }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = fixTrackNumbers,
                            onCheckedChange = { fixTrackNumbers = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_track_numbers),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { removeEmptyTags = !removeEmptyTags }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = removeEmptyTags,
                            onCheckedChange = { removeEmptyTags = it }
                        )
                        Text(
                            text = stringResource(R.string.fix_remove_empty_tags),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(FixMetadataOptions(autoTitleCase, removeExtraSpaces, fixTrackNumbers, removeEmptyTags))
                }
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

data class FixMetadataOptions(
    val autoTitleCase: Boolean,
    val removeExtraSpaces: Boolean,
    val fixTrackNumbers: Boolean,
    val removeEmptyTags: Boolean
)

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
        FileSortOption.NAME_ASC -> filtered.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        FileSortOption.NAME_DESC -> filtered.sortedWith(compareByDescending(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        FileSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
    }
}

private fun applySort(
    files: List<AudioFile>,
    sortOption: FileSortOption
): List<AudioFile> {
    return when (sortOption) {
        FileSortOption.NAME_ASC -> files.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        FileSortOption.NAME_DESC -> files.sortedWith(compareByDescending(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        FileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
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
                                contentDescription = stringResource(R.string.cd_selected),
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
    onNavigateToReplayGain: () -> Unit,
    onBatchOperations: () -> Unit
) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_clear_selection)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
windowInsets = WindowInsets(0.dp),
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all))
            }
            // Batch Operations Menu
            Box {
                var expanded by remember { mutableStateOf(false) }
                
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        painter = appIconPainter(AppIcon.MoreVert),
                        contentDescription = stringResource(R.string.batch_operations)
                    )
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_online_metadata)) },
                        leadingIcon = {
                            Icon(painter = appIconPainter(AppIcon.CloudDownload), contentDescription = stringResource(R.string.cd_online_metadata))
                        },
                        onClick = {
                            expanded = false
                            onBatchOperations()
                        }
                    )
                }
            }
            
            FilledTonalButton(
                onClick = onNavigateToReplayGain,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(painter = appIconPainter(AppIcon.Equalizer), contentDescription = stringResource(R.string.cd_scan_replay_gain))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.replay_gain))
            }
        }
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(directories, key = { it.uri }) { directory ->
            DirectoryItem(
                directory = directory,
                fileCount = directoryFiles[directory.uri]?.size ?: 0,
                onClick = { onOpenDirectory(directory.uri) }
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
                contentDescription = stringResource(R.string.cd_no_files),
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
                contentDescription = stringResource(R.string.cd_error),
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
    albumArtCache: MutableMap<String, android.graphics.Bitmap?>,
    selectedFiles: Set<String>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onEditFileMetadata: (AudioFile) -> Unit,
    onRenameFile: (AudioFile) -> Unit,
    onDeleteFile: (AudioFile) -> Unit,
    onFetchOnlineMetadata: (AudioFile) -> Unit,
    onFixMetadata: (AudioFile) -> Unit
) {
    val isSelectionMode = selectedFiles.isNotEmpty()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(files, key = { it.path }) { audioFile ->
            AudioFileItem(
                audioFile = audioFile,
                albumArtCache = albumArtCache,
                isSelected = audioFile.path in selectedFiles,
                onClick = { onFileClick(audioFile) },
                onLongClick = { onFileLongClick(audioFile) },
                showActions = !isSelectionMode,
                onEditMetadata = { onEditFileMetadata(audioFile) },
                onRename = { onRenameFile(audioFile) },
                onDelete = { onDeleteFile(audioFile) },
                onFetchOnlineMetadata = { onFetchOnlineMetadata(audioFile) },
                onFixMetadata = { onFixMetadata(audioFile) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AudioFileItem(
    audioFile: AudioFile,
    albumArtCache: MutableMap<String, android.graphics.Bitmap?>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showActions: Boolean,
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art display
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                val albumArtBitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = albumArtCache[audioFile.path],
                    key1 = audioFile.path,
                    key2 = audioFile.mediaStoreAlbumId
                ) {
                    val cacheKey = audioFile.path
                    if (albumArtCache.containsKey(cacheKey)) {
                        value = albumArtCache[cacheKey]
                        return@produceState
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        loadAlbumArt(context, audioFile)
                    }
                    albumArtCache[cacheKey] = bitmap
                    value = bitmap
                }

                val bitmap = albumArtBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = appIconPainter(AppIcon.MusicNote),
                        contentDescription = stringResource(R.string.cd_no_cover),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                    style = MaterialTheme.typography.bodyMedium,
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            } else if (showActions) {
                FileActionsMenu(
                    onEditMetadata = onEditMetadata,
                    onRename = onRename,
                    onDelete = onDelete,
                    onFetchOnlineMetadata = onFetchOnlineMetadata,
                    onFixMetadata = onFixMetadata
                )
            }
        }
    }
}

@Composable
private fun FileActionsMenu(
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = appIconPainter(AppIcon.MoreVert),
                contentDescription = stringResource(R.string.file_item_actions)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Edit),
                        contentDescription = stringResource(R.string.cd_edit_file)
                    )
                },
                onClick = {
                    expanded = false
                    onEditMetadata()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fetch_online_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.CloudDownload),
                        contentDescription = stringResource(R.string.cd_online_metadata)
                    )
                },
                onClick = {
                    expanded = false
                    onFetchOnlineMetadata()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fix_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.AutoFix),
                        contentDescription = stringResource(R.string.cd_batch_fix)
                    )
                },
                onClick = {
                    expanded = false
                    onFixMetadata()
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename_file)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Rename),
                        contentDescription = stringResource(R.string.cd_batch_rename)
                    )
                },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_viewer_delete)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Close),
                        contentDescription = stringResource(R.string.cd_delete_file),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleFileRenameDialog(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(audioFile.path) { mutableStateOf(audioFile.name.substringBeforeLast(".")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.rename_file)) },
        text = {
            Column {
                Text(
                    text = audioFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Loads album art from multiple sources:
 * 1. Embedded album art from the audio file
 * 2. MediaStore album art
 * Returns null if no album art is found.
 */
private fun loadAlbumArt(
    context: android.content.Context,
    audioFile: AudioFile
): android.graphics.Bitmap? {
    // First try embedded album art from the file
    val embeddedArt = loadEmbeddedAlbumArt(context, audioFile.path)
    if (embeddedArt != null) {
        return embeddedArt
    }

    // Then try MediaStore album art
    if (audioFile.mediaStoreAlbumId != null && audioFile.mediaStoreAlbumId > 0L) {
        val mediaStoreArt = loadMediaStoreAlbumBitmap(context, audioFile.mediaStoreAlbumId)
        if (mediaStoreArt != null) {
            return mediaStoreArt
        }
    }

    return null
}

/**
 * Loads embedded album art directly from the audio file using MediaMetadataRetriever.
 */
private fun loadEmbeddedAlbumArt(context: android.content.Context, filePath: String): android.graphics.Bitmap? {
    return runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                decodeThumbnailBitmap(artBytes)
            } else {
                null
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()
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
    return decodeBitmapFromBytes(bytes, targetSizePx)
}

@Composable
private fun DirectoryItem(
    directory: SelectedDirectory,
    fileCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = appIconPainter(AppIcon.Folder),
                contentDescription = stringResource(R.string.cd_directory),
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
            }
            Text(
                text = "$fileCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Batch Operations Menu Dialog (when files are selected)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchOperationsMenuDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Edit), contentDescription = stringResource(R.string.cd_edit_file)) },
        title = { Text(stringResource(R.string.batch_operations)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Menu items
                Column {
                    BatchMenuItem(
                        icon = AppIcon.CloudDownload,
                        label = stringResource(R.string.batch_online_metadata),
                        onClick = onOnlineMetadata
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BatchMenuItem(
                        icon = AppIcon.Edit,
                        label = stringResource(R.string.batch_unified_field),
                        onClick = onUnifiedField
                    )
                    
                    BatchMenuItem(
                        icon = AppIcon.AutoFix,
                        label = stringResource(R.string.batch_replace_text),
                        onClick = onReplaceText
                    )
                    
                    BatchMenuItem(
                        icon = AppIcon.Schedule,
                        label = stringResource(R.string.batch_auto_number),
                        onClick = onAutoNumber
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BatchMenuItem(
                        icon = AppIcon.Rename,
                        label = stringResource(R.string.batch_rename_files),
                        onClick = onRenameFiles
                    )
                    
                    BatchMenuItem(
                        icon = AppIcon.Check,
                        label = stringResource(R.string.batch_fix_metadata),
                        onClick = onFixMetadata
                    )
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
private fun BatchMenuItem(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                painter = appIconPainter(icon),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/**
 * Unified Field Dialog - Set a field to the same value for all selected files
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedFieldDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (field: String, value: String) -> Unit
) {
    var selectedField by remember { mutableStateOf("artist") }
    var fieldValue by remember { mutableStateOf("") }
    
    val fields = listOf(
        "artist" to stringResource(R.string.metadata_artist),
        "album" to stringResource(R.string.metadata_album),
        "albumArtist" to stringResource(R.string.metadata_album_artist),
        "year" to stringResource(R.string.metadata_year),
        "genre" to stringResource(R.string.metadata_genre),
        "composer" to stringResource(R.string.metadata_composer)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Edit), contentDescription = stringResource(R.string.cd_edit_file)) },
        title = { Text(stringResource(R.string.batch_unified_field_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Field selector
                Text(
                    text = stringResource(R.string.select_field),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Column {
                    fields.forEach { (fieldKey, fieldLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedField = fieldKey }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedField == fieldKey,
                                onClick = { selectedField = fieldKey }
                            )
                            Text(
                                text = fieldLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Value input
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = { Text(stringResource(R.string.field_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedField, fieldValue) },
                enabled = fieldValue.isNotBlank()
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Replace Text Dialog - Find and replace text in fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceTextDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (field: String, searchText: String, replaceText: String, useRegex: Boolean) -> Unit
) {
    var selectedField by remember { mutableStateOf("title") }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    
    val fields = listOf(
        "title" to stringResource(R.string.metadata_title),
        "artist" to stringResource(R.string.metadata_artist),
        "album" to stringResource(R.string.metadata_album),
        "all" to stringResource(R.string.all_fields)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.AutoFix), contentDescription = stringResource(R.string.cd_batch_fix)) },
        title = { Text(stringResource(R.string.batch_replace_text_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Field selector
                Text(
                    text = stringResource(R.string.select_field),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Column {
                    fields.forEach { (fieldKey, fieldLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedField = fieldKey }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedField == fieldKey,
                                onClick = { selectedField = fieldKey }
                            )
                            Text(
                                text = fieldLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search text
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.search_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Replace text
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text(stringResource(R.string.replace_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Use regex option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useRegex = !useRegex }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = useRegex,
                        onCheckedChange = { useRegex = it }
                    )
                    Text(
                        text = stringResource(R.string.use_regex),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedField, searchText, replaceText, useRegex) },
                enabled = searchText.isNotBlank()
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Auto Number Dialog - Generate sequential track numbers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoNumberDialog(
    targetFilesCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (startNumber: Int, step: Int, totalTracks: Int?) -> Unit
) {
    var startNumber by remember { mutableIntStateOf(1) }
    var step by remember { mutableIntStateOf(1) }
    var setTotalTracks by remember { mutableStateOf(false) }
    var totalTracks by remember { mutableIntStateOf(targetFilesCount) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(painter = appIconPainter(AppIcon.Schedule), contentDescription = stringResource(R.string.replay_gain_scan)) },
        title = { Text(stringResource(R.string.batch_auto_number_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.batch_target_files, targetFilesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Start number
                OutlinedTextField(
                    value = startNumber.toString(),
                    onValueChange = { startNumber = it.toIntOrNull() ?: 1 },
                    label = { Text(stringResource(R.string.start_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Step
                OutlinedTextField(
                    value = step.toString(),
                    onValueChange = { step = it.toIntOrNull() ?: 1 },
                    label = { Text(stringResource(R.string.step)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Set total tracks option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setTotalTracks = !setTotalTracks }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = setTotalTracks,
                        onCheckedChange = { setTotalTracks = it }
                    )
                    Text(
                        text = stringResource(R.string.set_total_tracks),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // Total tracks input
                if (setTotalTracks) {
                    OutlinedTextField(
                        value = totalTracks.toString(),
                        onValueChange = { totalTracks = it.toIntOrNull() ?: targetFilesCount },
                        label = { Text(stringResource(R.string.total_tracks)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Preview
                val preview = (0..minOf(2, targetFilesCount - 1)).joinToString(", ") { index ->
                    (startNumber + index * step).toString()
                } + if (targetFilesCount > 3) ", ..." else ""
                
                Text(
                    text = stringResource(R.string.number_preview, preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(startNumber, step, if (setTotalTracks) totalTracks else null) 
                }
            ) {
                Text(stringResource(R.string.batch_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
