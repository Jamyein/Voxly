package com.voxly.presentation.viewmodel

import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.usecase.ScanState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder for library-wide shared scan coordination.
 * Provides a refresh trigger that LibraryViewModel collects to know when to refresh.
 * Also exposes scan state and isRefreshing for all screens.
 *
 * Since @HiltViewModel cannot be injected into other @HiltViewModels,
 * we use this singleton to coordinate between them.
 */
@Singleton
class LibraryDataHolder @Inject constructor() {

    // Shared refresh trigger - LibraryViewModel collects this to trigger scans
    private val _refreshTrigger = MutableSharedFlow<Pair<Boolean, (Boolean) -> Unit>>(extraBufferCapacity = 1)
    val refreshTrigger: SharedFlow<Pair<Boolean, (Boolean) -> Unit>> = _refreshTrigger

    // Called by LibraryViewModel to collect refresh requests
    suspend fun collectRefreshTriggers(
        onRefresh: suspend (forceRefresh: Boolean) -> Unit
    ) {
        _refreshTrigger.collect { (forceRefresh, _) ->
            onRefresh(forceRefresh)
        }
    }

    // Convenience method to trigger refresh from any ViewModel
    fun requestRefresh(forceRefresh: Boolean = false) {
        // The actual refresh callback will be called by LibraryViewModel
        // This just emits to the shared flow
        _refreshTrigger.tryEmit(forceRefresh to { })
    }
}
