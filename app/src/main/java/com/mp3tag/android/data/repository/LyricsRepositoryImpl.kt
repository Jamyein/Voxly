package com.mp3tag.android.data.repository

import android.content.Context
import com.mp3tag.android.data.local.metadata.JaudiotaggerMetadataProcessor
import com.mp3tag.android.data.remote.lrclib.LRCLibApi
import com.mp3tag.android.data.remote.lrclib.LRCLibLyrics
import com.mp3tag.android.domain.model.Lyrics
import com.mp3tag.android.domain.repository.LyricsException
import com.mp3tag.android.domain.repository.LyricsRepository
import com.mp3tag.android.domain.repository.OnlineLyricsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LyricsRepository.
 * Handles local lyrics operations and online lyrics fetching.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: JaudiotaggerMetadataProcessor,
    private val lrclibApi: LRCLibApi
) : LyricsRepository {

    // Simple in-memory cache (in production, use Room database)
    private val lyricsCache = mutableMapOf<String, Lyrics>()

    override suspend fun readLyrics(filePath: String): Result<Lyrics?> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(LyricsException("File not found: $filePath"))
                }

                val audioFile: AudioFile = AudioFileIO.read(file)
                val tag: Tag = audioFile.tag ?: return@withContext Result.success(null)

                // Try to read unsynchronized lyrics (USLT)
                val lyricsText = tag.getFirst(FieldKey.LYRICS)

                if (lyricsText.isNullOrBlank()) {
                    // Try to read from custom field or comment
                    val comment = tag.getFirst(FieldKey.COMMENT)
                    if (!comment.isNullOrBlank() && comment.contains("[")) {
                        // Might be LRC format in comment
                        return@withContext Result.success(Lyrics.parseLrc(comment))
                    }
                    return@withContext Result.success(null)
                }

                // Check if it's LRC format
                val lyrics = if (lyricsText.contains("[") && lyricsText.contains("]")) {
                    Lyrics.parseLrc(lyricsText)
                } else {
                    Lyrics.createUnsynced(lyricsText)
                }

                Result.success(lyrics)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to read lyrics", e))
            }
        }

    override suspend fun saveLyrics(filePath: String, lyrics: Lyrics): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.canWrite()) {
                    return@withContext Result.failure(LyricsException("File not accessible: $filePath"))
                }

                val audioFile: AudioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: audioFile.createDefaultTag()

                // Save lyrics as USLT (Unsynchronized Lyrics)
                // If synced, save in LRC format; otherwise save as plain text
                val lyricsText = if (lyrics.isSynced) {
                    lyrics.toLrcFormat()
                } else {
                    lyrics.text
                }

                tag.setField(FieldKey.LYRICS, lyricsText)
                audioFile.tag = tag
                AudioFileIO.write(audioFile)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to save lyrics", e))
            }
        }

    override suspend fun removeLyrics(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.canWrite()) {
                    return@withContext Result.failure(LyricsException("File not accessible: $filePath"))
                }

                val audioFile: AudioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: return@withContext Result.success(Unit)

                // Remove lyrics field
                tag.deleteField(FieldKey.LYRICS)
                audioFile.tag = tag
                AudioFileIO.write(audioFile)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to remove lyrics", e))
            }
        }

    override suspend fun searchOnlineLyrics(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Result<List<OnlineLyricsResult>> = withContext(Dispatchers.IO) {
        try {
            val response = lrclibApi.searchLyrics(
                trackName = trackName,
                artistName = artistName,
                albumName = albumName
            )

            if (response.isSuccessful) {
                val lrclibResult = response.body()
                if (lrclibResult != null) {
                    // LRCLIB returns a single result for search
                    val result = OnlineLyricsResult(
                        id = lrclibResult.id,
                        trackName = lrclibResult.trackName,
                        artistName = lrclibResult.artistName,
                        albumName = lrclibResult.albumName,
                        duration = lrclibResult.duration,
                        hasSyncedLyrics = !lrclibResult.syncedLyrics.isNullOrBlank(),
                        hasPlainLyrics = !lrclibResult.plainLyrics.isNullOrBlank(),
                        isInstrumental = lrclibResult.instrumental,
                        preview = lrclibResult.plainLyrics?.lines()?.take(3)?.joinToString("\n")
                            ?: lrclibResult.syncedLyrics?.lines()?.take(3)?.joinToString("\n")
                    )
                    Result.success(listOf(result))
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.failure(LyricsException("Search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(LyricsException("Network error during search", e))
        }
    }

    override suspend fun getOnlineLyricsById(id: Long): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                val response = lrclibApi.getLyricsById(id)

                if (response.isSuccessful) {
                    val lrclibLyrics = response.body()
                        ?: return@withContext Result.failure(LyricsException("Empty response"))

                    val lyrics = convertToLyrics(lrclibLyrics)
                    Result.success(lyrics)
                } else {
                    Result.failure(LyricsException("Failed to get lyrics: ${response.errorBody()?.string()}"))
                }
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    override suspend fun getCachedLyrics(
        trackName: String,
        artistName: String
    ): Lyrics? = withContext(Dispatchers.IO) {
        val key = generateCacheKey(trackName, artistName)
        lyricsCache[key]
    }

    override suspend fun cacheLyrics(
        trackName: String,
        artistName: String,
        lyrics: Lyrics
    ) = withContext(Dispatchers.IO) {
        val key = generateCacheKey(trackName, artistName)
        lyricsCache[key] = lyrics
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        lyricsCache.clear()
    }

    /**
     * Converts LRCLIB response to domain Lyrics model.
     */
    private fun convertToLyrics(lrclibLyrics: LRCLibLyrics): Lyrics {
        return when {
            // Prefer synced lyrics
            !lrclibLyrics.syncedLyrics.isNullOrBlank() -> {
                Lyrics.parseLrc(lrclibLyrics.syncedLyrics)
            }
            // Fall back to plain lyrics
            !lrclibLyrics.plainLyrics.isNullOrBlank() -> {
                Lyrics.createUnsynced(lrclibLyrics.plainLyrics)
            }
            // Instrumental or no lyrics
            else -> {
                Lyrics.createUnsynced("[Instrumental]")
            }
        }
    }

    /**
     * Generates a cache key for lyrics.
     */
    private fun generateCacheKey(trackName: String, artistName: String): String {
        return "${artistName.lowercase()}_${trackName.lowercase()}"
    }
}
