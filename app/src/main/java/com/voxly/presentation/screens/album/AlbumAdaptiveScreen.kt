package com.voxly.presentation.screens.album

import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.PredictiveBackHandler
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
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.navigation.AlbumDetail
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AlbumViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Adaptive Album screen using Material3 ListDetailPaneScaffold with conditional three-pane support.
 *
 * Layout behavior:
 * - Large screens: Three-pane layout (Album list | Album detail | Metadata editor)  
 * - Medium screens: Two-pane layout (Album list | Album detail OR Metadata editor)
 * - Small screens: Single pane with navigation to independent MetadataEditor via onNavigateToMetadata
 *
 * @param onNavigateBack Callback when user wants to navigate back
 * @param onNavigateToMetadata Callback to navigate to independent MetadataEditor screen (used on small screens)
 * @param modifier Modifier for the screen
 * @param viewModel AlbumViewModel for the list
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumAdaptiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: ((String, String?) -> Unit)? = null,
    onNavigateToAlbumDetail: ((AlbumGroup) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // Navigator for list-detail layout
    val navigator = rememberListDetailPaneScaffoldNavigator<AlbumGroup>()
    
    // Track selected file for metadata editing with proper ViewModel recreation
    var selectedFileForEditing by remember { mutableStateOf<String?>(null) }
    
    // Force ViewModel recreation when switching files by using a counter
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    // Determine if we're in single-pane mode (small screens)
    // In single-pane mode, we should navigate to independent MetadataEditor
    val scaffoldValue = navigator.scaffoldValue
    val isSinglePane = scaffoldValue.primary == PaneAdaptedValue.Hidden
    
    // Handle back gesture when in metadata editor sub-screen (only in multi-pane mode)
    PredictiveBackHandler(enabled = selectedFileForEditing != null && !isSinglePane) { progress ->
        try {
            progress.collect { /* Handle progress if needed */ }
            // Exit metadata editor when back gesture completes
            selectedFileForEditing = null
            fileSwitchCounter++
        } catch (e: CancellationException) {
            // Gesture cancelled, do nothing
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            // Album list pane
            AnimatedPane {
                AlbumScreenContent(
                    viewModel = viewModel,
                onAlbumClick = { album ->
                    coroutineScope.launch {
                        selectedFileForEditing = null
                        fileSwitchCounter++
                        if (isSinglePane && onNavigateToAlbumDetail != null) {
                            onNavigateToAlbumDetail(album)
                        } else {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, album)
                        }
                    }
                },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        detailPane = {
            // Detail pane: either Album detail or Metadata editor (only in multi-pane mode)
            AnimatedPane {
                val currentAlbum = navigator.currentDestination?.contentKey
                
                // In single-pane mode, always show Album detail if available
                // Metadata editor is shown via onNavigateToMetadata callback
                if (!isSinglePane && selectedFileForEditing != null) {
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
                } else if (currentAlbum != null) {
                    // Show album detail
                    val navKey = AlbumDetail(
                        albumName = currentAlbum.name,
                        albumArtist = currentAlbum.artist ?: ""
                    )
                    val detailViewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                        key = currentAlbum.name + (currentAlbum.artist ?: ""),
                        creationCallback = { factory -> factory.create(navKey) }
                    )
                    AlbumDetailScreen(
                        albumName = currentAlbum.name,
                        albumArtist = currentAlbum.artist,
                        onNavigateBack = {
                            coroutineScope.launch {
                                navigator.navigateBack()
                            }
                        },
                        onNavigateToMetadata = { filePath, coverTag ->
                            // In single-pane mode, navigate to independent MetadataEditor
                            // In multi-pane mode, show in detail/extra pane
                            if (isSinglePane && onNavigateToMetadata != null) {
                                onNavigateToMetadata(filePath, coverTag)
                            } else {
                                fileSwitchCounter++
                                selectedFileForEditing = filePath
                            }
                        },
                        viewModel = detailViewModel
                    )
                } else {
                    EmptyDetailPane(
                        message = "Select an album to view details"
                    )
                }
            }
        },
        extraPane = {
            // Extra pane: show metadata editor for large screens (tablets)
            AnimatedPane {
                // Only show metadata editor in extra pane when:
                // 1. A file is selected
                // 2. We're in a large screen configuration with extra pane visible
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
