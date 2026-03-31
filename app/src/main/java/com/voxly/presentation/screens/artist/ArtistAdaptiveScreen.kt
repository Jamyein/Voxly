package com.voxly.presentation.screens.artist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
                        val navKey = MetadataEditor(
                            filePath = selectedFileForEditing!!,
                            coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!)
                        )
                        val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            key = selectedFileForEditing!!,
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AdaptiveMetadataEditorContainer(
                            filePath = selectedFileForEditing!!,
                            viewModel = metadataViewModel,
                            coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!),
                            sharedElementKey = null,
                            onNavigateBack = {
                                selectedFileForEditing = null
                            },
                            onNavigateToOnlineMetadata = { /* TODO */ },
                            onNavigateToOnlineLyricsSearch = { /* TODO */ },
                            onNavigateToOnlineCoverSearch = { /* TODO */ },
                            onNavigateToLyricsSelector = { _, _, _, _, _ -> /* TODO */ }
                        )
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
                    val navKey = MetadataEditor(
                        filePath = selectedFileForEditing!!,
                        coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!)
                    )
                    val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                        key = selectedFileForEditing!!,
                        creationCallback = { factory -> factory.create(navKey) }
                    )
                    AdaptiveMetadataEditorContainer(
                        filePath = selectedFileForEditing!!,
                        viewModel = metadataViewModel,
                        coverTag = createAlbumArtSharedElementKey(selectedFileForEditing!!),
                        sharedElementKey = null,
                        onNavigateBack = {
                            selectedFileForEditing = null
                        },
                        onNavigateToOnlineMetadata = { /* TODO */ },
                        onNavigateToOnlineLyricsSearch = { /* TODO */ },
                        onNavigateToOnlineCoverSearch = { /* TODO */ },
                        onNavigateToLyricsSelector = { _, _, _, _, _ -> /* TODO */ }
                    )
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
