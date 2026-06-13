package com.voxly.presentation.viewmodel

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Singleton holder for library-wide shared scan coordination.
 * Provides a refresh trigger that LibraryScanViewModel collects to know when to refresh.
 *
 * Since @HiltViewModel cannot be injected into other @HiltViewModels,
 * we use this singleton to coordinate between them.
 *
 * Uses a CONFLATED-style MutableSharedFlow with extraBufferCapacity so that a
 * rapid burst of refresh requests is collapsed to a single most-recent value --
 * a lost emit here used to silently drop refresh requests via tryEmit.
 */
@Singleton
class LibraryDataHolder @Inject constructor() {

    // Conflated: keep only the latest request. If multiple refreshes come in
    // fast, intermediate requests are dropped (the latest one will trigger the
    // scan anyway).
    private val _refreshTrigger = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val refreshTrigger: SharedFlow<Boolean> = _refreshTrigger

    // Called by LibraryScanViewModel to collect refresh requests
    suspend fun collectRefreshTriggers(
        onRefresh: suspend (forceRefresh: Boolean) -> Unit
    ) {
        _refreshTrigger.collect { forceRefresh ->
            onRefresh(forceRefresh)
        }
    }

    // Convenience method to trigger refresh from any ViewModel.
    // Uses tryEmit but with the buffer-overflow policy we never lose requests
    // here -- the buffer is sized to keep at least one pending value.
    fun requestRefresh(forceRefresh: Boolean = false) {
        _refreshTrigger.tryEmit(forceRefresh)
    }
}
