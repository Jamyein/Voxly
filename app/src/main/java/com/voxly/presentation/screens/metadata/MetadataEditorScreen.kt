package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.saf.SafGrantType
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

import com.voxly.presentation.viewmodel.MetadataEditorUiState
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.MetadataField
import com.voxly.presentation.viewmodel.ConvertibleField
import com.voxly.presentation.viewmodel.EditHistoryViewModel
import com.voxly.domain.repository.RecentEdit
import com.voxly.presentation.viewmodel.ReplayGainScanError

/**
 * Metadata editor screen for viewing and editing audio file metadata.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
    kotlinx.coroutines.FlowPreview::class
)
@Composable
fun MetadataEditorScreen(
    filePath: String,
    viewModel: MetadataEditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (lyricsText: String, title: String, artist: String, album: String, albumArtBytes: ByteArray?) -> Unit,
    coverTag: String? = null,
    sharedElementKey: String? = null,
    pendingOnlineLyrics: String? = null,
    onConsumePendingOnlineLyrics: () -> Unit = {},
    pendingOnlineCoverArt: ByteArray? = null,
    onConsumePendingOnlineCoverArt: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editedMetadata by viewModel.editedMetadata.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val hasUnsavedChanges = editState.hasUnsavedChanges
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle(initialValue = "")
    val modifiedFields = editState.modifiedFields

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAlbumArtOptions by remember { mutableStateOf(false) }
    var showAlbumArtPreview by remember { mutableStateOf(false) }
    var showConversionDialog by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    val moreOptionsSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var showReauthorizeDialog by remember { mutableStateOf(false) }
    var conversionType by remember { mutableStateOf(ConversionType.TO_SIMPLIFIED) }
    var exitAfterSave by remember { mutableStateOf(false) }

    var showEditHistorySheet by remember { mutableStateOf(false) }
    val editHistorySheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val editHistoryViewModel: EditHistoryViewModel = hiltViewModel()
    val allRecentEdits by editHistoryViewModel.recentEdits.collectAsStateWithLifecycle()
    val currentFileEdits = remember(allRecentEdits, filePath) {
        allRecentEdits.filter { it.filePath == filePath }
    }

    val coverFetchMessage by viewModel.coverFetchMessage.collectAsStateWithLifecycle(initialValue = null)
    val isLyricsTimestampFormatted by viewModel.isLyricsTimestampFormatted.collectAsStateWithLifecycle()
    val metadataEditorDynamicAlbumColor by viewModel.metadataEditorDynamicAlbumColor.collectAsStateWithLifecycle()

    LaunchedEffect(coverFetchMessage) {
        coverFetchMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        Timber.d("MetadataEditorScreen: checking pending online metadata for filePath=$filePath")
        viewModel.tryApplyPendingOnlineMetadata()
    }

    LaunchedEffect(pendingOnlineLyrics) {
        val lyricsText = pendingOnlineLyrics ?: return@LaunchedEffect
        viewModel.updateMetadataField(MetadataField.LYRICS, lyricsText)
        onConsumePendingOnlineLyrics()
    }

    LaunchedEffect(pendingOnlineCoverArt) {
        val coverBytes = pendingOnlineCoverArt ?: return@LaunchedEffect
        viewModel.updateAlbumArt(coverBytes)
        onConsumePendingOnlineCoverArt()
    }

    var pendingMediaStoreIntentSender by remember { mutableStateOf<android.content.IntentSender?>(null) }

    val mediaStorePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingMediaStoreIntentSender = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.retrySaveAfterMediaStorePermission()
        }
    }

    LaunchedEffect(saveResult) {
        if (saveResult.isNotBlank()) {
            if (saveResult == "Save successful") {
                if (exitAfterSave) {
                    exitAfterSave = false
                    onNavigateBack()
                }
            }
            exitAfterSave = false
        }
    }

    val handleNavigateBack = {
        focusManager.clearFocus()
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    val discardDialogState = remember { SeekableTransitionState(initialState = false) }

    PredictiveBackHandler(enabled = hasUnsavedChanges) { progress ->
        try {
            progress.collect { backEvent ->
                discardDialogState.seekTo(fraction = backEvent.progress)
            }
            discardDialogState.animateTo(targetState = true)
            showDiscardDialog = true
        } catch (e: CancellationException) {
            discardDialogState.snapTo(targetState = false)
        }
    }

    val launcherState = MetadataEditorLaunchers(
        onImageResult = { bytes -> viewModel.updateAlbumArt(bytes) },
        onTakePhotoResult = { bytes -> viewModel.updateAlbumArt(bytes) },
    )

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
    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    val currentSuccessState = uiState as? MetadataEditorUiState.Success
    val albumArtBytes = editedMetadata?.albumArt ?: currentSuccessState?.editedMetadata?.albumArt
    val mediaStoreAlbumId = currentSuccessState?.audioFile?.mediaStoreAlbumId
    val isDarkTheme = isSystemInDarkTheme()

    val dynamicPalette by produceState<MetadataEditorDynamicPalette?>(
        initialValue = null,
        albumArtBytes?.contentHashCode(),
        mediaStoreAlbumId,
        isDarkTheme,
        metadataEditorDynamicAlbumColor
    ) {
        if (!metadataEditorDynamicAlbumColor) {
            value = null
            return@produceState
        }
        val fallbackBitmap = if (albumArtBytes == null && mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
            withContext(Dispatchers.IO) {
                loadMediaStoreAlbumArt(context, mediaStoreAlbumId)
            }
        } else {
            null
        }
        value = MetadataEditorDynamicPaletteResolver.resolve(
            albumArtBytes = albumArtBytes,
            fallbackBitmap = fallbackBitmap,
            isDarkTheme = isDarkTheme
        )
    }

    MetadataEditorDynamicTheme(dynamicPalette = dynamicPalette) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val onBackgroundColor = MaterialTheme.colorScheme.onBackground

        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(floatingToolbarScrollBehavior)
                .background(backgroundColor),
            topBar = {
                MetadataEditorTopAppBar(
                    onBack = handleNavigateBack,
                    onMoreOptions = { showMoreOptionsSheet = true },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {}
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                tonalElevation = 0.dp
            ) {
                MetadataEditorScaffoldContent(
                uiState = uiState,
                filePath = filePath,
                editedMetadata = editedMetadata,
                audioFile = (uiState as? MetadataEditorUiState.Success)?.audioFile,
                albumArt = editedMetadata?.albumArt,
                mediaStoreAlbumId = (uiState as? MetadataEditorUiState.Success)?.audioFile?.mediaStoreAlbumId,
                bottomPadding = innerPadding.calculateBottomPadding() + 80.dp,
                modifiedFields = modifiedFields,
                coverTag = coverTag,
                hasUnsavedChanges = hasUnsavedChanges,
                viewModel = viewModel,
                onTitleChange = { viewModel.updateDebouncedTextField(MetadataField.TITLE, it) },
                onArtistChange = { viewModel.updateDebouncedTextField(MetadataField.ARTIST, it) },
                onAlbumChange = { viewModel.updateDebouncedTextField(MetadataField.ALBUM, it) },
                onAlbumArtistChange = { viewModel.updateDebouncedTextField(MetadataField.ALBUM_ARTIST, it) },
                onYearChange = { viewModel.updateDebouncedTextField(MetadataField.YEAR, it) },
                onGenreChange = { viewModel.updateDebouncedTextField(MetadataField.GENRE, it) },
                onComposerChange = { viewModel.updateDebouncedTextField(MetadataField.COMPOSER, it) },
                onLyricistChange = { viewModel.updateDebouncedTextField(MetadataField.LYRICIST, it) },
                onCommentChange = { viewModel.updateDebouncedTextField(MetadataField.COMMENT, it) },
                onLyricsChange = { viewModel.updateDebouncedTextField(MetadataField.LYRICS, it) },
                onTrackNumberChange = { track, total ->
                    viewModel.updateTrackNumber(track.toIntOrNull(), total.toIntOrNull())
                },
                onDiscNumberChange = { disc, total ->
                    viewModel.updateDiscNumber(disc.toIntOrNull(), total.toIntOrNull())
                },
                onPickAlbumArt = { showAlbumArtOptions = true },
                onZoomAlbumArt = { showAlbumArtPreview = true },
                onRotateAlbumArt = {
                    editedMetadata?.albumArt?.let { bytes ->
                        rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
                    }
                },
                onRemoveAlbumArt = { viewModel.updateAlbumArt(null) },
                onScanReplayGain = { viewModel.scanReplayGain() },
                onClearReplayGain = { viewModel.clearReplayGainInfo() },
                onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                onNavigateToLyricsSelector = onNavigateToLyricsSelector,
                onSave = { viewModel.saveMetadata() },
                onSyncDebouncedFields = {
                    val metadata = (uiState as? MetadataEditorUiState.Success)?.editedMetadata ?: return@MetadataEditorScaffoldContent
                    viewModel.updateDebouncedTextField(MetadataField.TITLE, metadata.title)
                    viewModel.updateDebouncedTextField(MetadataField.ARTIST, metadata.artist)
                    viewModel.updateDebouncedTextField(MetadataField.ALBUM, metadata.album)
                    viewModel.updateDebouncedTextField(MetadataField.ALBUM_ARTIST, metadata.albumArtist)
                    viewModel.updateDebouncedTextField(MetadataField.YEAR, metadata.year)
                    viewModel.updateDebouncedTextField(MetadataField.GENRE, metadata.genre)
                    viewModel.updateDebouncedTextField(MetadataField.COMPOSER, metadata.composer)
                    viewModel.updateDebouncedTextField(MetadataField.LYRICIST, metadata.lyricist)
                    viewModel.updateDebouncedTextField(MetadataField.COMMENT, metadata.comment)
                    viewModel.updateDebouncedTextField(MetadataField.LYRICS, metadata.lyrics)
                },
                floatingToolbarScrollBehavior = floatingToolbarScrollBehavior,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
    }

    MetadataEditorDialogsAndSheets(
        showEditHistorySheet = showEditHistorySheet,
        onShowEditHistorySheetChange = { showEditHistorySheet = it },
        editHistorySheetState = editHistorySheetState,
        currentFileEdits = currentFileEdits,
        showMoreOptionsSheet = showMoreOptionsSheet,
        onShowMoreOptionsSheetChange = { showMoreOptionsSheet = it },
        moreOptionsSheetState = moreOptionsSheetState,
        isLyricsTimestampFormatted = isLyricsTimestampFormatted,
        hasLyrics = editedMetadata?.lyrics?.isNotBlank() == true,
    showDiscardDialog = showDiscardDialog,
    onShowDiscardDialogChange = { showDiscardDialog = it },
    onSaveFromDiscardDialog = {
        viewModel.saveMetadata()
    },
    onDiscardChanges = {
        viewModel.discardChanges()
        onNavigateBack()
    },
        showReauthorizeDialog = showReauthorizeDialog,
        onShowReauthorizeDialogChange = { showReauthorizeDialog = it },
        onReauthorizeDirectory = { reauthorizeDirectoryLauncher.launch(null) },
        onReauthorizeFile = { reauthorizeFileLauncher.launch(arrayOf("audio/*", "*/*")) },
        showAlbumArtOptions = showAlbumArtOptions,
        onShowAlbumArtOptionsChange = { showAlbumArtOptions = it },
        hasAlbumArt = editedMetadata?.albumArt != null,
        onPickFromGallery = { launcherState.galleryPickerLauncher.launch("image/*") },
        onTakePhoto = { launcherState.cameraLauncher.launch(null) },
        onFetchOnlineCover = onNavigateToOnlineCoverSearch,
        onViewArt = { showAlbumArtPreview = true },
        onRotateArt = {
            editedMetadata?.albumArt?.let { bytes ->
                rotateJpegBytes(bytes, 90f)?.let { rotated -> viewModel.updateAlbumArt(rotated) }
            }
        },
        onRemoveArt = { viewModel.updateAlbumArt(null) },
        showAlbumArtPreview = showAlbumArtPreview,
        onShowAlbumArtPreviewChange = { showAlbumArtPreview = it },
        previewAlbumArt = editedMetadata?.albumArt,
        audioFilePath = (uiState as? MetadataEditorUiState.Success)?.audioFile?.path,
        showConversionDialog = showConversionDialog,
        onShowConversionDialogChange = { showConversionDialog = it },
        conversionType = conversionType,
        onConversionTypeChange = { conversionType = it },
        onConvert = { selectedFields ->
            if (conversionType == ConversionType.TO_SIMPLIFIED) {
                viewModel.convertToSimplified(selectedFields)
            } else {
                viewModel.convertToTraditional(selectedFields)
            }
        },
        onToggleLyricsTimestampFormat = { viewModel.toggleLyricsTimestampFormat() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MetadataEditorScaffoldContent(
    uiState: MetadataEditorUiState,
    filePath: String,
    editedMetadata: com.voxly.domain.model.AudioMetadata?,
    audioFile: com.voxly.domain.model.AudioFile?,
    albumArt: ByteArray?,
    mediaStoreAlbumId: Long?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifiedFields: Set<MetadataField>,
    coverTag: String?,
    hasUnsavedChanges: Boolean,
    viewModel: MetadataEditorViewModel,
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
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    onScanReplayGain: () -> Unit,
    onClearReplayGain: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    onSave: () -> Unit,
    onSyncDebouncedFields: () -> Unit,
    floatingToolbarScrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var showLoadingIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is MetadataEditorUiState.Loading) {
            delay(200L)
            showLoadingIndicator = true
        } else {
            showLoadingIndicator = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is MetadataEditorUiState.Loading -> {
                if (showLoadingIndicator) {
                    LoadingIndicator()
                }
            }
            is MetadataEditorUiState.Saving -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.saving_metadata))
                }
            }
            is MetadataEditorUiState.Success -> {
                MetadataEditorSuccessContent(
                    filePath = filePath,
                    state = state,
                    editedMetadata = editedMetadata ?: state.editedMetadata,
                    albumArt = albumArt,
                    mediaStoreAlbumId = mediaStoreAlbumId,
                    bottomPadding = bottomPadding,
                    modifiedFields = modifiedFields,
                    coverTag = coverTag,
                    hasUnsavedChanges = hasUnsavedChanges,
                    viewModel = viewModel,
                    onTitleChange = onTitleChange,
                    onArtistChange = onArtistChange,
                    onAlbumChange = onAlbumChange,
                    onAlbumArtistChange = onAlbumArtistChange,
                    onYearChange = onYearChange,
                    onGenreChange = onGenreChange,
                    onComposerChange = onComposerChange,
                    onLyricistChange = onLyricistChange,
                    onCommentChange = onCommentChange,
                    onLyricsChange = onLyricsChange,
                    onTrackNumberChange = onTrackNumberChange,
                    onDiscNumberChange = onDiscNumberChange,
                    onPickAlbumArt = onPickAlbumArt,
                    onZoomAlbumArt = onZoomAlbumArt,
                    onRotateAlbumArt = onRotateAlbumArt,
                    onRemoveAlbumArt = onRemoveAlbumArt,
                    onScanReplayGain = onScanReplayGain,
                    onClearReplayGain = onClearReplayGain,
                    onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                    onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                    onNavigateToLyricsSelector = onNavigateToLyricsSelector,
                    onSave = onSave,
                    onSyncDebouncedFields = onSyncDebouncedFields,
                    floatingToolbarScrollBehavior = floatingToolbarScrollBehavior,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
            is MetadataEditorUiState.Error -> {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MetadataEditorSuccessContent(
    filePath: String,
    state: MetadataEditorUiState.Success,
    editedMetadata: com.voxly.domain.model.AudioMetadata,
    albumArt: ByteArray?,
    mediaStoreAlbumId: Long?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifiedFields: Set<MetadataField>,
    coverTag: String?,
    hasUnsavedChanges: Boolean,
    viewModel: MetadataEditorViewModel,
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
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    onScanReplayGain: () -> Unit,
    onClearReplayGain: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    onSave: () -> Unit,
    onSyncDebouncedFields: () -> Unit,
    floatingToolbarScrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    val shouldEnhanceToolbarShadow by remember {
        derivedStateOf { scrollState.isScrollInProgress || scrollState.value > 0 }
    }

    val mediaStoreFallbackBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = mediaStoreAlbumId,
        key2 = albumArt
    ) {
        value = if (albumArt == null && mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
            withContext(Dispatchers.IO) {
                loadMediaStoreAlbumArt(context, mediaStoreAlbumId)
            }
        } else {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MetadataFormContent(
            metadata = editedMetadata,
            audioFile = state.audioFile,
            albumArtFallback = mediaStoreFallbackBitmap,
            bottomPadding = bottomPadding,
            scrollState = scrollState,
            nestedScrollModifier = Modifier,
            modifiedFields = modifiedFields,
            onTitleChange = onTitleChange,
            onArtistChange = onArtistChange,
            onAlbumChange = onAlbumChange,
            onAlbumArtistChange = onAlbumArtistChange,
            onYearChange = onYearChange,
            onGenreChange = onGenreChange,
            onComposerChange = onComposerChange,
            onLyricistChange = onLyricistChange,
            onCommentChange = onCommentChange,
            onLyricsChange = onLyricsChange,
            onTrackNumberChange = onTrackNumberChange,
            onDiscNumberChange = onDiscNumberChange,
            onPickAlbumArt = onPickAlbumArt,
            coverTag = coverTag,
            onZoomAlbumArt = onZoomAlbumArt,
            onRotateAlbumArt = onRotateAlbumArt,
            onRemoveAlbumArt = onRemoveAlbumArt,
            replayGainSection = {
                MetadataReplayGainSection(
                    viewModel = viewModel,
                    onScanReplayGain = onScanReplayGain,
                    onClearReplayGain = onClearReplayGain
                )
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp)
        ) {
            val toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer
            val toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
                toolbarContainerColor = toolbarContainerColor,
                toolbarContentColor = toolbarContentColor,
                fabContainerColor = MaterialTheme.colorScheme.primary,
                fabContentColor = MaterialTheme.colorScheme.onPrimary
            )

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                scrollBehavior = floatingToolbarScrollBehavior,
                colors = toolbarColors,
                expandedShadowElevation = if (shouldEnhanceToolbarShadow) 12.dp else 0.dp,
                collapsedShadowElevation = if (shouldEnhanceToolbarShadow) 6.dp else 0.dp
            ) {
                val hasLyrics = editedMetadata.lyrics?.isNotBlank() == true
                if (hasLyrics) {
                    IconButton(
                        onClick = {
                            onNavigateToLyricsSelector(
                                editedMetadata.lyrics,
                                editedMetadata.getDisplayTitle(state.audioFile.name),
                                editedMetadata.artist ?: "",
                                editedMetadata.album ?: "",
                                editedMetadata.albumArt
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = stringResource(R.string.select_lyrics_for_poster),
                            tint = toolbarContentColor
                        )
                    }
                }

                IconButton(onClick = onNavigateToOnlineLyricsSearch) {
                    Icon(
                        painter = appIconPainter(AppIcon.MusicNote),
                        contentDescription = stringResource(R.string.cd_online_lyrics),
                        tint = toolbarContentColor
                    )
                }

                IconButton(onClick = onNavigateToOnlineMetadata) {
                    Icon(
                        painter = appIconPainter(AppIcon.CloudDownload),
                        contentDescription = stringResource(R.string.cd_online_metadata),
                        tint = toolbarContentColor
                    )
                }

                IconButton(
                    onClick = {
                        if (hasUnsavedChanges) {
                            onSave()
                        }
                    },
                    enabled = hasUnsavedChanges
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.cd_save),
                        tint = toolbarContentColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataEditorDialogsAndSheets(
    showEditHistorySheet: Boolean,
    onShowEditHistorySheetChange: (Boolean) -> Unit,
    editHistorySheetState: androidx.compose.material3.SheetState,
    currentFileEdits: List<RecentEdit>,
    showMoreOptionsSheet: Boolean,
    onShowMoreOptionsSheetChange: (Boolean) -> Unit,
    moreOptionsSheetState: androidx.compose.material3.SheetState,
    isLyricsTimestampFormatted: Boolean,
    hasLyrics: Boolean,
    showDiscardDialog: Boolean,
    onShowDiscardDialogChange: (Boolean) -> Unit,
    onSaveFromDiscardDialog: () -> Unit,
    onDiscardChanges: () -> Unit,
    showReauthorizeDialog: Boolean,
    onShowReauthorizeDialogChange: (Boolean) -> Unit,
    onReauthorizeDirectory: () -> Unit,
    onReauthorizeFile: () -> Unit,
    showAlbumArtOptions: Boolean,
    onShowAlbumArtOptionsChange: (Boolean) -> Unit,
    hasAlbumArt: Boolean,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onFetchOnlineCover: () -> Unit,
    onViewArt: () -> Unit,
    onRotateArt: () -> Unit,
    onRemoveArt: () -> Unit,
    showAlbumArtPreview: Boolean,
    onShowAlbumArtPreviewChange: (Boolean) -> Unit,
    previewAlbumArt: ByteArray?,
    audioFilePath: String?,
    showConversionDialog: Boolean,
    onShowConversionDialogChange: (Boolean) -> Unit,
    conversionType: ConversionType,
    onConversionTypeChange: (ConversionType) -> Unit,
    onConvert: (Set<ConvertibleField>) -> Unit,
    onToggleLyricsTimestampFormat: () -> Unit
) {
    if (showEditHistorySheet) {
        EditHistorySheet(
            sheetState = editHistorySheetState,
            onDismiss = { onShowEditHistorySheetChange(false) },
            recentEdits = currentFileEdits
        )
    }

    if (showMoreOptionsSheet) {
        MoreOptionsSheet(
            sheetState = moreOptionsSheetState,
            onDismiss = { onShowMoreOptionsSheetChange(false) },
            onEditHistoryClick = {
                onShowMoreOptionsSheetChange(false)
                onShowEditHistorySheetChange(true)
            },
            onConvertToSimplifiedClick = {
                onShowMoreOptionsSheetChange(false)
                onConversionTypeChange(ConversionType.TO_SIMPLIFIED)
                onShowConversionDialogChange(true)
            },
            onConvertToTraditionalClick = {
                onShowMoreOptionsSheetChange(false)
                onConversionTypeChange(ConversionType.TO_TRADITIONAL)
                onShowConversionDialogChange(true)
            },
            onToggleLyricsTimestampClick = {
                onShowMoreOptionsSheetChange(false)
                onToggleLyricsTimestampFormat()
            },
            isLyricsTimestampFormatted = isLyricsTimestampFormatted,
            hasLyrics = hasLyrics
        )
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDismiss = { onShowDiscardDialogChange(false) },
            onSave = {
                onShowDiscardDialogChange(false)
                onSaveFromDiscardDialog()
            },
            onDiscard = {
                onShowDiscardDialogChange(false)
                onDiscardChanges()
            }
        )
    }

    if (showReauthorizeDialog) {
        ReauthorizeDialog(
            onDismiss = { onShowReauthorizeDialogChange(false) },
            onSelectDirectory = {
                onShowReauthorizeDialogChange(false)
                onReauthorizeDirectory()
            },
            onSelectFile = {
                onShowReauthorizeDialogChange(false)
                onReauthorizeFile()
            }
        )
    }

    if (showAlbumArtOptions) {
        AlbumArtOptionsSheet(
            hasAlbumArt = hasAlbumArt,
            onDismiss = { onShowAlbumArtOptionsChange(false) },
            onPickFromGallery = {
                onShowAlbumArtOptionsChange(false)
                onPickFromGallery()
            },
            onTakePhoto = {
                onShowAlbumArtOptionsChange(false)
                onTakePhoto()
            },
            onFetchOnline = {
                onShowAlbumArtOptionsChange(false)
                onFetchOnlineCover()
            },
            onViewArt = {
                onShowAlbumArtOptionsChange(false)
                onViewArt()
            },
            onRotateArt = {
                onShowAlbumArtOptionsChange(false)
                onRotateArt()
            },
            onRemoveArt = {
                onShowAlbumArtOptionsChange(false)
                onRemoveArt()
            }
        )
    }

    if (showAlbumArtPreview) {
        AlbumArtPreviewDialog(
            albumArt = previewAlbumArt,
            filePath = audioFilePath,
            onDismiss = { onShowAlbumArtPreviewChange(false) }
        )
    }

    if (showConversionDialog) {
        ConversionDialog(
            conversionType = conversionType,
            onDismiss = { onShowConversionDialogChange(false) },
            onConfirm = { selectedFields ->
                onConvert(selectedFields)
                onShowConversionDialogChange(false)
            }
        )
    }
}

@Composable
private fun MetadataReplayGainSection(
    viewModel: MetadataEditorViewModel,
    onScanReplayGain: () -> Unit,
    onClearReplayGain: () -> Unit
) {
    val isScanningReplayGain by viewModel.isScanningReplayGain.collectAsStateWithLifecycle()
    val pendingReplayGainInfo by viewModel.pendingReplayGainInfo.collectAsStateWithLifecycle()
    val replayGainScanError by viewModel.replayGainScanError.collectAsStateWithLifecycle(initialValue = null)

    ReplayGainSection(
        replayGainInfo = pendingReplayGainInfo,
        isScanning = isScanningReplayGain,
        onScan = onScanReplayGain,
        onClear = onClearReplayGain,
        error = replayGainScanError
    )
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
    metadata: com.voxly.domain.model.AudioMetadata,
    audioFile: com.voxly.domain.model.AudioFile,
    albumArtFallback: android.graphics.Bitmap? = null,
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
    replayGainSection: (@Composable () -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    nestedScrollModifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(nestedScrollModifier)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding)
    ) {
        AlbumArtSection(
            albumArt = metadata.albumArt,
            fallbackBitmap = albumArtFallback,
            onPickAlbumArt = onPickAlbumArt,
            coverTag = coverTag,
            onZoomAlbumArt = onZoomAlbumArt,
            onRotateAlbumArt = onRotateAlbumArt,
            onRemoveAlbumArt = onRemoveAlbumArt,
            filePath = audioFile.path,
            formatLabel = audioFile.format.displayName,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedVisibilityScope
        )

        Spacer(modifier = Modifier.height(16.dp))

        MetadataFieldsSection(
            metadata = metadata,
            modifiedFields = modifiedFields,
            onTitleChange = onTitleChange,
            onArtistChange = onArtistChange,
            onAlbumChange = onAlbumChange,
            onAlbumArtistChange = onAlbumArtistChange,
            onYearChange = onYearChange,
            onGenreChange = onGenreChange,
            onComposerChange = onComposerChange,
            onLyricistChange = onLyricistChange,
            onCommentChange = onCommentChange,
onLyricsChange = onLyricsChange,
            onTrackNumberChange = onTrackNumberChange,
            onDiscNumberChange = onDiscNumberChange,
            audioFile = audioFile
        )

        Spacer(modifier = Modifier.height(16.dp))

        replayGainSection?.invoke()
    }
}

@Composable
private fun MetadataEditorTopAppBar(
    onBack: () -> Unit,
    onMoreOptions: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(R.string.edit_metadata)) },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        navigationIcon = {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        },
        actions = {
            IconButton(onClick = onMoreOptions) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
        }
    )
}

data class LauncherState(
    val galleryPickerLauncher: ActivityResultLauncher<String>,
    val cameraLauncher: ActivityResultLauncher<Void?>,
)

@Composable
private fun MetadataEditorLaunchers(
    onImageResult: (ByteArray) -> Unit,
    onTakePhotoResult: (ByteArray) -> Unit,
): LauncherState {
    val context = LocalContext.current
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val bytes = uri?.let { readBytesFromUri(context, it) }
        bytes?.let { onImageResult(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { image ->
            bitmapToJpegBytes(image)?.let { onTakePhotoResult(it) }
        }
    }

    return LauncherState(galleryPickerLauncher, cameraLauncher)
}
