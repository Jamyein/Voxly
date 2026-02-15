package com.voxly.data.repository

import android.content.Context
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.remote.lrclib.LRCLibApi
import com.voxly.data.remote.lrclib.LRCLibLyrics
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.LyricsException
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    private val metadataProcessor: TagLibMetadataProcessor,
    private val settingsDataStore: SettingsDataStore,
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

                // Use TagLibMetadataProcessor to read lyrics
                val metadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)

                // Try to read lyrics from LYRICS field
                val lyricsText = metadata?.lyrics

                if (lyricsText.isNullOrBlank()) {
                    // Try to read from comment field
                    val comment = metadata?.comment
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

                // Save lyrics as USLT (Unsynchronized Lyrics)
                // If synced, save in LRC format; otherwise save as plain text
                val lyricsText = if (lyrics.isSynced) {
                    lyrics.toLrcFormat()
                } else {
                    lyrics.text
                }

                // Read existing metadata and update with lyrics
                val existingMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = lyricsText)
                    ?: com.voxly.domain.model.AudioMetadata(
                        title = null,
                        artist = null,
                        album = null,
                        lyrics = lyricsText
                    )

                val result = metadataProcessor.updateMetadata(filePath, updatedMetadata)
                if (result.isFailure) {
                    return@withContext Result.failure(LyricsException("Failed to save lyrics"))
                }

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

                // Use metadataProcessor to remove lyrics field by setting it to empty
                val existingMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = "")
                    ?: return@withContext Result.failure(LyricsException("Cannot read file metadata"))

                val result = metadataProcessor.updateMetadata(filePath, updatedMetadata)
                if (result.isFailure) {
                    return@withContext Result.failure(LyricsException("Failed to remove lyrics"))
                }

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
            val normalizedTrackName = trackName.trim()
            val normalizedArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedAlbumName = albumName?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedTrackName.isBlank()) {
                return@withContext Result.failure(LyricsException("Track name is required"))
            }

            val settings = getLyricsSourceSettings()
            if (!settings.hasAnyEnabledSource) {
                return@withContext Result.failure(LyricsException("No lyrics sources enabled"))
            }

            when (preferredSource) {
                LyricsSource.LRCLIB -> {
                    if (settings.enableLrclib) {
                        searchFromLRCLIB(normalizedTrackName, normalizedArtistName, normalizedAlbumName)
                            .map { applyLimit(it, settings.searchLimit) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                LyricsSource.NETEASE -> {
                    if (settings.enableNetease) {
                        searchFromNetEase(normalizedTrackName, normalizedArtistName)
                            .map { applyLimit(it, settings.searchLimit) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                LyricsSource.QQ_MUSIC -> {
                    if (settings.enableQQMusic) {
                        searchFromQQMusic(normalizedTrackName, normalizedArtistName)
                            .map { applyLimit(it, settings.searchLimit) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                LyricsSource.ALL -> searchFromAllSources(
                    trackName = normalizedTrackName,
                    artistName = normalizedArtistName,
                    albumName = normalizedAlbumName,
                    settings = settings
                )
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
            val lrclibResults = response.body().orEmpty()
            val results = lrclibResults.map { entry ->
                OnlineLyricsResult(
                    id = entry.id,
                    trackName = entry.trackName,
                    artistName = entry.artistName,
                    albumName = entry.albumName,
                    duration = entry.duration,
                    hasSyncedLyrics = !entry.syncedLyrics.isNullOrBlank(),
                    hasPlainLyrics = !entry.plainLyrics.isNullOrBlank(),
                    isInstrumental = entry.instrumental,
                    source = "LRCLIB",
                    sourceKey = null,
                    preview = entry.plainLyrics?.lines()?.take(3)?.joinToString("\n")
                        ?: entry.syncedLyrics?.lines()?.take(3)?.joinToString("\n")
                )
            }
            Result.success(results)
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
                    sourceKey = null,
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
            keywords = if (artistName.isNullOrBlank()) trackName else "$artistName $trackName",
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
                    sourceKey = song.mid.takeIf { it.isNotBlank() },
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
        albumName: String?,
        settings: LyricsSourceSettings
    ): Result<List<OnlineLyricsResult>> = coroutineScope {
        val lrclibDeferred = if (settings.enableLrclib) {
            async { runCatching { searchFromLRCLIB(trackName, artistName, albumName).getOrNull() } }
        } else null
        val neteaseDeferred = if (settings.enableNetease) {
            async { runCatching { searchFromNetEase(trackName, artistName).getOrNull() } }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { runCatching { searchFromQQMusic(trackName, artistName).getOrNull() } }
        } else null

        val lrclibResults = lrclibDeferred?.await()?.getOrNull() ?: emptyList()
        val neteaseResults = neteaseDeferred?.await()?.getOrNull() ?: emptyList()
        val qqMusicResults = qqMusicDeferred?.await()?.getOrNull() ?: emptyList()

        // Merge all results
        val allResults = mutableListOf<OnlineLyricsResult>()
        allResults.addAll(lrclibResults)
        allResults.addAll(neteaseResults)
        allResults.addAll(qqMusicResults)

        // Sort by relevance: prioritize results with synced lyrics and matching artist
        val sortedResults = allResults.sortedWith(compareBy<OnlineLyricsResult> {
            sourcePriorityIndex(it.source, settings.priority)
        }.thenByDescending {
            if (it.hasSyncedLyrics) 2 else 0
        }.thenByDescending {
            if (artistName != null && 
                (it.artistName?.contains(artistName, ignoreCase = true) == true ||
                 artistName.contains(it.artistName ?: "", ignoreCase = true))
            ) 1 else 0
        })

        Result.success(applyLimit(sortedResults, settings.searchLimit))
    }

    override suspend fun getOnlineLyrics(result: OnlineLyricsResult): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                when (result.source) {
                    "LRCLIB" -> getLRCLibLyricsById(result.id)
                    "NetEase" -> getNetEaseLyrics(result.id)
                    "QQ Music" -> {
                        val songMid = result.sourceKey?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: resolveQQSongMid(result.id)
                        if (songMid == null) {
                            Result.failure(LyricsException("QQ Music songMid is missing"))
                        } else {
                            getQQMusicLyrics(songMid)
                        }
                    }
                    else -> Result.failure(LyricsException("Unsupported lyrics source: ${result.source}"))
                }
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    private suspend fun getLRCLibLyricsById(id: Long): Result<Lyrics> {
        val response = lrclibApi.getLyricsById(id)
        return if (response.isSuccessful) {
            val lrclibLyrics = response.body()
                ?: return Result.failure(LyricsException("Empty response"))
            Result.success(convertLRCLIBToLyrics(lrclibLyrics))
        } else {
            Result.failure(LyricsException("Failed to get lyrics: ${response.errorBody()?.string()}"))
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

    private suspend fun resolveQQSongMid(songId: Long): String? {
        val detailResult = tengxRepository.getSongDetail(listOf(songId))
        if (!detailResult.isSuccess) return null
        return detailResult.getOrNull()
            ?.data
            ?.track
            ?.firstOrNull()
            ?.mid
            ?.takeIf { it.isNotBlank() }
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

    private suspend fun getLyricsSourceSettings(): LyricsSourceSettings {
        val enableMusicBrainz = settingsDataStore.lyricsSourceEnabledMusicBrainz.first()
        val enableITunes = settingsDataStore.lyricsSourceEnabledITunes.first()
        return LyricsSourceSettings(
            enableLrclib = enableMusicBrainz || enableITunes,
            enableNetease = settingsDataStore.lyricsSourceEnabledNetease.first(),
            enableQQMusic = settingsDataStore.lyricsSourceEnabledQQMusic.first(),
            searchLimit = normalizeSearchLimit(settingsDataStore.onlineSearchLimit.first()),
            priority = settingsDataStore.lyricsSourcePriority.first()
        )
    }

    private fun normalizeSearchLimit(limit: Int): Int {
        return if (limit <= 0) 0 else limit.coerceIn(5, 200)
    }

    private fun <T> applyLimit(list: List<T>, limit: Int): List<T> {
        return if (limit <= 0) list else list.take(limit)
    }

    private data class LyricsSourceSettings(
        val enableLrclib: Boolean,
        val enableNetease: Boolean,
        val enableQQMusic: Boolean,
        val searchLimit: Int,
        val priority: List<String>
    ) {
        val hasAnyEnabledSource: Boolean
            get() = enableLrclib || enableNetease || enableQQMusic
    }

    private fun sourcePriorityIndex(source: String, priority: List<String>): Int {
        val key = when (source) {
            "LRCLIB" -> "musicbrainz"
            "NetEase" -> "netease"
            "QQ Music" -> "qq_music"
            else -> "unknown"
        }
        val idx = priority.indexOf(key)
        return if (idx >= 0) idx else Int.MAX_VALUE
    }
}
