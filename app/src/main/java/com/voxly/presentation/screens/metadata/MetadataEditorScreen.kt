package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

import com.voxly.presentation.components.sharedBoundsIfAvailable
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
    viewModel: MetadataEditorViewModel = hiltViewModel(),
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
) {
    val sharedElementModifier = if (sharedElementKey != null) {
        Modifier.sharedBoundsIfAvailable(key = sharedElementKey)
    } else {
        Modifier
    }
    val context = LocalContext.current
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
    val moreOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReauthorizeDialog by remember { mutableStateOf(false) }
    var conversionType by remember { mutableStateOf(ConversionType.TO_SIMPLIFIED) }
    var exitAfterSave by remember { mutableStateOf(false) }

    var currentReplayGainInfo by remember(filePath) { mutableStateOf<ReplayGainInfo?>(null) }

    var showEditHistorySheet by remember { mutableStateOf(false) }
    val editHistorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editHistoryViewModel: EditHistoryViewModel = hiltViewModel()
    val allRecentEdits by editHistoryViewModel.recentEdits.collectAsStateWithLifecycle()
    val currentFileEdits = remember(allRecentEdits, filePath) {
        allRecentEdits.filter { it.filePath == filePath }
    }

    val coverFetchMessage by viewModel.coverFetchMessage.collectAsStateWithLifecycle(initialValue = null)
    val isLyricsTimestampFormatted by viewModel.isLyricsTimestampFormatted.collectAsStateWithLifecycle()

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
    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(floatingToolbarScrollBehavior),
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
                    IconButton(onClick = handleNavigateBack) {
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
                currentReplayGainInfo = currentReplayGainInfo,
                onCurrentReplayGainInfoChange = { currentReplayGainInfo = it },
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
                onClearReplayGain = {
                    viewModel.clearReplayGainInfo()
                    currentReplayGainInfo = null
                },
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
                floatingToolbarScrollBehavior = floatingToolbarScrollBehavior
            )
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
        onPickFromGallery = { galleryPickerLauncher.launch("image/*") },
        onTakePhoto = { cameraLauncher.launch(null) },
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
    currentReplayGainInfo: ReplayGainInfo?,
    onCurrentReplayGainInfoChange: (ReplayGainInfo?) -> Unit,
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
    modifier: Modifier = Modifier
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
                    currentReplayGainInfo = currentReplayGainInfo,
                    onCurrentReplayGainInfoChange = onCurrentReplayGainInfoChange,
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
                    floatingToolbarScrollBehavior = floatingToolbarScrollBehavior
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
    currentReplayGainInfo: ReplayGainInfo?,
    onCurrentReplayGainInfoChange: (ReplayGainInfo?) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(filePath, state.audioFile.path) {
        onCurrentReplayGainInfoChange(state.audioFile.replayGainInfo)
    }

    LaunchedEffect(filePath) {
        onSyncDebouncedFields()
    }

    val scrollState = rememberScrollState()

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
                    currentReplayGainInfo = currentReplayGainInfo,
                    onScanReplayGain = onScanReplayGain,
                    onClearReplayGain = onClearReplayGain
                )
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp)
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                scrollBehavior = floatingToolbarScrollBehavior,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
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
                            contentDescription = stringResource(R.string.select_lyrics_for_poster)
                        )
                    }
                }

                IconButton(onClick = onNavigateToOnlineLyricsSearch) {
                    Icon(
                        painter = appIconPainter(AppIcon.MusicNote),
                        contentDescription = stringResource(R.string.cd_online_lyrics)
                    )
                }

                IconButton(onClick = onNavigateToOnlineMetadata) {
                    Icon(
                        painter = appIconPainter(AppIcon.CloudDownload),
                        contentDescription = stringResource(R.string.cd_online_metadata)
                    )
                }

                IconButton(
                    onClick = {
                        if (hasUnsavedChanges) {
                            onSave()
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
    currentReplayGainInfo: ReplayGainInfo?,
    onScanReplayGain: () -> Unit,
    onClearReplayGain: () -> Unit
) {
    val isScanningReplayGain by viewModel.isScanningReplayGain.collectAsStateWithLifecycle()
    val pendingReplayGainInfo by viewModel.pendingReplayGainInfo.collectAsStateWithLifecycle()
    val replayGainScanError by viewModel.replayGainScanError.collectAsStateWithLifecycle(initialValue = null)

    ReplayGainSection(
        replayGainInfo = pendingReplayGainInfo ?: currentReplayGainInfo,
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
    albumArtFallback: Bitmap? = null,
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
    nestedScrollModifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    var titleText       by remember(metadata.title)       { mutableStateOf(metadata.title ?: "") }
    var artistText      by remember(metadata.artist)      { mutableStateOf(metadata.artist ?: "") }
    var albumText       by remember(metadata.album)       { mutableStateOf(metadata.album ?: "") }
    var albumArtistText by remember(metadata.albumArtist) { mutableStateOf(metadata.albumArtist ?: "") }
    var yearText        by remember(metadata.year)        { mutableStateOf(metadata.year ?: "") }
    var genreText       by remember(metadata.genre)       { mutableStateOf(metadata.genre ?: "") }
    var composerText    by remember(metadata.composer)    { mutableStateOf(metadata.composer ?: "") }
    var lyricistText    by remember(metadata.lyricist)    { mutableStateOf(metadata.lyricist ?: "") }
    var commentText     by remember(metadata.comment)     { mutableStateOf(metadata.comment ?: "") }
    var lyricsText      by remember(metadata.lyrics)      { mutableStateOf(metadata.lyrics ?: "") }
    var trackNumberText by remember(metadata.trackNumber) { mutableStateOf(metadata.trackNumber?.toString() ?: "") }
    var discNumberText  by remember(metadata.discNumber)  { mutableStateOf(metadata.discNumber?.toString() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(nestedScrollModifier)
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
            filePath = audioFile.path
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.basic_information))

        OutlinedTextField(
            value = titleText,
            onValueChange = {
                titleText = it
                onTitleChange(it)
            },
            label = { Text(fieldLabel(MetadataField.TITLE, R.string.metadata_title, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            enabled = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = artistText,
            onValueChange = {
                artistText = it
                onArtistChange(it)
            },
            label = { Text(fieldLabel(MetadataField.ARTIST, R.string.metadata_artist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = albumText,
            onValueChange = {
                albumText = it
                onAlbumChange(it)
            },
            label = { Text(fieldLabel(MetadataField.ALBUM, R.string.metadata_album, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = albumArtistText,
            onValueChange = {
                albumArtistText = it
                onAlbumArtistChange(it)
            },
            label = { Text(fieldLabel(MetadataField.ALBUM_ARTIST, R.string.metadata_album_artist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.track_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = trackNumberText,
                onValueChange = {
                    trackNumberText = it
                    onTrackNumberChange(it, metadata.totalTracks?.toString() ?: "")
                },
                label = { Text(stringResource(R.string.label_track)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = discNumberText,
                onValueChange = {
                    discNumberText = it
                    onDiscNumberChange(it, metadata.totalDiscs?.toString() ?: "")
                },
                label = { Text(stringResource(R.string.label_disc)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.additional_information))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = yearText,
                onValueChange = {
                    yearText = it
                    onYearChange(it)
                },
                label = { Text(fieldLabel(MetadataField.YEAR, R.string.metadata_year, modifiedFields)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = genreText,
                onValueChange = {
                    genreText = it
                    onGenreChange(it)
                },
                label = { Text(fieldLabel(MetadataField.GENRE, R.string.metadata_genre, modifiedFields)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = composerText,
            onValueChange = {
                composerText = it
                onComposerChange(it)
            },
            label = { Text(fieldLabel(MetadataField.COMPOSER, R.string.metadata_composer, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = lyricistText,
            onValueChange = {
                lyricistText = it
                onLyricistChange(it)
            },
            label = { Text(fieldLabel(MetadataField.LYRICIST, R.string.metadata_lyricist, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = commentText,
            onValueChange = {
                commentText = it
                onCommentChange(it)
            },
            label = { Text(fieldLabel(MetadataField.COMMENT, R.string.metadata_comment, modifiedFields)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.lyrics_section_title))

        OutlinedTextField(
            value = lyricsText,
            onValueChange = {
                lyricsText = it
                onLyricsChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            label = { Text(fieldLabel(MetadataField.LYRICS, R.string.edit_lyrics, modifiedFields)) },
            placeholder = { Text(stringResource(R.string.no_lyrics_added)) },
            minLines = 6,
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.file_information))

        FileInfoRow(stringResource(R.string.file_info_format), audioFile.format)
        FileInfoRow(stringResource(R.string.metadata_bitrate), "${audioFile.bitrate} kbps")
        FileInfoRow(stringResource(R.string.metadata_sample_rate), "${audioFile.sampleRate} Hz")
        FileInfoRow(stringResource(R.string.file_info_channels), audioFile.channels.toString())
        FileInfoRow(stringResource(R.string.metadata_duration), audioFile.getFormattedDuration())
        FileInfoRow(stringResource(R.string.file_info_size), audioFile.getFormattedSize())

        Spacer(modifier = Modifier.height(16.dp))

        replayGainSection?.invoke()
    }
}
