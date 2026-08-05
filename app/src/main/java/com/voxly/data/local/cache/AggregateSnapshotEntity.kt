package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Serialized aggregate snapshot (albums + artists) so cold start renders the
 * library without re-running the full pinyin sort + grouping rebuild.
 *
 * Single-row table keyed by [fingerprint] — a deterministic function of the
 * aggregate's inputs (cached file rows + filter settings + separator config).
 * When the fingerprint matches at startup, the persisted structures are a
 * correct representation of the current cache by construction (aggregation is
 * a pure function of those inputs); any mismatch falls back to a full rebuild.
 */
@Entity(tableName = "aggregate_snapshot")
data class AggregateSnapshotEntity(
    @PrimaryKey
    val id: Int = 1,
    /** Deterministic fingerprint of the inputs the aggregates were built from. */
    val fingerprint: String,
    /** [AlbumSnapshotDto] list in the published (pinyin-sorted) order. */
    val albumsJson: String,
    /** [ArtistSnapshotDto] list in the published (pinyin-sorted) order. */
    val artistsJson: String,
    val savedAt: Long
)

/**
 * Compact album group for persistence: identity + display fields + the already
 * sorted file-path list. AudioFile payloads are NOT serialized — they are
 * resolved from the hot cache at hydrate time (paths are the cache PK).
 * Storing the paths in sorted order preserves the group's file order without
 * re-sorting (which would re-invoke the expensive pinyin transliterator).
 */
@Serializable
data class AlbumSnapshotDto(
    val key: String,
    val name: String,
    val albumArtist: String?,
    val coverPath: String?,
    val year: Int?,
    /** File paths in the group's published (track-number-sorted) order. */
    val paths: List<String>
)

/** Compact artist group for persistence — same model as [AlbumSnapshotDto]. */
@Serializable
data class ArtistSnapshotDto(
    val key: String,
    val name: String,
    val albums: List<String>,
    val coverPath: String?,
    /** File paths in the group's published (album-sorted) order. */
    val paths: List<String>
)
