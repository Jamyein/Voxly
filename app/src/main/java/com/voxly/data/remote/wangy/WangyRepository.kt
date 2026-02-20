package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val requests: List<suspend () -> retrofit2.Response<WangySearchResponse>> = listOf(
            {
                api.searchSongsCloud(
                    keyword = keywords,
                    offset = offset,
                    limit = normalizedLimit
                )
            },
            {
                api.searchSongsLegacy(
                    keyword = keywords,
                    offset = offset,
                    limit = normalizedLimit
                )
            }
        )

        for (request in requests) {
            try {
                val response = request()
                val body = response.body()
                Timber.d(TAG, "NetEase API response: httpCode=${response.code()} bodyCode=${body?.code ?: -1} songsCount=${body?.result?.songs?.size ?: 0}")
                
                if (response.isSuccessful && body != null && body.code == 200) {
                    if (!body.result?.songs.isNullOrEmpty()) {
                        Timber.d(TAG, "NetEase found ${body.result?.songs?.size} songs for '$keywords'")
                        return@withContext Result.success(body)
                    }
                    if (emptySuccess == null) {
                        emptySuccess = body
                    }
                    failures.add("ok_empty")
                    Timber.w(TAG, "NetEase API returned empty result for '$keywords'")
                    continue
                }
                failures.add("http=${response.code()} bodyCode=${body?.code ?: -1}")
                Timber.w(TAG, "NetEase API failed: http=${response.code()} bodyCode=${body?.code ?: -1}")
            } catch (e: Exception) {
                failures.add(e.message ?: "unknown")
                Timber.e(TAG, "NetEase API exception: ${e.message}", e)
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
}
