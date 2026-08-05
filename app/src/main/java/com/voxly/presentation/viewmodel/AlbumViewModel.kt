package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
import com.voxly.data.local.UiStateDataStore
import com.voxly.domain.model.AlbumGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * Thin ViewModel layer for AlbumScreen.
 * Uses AudioFileScanner directly for data (same singleton instance as LibraryViewModel).
 * The repeatOnLifecycle bug was fixed by removing it - screens passively collect data.
 *
 * Sorting: Albums are pre-sorted by AlbumArtistAggregator; the scanner's
 * app-scope [AudioFileScanner.sortedAlbums] projection selects the list for
 * the persisted sort option (hot before navigation — no empty first frame).
 *
 * Refresh coordination: pull-to-refresh and initial-load refreshes go through
 * [LibraryRepository.refresh], the single fan-in point for all
 * ViewModels. [LibraryScanViewModel] collects from it and runs the actual scan,
 * updating [LibraryRepository.isRefreshing] for global visibility.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val uiStateDataStore: UiStateDataStore,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    // Display data comes from the scanner's app-scope Eagerly projection
    // (sortedAlbums = combine(canonical NAME_ASC list, persisted sortOption),
    // hot before navigation). The old per-VM stateIn(WhileSubscribed, emptyList)
    // re-wrap caused an empty-first-frame flash on first tab entry; expose directly.
    val sortedAlbums: StateFlow<List<AlbumGroup>> = audioFileScanner.sortedAlbums

    /** Persisted sort option; the screen writes it via [setSortOption]. */
    val sortOption = uiStateDataStore.albumSortOption

    /**
     * Mirrors the global scan activity maintained by [LibraryRepository].
     * Observing the repository (instead of inferring from refresh triggers +
     * filteredFiles emissions) means a VM created mid-scan — e.g. user
     * navigates to Albums while Artists is refreshing — picks up the current
     * spinner state immediately on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryRepository.isRefreshing

    /** Scan error events propagated through [LibraryRepository]. */
    val scanError: SharedFlow<String> = libraryRepository.scanError

    // Scroll positions storage with LRU eviction
    private val scrollPositions = LinkedHashMap<String, ScrollPosition>(MAX_SCROLL_POSITIONS, 0.75f, true)

    companion object {
        private const val MAX_SCROLL_POSITIONS = 10
    }

    /**
     * Save scroll position for a list key
     */
    fun saveScrollPosition(listKey: String, index: Int, offset: Int) {
        scrollPositions[listKey] = ScrollPosition(index = index, offset = offset)
        while (scrollPositions.size > MAX_SCROLL_POSITIONS) {
            scrollPositions.keys.firstOrNull()?.let { scrollPositions.remove(it) } ?: break
        }
    }

    /**
     * Get saved scroll position for a list key
     */
    fun getScrollPosition(listKey: String): ScrollPosition {
        return scrollPositions[listKey] ?: ScrollPosition()
    }

    /**
     * Request a library refresh via [LibraryRepository]. The actual scan is
     * performed by [LibraryScanViewModel] (single fan-out point); this method
     * is non-suspending and returns immediately. Bursts are deduplicated by
     * the repository's conflated SharedFlow + the collector's `collectLatest`.
     *
     * `bypassVersionCache = true` ensures the user-visible spinner always
     * corresponds to a real scan attempt, instead of returning early when
     * the MediaStore version has not changed since the last scan.
     */
    fun refresh(forceRefresh: Boolean = false) {
        Timber.tag("Voxly").i("AlbumViewModel refresh -> LibraryRepository")
        libraryRepository.refresh(
            if (forceRefresh) RefreshStrategy.FORCE else RefreshStrategy.INCREMENTAL
        )
    }

    /**
     * Save album sort option to persistent storage
     */
    fun setSortOption(option: String) {
        viewModelScope.launch {
            uiStateDataStore.setAlbumSortOption(option)
        }
    }

    /** True when a previous scan has persisted library data (so empty-screen auto-refresh can be skipped). */
    suspend fun hasCachedData(): Boolean = audioFileScanner.hasCachedData()
}
