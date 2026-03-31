package com.voxly.presentation.screens.album

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.navigation.AlbumDetail
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AlbumViewModel
import kotlinx.coroutines.launch

/**
 * Adaptive Album screen using Material3 ListDetailPaneScaffold.
 *
 * This is true adaptive design - Material3 automatically manages:
 * - Small screens: Single pane with full-screen navigation
 * - Medium screens: Dual pane with adjustable ratio
 * - Large screens: Dual pane with 40:60 split
 *
 * No conditional logic needed - Material3 handles all screen sizes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumAdaptiveScreen(
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // Material3 Adaptive Navigator - automatically handles all screen sizes
    val navigator = rememberListDetailPaneScaffoldNavigator<AlbumGroup>()

    // Material3 ListDetailPaneScaffold - handles all screen sizes automatically
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                // Album list pane - same content for all screen sizes
                AlbumScreenContent(
                    viewModel = viewModel,
                    onAlbumClick = { album ->
                        // Navigate to detail - use coroutine for suspend function
                        coroutineScope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, album)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val currentAlbum = navigator.currentDestination?.contentKey
                if (currentAlbum != null) {
                    // Create navKey for the detail view
                    val navKey = AlbumDetail(
                        albumName = currentAlbum.name,
                        albumArtist = currentAlbum.artist ?: ""
                    )
                    // Create ViewModel with proper factory
                    val detailViewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                        key = currentAlbum.name + (currentAlbum.artist ?: ""),
                        creationCallback = { factory -> factory.create(navKey) }
                    )
                    // Album detail pane
                    AlbumDetailScreen(
                        albumName = currentAlbum.name,
                        albumArtist = currentAlbum.artist,
                        onNavigateBack = {
                            // Use coroutine for suspend function
                            coroutineScope.launch {
                                navigator.navigateBack()
                            }
                        },
                        onNavigateToMetadata = onNavigateToMetadata,
                        viewModel = detailViewModel
                    )
                } else {
                    EmptyDetailPane(
                        message = "Select an album to view details"
                    )
                }
            }
        },
        modifier = modifier
    )
}
