package com.voxly.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.voxly.presentation.components.FlexibleBottomAppBar
import com.voxly.presentation.screens.RecentEditsScreen
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.StatisticsScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AppViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.LyricsSelectorViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.ReplayGainViewModel

/**
 * Main navigation host for the MP3 Tag Editor app using Navigation3.
 * Implements bottom navigation with Material Design 3 components.
 */
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val appViewModel: AppViewModel = hiltViewModel()

    // Create back stack using mutableStateListOf<Any> (matches nav3-recipes approach)
    // The NavKey objects are still used, but the list type is Any for compatibility
    val backStack: MutableList<Any> = remember {
        mutableStateListOf<Any>(FileBrowser)
    }

    // Entry decorators for ViewModel scoping and state saving
    @Suppress("UNCHECKED_CAST")
    val decorators: List<NavEntryDecorator<Any>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    )

    // Scene strategies for dialog support
    @Suppress("UNCHECKED_CAST")
    val sceneStrategy: SceneStrategy<Any> = remember { DialogSceneStrategy<Any>() }

    // Determine if bottom bar should be shown
    val currentKey = backStack.lastOrNull()
    val showBottomBar = currentKey == FileBrowser ||
            currentKey == RecentEdits ||
            currentKey == Statistics ||
            currentKey == Settings

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                @Suppress("UNCHECKED_CAST")
                FlexibleBottomAppBar(
                    backStack = backStack as MutableList<NavKey>,
                    currentKey = currentKey as NavKey
                )
            }
        }
    ) { outerPadding ->
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
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            },
                            onNavigateToReplayGain = { filePaths ->
                                backStack.add(ReplayGainScanner(filePaths))
                            },
                            onNavigateToDirectory = { directoryUri, directoryName, filePaths ->
                                backStack.add(DirectoryContent(directoryUri, directoryName, filePaths))
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

                    entry<RecentEdits> {
                        RecentEditsScreen(
                            outerPadding = PaddingValues(),
                            onNavigateToMetadata = { filePath, coverTag ->
                                backStack.add(MetadataEditor(filePath, coverTag ?: ""))
                            }
                        )
                    }

                    entry<Statistics> {
                        StatisticsScreen(
                            outerPadding = PaddingValues(),
                            onNavigateToSettings = {
                                backStack.add(Settings)
                            },
                            onNavigateToArtist = { artistName ->
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
                            initialFiles = key.filePaths,
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
                            pendingOnlineMetadata = null,
                            onConsumePendingOnlineMetadata = {},
                            pendingOnlineLyrics = null,
                            onConsumePendingOnlineLyrics = {}
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
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onCoverSelected = { coverBytes ->
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
                            }
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
                // Animation transitions using Material3 Expressive patterns
                transitionSpec = {
                    // Forward navigation: slide in from right
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(300)
                    )
                },
                popTransitionSpec = {
                    // Back navigation: slide in from left
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
