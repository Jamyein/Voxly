package com.voxly.domain.repository

import com.voxly.domain.model.Lyrics
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local lyrics operations.
 */
interface LocalLyricsRepository {
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
}

/**
 * Repository interface for online lyrics operations.
 */
interface OnlineLyricsRepository {
    /**
     * Searches for lyrics online with flow-based streaming results.
     * @param trackName Track title
     * @param artistName Artist name
     * @param albumName Album name (optional)
     * @return Flow emitting search results, completion, and errors per source
     */
    fun searchOnlineLyricsFlow(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Flow<LyricsSourceResult>

    /**
     * Gets lyrics from online source using a selected search result.
     * @param result Selected online lyrics result
     * @return Result containing Lyrics object
     */
    suspend fun getOnlineLyrics(result: OnlineLyricsResult): Result<Lyrics>

    /**
     * Gets cached lyrics for a song.
     */
    suspend fun getCachedLyrics(trackName: String, artistName: String): Lyrics?

    /**
     * Caches lyrics for offline use.
     */
    suspend fun cacheLyrics(trackName: String, artistName: String, lyrics: Lyrics)

    /**
     * Clears all cached lyrics.
     */
    suspend fun clearCache()
}

/**
 * Sealed class representing the result of an online lyrics search operation.
 * Used for flow-based streaming results from multiple sources.
 */
sealed class LyricsSourceResult {
    /**
     * A successful result containing lyrics data.
     */
    data class Result(
        val lyrics: OnlineLyricsResult,
        val source: String
    ) : LyricsSourceResult()

    /**
     * Indicates that a source has completed sending results.
     */
    data class SourceCompleted(val source: String) : LyricsSourceResult()

    /**
     * An error occurred while searching a source.
     */
    data class Error(val source: String, val message: String) : LyricsSourceResult()
}