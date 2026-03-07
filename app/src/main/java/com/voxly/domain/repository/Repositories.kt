package com.voxly.domain.repository

import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for audio file operations.
 * Defines the contract for data access layer implementations.
 */
interface AudioRepository {
    /**
     * Scans a directory and returns all audio files.
     * @param directoryPath The path to scan, or null for all storage
     * @return Flow emitting list of audio files
     */
    fun scanAudioFiles(
        directoryPath: String? = null,
        forceRefresh: Boolean = false
    ): Flow<List<AudioFile>>

    /**
     * Checks if cached audio files exist.
     * Use this to avoid unnecessary scans.
     * @return true if cache has data
     */
    suspend fun hasCachedData(): Boolean

    /**
     * Gets cached audio files if available.
     * @return Flow emitting cached audio files, or empty if no cache
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>>

    /**
     * Gets a single audio file by its path.
     * @param filePath The path to the audio file
     * @return Result containing the audio file or an error
     */
    suspend fun getAudioFile(filePath: String): Result<AudioFile>

    /**
     * Reads metadata from an audio file.
     * @param filePath The path to the audio file
     * @return Result containing the metadata or an error
     */
    suspend fun readMetadata(filePath: String): Result<AudioMetadata>

    /**
     * Updates metadata for an audio file.
     * @param filePath The path to the audio file
     * @param metadata The new metadata to save
     * @return Result indicating success or failure
     */
    suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit>

    /**
     * Extracts album art from an audio file.
     * @param filePath The path to the audio file
     * @return Result containing the album art bytes or null if not found
     */
    suspend fun extractAlbumArt(filePath: String): Result<ByteArray?>

    /**
     * Sets album art for an audio file.
     * @param filePath The path to the audio file
     * @param albumArtBytes The album art bytes to set
     * @return Result indicating success or failure
     */
    suspend fun setAlbumArt(filePath: String, albumArtBytes: ByteArray): Result<Unit>

    /**
     * Removes album art from an audio file.
     * @param filePath The path to the audio file
     * @return Result indicating success or failure
     */
    suspend fun removeAlbumArt(filePath: String): Result<Unit>
}

/**
 * Repository interface for ReplayGain operations.
 */
interface ReplayGainRepository {
    /**
     * Scans audio files and calculates ReplayGain values.
     * @param filePaths List of file paths to scan
     * @param scanQuality The quality level for scanning (determines max sample rate)
     * @param targetLoudness Target loudness in LUFS (default -14.0, standard ReplayGain)
     * @return Flow emitting scan progress (0.0 to 1.0)
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality = ScanQuality.NORMAL,
        targetLoudness: Float = -14f
    ): Flow<ScanProgress>

    /**
     * Applies ReplayGain values to audio files.
     * @param filePaths List of file paths to update
     * @param applyToTrack Whether to apply track gain
     * @param applyToAlbum Whether to apply album gain
     * @return Result indicating success or failure
     */
    suspend fun applyReplayGain(
        filePaths: List<String>,
        applyToTrack: Boolean = true,
        applyToAlbum: Boolean = false
    ): Result<Unit>

    /**
     * Saves ReplayGain information to audio files.
     * @param filePath The path to the audio file
     * @param replayGainInfo The ReplayGain info to save
     * @return Result indicating success or failure
     */
    suspend fun saveReplayGain(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Result<Unit>

    /**
     * Reads existing ReplayGain information from a file.
     * @param filePath The path to the audio file
     * @return Result containing the ReplayGain info or null if not found
     */
    suspend fun readReplayGain(filePath: String): Result<ReplayGainInfo?>
}

/**
 * Repository interface for online metadata lookup (MusicBrainz).
 */
interface OnlineMetadataRepository {
    /**
     * Searches for metadata by artist and album title.
     * @param artist The artist name
     * @param album The album title
     * @return Result containing list of matching releases
     */
    suspend fun searchByArtistAlbum(artist: String, album: String): Result<List<OnlineRelease>>

    /**
     * Searches for metadata by track title and artist.
     * @param title The track title
     * @param artist The artist name (optional)
     * @return Result containing list of matching recordings
     */
    suspend fun searchByTrack(title: String, artist: String? = null): Result<List<OnlineRecording>>

    /**
     * Gets detailed metadata for a specific release.
     * @param releaseId The MusicBrainz release ID
     * @return Result containing the detailed release info
     */
    suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails>

    /**
     * Gets cover art for a release.
     * @param releaseId The MusicBrainz release ID
     * @return Result containing the cover art bytes
     */
    suspend fun getCoverArt(releaseId: String): Result<ByteArray?>
}

/**
 * Repository interface for managing recent edits.
 */
interface RecentEditsRepository {
    /**
     * Gets recent edited files.
     * @param limit Maximum number of entries to return
     * @return Flow emitting list of recent edits
     */
    fun getRecentEdits(limit: Int = 50): Flow<List<RecentEdit>>

    /**
     * Adds a new recent edit entry.
     * @param filePath The path of the edited file
     * @param originalMetadata The original metadata
     * @param newMetadata The new metadata
     */
    suspend fun addRecentEdit(
        filePath: String,
        originalMetadata: AudioMetadata,
        newMetadata: AudioMetadata
    )

    /**
     * Clears all recent edits.
     */
    suspend fun clearRecentEdits()
}

/**
 * Enum representing ReplayGain scan quality levels.
 * 
 * The actual sample rate used for scanning is determined dynamically:
 * - If file sample rate <= maxSampleRate: use original sample rate
 * - If file sample rate > maxSampleRate: downsample to maxSampleRate
 * 
 * This ensures optimal performance for high-resolution audio files while
 * preserving quality for standard audio files.
 */
enum class ScanQuality(val maxSampleRate: Int) {
    /** Fast mode: limited to 22.05kHz for quick preview scanning */
    FAST(22050),
    /** Normal mode: limited to 48kHz, suitable for most audio files */
    NORMAL(48000),
    /** Accurate mode: supports up to 192kHz for high-resolution audio */
    ACCURATE(192000)
}

/**
 * Enum representing ReplayGain scan mode.
 * Compatible with foobar2000 scan modes:
 * 1. Track Only: Calculate track gain only, no album gain
 * 2. Single Album: Calculate both track and album gain, treating selection as one album
 * 3. Albums: Calculate track and album gain, grouped by album tags
 */
enum class ScanMode(val displayName: String) {
    /** Track gain only - calculate gain for each track independently (no album gain) */
    TRACK_ONLY("Track Only"),
    /** Single album - treat selection as one album, calculate both track and album gain */
    SINGLE_ALBUM("Single Album"),
    /** Albums - auto-group by album tags, calculate track and album gain per album */
    ALBUMS("Albums")
}

/**
 * Data class representing scan progress.
 */
data class ScanProgress(
    val currentFile: Int,
    val totalFiles: Int,
    val percentage: Float,
    val currentFilePath: String,
    val status: ScanStatus
)

/**
 * Enum representing scan status.
 */
enum class ScanStatus {
    SCANNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Data class representing an online music release.
 */
data class OnlineRelease(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val format: String?,
    val trackCount: Int?,
    val coverArtUrl: String?,
    val source: OnlineSource = OnlineSource.UNKNOWN,
    val songTitle: String? = null,
    val albumTitle: String? = null,
    val hasSyncedLyrics: Boolean = false,
    val discNumber: Int? = null,
    val discCount: Int? = null,
    val trackNumber: Int? = null,
    val recordLabel: String? = null,
    val comment: String? = null,
    val genre: String? = null,
    val albumArtist: String? = null,
    val lyrics: String? = null
)


/**
 * Enum representing the source of online metadata.
 */
enum class OnlineSource {
    MUSICBRAINZ,
    ITUNES,
    NETEASE,
    QQ_MUSIC,
    UNKNOWN;

    companion object {
        fun fromString(value: String): OnlineSource {
            return when (value) {
                "MusicBrainz" -> MUSICBRAINZ
                "iTunes" -> ITUNES
                "NetEase" -> NETEASE
                "QQ Music" -> QQ_MUSIC
                else -> UNKNOWN
            }
        }
    }

    fun toDisplayString(): String {
        return when (this) {
            MUSICBRAINZ -> "MusicBrainz"
            ITUNES -> "iTunes"
            NETEASE -> "NetEase"
            QQ_MUSIC -> "QQ Music"
            UNKNOWN -> "Unknown"
        }
    }
}


/**
 * Data class representing an online recording.
 */
data class OnlineRecording(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,  // Album name for track search results
    val duration: Int?,
    val releaseId: String?,
    val source: OnlineSource = OnlineSource.UNKNOWN,
    val coverArtUrl: String? = null,
    val coverArtBytes: ByteArray? = null,  // 二进制封面数据 (如 MusicBrainz)
    val discNumber: Int? = null,      // 碟号 (song.disc)
    val discCount: Int? = null,       // 总碟数
    val trackNumber: Int? = null,      // 曲目号 (song.no)
    val trackCount: Int? = null,       // 总曲目数
    val recordLabel: String? = null,   // 唱片公司 (album.company)
    val comment: String? = null,        // 注释/别名 (song.alias)
    val genre: String? = null,          // 流派 (primaryGenreName)
    val albumArtist: String? = null,    // 专辑艺术家 (collectionArtistName)
    val lyrics: String? = null,          // 歌词 (from NetEase lyrics API)
    val year: Int? = null               // 年份 (from releaseDate)
)


/**
 * Data class representing detailed online release information.
 */
data class OnlineReleaseDetails(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val genre: String?,
    val trackCount: Int?,
    val tracks: List<OnlineTrack>,
    val coverArtUrl: String?,
    val discNumber: Int? = null,
    val discCount: Int? = null,
    val trackNumber: Int? = null,
    val recordLabel: String? = null,
    val comment: String? = null,
    val albumArtist: String? = null
)


/**
 * Data class representing an online track.
 */
data class OnlineTrack(
    val number: Int,
    val title: String,
    val duration: Int?,
    val artist: String?,
    val discNumber: Int? = null
)

/**
 * Data class representing a recent edit entry.
 */
data class RecentEdit(
    val filePath: String,
    val fileName: String,
    val timestamp: Long,
    val originalMetadata: AudioMetadata,
    val newMetadata: AudioMetadata
)
