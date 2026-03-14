package com.voxly.presentation.screens.filebrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.SearchBottomSheet
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotionTokens
import com.voxly.presentation.viewmodel.FileBrowserViewModel
import com.voxly.presentation.screens.filebrowser.FixMetadataOptions
import com.voxly.presentation.screens.filebrowser.OnlineMetadataOptions
import java.text.Collator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryContentScreen(
    directoryUri: String,
    directoryName: String,
    initialFiles: List<String> = emptyList(),
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToReplayGain: (List<String>) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Get directory files from the ViewModel
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    // Use initialFiles passed via navigation, fallback to ViewModel data if available
    val files = remember(directoryUri, directoryFiles, initialFiles) {
        if (initialFiles.isNotEmpty()) {
            // Try to match with ViewModel data to get full AudioFile objects
            initialFiles.mapNotNull { path ->
                directoryFiles[directoryUri]?.find { it.path == path }
            }.ifEmpty {
                // If no match in ViewModel, try to use paths from other directories or create minimal entries
                initialFiles.mapNotNull { path ->
                    directoryFiles.values.flatten().find { it.path == path }
                }
            }
        } else {
            directoryFiles[directoryUri].orEmpty()
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val canScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    // Dialog states

    // Search states - using BottomSheet
    var showSearchSheet by remember { mutableStateOf(false) }
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }

    var isSortExpanded by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(DirFileSortOption.NAME_ASC) }

    // Apply search and sort to files
    val displayedFiles = remember(files, searchQuery, currentSortOption) {
        applySearchAndSort(files, searchQuery, currentSortOption)
    }

    var showOnlineMetadataDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showUnifiedFieldDialog by remember { mutableStateOf(false) }
    var showReplaceTextDialog by remember { mutableStateOf(false) }
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var showSingleEditMetadataDialog by remember { mutableStateOf(false) }
    var showSingleRenameDialog by remember { mutableStateOf(false) }
    var showSingleDeleteDialog by remember { mutableStateOf(false) }
    var showSingleOnlineMetadataDialog by remember { mutableStateOf(false) }
    var showSingleFixMetadataDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var deleteTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var currentActionFile by remember { mutableStateOf<AudioFile?>(null) }

    val isSelectionMode = selectedFiles.isNotEmpty()
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchError by viewModel.batchError.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
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
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        TextButton(onClick = {
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
                        // Search and Sort buttons row
                        Row(
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Search button - opens BottomSheet
                            IconButton(onClick = {
                                showSearchSheet = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search)
                                )
                            }
                            // Sort button
                            Box {
                                IconButton(onClick = {
                                    isSortExpanded = true
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.sort)
                                    )
                                }
                                SortMenu(
                                    isExpanded = isSortExpanded,
                                    currentSortOption = currentSortOption,
                                    onSortOptionChange = { option ->
                                        currentSortOption = option
                                    },
                                    onDismiss = { isSortExpanded = false }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isBatchProcessing && files.isNotEmpty() && canScrollToTop) {
                SmallFloatingActionButton(
                    onClick = {
                        // Scroll to top
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.back_to_top)
                    )
                }
            }
        }
    ) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Search BottomSheet
        if (showSearchSheet) {
            SearchBottomSheet(
                sheetState = searchSheetState,
                onDismiss = {
                    showSearchSheet = false
                },
                allFiles = files,
                onFileClick = { audioFile ->
                    showSearchSheet = false
                    onNavigateToMetadata(audioFile.path, null)
                }
            )
        }

        if (files.isEmpty()) {
            DirectoryEmptyContent(
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AudioFileListWithIndexer(
                files = displayedFiles,
                listState = listState,
                selectedFiles = selectedFiles,
                onFileClick = { audioFile ->
                    if (isSelectionMode) {
                        viewModel.toggleFileSelection(audioFile.path)
                    } else {
                        onNavigateToMetadata(audioFile.path, null)
                    }
                },
                onFileLongClick = { audioFile ->
                    viewModel.toggleFileSelection(audioFile.path)
                },
                onEditFileMetadata = { audioFile ->
                    currentActionFile = audioFile
                    showSingleEditMetadataDialog = true
                },
                onRenameFile = { audioFile ->
                    currentActionFile = audioFile
                    showSingleRenameDialog = true
                },
                onDeleteFile = { audioFile ->
                    currentActionFile = audioFile
                    showSingleDeleteDialog = true
                },
                onFetchOnlineMetadata = { audioFile ->
                    currentActionFile = audioFile
                    showSingleOnlineMetadataDialog = true
                },
                onFixMetadata = { audioFile ->
                    currentActionFile = audioFile
                    showSingleFixMetadataDialog = true
                },
                bottomPadding = 80.dp
            )
        }

        // FloatingToolbar for batch operations - only show in selection mode
        if (!isBatchProcessing && files.isNotEmpty() && isSelectionMode) {
            BatchOperationsToolbar(
                isSelectionMode = isSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                onOnlineMetadata = {
                    showOnlineMetadataDialog = true
                },
                onUnifiedField = {
                    showUnifiedFieldDialog = true
                },
                onReplaceText = {
                    showReplaceTextDialog = true
                },
                onAutoNumber = {
                    showAutoNumberDialog = true
                },
                onRenameFiles = {
                    showRenameDialog = true
                },
                onFixMetadata = {
                    showFixMetadataDialog = true
                }
            )
        }
    }
    }

    // Single file action dialogs
    if (showSingleEditMetadataDialog && currentActionFile != null) {
        // Navigate to metadata editor
        LaunchedEffect(currentActionFile) {
            showSingleEditMetadataDialog = false
            onNavigateToMetadata(currentActionFile!!.path, null)
            currentActionFile = null
        }
    }

    if (showSingleRenameDialog && currentActionFile != null) {
        SingleFileRenameDialog(
            audioFile = currentActionFile!!,
            onDismiss = {
                showSingleRenameDialog = false
                currentActionFile = null
            },
            onConfirm = { newName ->
                viewModel.renameSingleFile(currentActionFile!!.path, newName) { _, _ -> }
                showSingleRenameDialog = false
                currentActionFile = null
            }
        )
    }

    if (showSingleDeleteDialog && currentActionFile != null) {
        AlertDialog(
            onDismissRequest = {
                showSingleDeleteDialog = false
                currentActionFile = null
            },
            title = { Text(stringResource(R.string.dialog_confirm_delete)) },
            text = { Text(stringResource(R.string.dialog_confirm_delete_single_file_message, currentActionFile!!.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSingleFile(currentActionFile!!.path) { _, _ -> }
                        showSingleDeleteDialog = false
                        currentActionFile = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.log_viewer_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSingleDeleteDialog = false
                    currentActionFile = null
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showSingleOnlineMetadataDialog && currentActionFile != null) {
        LaunchedEffect(currentActionFile) {
            viewModel.batchFetchOnlineMetadata(
                listOf(currentActionFile!!.path),
                OnlineMetadataOptions(
                    overwriteExisting = false,
                    fetchAlbumArt = true,
                    fetchLyrics = true
                )
            )
            showSingleOnlineMetadataDialog = false
            currentActionFile = null
        }
    }

    if (showSingleFixMetadataDialog && currentActionFile != null) {
        LaunchedEffect(currentActionFile) {
            viewModel.batchFixMetadata(
                listOf(currentActionFile!!.path),
                FixMetadataOptions(
                    autoTitleCase = true,
                    removeExtraSpaces = true,
                    fixTrackNumbers = true,
                    removeEmptyTags = true
                )
            )
            showSingleFixMetadataDialog = false
            currentActionFile = null
        }
    }

    // Batch operations dialogs
    val targetFiles = if (isSelectionMode) selectedFiles else files.map { it.path }.toSet()
    val targetFilesCount = targetFiles.size

    if (showOnlineMetadataDialog) {
        BatchOnlineMetadataDialog(
            targetFilesCount = targetFilesCount,
            onDismiss = { showOnlineMetadataDialog = false },
            onConfirm = { options ->
                viewModel.batchFetchOnlineMetadata(targetFiles.toList(), options)
                showOnlineMetadataDialog = false
            }
        )
    }

    if (showRenameDialog) {
        BatchRenameDialog(
            targetFilesCount = targetFilesCount,
            onDismiss = { showRenameDialog = false },
            onConfirm = { pattern, startNumber ->
                viewModel.batchRenameFiles(targetFiles.toList(), pattern, startNumber)
                showRenameDialog = false
            }
        )
    }

    if (showFixMetadataDialog) {
        BatchFixMetadataDialog(
            targetFilesCount = targetFilesCount,
            onDismiss = { showFixMetadataDialog = false },
            onConfirm = { options ->
                viewModel.batchFixMetadata(targetFiles.toList(), options)
                showFixMetadataDialog = false
            }
        )
    }

    if (showUnifiedFieldDialog) {
        var field by remember { mutableStateOf("artist") }
        var fieldValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showUnifiedFieldDialog = false },
            title = { Text(stringResource(R.string.batch_unified_field)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.batch_target_files, targetFilesCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        label = { Text(stringResource(R.string.select_field)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        label = { Text(stringResource(R.string.field_value)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchSetUnifiedField(targetFiles.toList(), field, fieldValue)
                        showUnifiedFieldDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnifiedFieldDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showReplaceTextDialog) {
        var field by remember { mutableStateOf("title") }
        var searchText by remember { mutableStateOf("") }
        var replaceText by remember { mutableStateOf("") }
        var useRegex by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showReplaceTextDialog = false },
            title = { Text(stringResource(R.string.batch_replace_text)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.batch_target_files, targetFilesCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        label = { Text(stringResource(R.string.select_field)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text(stringResource(R.string.search_text)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text(stringResource(R.string.replace_text)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchReplaceText(
                            targetFiles.toList(),
                            field,
                            searchText,
                            replaceText,
                            useRegex
                        )
                        showReplaceTextDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceTextDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showAutoNumberDialog) {
        var startNumber by remember { mutableIntStateOf(1) }
        var step by remember { mutableIntStateOf(1) }

        AlertDialog(
            onDismissRequest = { showAutoNumberDialog = false },
            title = { Text(stringResource(R.string.batch_auto_number)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.batch_target_files, targetFilesCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = startNumber.toString(),
                        onValueChange = { startNumber = it.toIntOrNull() ?: 1 },
                        label = { Text(stringResource(R.string.start_number)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = step.toString(),
                        onValueChange = { step = it.toIntOrNull() ?: 1 },
                        label = { Text(stringResource(R.string.step)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchAutoNumberTracks(targetFiles.toList(), startNumber, step, null)
                        showAutoNumberDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoNumberDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

/**
 * Batch Operations FAB with expandable menu (Speed Dial style)
 * for DirectoryContentScreen
 */
@Composable
fun DirectoryBatchOperationsFAB(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Menu items
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
            )) + expandVertically(expandFrom = Alignment.Bottom, animationSpec = spring(
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
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_online_metadata),
                    icon = AppIcon.CloudDownload,
                    onClick = onOnlineMetadata
                )

                // Unified Field
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_unified_field),
                    icon = AppIcon.Edit,
                    onClick = onUnifiedField
                )

                // Replace Text
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_replace_text),
                    icon = AppIcon.AutoFix,
                    onClick = onReplaceText
                )

                // Auto Number
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_auto_number),
                    icon = AppIcon.Schedule,
                    onClick = onAutoNumber
                )

                // Rename Files
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_rename_files),
                    icon = AppIcon.Rename,
                    onClick = onRenameFiles
                )

                // Fix Metadata
                DirectoryMenuItem(
                    label = stringResource(R.string.batch_fix_metadata),
                    icon = AppIcon.Check,
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
fun DirectoryMenuItem(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(horizontal = 8.dp)
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

// Chinese collator for proper sorting
private val chineseCollator = Collator.getInstance(Locale.CHINA).apply {
    strength = Collator.PRIMARY
}

// Sort options for directory content
private enum class DirFileSortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}

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

private fun applySort(
    files: List<AudioFile>,
    sortOption: DirFileSortOption
): List<AudioFile> {
    return when (sortOption) {
        DirFileSortOption.NAME_ASC -> files.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        DirFileSortOption.NAME_DESC -> files.sortedWith(compareByDescending(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        DirFileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        DirFileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(
    isExpanded: Boolean,
    currentSortOption: DirFileSortOption,
    onSortOptionChange: (DirFileSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismiss
        ) {
            DirFileSortOption.entries.forEach { option ->
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

private fun DirFileSortOption.labelResId(): Int = when (this) {
    DirFileSortOption.NAME_ASC -> R.string.file_sort_name_asc
    DirFileSortOption.NAME_DESC -> R.string.file_sort_name_desc
    DirFileSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    DirFileSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
}
