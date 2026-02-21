package com.voxly.data.remote.wangy

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voxly.data.remote.wangy.crypto.WangyCrypto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WangyRepository"

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

    private val gson = Gson()

    override suspend fun searchSongs(
        keywords: String,
        page: Int,
        limit: Int
    ): Result<WangySearchResponse> = withContext(Dispatchers.IO) {
        val normalizedPage = if (page <= 0) 1 else page
        val normalizedLimit = limit.coerceIn(1, 100)
        val offset = (normalizedPage - 1) * normalizedLimit

        Timber.d(TAG, "Searching NetEase for: '$keywords' page=$normalizedPage limit=$normalizedLimit")

        val failures = mutableListOf<String>()
        var emptySuccess: WangySearchResponse? = null

        // 搜索优先级: EAPI > LinuxAPI > Web > Simple
        // EAPI 加密搜索最稳定，优先尝试
        val requests: List<suspend () -> retrofit2.Response<okhttp3.ResponseBody>> = listOf(
            {
                // EAPI 加密搜索 - 参考 any-listen-extension
                // 端点: /api/search/song/list/page，发送到 /eapi/batch
                val searchData = mapOf(
                    "keyword" to keywords,
                    "needCorrect" to "1",
                    "channel" to "typing",
                    "offset" to offset,
                    "scene" to "normal",
                    "total" to (normalizedPage == 1),
                    "limit" to normalizedLimit
                )
                val encrypted = WangyCrypto.eapiEncrypt("/api/search/song/list/page", searchData)
                val bodyString = "params=${encrypted["params"]}"
                val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                api.searchSongsEapi(requestBody)
            },
            {
                // LinuxAPI 加密搜索 (仅需 AES 加密，最简单稳定)
                // 参考: music-tag-web applications/utils/encrypt.py
                val searchData = mapOf(
                    "s" to keywords,
                    "type" to 1,
                    "offset" to offset,
                    "limit" to normalizedLimit,
                    "total" to true
                )
                // LinuxAPI 加密只加密数据，不包含 URL
                val encrypted = WangyCrypto.linuxEncryptSimple(Gson().toJson(searchData))
                val bodyString = "eparams=${encrypted["eparams"]}"
                val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                api.searchSongsLinuxApi(requestBody)
            },
            {
                // 简单网页搜索 (无需加密) - Web接口
                api.searchSongsWeb(
                    keyword = keywords,
                    offset = offset,
                    limit = normalizedLimit
                )
            },
            {
                // 简单网页搜索 (无需加密) - Simple接口
                api.searchSongsSimple(
                    keyword = keywords,
                    offset = offset,
                    limit = normalizedLimit
                )
            }
        )

        for ((index, request) in requests.withIndex()) {
            val apiName = when (index) {
                0 -> "EAPI"
                1 -> "LinuxAPI"
                2 -> "Web"
                3 -> "Simple"
                else -> "Unknown"
            }
            try {
                val response = request()

                Timber.d(TAG, "[$apiName] NetEase API response: httpCode=${response.code()}")
                Timber.d(TAG, "[$apiName] Response headers: ${response.headers()}")

                if (!response.isSuccessful) {
                    // Try to read error body for debugging
                    val errorBody = response.errorBody()?.string()
                    Timber.w(TAG, "NetEase API failed with code ${response.code()}, error body: ${errorBody?.take(500)}")
                    failures.add("error_${response.code()}")
                    continue
                }

                val body = response.body()
                if (body == null) {
                    Timber.w(TAG, "NetEase API returned null body")
                    failures.add("null_body")
                    continue
                }

                Timber.d(TAG, "[$apiName] Response body size: ${body.contentLength()}")

                // Read response as string and parse manually
                val responseString = try {
                    body.string()
                } catch (e: Exception) {
                    Timber.e(TAG, "[$apiName] Failed to read response body: ${e.message}")
                    failures.add("read_error")
                    continue
                }
                Timber.d(TAG, "[$apiName] Response body (first 1000 chars): $responseString")

                if (responseString.isBlank()) {
                    Timber.w(TAG, "NetEase API returned empty response")
                    failures.add("empty_response")
                    continue
                }

                // Parse response string to JsonElement
                val jsonElement = try {
                    JsonParser.parseString(responseString)
                } catch (e: Exception) {
                    Timber.w(TAG, "NetEase API returned invalid JSON: $responseString")
                    failures.add("invalid_json")
                    continue
                }

                // Check if response is actually a JsonObject (not JsonPrimitive like "ok")
                if (!jsonElement.isJsonObject) {
                    Timber.w(TAG, "NetEase API returned non-object JSON: $responseString")
                    failures.add("not_json_object")
                    continue
                }

                // Parse JsonObject to WangySearchResponse
                val jsonObject = jsonElement.asJsonObject
                val parsedResponse = parseSearchResponse(jsonObject)
                val code = parsedResponse.code

                // Debug: Log first song details
                parsedResponse.result?.songs?.firstOrNull()?.let { firstSong ->
                    Timber.d(TAG, "[$apiName] First song: id=${firstSong.id}, name=${firstSong.name}, " +
                        "artists=${firstSong.artists.map { it.name }}, album=${firstSong.album?.name}, " +
                        "albumPicUrl=${firstSong.album?.picUrl}")
                }

                // Debug: Log raw JSON first song
                parsedResponse.raw?.get("result")?.asJsonObject?.get("songs")?.asJsonArray?.firstOrNull()?.let { firstSongElem ->
                    Timber.d(TAG, "[$apiName] Raw song JSON: ${firstSongElem}")
                }

                if (code == 200 && !parsedResponse.result?.songs.isNullOrEmpty()) {
                    Timber.d(TAG, "NetEase found ${parsedResponse.result.songs.size} songs for '$keywords'")
                    return@withContext Result.success(parsedResponse)
                }
                if (emptySuccess == null && code == 200) {
                    emptySuccess = parsedResponse
                }
                failures.add("ok_empty")
                Timber.w(TAG, "NetEase API returned empty result for '$keywords'")
                continue
            } catch (e: Exception) {
                Timber.e(TAG, "Exception during API call: ${e.message}", e)
                failures.add("exception_${e.message}")
            }
        }

        emptySuccess?.let { 
            Timber.w(TAG, "NetEase returning empty success for '$keywords'")
            return@withContext Result.success(it) 
        }
        Timber.e(TAG, "NetEase search failed completely for '$keywords': ${failures.joinToString(" | ")}")
        Result.failure(Exception("NetEase search failed: ${failures.joinToString(" | ")}"))
    }

    /**
     * Parse JsonObject response to WangySearchResponse.
     * Handles multiple API response formats.
     */
    private fun parseSearchResponse(jsonObject: JsonObject): WangySearchResponse {
        val code = jsonObject.get("code")?.asInt ?: -1

        // Debug: log response structure
        Timber.d(TAG, "parseSearchResponse: has result=${jsonObject.has("result")}, has data=${jsonObject.has("data")}")

        // Try to parse result (web API format)
        val result = jsonObject.get("result")?.asJsonObject?.let { resultJson ->
            Timber.d(TAG, "parseSearchResponse: parsing Web API format, result keys=${resultJson.keySet()}")
            parseSearchResult(resultJson)
        }

        // Try to parse data (EAPI format)
        val data = jsonObject.get("data")?.asJsonObject

        // If we have EAPI format data, convert it to WangySearchResult
        val finalResult = if (result == null && data != null) {
            Timber.d(TAG, "parseSearchResponse: parsing EAPI format, data keys=${data.keySet()}")
            parseEapiSearchResult(data)
        } else {
            result
        }

        // Debug: log parsed result
        finalResult?.songs?.firstOrNull()?.let { firstSong ->
            Timber.d(TAG, "parseSearchResponse: firstSong parsed - id=${firstSong.id}, name=${firstSong.name}, " +
                "artists=${firstSong.artists.map { it.name }}, album=${firstSong.album?.name}, albumPic=${firstSong.album?.picUrl}")
        }

        return WangySearchResponse(
            code = code,
            result = finalResult,
            data = data,
            raw = jsonObject
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
     * Parse search result from EAPI format.
     */
    private fun parseEapiSearchResult(dataJson: JsonObject): WangySearchResult? {
        val totalCount = dataJson.get("totalCount")?.asInt ?: 0
        val resources = dataJson.get("resources")?.asJsonArray ?: return null
        
        val songs = resources.mapNotNull { resourceElement ->
            val resource = resourceElement.asJsonObject
            val resourceType = resource.get("resourceType")?.asInt ?: return@mapNotNull null
            
            // Resource type 0 = song
            if (resourceType != 0) return@mapNotNull null
            
            val baseInfo = resource.get("baseInfo")?.asJsonObject ?: return@mapNotNull null
            val simpleSongData = baseInfo.get("simpleSongData")?.asJsonObject ?: return@mapNotNull null
            
            parseEapiSong(simpleSongData)
        }
        
        return WangySearchResult(
            hasMore = false,
            queryCorrected = emptyList(),
            songs = songs,
            albums = emptyList(),
            artists = emptyList(),
            songCount = totalCount,
            albumCount = 0,
            artistCount = 0
        )
    }

    /**
     * Parse song from web API format.
     */
    private fun parseSong(songJson: JsonObject): WangySong {
        val artists = songJson.get("ar")?.asJsonArray?.let { arArray ->
            arArray.mapNotNull { artistElement ->
                parseArtist(artistElement.asJsonObject)
            }
        } ?: emptyList()
        
        val album = songJson.get("al")?.asJsonObject?.let { parseAlbum(it) }
        
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
     * Parse song from EAPI format.
     */
    private fun parseEapiSong(songJson: JsonObject): WangySong {
        val artists = songJson.get("ar")?.asJsonArray?.let { arArray ->
            arArray.mapNotNull { artistElement ->
                parseArtist(artistElement.asJsonObject)
            }
        } ?: emptyList()
        
        val album = songJson.get("al")?.asJsonObject?.let { parseAlbum(it) }
        
        return WangySong(
            id = songJson.get("id")?.asLong ?: 0L,
            name = songJson.get("name")?.asString ?: "",
            artists = artists,
            album = album,
            duration = songJson.get("dt")?.asLong ?: 0L,
            copyrightId = 0L,
            fee = 0,
            trackNumber = 0,
            version = 0
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
            size = albumJson.get("size")?.asInt ?: 0,
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
            val detailData = mapOf(
                "ids" to "[$songId]",
                "c" to "[{\"id\":$songId}]"
            )
            val encrypted = WangyCrypto.weapiEncrypt(detailData)
            val bodyString = "params=${encrypted["params"]}&encSecKey=${encrypted["encSecKey"]}"
            val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val response = api.getSongDetailWeapi(requestBody)
            
            if (response.isSuccessful && response.body() != null) {
                val song = response.body()!!.songs.firstOrNull()
                val coverUrl = song?.al?.picUrl
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
        try {
            val lyricData = mapOf(
                "id" to songId,
                "cp" to false,
                "tv" to 0,
                "lv" to 0,
                "rv" to 0,
                "kv" to 0,
                "yv" to 0,
                "ytv" to 0,
                "yrv" to 0
            )
            val encrypted = WangyCrypto.eapiEncrypt("/api/song/lyric/v1", lyricData)
            val bodyString = "params=${encrypted["params"]}"
            val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val response = api.getLyricsEapi(requestBody)
            
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
