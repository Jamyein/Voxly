package com.voxly.domain.repository

import com.voxly.domain.model.Lyrics
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for lyrics operations.
 */
interface LyricsRepository {
    /**
     * Reads lyrics from an audio file.
     * @param filePath Path to the audio file
     * @return Result containing Lyrics object or null if no lyrics found
     */
    suspend fun readLyrics(filePath: String): Result<Lyrics?>

    /**
     * Saves lyrics to an audio file.
     * @param filePath Path to the audio file
     * @param lyrics Lyrics to save
     * @return Result indicating success or failure
     */
    suspend fun saveLyrics(filePath: String, lyrics: Lyrics): Result<Unit>

    /**
     * Removes lyrics from an audio file.
     * @param filePath Path to the audio file
     * @return Result indicating success or failure
     */
    suspend fun removeLyrics(filePath: String): Result<Unit>

    /**
     * Searches for lyrics online.
     * @param trackName Track title
     * @param artistName Artist name
     * @param albumName Album name (optional)
     * @return Result containing list of found lyrics
     */
    suspend fun searchOnlineLyrics(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null
    ): Result<List<OnlineLyricsResult>>

    /**
     * Gets lyrics from online source using a selected search result.
     * @param result Selected online lyrics result
     * @return Result containing Lyrics object
     */
    suspend fun getOnlineLyrics(result: OnlineLyricsResult): Result<Lyrics>

    /**
     * Gets cached lyrics for a song.
     * @param trackName Track title
     * @param artistName Artist name
     * @return Cached lyrics or null
     */
    suspend fun getCachedLyrics(
        trackName: String,
        artistName: String
    ): Lyrics?

    /**
     * Caches lyrics for offline use.
     * @param trackName Track title
     * @param artistName Artist name
     * @param lyrics Lyrics to cache
     */
    suspend fun cacheLyrics(
        trackName: String,
        artistName: String,
        lyrics: Lyrics
    )

    /**
     * Clears all cached lyrics.
     */
    suspend fun clearCache()
}

/**
 * Data class representing an online lyrics search result.
 */
data class OnlineLyricsResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double?,
    val hasSyncedLyrics: Boolean,
    val hasPlainLyrics: Boolean,
    val isInstrumental: Boolean,
    val source: String, // Source: "LRCLIB", "NetEase", "QQ Music"
    val sourceKey: String? = null, // Source-specific key (e.g. QQ Music songMid)
    val preview: String? // First few lines for preview
)

/**
 * Exception for lyrics-related errors.
 */
class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
