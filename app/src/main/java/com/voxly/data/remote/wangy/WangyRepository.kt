package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.crypto.WangyCrypto
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for WangY Music API operations.
 * Handles encryption and API communication.
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
        try {
            val offset = (page - 1) * limit
            
            // Prepare parameters for EAPI encryption
            val params = mapOf<String, Any>(
                "keyword" to keywords,
                "offset" to offset,
                "limit" to limit,
                "scene" to "normal",
                "channel" to "typing",
                "needCorrect" to "1",
                "total" to true
            )

            // Encrypt using EAPI
            val encryptedParams = WangyCrypto.eapiEncrypt(
                "/api/search/song/list/page",
                params
            )

            // Make API call
            val response = api.searchSongs(encryptedParams = encryptedParams)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSongDetail(songId: Long): Result<WangySongDetail> = withContext(Dispatchers.IO) {
        try {
            // Prepare parameters for WeAPI encryption
            val params = mapOf<String, Any>(
                "c" to "[{\"id\":$songId}]",
                "ids" to "[$songId]"
            )

            // Encrypt using WeAPI
            val encryptedParams = WangyCrypto.weapiEncrypt(params)

            // Make API call
            val response = api.getSongDetail(encryptedParams = encryptedParams)

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
            // Prepare parameters for EAPI encryption
            val params = mapOf<String, Any>(
                "id" to songId,
                "cp" to false,
                "tv" to 0,
                "lv" to 0,
                "rv" to 0,
                "kv" to 0,
                "yv" to 0,
                "ytv" to 0,
                "rvk" to 0,
                "ytk" to 0
            )

            // Encrypt using EAPI
            val encryptedParams = WangyCrypto.eapiEncrypt(
                "/api/song/lyric/v1",
                params
            )

            // Make API call
            val response = api.getLyrics(encryptedParams = encryptedParams)

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
            // Prepare parameters for WeAPI encryption
            val params = mapOf<String, Any>(
                "id" to albumId
            )

            // Encrypt using WeAPI
            val encryptedParams = WangyCrypto.weapiEncrypt(params)

            // Make API call
            val response = api.getAlbumDetail(encryptedParams = encryptedParams)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Get album detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
