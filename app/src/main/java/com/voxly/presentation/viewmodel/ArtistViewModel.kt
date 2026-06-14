package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.SortUtil
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.ArtistListItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Artists list screen.
 *
 * Refresh coordination: pull-to-refresh and initial-load refreshes go through
 * [LibraryDataHolder.requestRefresh], the single fan-in point. The actual
 * scan runs in [LibraryScanViewModel], which updates
 * [LibraryDataHolder.isRefreshing] for global visibility across screens.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {

    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList()
        )

    val artistListItems: StateFlow<List<ArtistListItemState>> = artists
        .map { artistGroups ->
            artistGroups
                .groupBy { it.name }
                .map { (name, groups) ->
                    val first = groups.first()
                    val albumNames = groups.flatMap { it.files }
                        .mapNotNull { it.metadata.album }
                        .filter { it.isNotBlank() }
                        .toSet()
                    val coverFile = groups.flatMap { it.files }
                        .firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ArtistListItemState(
                        name = name,
                        coverPath = first.coverPath,
                        coverAlbumId = coverFile?.mediaStoreAlbumId,
                        albumCount = albumNames.size,
                        trackCount = groups.sumOf { it.files.size }
                    )
                }
                .sortedBy { SortUtil.toSortablePinyin(it.name) }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList()
        )

    /**
     * Mirrors the global scan activity maintained by [LibraryDataHolder].
     * A VM created mid-scan picks up the current spinner state immediately
     * on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing

    /**
     * Request a library refresh via [LibraryDataHolder]. Bursts are
     * deduplicated by the holder's conflated SharedFlow + the collector's
     * `collectLatest`.
     */
    fun refresh(forceRefresh: Boolean = false) {
        Timber.tag("Voxly").i("ArtistViewModel refresh -> LibraryDataHolder")
        libraryDataHolder.requestRefresh(forceRefresh)
    }


}
