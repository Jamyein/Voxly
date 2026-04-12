package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.AlbumSortOption
import com.voxly.data.local.UiStateDataStore
import com.voxly.data.local.cache.AlbumInfoEntity
import com.voxly.domain.model.AlbumGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val uiStateDataStore: UiStateDataStore
) : ViewModel() {

    // Albums sorted by different options - pre-computed by aggregator
    private val albumsBySort: StateFlow<Map<AlbumSortOption, List<AlbumGroup>>> = audioFileScanner.albumsBySort
    val albumInfoMap: StateFlow<Map<String, AlbumInfoEntity>> = audioFileScanner.albumInfoMap

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var refreshJob: Job? = null

    // Scroll positions storage with LRU eviction
    private val scrollPositions = LinkedHashMap<String, ScrollPosition>(MAX_SCROLL_POSITIONS, 0.75f, true)

    companion object {
        private const val MAX_SCROLL_POSITIONS = 10
    }

    init {
        refresh(forceRefresh = false)
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

    fun refresh(forceRefresh: Boolean = false) {
        // Cancel previous refresh if still running
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                Timber.d("AlbumViewModel.refresh: starting loadAudioFiles(isIncremental=${!forceRefresh})")
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
                Timber.d("AlbumViewModel.refresh: loadAudioFiles completed")
            } catch (e: Exception) {
                Timber.e(e, "Album refresh failed")
            } finally {
                Timber.d("AlbumViewModel.refresh: finally block, setting isRefreshing=false")
                _isRefreshing.value = false
            }
        }
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
