package com.voxly.presentation.screens.artist

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
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
 * - Large screens: Three-pane layout (Artist list | Artist detail | Metadata editor)
 * - Medium screens: Two-pane layout (Artist list | Artist detail OR Metadata editor)
 * - Small screens: Single pane with navigation to independent MetadataEditor via onNavigateToMetadata
 *
 * @param onNavigateBack Callback when user wants to navigate back
 * @param onNavigateToMetadata Callback to navigate to independent MetadataEditor screen (used on small screens)
 * @param modifier Modifier for the screen
 * @param viewModel ArtistViewModel for the list
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAdaptiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: ((String, String?) -> Unit)? = null,
    onNavigateToArtistDetail: ((ArtistGroup) -> Unit)? = null,
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
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    // Determine if we're in single-pane mode (small screens)
    val scaffoldValue = navigator.scaffoldValue
    val isSinglePane = scaffoldValue.primary == PaneAdaptedValue.Hidden
    
    // Check if currently in a sub-screen (metadata editor or album detail from artist)
    val isInSubScreen = selectedFileForEditing != null || selectedAlbumNavKey != null || 
        navigator.currentDestination?.contentKey is ArtistDetail
    
    // Handle back gesture when in sub-screen (only in multi-pane mode)
    PredictiveBackHandler(enabled = isInSubScreen && !isSinglePane) { progress ->
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
                            if (isSinglePane && onNavigateToArtistDetail != null) {
                                onNavigateToArtistDetail(artist)
                            } else {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, navKey)
                            }
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
                
                // In single-pane mode:
                // - Show ArtistDetail if that's the current destination
                // - MetadataEditor is shown via onNavigateToMetadata callback
                // - AlbumDetail from artist is also shown via onNavigateToAlbumDetail callback
                when {
                    !isSinglePane && selectedFileForEditing != null -> {
                        // Show metadata editor in detail pane (for medium screens)
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
                            onNavigateToMetadata = { filePath, coverTag ->
                                // In single-pane mode, navigate to independent MetadataEditor
                                // In multi-pane mode, show in detail pane
                                if (isSinglePane && onNavigateToMetadata != null) {
                                    onNavigateToMetadata(filePath, coverTag)
                                } else {
                                    fileSwitchCounter++
                                    selectedFileForEditing = filePath
                                }
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
                            onNavigateToMetadata = { filePath, coverTag ->
                                // In single-pane mode, navigate to independent MetadataEditor
                                // In multi-pane mode, show in extra pane
                                if (isSinglePane && onNavigateToMetadata != null) {
                                    onNavigateToMetadata(filePath, coverTag)
                                } else {
                                    fileSwitchCounter++
                                    selectedFileForEditing = filePath
                                }
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
            // Extra pane: show metadata editor for large screens (tablets)
            AnimatedPane {
                // Only show metadata editor in extra pane when a file is selected
                // and we're in a large screen configuration with extra pane visible
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
