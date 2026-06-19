package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo

/**
 * Room Entity for cached audio files.
 * Optimized for fast queries and instant app startup.
 */
@Entity(
    tableName = "cached_audio_files",
    indices = [
        // `path` is the PRIMARY KEY below — it is unique by definition.
        // The previous `Index(value = ["path"], unique = true)` was removed when
        // the primary key was migrated from the polymorphic `id` (MediaStore _ID
        // at scan time, `path.hashCode()` after the first save) to `path` itself
        // (see lesson.md #24 + #25). Carrying a parallel mutable `id` was the
        // root cause of the cross-workspace cache collision bug.
        Index(value = ["albumId"]),
        Index(value = ["artistId"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["year"]),
        // Pre-computed sort columns — avoid COALESCE in ORDER BY (can't use B-tree index)
        Index(value = ["sortTitle"]),
        Index(value = ["sortAlbum"]),
        Index(value = ["artist", "sortAlbum", "trackNumber", "sortTitle"])
    ]
)
data class CachedAudioFileEntity(
    @PrimaryKey
    val path: String,
    val name: String,
    val size: Long,
    val duration: Long,
    val format: String,
    val mimeType: String?,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val albumId: Long?,
    val artistId: Long?,
    val dateAdded: Long,
    
    // Basic metadata (from MediaStore - fast)
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: String?,
    val trackNumber: Int?,
    
    // Pre-computed sort keys (populated at insert/update time).
    // Eliminates COALESCE in ORDER BY clauses so SQLite can use B-tree indices.
    val sortTitle: String = "",
    val sortAlbum: String = "",
    
    // Detailed metadata (loaded on-demand, cached when available)
    val albumArtist: String?,
    val genre: String?,
    val totalTracks: Int?,
    val discNumber: Int?,
    val totalDiscs: Int?,
    val composer: String?,
    val lyricist: String?,
    val conductor: String?,
    val originalArtist: String?,
    val comment: String?,
    val lyrics: String?,
    val customFieldsJson: String?,  // JSON-serialized Map<String, String>
    
    // ReplayGain info (1.0 + 2.0 fields)
    val replayGainTrackGain: Float?,
    val replayGainTrackPeak: Float?,
    val replayGainAlbumGain: Float?,
    val replayGainAlbumPeak: Float?,
    // ReplayGain 2.0 (EBU R128) - persisted so values survive a Room round-trip
    val replayGainTruePeak: Float?,
    val replayGainTrackLoudness: Float?,
    val replayGainAlbumLoudness: Float?,
    val replayGainTrackRange: Float?,
    val replayGainAlbumRange: Float?,
    val replayGainReferenceLoudness: Float?,

    // Timestamps for incremental scanning
    val lastScannedAt: Long,
    val fileLastModifiedAt: Long,
    
    // Timestamp when user last edited metadata via MetadataEditor.
    // Used to prevent EnrichmentWorker from overwriting user edits.
    // Set only when saveMetadata() succeeds; null otherwise.
    val lastEditedByUserAt: Long?
) {
    /**
     * Converts entity to domain model.
     */
    fun toAudioFile(): AudioFile {
        return AudioFile(
            path = path,
            name = name,
            size = size,
            duration = duration,
            format = AudioFormat.fromExtension(format),
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            mediaStoreAlbumId = albumId,
            mediaStoreArtistId = artistId,
            dateAdded = dateAdded,
            metadata = AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                year = year,
                genre = genre,
                trackNumber = trackNumber,
                totalTracks = totalTracks,
                discNumber = discNumber,
                totalDiscs = totalDiscs,
                composer = composer,
                lyricist = lyricist,
                conductor = conductor,
                originalArtist = originalArtist,
                comment = comment,
                lyrics = lyrics,
                albumArt = null,  // Always null in cache - use getAlbumArtUri() instead
                customFields = parseCustomFields(customFieldsJson)
            ),
            replayGainInfo = if (replayGainTrackGain != null) {
                ReplayGainInfo(
                    trackGain = replayGainTrackGain,
                    trackPeak = replayGainTrackPeak ?: 0f,
                    albumGain = replayGainAlbumGain,
                    albumPeak = replayGainAlbumPeak,
                    truePeak = replayGainTruePeak,
                    trackLoudness = replayGainTrackLoudness,
                    albumLoudness = replayGainAlbumLoudness,
                    trackRange = replayGainTrackRange,
                    albumRange = replayGainAlbumRange,
                    referenceLoudness = replayGainReferenceLoudness ?: -18f
                )
            } else null
        )
    }
    
    companion object {
        private val customFieldsGson = com.google.gson.Gson()

        /**
         * Deserializes the customFieldsJson column back into a Map.
         * Returns emptyMap() if the JSON is null, blank, or malformed.
         */
        fun parseCustomFields(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                customFieldsGson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /**
         * Creates entity from domain model.
         */
        fun fromAudioFile(
            audioFile: AudioFile,
            fileLastModified: Long,
            customFieldsJson: String? = null,
            lastEditedByUserAt: Long? = null
        ): CachedAudioFileEntity {
            return CachedAudioFileEntity(
                path = audioFile.path,
                name = audioFile.name,
                size = audioFile.size,
                duration = audioFile.duration,
                format = audioFile.format.name,
                mimeType = audioFile.mimeType,
                bitrate = audioFile.bitrate,
                sampleRate = audioFile.sampleRate,
                channels = audioFile.channels,
                albumId = audioFile.mediaStoreAlbumId,
                artistId = audioFile.mediaStoreArtistId,
                dateAdded = audioFile.dateAdded,
                title = audioFile.metadata.title,
                artist = audioFile.metadata.artist,
                album = audioFile.metadata.album,
                year = audioFile.metadata.year,
                trackNumber = audioFile.metadata.trackNumber,
                sortTitle = audioFile.metadata.title ?: audioFile.name,
                sortAlbum = audioFile.metadata.album ?: "",
                albumArtist = audioFile.metadata.albumArtist,
                genre = audioFile.metadata.genre,
                totalTracks = audioFile.metadata.totalTracks,
                discNumber = audioFile.metadata.discNumber,
                totalDiscs = audioFile.metadata.totalDiscs,
                composer = audioFile.metadata.composer,
                lyricist = audioFile.metadata.lyricist,
                conductor = audioFile.metadata.conductor,
                originalArtist = audioFile.metadata.originalArtist,
                comment = audioFile.metadata.comment,
                lyrics = audioFile.metadata.lyrics,
                customFieldsJson = customFieldsJson,
                replayGainTrackGain = audioFile.replayGainInfo?.trackGain,
                replayGainTrackPeak = audioFile.replayGainInfo?.trackPeak,
                replayGainAlbumGain = audioFile.replayGainInfo?.albumGain,
                replayGainAlbumPeak = audioFile.replayGainInfo?.albumPeak,
                replayGainTruePeak = audioFile.replayGainInfo?.truePeak,
                replayGainTrackLoudness = audioFile.replayGainInfo?.trackLoudness,
                replayGainAlbumLoudness = audioFile.replayGainInfo?.albumLoudness,
                replayGainTrackRange = audioFile.replayGainInfo?.trackRange,
                replayGainAlbumRange = audioFile.replayGainInfo?.albumRange,
                replayGainReferenceLoudness = audioFile.replayGainInfo?.referenceLoudness,
                lastScannedAt = System.currentTimeMillis(),
                fileLastModifiedAt = fileLastModified,
                lastEditedByUserAt = lastEditedByUserAt
            )
        }
    }
}

/**
 * FTS4 entity for full-text search on cached audio files.
 * Provides fast prefix and infix search using MATCH instead of LIKE.
 */
@Entity(tableName = "cached_audio_files_fts")
@Fts4(contentEntity = CachedAudioFileEntity::class)
data class CachedAudioFileFts(
    val title: String?,
    val artist: String?,
    val album: String?
)
