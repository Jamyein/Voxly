package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.crypto.WangyCrypto
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
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

        // 搜索优先级: Simple > Web > EAPI > LinuxAPI
        // Simple搜索无需加密，优先尝试最简单的方案
        val requests: List<suspend () -> retrofit2.Response<WangySearchResponse>> = listOf(
            {
                // 简单网页搜索 (无需加密) - 优先尝试
                api.searchSongsSimple(
                    keyword = keywords,
                    offset = offset,
                    limit = normalizedLimit
                )
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
                // EAPI 加密搜索 - 参考 any-listen-extension
                val searchData = mapOf(
                    "keyword" to keywords,
                    "needCorrect" to "1",
                    "offset" to offset,
                    "limit" to normalizedLimit,
                    "total" to (normalizedPage == 1)
                )
                val encrypted = WangyCrypto.eapiEncrypt("/api/search/song/list/page", searchData)
                val bodyString = "params=${encrypted["params"]}"
                val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                api.searchSongsEapi(requestBody)
            },
            {
                // LinuxAPI 加密搜索 (仅需 AES 加密，最简单稳定)
                // 参考: music-tag-web applications/utils/encrypt.py - linuxEncrypt
                val searchData = mapOf(
                    "s" to keywords,
                    "type" to 1,
                    "offset" to offset,
                    "limit" to normalizedLimit,
                    "total" to true
                )
                val encrypted = WangyCrypto.linuxEncrypt("/api/cloudsearch/get/web", searchData)
                val bodyString = "eparams=${encrypted["eparams"]}"
                val requestBody = bodyString.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                api.searchSongsLinuxApi(requestBody)
            }
        )

        for (request in requests) {
            try {
                val response = request()
                val body = response.body()
                
                Timber.d(TAG, "NetEase API response: httpCode=${response.code()} bodySize=${body?.hashCode()}")
                Timber.d(TAG, "Response headers: ${response.headers()}")
                
                if (response.isSuccessful && body != null && body.code == 200) {
                    if (!body.result?.songs.isNullOrEmpty()) {
                        Timber.d(TAG, "NetEase found ${body.result.songs.size} songs for '$keywords'")
                        return@withContext Result.success(body)
                    }
                    if (emptySuccess == null) {
                        emptySuccess = body
                    }
                    failures.add("ok_empty")
                    Timber.w(TAG, "NetEase API returned empty result for '$keywords'")
                    continue
                }
                
                // Log failure details
                Timber.w(TAG, "NetEase API failed: http=${response.code()} bodyCode=${body?.code ?: -1}")
                
                // Check if it's a redirect or error page
                if (response.code() in 300..399) {
                    failures.add("redirect_${response.code()}")
                    Timber.w(TAG, "Redirect detected, headers: ${response.headers()}")
                } else if (response.code() >= 400) {
                    failures.add("error_${response.code()}")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "unknown"
                // Log specific error types for debugging
                when {
                    errorMsg.contains("converter", ignoreCase = true) -> {
                        Timber.e(TAG, "NetEase converter error (response format mismatch): $errorMsg")
                    }
                    errorMsg.contains("encryption", ignoreCase = true) -> {
                        Timber.e(TAG, "NetEase encryption error: $errorMsg")
                    }
                    else -> {
                        Timber.e(TAG, "NetEase API exception: $errorMsg", e)
                    }
                }
                failures.add(errorMsg)
            }
        }

        emptySuccess?.let { 
            Timber.w(TAG, "NetEase returning empty success for '$keywords'")
            return@withContext Result.success(it) 
        }
        Timber.e(TAG, "NetEase search failed completely for '$keywords': ${failures.joinToString(" | ")}")
        Result.failure(Exception("NetEase search failed: ${failures.joinToString(" | ")}"))
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
