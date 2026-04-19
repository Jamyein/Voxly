package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.LocalSharedTransitionScope
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.album.AlbumAdaptiveScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.artist.ArtistAdaptiveScreen
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
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
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
        ExpressiveAnimations.ContainerTransformEnter togetherWith ExpressiveAnimations.ContainerTransformExit
    }
    put(NavDisplay.PopTransitionKey) {
        ExpressiveAnimations.ContainerTransformPopEnter togetherWith ExpressiveAnimations.ContainerTransformPopExit
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        ExpressiveAnimations.ContainerTransformPopEnter togetherWith ExpressiveAnimations.ContainerTransformPopExit
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
        ExpressiveAnimations.SharedAxisXPopEnter togetherWith ExpressiveAnimations.SharedAxisXPopExit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> rememberListDetailSceneStrategy(): ListDetailSceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return remember(windowSizeClass) {
        ListDetailSceneStrategy(windowSizeClass)
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryScanViewModel: LibraryScanViewModel = hiltViewModel()
    val librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel()
    val libraryBatchViewModel: LibraryBatchViewModel = hiltViewModel()

    val navigationState = rememberNavigationState(FileBrowser)
    val navigator = remember { Navigator(navigationState) }

    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    val topLevelRoute = navigationState.topLevelRoute
    val showNavigationBar = isMainScreenKey(topLevelRoute) && navigationState.isAtTabRoot()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    SharedTransitionLayout {
        val sharedTransitionScope = this@SharedTransitionLayout

        val navDisplayContent: @Composable () -> Unit = {
            MP3TagNavDisplay(
                navigationState = navigationState,
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

        if (showNavigationBar) {
            val isFileSelected = topLevelRoute is FileBrowser
            val isAlbumsSelected = topLevelRoute is Albums
            val isArtistsSelected = topLevelRoute is Artists
            val isSettingsSelected = topLevelRoute is Settings

            val onFileBrowserClick = dropUnlessResumed {
                if (!isFileSelected) navigator.navigate(FileBrowser)
            }
            val onAlbumsClick = dropUnlessResumed {
                if (!isAlbumsSelected) navigator.navigate(Albums)
            }
            val onArtistsClick = dropUnlessResumed {
                if (!isArtistsSelected) navigator.navigate(Artists)
            }
            val onSettingsClick = dropUnlessResumed {
                if (!isSettingsSelected) navigator.navigate(Settings)
            }

            NavigationSuiteScaffold(
                layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
                navigationSuiteItems = {
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
                },
                containerColor = MaterialTheme.colorScheme.background
            ) {
                navDisplayContent()
            }
        } else {
            navDisplayContent()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MP3TagNavDisplay(
    navigationState: NavigationState,
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
    val navigator = remember { Navigator(navigationState) }

    NavDisplay(
        entries = navigationState.toDecoratedEntries(
            entryProvider = entryProvider<NavKey> {
                entry<FileBrowser> {
                    SharedTransitionWrapper(sharedTransitionScope) {
                        FileBrowserAdaptiveScreen(
                            viewModel = libraryViewModel,
                            scanViewModel = libraryScanViewModel,
                            settingsViewModel = librarySettingsViewModel,
                            onNavigateToDirectory = { directoryUri, directoryName ->
                                navigator.navigate(DirectoryContent(directoryUri, directoryName))
                            },
                            onNavigateToMetadata = { filePath, coverTag ->
                                navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
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
                    SharedTransitionWrapper(sharedTransitionScope) {
                        AlbumAdaptiveScreen(
                            onNavigateBack = {},
                            onNavigateToMetadata = { filePath, coverTag ->
                                navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToAlbumDetail = { albumGroup ->
                                navigator.navigate(AlbumDetail(albumGroup.name, albumGroup.albumArtist ?: ""))
                            }
                        )
                    }
                }

                entry<Artists> {
                    SharedTransitionWrapper(sharedTransitionScope) {
                        ArtistAdaptiveScreen(
                            onNavigateBack = {},
                            onNavigateToMetadata = { filePath, coverTag ->
                                navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToArtistDetail = { artistGroup ->
                                navigator.navigate(ArtistDetail(artistGroup.name))
                            }
                        )
                    }
                }

                entry<Settings> {
                    SharedTransitionWrapper(sharedTransitionScope) {
                        SettingsEntry(navigator, LocalContext.current)
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
                            onNavigateBack = { navigator.goBack() },
                            onNavigateToMetadata = { filePath, coverTag ->
                                navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToReplayGain = { filePaths ->
                                navigator.navigate(ReplayGainScanner(filePaths))
                            },
                            onNavigateToOnlineMetadata = {
                                navigator.navigate(OnlineMetadata(key.directoryUri))
                            },
                            onNavigateToOnlineLyricsSearch = {
                                navigator.navigate(OnlineLyricsSearch(key.directoryUri))
                            },
                            onNavigateToOnlineCoverSearch = {
                                navigator.navigate(OnlineCoverSearch(key.directoryUri))
                            },
                            onNavigateToLyricsSelector = { filePath, title, artist, album, _ ->
                                navigator.navigate(
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
                    clazzContentKey = { key -> "MetadataEditor_${key.filePath}" },
                    metadata = containerTransformMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        MetadataEditorEntry(
                            key = key,
                            navigator = navigator,
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
                        ReplayGainScannerEntry(key, navigator)
                    }
                }

                entry<OnlineMetadata>(
                    clazzContentKey = { key -> "OnlineMetadata_${key.filePath}" },
                    metadata = sharedAxisXMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        OnlineMetadataEntry(key, navigator)
                    }
                }

                entry<OnlineLyricsSearch>(
                    clazzContentKey = { key -> "OnlineLyricsSearch_${key.filePath}" },
                    metadata = sharedAxisXMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        OnlineLyricsSearchEntry(key, navigator, onPendingLyricsSet = onPendingLyricsSet)
                    }
                }

                entry<OnlineCoverSearch>(
                    clazzContentKey = { key -> "OnlineCoverSearch_${key.filePath}" },
                    metadata = sharedAxisXMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        OnlineCoverSearchEntry(key, navigator, onPendingCoverArtSet = onPendingCoverArtSet)
                    }
                }

                entry<LyricsSelector>(
                    clazzContentKey = { key -> "LyricsSelector_${key.filePath}" },
                    metadata = sharedAxisXMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        LyricsSelectorEntry(key, navigator)
                    }
                }

                entry<LyricsPoster>(
                    clazzContentKey = { key -> "LyricsPoster_${key.filePath}" },
                    metadata = sharedAxisXMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        LyricsPosterEntry(key, navigator)
                    }
                }

                entry<AlbumDetail>(
                    clazzContentKey = { key -> "AlbumDetail_${key.albumName}_${key.albumArtist}" },
                    metadata = containerTransformMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        AlbumDetailEntry(key, navigator)
                    }
                }

                entry<ArtistDetail>(
                    clazzContentKey = { key -> "ArtistDetail_${key.artistName}" },
                    metadata = containerTransformMetadata
                ) { key ->
                    SharedTransitionWrapper(sharedTransitionScope) {
                        ArtistDetailEntry(key, navigator)
                    }
                }

                entry<ScanDirectorySettings>(
                    metadata = sharedAxisXMetadata
                ) {
                    SharedTransitionWrapper(sharedTransitionScope) {
                        com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                            onNavigateBack = { navigator.goBack() }
                        )
                    }
                }

                entry<LogViewer>(
                    metadata = sharedAxisXMetadata
                ) {
                    SharedTransitionWrapper(sharedTransitionScope) {
                        LogViewerScreen(
                            onBack = { navigator.goBack() }
                        )
                    }
                }
            }
        ),
        onBack = { navigator.goBack() },
        sharedTransitionScope = sharedTransitionScope,
        transitionSpec = {
            computeTransition(isPush = true, transitionType = TransitionType.TRANSITION)
        },
        popTransitionSpec = {
            computeTransition(isPush = false, transitionType = TransitionType.POP)
        },
        predictivePopTransitionSpec = {
            computeTransition(isPush = false, transitionType = TransitionType.PREDICTIVE_POP)
        },
        sceneStrategies = listOf(
            rememberListDetailSceneStrategy(),
            remember { BottomSheetSceneStrategy() }
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
private fun SettingsEntry(navigator: Navigator, context: android.content.Context) {
    val logViewerViewModel = hiltViewModel<com.voxly.presentation.screens.log.LogViewerViewModel>()
    SettingsScreen(
        outerPadding = PaddingValues(),
        onNavigateToLogViewer = { navigator.navigate(LogViewer) },
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
        onNavigateToScanDirectorySettings = { navigator.navigate(ScanDirectorySettings) },
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
    navigator: Navigator,
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
        onNavigateBack = { navigator.goBack() },
        onNavigateToOnlineMetadata = {
            navigator.navigate(OnlineMetadata(key.filePath))
        },
        onNavigateToOnlineLyricsSearch = {
            navigator.navigate(OnlineLyricsSearch(key.filePath))
        },
        onNavigateToOnlineCoverSearch = {
            navigator.navigate(OnlineCoverSearch(key.filePath))
        },
        onNavigateToLyricsSelector = { _, title, artist, album, _ ->
            navigator.navigate(
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
private fun ReplayGainScannerEntry(key: ReplayGainScanner, navigator: Navigator) {
    val viewModel = hiltViewModel<ReplayGainViewModel, ReplayGainViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    ReplayGainScannerScreen(
        filePaths = key.filePaths,
        onNavigateBack = { navigator.goBack() },
        onNavigateToMetadata = { filePath, coverTag ->
            navigator.goBack()
            navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
        }
    )
}

@Composable
private fun OnlineMetadataEntry(key: OnlineMetadata, navigator: Navigator) {
    val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineMetadataScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { navigator.goBack() },
        onApplyMetadata = { metadata ->
            Timber.d("MP3TagNavHost: onApplyMetadata called, title=${metadata.title}")
            navigator.goBack()
            Timber.d("MP3TagNavHost: backStack popped")
        }
    )
}

@Composable
private fun OnlineLyricsSearchEntry(
    key: OnlineLyricsSearch,
    navigator: Navigator,
    onPendingLyricsSet: (String) -> Unit
) {
    val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineLyricsSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { navigator.goBack() },
        onLyricsSelected = { lyricsText ->
            onPendingLyricsSet(lyricsText)
            navigator.goBack()
        }
    )
}

@Composable
private fun OnlineCoverSearchEntry(
    key: OnlineCoverSearch,
    navigator: Navigator,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineCoverSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { navigator.goBack() },
        onCoverSelected = { coverBytes ->
            onPendingCoverArtSet(coverBytes)
            navigator.goBack()
        }
    )
}

@Composable
private fun LyricsSelectorEntry(key: LyricsSelector, navigator: Navigator) {
    val viewModel = hiltViewModel<LyricsSelectorViewModel, LyricsSelectorViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    LyricsSelectorScreen(
        title = key.title,
        artist = key.artist,
        album = key.album,
        onNavigateBack = { navigator.goBack() },
        onDismiss = { navigator.goBack() },
        onNavigateToLyricsPoster = { lyricsText, selectedIndices ->
            navigator.navigate(
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
private fun LyricsPosterEntry(key: LyricsPoster, navigator: Navigator) {
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
        onNavigateBack = { navigator.goBack() }
    )
}

@Composable
private fun AlbumDetailEntry(key: AlbumDetail, navigator: Navigator) {
    val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    AlbumDetailScreen(
        albumName = key.albumName,
        albumArtist = key.albumArtist.takeIf { it.isNotEmpty() },
        onNavigateBack = { navigator.goBack() },
        onNavigateToMetadata = { filePath, coverTag ->
            navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
        },
        viewModel = viewModel
    )
}

@Composable
private fun ArtistDetailEntry(key: ArtistDetail, navigator: Navigator) {
    val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    ArtistDetailScreen(
        artistName = key.artistName,
        onNavigateBack = { navigator.goBack() },
        onNavigateToMetadata = { filePath, coverTag ->
            navigator.navigate(MetadataEditor(filePath, coverTag ?: ""))
        },
        onNavigateToAlbumDetail = { albumName, albumArtist ->
            navigator.navigate(AlbumDetail(albumName, albumArtist ?: ""))
        },
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

    Timber.d("computeTransition: from=$from, to=$to, isPush=$isPush, transitionType=$transitionType, " +
            "transitionKey=$transitionKey, metadataKeys=${metadataMap?.keys}")

    // 尝试获取 entry-level 自定义 transition
    @Suppress("UNCHECKED_CAST")
    val customTransition = metadataMap?.get(transitionKey.toString()) as? androidx.compose.animation.ContentTransform

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
