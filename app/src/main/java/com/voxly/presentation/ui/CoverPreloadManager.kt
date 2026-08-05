package com.voxly.presentation.ui

import android.content.Context
import android.net.Uri
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistListItemState
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Keeps the first screen of Albums/Artists covers hot in Coil's memory cache.
 *
 * Unified with the display-data architecture: it observes the same app-scope
 * Eagerly projections the screens render ([AudioFileScanner.sortedAlbums] /
 * [AudioFileScanner.artistListItems]) and maintains the invariant "the first
 * screen's cover bitmaps are in Coil's memory cache" — not a one-shot guess at
 * app start, but re-established whenever the lists change (start hydration,
 * scan results, sort/filter toggles). When the user first opens a tab, the
 * visible covers are memory-cache hits: drawn synchronously, no shimmer, no
 * fade, no flash.
 *
 * The pre-warm requests reuse the renderer's exact memory key
 * ([coil3.memory.MemoryCache.Key] = memoryCacheKey + extras, no size) and match
 * the display pixel size (computed from density), so the cached bitmap is never
 * upscaled. Scrolling beyond the first screen is handled by the existing
 * scroll-based [com.voxly.presentation.components.CoverArtPreloader], which
 * shares the same key mechanism.
 *
 * Failure is silent: a cover that cannot be resolved is skipped; pre-warming
 * never blocks or delays startup (runs on [applicationScope], cancelled by
 * [collectLatest] when a newer list supersedes it).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class CoverPreloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val coverUriProvider: CoverUriProvider,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "CoverPreloadManager"

        /** Covers kept warm ≈ first screen + one extra row per list. */
        private const val PREWARM_COUNT = 24

        /** Debounce so a scan's progressive batch storm pre-warms once. */
        private const val PREWARM_DEBOUNCE_MS = 600L
    }

    private val imageLoader = context.imageLoader

    // Display sizes matched to the renderers so the cached bitmap is never
    // upscaled: AlbumCard renders AlbumArtImage(size = 200.dp), ArtistListItem
    // uses 48.dp. roundToPx in AlbumArtImage uses the same density.
    private val albumPx = (200 * context.resources.displayMetrics.density).toInt()
    private val artistPx = (48 * context.resources.displayMetrics.density).toInt()

    private var started = false

    /** Idempotent: call once from [android.app.Application.onCreate]. */
    fun start() {
        if (started) return
        started = true
        applicationScope.launch {
            combine(
                audioFileScanner.sortedAlbums,
                audioFileScanner.artistListItems
            ) { albums, artists -> albums to artists }
                .debounce(PREWARM_DEBOUNCE_MS)
                .collectLatest { (albums, artists) ->
                    prewarmAlbums(albums.take(PREWARM_COUNT))
                    prewarmArtists(artists.take(PREWARM_COUNT))
                }
        }
    }

    private suspend fun prewarmAlbums(albums: List<AlbumGroup>) {
        if (albums.isEmpty()) return
        coroutineScope {
            val uris = albums
                .map { album -> coverFileOf(album) }
                .filterNotNull()
                .map { file ->
                    async(Dispatchers.IO) {
                        coverUriProvider.getCoverUri(file.mediaStoreAlbumId, file.path)
                    }
                }
                .awaitAll()
            uris.filterNotNull().forEach { uri -> enqueue(uri, albumPx) }
        }
    }

    private suspend fun prewarmArtists(artists: List<ArtistListItemState>) {
        if (artists.isEmpty()) return
        coroutineScope {
            val uris = artists
                .map { item -> item.coverPath to item.coverAlbumId }
                .filter { (path, _) -> !path.isNullOrBlank() }
                .map { (path, albumId) ->
                    async(Dispatchers.IO) {
                        coverUriProvider.getCoverUri(albumId, path)
                    }
                }
                .awaitAll()
            uris.filterNotNull().forEach { uri -> enqueue(uri, artistPx) }
        }
    }

    /** Best cover source for an album — mirrors AlbumScreenContent.coverFile(). */
    private fun coverFileOf(album: AlbumGroup): AudioFile? =
        album.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: album.files.firstOrNull()

    private fun enqueue(uri: Uri, px: Int) {
        try {
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(Size(px, px))
                    .precision(Precision.INEXACT)
                    .memoryCacheKey(uri.toString())
                    .build()
            )
        } catch (e: Exception) {
            Timber.w(TAG, "Cover pre-warm enqueue failed: $uri", e)
        }
    }
}
