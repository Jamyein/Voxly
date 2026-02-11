package com.mp3tag.android.domain.repository

import com.mp3tag.android.domain.model.AudioFile
import com.mp3tag.android.domain.model.AudioMetadata
import com.mp3tag.android.domain.model.ReplayGainInfo
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
    fun scanAudioFiles(directoryPath: String? = null): Flow<List<AudioFile>>

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
     * @param scanQuality The quality level for scanning
     * @return Flow emitting scan progress (0.0 to 1.0)
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality = ScanQuality.NORMAL
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
 */
enum class ScanQuality(val sampleRate: Int, val channels: Int) {
    FAST(22050, 1),
    NORMAL(44100, 2),
    ACCURATE(48000, 2)
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
    val coverArtUrl: String?
)

/**
 * Data class representing an online recording.
 */
data class OnlineRecording(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int?,
    val releaseId: String?
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
    val coverArtUrl: String?
)

/**
 * Data class representing an online track.
 */
data class OnlineTrack(
    val number: Int,
    val title: String,
    val duration: Int?,
    val artist: String?
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
