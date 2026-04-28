package com.voxly.presentation.screens.album

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.SeekableTransitionState
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumAdaptiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: ((String, String?) -> Unit)? = null,
    onNavigateToAlbumDetail: ((AlbumGroup) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val coroutineScope = rememberCoroutineScope()

    val navigator = rememberListDetailPaneScaffoldNavigator<AlbumGroup>()
    
    var selectedFileForEditing by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<AlbumGroup?>(null) }
    
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    val scaffoldValue = navigator.scaffoldValue
    val isSinglePane = scaffoldValue.primary == PaneAdaptedValue.Hidden

    val canCloseDetailPane = !isSinglePane && navigator.currentDestination != null

    LaunchedEffect(isSinglePane, onNavigateToAlbumDetail) {
        if (isSinglePane && onNavigateToAlbumDetail != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.List, null)
        }
    }

    val detailPaneState = remember { SeekableTransitionState(initialState = false) }

    PredictiveBackHandler(enabled = canCloseDetailPane) { progress ->
        try {
            progress.collect { backEvent ->
                detailPaneState.seekTo(fraction = backEvent.progress)
            }
            if (canCloseDetailPane) {
                detailPaneState.animateTo(targetState = true)
                coroutineScope.launch {
                    navigator.navigateBack()
                }
            }
        } catch (e: CancellationException) {
            detailPaneState.snapTo(targetState = false)
        }
    }

    val onAlbumClick: (AlbumGroup) -> Unit = remember(isSinglePane, onNavigateToAlbumDetail, coroutineScope) {
        { album ->
            coroutineScope.launch {
                selectedFileForEditing = null
                fileSwitchCounter++
                if (isSinglePane && onNavigateToAlbumDetail != null) {
                    onNavigateToAlbumDetail(album)
                } else {
                    selectedAlbum = album
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, album)
                }
            }
            Unit
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AlbumScreenContent(
                viewModel = viewModel,
                onAlbumClick = onAlbumClick,
                modifier = Modifier.fillMaxSize(),
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        },
        detailPane = {
            AnimatedPane {
                if (selectedFileForEditing != null) {
                    val selectedFile = selectedFileForEditing!!
                    key(selectedFile, fileSwitchCounter) {
                        val navKey = MetadataEditor(
                            filePath = selectedFile,
                            coverTag = createAlbumArtSharedElementKey(selectedFile)
                        )
                        val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            key = "${selectedFile}_$fileSwitchCounter",
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AdaptiveMetadataEditorContainer(
                            filePath = selectedFile,
                            viewModel = metadataViewModel,
                            coverTag = createAlbumArtSharedElementKey(selectedFile),
                            sharedElementKey = createAlbumArtSharedElementKey(selectedFile),
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
                } else {
                    val currentAlbum = selectedAlbum
                    if (currentAlbum != null) {
                        val albumKey = currentAlbum.name + (currentAlbum.albumArtist ?: "")
                        key(albumKey) {
                            val navKey = AlbumDetail(
                                albumName = currentAlbum.name,
                                albumArtist = currentAlbum.albumArtist ?: ""
                            )
                            val detailViewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
                                key = albumKey,
                                creationCallback = { factory -> factory.create(navKey) }
                            )
                            AlbumDetailScreen(
                                albumName = currentAlbum.name,
                                albumArtist = currentAlbum.albumArtist,
                                onNavigateBack = {
                                    selectedAlbum = null
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
                                initialCoverPath = currentAlbum.coverPath ?: currentAlbum.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }?.path ?: currentAlbum.files.firstOrNull()?.path,
                                initialCoverAlbumId = currentAlbum.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }?.mediaStoreAlbumId ?: currentAlbum.files.firstOrNull()?.mediaStoreAlbumId,
                                viewModel = detailViewModel
                            )
                        }
                    } else {
                        EmptyDetailPane(
                            message = "Select an album to view details"
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}
