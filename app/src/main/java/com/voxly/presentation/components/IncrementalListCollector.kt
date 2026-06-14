package com.voxly.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.IncrementalList
import kotlinx.coroutines.flow.Flow

/**
 * Collects album diff events from [AlbumArtistAggregator.albumDiff] and
 * accumulates a local list. Each emitted diff implicitly carries the full
 * list snapshot in its [IncrementalList.after] field, and the SharedFlow
 * replays the most recent event, so the initial state is always correct
 * without explicit fallback.
 *
 * The resulting list can be passed to LazyColumn as usual — the benefit
 * is that LazyColumn's `key` parameter identifies items by identity, and
 * only the changed items recompose (instead of all items on every scan).
 */
@Composable
fun collectAlbumsDiff(
    albumDiff: Flow<IncrementalList<AlbumGroup>>
): List<AlbumGroup> {
    var accumulated by remember { mutableStateOf<List<AlbumGroup>>(emptyList()) }

    LaunchedEffect(albumDiff) {
        albumDiff.collect { diff ->
            accumulated = when (diff) {
                is IncrementalList.Reset -> diff.after
                is IncrementalList.Insert -> accumulated + diff.items
                is IncrementalList.Remove -> accumulated.filterNot { old ->
                    diff.items.any { removed -> keyMatch(old, removed) }
                }
                is IncrementalList.Update -> accumulated.map { old ->
                    diff.items.find { updated -> keyMatch(old, updated) } ?: old
                }
            }
        }
    }

    return accumulated
}

/**
 * Collects artist diff events from [AlbumArtistAggregator.artistDiff].
 */
@Composable
fun collectArtistsDiff(
    artistDiff: Flow<IncrementalList<ArtistGroup>>
): List<ArtistGroup> {
    var accumulated by remember { mutableStateOf<List<ArtistGroup>>(emptyList()) }

    LaunchedEffect(artistDiff) {
        artistDiff.collect { diff ->
            accumulated = when (diff) {
                is IncrementalList.Reset -> diff.after
                is IncrementalList.Insert -> accumulated + diff.items
                is IncrementalList.Remove -> accumulated.filterNot { old ->
                    diff.items.any { removed -> old.name == removed.name }
                }
                is IncrementalList.Update -> accumulated.map { old ->
                    diff.items.find { updated -> old.name == updated.name } ?: old
                }
            }
        }
    }

    return accumulated
}

/** Simple key matching — two albums are the "same" if name + artist match. */
private fun keyMatch(a: AlbumGroup, b: AlbumGroup): Boolean =
    a.name == b.name &&
        (a.albumArtist?.lowercase() ?: "") == (b.albumArtist?.lowercase() ?: "")
