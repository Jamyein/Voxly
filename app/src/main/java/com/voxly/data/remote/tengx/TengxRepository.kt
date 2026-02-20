package com.voxly.data.remote.tengx

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.voxly.data.remote.tengx.model.TengxAlbum
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchData
import com.voxly.data.remote.tengx.model.TengxSearchResponse
import com.voxly.data.remote.tengx.model.TengxSinger
import com.voxly.data.remote.tengx.model.TengxSong
import com.voxly.data.remote.tengx.model.TengxSongResult
import com.voxly.data.remote.tengx.model.TengxSongDetail
import timber.log.Timber

private const val TAG = "TengxRepository"

/**
 * Repository for TengX Music API operations.
 * Handles API communication for QQ Music service.
 *
 * Uses simplified web API (no complex JSON body required).
 *
 * Features:
 * - Song search with pagination
 * - Lyrics retrieval with Base64 decoding
 * - Song and album details
 */
interface TengxRepository {

    /**
     * Searches for songs by keywords.
     *
     * @param keywords Search keywords
     * @param pageNum Page number (1-indexed)
     * @param pageSize Results per page (default: 20)
     * @param type Search type: 0=song, 2=album, 3=singer
     * @return Search response or error
     */
    suspend fun searchSongs(
        keywords: String,
        pageNum: Int = 0,
        pageSize: Int = 20,
        type: Int = 0
    ): Result<TengxSearchResponse>

    /**
     * Gets lyrics for a song by songmid.
     * Returns decoded lyrics content.
     *
     * @param songMid Song middle ID
     * @param decode Whether to Base64 decode the lyrics (default: true)
     * @return Lyrics response with decoded content or error
     */
    suspend fun getLyrics(
        songMid: String,
        decode: Boolean = true
    ): Result<DecodedLyricsResult>

    /**
     * Gets detailed information for songs.
     *
     * @param songIds List of song IDs
     * @return Song detail response or error
     */
    suspend fun getSongDetail(songIds: List<Long>): Result<TengxSongDetail>

    /**
     * Gets album details.
     *
     * @param albumId Album ID
     * @return Album detail response or error
     */
    suspend fun getAlbumDetail(albumId: Long): Result<TengxAlbumDetail>
}

/**
 * Result container for decoded lyrics.
 */
data class DecodedLyricsResult(
    /** Original response */
    val response: TengxLyricsResponse,
    /** Decoded original lyrics */
    val lyrics: String = "",
    /** Decoded translated lyrics (if available) */
    val translatedLyrics: String = ""
)

/**
 * Default implementation of TengxRepository.
 *
 * @property api TengX Music API instance
 */
class TengxRepositoryImpl(
    private val api: TengxApi
) : TengxRepository {

    override suspend fun searchSongs(
        keywords: String,
        pageNum: Int,
        pageSize: Int,
        type: Int
    ): Result<TengxSearchResponse> {
        val normalizedPage = if (pageNum <= 0) 1 else pageNum
        val normalizedSize = pageSize.coerceIn(1, 50)
        val failures = mutableListOf<String>()
        var emptySuccess: TengxSearchResponse? = null

        Timber.d(TAG, "Searching QQ Music for: '$keywords' page=$normalizedPage limit=$normalizedSize")

        // Try mobile web search first - more reliable as it simulates browser search
        try {
            Timber.d(TAG, "Trying QQ Music mobile web search for '$keywords'")
            val mobileResponse = api.searchMobile(
                keyword = keywords,
                page = normalizedPage,
                perPage = normalizedSize
            )
            
            if (mobileResponse.isSuccessful) {
                val body = mobileResponse.body()?.string()
                if (!body.isNullOrBlank()) {
                    Timber.d(TAG, "QQ Music mobile response: ${body.take(200)}")
                    parseMobileSearchResponse(body)?.let { parsed ->
                        if (!parsed.data?.song?.list.isNullOrEmpty()) {
                            val songCount = parsed.data.song.list.size
                            Timber.d(TAG, "QQ Music mobile found $songCount songs for '$keywords'")
                            return Result.success(parsed)
                        }
                    }
                }
            }
            Timber.w(TAG, "QQ Music mobile search failed: http=${mobileResponse.code()}")
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music mobile search exception: ${e.message}", e)
        }

        // Try both known web-search methods. QQ occasionally deprecates one side.
        val v2Methods = listOf("DoSearchForQQMusicDesktop", "DoSearchForQQMusicMobile")
        for (method in v2Methods) {
            try {
                val v2Response = api.searchV2(
                    body = buildV2RequestBody(
                        keywords = keywords,
                        pageNum = normalizedPage,
                        pageSize = normalizedSize,
                        type = type,
                        method = method
                    )
                )
                val body = v2Response.body()
                Timber.d(TAG, "QQ Music v2($method) response: httpCode=${v2Response.code()}")
                
                if (v2Response.isSuccessful && body != null) {
                    parseV2SearchResponse(body)?.let { parsed ->
                        val songCount = parsed.data?.song?.list?.size ?: 0
                        Timber.d(TAG, "QQ Music v2($method) parsed songs: $songCount")
                        
                        if (!parsed.data?.song?.list.isNullOrEmpty()) {
                            Timber.d(TAG, "QQ Music found $songCount songs for '$keywords' using method $method")
                            return Result.success(parsed)
                        }
                        if (emptySuccess == null) {
                            emptySuccess = parsed
                        }
                        Timber.w(TAG, "QQ Music v2($method) returned empty results")
                    }
                }
                failures.add("v2:$method http=${v2Response.code()}")
                Timber.w(TAG, "QQ Music v2($method) failed: http=${v2Response.code()}")
            } catch (e: Exception) {
                failures.add("v2:$method ex=${e.message ?: "unknown"}")
                Timber.e(TAG, "QQ Music v2($method) exception: ${e.message}", e)
            }
        }

        return try {
            // Fallback to legacy endpoint if v2 response cannot be parsed.
            Timber.d(TAG, "Trying QQ Music legacy endpoint for '$keywords'")
            val legacyResponse = api.search(
                keyword = keywords,
                page = normalizedPage,
                perPage = normalizedSize
            )
            if (legacyResponse.isSuccessful && legacyResponse.body() != null) {
                val body = legacyResponse.body()!!
                val songCount = body.data?.song?.list?.size ?: 0
                Timber.d(TAG, "QQ Music legacy response: httpCode=${legacyResponse.code()} songs=$songCount")
                
                if (!body.data?.song?.list.isNullOrEmpty()) {
                    Timber.d(TAG, "QQ Music legacy found $songCount songs for '$keywords'")
                    Result.success(body)
                } else {
                    Timber.w(TAG, "QQ Music legacy returned empty for '$keywords'")
                    emptySuccess?.let { Result.success(it) } ?: Result.success(body)
                }
            } else {
                failures.add("legacy http=${legacyResponse.code()}")
                Timber.w(TAG, "QQ Music legacy failed: http=${legacyResponse.code()}")
                emptySuccess?.let { Result.success(it) }
                    ?: Result.failure(Exception("QQ Music search failed: ${failures.joinToString(" | ")}"))
            }
        } catch (e: Exception) {
            failures.add("legacy ex=${e.message ?: "unknown"}")
            Timber.e(TAG, "QQ Music legacy exception: ${e.message}", e)
            emptySuccess?.let { Result.success(it) }
                ?: Result.failure(Exception("QQ Music search failed: ${failures.joinToString(" | ")}"))
        }
    }

    override suspend fun getLyrics(
        songMid: String,
        decode: Boolean
    ): Result<DecodedLyricsResult> {
        return try {
            val response = api.getLyrics(songmid = songMid)

            if (response.isSuccessful && response.body() != null) {
                val lyricsResponse = response.body()!!
                val decodedResult = if (decode) {
                    DecodedLyricsResult(
                        response = lyricsResponse,
                        lyrics = decodeBase64(lyricsResponse.lyric?.lyric ?: ""),
                        translatedLyrics = decodeBase64(lyricsResponse.trans?.lyric ?: "")
                    )
                } else {
                    DecodedLyricsResult(
                        response = lyricsResponse,
                        lyrics = lyricsResponse.lyric?.lyric ?: "",
                        translatedLyrics = lyricsResponse.trans?.lyric ?: ""
                    )
                }
                Result.success(decodedResult)
            } else {
                Result.failure(Exception("QQ Music get lyrics failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSongDetail(songIds: List<Long>): Result<TengxSongDetail> {
        return try {
            val response = api.getSongDetail(
                songIds = songIds.joinToString(",")
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("QQ Music get song detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumDetail(albumId: Long): Result<TengxAlbumDetail> {
        return try {
            val response = api.getAlbumDetail(albumId = albumId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("QQ Music get album detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decodes Base64 encoded string.
     *
     * @param encoded Base64 encoded string
     * @return Decoded string
     */
    private fun decodeBase64(encoded: String): String {
        return try {
            if (encoded.isBlank()) {
                ""
            } else {
                val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildV2RequestBody(
        keywords: String,
        pageNum: Int,
        pageSize: Int,
        type: Int,
        method: String
    ): Map<String, Any> {
        return mapOf(
            "comm" to mapOf(
                "ct" to 24,
                "cv" to 0,
                "uin" to 0
            ),
            "req_1" to mapOf(
                "module" to "music.search.SearchCgiService",
                "method" to method,
                "param" to mapOf(
                    "query" to keywords,
                    "search_type" to type,
                    "page_num" to pageNum,
                    "num_per_page" to pageSize
                )
            )
        )
    }

    private fun parseV2SearchResponse(root: JsonObject): TengxSearchResponse? {
        val req = root.optObject("req_1")
            ?: root.optObject("req")
            ?: root.firstNestedObject()
            ?: root
        val reqCode = req.optInt("code") ?: root.optInt("code") ?: 0
        if (reqCode != 0) return null

        val data = req.optObject("data") ?: req
        val body = data.optObject("body") ?: data
        val songNode = body.optObject("song")
            ?: data.optObject("song")
            ?: req.optObject("song")
            ?: return null
        val songList = songNode.optArray("list") ?: JsonArray()

        val songs = songList.mapNotNull { it.asJsonObjectOrNull() }.mapNotNull { item ->
            val id = item.optLong("id") ?: item.optLong("songid") ?: return@mapNotNull null
            val name = item.optString("name") ?: item.optString("title") ?: return@mapNotNull null
            val title = item.optString("title") ?: name
            val subtitle = item.optString("subtitle").orEmpty()
            val interval = item.optInt("interval") ?: item.optInt("duration") ?: 0
            val version = item.optInt("version") ?: 0
            val mid = item.optString("mid") ?: item.optString("songmid") ?: ""

            val singers = item.optArray("singer")
                ?.mapNotNull { it.asJsonObjectOrNull() }
                ?.mapNotNull { singer ->
                    val singerId = singer.optLong("id") ?: return@mapNotNull null
                    val singerName = singer.optString("name") ?: return@mapNotNull null
                    TengxSinger(
                        id = singerId,
                        name = singerName,
                        title = singer.optString("title") ?: "",
                        type = singer.optInt("type") ?: 0,
                        gender = singer.optInt("gender") ?: 0,
                        pic = singer.optString("pic") ?: ""
                    )
                }
                .orEmpty()

            val album = item.optObject("album")?.let { albumJson ->
                val albumId = albumJson.optLong("id") ?: 0L
                val albumName = albumJson.optString("name")
                    ?: albumJson.optString("title")
                    ?: "Unknown Album"
                val albumMid = albumJson.optString("mid") ?: ""
                val albumPicRaw = albumJson.optString("pic")
                val albumPic = when {
                    !albumPicRaw.isNullOrBlank() -> albumPicRaw
                    albumMid.isNotBlank() -> "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg"
                    else -> ""
                }

                TengxAlbum(
                    id = albumId,
                    mid = albumMid,
                    name = albumName,
                    title = albumJson.optString("title") ?: "",
                    singer = null,
                    publicTime = albumJson.optString("publicTime") ?: "",
                    pic = albumPic
                )
            }

            TengxSong(
                id = id,
                mid = mid,
                name = name,
                title = title,
                subtitle = subtitle,
                singer = singers,
                album = album,
                interval = interval,
                version = version
            )
        }

        val total = songNode.optInt("totalnum")
            ?: songNode.optInt("totalNum")
            ?: songNode.optInt("sum")
            ?: songs.size

        return TengxSearchResponse(
            code = reqCode,
            data = TengxSearchData(
                song = TengxSongResult(
                    list = songs,
                    totalnum = total
                )
            ),
            message = req.optString("message")
        )
    }

    /**
     * Parses mobile web search response.
     * The mobile endpoint returns raw JSON without callback wrapper.
     */
    private fun parseMobileSearchResponse(response: String): TengxSearchResponse? {
        return try {
            val json = com.google.gson.JsonParser.parseString(response).asJsonObject
            val code = json.optInt("code") ?: -1
            if (code != 0) {
                Timber.w(TAG, "Mobile search returned error code: $code")
                return null
            }

            val songJson = json.optObject("data")?.optObject("song")
            val songList = songJson?.optArray("list")
            val totalnum = songJson?.optInt("totalnum") ?: 0

            val songs: List<TengxSong> = songList?.mapNotNull<JsonElement, TengxSong> { element ->
                val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
                
                val id = item.optLong("id") ?: item.optLong("songid") ?: return@mapNotNull null
                val name = item.optString("songName") 
                    ?: item.optString("name") 
                    ?: item.optString("title") 
                    ?: return@mapNotNull null
                val title = item.optString("subtitle") ?: name
                val interval = item.optInt("interval") ?: 0
                val mid = item.optString("songmid") ?: item.optString("mid") ?: ""
                
                // Parse singer
                val singerArray = item.optArray("singer")
                val singers: List<TengxSinger> = singerArray?.mapNotNull<JsonElement, TengxSinger> { singerElem ->
                    val singerObj = singerElem.asJsonObjectOrNull() ?: return@mapNotNull null
                    val singerId = singerObj.optLong("id") ?: return@mapNotNull null
                    val singerName = singerObj.optString("name") ?: return@mapNotNull null
                    TengxSinger(
                        id = singerId,
                        name = singerName,
                        title = singerObj.optString("title") ?: "",
                        type = singerObj.optInt("type") ?: 0,
                        gender = singerObj.optInt("gender") ?: 0,
                        pic = singerObj.optString("pic") ?: ""
                    )
                } ?: emptyList()

                // Parse album
                val albumJson = item.optObject("album")
                val album = if (albumJson != null) {
                    val albumId = albumJson.optLong("id") ?: 0L
                    val albumName = albumJson.optString("name") ?: "Unknown Album"
                    val albumMid = albumJson.optString("mid") ?: ""
                    val albumPic = albumJson.optString("picUrl") 
                        ?: albumJson.optString("pic")
                        ?: if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg" 
                        else ""
                    
                    TengxAlbum(
                        id = albumId,
                        mid = albumMid,
                        name = albumName,
                        title = albumJson.optString("title") ?: "",
                        singer = null,
                        publicTime = albumJson.optString("publicTime") ?: "",
                        pic = albumPic
                    )
                } else null

                TengxSong(
                    id = id,
                    mid = mid,
                    name = name,
                    title = title,
                    subtitle = "",
                    singer = singers,
                    album = album,
                    interval = interval,
                    version = 0
                )
            } ?: emptyList()

            TengxSearchResponse(
                code = code,
                data = TengxSearchData(
                    song = TengxSongResult(
                        list = songs,
                        totalnum = totalnum
                    )
                ),
                message = json.optString("message")
            )
        } catch (e: Exception) {
            Timber.e(TAG, "Failed to parse mobile search response: ${e.message}", e)
            null
        }
    }

    private fun JsonObject.optObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.firstNestedObject(): JsonObject? {
        for ((_, value) in entrySet()) {
            if (value.isJsonObject) return value.asJsonObject
        }
        return null
    }

    private fun JsonObject.optArray(name: String): JsonArray? {
        val element = get(name) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.optString(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.optInt(name: String): Int? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.optLong(name: String): Long? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asLong }.getOrNull()
    }

    private fun JsonArray.mapNotNull(transform: (JsonElement) -> JsonObject?): List<JsonObject> {
        val out = ArrayList<JsonObject>(size())
        for (i in 0 until size()) {
            transform(get(i))?.let(out::add)
        }
        return out
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }
}
