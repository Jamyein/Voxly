package com.voxly.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.viewmodel.LibraryScanViewModel

/**
 * Library-wide search sheet, shared by the Files / Albums / Artists top bars.
 * Sources the searchable file list from [LibraryScanViewModel.fileBrowserUiState];
 * each caller supplies only its own navigation hook via [onFileClick].
 *
 * The internal [SheetState] is hidden by default; the caller controls
 * visibility through [visible] and is responsible for dismissing the sheet
 * (typically via `showSearchSheet = false`) before navigating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
) {
    if (!visible) return

    val scanViewModel: LibraryScanViewModel = hiltViewModel()
    val scanUiState by scanViewModel.fileBrowserUiState.collectAsStateWithLifecycle()

    val sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    SearchBottomSheet(
        sheetState = sheetState,
        onDismiss = onDismiss,
        allFiles = scanUiState.allAudios,
        onFileClick = onFileClick,
        searchFn = { query -> scanViewModel.searchFiles(query) }
    )
}