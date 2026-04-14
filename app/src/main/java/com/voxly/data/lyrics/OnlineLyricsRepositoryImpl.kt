package com.voxly.data.lyrics

import com.voxly.data.local.SettingsDataStore
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.LyricsException
import com.voxly.domain.repository.LyricsSourceResult
import com.voxly.domain.repository.OnlineLyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.util.OnlineSearchSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineLyricsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val wangyRepository: WangyRepository,
    private val tengxRepository: TengxRepository
) : OnlineLyricsRepository {

    private val lyricsCache = object : LinkedHashMap<String, Lyrics>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean {
            return size > 50
        }
    }

    enum class LyricsSource {
        NETEASE,
        QQ_MUSIC,
        ALL
    }

    var preferredSource: LyricsSource = LyricsSource.ALL

    override fun searchOnlineLyricsFlow(
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

        supervisorScope {
            if (settings.enableNetease) {
                launch {
                    try {
                        val netEaseSearchResult = searchFromNetEase(normalizedTrackName, normalizedArtistName)
                        applyLimit(netEaseSearchResult.getOrNull().orEmpty(), settings.searchLimit).forEach { lyrics ->
                            trySend(LyricsSourceResult.Result(lyrics, "NetEase"))
                        }
                        if (netEaseSearchResult.isFailure) {
                            trySend(
                                LyricsSourceResult.Error(
                                    "NetEase",
                                    netEaseSearchResult.exceptionOrNull()?.message ?: "Failed"
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
                        val qqMusicSearchResult = searchFromQQMusic(normalizedTrackName, normalizedArtistName)
                        applyLimit(qqMusicSearchResult.getOrNull().orEmpty(), settings.searchLimit).forEach { lyrics ->
                            trySend(LyricsSourceResult.Result(lyrics, "QQ Music"))
                        }
                        if (qqMusicSearchResult.isFailure) {
                            trySend(
                                LyricsSourceResult.Error(
                                    "QQ Music",
                                    qqMusicSearchResult.exceptionOrNull()?.message ?: "Failed"
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
        }

        awaitClose { }
    }

    override suspend fun getOnlineLyrics(result: OnlineLyricsResult): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            val cacheKey = generateLyricsCacheKey(result)

            lyricsCache[cacheKey]?.let { cachedLyrics ->
                Timber.d("Lyrics cache hit: $cacheKey")
                return@withContext Result.success(cachedLyrics)
            }

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

                val finalResult = lyricsResult.map { lyrics ->
                    val formatEnabled = settingsDataStore.lyricsTimestampFormatEnabled.first()
                    if (formatEnabled && lyrics.isSynced) {
                        val formattedLrc = Lyrics.formatTimestamps(lyrics.toLrcFormat())
                        Lyrics.parseLrc(formattedLrc)
                    } else {
                        lyrics
                    }
                }

                finalResult.getOrNull()?.let { lyrics ->
                    lyricsCache[cacheKey] = lyrics
                    Timber.d("Lyrics cached: $cacheKey")
                }

                finalResult
            } catch (e: Exception) {
                Result.failure(LyricsException("Network error", e))
            }
        }

    private suspend fun searchFromNetEase(
        trackName: String,
        artistName: String?
    ): Result<List<OnlineLyricsResult>> {
        Timber.d("NetEase lyrics search starting: trackName=$trackName, artistName=$artistName")

        val searchResult = wangyRepository.searchSongs(
            keywords = if (artistName != null) "$artistName $trackName" else trackName,
            page = 1,
            limit = 5
        )

        return if (searchResult.isSuccess) {
            val response = searchResult.getOrNull()
            val songs = response?.result?.songs ?: emptyList()
            Timber.d("NetEase lyrics search success: found ${songs.size} songs for '$trackName'")

            val results = songs.map { song ->
                val detailResult = wangyRepository.getSongDetail(song.id)
                val firstDetailSong = detailResult.getOrNull()?.songs?.firstOrNull()
                val resolvedArtistName = firstDetailSong?.ar?.firstOrNull()?.name
                    ?: song.artists.firstOrNull()?.name
                    ?: ""
                val resolvedAlbumName = firstDetailSong?.al?.name
                    ?: song.album?.name

                OnlineLyricsResult(
                    id = song.id,
                    trackName = song.name,
                    artistName = resolvedArtistName,
                    albumName = resolvedAlbumName,
                    duration = song.duration.toDouble() / com.voxly.core.util.Constants.MS_PER_SECOND.toDouble(),
                    hasSyncedLyrics = true,
                    hasPlainLyrics = true,
                    isInstrumental = false,
                    source = "NetEase",
                    sourceKey = song.id.toString(),
                    preview = null
                )
            }
            Result.success(results)
        } else {
            val errorMsg = searchResult.exceptionOrNull()?.message ?: "Unknown error"
            Timber.e("NetEase lyrics search failed: $errorMsg")
            Result.failure(LyricsException("NetEase search failed: $errorMsg"))
        }
    }

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
            Timber.d("QQ Music search response: code=${response?.code}, data=${response?.data != null}")

            val songs = response?.data?.song?.list.orEmpty()
            Timber.d("QQ Music lyrics search found ${songs.size} songs for '$keywords'")

            if (songs.isEmpty()) {
                Timber.w("QQ Music lyrics search returned empty results for '$keywords'")
            }

            val results = songs.mapNotNull { song ->
                if (song.id <= 0 || song.name.isBlank()) {
                    Timber.w("QQ Music song invalid: id=${song.id}, name=${song.name}")
                    return@mapNotNull null
                }

                OnlineLyricsResult(
                    id = song.id,
                    trackName = song.name,
                    artistName = song.singer.joinToString(", ") { it.name },
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

    suspend fun getNetEaseLyrics(songId: Long): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
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

    suspend fun getQQMusicLyrics(songMid: String): Result<Lyrics> =
        withContext(Dispatchers.IO) {
            try {
                val response = tengxRepository.getLyrics(songMid)

                if (response.isSuccess) {
                    val lyricsData = response.getOrNull()
                    val lrc = lyricsData?.lyrics ?: ""

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
        return tengxRepository.getSongDetail(listOf(songId))
            .getOrNull()
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

    private fun generateLyricsCacheKey(result: OnlineLyricsResult): String {
        return "${result.source}_${result.sourceKey ?: result.id}"
    }

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
}