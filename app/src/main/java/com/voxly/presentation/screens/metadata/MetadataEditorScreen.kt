package com.voxly.presentation.screens.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.MetadataEditorUiState
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.ConvertibleField
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.voxly.presentation.ui.decodeBitmapFromBytes
import com.voxly.presentation.ui.loadImageBitmapFromUrl

/**
 * Metadata editor screen for viewing and editing audio file metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    filePath: String,
    viewModel: MetadataEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    pendingOnlineMetadata: com.voxly.domain.model.AudioMetadata? = null,
    onConsumePendingOnlineMetadata: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    var showAlbumArtOptions by remember { mutableStateOf(false) }
    var showAlbumArtPreview by remember { mutableStateOf(false) }
    var showOnlineLyricsDialog by remember { mutableStateOf(false) }
    var showOnlineCoverDialog by remember { mutableStateOf(false) }
    var showConversionMenu by remember { mutableStateOf(false) }
    var showConversionDialog by remember { mutableStateOf(false) }
    var conversionType by remember { mutableStateOf(ConversionType.TO_SIMPLIFIED) }
    var showActionMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // ReplayGain state from ViewModel
    val isScanningReplayGain by viewModel.isScanningReplayGain.collectAsState()
    val pendingReplayGainInfo by viewModel.pendingReplayGainInfo.collectAsState()
    var currentReplayGainInfo by remember { mutableStateOf<com.voxly.domain.model.ReplayGainInfo?>(null) }
    val onlineLyricsResults by viewModel.onlineLyricsResults.collectAsState()
    val isOnlineLyricsLoading by viewModel.isOnlineLyricsLoading.collectAsState()
    val onlineLyricsError by viewModel.onlineLyricsError.collectAsState()
    val onlineCoverResults by viewModel.onlineCoverResults.collectAsState()
    val isOnlineCoverLoading by viewModel.isOnlineCoverLoading.collectAsState()
    val onlineCoverError by viewModel.onlineCoverError.collectAsState()
    val coverFetchMessage by viewModel.coverFetchMessage.collectAsState()

    LaunchedEffect(coverFetchMessage) {
        coverFetchMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearCoverFetchMessage()
        }
    }

    LaunchedEffect(pendingOnlineMetadata) {
        val metadata = pendingOnlineMetadata ?: return@LaunchedEffect
        viewModel.applyOnlineMetadata(metadata)
        onConsumePendingOnlineMetadata()
    }

    // Handle save result
    LaunchedEffect(saveResult) {
        if (saveResult is com.voxly.presentation.viewmodel.SaveResult.Success) {
            if (exitAfterSave) {
                exitAfterSave = false
                onNavigateBack()
            }
            viewModel.clearSaveResult()
        } else if (saveResult is com.voxly.presentation.viewmodel.SaveResult.Error) {
            exitAfterSave = false
            viewModel.clearSaveResult()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val bytes = uri?.let { readBytesFromUri(context, it) }
        bytes?.let { viewModel.updateAlbumArt(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { image ->
            bitmapToJpegBytes(image)?.let { bytes -> viewModel.updateAlbumArt(bytes) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_metadata)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Chinese conversion dropdown menu
                    Box {
                        IconButton(onClick = { showConversionMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = showConversionMenu,
                            onDismissRequest = { showConversionMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.convert_to_simplified)) },
                                onClick = {
                                    showConversionMenu = false
                                    conversionType = ConversionType.TO_SIMPLIFIED
                                    showConversionDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Translate, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.convert_to_traditional)) },
                                onClick = {
                                    showConversionMenu = false
                                    conversionType = ConversionType.TO_TRADITIONAL
                                    showConversionDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Translate, contentDescription = null)
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.saveMetadata() },
                        enabled = hasUnsavedChanges && uiState !is MetadataEditorUiState.Saving
                    ) {
                        if (uiState is MetadataEditorUiState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.dialog_save))
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {
                // 半透明遮罩 - 淡入淡出动画
                AnimatedVisibility(
                    visible = showActionMenu,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showActionMenu = false }
                    )
                }
                // 悬浮按钮列表 - 从底部滑入动画
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 88.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AnimatedVisibility(
                        visible = showActionMenu,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(200)
                        ) + fadeOut(animationSpec = tween(200))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 在线获取歌词
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_online_lyrics),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FloatingActionButton(
                                    onClick = {
                                        showActionMenu = false
                                        onNavigateToOnlineLyricsSearch()
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        painter = appIconPainter(AppIcon.MusicNote),
                                        contentDescription = null
                                    )
                                }
                            }
                            // 获取在线元数据
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.fetch_online_metadata),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FloatingActionButton(
                                    onClick = {
                                        showActionMenu = false
                                        onNavigateToOnlineMetadata()
                                    },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Icon(
                                        painter = appIconPainter(AppIcon.CloudDownload),
                                        contentDescription = null
                                    )
                                }
                            }
                            // 保存
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.dialog_save),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FloatingActionButton(
                                    onClick = {
                                        showActionMenu = false
                                        if (hasUnsavedChanges && uiState !is MetadataEditorUiState.Saving) {
                                            viewModel.saveMetadata()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                }
                            }
                        }
                    }
                }
                // 主 FAB 按钮 - 带图标切换动画
                FloatingActionButton(
                    onClick = { showActionMenu = !showActionMenu },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    AnimatedContent(
                        targetState = showActionMenu,
                        transitionSpec = {
                            (scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)))
                                .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)))
                        },
                        label = "fab_icon"
                    ) { isExpanded ->
                        Icon(
                            painter = appIconPainter(
                                if (isExpanded) AppIcon.Close else AppIcon.MoreVert
                            ),
                            contentDescription = stringResource(
                                if (isExpanded) R.string.cd_close else R.string.cd_more_options
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is MetadataEditorUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MetadataEditorUiState.Saving -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.saving_metadata))
                        }
                    }
                }
                is MetadataEditorUiState.Success -> {
                    // Load ReplayGain info from audio file on first load
                    LaunchedEffect(state.audioFile.path) {
                        if (currentReplayGainInfo == null && pendingReplayGainInfo == null) {
                            currentReplayGainInfo = state.audioFile.replayGainInfo
                        }
                    }
                    
                    MetadataForm(
                        metadata = state.editedMetadata,
                        audioFile = state.audioFile,
                        onTitleChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.TITLE, it) },
                        onArtistChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ARTIST, it) },
                        onAlbumChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ALBUM, it) },
                        onAlbumArtistChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ALBUM_ARTIST, it) },
                        onYearChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.YEAR, it) },
                        onGenreChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.GENRE, it) },
                        onComposerChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.COMPOSER, it) },
                        onLyricistChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.LYRICIST, it) },
                        onCommentChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.COMMENT, it) },
                        onRecordLabelChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.RECORD_LABEL, it) },
                        onEncoderChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ENCODER, it) },
                        onIsrcChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.ISRC, it) },
                        onCopyrightChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.COPYRIGHT, it) },
                        onLyricsChange = { viewModel.updateMetadataField(com.voxly.presentation.viewmodel.MetadataField.LYRICS, it) },
                        onTrackNumberChange = { track, total ->
                            viewModel.updateTrackNumber(
                                track.toIntOrNull(),
                                total.toIntOrNull()
                            )
                        },
                        onDiscNumberChange = { disc, total ->
                            viewModel.updateDiscNumber(
                                disc.toIntOrNull(),
                                total.toIntOrNull()
                            )
                        },
                        onPickAlbumArt = { showAlbumArtOptions = true },
                        onZoomAlbumArt = { showAlbumArtPreview = true },
                        onRotateAlbumArt = {
                            state.editedMetadata.albumArt?.let { bytes ->
                                rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
                            }
                        },
                        onRemoveAlbumArt = { viewModel.updateAlbumArt(null) },
                        onScanReplayGain = {
                            viewModel.scanReplayGain()
                        },
                        onClearReplayGain = {
                            viewModel.clearReplayGainInfo()
                            currentReplayGainInfo = null
                        },
                        isScanningReplayGain = isScanningReplayGain,
                        replayGainInfo = pendingReplayGainInfo ?: currentReplayGainInfo
                    )
                }
                is MetadataEditorUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = appIconPainter(AppIcon.Error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.dialog_unsaved_changes)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    exitAfterSave = true
                    viewModel.saveMetadata()
                }) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.discardChanges()
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.dialog_discard))
                }
            }
        )
    }

    if (showAlbumArtOptions) {
        val hasAlbumArt = (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt != null
        
        ModalBottomSheet(
            onDismissRequest = { showAlbumArtOptions = false },
            shape = MaterialTheme.shapes.large,
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.album_art_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.select_album_art)) },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAlbumArtOptions = false
                        galleryPickerLauncher.launch("image/*")
                    }
                )
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.take_photo)) },
                    leadingContent = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAlbumArtOptions = false
                        cameraLauncher.launch(null)
                    }
                )
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.fetch_online_cover_art)) },
                    leadingContent = {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAlbumArtOptions = false
                        onNavigateToOnlineCoverSearch()
                    }
                )
                
                // 只有存在封面时才显示以下选项
                if (hasAlbumArt) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.album_art_view)) },
                        leadingContent = {
                            Icon(Icons.Default.ZoomIn, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            showAlbumArtOptions = false
                            showAlbumArtPreview = true
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.album_art_rotate)) },
                        leadingContent = {
                            Icon(Icons.Default.RotateRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            showAlbumArtOptions = false
                            (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt?.let { bytes ->
                                rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
                            }
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.remove_album_art)) },
                        leadingContent = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            showAlbumArtOptions = false
                            viewModel.updateAlbumArt(null)
                        }
                    )
                }
            }
        }
    }

    if (showAlbumArtPreview) {
        val previewBytes = (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt
        if (previewBytes != null) {
            AlertDialog(
                onDismissRequest = { showAlbumArtPreview = false },
                shape = MaterialTheme.shapes.large,
                title = { Text(stringResource(R.string.metadata_album_art)) },
                text = {
                    val preview = remember(previewBytes) { decodeAlbumArtPreview(previewBytes, 2048) }
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_album_art),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.no_album_art))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAlbumArtPreview = false }) {
                        Text(stringResource(R.string.dialog_close))
                    }
                }
            )
        }
    }

    if (showOnlineLyricsDialog) {
        AlertDialog(
            onDismissRequest = {
                showOnlineLyricsDialog = false
                viewModel.clearOnlineLyricsResults()
            },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.search_online_lyrics)) },
            text = {
                when {
                    isOnlineLyricsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    !onlineLyricsError.isNullOrBlank() -> {
                        Text(
                            text = onlineLyricsError.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    onlineLyricsResults.isEmpty() -> {
                        Text(stringResource(R.string.error_no_results))
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(onlineLyricsResults) { item ->
                                ListItem(
                                    headlineContent = { Text(item.trackName) },
                                    supportingContent = {
                                        Text("${item.artistName} • ${item.albumName ?: "-"} • ${item.source}")
                                    },
                                    trailingContent = {
                                        if (item.hasSyncedLyrics) {
                                            Text("LRC", color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        viewModel.applyOnlineLyrics(item)
                                        showOnlineLyricsDialog = false
                                        viewModel.clearOnlineLyricsResults()
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.searchOnlineLyrics()
                    }
                ) {
                    Text(stringResource(R.string.fetch_online_metadata))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOnlineLyricsDialog = false
                        viewModel.clearOnlineLyricsResults()
                    }
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showOnlineCoverDialog) {
        AlertDialog(
            onDismissRequest = {
                showOnlineCoverDialog = false
                viewModel.clearOnlineCoverResults()
            },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.fetch_online_cover_art)) },
            text = {
                when {
                    isOnlineCoverLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    !onlineCoverError.isNullOrBlank() -> {
                        Text(
                            text = onlineCoverError.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    onlineCoverResults.isEmpty() -> {
                        Text(stringResource(R.string.error_no_results))
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(onlineCoverResults) { item ->
                        ListItem(
                            leadingContent = {
                                CoverCandidateThumbnail(
                                    coverArtUrl = item.coverArtUrl,
                                    modifier = Modifier.size(56.dp)
                                )
                            },
                            headlineContent = { Text(item.title) },
                            supportingContent = {
                                Text("${item.artist} • ${item.source}")
                            },
                            modifier = Modifier.clickable {
                                viewModel.applyOnlineCover(item)
                                showOnlineCoverDialog = false
                                viewModel.clearOnlineCoverResults()
                            }
                        )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.searchOnlineCoverCandidates() }
                ) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOnlineCoverDialog = false
                        viewModel.clearOnlineCoverResults()
                    }
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // Chinese conversion dialog
    if (showConversionDialog) {
        ConversionDialog(
            conversionType = conversionType,
            onDismiss = { showConversionDialog = false },
            onConfirm = { selectedFields ->
                if (conversionType == ConversionType.TO_SIMPLIFIED) {
                    viewModel.convertToSimplified(selectedFields)
                } else {
                    viewModel.convertToTraditional(selectedFields)
                }
                showConversionDialog = false
            }
        )
    }
}

@Composable
private fun MetadataForm(
    metadata: com.voxly.domain.model.AudioMetadata,
    audioFile: com.voxly.domain.model.AudioFile,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onLyricistChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onRecordLabelChange: (String) -> Unit,
    onEncoderChange: (String) -> Unit,
    onIsrcChange: (String) -> Unit,
    onCopyrightChange: (String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onTrackNumberChange: (String, String) -> Unit,
    onDiscNumberChange: (String, String) -> Unit,
    onPickAlbumArt: () -> Unit,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    onScanReplayGain: () -> Unit = {},
    onClearReplayGain: () -> Unit = {},
    isScanningReplayGain: Boolean = false,
    replayGainInfo: com.voxly.domain.model.ReplayGainInfo? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Album Art Section
        AlbumArtSection(
            albumArt = metadata.albumArt,
            onPickAlbumArt = onPickAlbumArt,
            onZoomAlbumArt = onZoomAlbumArt,
            onRotateAlbumArt = onRotateAlbumArt,
            onRemoveAlbumArt = onRemoveAlbumArt
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Information
        SectionTitle(stringResource(R.string.basic_information))

        OutlinedTextField(
            value = metadata.title ?: "",
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.metadata_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.artist ?: "",
            onValueChange = onArtistChange,
            label = { Text(stringResource(R.string.metadata_artist)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.album ?: "",
            onValueChange = onAlbumChange,
            label = { Text(stringResource(R.string.metadata_album)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.albumArtist ?: "",
            onValueChange = onAlbumArtistChange,
            label = { Text(stringResource(R.string.metadata_album_artist)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Track Information
        SectionTitle(stringResource(R.string.track_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.trackNumber?.toString() ?: "",
                onValueChange = { onTrackNumberChange(it, metadata.totalTracks?.toString() ?: "") },
                label = { Text(stringResource(R.string.label_track)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalTracks?.toString() ?: "",
                onValueChange = { onTrackNumberChange(metadata.trackNumber?.toString() ?: "", it) },
                label = { Text(stringResource(R.string.label_total)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.discNumber?.toString() ?: "",
                onValueChange = { onDiscNumberChange(it, metadata.totalDiscs?.toString() ?: "") },
                label = { Text(stringResource(R.string.label_disc)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.totalDiscs?.toString() ?: "",
                onValueChange = { onDiscNumberChange(metadata.discNumber?.toString() ?: "", it) },
                label = { Text(stringResource(R.string.label_total)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Information
        SectionTitle(stringResource(R.string.additional_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.year ?: "",
                onValueChange = onYearChange,
                label = { Text(stringResource(R.string.metadata_year)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.genre ?: "",
                onValueChange = onGenreChange,
                label = { Text(stringResource(R.string.metadata_genre)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.composer ?: "",
            onValueChange = onComposerChange,
            label = { Text(stringResource(R.string.metadata_composer)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.lyricist ?: "",
            onValueChange = onLyricistChange,
            label = { Text(stringResource(R.string.metadata_lyricist)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.customFields["record_label"] ?: "",
            onValueChange = onRecordLabelChange,
            label = { Text(stringResource(R.string.metadata_record_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.customFields["encoder"] ?: "",
                onValueChange = onEncoderChange,
                label = { Text(stringResource(R.string.metadata_encoder)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = metadata.customFields["isrc"] ?: "",
                onValueChange = onIsrcChange,
                label = { Text(stringResource(R.string.metadata_isrc)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.customFields["copyright"] ?: "",
            onValueChange = onCopyrightChange,
            label = { Text(stringResource(R.string.metadata_copyright)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.comment ?: "",
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.metadata_comment)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics Section
        SectionTitle(stringResource(R.string.lyrics_section_title))

        OutlinedTextField(
            value = metadata.lyrics ?: "",
            onValueChange = onLyricsChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            label = { Text(stringResource(R.string.edit_lyrics)) },
            placeholder = { Text(stringResource(R.string.no_lyrics_added)) },
            minLines = 6
        )

        Spacer(modifier = Modifier.height(16.dp))

        // File Information (read-only)
        SectionTitle(stringResource(R.string.file_information))

        FileInfoRow(stringResource(R.string.file_info_format), audioFile.format)
        FileInfoRow(stringResource(R.string.metadata_bitrate), "${audioFile.bitrate} kbps")
        FileInfoRow(stringResource(R.string.metadata_sample_rate), "${audioFile.sampleRate} Hz")
        FileInfoRow(stringResource(R.string.file_info_channels), audioFile.channels.toString())
        FileInfoRow(stringResource(R.string.metadata_duration), audioFile.getFormattedDuration())
        FileInfoRow(stringResource(R.string.file_info_size), audioFile.getFormattedSize())

        Spacer(modifier = Modifier.height(16.dp))

        // ReplayGain Section (collapsible)
        ReplayGainSection(
            replayGainInfo = replayGainInfo,
            isScanning = isScanningReplayGain,
            onScan = onScanReplayGain,
            onClear = onClearReplayGain
        )

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReplayGainSection(
    replayGainInfo: com.voxly.domain.model.ReplayGainInfo?,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header - clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = appIconPainter(AppIcon.Equalizer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.replay_gain_section_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
                )
            }
            
            // Expanded content
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                if (isScanning) {
                    // Scanning state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.replay_gain_scanning),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (replayGainInfo != null) {
                    // Display existing ReplayGain values
                    ReplayGainRow(
                        label = stringResource(R.string.replay_gain_track),
                        value = replayGainInfo.getFormattedTrackGain()
                    )
                    ReplayGainRow(
                        label = stringResource(R.string.replay_gain_peak),
                        value = replayGainInfo.getFormattedTrackPeak()
                    )
                    replayGainInfo.albumGain?.let { albumGain ->
                        ReplayGainRow(
                            label = stringResource(R.string.replay_gain_album),
                            value = String.format("%.2f dB", albumGain)
                        )
                    }
                    replayGainInfo.albumPeak?.let { albumPeak ->
                        ReplayGainRow(
                            label = stringResource(R.string.replay_gain_album_peak),
                            value = String.format("%.4f", albumPeak)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onScan,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.replay_gain_rescan))
                        }
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.replay_gain_clear))
                        }
                    }
                } else {
                    // No ReplayGain info
                    Text(
                        text = stringResource(R.string.replay_gain_no_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.replay_gain_scan))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayGainRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AlbumArtSection(
    albumArt: ByteArray?,
    onPickAlbumArt: () -> Unit,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onPickAlbumArt),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Use Crossfade for smooth album art transitions
            Crossfade(
                targetState = albumArt != null,
                label = "album_art_crossfade"
            ) { hasArt ->
                if (hasArt && albumArt != null) {
                    val bitmap = remember(albumArt.contentHashCode()) {
                        decodeAlbumArtPreview(albumArt)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_album_art),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        EmptyAlbumArtContent()
                    }
                } else {
                    EmptyAlbumArtContent()
                }
            }
        }
    }
}

@Composable
private fun EmptyAlbumArtContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            painter = appIconPainter(AppIcon.MusicNote),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tap_to_add_album_art),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun decodeAlbumArtPreview(
    bytes: ByteArray,
    targetSizePx: Int = 1024
): android.graphics.Bitmap? {
    return decodeBitmapFromBytes(bytes, targetSizePx)
}

@Composable
private fun CoverCandidateThumbnail(
    coverArtUrl: String?,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = coverArtUrl) {
        value = loadImageBitmapFromUrl(coverArtUrl)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}

private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray? {
    return runCatching {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), output)
        output.toByteArray()
    }.getOrNull()
}

private fun rotateJpegBytes(bytes: ByteArray, degrees: Float): ByteArray? {
    return runCatching {
        val src = decodeBitmapFromBytes(bytes)
            ?: throw IllegalArgumentException("Invalid image bytes")
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        bitmapToJpegBytes(rotated) ?: throw IllegalStateException("Failed to encode rotated image")
    }.getOrNull()
}

/**
 * Conversion type for Chinese character conversion.
 */
private enum class ConversionType {
    TO_SIMPLIFIED,
    TO_TRADITIONAL
}

/**
 * Dialog for selecting metadata fields to convert.
 */
@Composable
private fun ConversionDialog(
    conversionType: ConversionType,
    onDismiss: () -> Unit,
    onConfirm: (Set<ConvertibleField>) -> Unit
) {
    var selectedFields by remember { 
        mutableStateOf(
            setOf(
                ConvertibleField.TITLE,
                ConvertibleField.ARTIST,
                ConvertibleField.ALBUM,
                ConvertibleField.ALBUM_ARTIST,
                ConvertibleField.GENRE,
                ConvertibleField.COMPOSER,
                ConvertibleField.LYRICIST,
                ConvertibleField.COMMENT,
                ConvertibleField.RECORD_LABEL,
                ConvertibleField.COPYRIGHT,
                ConvertibleField.LYRICS
            )
        )
    }

    val title = if (conversionType == ConversionType.TO_SIMPLIFIED) {
        stringResource(R.string.convert_to_simplified)
    } else {
        stringResource(R.string.convert_to_traditional)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_fields_to_convert),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Select All / Deselect All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            selectedFields = ConvertibleField.entries.toSet()
                        }
                    ) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(
                        onClick = {
                            selectedFields = emptySet()
                        }
                    ) {
                        Text(stringResource(R.string.deselect_all))
                    }
                }
                
                HorizontalDivider()
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(ConvertibleField.entries.toList()) { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFields = if (field in selectedFields) {
                                        selectedFields - field
                                    } else {
                                        selectedFields + field
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = field in selectedFields,
                                onCheckedChange = { checked ->
                                    selectedFields = if (checked) {
                                        selectedFields + field
                                    } else {
                                        selectedFields - field
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(field.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedFields) },
                enabled = selectedFields.isNotEmpty()
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
