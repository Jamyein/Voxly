package com.voxly.domain.model

import android.net.Uri
import java.io.Serializable

/**
 * Domain model representing an audio file with its metadata and replay gain information.
 */
data class AudioFile(
    val id: String,
    val path: String,
    val name: String,
    val size: Long,
    val duration: Long,
    val format: String,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val metadata: AudioMetadata,
    val replayGainInfo: ReplayGainInfo? = null,
    val mediaStoreAlbumId: Long? = null
) {
    companion object {
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }

    /**
     * Returns the album art URI for fast thumbnail loading via MediaStore.
     * Use this with Coil/Glide for efficient image loading without file parsing.
     * 
     * @return Uri for album art, or null if no album ID available
     */
    fun getAlbumArtUri(): Uri? {
        return mediaStoreAlbumId?.let { albumId ->
            Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
        }
    }

    /**
     * Returns a human-readable duration string.
     */
    fun getFormattedDuration(): String {
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Returns a human-readable file size string.
     */
    fun getFormattedSize(): String {
        return when {
            size >= 1_000_000_000 -> String.format("%.2f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.2f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.2f KB", size / 1_000.0)
            else -> "$size B"
        }
    }

    /**
     * Returns the bitrate value.
     * This method is R8-resistant as it doesn't rely on property name reflection.
     */
    fun getBitrateValue(): Int = bitrate

    /**
     * Returns the sample rate value.
     * This method is R8-resistant as it doesn't rely on property name reflection.
     */
    fun getSampleRateValue(): Int = sampleRate
}

/**
 * Domain model representing audio metadata (ID3 tags, etc.).
 */
data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val discNumber: Int? = null,
    val totalDiscs: Int? = null,
    val composer: String? = null,
    val lyricist: String? = null,
    val conductor: String? = null,
    val originalArtist: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val albumArt: ByteArray? = null,
    val customFields: Map<String, String> = emptyMap()
) : Serializable {
    /**
     * Returns a display-friendly title, falling back to filename if title is empty.
     */
    fun getDisplayTitle(fileName: String): String {
        return title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast(".")
    }

    /**
     * Returns a formatted track string (e.g., "3/12").
     */
    fun getFormattedTrackNumber(): String {
        return when {
            trackNumber != null && totalTracks != null -> "$trackNumber/$totalTracks"
            trackNumber != null -> trackNumber.toString()
            else -> ""
        }
    }

    /**
     * Returns a formatted disc string (e.g., "1/2").
     */
    fun getFormattedDiscNumber(): String {
        return when {
            discNumber != null && totalDiscs != null -> "$discNumber/$totalDiscs"
            discNumber != null -> discNumber.toString()
            else -> ""
        }
    }

    /**
     * Returns the release year of the audio.
     * This method is R8-resistant as it doesn't rely on property name reflection.
     */
    fun getReleaseYear(): String? = year

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioMetadata

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumArtist != other.albumArtist) return false
        if (year != other.year) return false
        if (genre != other.genre) return false
        if (trackNumber != other.trackNumber) return false
        if (totalTracks != other.totalTracks) return false
        if (discNumber != other.discNumber) return false
        if (totalDiscs != other.totalDiscs) return false
        if (composer != other.composer) return false
        if (lyricist != other.lyricist) return false
        if (conductor != other.conductor) return false
        if (originalArtist != other.originalArtist) return false
        if (comment != other.comment) return false
        if (lyrics != other.lyrics) return false
        if (!albumArt.contentEquals(other.albumArt)) return false
        if (customFields != other.customFields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (totalTracks ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + (totalDiscs ?: 0)
        result = 31 * result + (composer?.hashCode() ?: 0)
        result = 31 * result + (lyricist?.hashCode() ?: 0)
        result = 31 * result + (conductor?.hashCode() ?: 0)
        result = 31 * result + (originalArtist?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + albumArt.contentHashCode()
        result = 31 * result + customFields.hashCode()
        return result
    }
}

/**
 * Domain model representing ReplayGain information.
 */
data class ReplayGainInfo(
    val trackGain: Float = 0f,
    val trackPeak: Float = 0f,
    val albumGain: Float? = null,
    val albumPeak: Float? = null
) {
    /**
     * Returns track gain in dB format.
     */
    fun getFormattedTrackGain(): String {
        return String.format("%.2f dB", trackGain)
    }

    /**
     * Returns album gain in dB format.
     */
    fun getFormattedAlbumGain(): String {
        return albumGain?.let { String.format("%.2f dB", it) } ?: "N/A"
    }

    /**
     * Returns track peak as a percentage.
     */
    fun getFormattedTrackPeak(): String {
        return String.format("%.4f", trackPeak)
    }
}

/**
 * Domain model representing a directory entry.
 */
data class DirectoryEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val audioFiles: List<AudioFile> = emptyList(),
    val subDirectories: List<DirectoryEntry> = emptyList()
)

/**
 * Domain model representing a group of audio files by album.
 * Year, bitrate, and sampleRate are pre-computed and stored to avoid R8 issues.
 */
data class AlbumGroup(
    val name: String,
    val artist: String?,
    val files: List<AudioFile>,
    val coverPath: String? = null,
    val year: String? = null,
    val bitrate: Int = 0,
    val sampleRate: Int = 0
)

/**
 * Domain model representing a group of audio files by artist.
 */
data class ArtistGroup(
    val name: String,
    val albums: List<String>,
    val files: List<AudioFile>,
    val coverPath: String? = null
)

/**
 * Enum representing root tab selection in file browser.
 */
enum class RootTab {
    DIRECTORIES,
    ALBUMS,
    ARTISTS,
    ALL
}

/**
 * Enum representing audio file formats.
 */
enum class AudioFormat(val extensions: List<String>, val displayName: String) {
    MP3(listOf("mp3"), "MP3"),
    FLAC(listOf("flac"), "FLAC"),
    OGG(listOf("ogg", "oga"), "OGG Vorbis"),
    M4A(listOf("m4a", "mp4"), "M4A (AAC)"),
    WMA(listOf("wma"), "Windows Media Audio"),
    WAV(listOf("wav"), "WAV"),
    APE(listOf("ape"), "APE"),
    WavPack(listOf("wv"), "WavPack"),
    OPUS(listOf("opus"), "Opus"),
    OTHER(listOf(), "Unknown");

    companion object {
        fun fromExtension(extension: String): AudioFormat {
            return entries.find { it.extensions.contains(extension.lowercase()) } ?: OTHER
        }
    }
}

/**
 * Constants for MediaStore track parsing.
 */
private object MediaStoreConstants {
    // Track number offset used by some sources incorrectly
    const val TRACK_OFFSET = 1000
    // Maximum track number after normalization
    const val MAX_NORMALIZED_TRACK = 999
    // Track number range that indicates corruption
    const val MIN_CORRUPTED_TRACK = 1000
    const val MAX_CORRUPTED_TRACK = 10000
}

/**
 * Parses the MediaStore TRACK field which encodes both track number and total tracks.
 *
 * MediaStore stores: trackNumber | (totalTracks << 16)
 * - Bits 0-15: track number
 * - Bits 16-31: total tracks
 *
 * Also handles corrupted track values like 1001 which should be 1.
 *
 * @param value The raw track value from MediaStore
 * @return Pair of (trackNumber, totalTracks) or (null, null) if invalid
 */
fun parseMediaStoreTrackField(value: Int): Pair<Int?, Int?> {
    if (value <= 0) return Pair(null, null)

    var trackNumber = value and 0xFFFF
    val totalTracks = (value shr 16) and 0xFFFF

    // Normalize corrupted track numbers (some sources add 1000 offset incorrectly)
    // e.g., 1001 should be 1, 1012 should be 12
    if (trackNumber in MediaStoreConstants.MIN_CORRUPTED_TRACK until MediaStoreConstants.MAX_CORRUPTED_TRACK) {
        val normalized = trackNumber - MediaStoreConstants.TRACK_OFFSET
        if (normalized in 1..MediaStoreConstants.MAX_NORMALIZED_TRACK) {
            trackNumber = normalized
        }
    }

    return Pair(
        if (trackNumber > 0) trackNumber else null,
        if (totalTracks > 0) totalTracks else null
    )
}
