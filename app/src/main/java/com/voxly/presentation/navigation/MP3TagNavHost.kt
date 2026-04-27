package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.data.local.AlbumSortOption
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.LocalSharedTransitionScope
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.album.AlbumAdaptiveScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.album.AlbumTabContent
import com.voxly.presentation.screens.artist.ArtistScreenContent
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentAdaptiveScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserAdaptiveScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.LyricsPosterScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AlbumViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.ArtistViewModel
import com.voxly.presentation.viewmodel.LibraryBatchViewModel
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import com.voxly.presentation.viewmodel.LibrarySettingsViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import com.voxly.presentation.viewmodel.LyricsSelectorViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.ReplayGainViewModel
import timber.log.Timber

/**
 * Main navigation host for the MP3 Tag Editor app using official Navigation3 APIs.
 */
private val containerTransformMetadata = metadata {
    put(NavDisplay.TransitionKey) {
        ExpressiveAnimations.ContainerTransformSharedElementEnter togetherWith ExpressiveAnimations.ContainerTransformSharedElementExit
    }
    put(NavDisplay.PopTransitionKey) {
        ExpressiveAnimations.ContainerTransformSharedElementPopEnter togetherWith ExpressiveAnimations.ContainerTransformSharedElementPopExit
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        ExpressiveAnimations.ContainerTransformSharedElementPredictiveBackEnter togetherWith ExpressiveAnimations.ContainerTransformSharedElementPredictiveBackExit
    }
}

private val sharedAxisXMetadata = metadata {
    put(NavDisplay.TransitionKey) {
        ExpressiveAnimations.SharedAxisXEnter togetherWith ExpressiveAnimations.SharedAxisXExit
    }
    put(NavDisplay.PopTransitionKey) {
        ExpressiveAnimations.SharedAxisXPopEnter togetherWith ExpressiveAnimations.SharedAxisXPopExit
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        ExpressiveAnimations.ContainerTransformPredictiveBackEnter togetherWith
        ExpressiveAnimations.ContainerTransformPredictiveBackExit
    }
}


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun <T : Any> rememberListDetailSceneStrategy(): androidx.navigation3.scene.SceneStrategy<T> {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    return rememberListDetailSceneStrategy<T>(directive = directive)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryScanViewModel: LibraryScanViewModel = hiltViewModel()
    val librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel()
    val libraryBatchViewModel: LibraryBatchViewModel = hiltViewModel()

    val topLevelBackStack = rememberTopLevelBackStack(FileBrowser)

    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    val topLevelRoute = topLevelBackStack.topLevelKey
    val showNavigationBar = isMainScreenKey(topLevelBackStack.backStack.lastOrNull())
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    SharedTransitionLayout {
        val sharedTransitionScope = this@SharedTransitionLayout

        val navDisplayContent: @Composable () -> Unit = {
            MP3TagNavDisplay(
                topLevelBackStack = topLevelBackStack,
                sharedTransitionScope = sharedTransitionScope,
                libraryViewModel = libraryViewModel,
                libraryScanViewModel = libraryScanViewModel,
                librarySettingsViewModel = librarySettingsViewModel,
                libraryBatchViewModel = libraryBatchViewModel,
                pendingLyrics = pendingLyrics,
                onPendingLyricsConsumed = { pendingLyrics = null },
                pendingCoverArt = pendingCoverArt,
                onPendingCoverArtConsumed = { pendingCoverArt = null },
                onPendingLyricsSet = { pendingLyrics = it },
                onPendingCoverArtSet = { pendingCoverArt = it }
            )
        }

        val isFileSelected = topLevelRoute is FileBrowser
        val isAlbumsSelected = topLevelRoute is Albums
        val isArtistsSelected = topLevelRoute is Artists
        val isSettingsSelected = topLevelRoute is Settings

        val onFileBrowserClick = dropUnlessResumed {
            if (!isFileSelected) topLevelBackStack.addTopLevel(FileBrowser)
        }
        val onAlbumsClick = dropUnlessResumed {
            if (!isAlbumsSelected) topLevelBackStack.addTopLevel(Albums)
        }
        val onArtistsClick = dropUnlessResumed {
            if (!isArtistsSelected) topLevelBackStack.addTopLevel(Artists)
        }
        val onSettingsClick = dropUnlessResumed {
            if (!isSettingsSelected) topLevelBackStack.addTopLevel(Settings)
        }

        NavigationSuiteScaffold(
            layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
            navigationSuiteItems = {
                if (showNavigationBar) {
                    item(
                        icon = {
                            Icon(
                                imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                                contentDescription = "Files"
                            )
                        },
                        label = { Text("Files") },
                        selected = isFileSelected,
                        onClick = onFileBrowserClick
                    )

                    item(
                        icon = {
                            Icon(
                                imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                                contentDescription = "Albums"
                            )
                        },
                        label = { Text("Albums") },
                        selected = isAlbumsSelected,
                        onClick = onAlbumsClick
                    )

                    item(
                        icon = {
                            Icon(
                                imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                                contentDescription = "Artists"
                            )
                        },
                        label = { Text("Artists") },
                        selected = isArtistsSelected,
                        onClick = onArtistsClick
                    )

                    item(
                        icon = {
                            Icon(
                                imageVector = if (isSettingsSelected) AppIcon.Settings.vector else AppIcon.SettingsOutlined.vector,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        selected = isSettingsSelected,
                        onClick = onSettingsClick
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            navDisplayContent()
        }
    }
}

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class
)
@Composable
private fun MP3TagNavDisplay(
    topLevelBackStack: TopLevelBackStack<NavKey>,
    sharedTransitionScope: SharedTransitionScope,
    libraryViewModel: LibraryViewModel,
    libraryScanViewModel: LibraryScanViewModel,
    librarySettingsViewModel: LibrarySettingsViewModel,
    libraryBatchViewModel: LibraryBatchViewModel,
    pendingLyrics: String?,
    onPendingLyricsConsumed: () -> Unit,
    pendingCoverArt: ByteArray?,
    onPendingCoverArtConsumed: () -> Unit,
    onPendingLyricsSet: (String) -> Unit,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    NavDisplay(
        backStack = topLevelBackStack.backStack,
        onBack = { topLevelBackStack.removeLast() },
        entryProvider = entryProvider<NavKey> {
            entry<FileBrowser> {
                SharedTransitionWrapper(sharedTransitionScope) {
                    FileBrowserAdaptiveScreen(
                        viewModel = libraryViewModel,
                        scanViewModel = libraryScanViewModel,
                        settingsViewModel = librarySettingsViewModel,
                        onNavigateToDirectory = { directoryUri, directoryName ->
                            topLevelBackStack.add(DirectoryContent(directoryUri, directoryName))
                        },
                        onNavigateToMetadata = { filePath, coverTag ->
                            topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
                        },
                        onNavigateToOnlineMetadata = {},
                        onNavigateToOnlineLyricsSearch = {},
                        onNavigateToOnlineCoverSearch = {},
                        onNavigateToLyricsSelector = { _, _, _, _, _ -> },
                        onNavigateBack = {}
                    )
                }
            }

            entry<Albums> {
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                SharedTransitionWrapper(sharedTransitionScope) {
                    val albumViewModel: AlbumViewModel = hiltViewModel()
                    val albums by albumViewModel.sortedAlbums.collectAsState()
                    val sortOption by albumViewModel.sortOption.collectAsState(initial = AlbumSortOption.NAME_ASC.name)
                    val savedScrollPosition = remember { albumViewModel.getScrollPosition("albums") }
                    val parsedSortOption = remember(sortOption) { try { AlbumSortOption.valueOf(sortOption) } catch (_: Exception) { null } }
                    AlbumTabContent(
                        albums = albums,
                        onAlbumClick = { albumGroup ->
                            topLevelBackStack.add(AlbumDetail(albumGroup.name, albumGroup.albumArtist ?: ""))
                        },
                        sortOption = parsedSortOption,
                        savedScrollPosition = savedScrollPosition,
                        onSaveScrollPosition = { index, offset ->
                            albumViewModel.saveScrollPosition("albums", index, offset)
                        }
                    )
                }
            }

            entry<Artists> {
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                SharedTransitionWrapper(sharedTransitionScope) {
                    val artistViewModel: ArtistViewModel = hiltViewModel()
                    ArtistScreenContent(
                        viewModel = artistViewModel,
                        onArtistClick = { artistGroup ->
                            topLevelBackStack.add(ArtistDetail(artistGroup.name))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            entry<Settings> {
                SharedTransitionWrapper(sharedTransitionScope) {
                    SettingsEntry(topLevelBackStack, LocalContext.current)
                }
            }

            entry<DirectoryContent>(
                clazzContentKey = { key -> "DirectoryContent_${key.directoryUri}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    DirectoryContentAdaptiveScreen(
                        directoryUri = key.directoryUri,
                        directoryName = key.directoryName,
                        viewModel = libraryViewModel,
                        scanViewModel = libraryScanViewModel,
                        settingsViewModel = librarySettingsViewModel,
                        batchViewModel = libraryBatchViewModel,
                        onNavigateBack = { topLevelBackStack.removeLast() },
                        onNavigateToMetadata = { filePath, coverTag ->
                            topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
                        },
                        onNavigateToReplayGain = { filePaths ->
                            topLevelBackStack.add(ReplayGainScanner(filePaths))
                        },
                        onNavigateToOnlineMetadata = {
                            topLevelBackStack.add(OnlineMetadata(key.directoryUri))
                        },
                        onNavigateToOnlineLyricsSearch = {
                            topLevelBackStack.add(OnlineLyricsSearch(key.directoryUri))
                        },
                        onNavigateToOnlineCoverSearch = {
                            topLevelBackStack.add(OnlineCoverSearch(key.directoryUri))
                        },
                        onNavigateToLyricsSelector = { filePath, title, artist, album, _ ->
                            topLevelBackStack.add(
                                LyricsSelector(
                                    filePath = filePath,
                                    title = title,
                                    artist = artist,
                                    album = album
                                )
                            )
                        }
                    )
                }
            }

            entry<MetadataEditor>(
                clazzContentKey = { key -> "MetadataEditor_${key.filePath}" }
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    MetadataEditorEntry(
                        key = key,
                        topLevelBackStack = topLevelBackStack,
                        pendingLyrics = pendingLyrics,
                        onPendingLyricsConsumed = onPendingLyricsConsumed,
                        pendingCoverArt = pendingCoverArt,
                        onPendingCoverArtConsumed = onPendingCoverArtConsumed
                    )
                }
            }

            entry<ReplayGainScanner>(
                clazzContentKey = { key -> "ReplayGainScanner_${key.filePaths.hashCode()}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    ReplayGainScannerEntry(key, topLevelBackStack)
                }
            }

            entry<OnlineMetadata>(
                clazzContentKey = { key -> "OnlineMetadata_${key.filePath}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    OnlineMetadataEntry(key, topLevelBackStack)
                }
            }

            entry<OnlineLyricsSearch>(
                clazzContentKey = { key -> "OnlineLyricsSearch_${key.filePath}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    OnlineLyricsSearchEntry(key, topLevelBackStack, onPendingLyricsSet = onPendingLyricsSet)
                }
            }

            entry<OnlineCoverSearch>(
                clazzContentKey = { key -> "OnlineCoverSearch_${key.filePath}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    OnlineCoverSearchEntry(key, topLevelBackStack, onPendingCoverArtSet = onPendingCoverArtSet)
                }
            }

            entry<LyricsSelector>(
                clazzContentKey = { key -> "LyricsSelector_${key.filePath}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    LyricsSelectorEntry(key, topLevelBackStack)
                }
            }

            entry<LyricsPoster>(
                clazzContentKey = { key -> "LyricsPoster_${key.filePath}" },
                metadata = sharedAxisXMetadata
            ) { key ->
                SharedTransitionWrapper(sharedTransitionScope) {
                    LyricsPosterEntry(key, topLevelBackStack)
                }
            }

            entry<AlbumDetail>(
                clazzContentKey = { key -> "AlbumDetail_${key.albumName}_${key.albumArtist}" }
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                SharedTransitionWrapper(sharedTransitionScope) {
                    AlbumDetailEntry(
                        key = key,
                        topLevelBackStack = topLevelBackStack
                    )
                }
            }

            entry<ArtistDetail>(
                clazzContentKey = { key -> "ArtistDetail_${key.artistName}" }
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                SharedTransitionWrapper(sharedTransitionScope) {
                    ArtistDetailEntry(
                        key = key,
                        topLevelBackStack = topLevelBackStack
                    )
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            entry<ScanDirectorySettings>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) {
                SharedTransitionWrapper(sharedTransitionScope) {
                    com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                        onNavigateBack = { topLevelBackStack.removeLast() }
                    )
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            entry<LogViewer>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) {
                SharedTransitionWrapper(sharedTransitionScope) {
                    LogViewerScreen(
                        onBack = { topLevelBackStack.removeLast() }
                    )
                }
            }
        },
        sharedTransitionScope = sharedTransitionScope,
        sceneStrategies = listOf(
            BottomSheetSceneStrategy()
        )
    )
}

@Composable
private fun SharedTransitionWrapper(
    sharedTransitionScope: SharedTransitionScope,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedTransitionScope
    ) {
        content()
    }
}

@Composable
private fun SettingsEntry(topLevelBackStack: TopLevelBackStack<NavKey>, context: android.content.Context) {
    val logViewerViewModel = hiltViewModel<com.voxly.presentation.screens.log.LogViewerViewModel>()
    SettingsScreen(
        outerPadding = PaddingValues(),
        onNavigateToLogViewer = { topLevelBackStack.add(LogViewer) },
        onExportLogs = {
            logViewerViewModel.exportLogs(context) { uri ->
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
        onNavigateToScanDirectorySettings = { topLevelBackStack.add(ScanDirectorySettings) },
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

@Composable
private fun MetadataEditorEntry(
    key: MetadataEditor,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    pendingLyrics: String?,
    onPendingLyricsConsumed: () -> Unit,
    pendingCoverArt: ByteArray?,
    onPendingCoverArtConsumed: () -> Unit
) {
    val viewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    MetadataEditorScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        coverTag = key.coverTag.takeIf { it.isNotEmpty() },
        sharedElementKey = key.coverTag.takeIf { it.isNotEmpty() },
        onNavigateBack = { topLevelBackStack.removeLast() },
        onNavigateToOnlineMetadata = {
            topLevelBackStack.add(OnlineMetadata(key.filePath))
        },
        onNavigateToOnlineLyricsSearch = {
            topLevelBackStack.add(OnlineLyricsSearch(key.filePath))
        },
        onNavigateToOnlineCoverSearch = {
            topLevelBackStack.add(OnlineCoverSearch(key.filePath))
        },
        onNavigateToLyricsSelector = { _, title, artist, album, _ ->
            topLevelBackStack.add(
                LyricsSelector(
                    filePath = key.filePath,
                    title = title,
                    artist = artist,
                    album = album
                )
            )
        },
        pendingOnlineLyrics = pendingLyrics,
        onConsumePendingOnlineLyrics = onPendingLyricsConsumed,
        pendingOnlineCoverArt = pendingCoverArt,
        onConsumePendingOnlineCoverArt = onPendingCoverArtConsumed
    )
}

@Composable
private fun ReplayGainScannerEntry(key: ReplayGainScanner, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<ReplayGainViewModel, ReplayGainViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    ReplayGainScannerScreen(
        filePaths = key.filePaths,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onNavigateToMetadata = { filePath, coverTag ->
            topLevelBackStack.removeLast()
            topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
        }
    )
}

@Composable
private fun OnlineMetadataEntry(key: OnlineMetadata, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineMetadataScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onApplyMetadata = { metadata ->
            Timber.d("MP3TagNavHost: onApplyMetadata called, title=${metadata.title}")
            topLevelBackStack.removeLast()
            Timber.d("MP3TagNavHost: backStack popped")
        }
    )
}

@Composable
private fun OnlineLyricsSearchEntry(
    key: OnlineLyricsSearch,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    onPendingLyricsSet: (String) -> Unit
) {
    val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineLyricsSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onLyricsSelected = { lyricsText ->
            onPendingLyricsSet(lyricsText)
            topLevelBackStack.removeLast()
        }
    )
}

@Composable
private fun OnlineCoverSearchEntry(
    key: OnlineCoverSearch,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineCoverSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onCoverSelected = { coverBytes ->
            onPendingCoverArtSet(coverBytes)
            topLevelBackStack.removeLast()
        }
    )
}

@Composable
private fun LyricsSelectorEntry(key: LyricsSelector, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<LyricsSelectorViewModel, LyricsSelectorViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    LyricsSelectorScreen(
        title = key.title,
        artist = key.artist,
        album = key.album,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onDismiss = { topLevelBackStack.removeLast() },
        onNavigateToLyricsPoster = { lyricsText, selectedIndices ->
            topLevelBackStack.add(
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

@Composable
private fun LyricsPosterEntry(key: LyricsPoster, topLevelBackStack: TopLevelBackStack<NavKey>) {
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
        onNavigateBack = { topLevelBackStack.removeLast() }
    )
}

@Composable
private fun AlbumDetailEntry(
    key: AlbumDetail,
    topLevelBackStack: TopLevelBackStack<NavKey>
) {
    Timber.d("AlbumDetailEntry: loading $key")
    val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
        key = "${key.albumName}_${key.albumArtist}",
        creationCallback = { factory -> factory.create(key) }
    )
    val albumOnNavigateBack = remember(topLevelBackStack) { { topLevelBackStack.removeLast(); Unit } }
    val albumOnNavigateToMetadata = remember(topLevelBackStack) { { filePath: String, coverTag: String? ->
        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
    } }
    AlbumDetailScreen(
        albumName = key.albumName,
        albumArtist = key.albumArtist.takeIf { it.isNotEmpty() },
        onNavigateBack = albumOnNavigateBack,
        onNavigateToMetadata = albumOnNavigateToMetadata,
        viewModel = viewModel
    )
}

@Composable
private fun ArtistDetailEntry(
    key: ArtistDetail,
    topLevelBackStack: TopLevelBackStack<NavKey>
) {
    val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
        key = key.artistName,
        creationCallback = { factory -> factory.create(key) }
    )
    val artistOnNavigateBack = remember(topLevelBackStack) { { topLevelBackStack.removeLast(); Unit } }
    val artistOnNavigateToMetadata = remember(topLevelBackStack) { { filePath: String, coverTag: String? ->
        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
    } }
    val artistOnNavigateToAlbumDetail = remember(topLevelBackStack) { { albumName: String, albumArtist: String? ->
        topLevelBackStack.add(AlbumDetail(albumName, albumArtist ?: ""))
    } }
    ArtistDetailScreen(
        artistName = key.artistName,
        onNavigateBack = artistOnNavigateBack,
        onNavigateToMetadata = artistOnNavigateToMetadata,
        onNavigateToAlbumDetail = artistOnNavigateToAlbumDetail,
        viewModel = viewModel
    )
}

private enum class TransitionType {
    TRANSITION, POP, PREDICTIVE_POP
}

private fun AnimatedContentTransitionScope<Scene<NavKey>>.computeTransition(
    isPush: Boolean,
    transitionType: TransitionType = TransitionType.TRANSITION
): androidx.compose.animation.ContentTransform {
    val from = initialState.key
    val to = targetState.key
    val isMainToMain = isMainScreenKey(from) && isMainScreenKey(to)

    // 根据 transitionType 选择正确的 key
    val transitionKey = when (transitionType) {
        TransitionType.TRANSITION -> NavDisplay.TransitionKey
        TransitionType.POP -> NavDisplay.PopTransitionKey
        TransitionType.PREDICTIVE_POP -> NavDisplay.PredictivePopTransitionKey
    }

    // 检查 target entry 是否有自定义 metadata
    val targetEntry = targetState.entries.lastOrNull()
    val metadataMap = targetEntry?.metadata
    Timber.d("computeTransition: INITIAL TYPE=${initialState::class.simpleName}, KEY TYPE=${from::class.simpleName}, from=$from, to=$to, isPush=$isPush, transitionType=$transitionType, " +
            "transitionKey=$transitionKey, metadataKeys=${metadataMap?.keys}, targetEntry=$targetEntry")

    // 尝试获取 entry-level 自定义 transition
    @Suppress("UNCHECKED_CAST")
    val customTransition = (metadataMap as? Map<Any, Any?>)?.get(transitionKey) as? androidx.compose.animation.ContentTransform

    // 如果有 entry-level 自定义 metadata，优先使用它
    if (customTransition != null) {
        Timber.d("computeTransition: using custom $transitionType transition for $to")
        return customTransition
    }

    return if (isMainToMain) {
        ExpressiveAnimations.FadeThroughEnter togetherWith ExpressiveAnimations.FadeThroughExit
    } else {
        val enter = if (isPush) ExpressiveAnimations.SharedAxisXEnter else ExpressiveAnimations.SharedAxisXPopEnter
        val exit = if (isPush) ExpressiveAnimations.SharedAxisXExit else ExpressiveAnimations.SharedAxisXPopExit
        enter togetherWith exit
    }.apply {
        targetContentZIndex = when {
            isMainToMain -> 0f
            isPush -> 1f
            else -> 0f
        }
    }
}

private fun isMainScreenKey(key: Any?): Boolean = key == FileBrowser ||
    key == Albums ||
    key == Artists ||
    key == Settings
