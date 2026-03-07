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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotionTokens
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
    onNavigateToReplayGain: (List<String>) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Get directory files from the ViewModel
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val selectedDirectories by viewModel.selectedDirectories.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    // Find the directory info
    val directory = remember(directoryUri, selectedDirectories) {
        selectedDirectories.firstOrNull { it.uri == directoryUri }
    }

    // Get files for this directory
    val files = remember(directoryUri, directoryFiles) {
        directoryFiles[directoryUri].orEmpty()
    }

    val listState = rememberLazyListState()
    val canScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    // Get directory name from path
    val directoryName = remember(directory) {
        directory?.path?.substringAfterLast("/") ?: directory?.path?.substringAfterLast(":") ?: "Unknown"
    }

    // Dialog states
    var showBatchMenu by remember { mutableStateOf(false) }
    var showOnlineMetadataDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFixMetadataDialog by remember { mutableStateOf(false) }
    var showUnifiedFieldDialog by remember { mutableStateOf(false) }
    var showReplaceTextDialog by remember { mutableStateOf(false) }
    var showAutoNumberDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<AudioFile?>(null) }
    var deleteTargetFile by remember { mutableStateOf<AudioFile?>(null) }

    val isSelectionMode = selectedFiles.isNotEmpty()
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchError by viewModel.batchError.collectAsState()

    Scaffold(
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
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        floatingActionButton = {
            if (!isBatchProcessing && files.isNotEmpty()) {
                if (isSelectionMode || canScrollToTop) {
                    // Show selection actions when in selection mode
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isSelectionMode) {
                            SmallFloatingActionButton(
                                onClick = {
                                    showBatchMenu = true
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.batch_operations)
                                )
                            }
                        }
                        if (canScrollToTop) {
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
                } else {
                    BatchOperationsFAB(
                        expanded = showBatchMenu,
                        onExpandChange = { showBatchMenu = it },
                        onOnlineMetadata = {
                            showBatchMenu = false
                            showOnlineMetadataDialog = true
                        },
                        onUnifiedField = {
                            showBatchMenu = false
                            showUnifiedFieldDialog = true
                        },
                        onReplaceText = {
                            showBatchMenu = false
                            showReplaceTextDialog = true
                        },
                        onAutoNumber = {
                            showBatchMenu = false
                            showAutoNumberDialog = true
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
                }
            }
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
                        isSelected = audioFile.path in selectedFiles,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleFileSelection(audioFile.path)
                            } else {
                                onNavigateToMetadata(audioFile.path)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleFileSelection(audioFile.path)
                        }
                    )
                }
            }
        }
    }

    // Batch operation dialogs would go here
    // For now, they call viewModel methods directly
}

/**
 * Batch Operations FAB with expandable menu (Speed Dial style)
 */
@Composable
private fun BatchOperationsFAB(
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

                // Unified Field
                MenuItem(
                    label = stringResource(R.string.batch_unified_field),
                    icon = AppIcon.Edit,
                    onClick = onUnifiedField
                )

                // Replace Text
                MenuItem(
                    label = stringResource(R.string.batch_replace_text),
                    icon = AppIcon.AutoFix,
                    onClick = onReplaceText
                )

                // Auto Number
                MenuItem(
                    label = stringResource(R.string.batch_auto_number),
                    icon = AppIcon.Schedule,
                    onClick = onAutoNumber
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
