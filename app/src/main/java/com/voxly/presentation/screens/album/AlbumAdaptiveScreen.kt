package com.voxly.presentation.screens.album

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
import androidx.compose.runtime.LaunchedEffect
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

    val navigator = rememberListDetailPaneScaffoldNavigator<AlbumGroup>()
    
    var selectedFileForEditing by remember { mutableStateOf<String?>(null) }
    
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    val scaffoldValue = navigator.scaffoldValue
    val isSinglePane = scaffoldValue.primary == PaneAdaptedValue.Hidden

    val canCloseDetailPane = !isSinglePane && navigator.currentDestination != null

    // Guard against scaffold restoring a detail destination that would hide the list pane
    // when NavDisplay has already taken over detail navigation (e.g. after returning from
    // AlbumDetail). This ensures the album grid is always visible on single-pane layouts.
    LaunchedEffect(Unit) {
        if (navigator.scaffoldValue.primary == PaneAdaptedValue.Hidden && navigator.currentDestination != null) {
            navigator.navigateBack()
        }
    }

    // Handle system back gesture/button for internal scaffold navigation
    // This intercepts back before NavHost's PredictiveBackHandler when detail pane has content
    PredictiveBackHandler(enabled = selectedFileForEditing != null || canCloseDetailPane) { progress ->
        try {
            progress.collect { }
            if (selectedFileForEditing != null) {
                selectedFileForEditing = null
                fileSwitchCounter++
            } else if (canCloseDetailPane) {
                coroutineScope.launch {
                    navigator.navigateBack()
                }
            }
        } catch (e: CancellationException) {
            // Gesture cancelled - no action
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
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
            AnimatedPane {
                val currentAlbum = navigator.currentDestination?.contentKey
                
                // 2-pane mode: show MetadataEditor when file selected (replaces AlbumDetail)
                if (selectedFileForEditing != null) {
                    key(selectedFileForEditing!!, fileSwitchCounter) {
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
                            sharedElementKey = createAlbumArtSharedElementKey(selectedFileForEditing!!),
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
                } else if (currentAlbum != null) {
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
                            sharedElementKey = createAlbumArtSharedElementKey(filePath),
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
