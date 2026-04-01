package com.voxly.presentation.screens.artist

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.navigation.AlbumDetail
import com.voxly.presentation.navigation.ArtistDetail
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.ArtistViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Adaptive Artist screen using Material3 ListDetailPaneScaffold with conditional three-pane support.
 *
 * Layout behavior:
 * - Initial state: Two-pane layout (Artist list | Artist detail)
 * - After clicking album: Two-pane layout (Artist list | Album detail) with back to artist
 * - After clicking track: Three-pane layout on tablets (Artist list | Artist/Album detail | Metadata editor)
 * - On phones: Single pane with navigation
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAdaptiveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // Navigator for list-detail layout
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    
    // Track selected file for metadata editing and current album detail view
    var selectedFileForEditing by remember { mutableStateOf<String?>(null) }
    var selectedAlbumNavKey by remember { mutableStateOf<AlbumDetail?>(null) }
    
    // Force ViewModel recreation when switching files by using a counter
    var fileSwitchCounter by remember { mutableStateOf(0) }
    
    // Check if currently in a sub-screen (metadata editor or album detail from artist)
    val isInSubScreen = selectedFileForEditing != null || selectedAlbumNavKey != null || 
        navigator.currentDestination?.contentKey is ArtistDetail
    
    // Handle back gesture when in sub-screen
    PredictiveBackHandler(enabled = isInSubScreen) { progress ->
        try {
            progress.collect { /* Handle progress if needed */ }
            // Handle back navigation based on current state
            when {
                selectedFileForEditing != null -> {
                    // Exit metadata editor
                    selectedFileForEditing = null
                    fileSwitchCounter++
                }
                selectedAlbumNavKey != null -> {
                    // Return from album detail to artist detail
                    selectedAlbumNavKey = null
                }
                navigator.currentDestination?.contentKey is ArtistDetail -> {
                    // Return from artist detail to artist list
                    coroutineScope.launch {
                        navigator.navigateBack()
                    }
                }
            }
        } catch (e: CancellationException) {
            // Gesture cancelled, do nothing
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            // Artist list pane
            AnimatedPane {
                ArtistScreenContent(
                    viewModel = viewModel,
                    onArtistClick = { artist ->
                        coroutineScope.launch {
                            selectedFileForEditing = null
                            selectedAlbumNavKey = null
                            fileSwitchCounter++
                            val navKey = ArtistDetail(artistName = artist.name)
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, navKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        detailPane = {
            // Detail pane: Artist detail, Album detail, or Metadata editor
            AnimatedPane {
                val currentDestination = navigator.currentDestination?.contentKey
                
                when {
                    selectedFileForEditing != null -> {
                        // Show metadata editor in detail pane (for small/medium screens)
                        key(selectedFileForEditing, fileSwitchCounter) {
                            val navKey = MetadataEditor(
                                filePath = selectedFileForEditing!!,
                                coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!)
                            )
                            val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                                key = "${selectedFileForEditing!!}_$fileSwitchCounter",
                                creationCallback = { factory -> factory.create(navKey) }
                            )
                            AdaptiveMetadataEditorContainer(
                                filePath = selectedFileForEditing!!,
                                viewModel = metadataViewModel,
                                coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!),
                                sharedElementKey = null,
                                onNavigateBack = {
                                    selectedFileForEditing = null
                                    fileSwitchCounter++
                                },
                                onNavigateToOnlineMetadata = { /* TODO */ },
                                onNavigateToOnlineLyricsSearch = { /* TODO */ },
                                onNavigateToOnlineCoverSearch = { /* TODO */ },
                                onNavigateToLyricsSelector = { _, _, _, _, _ -> /* TODO */ }
                            )
                        }
                    }
                    selectedAlbumNavKey != null -> {
                        // Show album detail (navigated from artist's album carousel)
                        val navKey = selectedAlbumNavKey!!
                        val detailViewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                            key = navKey.albumName + navKey.albumArtist,
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AlbumDetailScreen(
                            albumName = navKey.albumName,
                            albumArtist = navKey.albumArtist.takeIf { it.isNotEmpty() },
                            onNavigateBack = {
                                // Return to artist detail
                                selectedAlbumNavKey = null
                            },
                            onNavigateToMetadata = { filePath, _ ->
                                fileSwitchCounter++
                                selectedFileForEditing = filePath
                            },
                            viewModel = detailViewModel
                        )
                    }
                    currentDestination is ArtistDetail -> {
                        // Show artist detail
                        val detailViewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
                            key = currentDestination.artistName,
                            creationCallback = { factory -> factory.create(currentDestination) }
                        )
                        ArtistDetailScreen(
                            artistName = currentDestination.artistName,
                            onNavigateBack = {
                                coroutineScope.launch {
                                    navigator.navigateBack()
                                }
                            },
                            onNavigateToMetadata = { filePath, _ ->
                                fileSwitchCounter++
                                selectedFileForEditing = filePath
                            },
                            onNavigateToAlbumDetail = { albumName, albumArtist ->
                                selectedAlbumNavKey = AlbumDetail(
                                    albumName = albumName,
                                    albumArtist = albumArtist ?: ""
                                )
                            },
                            viewModel = detailViewModel
                        )
                    }
                    else -> {
                        EmptyDetailPane(
                            message = "Select an artist to view details"
                        )
                    }
                }
            }
        },
        extraPane = {
            // Extra pane: conditionally show metadata editor for large screens (tablets)
            AnimatedPane {
                // Only show metadata editor in extra pane when a file is selected
                // and we're in a large screen configuration (tablet)
                if (selectedFileForEditing != null) {
                    key(selectedFileForEditing, fileSwitchCounter) {
                        val navKey = MetadataEditor(
                            filePath = selectedFileForEditing!!,
                            coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!)
                        )
                        val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            key = "${selectedFileForEditing!!}_extra_$fileSwitchCounter",
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AdaptiveMetadataEditorContainer(
                            filePath = selectedFileForEditing!!,
                            viewModel = metadataViewModel,
                            coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!),
                            sharedElementKey = null,
                            onNavigateBack = {
                                selectedFileForEditing = null
                                fileSwitchCounter++
                            },
                            onNavigateToOnlineMetadata = { /* TODO */ },
                            onNavigateToOnlineLyricsSearch = { /* TODO */ },
                            onNavigateToOnlineCoverSearch = { /* TODO */ },
                            onNavigateToLyricsSelector = { _, _, _, _, _ -> /* TODO */ }
                        )
                    }
                } else {
                    // Show empty placeholder when no file is selected
                    EmptyDetailPane(
                        message = "Select a track to edit metadata"
                    )
                }
            }
        },
        modifier = modifier
    )
}
