package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo

/**
 * Room Entity for cached audio files.
 * Optimized for fast queries and instant app startup.
 */
@Entity(
    tableName = "cached_audio_files",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["artistId"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["year"])
    ]
)
data class CachedAudioFileEntity(
    @PrimaryKey
    val id: String,
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
    
    // ReplayGain info
    val replayGainTrackGain: Float?,
    val replayGainTrackPeak: Float?,
    val replayGainAlbumGain: Float?,
    val replayGainAlbumPeak: Float?,
    
    // Timestamps for incremental scanning
    val lastScannedAt: Long,
    val fileLastModifiedAt: Long
) {
    /**
     * Converts entity to domain model.
     */
    fun toAudioFile(): AudioFile {
        return AudioFile(
            id = id,
            path = path,
            name = name,
            size = size,
            duration = duration,
            format = format,
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
                customFields = emptyMap()  // Parsed separately if needed
            ),
            replayGainInfo = if (replayGainTrackGain != null) {
                ReplayGainInfo(
                    trackGain = replayGainTrackGain,
                    trackPeak = replayGainTrackPeak ?: 0f,
                    albumGain = replayGainAlbumGain,
                    albumPeak = replayGainAlbumPeak
                )
            } else null
        )
    }
    
    companion object {
        /**
         * Creates entity from domain model.
         */
        fun fromAudioFile(
            audioFile: AudioFile,
            fileLastModified: Long,
            customFieldsJson: String? = null
        ): CachedAudioFileEntity {
            return CachedAudioFileEntity(
                id = audioFile.id,
                path = audioFile.path,
                name = audioFile.name,
                size = audioFile.size,
                duration = audioFile.duration,
                format = audioFile.format,
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
                lastScannedAt = System.currentTimeMillis(),
                fileLastModifiedAt = fileLastModified
            )
        }
    }
}
