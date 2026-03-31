package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.presentation.components.LocalNavAnimatedVisibilityScope
import com.voxly.presentation.components.LocalSharedTransitionScope
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.icons.AppIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.album.AlbumAdaptiveScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.artist.ArtistAdaptiveScreen
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentAdaptiveScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserAdaptiveScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.screens.metadata.LyricsPosterScreen
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import com.voxly.presentation.viewmodel.LyricsSelectorViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.ReplayGainViewModel
import com.voxly.presentation.theme.ExpressiveAnimations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Main navigation host for the MP3 Tag Editor app using Navigation3.
 * Implements adaptive navigation with NavigationSuiteScaffold for M3E Flexible navigation bar.
 *
 * Architecture:
 * 1. Navigation 3 maintains the back stack state
 * 2. NavigationSuiteScaffold wraps only main screens (FileBrowser, Albums, Artists, Settings)
 * 3. Sub-screens (MetadataEditor, DirectoryContent, etc.) are rendered outside NavigationSuiteScaffold
 * 4. This prevents NavigationSuiteScaffold from adding unwanted padding on sub-screens
 * 5. SharedTransitionLayout + AnimatedContent for full Container Transform support
 * 6. Both scopes are injected via CompositionLocal
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    // Navigation 3 back stack
    val backStack: MutableList<Any> = remember {
        mutableStateListOf<Any>(FileBrowser)
    }

    // Pending data for cross-screen communication
    var pendingMetadata by remember { mutableStateOf<AudioMetadata?>(null) }
    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    // Current screen
    val currentKey = backStack.lastOrNull()

    // Check if current screen is a main screen (has bottom navigation)
    val isMainScreen = isMainScreenKey(currentKey)

    val adaptiveInfo = currentWindowAdaptiveInfo()

    var backProgress by remember { mutableFloatStateOf(0f) }
    val backScale = 1f - (0.04f * backProgress)

    // Handle system back navigation - predictive back for sub-screens
    PredictiveBackHandler(enabled = !isMainScreen) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress
            }
            backStack.removeLastOrNull()
        } catch (e: CancellationException) {
            // Gesture cancelled, reset progress
        } finally {
            backProgress = 0f
        }
    }

    // SharedTransitionLayout wraps navigation for shared element transitions
    SharedTransitionLayout {
        // Single AnimatedContent for main + sub screens
        // Ensures both cover source (list) and cover target (MetadataEditor)
        // share the same AnimatedVisibilityScope for sharedBounds morph animation
        AnimatedContent(
            targetState = currentKey,
            transitionSpec = {
                val isPush = backStack.size > (initialState?.let { backStack.indexOf(it) + 1 } ?: 0)
                val isMainToMain = isMainScreenKey(initialState) && isMainScreenKey(targetState)

                val (enterAnim, exitAnim) = when {
                    // Main tab switching
                    isMainToMain -> Pair(
                        ExpressiveAnimations.FadeThroughEnter,
                        ExpressiveAnimations.FadeThroughExit
                    )

                    // Container Transform: Album/Artist list → detail pages
                    targetState is AlbumDetail || targetState is ArtistDetail ||
                        initialState is AlbumDetail || initialState is ArtistDetail -> Pair(
                        if (isPush) ExpressiveAnimations.ContainerTransformEnter else ExpressiveAnimations.ContainerTransformPopEnter,
                        if (isPush) ExpressiveAnimations.ContainerTransformExit else ExpressiveAnimations.ContainerTransformPopExit
                    )

                    // Container Transform: list item → MetadataEditor (existing behavior)
                    targetState is MetadataEditor || initialState is MetadataEditor -> Pair(
                        if (isPush) ExpressiveAnimations.ContainerTransformEnter else ExpressiveAnimations.ContainerTransformPopEnter,
                        if (isPush) ExpressiveAnimations.ContainerTransformExit else ExpressiveAnimations.ContainerTransformPopExit
                    )

                    // Shared Axis X: Settings sub-pages
                    targetState is LogViewer || targetState is ScanDirectorySettings -> Pair(
                        if (isPush) ExpressiveAnimations.SharedAxisXEnter else ExpressiveAnimations.SharedAxisXPopEnter,
                        if (isPush) ExpressiveAnimations.SharedAxisXExit else ExpressiveAnimations.SharedAxisXPopExit
                    )

                    // Default: Shared Axis X for other transitions
                    else -> Pair(
                        if (isPush) ExpressiveAnimations.SharedAxisXEnter else ExpressiveAnimations.SharedAxisXPopEnter,
                        if (isPush) ExpressiveAnimations.SharedAxisXExit else ExpressiveAnimations.SharedAxisXPopExit
                    )
                }

                enterAnim.togetherWith(exitAnim)
                    .apply { targetContentZIndex = if (isPush) 1f else -1f }
            },
            contentKey = { it },
            label = "Unified_Navigation"
        ) { targetKey ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalNavAnimatedVisibilityScope provides this@AnimatedContent
            ) {
                if (isMainScreenKey(targetKey)) {
                    NavigationSuiteScaffold(
                        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
                        navigationSuiteItems = {
                            // Files tab
                            val isFileSelected = targetKey is FileBrowser
                            item(
                                icon = {
                                    Icon(
                                        imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                                        contentDescription = "Files"
                                    )
                                },
                                label = { Text("Files") },
                                selected = isFileSelected,
                                onClick = {
                                    if (!isFileSelected) {
                                        val currentIndex = backStack.indexOfFirst {
                                            it is FileBrowser || it is Albums || it is Artists || it is Settings
                                        }
                                        if (currentIndex >= 0) {
                                            backStack[currentIndex] = FileBrowser
                                        }
                                    }
                                }
                            )

                            // Albums tab
                            val isAlbumsSelected = targetKey is Albums
                            item(
                                icon = {
                                    Icon(
                                        imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                                        contentDescription = "Albums"
                                    )
                                },
                                label = { Text("Albums") },
                                selected = isAlbumsSelected,
                                onClick = {
                                    if (!isAlbumsSelected) {
                                        val currentIndex = backStack.indexOfFirst {
                                            it is FileBrowser || it is Albums || it is Artists || it is Settings
                                        }
                                        if (currentIndex >= 0) {
                                            backStack[currentIndex] = Albums
                                        }
                                    }
                                }
                            )

                            // Artists tab
                            val isArtistsSelected = targetKey is Artists
                            item(
                                icon = {
                                    Icon(
                                        imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                                        contentDescription = "Artists"
                                    )
                                },
                                label = { Text("Artists") },
                                selected = isArtistsSelected,
                                onClick = {
                                    if (!isArtistsSelected) {
                                        val currentIndex = backStack.indexOfFirst {
                                            it is FileBrowser || it is Albums || it is Artists || it is Settings
                                        }
                                        if (currentIndex >= 0) {
                                            backStack[currentIndex] = Artists
                                        }
                                    }
                                }
                            )

                            // Settings tab
                            val isSettingsSelected = targetKey is Settings
                            item(
                                icon = {
                                    Icon(
                                        imageVector = if (isSettingsSelected) AppIcon.Settings.vector else AppIcon.SettingsOutlined.vector,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") },
                                selected = isSettingsSelected,
                                onClick = {
                                    if (!isSettingsSelected) {
                                        val currentIndex = backStack.indexOfFirst {
                                            it is FileBrowser || it is Albums || it is Artists || it is Settings
                                        }
                                        if (currentIndex >= 0) {
                                            backStack[currentIndex] = Settings
                                        }
                                    }
                                }
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        RenderMainScreen(
                            currentKey = targetKey,
                            backStack = backStack,
                            libraryViewModel = libraryViewModel,
                            context = context
                        )
                    }
                } else if (targetKey != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = backScale
                                scaleY = backScale
                                alpha = 1f - (0.05f * backProgress)
                            }
                    ) {
                        RenderSubScreen(
                            targetKey = targetKey,
                            backStack = backStack,
                            libraryViewModel = libraryViewModel,
                            pendingMetadata = pendingMetadata,
                            onPendingMetadataConsumed = { pendingMetadata = null },
                            pendingLyrics = pendingLyrics,
                            onPendingLyricsConsumed = { pendingLyrics = null },
                            pendingCoverArt = pendingCoverArt,
                            onPendingCoverArtConsumed = { pendingCoverArt = null },
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMainScreen(
    currentKey: Any?,
    backStack: MutableList<Any>,
    libraryViewModel: LibraryViewModel,
    context: android.content.Context
) {
    when (val key = currentKey) {
        is FileBrowser -> {
            FileBrowserAdaptiveScreen(
                viewModel = libraryViewModel,
                onNavigateToDirectory = { directoryUri, directoryName ->
                    backStack.add(DirectoryContent(directoryUri, directoryName))
                },
                onNavigateToMetadata = { filePath, coverTag ->
                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                },
                onNavigateToOnlineMetadata = {
                    // Not used in this context - online metadata is accessed from MetadataEditor
                },
                onNavigateToOnlineLyricsSearch = {
                    // Not used in this context
                },
                onNavigateToOnlineCoverSearch = {
                    // Not used in this context
                },
                onNavigateToLyricsSelector = { _, _, _, _, _ ->
                    // Not used in this context
                },
                onNavigateBack = {}
            )
        }

        is Albums -> {
            AlbumAdaptiveScreen(
                onNavigateBack = {}
            )
        }

        is Artists -> {
            ArtistAdaptiveScreen(
                onNavigateBack = {}
            )
        }

        is Settings -> {
            SettingsScreen(
                outerPadding = PaddingValues(),
                onNavigateToLogViewer = {
                    backStack.add(LogViewer)
                },
                onExportLogs = {
                    val viewModel = com.voxly.presentation.screens.log.LogViewerViewModel()
                    viewModel.exportLogs(context) { uri ->
                        if (uri != null) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                        } else {
                            Toast.makeText(context, R.string.settings_logging_no_logs, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onNavigateToScanDirectorySettings = {
                    backStack.add(ScanDirectorySettings)
                },
                onCleanupLogs = {
                    val deletedCount = LogManager.clearAllLogs()
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_logging_cleanup_complete, deletedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        else -> {
            // Not a main screen, render empty
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RenderSubScreen(
    targetKey: Any?,
    backStack: MutableList<Any>,
    libraryViewModel: LibraryViewModel,
    pendingMetadata: AudioMetadata?,
    onPendingMetadataConsumed: () -> Unit,
    pendingLyrics: String?,
    onPendingLyricsConsumed: () -> Unit,
    pendingCoverArt: ByteArray?,
    onPendingCoverArtConsumed: () -> Unit,
    context: android.content.Context
) {
    when (val key = targetKey) {
        is DirectoryContent -> {
            DirectoryContentAdaptiveScreen(
                directoryUri = key.directoryUri,
                directoryName = key.directoryName,
                viewModel = libraryViewModel,
                onNavigateBack = { backStack.removeLastOrNull() },
                onNavigateToReplayGain = { filePaths ->
                    backStack.add(ReplayGainScanner(filePaths))
                },
                onNavigateToOnlineMetadata = {
                    backStack.add(OnlineMetadata(key.directoryUri))
                },
                onNavigateToOnlineLyricsSearch = {
                    backStack.add(OnlineLyricsSearch(key.directoryUri))
                },
                onNavigateToOnlineCoverSearch = {
                    backStack.add(OnlineCoverSearch(key.directoryUri))
                },
                onNavigateToLyricsSelector = { _, title, artist, album, _ ->
                    backStack.add(LyricsSelector(
                        filePath = key.directoryUri,
                        title = title,
                        artist = artist,
                        album = album
                    ))
                }
            )
        }

        is MetadataEditor -> {
            // 使用filePath作为key，确保切换歌曲时创建新的ViewModel实例
            val viewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                key = key.filePath,
                creationCallback = { factory -> factory.create(key) }
            )
            MetadataEditorScreen(
                filePath = key.filePath,
                viewModel = viewModel,
                coverTag = key.coverTag.takeIf { it.isNotEmpty() },
                sharedElementKey = null,
                onNavigateBack = { backStack.removeLastOrNull() },
                onNavigateToOnlineMetadata = {
                    backStack.add(OnlineMetadata(key.filePath))
                },
                onNavigateToOnlineLyricsSearch = {
                    backStack.add(OnlineLyricsSearch(key.filePath))
                },
                onNavigateToOnlineCoverSearch = {
                    backStack.add(OnlineCoverSearch(key.filePath))
                },
                onNavigateToLyricsSelector = { _, title, artist, album, _ ->
                    backStack.add(LyricsSelector(
                        filePath = key.filePath,
                        title = title,
                        artist = artist,
                        album = album
                    ))
                },
                pendingOnlineMetadata = pendingMetadata,
                onConsumePendingOnlineMetadata = onPendingMetadataConsumed,
                pendingOnlineLyrics = pendingLyrics,
                onConsumePendingOnlineLyrics = onPendingLyricsConsumed
            )
        }

        is ReplayGainScanner -> {
            val viewModel = hiltViewModel<ReplayGainViewModel, ReplayGainViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            ReplayGainScannerScreen(
                filePaths = key.filePaths,
                onNavigateBack = { backStack.removeLastOrNull() },
                onNavigateToMetadata = { filePath, coverTag ->
                    backStack.removeLastOrNull()
                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                }
            )
        }

        is OnlineMetadata -> {
            val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            OnlineMetadataScreen(
                filePath = key.filePath,
                onNavigateBack = { backStack.removeLastOrNull() },
                onApplyMetadata = { metadata ->
                    onPendingMetadataConsumed()
                    backStack.removeLastOrNull()
                }
            )
        }

        is OnlineLyricsSearch -> {
            val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            OnlineLyricsSearchScreen(
                filePath = key.filePath,
                onNavigateBack = { backStack.removeLastOrNull() },
                onLyricsSelected = { lyricsText ->
                    onPendingLyricsConsumed()
                    backStack.removeLastOrNull()
                }
            )
        }

        is OnlineCoverSearch -> {
            val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            OnlineCoverSearchScreen(
                filePath = key.filePath,
                viewModel = viewModel,
                onNavigateBack = { backStack.removeLastOrNull() },
                onCoverSelected = { coverBytes ->
                    onPendingCoverArtConsumed()
                    backStack.removeLastOrNull()
                }
            )
        }

        is LyricsSelector -> {
            val viewModel = hiltViewModel<LyricsSelectorViewModel, LyricsSelectorViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            LyricsSelectorScreen(
                title = key.title,
                artist = key.artist,
                album = key.album,
                onNavigateBack = { backStack.removeLastOrNull() },
                onDismiss = {
                    backStack.removeLastOrNull()
                },
                onNavigateToLyricsPoster = { lyricsText, selectedIndices ->
                    backStack.add(
                        LyricsPoster(
                            filePath = key.filePath,
                            title = key.title,
                            artist = key.artist,
                            album = key.album,
                            lyricsText = lyricsText,
                            selectedLyricsIndices = selectedIndices
                        )
                    )
                }
            )
        }

        is LyricsPoster -> {
            val viewModel = hiltViewModel<LyricsPosterViewModel, LyricsPosterViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            LyricsPosterScreen(
                filePath = key.filePath,
                title = key.title,
                artist = key.artist,
                album = key.album,
                lyricsText = key.lyricsText,
                selectedLyricsIndices = key.selectedLyricsIndices,
                onNavigateBack = { backStack.removeLastOrNull() }
            )
        }

        is AlbumDetail -> {
            val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            AlbumDetailScreen(
                albumName = key.albumName,
                albumArtist = key.albumArtist.takeIf { it.isNotEmpty() },
                onNavigateBack = { backStack.removeLastOrNull() },
                onNavigateToMetadata = { filePath, coverTag ->
                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                },
                viewModel = viewModel
            )
        }

        is ArtistDetail -> {
            val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            ArtistDetailScreen(
                artistName = key.artistName,
                onNavigateBack = { backStack.removeLastOrNull() },
                onNavigateToMetadata = { filePath, coverTag ->
                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                },
                onNavigateToAlbumDetail = { albumName, albumArtist ->
                    backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
                },
                viewModel = viewModel
            )
        }

        is ScanDirectorySettings -> {
            com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                onNavigateBack = { backStack.removeLastOrNull() }
            )
        }

        is LogViewer -> {
            LogViewerScreen(
                onBack = { backStack.removeLastOrNull() }
            )
        }

        null -> {
            // Empty state
            Box(modifier = Modifier.fillMaxSize())
        }

        else -> {
            // Unknown screen type - might be a main screen, don't render here
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

private fun isMainScreenKey(key: Any?): Boolean = key == FileBrowser ||
        key == Albums ||
        key == Artists ||
        key == Settings
