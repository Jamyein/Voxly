package com.voxly.presentation.screens.metadata

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.domain.model.AudioMetadata

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
    coverTag: String? = null,
    sharedElementKey: String? = null,
    pendingOnlineMetadata: AudioMetadata? = null,
    onConsumePendingOnlineMetadata: () -> Unit = {},
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
        viewModel = hiltViewModel(),
        coverTag = coverTag,
        sharedElementKey = sharedElementKey,
        onNavigateBack = onNavigateBack,
        onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
        onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
        onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
        onNavigateToLyricsSelector = onNavigateToLyricsSelector,
        pendingOnlineMetadata = pendingOnlineMetadata,
        onConsumePendingOnlineMetadata = onConsumePendingOnlineMetadata,
        pendingOnlineLyrics = pendingOnlineLyrics,
        onConsumePendingOnlineLyrics = onConsumePendingOnlineLyrics
    )
}
