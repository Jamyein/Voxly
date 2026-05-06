package com.voxly.data.remote.tengx

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voxly.data.remote.tengx.crypto.QQMusicCrypto
import com.voxly.data.remote.tengx.model.TengxAlbum
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxCommParams
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchData
import com.voxly.data.remote.tengx.model.TengxSearchParam
import com.voxly.data.remote.tengx.model.TengxSearchRequest
import com.voxly.data.remote.tengx.model.TengxSearchReqParams
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
 * Search API based on any-listen-extension-online-metadata:
 * https://github.com/any-listen/any-listen-extension-online-metadata
 * Reference: src/qq_music/index.ts
 *
 * Features:
 * - Song search with pagination (POST with zzcSign)
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

        Timber.d(TAG, "Searching QQ Music for: '$keywords' page=$normalizedPage limit=$normalizedSize")

        return try {
            Timber.d(TAG, "=== Starting QQ Music search ===")
            Timber.d(TAG, "QQ Music search: keywords='$keywords' page=$normalizedPage limit=$normalizedSize")

            // Build POST request body
            val searchRequest = TengxSearchRequest(
                comm = TengxCommParams(),
                req = TengxSearchReqParams(
                    param = TengxSearchParam(
                        search_type = type,
                        query = keywords,
                        page_num = normalizedPage - 1, // API uses 0-indexed
                        num_per_page = normalizedSize
                    )
                )
            )

            // Serialize to JSON and compute signature
            val gson = Gson()
            val requestJson = gson.toJson(searchRequest)
            val sign = QQMusicCrypto.zzcSign(requestJson)

            Timber.d(TAG, "QQ Music sign: $sign")

            val response = api.search(sign = sign, body = searchRequest)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Timber.d(TAG, "QQ Music search response: httpCode=${response.code()}")

                    val responseString = body.string()
                    Timber.d(TAG, "QQ Music raw response: $responseString")

                    val jsonObject = JsonParser.parseString(responseString).asJsonObject
                    val parsed = parseSearchResponse(jsonObject)
                    if (parsed != null) {
                        val songCount = parsed.data?.song?.list?.size ?: 0
                        Timber.d(TAG, "QQ Music parsed songs: $songCount")

                        if (!parsed.data?.song?.list.isNullOrEmpty()) {
                            Timber.d(TAG, "QQ Music found $songCount songs for '$keywords'")
                            Timber.tag("Voxly").i("TengxRepository searchSongs completed: keywords='$keywords' resultCount=${parsed.data.song.list.size}")
                            return Result.success(parsed)
                        } else {
                            Timber.w(TAG, "QQ Music parsed but song list is empty")
                        }
                    } else {
                        Timber.w(TAG, "QQ Music parseSearchResponse returned null")
                    }
                    // Return parsed response even if empty
                    parsed?.let { return Result.success(it) }
                }
                Result.failure(Exception("QQ Music search returned empty body"))
            } else {
                Timber.w(TAG, "QQ Music search failed: http=${response.code()}")
                Result.failure(Exception("QQ Music search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "QQ Music search exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(
        songMid: String,
        decode: Boolean
    ): Result<DecodedLyricsResult> {
        return try {
            val response = api.getLyrics(songmid = songMid)

            val body = response.body()
            if (body != null) {
                val decodedResult = if (decode) {
                    DecodedLyricsResult(
                        response = body,
                        lyrics = decodeBase64(body.lyric?.lyric ?: ""),
                        translatedLyrics = decodeBase64(body.trans?.lyric ?: "")
                    )
                } else {
                    DecodedLyricsResult(
                        response = body,
                        lyrics = body.lyric?.lyric ?: "",
                        translatedLyrics = body.trans?.lyric ?: ""
                    )
                }
                Result.success(decodedResult)
            } else {
                Result.failure(Exception("QQ Music get lyrics returned empty body"))
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

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("QQ Music get song detail returned empty body"))
                }
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

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("QQ Music get album detail returned empty body"))
                }
            } else {
                Result.failure(Exception("QQ Music get album detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decodes Base64 encoded string using UTF-8.
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

    /**
     * Parses POST search response from QQ Music mobile API.
     *
     * New response format: body -> req -> data -> body -> item_song (or songlist)
     * Also supports legacy format: data -> song -> list
     */
    private fun parseSearchResponse(root: JsonObject): TengxSearchResponse? {
        val code = root.optInt("code") ?: 0
        if (code != 0) {
            Timber.w(TAG, "Search response error code: $code")
            return null
        }

        // Try new POST response format first: body.req.data.body.item_song
        val songList = run {
            val body = root.optObject("body")
            val req = body?.optObject("req")
            val data = req?.optObject("data")
            val innerBody = data?.optObject("body")

            // Try item_song first, then songlist
            innerBody?.optArray("item_song")
                ?: innerBody?.optArray("songlist")
                ?: JsonArray()
        }

        // If new format didn't find songs, try legacy GET format: data.song.list
        val finalSongList = if (songList.size() == 0) {
            val data = root.optObject("data") ?: root
            val songNode = data.optObject("song")
            songNode?.optArray("list") ?: JsonArray()
        } else {
            songList
        }

        val songs = finalSongList.mapNotNull { it.asJsonObjectOrNull() }.mapNotNull { item ->
            val id = item.optLong("id") ?: item.optLong("songid") ?: return@mapNotNull null
            val name = item.optString("name") ?: item.optString("title") ?: item.optString("songName") ?: return@mapNotNull null
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
                val albumMid = albumJson.optString("mid")?.takeIf { it.isNotBlank() }
                val albumPicRaw = albumJson.optString("pic")?.takeIf { it.isNotBlank() }
                val albumPic = when {
                    albumPicRaw != null -> albumPicRaw
                    albumMid != null -> "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg"
                    else -> null
                }

                TengxAlbum(
                    id = albumId,
                    mid = albumMid ?: "",
                    name = albumName,
                    title = albumJson.optString("title") ?: "",
                    singer = null,
                    publicTime = albumJson.optString("publicTime") ?: "",
                    pic = albumPic ?: ""
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

        // Calculate total from new format meta or legacy format
        val total = run {
            val body = root.optObject("body")
            val req = body?.optObject("req")
            val data = req?.optObject("data")
            val meta = data?.optObject("meta")
            meta?.optInt("sum")
        }
            ?: run {
                val data = root.optObject("data") ?: root
                val songNode = data.optObject("song")
                songNode?.optInt("totalnum")
                    ?: songNode?.optInt("totalNum")
                    ?: songNode?.optInt("sum")
            }
            ?: songs.size

        return TengxSearchResponse(
            code = code,
            data = TengxSearchData(
                song = TengxSongResult(
                    list = songs,
                    totalnum = total
                )
            ),
            message = root.optString("message")
        )
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
