package com.voxly.data.repository

import android.content.Context
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LyricsRepository.
 * Handles local lyrics operations and online lyrics fetching from multiple sources:
 * - NetEase Cloud Music (Chinese music)
 * - QQ Music (Chinese music)
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val settingsDataStore: SettingsDataStore,

    private val wangyRepository: WangyRepository,
    private val tengxRepository: TengxRepository
) : LyricsRepository {

    sealed class LyricsSourceResult {
        data class Result(
            val lyrics: OnlineLyricsResult,
            val source: String
        ) : LyricsSourceResult()

        data class SourceCompleted(val source: String) : LyricsSourceResult()

        data class Error(val source: String, val message: String) : LyricsSourceResult()
    }

    // LRU cache for lyrics content (50 entries, session-level)
    private val lyricsCache = object : LinkedHashMap<String, Lyrics>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean {
            return size > 50
        }
    }

    // Data source preference
    enum class LyricsSource {
        NETEASE,
        QQ_MUSIC,
        ALL
    }

    var preferredSource: LyricsSource = LyricsSource.ALL

    override suspend fun readLyrics(filePath: String): Result<Lyrics?> =
        withContext(Dispatchers.IO) {
            try {
                // Normalize path before checking - handle common path issues
                val normalizedPath = filePath
                    .replace(Regex("//+"), "/")
                    .trimEnd('/')
                
                val file = File(normalizedPath)
                if (!file.exists()) {
                    // Try the original path as well - metadata processor will try path resolution
                    val originalFile = File(filePath)
                    if (!originalFile.exists()) {
                        return@withContext Result.failure(
                            LyricsException("File not found: $filePath. The file may have been moved or deleted.")
                        )
                    }
                }

                // Use TagLibMetadataProcessor to read lyrics - it handles path resolution internally
                val metadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)

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
                // Normalize path before checking - handle common path issues
                val normalizedPath = filePath
                    .replace(Regex("//+"), "/")
                    .trimEnd('/')
                
                val file = File(normalizedPath)
                if (!file.exists()) {
                    // Try the original path - metadata processor will try path resolution
                    val originalFile = File(filePath)
                    if (!originalFile.exists()) {
                        return@withContext Result.failure(
                            LyricsException("File not accessible: $filePath. The file may have been moved or deleted.")
                        )
                    }
                }

                // Save lyrics as USLT (Unsynchronized Lyrics)
                // If synced, save in LRC format; otherwise save as plain text
                val lyricsText = if (lyrics.isSynced) {
                    lyrics.toLrcFormat()
                } else {
                    lyrics.text
                }

                // Read existing metadata and update with lyrics
                val existingMetadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = lyricsText)
                    ?: com.voxly.domain.model.AudioMetadata(
                        title = null,
                        artist = null,
                        album = null,
                        lyrics = lyricsText
                    )

                val result = metadataProcessor.updateMetadata(normalizedPath, updatedMetadata)
                    ?: metadataProcessor.updateMetadata(filePath, updatedMetadata)
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
                // Normalize path before checking - handle common path issues
                val normalizedPath = filePath
                    .replace(Regex("//+"), "/")
                    .trimEnd('/')
                
                val file = File(normalizedPath)
                if (!file.exists()) {
                    // Try the original path - metadata processor will try path resolution
                    val originalFile = File(filePath)
                    if (!originalFile.exists()) {
                        return@withContext Result.failure(
                            LyricsException("File not accessible: $filePath. The file may have been moved or deleted.")
                        )
                    }
                }

                // Use metadataProcessor to remove lyrics field by setting it to empty
                val existingMetadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = "")
                    ?: return@withContext Result.failure(LyricsException("Cannot read file metadata"))

                val result = metadataProcessor.updateMetadata(normalizedPath, updatedMetadata)
                    ?: metadataProcessor.updateMetadata(filePath, updatedMetadata)
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
                LyricsSource.NETEASE -> {
                    if (settings.enableNetease) {
                        searchFromNetEase(normalizedTrackName, normalizedArtistName)
                            .map { applyLimit(it, settings.searchLimit) }
                            // 失败时返回空列表而不是错误
                            .getOrElse { emptyList() }
                            .let { Result.success(it) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                LyricsSource.QQ_MUSIC -> {
                    if (settings.enableQQMusic) {
                        searchFromQQMusic(normalizedTrackName, normalizedArtistName)
                            .map { applyLimit(it, settings.searchLimit) }
                            // 失败时返回空列表而不是错误
                            .getOrElse { emptyList() }
                            .let { Result.success(it) }
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

    fun searchOnlineLyricsFlow(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Flow<LyricsSourceResult> = callbackFlow {
        val normalizedTrackName = trackName.trim()
        val normalizedArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAlbumName = albumName?.trim()?.takeIf { it.isNotEmpty() }

        if (normalizedTrackName.isBlank()) {
            trySend(LyricsSourceResult.Error("INPUT", "Track name is required"))
            close()
            return@callbackFlow
        }

        val settings = try {
            getLyricsSourceSettings()
        } catch (e: Exception) {
            trySend(LyricsSourceResult.Error("SETTINGS", e.message ?: "Failed to load settings"))
            close()
            return@callbackFlow
        }

        if (!settings.hasAnyEnabledSource) {
            trySend(LyricsSourceResult.Error("SETTINGS", "No lyrics sources enabled"))
            close()
            return@callbackFlow
        }

        val enabledSourceCount = listOf(
            settings.enableNetease,
            settings.enableQQMusic
        ).count { it }
        val completedSources = AtomicInteger(0)

        fun markSourceCompleted(source: String) {
            trySend(LyricsSourceResult.SourceCompleted(source))
            if (completedSources.incrementAndGet() >= enabledSourceCount) {
                close()
            }
        }

        if (settings.enableNetease) {
            launch {
                try {
                    val result = searchFromNetEase(normalizedTrackName, normalizedArtistName)
                    applyLimit(result.getOrNull().orEmpty(), settings.searchLimit).forEach { lyrics ->
                        trySend(LyricsSourceResult.Result(lyrics, "NetEase"))
                    }
                    if (result.isFailure) {
                        trySend(
                            LyricsSourceResult.Error(
                                "NetEase",
                                result.exceptionOrNull()?.message ?: "Failed"
                            )
                        )
                    }
                } catch (e: Exception) {
                    trySend(LyricsSourceResult.Error("NetEase", e.message ?: "Failed"))
                } finally {
                    markSourceCompleted("NetEase")
                }
            }
        }

        if (settings.enableQQMusic) {
            launch {
                try {
                    val result = searchFromQQMusic(normalizedTrackName, normalizedArtistName)
                    applyLimit(result.getOrNull().orEmpty(), settings.searchLimit).forEach { lyrics ->
                        trySend(LyricsSourceResult.Result(lyrics, "QQ Music"))
                    }
                    if (result.isFailure) {
                        trySend(
                            LyricsSourceResult.Error(
                                "QQ Music",
                                result.exceptionOrNull()?.message ?: "Failed"
                            )
                        )
                    }
                } catch (e: Exception) {
                    trySend(LyricsSourceResult.Error("QQ Music", e.message ?: "Failed"))
                } finally {
                    markSourceCompleted("QQ Music")
                }
            }
        }

        awaitClose { }
    }

    /**
     * Searches lyrics from NetEase Cloud Music.
     * Uses Simple API (WangyRepository) - no encryption required.
     */
    private suspend fun searchFromNetEase(
        trackName: String,
        artistName: String?
    ): Result<List<OnlineLyricsResult>> {
        Timber.d("NetEase lyrics search starting: trackName=$trackName, artistName=$artistName")

        // Use wangyRepository (Simple API)
        val searchResult = wangyRepository.searchSongs(
            keywords = if (artistName != null) "$artistName $trackName" else trackName,
            page = 1,
            limit = 5
        )

        return if (searchResult.isSuccess) {
            val response = searchResult.getOrNull()
            val songs = response?.result?.songs ?: emptyList()
            Timber.d("NetEase lyrics search success: found ${songs.size} songs for '$trackName'")

            // 并发获取每首歌的详情（包括完整歌手和专辑信息）
            val results = songs.map { song ->
                coroutineScope {
                    // 并发获取歌曲详情
                    val detailJob = async { wangyRepository.getSongDetail(song.id) }
                    val detailResult = detailJob.await()
                    
                    // 从详情中获取完整的歌手和专辑信息，如果详情失败则使用搜索结果
                    val songDetail = detailResult.getOrNull()
                    val detailArtists = songDetail?.songs?.firstOrNull()?.ar
                    val detailAlbum = songDetail?.songs?.firstOrNull()?.al
                    val artistName = detailArtists?.firstOrNull()?.name
                        ?: song.artists.firstOrNull()?.name
                        ?: ""
                    val albumName = detailAlbum?.name
                        ?: song.album?.name
                    
                    OnlineLyricsResult(
                        id = song.id,
                        trackName = song.name,
                        artistName = artistName,
                        albumName = albumName,
                        duration = song.duration.toDouble() / 1000.0,
                        hasSyncedLyrics = true,
                        hasPlainLyrics = true,
                        isInstrumental = false,
                        source = "NetEase",
                        sourceKey = song.id.toString(),
                        preview = null
                    )
                }
            }
            Result.success(results)
        } else {
            val errorMsg = searchResult.exceptionOrNull()?.message ?: "Unknown error"
            Timber.e("NetEase lyrics search failed: $errorMsg")
            Result.failure(LyricsException("NetEase search failed: $errorMsg"))
        }
    }

    /**
     * Searches lyrics from QQ Music.
     */
    private suspend fun searchFromQQMusic(
        trackName: String,
        artistName: String?
    ): Result<List<OnlineLyricsResult>> {
        val keywords = if (artistName.isNullOrBlank()) trackName else "$artistName $trackName"
        Timber.d("QQ Music lyrics search starting: keywords='$keywords'")
        
        val searchResult = tengxRepository.searchSongs(
            keywords = keywords,
            pageNum = 1,
            pageSize = 5
        )

        return if (searchResult.isSuccess) {
            val response = searchResult.getOrNull()
            Timber.d("QQ Music search response: code=${response?.code}, data=${response?.data != null}, song=${response?.data?.song != null}")
            
            val songs = response?.data?.song?.list ?: emptyList()
            Timber.d("QQ Music lyrics search found ${songs.size} songs for '$keywords'")
            
            if (songs.isEmpty()) {
                Timber.w("QQ Music lyrics search returned empty results for '$keywords'")
            }
            
            val results = songs.mapNotNull { song ->
                // Validate required fields
                if (song.id <= 0 || song.name.isBlank()) {
                    Timber.w("QQ Music song invalid: id=${song.id}, name=${song.name}")
                    return@mapNotNull null
                }
                
                OnlineLyricsResult(
                    id = song.id,
                    trackName = song.name,
                    artistName = song.singer.joinToString(", ") { it.name }.ifBlank { "" },
                    albumName = song.album?.name,
                    duration = song.interval.toDouble(),
                    hasSyncedLyrics = true,
                    hasPlainLyrics = true,
                    isInstrumental = false,
                    source = "QQ Music",
                    sourceKey = song.mid.takeIf { it.isNotBlank() } ?: song.id.toString(),
                    preview = null
                )
            }
            
            Timber.d("QQ Music mapped ${results.size} valid results")
            Result.success(results)
        } else {
            val errorMsg = searchResult.exceptionOrNull()?.message ?: "Unknown error"
            Timber.e("QQ Music lyrics search failed: $errorMsg")
            Result.failure(LyricsException("QQ Music search failed: $errorMsg"))
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
        val neteaseDeferred = if (settings.enableNetease) {
            async { runCatching { searchFromNetEase(trackName, artistName).getOrNull() } }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { runCatching { searchFromQQMusic(trackName, artistName).getOrNull() } }
        } else null

        val neteaseResults = applyLimit(neteaseDeferred?.await()?.getOrNull() ?: emptyList(), settings.searchLimit)
        val qqMusicResults = applyLimit(qqMusicDeferred?.await()?.getOrNull() ?: emptyList(), settings.searchLimit)

        // Merge all results
        val allResults = mutableListOf<OnlineLyricsResult>()
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

        Result.success(sortedResults)
    }

    override suspend fun getOnlineLyrics(result: OnlineLyricsResult): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            // Generate cache key based on source and sourceKey/id
            val cacheKey = generateLyricsCacheKey(result)

            // Check cache first
            lyricsCache[cacheKey]?.let { cachedLyrics ->
                Timber.d("Lyrics cache hit: $cacheKey")
                return@withContext Result.success(cachedLyrics)
            }

            // Cache miss - fetch from network
            try {
                val lyricsResult = when (result.source) {
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

                // Cache the result if successful
                lyricsResult.getOrNull()?.let { lyrics ->
                    lyricsCache[cacheKey] = lyrics
                    Timber.d("Lyrics cached: $cacheKey")
                }

                lyricsResult
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    /**
     * Gets lyrics from NetEase by song ID.
     * Uses Simple API (WangyRepository).
     */
    suspend fun getNetEaseLyrics(songId: Long): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                // Use wangyRepository (Simple API)
                val response = wangyRepository.getLyrics(songId)

                if (response.isSuccess) {
                    val lyricsResponse = response.getOrNull()
                    val lrc = lyricsResponse?.lrc?.lyric
                    val tLrc = lyricsResponse?.tlyric?.lyric

                    if (!lrc.isNullOrBlank()) {
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
                    val errorMsg = response.exceptionOrNull()?.message ?: "Unknown error"
                    Result.failure(LyricsException("NetEase get lyrics failed: $errorMsg"))
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
     * Generates a cache key for lyrics content based on source and ID.
     */
    private fun generateLyricsCacheKey(result: OnlineLyricsResult): String {
        return "${result.source}_${result.sourceKey ?: result.id}"
    }

    /**
     * Generates a cache key for lyrics (legacy method for metadata-based caching).
     */
    private fun generateCacheKey(trackName: String, artistName: String): String {
        return "${artistName.lowercase()}_${trackName.lowercase()}"
    }

    private suspend fun getLyricsSourceSettings(): LyricsSourceSettings {
        return LyricsSourceSettings(
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
        val enableNetease: Boolean,
        val enableQQMusic: Boolean,
        val searchLimit: Int,
        val priority: List<String>
    ) {
        val hasAnyEnabledSource: Boolean
            get() = enableNetease || enableQQMusic
    }

    private fun sourcePriorityIndex(source: String, priority: List<String>): Int {
        val key = when (source) {
            "NetEase" -> "netease"
            "QQ Music" -> "qq_music"
            else -> "unknown"
        }
        val idx = priority.indexOf(key)
        return if (idx >= 0) idx else Int.MAX_VALUE
    }
}
