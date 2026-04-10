package com.voxly.presentation.screens.metadata

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.viewmodel.MetadataEditorViewModel

/**
 * Adaptive container for MetadataEditorScreen that handles both
 * standalone and dual-pane modes.
 *
 * In dual-pane mode, this is embedded in the detail pane.
 * In single-pane mode, this is a full-screen destination.
 */
@Composable
fun AdaptiveMetadataEditorContainer(
    filePath: String,
    viewModel: MetadataEditorViewModel,
    coverTag: String? = null,
    sharedElementKey: String? = null,
    pendingOnlineLyrics: String? = null,
    onConsumePendingOnlineLyrics: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit
) {
    MetadataEditorScreen(
        filePath = filePath,
        viewModel = viewModel,
        coverTag = coverTag,
        sharedElementKey = sharedElementKey,
        onNavigateBack = onNavigateBack,
        onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
        onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
        onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
        onNavigateToLyricsSelector = onNavigateToLyricsSelector,
        pendingOnlineLyrics = pendingOnlineLyrics,
        onConsumePendingOnlineLyrics = onConsumePendingOnlineLyrics
    )
}
