package com.voxly.data.remote.wangy

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

            // Make API call directly with parameters (no encryption needed)
            val response = api.searchSongs(
                keyword = keywords,
                offset = offset,
                limit = limit
            )

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
}
