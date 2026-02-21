package com.voxly.data.remote.wangy.ne

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.voxly.data.remote.wangy.crypto.WangyCrypto
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySong
import com.voxly.data.remote.wangy.model.WangyAlbum
import com.voxly.data.remote.wangy.model.WangyArtist
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.OnlineLyricsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Netease Cloud Music API operations.
 * Implements anonymous login mechanism similar to Lyrico.
 * 
 * Key features:
 * - Anonymous login with session caching
 * - EAPI encryption
 * - Complete lyrics parsing (YRC/LRC/translation/romanization)
 */
interface NeRepository {
    /**
     * Searches for songs by keywords.
     */
    suspend fun searchSongs(
        keywords: String,
        page: Int = 1,
        limit: Int = 30
    ): Result<List<OnlineLyricsResult>>

    /**
     * Gets lyrics for a song.
     */
    suspend fun getLyrics(songId: Long): Result<Lyrics>

    /**
     * Gets album details.
     */
    suspend fun getAlbumDetail(albumId: Long): Result<WangyAlbumDetail>
}

/**
 * Implementation of NeRepository.
 */
@Singleton
class NeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: NeApi
) : NeRepository {

    private val gson = Gson()
    private val mutex = Mutex()
    private var isInitialized = false
    private var userId: Long = 0

    // Session management
    private val cookieMap = mutableMapOf<String, String>()
    private val deviceId = NeCrypto.generateDeviceId()
    private val clientSign = NeCrypto.generateClientSign()

    // Preferences
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "NeRepository"
        private const val PREF_NAME = "ne_source_prefs"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_INIT_TIME = "init_time"
    }

    /**
     * Ensures initialization (anonymous login) is complete.
     */
    private suspend fun ensureInit() {
        if (isInitialized) return

        mutex.withLock {
            if (isInitialized) return

            // Try to load cached session
            if (loadSession()) {
                isInitialized = true
                Timber.d(TAG, "Session restored from cache, uid: $userId")
                return
            }

            Timber.d(TAG, "Starting anonymous login...")
            performAnonymousLogin()
        }
    }

    /**
     * Loads cached session from SharedPreferences.
     */
    private fun loadSession(): Boolean {
        val savedInitTime = prefs.getLong(KEY_INIT_TIME, 0L)
        if (System.currentTimeMillis() - savedInitTime > NeCrypto.SESSION_EXPIRE_TIME) {
            return false
        }

        val savedCookiesJson = prefs.getString(KEY_COOKIES, null)
        val savedUserId = prefs.getLong(KEY_USER_ID, 0L)

        return if (!savedCookiesJson.isNullOrEmpty() && savedUserId != 0L) {
            try {
                val map: Map<String, String> = gson.fromJson(savedCookiesJson, Map::class.java) as Map<String, String>
                cookieMap.clear()
                cookieMap.putAll(map)
                userId = savedUserId
                true
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to load session: ${e.message}")
                false
            }
        } else false
    }

    /**
     * Saves session to SharedPreferences.
     */
    private fun saveSession(uid: Long, cookies: Map<String, String>) {
        prefs.edit {
            putLong(KEY_USER_ID, uid)
            putString(KEY_COOKIES, gson.toJson(cookies))
            putLong(KEY_INIT_TIME, System.currentTimeMillis())
        }
    }

    /**
     * Performs anonymous login to get session cookies.
     */
    private suspend fun performAnonymousLogin() {
        try {
            val path = "/eapi/register/anonimous"
            val username = NeCrypto.getAnonymousUsername(deviceId)

            val preCookies = mutableMapOf(
                "os" to "pc",
                "deviceId" to deviceId,
                "osver" to "Microsoft-Windows-10--build-${(19000..23000).random()}-64bit",
                "clientSign" to clientSign,
                "channel" to "netease",
                "mode" to listOf("MS-iCraft B760M WIFI", "ASUS ROG STRIX Z790", "MSI MAG B550 TOMAHAWK").random(),
                "appver" to NeCrypto.APP_VER
            )

            val params = mapOf(
                "username" to username,
                "e_r" to true
            )

            val cookieStr = preCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val headers = NeApi.buildLoginHeaders(cookieStr)

            val body = buildEncryptedBody(path, params, preCookies)
            val response = api.request("${NeCrypto.EAPI_BASE_URL}$path", headers, body)

            if (response.isSuccessful) {
                val setCookieHeaders = response.headers().values("Set-Cookie")
                val responseCookies = mutableMapOf<String, String>()
                
                setCookieHeaders.forEach { cookieLine ->
                    val cookiePair = cookieLine.split(";")[0].split("=")
                    if (cookiePair.size >= 2) {
                        responseCookies[cookiePair[0]] = cookiePair[1]
                    }
                }

                cookieMap.clear()
                cookieMap.putAll(preCookies)
                responseCookies["MUSIC_A"]?.let { cookieMap["MUSIC_A"] = it }
                responseCookies["NMTID"]?.let { cookieMap["NMTID"] = it }
                responseCookies["__csrf"]?.let { cookieMap["__csrf"] = it }

                // Generate WNMCID
                val wnmcid = "${(1..6).map { ('a'..'z').random() }.joinToString("")}.${System.currentTimeMillis()}.01.0"
                cookieMap["WNMCID"] = wnmcid

                // Parse response to get userId
                val responseBodyBytes = response.body()?.bytes() ?: byteArrayOf()
                if (responseBodyBytes.isNotEmpty()) {
                    val decrypted = String(NeCrypto.encryptParams(path, "{}").let {
                        // Re-decrypt properly
                        WangyCrypto.aesEncryptEcbWithPadding(responseBodyBytes, "e82ckenh8dichen8".toByteArray())
                    })
                    
                    // Try to parse JSON directly
                    try {
                        val jsonStr = String(responseBodyBytes)
                        if (jsonStr.contains("userId")) {
                            val userIdMatch = Regex("\"userId\":(\\d+)").find(jsonStr)
                            userId = userIdMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        }
                    } catch (e: Exception) {
                        Timber.w(TAG, "Could not parse userId from response")
                    }

                    if (userId > 0) {
                        saveSession(userId, cookieMap)
                        isInitialized = true
                        Timber.d(TAG, "Anonymous login success: userId=$userId")
                    } else {
                        Timber.e(TAG, "Login failed: userId not found in response")
                    }
                }
            } else {
                Timber.e(TAG, "Anonymous login HTTP failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(TAG, "Anonymous login exception: ${e.message}", e)
        }
    }

    /**
     * Builds encrypted request body for EAPI.
     */
    private fun buildEncryptedBody(
        path: String,
        params: Map<String, Any>,
        preCookies: Map<String, String>
    ): okhttp3.RequestBody {
        val headerParam = mapOf(
            "clientSign" to (preCookies["clientSign"] ?: ""),
            "osver" to (preCookies["osver"] ?: ""),
            "deviceId" to (preCookies["deviceId"] ?: ""),
            "os" to (preCookies["os"] ?: "pc"),
            "appver" to NeCrypto.APP_VER,
            "requestId" to System.currentTimeMillis().toString()
        )

        val finalParams = params.toMutableMap()
        finalParams["header"] = gson.toJson(headerParam)
        finalParams["e_r"] = true

        val paramsStr = gson.toJson(finalParams)
        val encryptPath = path.replace("/eapi/", "/api/")

        val encrypted = NeCrypto.encryptParams(encryptPath, paramsStr)
        val encryptedHex = NeCrypto.bytesToHexUppercase(encrypted)

        return "params=$encryptedHex".toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    /**
     * Makes an encrypted EAPI request.
     */
    private suspend fun doRequest(path: String, params: Map<String, Any>): String = withContext(Dispatchers.IO) {
        ensureInit()

        val headerParam = mapOf(
            "clientSign" to clientSign,
            "osver" to NeCrypto.OS_VER,
            "deviceId" to deviceId,
            "os" to "pc",
            "appver" to NeCrypto.APP_VER,
            "requestId" to System.currentTimeMillis().toString()
        )

        val finalParams = params.toMutableMap()
        finalParams["header"] = gson.toJson(headerParam)
        finalParams["e_r"] = true

        val paramsStr = gson.toJson(finalParams)
        val encryptPath = path.replace("/eapi/", "/api/")

        val encrypted = NeCrypto.encryptParams(encryptPath, paramsStr)
        val encryptedHex = NeCrypto.bytesToHexUppercase(encrypted)

        val body = "params=$encryptedHex".toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val cookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val headers = NeApi.buildCommonHeaders(cookieStr)

        val fullUrl = "${NeCrypto.EAPI_BASE_URL}$path"
        val response = api.request(fullUrl, headers, body)
        
        val responseBytes = response.body()?.bytes() ?: return@withContext ""
        
        if (responseBytes.isEmpty()) return@withContext ""

        // Decrypt response
        try {
            val decrypted = WangyCrypto.aesEncryptEcbWithPadding(
                responseBytes,
                "e82ckenh8dichen8".toByteArray()
            )
            val result = String(decrypted).let {
                // Remove PKCS7 padding
                val paddingLength = it.last().code
                if (paddingLength in 1..16) {
                    it.substring(0, it.length - paddingLength)
                } else it
            }

            // Check for session invalidation
            if (result.contains("\"code\":301") || result.contains("\"code\":401")) {
                Timber.w(TAG, "Session invalid (code 301/401), clearing cache...")
                isInitialized = false
            }

            result
        } catch (e: Exception) {
            Timber.e(TAG, "Decryption failed: ${e.message}")
            ""
        }
    }

    override suspend fun searchSongs(
        keywords: String,
        page: Int,
        limit: Int
    ): Result<List<OnlineLyricsResult>> = withContext(Dispatchers.IO) {
        try {
            val path = "/eapi/search/song/list/page"
            val offset = (page - 1) * 20

            val params = mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "keyword" to keywords,
                "scene" to "NORMAL",
                "needCorrect" to "true"
            )

            val rawJson = doRequest(path, params)
            Timber.d(TAG, "Search response: ${rawJson.take(500)}")

            if (rawJson.isBlank()) {
                return@withContext Result.failure(Exception("Empty response"))
            }

            val response = gson.fromJson(rawJson, NeSearchResponse::class.java)

            if (response.code != 200) {
                return@withContext Result.failure(Exception("Search failed with code: ${response.code}"))
            }

            val results = response.data?.resources?.mapNotNull { res ->
                if (res.resourceType != 0) return@mapNotNull null
                
                val song = res.baseInfo?.simpleSongData ?: return@mapNotNull null

                OnlineLyricsResult(
                    id = song.id,
                    trackName = song.name,
                    artistName = song.artists.joinToString(", ") { it.name },
                    albumName = song.album?.name,
                    duration = song.duration.toDouble() / 1000.0,
                    hasSyncedLyrics = true, // Will be confirmed when fetching
                    hasPlainLyrics = true,
                    isInstrumental = false,
                    source = "NetEase",
                    sourceKey = song.id.toString(),
                    preview = null
                )
            } ?: emptyList()

            Result.success(results)
        } catch (e: Exception) {
            Timber.e(TAG, "Search exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(songId: Long): Result<Lyrics> = withContext(Dispatchers.IO) {
        try {
            val path = "/eapi/song/lyric/v1"
            val params = mapOf(
                "id" to songId,
                "lv" to "-1",
                "tv" to "-1",
                "rv" to "-1",
                "yv" to "-1"
            )

            val rawJson = doRequest(path, params)

            if (rawJson.isBlank()) {
                return@withContext Result.failure(Exception("Empty lyrics response"))
            }

            val response = gson.fromJson(rawJson, NeLyricResponse::class.java)

            // Parse using YrcParser
            val lyrics = YrcParser.parse(
                yrc = response.yrc?.lyric,
                lrc = response.lrc?.lyric,
                tlyric = response.tlyric?.lyric,
                romalrc = response.romalrc?.lyric
            )

            if (lyrics != null) {
                Result.success(lyrics)
            } else {
                Result.failure(Exception("No lyrics found"))
            }
        } catch (e: Exception) {
            Timber.e(TAG, "Get lyrics exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getAlbumDetail(albumId: Long): Result<WangyAlbumDetail> = withContext(Dispatchers.IO) {
        try {
            val path = "/eapi/v1/album/detail"
            val params = mapOf(
                "id" to albumId.toString()
            )

            val rawJson = doRequest(path, params)
            Timber.d(TAG, "Album detail response: ${rawJson.take(500)}")

            if (rawJson.isBlank()) {
                return@withContext Result.failure(Exception("Empty response"))
            }

            val response = gson.fromJson(rawJson, NeAlbumDetailResponse::class.java)

            if (response.code != 200) {
                return@withContext Result.failure(Exception("Album detail failed with code: ${response.code}"))
            }

            // Convert to WangyAlbumDetail format
            val albumDetail = WangyAlbumDetail(
                code = response.code,
                album = response.data?.let { album ->
                    com.voxly.data.remote.wangy.model.WangyAlbumDetailInfo(
                        id = album.id,
                        name = album.name,
                        artist = album.artist?.let { artist ->
                            com.voxly.data.remote.wangy.model.WangyArtistBasic(
                                id = artist.id,
                                name = artist.name,
                                picUrl = artist.picUrl ?: ""
                            )
                        },
                        company = album.company ?: "",
                        picUrl = album.picUrl ?: "",
                        publishTime = album.publishTime ?: 0,
                        description = album.description ?: "",
                        tags = album.tags ?: "",
                        size = album.size ?: 0
                    )
                },
                songs = response.data?.songs?.map { song ->
                    com.voxly.data.remote.wangy.model.WangyAlbumSong(
                        id = song.id,
                        name = song.name,
                        ar = song.ar?.map { ar ->
                            com.voxly.data.remote.wangy.model.WangyArtistBasic(
                                id = ar.id,
                                name = ar.name,
                                picUrl = ar.picUrl ?: ""
                            )
                        } ?: emptyList(),
                        al = song.al?.let { al ->
                            com.voxly.data.remote.wangy.model.WangyAlbumBasic(
                                id = al.id,
                                name = al.name,
                                picUrl = al.picUrl ?: ""
                            )
                        },
                        dt = song.duration ?: 0,
                        trackNo = song.trackNo ?: 0,
                        cd = song.cd ?: ""
                    )
                } ?: emptyList(),
                privileges = emptyList()
            )

            Result.success(albumDetail)
        } catch (e: Exception) {
            Timber.e(TAG, "Get album detail exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
