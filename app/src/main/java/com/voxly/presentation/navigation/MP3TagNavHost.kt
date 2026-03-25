package com.voxly.presentation.navigation

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.presentation.components.LocalSharedTransitionScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.components.FlexibleBottomAppBar
import com.voxly.presentation.icons.AppIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.album.AlbumScreen
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.artist.ArtistScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.screens.metadata.LyricsPosterScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AppViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import com.voxly.presentation.viewmodel.LyricsSelectorViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.ReplayGainViewModel

/**
 * Main navigation host for the MP3 Tag Editor app using Navigation3.
 * Implements adaptive navigation with NavigationSuiteScaffold for M3E Flexible navigation bar.
 * Automatically switches between NavigationBar (bottom) and NavigationRail (side) based on screen size.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val appViewModel: AppViewModel = hiltViewModel()
    // Shared ViewModel for FileBrowserScreen and DirectoryContentScreen to ensure state consistency
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    // Create back stack using mutableStateListOf<Any> (matches nav3-recipes approach)
    // The NavKey objects are still used, but the list type is Any for compatibility
    val backStack: MutableList<Any> = remember {
        mutableStateListOf<Any>(FileBrowser)
    }

    // State to hold pending data from online search screens to pass back to MetadataEditor
    var pendingMetadata by remember { mutableStateOf<AudioMetadata?>(null) }
    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    // Entry decorators for ViewModel scoping and state saving
    @Suppress("UNCHECKED_CAST")
    val decorators: List<NavEntryDecorator<Any>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    )

    // Scene strategies for dialog support
    @Suppress("UNCHECKED_CAST")
    val sceneStrategy: SceneStrategy<Any> = remember { DialogSceneStrategy<Any>() }

    // Determine current screen for navigation
    val currentKey = backStack.lastOrNull()
    val showBottomBar = currentKey == FileBrowser ||
            currentKey == Albums ||
            currentKey == Artists ||
            currentKey == Settings

    // Get adaptive info for responsive layout
    val adaptiveInfo = currentWindowAdaptiveInfo()

    // NavigationSuiteScaffold for adaptive navigation (NavigationBar/Rail)
    NavigationSuiteScaffold(
        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
        navigationSuiteItems = {
            if (showBottomBar) {
                // Files - Filled when selected, Outlined when unselected
                val isFileSelected = currentKey is FileBrowser
                item(
                    icon = { Icon(
                        imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                        contentDescription = "Files"
                    ) },
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
                // Albums - Filled when selected, Outlined when unselected
                val isAlbumsSelected = currentKey is Albums
                item(
                    icon = { Icon(
                        imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                        contentDescription = "Albums"
                    ) },
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
                // Artists - Filled when selected, Outlined when unselected
                val isArtistsSelected = currentKey is Artists
                item(
                    icon = { Icon(
                        imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                        contentDescription = "Artists"
                    ) },
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
                // Settings - Filled when selected, Outlined when unselected
                val isSettingsSelected = currentKey is Settings
                item(
                    icon = { Icon(
                        imageVector = if (isSettingsSelected) AppIcon.Settings.vector else AppIcon.SettingsOutlined.vector,
                        contentDescription = "Settings"
                    ) },
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
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategy = sceneStrategy,
                entryDecorators = decorators,
                entryProvider = entryProvider {
                    // Bottom navigation screens
                    entry<FileBrowser> {
                        FileBrowserScreen(
                            outerPadding = PaddingValues(),
                            viewModel = libraryViewModel,
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToReplayGain = { filePaths ->
                                backStack.add(ReplayGainScanner(filePaths))
                            },
                            onNavigateToDirectory = { directoryUri, directoryName ->
                                backStack.add(DirectoryContent(directoryUri, directoryName))
                            },
                            onNavigateToSearch = {},
                            onNavigateToAlbum = { albumName, albumArtist ->
                                backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
                            },
                            onNavigateToArtist = { artistName ->
                                backStack.add(ArtistDetail(artistName))
                            }
                        )
                    }

                    entry<Albums> {
                        AlbumScreen(
                            outerPadding = PaddingValues(),
                            onNavigateToAlbumDetail = { albumName, albumArtist ->
                                backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
                            }
                        )
                    }

                    entry<Artists> {
                        ArtistScreen(
                            outerPadding = PaddingValues(),
                            onNavigateToArtistDetail = { artistName ->
                                backStack.add(ArtistDetail(artistName))
                            }
                        )
                    }

                    entry<Settings> {
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

                    // Detail screens
                    entry<DirectoryContent> { key ->
                        DirectoryContentScreen(
                            directoryUri = key.directoryUri,
                            directoryName = key.directoryName,
                            viewModel = libraryViewModel,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToReplayGain = { filePaths ->
                                backStack.add(ReplayGainScanner(filePaths))
                            }
                        )
                    }

                    entry<MetadataEditor> { key ->
                        val viewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        MetadataEditorScreen(
                            filePath = key.filePath,
                            viewModel = viewModel,
                            coverTag = key.coverTag.takeIf { it.isNotEmpty() },
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
                            onConsumePendingOnlineMetadata = { pendingMetadata = null },
                            pendingOnlineLyrics = pendingLyrics,
                            onConsumePendingOnlineLyrics = { pendingLyrics = null }
                        )
                    }

                    entry<ReplayGainScanner> { key ->
                        val viewModel = hiltViewModel<ReplayGainViewModel, ReplayGainViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        ReplayGainScannerScreen(
                            filePaths = key.filePaths,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToMetadata = { filePath, coverTag ->
                                // Replace current screen with metadata editor
                                backStack.removeLastOrNull()
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            }
                        )
                    }

                    entry<OnlineMetadata> { key ->
                        val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        OnlineMetadataScreen(
                            filePath = key.filePath,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onApplyMetadata = { metadata ->
                                pendingMetadata = metadata
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    entry<OnlineLyricsSearch> { key ->
                        val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        OnlineLyricsSearchScreen(
                            filePath = key.filePath,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onLyricsSelected = { lyricsText ->
                                pendingLyrics = lyricsText
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    entry<OnlineCoverSearch> { key ->
                        val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        OnlineCoverSearchScreen(
                            filePath = key.filePath,
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onCoverSelected = { coverBytes ->
                                pendingCoverArt = coverBytes
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    entry<LyricsSelector> { key ->
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

                    entry<LyricsPoster> { key ->
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

                    entry<AlbumDetail> { key ->
                        val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        AlbumDetailScreen(
                            albumName = key.albumName,
                            albumArtist = key.albumArtist.takeIf { it.isNotEmpty() },
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            }
                        )
                    }

                    entry<ArtistDetail> { key ->
                        val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
                            creationCallback = { factory -> factory.create(key) }
                        )
                        ArtistDetailScreen(
                            artistName = key.artistName,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            }
                        )
                    }

                    entry<ScanDirectorySettings> {
                        com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }

                    entry<LogViewer> {
                        LogViewerScreen(
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                },
                // Animation transitions using ExpressiveAnimations
                transitionSpec = {
                    ExpressiveAnimations.SlideInHorizontallyInitialOffsetForward togetherWith
                            ExpressiveAnimations.SlideOutHorizontallyInitialOffsetForward
                },
                popTransitionSpec = {
                    ExpressiveAnimations.SlideInHorizontallyInitialOffsetBackward togetherWith
                            ExpressiveAnimations.SlideOutHorizontallyInitialOffsetBackward
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
