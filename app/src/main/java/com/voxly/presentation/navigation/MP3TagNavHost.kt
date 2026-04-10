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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
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
 *
 * Architecture:
 * 1. Uses official NavDisplay with entryProvider for Navigation3 rendering
 * 2. SharedTransitionLayout wraps NavDisplay with sharedTransitionScope
 * 3. NavigationSuiteScaffold conditionally shown for main screens only
 * 4. Per-entry transitions mapped to NavDisplay transitionSpec / metadata
 * 5. Predictive back handled natively by NavDisplay via onBack
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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    val backStack = remember { mutableStateListOf<NavKey>(FileBrowser) }

    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    val currentKey = backStack.lastOrNull()
    val isMainScreen = isMainScreenKey(currentKey)
    val adaptiveInfo = currentWindowAdaptiveInfo()

    SharedTransitionLayout {
        val sharedTransitionScope = this@SharedTransitionLayout

        val navDisplayContent: @Composable () -> Unit = {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sharedTransitionScope = sharedTransitionScope,
                transitionSpec = {
                    computeTransition(isPush = true)
                },
                popTransitionSpec = {
                    computeTransition(isPush = false)
                },
                predictivePopTransitionSpec = {
                    computeTransition(isPush = false)
                },
                entryProvider = entryProvider<NavKey> {
                    entry<FileBrowser> {
                        SharedTransitionWrapper(sharedTransitionScope) {
                            FileBrowserAdaptiveScreen(
                                viewModel = libraryViewModel,
                                onNavigateToDirectory = { directoryUri, directoryName ->
                                    backStack.add(DirectoryContent(directoryUri, directoryName))
                                },
                                onNavigateToMetadata = { filePath, coverTag ->
                                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
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
                                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                                },
                                onNavigateToAlbumDetail = { albumGroup ->
                                    backStack.add(AlbumDetail(albumGroup.name, albumGroup.artist ?: ""))
                                }
                            )
                        }
                    }

                    entry<Artists> {
                        SharedTransitionWrapper(sharedTransitionScope) {
                            ArtistAdaptiveScreen(
                                onNavigateBack = {},
                                onNavigateToMetadata = { filePath, coverTag ->
                                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                                },
                                onNavigateToArtistDetail = { artistGroup ->
                                    backStack.add(ArtistDetail(artistGroup.name))
                                }
                            )
                        }
                    }

                    entry<Settings> {
                        SharedTransitionWrapper(sharedTransitionScope) {
                            SettingsEntry(backStack, context)
                        }
                    }

                    entry<DirectoryContent>(
                        clazzContentKey = { key -> "DirectoryContent_${key.directoryUri}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            DirectoryContentAdaptiveScreen(
                                directoryUri = key.directoryUri,
                                directoryName = key.directoryName,
                                viewModel = libraryViewModel,
                                onNavigateBack = { backStack.removeLastOrNull() },
                                onNavigateToMetadata = { filePath, coverTag ->
                                    backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                                },
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
                                onNavigateToLyricsSelector = { filePath, title, artist, album, _ ->
                                    backStack.add(
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
                                backStack = backStack,
                                pendingLyrics = pendingLyrics,
                                onPendingLyricsConsumed = { pendingLyrics = null },
                                pendingCoverArt = pendingCoverArt,
                                onPendingCoverArtConsumed = { pendingCoverArt = null }
                            )
                        }
                    }

                    entry<ReplayGainScanner>(
                        clazzContentKey = { key -> "ReplayGainScanner_${key.filePaths.hashCode()}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            ReplayGainScannerEntry(key, backStack)
                        }
                    }

                    entry<OnlineMetadata>(
                        clazzContentKey = { key -> "OnlineMetadata_${key.filePath}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            OnlineMetadataEntry(key, backStack)
                        }
                    }

                    entry<OnlineLyricsSearch>(
                        clazzContentKey = { key -> "OnlineLyricsSearch_${key.filePath}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            OnlineLyricsSearchEntry(key, backStack, onPendingLyricsSet = { pendingLyrics = it })
                        }
                    }

                    entry<OnlineCoverSearch>(
                        clazzContentKey = { key -> "OnlineCoverSearch_${key.filePath}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            OnlineCoverSearchEntry(key, backStack, onPendingCoverArtSet = { pendingCoverArt = it })
                        }
                    }

                    entry<LyricsSelector>(
                        clazzContentKey = { key -> "LyricsSelector_${key.filePath}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            LyricsSelectorEntry(key, backStack)
                        }
                    }

                    entry<LyricsPoster>(
                        clazzContentKey = { key -> "LyricsPoster_${key.filePath}" }
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            LyricsPosterEntry(key, backStack)
                        }
                    }

                    entry<AlbumDetail>(
                        clazzContentKey = { key -> "AlbumDetail_${key.albumName}_${key.albumArtist}" },
                        metadata = containerTransformMetadata
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            AlbumDetailEntry(key, backStack)
                        }
                    }

                    entry<ArtistDetail>(
                        clazzContentKey = { key -> "ArtistDetail_${key.artistName}" },
                        metadata = containerTransformMetadata
                    ) { key ->
                        SharedTransitionWrapper(sharedTransitionScope) {
                            ArtistDetailEntry(key, backStack)
                        }
                    }

                    entry<ScanDirectorySettings>(
                        metadata = sharedAxisXMetadata
                    ) {
                        SharedTransitionWrapper(sharedTransitionScope) {
                            com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                                onNavigateBack = { backStack.removeLastOrNull() }
                            )
                        }
                    }

                    entry<LogViewer>(
                        metadata = sharedAxisXMetadata
                    ) {
                        SharedTransitionWrapper(sharedTransitionScope) {
                            LogViewerScreen(
                                onBack = { backStack.removeLastOrNull() }
                            )
                        }
                    }
                }
            )
        }

        if (isMainScreen) {
            NavigationSuiteScaffold(
                layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
                navigationSuiteItems = {
                    val targetKey = currentKey
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
                            if (!isFileSelected) navigateToMainScreen(backStack, FileBrowser)
                        }
                    )

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
                            if (!isAlbumsSelected) navigateToMainScreen(backStack, Albums)
                        }
                    )

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
                            if (!isArtistsSelected) navigateToMainScreen(backStack, Artists)
                        }
                    )

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
                            if (!isSettingsSelected) navigateToMainScreen(backStack, Settings)
                        }
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
private fun SettingsEntry(backStack: SnapshotStateList<NavKey>, context: android.content.Context) {
    val logViewerViewModel = hiltViewModel<com.voxly.presentation.screens.log.LogViewerViewModel>()
    SettingsScreen(
        outerPadding = PaddingValues(),
        onNavigateToLogViewer = { backStack.add(LogViewer) },
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
        onNavigateToScanDirectorySettings = { backStack.add(ScanDirectorySettings) },
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
    backStack: SnapshotStateList<NavKey>,
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
            backStack.add(
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
private fun ReplayGainScannerEntry(key: ReplayGainScanner, backStack: SnapshotStateList<NavKey>) {
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

@Composable
private fun OnlineMetadataEntry(key: OnlineMetadata, backStack: SnapshotStateList<NavKey>) {
    val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineMetadataScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { backStack.removeLastOrNull() },
        onApplyMetadata = { metadata ->
            Timber.d("MP3TagNavHost: onApplyMetadata called, title=${metadata.title}")
            backStack.removeLastOrNull()
            Timber.d("MP3TagNavHost: backStack popped, size=${backStack.size}")
        }
    )
}

@Composable
private fun OnlineLyricsSearchEntry(
    key: OnlineLyricsSearch,
    backStack: SnapshotStateList<NavKey>,
    onPendingLyricsSet: (String) -> Unit
) {
    val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineLyricsSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { backStack.removeLastOrNull() },
        onLyricsSelected = { lyricsText ->
            onPendingLyricsSet(lyricsText)
            backStack.removeLastOrNull()
        }
    )
}

@Composable
private fun OnlineCoverSearchEntry(
    key: OnlineCoverSearch,
    backStack: SnapshotStateList<NavKey>,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineCoverSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { backStack.removeLastOrNull() },
        onCoverSelected = { coverBytes ->
            onPendingCoverArtSet(coverBytes)
            backStack.removeLastOrNull()
        }
    )
}

@Composable
private fun LyricsSelectorEntry(key: LyricsSelector, backStack: SnapshotStateList<NavKey>) {
    val viewModel = hiltViewModel<LyricsSelectorViewModel, LyricsSelectorViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    LyricsSelectorScreen(
        title = key.title,
        artist = key.artist,
        album = key.album,
        onNavigateBack = { backStack.removeLastOrNull() },
        onDismiss = { backStack.removeLastOrNull() },
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

@Composable
private fun LyricsPosterEntry(key: LyricsPoster, backStack: SnapshotStateList<NavKey>) {
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

@Composable
private fun AlbumDetailEntry(key: AlbumDetail, backStack: SnapshotStateList<NavKey>) {
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

@Composable
private fun ArtistDetailEntry(key: ArtistDetail, backStack: SnapshotStateList<NavKey>) {
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

private fun AnimatedContentTransitionScope<Scene<NavKey>>.computeTransition(
    isPush: Boolean
): androidx.compose.animation.ContentTransform {
    val from = initialState.key
    val to = targetState.key
    val isMainToMain = isMainScreenKey(from) && isMainScreenKey(to)

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

private fun navigateToMainScreen(backStack: SnapshotStateList<NavKey>, targetMainScreen: NavKey) {
    val mainScreenIndex = backStack.indexOfFirst {
        it is FileBrowser || it is Albums || it is Artists || it is Settings
    }
    if (mainScreenIndex >= 0) {
        while (backStack.size > mainScreenIndex + 1) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack[mainScreenIndex] = targetMainScreen
    }
}
