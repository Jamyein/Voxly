package com.voxly.data.repository

import android.content.Context
import com.voxly.data.local.metadata.JaudiotaggerMetadataProcessor
import com.voxly.data.remote.lrclib.LRCLibApi
import com.voxly.data.remote.lrclib.LRCLibLyrics
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.LyricsException
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * Handles local lyrics operations and online lyrics fetching from multiple sources:
 * - LRCLIB (primary source for synced lyrics)
 * - NetEase Cloud Music (Chinese music)
 * - QQ Music (Chinese music)
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: JaudiotaggerMetadataProcessor,
    private val lrclibApi: LRCLibApi,
    private val wangyRepository: WangyRepository,
    private val tengxRepository: TengxRepository
) : LyricsRepository {

    // Simple in-memory cache (in production, use Room database)
    private val lyricsCache = mutableMapOf<String, Lyrics>()

    // Data source preference
    enum class LyricsSource {
        LRCLIB,
        NETEASE,
        QQ_MUSIC,
        ALL
    }

    var preferredSource: LyricsSource = LyricsSource.ALL

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
            when (preferredSource) {
                LyricsSource.LRCLIB -> searchFromLRCLIB(trackName, artistName, albumName)
                LyricsSource.NETEASE -> searchFromNetEase(trackName, artistName)
                LyricsSource.QQ_MUSIC -> searchFromQQMusic(trackName, artistName)
                LyricsSource.ALL -> searchFromAllSources(trackName, artistName, albumName)
            }
        } catch (e: Exception) {
            Result.failure(LyricsException("Network error during search", e))
        }
    }

    /**
     * Searches lyrics from LRCLIB.
     */
    private suspend fun searchFromLRCLIB(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Result<List<OnlineLyricsResult>> {
        val response = lrclibApi.searchLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName
        )

        return if (response.isSuccessful) {
            val lrclibResult = response.body()
            if (lrclibResult != null) {
                val result = OnlineLyricsResult(
                    id = lrclibResult.id,
                    trackName = lrclibResult.trackName,
                    artistName = lrclibResult.artistName,
                    albumName = lrclibResult.albumName,
                    duration = lrclibResult.duration,
                    hasSyncedLyrics = !lrclibResult.syncedLyrics.isNullOrBlank(),
                    hasPlainLyrics = !lrclibResult.plainLyrics.isNullOrBlank(),
                    isInstrumental = lrclibResult.instrumental,
                    source = "LRCLIB",
                    preview = lrclibResult.plainLyrics?.lines()?.take(3)?.joinToString("\n")
                        ?: lrclibResult.syncedLyrics?.lines()?.take(3)?.joinToString("\n")
                )
                Result.success(listOf(result))
            } else {
                Result.success(emptyList())
            }
        } else {
            Result.failure(LyricsException("LRCLIB search failed: ${response.errorBody()?.string()}"))
        }
    }

    /**
     * Searches lyrics from NetEase Cloud Music.
     */
    private suspend fun searchFromNetEase(
        trackName: String,
        artistName: String?
    ): Result<List<OnlineLyricsResult>> {
        val searchResult = wangyRepository.searchSongs(
            keywords = if (artistName != null) "$artistName $trackName" else trackName,
            page = 1,
            limit = 5
        )

        return if (searchResult.isSuccess) {
            val response = searchResult.getOrNull()
            val songs = response?.result?.songs ?: emptyList()
            
            val results = songs.map { song ->
                OnlineLyricsResult(
                    id = song.id.toLong(),
                    trackName = song.name,
                    artistName = song.artists.joinToString(", ") { it.name },
                    albumName = song.album?.name,
                    duration = song.duration / 1000.0,
                    hasSyncedLyrics = true, // NetEase usually has synced lyrics
                    hasPlainLyrics = true,
                    isInstrumental = false,
                    source = "NetEase",
                    preview = null
                )
            }
            Result.success(results)
        } else {
            Result.failure(LyricsException("NetEase search failed"))
        }
    }

    /**
     * Searches lyrics from QQ Music.
     */
    private suspend fun searchFromQQMusic(
        trackName: String,
        artistName: String?
    ): Result<List<OnlineLyricsResult>> {
        val searchResult = tengxRepository.searchSongs(
            keywords = trackName,
            pageNum = 1,
            pageSize = 5
        )

        return if (searchResult.isSuccess) {
            val response = searchResult.getOrNull()
            val songs = response?.data?.song?.list ?: emptyList()
            
            val results = songs.map { song ->
                OnlineLyricsResult(
                    id = song.id.toLong(),
                    trackName = song.name,
                    artistName = song.singer.joinToString(", ") { it.name },
                    albumName = song.album?.name,
                    duration = song.interval.toDouble(),
                    hasSyncedLyrics = true,
                    hasPlainLyrics = true,
                    isInstrumental = false,
                    source = "QQ Music",
                    preview = null
                )
            }
            Result.success(results)
        } else {
            Result.failure(LyricsException("QQ Music search failed"))
        }
    }

    /**
     * Searches lyrics from all sources concurrently.
     */
    private suspend fun searchFromAllSources(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Result<List<OnlineLyricsResult>> = coroutineScope {
        val lrclibDeferred = async {
            runCatching { searchFromLRCLIB(trackName, artistName, albumName).getOrNull() }
        }
        val neteaseDeferred = async {
            runCatching { searchFromNetEase(trackName, artistName).getOrNull() }
        }
        val qqMusicDeferred = async {
            runCatching { searchFromQQMusic(trackName, artistName).getOrNull() }
        }

        val lrclibResults = lrclibDeferred.await().getOrNull() ?: emptyList()
        val neteaseResults = neteaseDeferred.await().getOrNull() ?: emptyList()
        val qqMusicResults = qqMusicDeferred.await().getOrNull() ?: emptyList()

        // Merge all results
        val allResults = mutableListOf<OnlineLyricsResult>()
        allResults.addAll(lrclibResults)
        allResults.addAll(neteaseResults)
        allResults.addAll(qqMusicResults)

        // Sort by relevance: prioritize results with synced lyrics and matching artist
        val sortedResults = allResults.sortedWith(compareByDescending<OnlineLyricsResult> {
            if (it.hasSyncedLyrics) 2 else 0
        }.thenByDescending {
            if (artistName != null && 
                (it.artistName?.contains(artistName, ignoreCase = true) == true ||
                 artistName.contains(it.artistName ?: "", ignoreCase = true))
            ) 1 else 0
        })

        Result.success(sortedResults)
    }

    override suspend fun getOnlineLyricsById(id: Long): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                // Determine source based on ID range or other heuristics
                // For now, try LRCLIB first
                val response = lrclibApi.getLyricsById(id)

                if (response.isSuccessful) {
                    val lrclibLyrics = response.body()
                        ?: return@withContext Result.failure(LyricsException("Empty response"))

                    val lyrics = convertLRCLIBToLyrics(lrclibLyrics)
                    Result.success(lyrics)
                } else {
                    Result.failure(LyricsException("Failed to get lyrics: ${response.errorBody()?.string()}"))
                }
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    /**
     * Gets lyrics from NetEase by song ID.
     */
    suspend fun getNetEaseLyrics(songId: Long): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                val response = wangyRepository.getLyrics(songId)

                if (response.isSuccess) {
                    val lyricsData = response.getOrNull()
                    val lrc = lyricsData?.lrc?.lyric ?: ""
                    val tLrc = lyricsData?.tlyric?.lyric

                    if (lrc.isNotBlank()) {
                        val lyrics = if (lrc.contains("[")) {
                            Lyrics.parseLrc(lrc)
                        } else {
                            Lyrics.createUnsynced(lrc)
                        }
                        Result.success(lyrics)
                    } else {
                        Result.failure(LyricsException("No lyrics found"))
                    }
                } else {
                    Result.failure(LyricsException("NetEase get lyrics failed"))
                }
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    /**
     * Gets lyrics from QQ Music by song mid.
     */
    suspend fun getQQMusicLyrics(songMid: String): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                val response = tengxRepository.getLyrics(songMid)

                if (response.isSuccess) {
                    val lyricsData = response.getOrNull()
                    val lrc = lyricsData?.lyrics ?: ""
                    val tLrc = lyricsData?.translatedLyrics

                    if (lrc.isNotBlank()) {
                        val lyrics = if (lrc.contains("[")) {
                            Lyrics.parseLrc(lrc)
                        } else {
                            Lyrics.createUnsynced(lrc)
                        }
                        Result.success(lyrics)
                    } else {
                        Result.failure(LyricsException("No lyrics found"))
                    }
                } else {
                    Result.failure(LyricsException("QQ Music get lyrics failed"))
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
    private fun convertLRCLIBToLyrics(lrclibLyrics: LRCLibLyrics): Lyrics {
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
