package com.voxly.presentation.screens.artist

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
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.navigation.ArtistDetail
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.ArtistViewModel
import kotlinx.coroutines.launch

/**
 * Adaptive Artist screen using Material3 ListDetailPaneScaffold.
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
fun ArtistAdaptiveScreen(
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // Material3 Adaptive Navigator - automatically handles all screen sizes
    val navigator = rememberListDetailPaneScaffoldNavigator<ArtistGroup>()

    // Material3 ListDetailPaneScaffold - handles all screen sizes automatically
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                // Artist list pane - same content for all screen sizes
                ArtistScreenContent(
                    viewModel = viewModel,
                    onArtistClick = { artist ->
                        // Navigate to detail - use coroutine for suspend function
                        coroutineScope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, artist)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val currentArtist = navigator.currentDestination?.contentKey
                if (currentArtist != null) {
                    // Create navKey for the detail view
                    val navKey = ArtistDetail(
                        artistName = currentArtist.name
                    )
                    // Create ViewModel with proper factory
                    val detailViewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
                        key = currentArtist.name,
                        creationCallback = { factory -> factory.create(navKey) }
                    )
                    // Artist detail pane
                    ArtistDetailScreen(
                        artistName = currentArtist.name,
                        onNavigateBack = {
                            // Use coroutine for suspend function
                            coroutineScope.launch {
                                navigator.navigateBack()
                            }
                        },
                        onNavigateToMetadata = onNavigateToMetadata,
                        onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                        viewModel = detailViewModel
                    )
                } else {
                    EmptyDetailPane(
                        message = "Select an artist to view details"
                    )
                }
            }
        },
        modifier = modifier
    )
}
