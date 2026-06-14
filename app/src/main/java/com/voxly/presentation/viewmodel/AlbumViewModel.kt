package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.data.local.AlbumSortOption
import com.voxly.data.local.UiStateDataStore
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.IncrementalList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * Thin ViewModel layer for AlbumScreen.
 * Uses AudioFileScanner directly for data (same singleton instance as LibraryViewModel).
 * The repeatOnLifecycle bug was fixed by removing it - screens passively collect data.
 *
 * Sorting: Albums are pre-sorted by AlbumArtistAggregator and cached in Room.
 * AlbumViewModel selects the correct pre-sorted list based on current sort option.
 *
 * Refresh coordination: pull-to-refresh and initial-load refreshes go through
 * [LibraryDataHolder.requestRefresh], the single fan-in point for all
 * ViewModels. [LibraryScanViewModel] collects from it and runs the actual scan,
 * updating [LibraryDataHolder.isRefreshing] for global visibility.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val uiStateDataStore: UiStateDataStore,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {

    // Albums sorted by different options - pre-computed by aggregator
    private val albumsBySort: StateFlow<Map<AlbumSortOption, List<AlbumGroup>>> = audioFileScanner.albumsBySort

    /**
     * Mirrors the global scan activity maintained by [LibraryDataHolder].
     * Observing the holder (instead of inferring from refresh triggers +
     * filteredFiles emissions) means a VM created mid-scan — e.g. user
     * navigates to Albums while Artists is refreshing — picks up the current
     * spinner state immediately on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing

    /** Scan error events propagated through [LibraryDataHolder]. */
    val scanError: SharedFlow<String> = libraryDataHolder.scanError

    /** Diff-based album list updates from AlbumArtistAggregator. */
    val albumDiff: SharedFlow<IncrementalList<AlbumGroup>> = audioFileScanner.albumDiff

    val sortOption = uiStateDataStore.albumSortOption

    // Pre-sorted albums based on current sort option
    val sortedAlbums: StateFlow<List<AlbumGroup>> = combine(
        albumsBySort,
        sortOption
    ) { sortMap, currentOption ->
        try {
            val option = AlbumSortOption.valueOf(currentOption)
            sortMap[option] ?: sortMap[AlbumSortOption.NAME_ASC] ?: emptyList()
        } catch (e: IllegalArgumentException) {
            sortMap[AlbumSortOption.NAME_ASC] ?: emptyList()
        }
    }.distinctUntilChanged()
        .stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
        initialValue = emptyList()
    )

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
     * Request a library refresh via [LibraryDataHolder]. The actual scan is
     * performed by [LibraryScanViewModel] (single fan-out point); this method
     * is non-suspending and returns immediately. Bursts are deduplicated by
     * the holder's conflated SharedFlow + the collector's `collectLatest`.
     */
    fun refresh(forceRefresh: Boolean = false) {
        Timber.tag("Voxly").i("AlbumViewModel refresh -> LibraryDataHolder")
        libraryDataHolder.requestRefresh(forceRefresh)
    }

    /**
     * Save album sort option to persistent storage
     */
    fun setSortOption(option: String) {
        viewModelScope.launch {
            uiStateDataStore.setAlbumSortOption(option)
        }
    }
}
