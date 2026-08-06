package com.voxly.data.remote.tengx

import java.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voxly.data.remote.tengx.crypto.QQMusicLyricCrypto
import com.voxly.data.remote.tengx.model.TengxAlbum
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxSearchData
import com.voxly.data.remote.tengx.model.TengxSearchResponse
import com.voxly.data.remote.tengx.model.TengxSinger
import com.voxly.data.remote.tengx.model.TengxSong
import com.voxly.data.remote.tengx.model.TengxSongDetail
import com.voxly.data.remote.tengx.model.TengxSongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

private const val TAG = "TengxRepository"

/**
 * Repository for QQ Music API operations.
 *
 * Uses the unified musicu.fcg POST endpoint for search and lyrics,
 * matching the QQ Music mobile client protocol documented by the
 * Lyrico plugin reference (DoSearchForQQMusicLite / GetPlayLyricInfo).
 */
interface TengxRepository {

    /**
     * Searches for songs by keywords.
     *
     * @param keywords Search keywords
     * @param pageNum Page number (1-indexed)
     * @param pageSize Results per page (default: 20)
     * @param type Search type: 0=song
     */
    suspend fun searchSongs(
        keywords: String,
        pageNum: Int = 1,
        pageSize: Int = 20,
        type: Int = 0
    ): Result<TengxSearchResponse>

    /**
     * Gets lyrics for a song by numeric QQ Music song ID.
     *
     * Uses the GetPlayLyricInfo endpoint with QRC decryption.
     * Returns original lyrics (QRC→LRC), translated lyrics, and
     * romanized lyrics when available.
     */
    suspend fun getLyrics(
        songId: Long,
        songName: String = "",
        albumName: String = "",
        artistName: String = "",
        duration: Long = 0
    ): Result<DecodedLyricsResult>

    /**
     * Gets detailed information for songs.
     */
    suspend fun getSongDetail(songIds: List<Long>): Result<TengxSongDetail>

    /**
     * Gets album details.
     */
    suspend fun getAlbumDetail(albumId: Long): Result<TengxAlbumDetail>
}

/**
 * Result container for decoded lyrics.
 */
data class DecodedLyricsResult(
    /** Original lyrics (QRC→LRC or plain) */
    val lyrics: String = "",
    /** Translated lyrics (if available) */
    val translatedLyrics: String = ""
)

/**
 * Default implementation of TengxRepository.
 */
class TengxRepositoryImpl(
    private val api: TengxApi
) : TengxRepository {

    // -- common device fingerprint for musicu.fcg (matches Lyrico QQ_MUSICU_COMM) --

    private val musicuComm: JsonObject
        get() = JsonObject().apply {
            addProperty("ct", "11")
            addProperty("cv", "1003006")
            addProperty("v", "1003006")
            addProperty("os_ver", "15")
            addProperty("phonetype", "24122RKC7C")
            addProperty("tmeAppID", "qqmusiclight")
            addProperty("nettype", "NETWORK_WIFI")
        }

    override suspend fun searchSongs(
        keywords: String,
        pageNum: Int,
        pageSize: Int,
        type: Int
    ): Result<TengxSearchResponse> = withContext(Dispatchers.IO) {
        val page = if (pageNum <= 0) 1 else pageNum
        val size = pageSize.coerceIn(1, 50)

        Timber.d(TAG, "Searching QQ Music for: '$keywords' page=$page limit=$size")

        try {
            val param = JsonObject().apply {
                addProperty("search_id", randomSearchId())
                addProperty("remoteplace", "search.android.keyboard")
                addProperty("query", keywords)
                addProperty("search_type", type)
                addProperty("num_per_page", size)
                addProperty("page_num", page)
                addProperty("highlight", 0)
                addProperty("nqc_flag", 0)
                addProperty("page_id", 1)
                addProperty("grp", 1)
            }
            val req0 = JsonObject().apply {
                addProperty("module", "music.search.SearchCgiService")
                addProperty("method", "DoSearchForQQMusicLite")
                add("param", param)
            }
            val body = JsonObject().apply {
                add("comm", musicuComm)
                add("req_0", req0)
            }

            val requestBody = body.toString().toByteArray(Charsets.UTF_8)
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val response = api.postMusicu(requestBody)

            if (response.isSuccessful) {
                val raw = response.body()?.string()
                if (raw != null) {
                    val parsed = parseMusicuSearch(JsonParser.parseString(raw).asJsonObject)
                    if (parsed != null) {
                        Timber.tag("Voxly").i(
                            "TengxRepository searchSongs completed: keywords='$keywords' resultCount=${parsed.data?.song?.totalnum ?: 0}"
                        )
                        Result.success(parsed)
                    } else {
                        Result.failure(Exception("QQ Music search returned empty body"))
                    }
                } else {
                    Result.failure(Exception("QQ Music search returned empty body"))
                }
            } else {
                Result.failure(Exception("QQ Music search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "QQ Music search exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(
        songId: Long,
        songName: String,
        albumName: String,
        artistName: String,
        duration: Long
    ): Result<DecodedLyricsResult> = withContext(Dispatchers.IO) {
        try {
            val intervalSec = (duration / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

            val param = JsonObject().apply {
                addProperty("songID", songId)
                addProperty("songName", encodeB64(songName))
                addProperty("albumName", encodeB64(albumName))
                addProperty("singerName", encodeB64(artistName))
                addProperty("crypt", 1)
                addProperty("qrc", 1)
                addProperty("trans", 1)
                addProperty("roma", 1)
                addProperty("cv", 2111)
                addProperty("ct", 19)
                addProperty("lrc_t", 0)
                addProperty("qrc_t", 0)
                addProperty("roma_t", 0)
                addProperty("trans_t", 0)
                addProperty("type", 0)
                addProperty("interval", intervalSec)
            }
            val req0 = JsonObject().apply {
                addProperty("module", "music.musichallSong.PlayLyricInfo")
                addProperty("method", "GetPlayLyricInfo")
                add("param", param)
            }
            val body = JsonObject().apply {
                add("comm", musicuComm)
                add("req_0", req0)
            }

            val requestBody = body.toString().toByteArray(Charsets.UTF_8)
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val response = api.postMusicu(requestBody)

            if (response.isSuccessful) {
                val raw = response.body()?.string()
                if (raw == null) {
                    Result.failure(Exception("QQ Music get lyrics returned empty body"))
                } else {
                    val root = JsonParser.parseString(raw).asJsonObject
                    val data = root
                        .optObject("req_0")
                        ?.optObject("data")
                    if (data == null) {
                        Result.failure(Exception("QQ Music get lyrics: missing req_0.data"))
                    } else {
                        val qrcEncrypted = data.optString("lyric") ?: ""
                        val transEncrypted = data.optString("trans") ?: ""
                        val romaEncrypted = data.optString("roma") ?: ""

                        val qrcText = QQMusicLyricCrypto.decodeLyricPayload(qrcEncrypted)
                        val transText = QQMusicLyricCrypto.decodeLyricPayload(transEncrypted)

                        val lrc = if (qrcText.isNotBlank()) {
                            QQMusicQrcParser.qrcToLrc(qrcText)
                        } else {
                            ""
                        }
                        val transLrc = if (transText.isNotBlank()) {
                            QQMusicQrcParser.plainTextToLrc(transText)
                        } else {
                            ""
                        }

                        Result.success(DecodedLyricsResult(lyrics = lrc, translatedLyrics = transLrc))
                    }
                }
            } else {
                Result.failure(Exception("QQ Music get lyrics failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "QQ Music get lyrics exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getSongDetail(songIds: List<Long>): Result<TengxSongDetail> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSongDetail(songIds = songIds.joinToString(","))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("QQ Music get song detail returned empty body"))
            } else {
                Result.failure(Exception("QQ Music get song detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumDetail(albumId: Long): Result<TengxAlbumDetail> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlbumDetail(albumId = albumId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("QQ Music get album detail returned empty body"))
            } else {
                Result.failure(Exception("QQ Music get album detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- private helpers ---

    /**
     * Parses musicu.fcg search response: root → req_0 → data → body → item_song.
     */
    private fun parseMusicuSearch(root: JsonObject): TengxSearchResponse? {
        val reqData = root.optObject("req_0")?.optObject("data") ?: run {
            Timber.w(TAG, "Search response missing req_0.data")
            return null
        }
        val innerBody = reqData.optObject("body")
        val itemSong = innerBody?.optArray("item_song") ?: JsonArray()
        val meta = reqData.optObject("meta")
        val total = meta?.optInt("sum") ?: innerBody?.optInt("sum") ?: itemSong.size()

        val songs = ArrayList<TengxSong>(itemSong.size())
        for (je in itemSong) {
            val item = je?.asJsonObject ?: continue
            val id = item.optLong("id") ?: continue
            val mid = item.optString("mid") ?: ""
            val name = item.optString("name") ?: item.optString("title") ?: ""
            val title = item.optString("title") ?: name
            val subtitle = item.optString("subtitle")?.takeIf { it.isNotBlank() }
                ?: item.optString("desc")?.takeIf { it.isNotBlank() } ?: ""
            val interval = item.optInt("interval") ?: 0

            val singerList = ArrayList<TengxSinger>()
            item.optArray("singer")?.let { arr ->
                for (s in arr) {
                    val sj = s?.asJsonObject ?: continue
                    singerList.add(TengxSinger(
                        id = sj.optLong("id") ?: 0L,
                        name = sj.optString("name") ?: "",
                        title = sj.optString("title") ?: "",
                        type = sj.optInt("type") ?: 0,
                        gender = sj.optInt("gender") ?: 0,
                        pic = sj.optString("pic") ?: ""
                    ))
                }
            }

            val album = item.optObject("album")?.let { aj ->
                val aid = aj.optLong("id") ?: 0L
                val amid = aj.optString("mid") ?: ""
                val aname = aj.optString("name") ?: aj.optString("title") ?: ""
                TengxAlbum(
                    id = aid, mid = amid, name = aname,
                    title = aj.optString("title") ?: "",
                    singer = null,
                    publicTime = item.optString("time_public") ?: "",
                    pic = aj.optString("pic")?.takeIf { it.isNotBlank() }
                        ?: amid.takeIf { it.isNotBlank() }?.let {
                            "https://y.gtimg.cn/music/photo_new/T002R500x500M000${it}.jpg"
                        } ?: ""
                )
            }

            songs.add(TengxSong(
                id = id, mid = mid, name = name, title = title, subtitle = subtitle,
                singer = singerList, album = album, interval = interval, version = 0
            ))
        }

        return TengxSearchResponse(
            code = 0,
            data = TengxSearchData(
                song = TengxSongResult(list = songs, totalnum = total)
            )
        )
    }

    private fun encodeB64(text: String): String {
        return Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    private fun randomSearchId(): String {
        return (10_000_000_000_000_000L + (Math.random() * 80_000_000_000_000_000L).toLong()).toString()
    }

    // --- Gson helpers ---
    // ponytail: inline Gson helpers instead of a separate extension file —
    // used only in this file.

    private fun JsonObject.optObject(name: String): JsonObject? {
        val e = get(name) ?: return null
        return if (e.isJsonObject) e.asJsonObject else null
    }

    private fun JsonObject.optArray(name: String): JsonArray? {
        val e = get(name) ?: return null
        return if (e.isJsonArray) e.asJsonArray else null
    }

    private fun JsonObject.optString(name: String): String? {
        val e = get(name) ?: return null
        return if (e.isJsonNull) null else runCatching { e.asString }.getOrNull()
    }

    private fun JsonObject.optInt(name: String): Int? {
        val e = get(name) ?: return null
        return if (e.isJsonNull) null else runCatching { e.asInt }.getOrNull()
    }

    private fun JsonObject.optLong(name: String): Long? {
        val e = get(name) ?: return null
        return if (e.isJsonNull) null else runCatching { e.asLong }.getOrNull()
    }

}
