package com.voxly.data.remote.tengx

import android.util.Base64
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchResponse
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
            // Call API directly with parameters (no complex JSON body needed)
            val response = api.search(
                keyword = keywords,
                page = pageNum + 1,  // API uses 1-based indexing
                perPage = pageSize
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("QQ Music search failed: ${response.code()}"))
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
}
