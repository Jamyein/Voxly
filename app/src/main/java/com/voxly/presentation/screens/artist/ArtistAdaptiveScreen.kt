package com.voxly.presentation.screens.artist

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
import kotlinx.coroutines.launch

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

    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    
    var selectedFileForEditing by remember { mutableStateOf<String?>(null) }
    var selectedAlbumNavKey by remember { mutableStateOf<AlbumDetail?>(null) }
    
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    val scaffoldValue = navigator.scaffoldValue
    val isSinglePane = scaffoldValue.primary == PaneAdaptedValue.Hidden

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
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
            AnimatedPane {
                val currentDestination = navigator.currentDestination?.contentKey
                
                when {
                    !isSinglePane && selectedFileForEditing != null -> {
                        selectedFileForEditing?.let { filePath ->
                            key(filePath, fileSwitchCounter) {
                                val navKey = MetadataEditor(
                                    filePath = filePath,
                                    coverTag = createAlbumArtSharedElementKey(filePath)
                                )
                                val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                                    key = "${filePath}_$fileSwitchCounter",
                                    creationCallback = { factory -> factory.create(navKey) }
                                )
                                AdaptiveMetadataEditorContainer(
                                    filePath = filePath,
                                    viewModel = metadataViewModel,
                                    coverTag = createAlbumArtSharedElementKey(filePath),
                                    sharedElementKey = null,
                                    onNavigateBack = {
                                        selectedFileForEditing = null
                                        fileSwitchCounter++
                                    },
                                    onNavigateToOnlineMetadata = { },
                                    onNavigateToOnlineLyricsSearch = { },
                                    onNavigateToOnlineCoverSearch = { },
                                    onNavigateToLyricsSelector = { _, _, _, _, _ -> }
                                )
                            }
                        }
                    }
                    selectedAlbumNavKey != null -> {
                        val navKey = selectedAlbumNavKey!!
                        val detailViewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                            key = navKey.albumName + navKey.albumArtist,
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AlbumDetailScreen(
                            albumName = navKey.albumName,
                            albumArtist = navKey.albumArtist.takeIf { it.isNotEmpty() },
                            onNavigateBack = {
                                selectedAlbumNavKey = null
                            },
                            onNavigateToMetadata = { filePath, coverTag ->
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
            AnimatedPane {
                selectedFileForEditing?.let { filePath ->
                    key(filePath, fileSwitchCounter) {
                        val navKey = MetadataEditor(
                            filePath = filePath,
                            coverTag = createAlbumArtSharedElementKey(filePath)
                        )
                        val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            key = "${filePath}_extra_$fileSwitchCounter",
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AdaptiveMetadataEditorContainer(
                            filePath = filePath,
                            viewModel = metadataViewModel,
                            coverTag = createAlbumArtSharedElementKey(filePath),
                            sharedElementKey = null,
                            onNavigateBack = {
                                selectedFileForEditing = null
                                fileSwitchCounter++
                            },
                            onNavigateToOnlineMetadata = { },
                            onNavigateToOnlineLyricsSearch = { },
                            onNavigateToOnlineCoverSearch = { },
                            onNavigateToLyricsSelector = { _, _, _, _, _ -> }
                        )
                    }
                } ?: run {
                    EmptyDetailPane(
                        message = "Select a track to edit metadata"
                    )
                }
            }
        },
        modifier = modifier
    )
}
