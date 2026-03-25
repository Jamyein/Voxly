package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
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
 * 
 * This implementation uses SharedTransitionLayout + AnimatedContent architecture for
 * full Container Transform support in Navigation 3.
 * 
 * Architecture:
 * 1. Navigation 3 maintains the back stack state
 * 2. AnimatedContent handles the physical rendering with lifecycle overlap
 * 3. SharedTransitionLayout provides the scope for sharedBounds transitions
 * 4. Both scopes are injected via CompositionLocal
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val appViewModel: AppViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    // Navigation 3 back stack
    val backStack: MutableList<Any> = remember {
        mutableStateListOf<Any>(FileBrowser)
    }

    // Pending data for cross-screen communication
    var pendingMetadata by remember { mutableStateOf<AudioMetadata?>(null) }
    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    // Current screen for bottom bar visibility
    val currentKey = backStack.lastOrNull()
    val showBottomBar = currentKey == FileBrowser ||
            currentKey == Albums ||
            currentKey == Artists ||
            currentKey == Settings

    val adaptiveInfo = currentWindowAdaptiveInfo()

    NavigationSuiteScaffold(
        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo),
        navigationSuiteItems = {
            if (showBottomBar) {
                // Files tab
                val isFileSelected = currentKey is FileBrowser
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
                val isAlbumsSelected = currentKey is Albums
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
                val isArtistsSelected = currentKey is Artists
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
                val isSettingsSelected = currentKey is Settings
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
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // SharedTransitionLayout provides the scope for shared element transitions
            SharedTransitionLayout {
                // AnimatedContent creates the "lifecycle overlap" needed for shared transitions
                AnimatedContent(
                    targetState = backStack.lastOrNull(),
                    transitionSpec = {
                        // Determine if this is a push or pop based on back stack size
                        val isPush = backStack.size > (initialState?.let { backStack.indexOf(it) + 1 } ?: 0)
                        
                        if (isPush) {
                            // Push animation: new page enters from right, old page stays visible briefly
                            (fadeIn(animationSpec = tween(300)) + 
                             slideInHorizontally { it / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(300)))
                                .apply { targetContentZIndex = 1f }
                        } else {
                            // Pop animation: old page exits to right, new page enters from left
                            fadeIn(animationSpec = tween(300))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(300)) + 
                                    slideOutHorizontally { it / 4 }
                                )
                                .apply { targetContentZIndex = -1f }
                        }
                    },
                    label = "SharedTransition_Navigation"
                ) { targetKey ->
                    // Provide scopes to child composables
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@AnimatedContent
                    ) {
                        // Render the appropriate screen based on target key
                        RenderScreen(
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
private fun RenderScreen(
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
        is FileBrowser -> {
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

        is Albums -> {
            AlbumScreen(
                outerPadding = PaddingValues(),
                onNavigateToAlbumDetail = { albumName, albumArtist ->
                    backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
                }
            )
        }

        is Artists -> {
            ArtistScreen(
                outerPadding = PaddingValues(),
                onNavigateToArtistDetail = { artistName ->
                    backStack.add(ArtistDetail(artistName))
                }
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

        is DirectoryContent -> {
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

        is MetadataEditor -> {
            val viewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) }
            )
            MetadataEditorScreen(
                filePath = key.filePath,
                viewModel = viewModel,
                coverTag = key.coverTag.takeIf { it.isNotEmpty() },
                sharedElementKey = "audio-file-${key.filePath}",
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
                }
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
                }
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
            // Empty state or initial loading
            Box(modifier = Modifier.fillMaxSize())
        }

        else -> {
            // Unknown screen type
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}