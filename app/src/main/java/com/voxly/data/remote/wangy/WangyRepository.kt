package com.voxly.data.remote.wangy

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voxly.data.remote.wangy.model.WangyAlbum
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyArtist
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySearchResult
import com.voxly.data.remote.wangy.model.WangySong
import com.voxly.data.remote.wangy.model.WangySongDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WangyRepository"

// 错误追踪：防止无限循环 (线程安全)
private val consecutiveErrorCount = AtomicInteger(0)
private const val MAX_CONSECUTIVE_ERRORS = 5
private const val ERROR_RESET_TIMEOUT_MS = 30_000L  // 30秒后重置错误计数

/**
 * Repository for WangY Music API operations.
 * Uses simplified web API (no encryption required).
 */
interface WangyRepository {

    /**
     * Searches for songs by keywords.
     *
     * @param keywords Search keywords
     * @param page Page number (1-based)
     * @param limit Maximum number of results per page
     * @return Search response wrapped in Result
     */
    suspend fun searchSongs(
        keywords: String,
        page: Int = 1,
        limit: Int = 30
    ): Result<WangySearchResponse>

    /**
     * Gets detailed information for a song.
     *
     * @param songId Song ID
     * @return Song detail response wrapped in Result
     */
    suspend fun getSongDetail(songId: Long): Result<WangySongDetail>

    /**
     * Gets lyrics for a song.
     *
     * @param songId Song ID
     * @return Lyrics response wrapped in Result
     */
    suspend fun getLyrics(songId: Long): Result<WangyLyricsResponse>

    /**
     * Gets album details.
     *
     * @param albumId Album ID
     * @return Album detail response wrapped in Result
     */
    suspend fun getAlbumDetail(albumId: Long): Result<WangyAlbumDetail>

    /**
     * Gets cover art URL for a song.
     *
     * @param songId Song ID
     * @return Cover art URL wrapped in Result
     */
    suspend fun getCoverArt(songId: Long): Result<String>

    /**
     * Gets enhanced lyrics with YRC support.
     *
     * @param songId Song ID
     * @return Enhanced lyrics response wrapped in Result
     */
    suspend fun getEnhancedLyrics(songId: Long): Result<WangyLyricsResponse>
}

/**
 * Implementation of WangyRepository.
 *
 * @property api WangY API instance
 */
@Singleton
class WangyRepositoryImpl @Inject constructor(
    private val api: WangyApi
) : WangyRepository {

    override suspend fun searchSongs(
        keywords: String,
        page: Int,
        limit: Int
    ): Result<WangySearchResponse> = withContext(Dispatchers.IO) {
        // 电路保护：如果连续错误太多，暂时跳过请求
        if (consecutiveErrorCount.get() >= MAX_CONSECUTIVE_ERRORS) {
            Timber.w(TAG, "Circuit breaker activated: too many consecutive errors (${consecutiveErrorCount.get()}), skipping request")
            return@withContext Result.failure(Exception("Circuit breaker: too many consecutive errors"))
        }

        val normalizedPage = if (page <= 0) 1 else page
        val normalizedLimit = limit.coerceIn(1, 100)
        val offset = (normalizedPage - 1) * normalizedLimit

        Timber.d(TAG, "Searching NetEase for: '$keywords' page=$normalizedPage limit=$normalizedLimit")

        try {
            // Simple web search (no encryption required)
            val response = api.searchSongsSimple(
                keyword = keywords,
                type = 1,
                offset = offset,
                limit = normalizedLimit,
                total = true
            )

            Timber.d(TAG, "[Simple] NetEase API response: httpCode=${response.code()}")

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.w(TAG, "NetEase API failed with code ${response.code()}, error body: ${errorBody?.take(500)}")
                return@withContext Result.failure(Exception("NetEase API failed: ${response.code()}"))
            }

            val body = response.body()
            if (body == null) {
                Timber.w(TAG, "NetEase API returned null body")
                return@withContext Result.failure(Exception("NetEase API returned null body"))
            }

            // Read response as string and parse
            val responseString = try {
                body.string()
            } catch (e: Exception) {
                Timber.e(TAG, "[Simple] Failed to read response body: ${e.message}")
                return@withContext Result.failure(Exception("Failed to read response body: ${e.message}"))
            }

            if (responseString.isBlank()) {
                Timber.w(TAG, "NetEase API returned empty response")
                return@withContext Result.failure(Exception("NetEase API returned empty response"))
            }

            // Parse response string to JsonElement
            val jsonElement = try {
                JsonParser.parseString(responseString)
            } catch (e: Exception) {
                Timber.w(TAG, "NetEase API returned invalid JSON: $responseString")
                return@withContext Result.failure(Exception("Invalid JSON response"))
            }

            // Check if response is actually a JsonObject
            if (!jsonElement.isJsonObject) {
                Timber.w(TAG, "NetEase API returned non-object JSON: $responseString")
                return@withContext Result.failure(Exception("Invalid response format"))
            }

            // Parse JsonObject to WangySearchResponse
            val jsonObject = jsonElement.asJsonObject
            val parsedResponse = parseSearchResponse(jsonObject)
            val code = parsedResponse.code

            if (code == 200 && !parsedResponse.result?.songs.isNullOrEmpty()) {
                Timber.d(TAG, "NetEase found ${parsedResponse.result.songs.size} songs for '$keywords'")
                // 成功：重置错误计数
                consecutiveErrorCount.set(0)
                return@withContext Result.success(parsedResponse)
            }

            Timber.w(TAG, "NetEase API returned empty result for '$keywords'")
            // 成功：重置错误计数
            consecutiveErrorCount.set(0)
            Result.success(parsedResponse)
        } catch (e: Exception) {
            // 失败：增加错误计数
            consecutiveErrorCount.incrementAndGet()
            Timber.e(TAG, "Exception during API call: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse JsonObject response to WangySearchResponse.
     * Handles simple web API response format.
     */
    private fun parseSearchResponse(jsonObject: JsonObject): WangySearchResponse {
        val code = jsonObject.get("code")?.asInt ?: -1

        // Parse result (simple web API format)
        val result = jsonObject.get("result")?.asJsonObject?.let { resultJson ->
            Timber.d(TAG, "parseSearchResponse: parsing Web API format, result keys=${resultJson.keySet()}")
            parseSearchResult(resultJson)
        }

        // Debug: log parsed result
        result?.songs?.firstOrNull()?.let { firstSong ->
            Timber.d(TAG, "parseSearchResponse: firstSong parsed - id=${firstSong.id}, name=${firstSong.name}, " +
                "artists=${firstSong.artists.map { it.name }}, album=${firstSong.album?.name}, albumPic=${firstSong.album?.picUrl}")
        }

        return WangySearchResponse(
            code = code,
            result = result
        )
    }

    /**
     * Parse search result from web API format.
     */
    private fun parseSearchResult(resultJson: JsonObject): WangySearchResult {
        val songs = resultJson.get("songs")?.asJsonArray?.let { songsArray ->
            songsArray.mapNotNull { songElement ->
                parseSong(songElement.asJsonObject)
            }
        } ?: emptyList()
        
        val albums = resultJson.get("albums")?.asJsonArray?.let { albumsArray ->
            albumsArray.mapNotNull { albumElement ->
                parseAlbum(albumElement.asJsonObject)
            }
        } ?: emptyList()
        
        val artists = resultJson.get("artists")?.asJsonArray?.let { artistsArray ->
            artistsArray.mapNotNull { artistElement ->
                parseArtist(artistElement.asJsonObject)
            }
        } ?: emptyList()
        
        return WangySearchResult(
            hasMore = resultJson.get("hasMore")?.asBoolean ?: false,
            queryCorrected = resultJson.get("queryCorrected")?.asJsonArray?.map { it.asString } ?: emptyList(),
            songs = songs,
            albums = albums,
            artists = artists,
            songCount = resultJson.get("songCount")?.asInt ?: 0,
            albumCount = resultJson.get("albumCount")?.asInt ?: 0,
            artistCount = resultJson.get("artistCount")?.asInt ?: 0
        )
    }

    /**
     * Parse song from web API format.
     */
    private fun parseSong(songJson: JsonObject): WangySong {
        // First try "artists", fallback to "ar" for compatibility
        val artists = songJson.get("artists")?.asJsonArray?.let { arArray ->
            arArray.mapNotNull { artistElement ->
                parseArtist(artistElement.asJsonObject)
            }
        } ?: songJson.get("ar")?.asJsonArray?.let { arArray ->
            arArray.mapNotNull { artistElement ->
                parseArtist(artistElement.asJsonObject)
            }
        } ?: emptyList()
        
        val album = songJson.get("album")?.asJsonObject?.let { parseAlbum(it) } ?: songJson.get("al")?.asJsonObject?.let { parseAlbum(it) }
        
        return WangySong(
            id = songJson.get("id")?.asLong ?: 0L,
            name = songJson.get("name")?.asString ?: "",
            artists = artists,
            album = album,
            duration = songJson.get("dt")?.asLong ?: 0L,
            copyrightId = songJson.get("copyrightId")?.asLong ?: 0L,
            fee = songJson.get("fee")?.asInt ?: 0,
            trackNumber = songJson.get("no")?.asInt ?: 0,
            version = songJson.get("version")?.asInt ?: 0
        )
    }

    /**
     * Parse album from JSON.
     */
    private fun parseAlbum(albumJson: JsonObject): WangyAlbum {
        return WangyAlbum(
            id = albumJson.get("id")?.asLong ?: 0L,
            name = albumJson.get("name")?.asString ?: "",
            artist = albumJson.get("artist")?.asJsonObject?.let { parseArtist(it) },
            publishDate = albumJson.get("publishTime")?.asString ?: "",
            songsCount = albumJson.get("size")?.asInt ?: 0,
            picUrl = albumJson.get("picUrl")?.asString ?: ""
        )
    }

    /**
     * Parse artist from JSON.
     */
    private fun parseArtist(artistJson: JsonObject): WangyArtist {
        return WangyArtist(
            id = artistJson.get("id")?.asLong ?: 0L,
            name = artistJson.get("name")?.asString ?: "",
            picUrl = artistJson.get("picUrl")?.asString ?: "",
            albumSize = artistJson.get("albumSize")?.asInt ?: 0,
            fans = artistJson.get("fans")?.asLong ?: 0L
        )
    }

    override suspend fun getSongDetail(songId: Long): Result<WangySongDetail> = withContext(Dispatchers.IO) {
        try {
            // Make API call directly with parameters (no encryption needed)
            val response = api.getSongDetail(
                songIds = "[$songId]"
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Get song detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(songId: Long): Result<WangyLyricsResponse> = withContext(Dispatchers.IO) {
        try {
            // Make API call directly with parameters (no encryption needed)
            val response = api.getLyrics(
                songId = songId
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Get lyrics failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumDetail(albumId: Long): Result<WangyAlbumDetail> = withContext(Dispatchers.IO) {
        try {
            // Make API call directly with parameters (no encryption needed)
            val response = api.getAlbumDetail(
                albumId = albumId
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Get album detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoverArt(songId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSongDetail(
                songIds = "[$songId]"
            )

            if (response.isSuccessful && response.body() != null) {
                val song = response.body()!!.songs.firstOrNull()
                val coverUrl = song?.album?.picUrl ?: song?.al?.picUrl
                if (coverUrl != null) {
                    Result.success("$coverUrl?param=500y500")
                } else {
                    Result.failure(Exception("No cover art found"))
                }
            } else {
                Result.failure(Exception("Failed to get song detail: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEnhancedLyrics(songId: Long): Result<WangyLyricsResponse> = withContext(Dispatchers.IO) {
        // 使用简单API获取歌词（不再支持EAPI增强歌词）
        try {
            val response = api.getLyrics(
                songId = songId,
                os = "pc",
                lv = -1,
                tv = -1
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Get lyrics failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
