package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.repository.LibraryDataHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes [LibraryDataHolder.requestRefresh] to
 * Composables.
 *
 * The Files page used to call `LibraryScanViewModel.refresh()` directly, which
 * bypassed the shared fan-in path used by Albums / Artists / MediaStore
 * observer / SAF watcher. Routing the user-initiated pull-to-refresh through
 * this coordinator — and then through `LibraryDataHolder.requestRefresh` —
 * keeps a single fan-in point while letting the request carry the
 * `bypassVersionCache = true` flag so the spinner always corresponds to a
 * real scan attempt.
 */
@HiltViewModel
class LibraryRefreshCoordinator @Inject constructor(
    private val libraryDataHolder: LibraryDataHolder,
) : ViewModel() {

    /**
     * Trigger a user-initiated library refresh.
     *
     * `forceRefresh = false` performs an incremental scan (mtime diff against
     * the Room cache). `bypassVersionCache = true` skips the MediaStore
     * version short-circuit so the spinner stays on until the scan actually
     * runs, instead of returning early when nothing in MediaStore changed.
     */
    fun requestUserRefresh(forceRefresh: Boolean = false) {
        libraryDataHolder.requestRefresh(
            forceRefresh = forceRefresh,
            bypassVersionCache = true,
        )
    }
}