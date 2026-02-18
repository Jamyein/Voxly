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
     * @param pageNum Page number (0-indexed)
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
        return try {
            val v2Response = api.searchV2(body = buildV2RequestBody(keywords, pageNum, pageSize, type))
            if (v2Response.isSuccessful && v2Response.body() != null) {
                parseV2SearchResponse(v2Response.body()!!)?.let { return Result.success(it) }
            }

            // Fallback to legacy endpoint if v2 response cannot be parsed.
            val legacyResponse = api.search(
                keyword = keywords,
                page = pageNum + 1,
                perPage = pageSize
            )
            if (legacyResponse.isSuccessful && legacyResponse.body() != null) {
                Result.success(legacyResponse.body()!!)
            } else {
                Result.failure(Exception("QQ Music search failed: v2=${v2Response.code()}, legacy=${legacyResponse.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        type: Int
    ): Map<String, Any> {
        return mapOf(
            "comm" to mapOf(
                "ct" to 24,
                "cv" to 0,
                "uin" to 0
            ),
            "req_1" to mapOf(
                "module" to "music.search.SearchCgiService",
                "method" to "DoSearchForQQMusicDesktop",
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
        val req = root.optObject("req_1") ?: root
        val reqCode = req.optInt("code") ?: root.optInt("code") ?: 0
        if (reqCode != 0) return null

        val data = req.optObject("data") ?: return null
        val body = data.optObject("body") ?: data
        val songNode = body.optObject("song") ?: return null
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

    private fun JsonObject.optObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
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
