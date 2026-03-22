package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import com.voxly.presentation.theme.ExpressiveMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.saf.SafGrantType
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.components.lyricsposter.LyricsPosterPreviewSheet
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.viewmodel.MetadataEditorUiState
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.MetadataField
import com.voxly.presentation.viewmodel.EditHistoryViewModel

/**
 * Metadata editor screen for viewing and editing audio file metadata.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetadataEditorScreen(
    filePath: String,
    viewModel: MetadataEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (lyricsText: String, title: String, artist: String, album: String, albumArtBytes: ByteArray?) -> Unit,
    coverTag: String? = null,
    pendingOnlineMetadata: AudioMetadata? = null,
    onConsumePendingOnlineMetadata: () -> Unit = {},
    pendingOnlineLyrics: String? = null,
    onConsumePendingOnlineLyrics: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val modifiedFields by viewModel.modifiedFields.collectAsState()

    // Dialog visibility states
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAlbumArtOptions by remember { mutableStateOf(false) }
    var showAlbumArtPreview by remember { mutableStateOf(false) }
    var showConversionDialog by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    val moreOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReauthorizeDialog by remember { mutableStateOf(false) }
    var conversionType by remember { mutableStateOf(ConversionType.TO_SIMPLIFIED) }
    var exitAfterSave by remember { mutableStateOf(false) }
    var showLyricsPosterPreview by remember { mutableStateOf(false) }
    var selectedLyricsIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedLyricsText by remember { mutableStateOf<List<String>>(emptyList()) }

    // ReplayGain state from ViewModel
    val isScanningReplayGain by viewModel.isScanningReplayGain.collectAsState()
    val pendingReplayGainInfo by viewModel.pendingReplayGainInfo.collectAsState()
    var currentReplayGainInfo by remember { mutableStateOf<ReplayGainInfo?>(null) }

    // EditHistory state - filter to current file only
    var showEditHistorySheet by remember { mutableStateOf(false) }
    val editHistorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editHistoryViewModel: EditHistoryViewModel = hiltViewModel()
    val allRecentEdits by editHistoryViewModel.recentEdits.collectAsState()
    val currentFileEdits = remember(allRecentEdits, filePath) {
        allRecentEdits.filter { it.filePath == filePath }
    }

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

    // Handle online lyrics result from search screen
    LaunchedEffect(pendingOnlineLyrics) {
        val lyricsText = pendingOnlineLyrics ?: return@LaunchedEffect
        viewModel.updateMetadataField(MetadataField.LYRICS, lyricsText)
        onConsumePendingOnlineLyrics()
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
            val error = saveResult as com.voxly.presentation.viewmodel.SaveResult.Error
            if (error.requiresReauthorization) {
                showReauthorizeDialog = true
            }
            viewModel.clearSaveResult()
        }
    }

    // Predictive back handler for Android 14+ back gesture
    var backProgress by remember { mutableFloatStateOf(0f) }
    val animatedBackProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = ExpressiveMotion.SlowSpring,
        label = "backProgress"
    )

    PredictiveBackHandler(enabled = hasUnsavedChanges) { progress ->
        progress.collect { backEvent ->
            backProgress = backEvent.progress
        }
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
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

    val reauthorizeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.reauthorizeAndRetrySave(it, SafGrantType.DOCUMENT) }
    }

    val reauthorizeDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.reauthorizeAndRetrySave(it, SafGrantType.TREE) }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // FloatingToolbar scroll behavior using official M3E API
    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .graphicsLayer {
                val scale = 1f - (animatedBackProgress * 0.05f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (animatedBackProgress * 0.3f)
            },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_metadata)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreOptionsSheet = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                }
            )
        },
        floatingActionButton = {}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when (val state = uiState) {
                is MetadataEditorUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                is MetadataEditorUiState.Saving -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.saving_metadata))
                        }
                    }
                }
                is MetadataEditorUiState.Success -> {
                    LaunchedEffect(state.audioFile.path) {
                        if (currentReplayGainInfo == null && pendingReplayGainInfo == null) {
                            currentReplayGainInfo = state.audioFile.replayGainInfo
                        }
                    }

                    // Box with FloatingToolbar at bottom
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Create scroll state for FloatingToolbarScrollBehavior
                        val scrollState = rememberScrollState()

                        MetadataFormContent(
                            metadata = state.editedMetadata,
                            audioFile = state.audioFile,
                            bottomPadding = innerPadding.calculateBottomPadding() + 80.dp, // Extra space for toolbar
                            scrollState = scrollState,
                            nestedScrollModifier = Modifier.nestedScroll(floatingToolbarScrollBehavior),
                            modifiedFields = modifiedFields,
                            onTitleChange = { viewModel.updateMetadataField(MetadataField.TITLE, it) },
                            onArtistChange = { viewModel.updateMetadataField(MetadataField.ARTIST, it) },
                            onAlbumChange = { viewModel.updateMetadataField(MetadataField.ALBUM, it) },
                            onAlbumArtistChange = { viewModel.updateMetadataField(MetadataField.ALBUM_ARTIST, it) },
                            onYearChange = { viewModel.updateMetadataField(MetadataField.YEAR, it) },
                            onGenreChange = { viewModel.updateMetadataField(MetadataField.GENRE, it) },
                            onComposerChange = { viewModel.updateMetadataField(MetadataField.COMPOSER, it) },
                            onLyricistChange = { viewModel.updateMetadataField(MetadataField.LYRICIST, it) },
                            onCommentChange = { viewModel.updateMetadataField(MetadataField.COMMENT, it) },
                            onLyricsChange = { viewModel.updateMetadataField(MetadataField.LYRICS, it) },
                            onTrackNumberChange = { track, total ->
                                viewModel.updateTrackNumber(track.toIntOrNull(), total.toIntOrNull())
                            },
                            onDiscNumberChange = { disc, total ->
                                viewModel.updateDiscNumber(disc.toIntOrNull(), total.toIntOrNull())
                            },
                            onPickAlbumArt = { showAlbumArtOptions = true },
                            coverTag = coverTag,
                            onZoomAlbumArt = { showAlbumArtPreview = true },
                            onRotateAlbumArt = {
                                state.editedMetadata.albumArt?.let { bytes ->
                                    rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
                                }
                            },
                            onRemoveAlbumArt = { viewModel.updateAlbumArt(null) },
                            onScanReplayGain = { viewModel.scanReplayGain() },
                            onClearReplayGain = {
                                viewModel.clearReplayGainInfo()
                                currentReplayGainInfo = null
                            },
                            isScanningReplayGain = isScanningReplayGain,
                            replayGainInfo = pendingReplayGainInfo ?: currentReplayGainInfo
                        )

                        // Toolbar at bottom, above content
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                        ) {
                            HorizontalFloatingToolbar(
                                expanded = true,
                                scrollBehavior = floatingToolbarScrollBehavior,
                                colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
                            ) {
                                // Using IconButton per official M3E FloatingToolbar API
                                // Lyrics Selection (only show if there are lyrics)
                                val hasLyrics = state.editedMetadata.lyrics?.isNotBlank() == true
                                if (hasLyrics) {
                                    IconButton(
                                        onClick = {
                                            onNavigateToLyricsSelector(
                                                state.editedMetadata.lyrics ?: "",
                                                state.editedMetadata.getDisplayTitle(state.audioFile.name ?: "") ?: "",
                                                state.editedMetadata.artist ?: "",
                                                state.editedMetadata.album ?: "",
                                                state.editedMetadata.albumArt
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Lyrics,
                                            contentDescription = stringResource(R.string.select_lyrics_for_poster)
                                        )
                                    }
                                }

                                // Search Online Lyrics
                                IconButton(
                                    onClick = { onNavigateToOnlineLyricsSearch() }
                                ) {
                                    Icon(
                                        painter = appIconPainter(AppIcon.MusicNote),
                                        contentDescription = stringResource(R.string.cd_online_lyrics)
                                    )
                                }

                                // Fetch Online Metadata
                                IconButton(
                                    onClick = { onNavigateToOnlineMetadata() }
                                ) {
                                    Icon(
                                        painter = appIconPainter(AppIcon.CloudDownload),
                                        contentDescription = stringResource(R.string.cd_online_metadata)
                                    )
                                }

                                // Save
                                IconButton(
                                    onClick = {
                                        if (hasUnsavedChanges && uiState !is MetadataEditorUiState.Saving) {
                                            viewModel.saveMetadata()
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = stringResource(R.string.cd_save)
                                    )
                                }
                            }
                        }
                    }
                }
                is MetadataEditorUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = appIconPainter(AppIcon.Error),
                                contentDescription = stringResource(R.string.cd_error),
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

    // EditHistory Sheet
    if (showEditHistorySheet) {
        EditHistorySheet(
            sheetState = editHistorySheetState,
            onDismiss = { showEditHistorySheet = false },
            recentEdits = currentFileEdits
        )
    }

    // More Options Sheet
    if (showMoreOptionsSheet) {
        MoreOptionsSheet(
            sheetState = moreOptionsSheetState,
            onDismiss = { showMoreOptionsSheet = false },
            onEditHistoryClick = {
                showMoreOptionsSheet = false
                showEditHistorySheet = true
            },
            onConvertToSimplifiedClick = {
                showMoreOptionsSheet = false
                conversionType = ConversionType.TO_SIMPLIFIED
                showConversionDialog = true
            },
            onConvertToTraditionalClick = {
                showMoreOptionsSheet = false
                conversionType = ConversionType.TO_TRADITIONAL
                showConversionDialog = true
            }
        )
    }

    // Dialogs
    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onSave = {
                showDiscardDialog = false
                viewModel.saveMetadata()
            },
            onDiscard = {
                showDiscardDialog = false
                viewModel.discardChanges()
                onNavigateBack()
            }
        )
    }

    if (showReauthorizeDialog) {
        ReauthorizeDialog(
            onDismiss = { showReauthorizeDialog = false },
            onSelectDirectory = {
                showReauthorizeDialog = false
                reauthorizeDirectoryLauncher.launch(null)
            },
            onSelectFile = {
                showReauthorizeDialog = false
                reauthorizeFileLauncher.launch(arrayOf("audio/*", "*/*"))
            }
        )
    }

    if (showAlbumArtOptions) {
        val hasAlbumArt = (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt != null
        AlbumArtOptionsSheet(
            hasAlbumArt = hasAlbumArt,
            onDismiss = { showAlbumArtOptions = false },
            onPickFromGallery = {
                showAlbumArtOptions = false
                galleryPickerLauncher.launch("image/*")
            },
            onTakePhoto = {
                showAlbumArtOptions = false
                cameraLauncher.launch(null)
            },
            onFetchOnline = {
                showAlbumArtOptions = false
                onNavigateToOnlineCoverSearch()
            },
            onViewArt = {
                showAlbumArtOptions = false
                showAlbumArtPreview = true
            },
            onRotateArt = {
                showAlbumArtOptions = false
                (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt?.let { bytes ->
                    rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
                }
            },
            onRemoveArt = {
                showAlbumArtOptions = false
                viewModel.updateAlbumArt(null)
            }
        )
    }

    if (showAlbumArtPreview) {
        val previewBytes = (uiState as? MetadataEditorUiState.Success)?.editedMetadata?.albumArt
        AlbumArtPreviewDialog(
            albumArt = previewBytes,
            onDismiss = { showAlbumArtPreview = false }
        )
    }

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

    // Lyrics Poster Preview Sheet
    if (showLyricsPosterPreview) {
        val successState = uiState as? MetadataEditorUiState.Success
        if (successState != null) {
            LyricsPosterPreviewSheet(
                title = successState.editedMetadata.getDisplayTitle(successState.audioFile.name),
                artist = successState.editedMetadata.artist ?: "",
                album = successState.editedMetadata.album ?: "",
                lyricsText = successState.editedMetadata.lyrics ?: "",
                albumArtBytes = successState.editedMetadata.albumArt,
                preSelectedLyrics = selectedLyricsText,
                onDismiss = {
                    selectedLyricsIndices = emptySet()
                    selectedLyricsText = emptyList()
                    showLyricsPosterPreview = false
                },
                onShare = { bitmap ->
                    // Share is handled in the sheet
                }
            )
        }
    }
}

/**
 * Generates a field label with modified indicator if the field is in the modified set.
 */
@Composable
private fun fieldLabel(field: MetadataField, baseLabelResId: Int, modifiedFields: Set<MetadataField>): String {
    val baseLabel = stringResource(baseLabelResId)
    return if (field in modifiedFields) {
        baseLabel + stringResource(R.string.field_modified)
    } else {
        baseLabel
    }
}

/**
 * Main metadata form content.
 */
@Composable
private fun MetadataFormContent(
    metadata: AudioMetadata,
    audioFile: com.voxly.domain.model.AudioFile,
    modifiedFields: Set<MetadataField>,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onLyricistChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onTrackNumberChange: (String, String) -> Unit,
    onDiscNumberChange: (String, String) -> Unit,
    onPickAlbumArt: () -> Unit,
    coverTag: String? = null,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    onScanReplayGain: () -> Unit = {},
    onClearReplayGain: () -> Unit = {},
    isScanningReplayGain: Boolean = false,
    replayGainInfo: ReplayGainInfo? = null,
    bottomPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    nestedScrollModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(nestedScrollModifier)
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding)
    ) {
        // Album Art Section
        AlbumArtSection(
            albumArt = metadata.albumArt,
            onPickAlbumArt = onPickAlbumArt,
            coverTag = coverTag,
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
            label = { Text(fieldLabel(MetadataField.TITLE, R.string.metadata_title, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.artist ?: "",
            onValueChange = onArtistChange,
            label = { Text(fieldLabel(MetadataField.ARTIST, R.string.metadata_artist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.album ?: "",
            onValueChange = onAlbumChange,
            label = { Text(fieldLabel(MetadataField.ALBUM, R.string.metadata_album, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.albumArtist ?: "",
            onValueChange = onAlbumArtistChange,
            label = { Text(fieldLabel(MetadataField.ALBUM_ARTIST, R.string.metadata_album_artist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.discNumber?.toString() ?: "",
                onValueChange = { onDiscNumberChange(it, metadata.totalDiscs?.toString() ?: "") },
                label = { Text(stringResource(R.string.label_disc)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Information
        SectionTitle(stringResource(R.string.additional_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.year ?: "",
                onValueChange = onYearChange,
                label = { Text(fieldLabel(MetadataField.YEAR, R.string.metadata_year, modifiedFields)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = metadata.genre ?: "",
                onValueChange = onGenreChange,
                label = { Text(fieldLabel(MetadataField.GENRE, R.string.metadata_genre, modifiedFields)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.composer ?: "",
            onValueChange = onComposerChange,
            label = { Text(fieldLabel(MetadataField.COMPOSER, R.string.metadata_composer, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.lyricist ?: "",
            onValueChange = onLyricistChange,
            label = { Text(fieldLabel(MetadataField.LYRICIST, R.string.metadata_lyricist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = metadata.comment ?: "",
            onValueChange = onCommentChange,
            label = { Text(fieldLabel(MetadataField.COMMENT, R.string.metadata_comment, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = MaterialTheme.shapes.extraLarge
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
            label = { Text(fieldLabel(MetadataField.LYRICS, R.string.edit_lyrics, modifiedFields)) },
            placeholder = { Text(stringResource(R.string.no_lyrics_added)) },
            minLines = 6,
            shape = MaterialTheme.shapes.extraLarge
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
    }
}
