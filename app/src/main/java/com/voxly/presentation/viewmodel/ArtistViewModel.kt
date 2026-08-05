package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.ArtistListItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Artists list screen.
 *
 * Refresh coordination: pull-to-refresh and initial-load refreshes go through
 * [LibraryRepository.refresh], the single fan-in point. The actual
 * scan runs in [LibraryScanViewModel], which updates
 * [LibraryRepository.isRefreshing] for global visibility across screens.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    // Display data comes from the scanner's app-scope Eagerly projections
    // (hot before navigation — no empty-first-frame flash). The old per-VM
    // stateIn(WhileSubscribed, emptyList) re-wraps re-ran the artist mapping
    // on every tab re-entry; expose directly instead.
    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
    val artistListItems: StateFlow<List<ArtistListItemState>> = audioFileScanner.artistListItems

    /**
     * Mirrors the global scan activity maintained by [LibraryRepository].
     * A VM created mid-scan picks up the current spinner state immediately
     * on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryRepository.isRefreshing

    /** Scan error events propagated through [LibraryRepository]. */
    val scanError: SharedFlow<String> = libraryRepository.scanError

    /**
     * Request a library refresh via [LibraryRepository]. Bursts are
     * deduplicated by the repository's conflated SharedFlow + the collector's
     * `collectLatest`.
     *
     * `bypassVersionCache = true` ensures the user-visible spinner always
     * corresponds to a real scan attempt, instead of returning early when
     * the MediaStore version has not changed since the last scan.
     */
    fun refresh(forceRefresh: Boolean = false) {
        Timber.tag("Voxly").i("ArtistViewModel refresh -> LibraryRepository")
        libraryRepository.refresh(
            if (forceRefresh) RefreshStrategy.FORCE else RefreshStrategy.INCREMENTAL
        )
    }

    /** True when a previous scan has persisted library data (so empty-screen auto-refresh can be skipped). */
    suspend fun hasCachedData(): Boolean = audioFileScanner.hasCachedData()

}
