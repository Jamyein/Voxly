package com.voxly.data.remote.musicbrainz

import com.voxly.data.helper.SearchQueryBuilder
import com.voxly.data.mapper.OnlineRecordingMapper
import com.voxly.data.remote.NetworkConstants
import com.voxly.data.remote.musicbrainz.model.*
import com.voxly.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Implementation of OnlineMetadataRepository using MusicBrainz API.
 * Provides metadata lookup and cover art fetching capabilities.
 */
@Singleton
class MusicBrainzRepository @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val musicBrainzApi: MusicBrainzApi
) : OnlineMetadataRepository {

    companion object {
        // Cover Art Archive base URL
        const val COVER_ART_ARCHIVE_URL = "https://coverartarchive.org/release/"
        
        // Request rate limiting (MusicBrainz requires 1 second between requests)
        const val MIN_REQUEST_INTERVAL = 1000L

        // Transient TLS/network retry config
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val INITIAL_RETRY_DELAY_MS = 400L
    }

    private var lastRequestTime = 0L

    /**
     * Rate-limited wrapper for API calls.
     */
    private suspend fun <T> rateLimitedCall(call: suspend () -> T): T {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
            kotlinx.coroutines.delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest)
        }
        
        lastRequestTime = System.currentTimeMillis()
        return call()
    }

    private suspend fun <T> callWithTransientRetry(
        operation: String,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var backoffMs = INITIAL_RETRY_DELAY_MS

        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                val shouldRetry = isTransientRetryable(e)
                if (!shouldRetry || attempt >= MAX_TRANSIENT_RETRIES) {
                    throw e
                }

                attempt += 1
                Timber.w(e, "MusicBrainz transient failure on %s, retry %d/%d", operation, attempt, MAX_TRANSIENT_RETRIES)
                delay(backoffMs)
                backoffMs *= 2
            }
        }
    }

    private fun isTransientRetryable(error: Throwable): Boolean {
        if (error is SSLHandshakeException && error.cause is CertificateException) {
            return false
        }
        if (error is SSLPeerUnverifiedException) {
            return false
        }

        if (error is SSLException) {
            val msg = error.message.orEmpty()
            return msg.contains("connection closed", ignoreCase = true) ||
                msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("unexpected end", ignoreCase = true) ||
                msg.contains("handshake", ignoreCase = true)
        }

        if (error is IOException) {
            val msg = error.message.orEmpty()
            return msg.contains("connection closed", ignoreCase = true) ||
                msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true)
        }

        return false
    }

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append("artist:\"").append(artist).append("\"")
                append(" AND ")
                append("release:\"").append(album).append("\"")
            }

            val response = callWithTransientRetry("searchReleases") {
                rateLimitedCall {
                    musicBrainzApi.searchReleases(query = query)
                }
            }

            if (response.isSuccessful) {
                val searchResult = response.body()
                val releases = searchResult?.releaseGroups?.map { releaseGroup ->
                    OnlineRelease(
                        id = releaseGroup.id,
                        title = releaseGroup.title,
                        artist = releaseGroup.getArtistName() ?: artist,
                        year = releaseGroup.getReleaseYear(),
                        format = releaseGroup.primaryType,
                        trackCount = releaseGroup.releases?.firstOrNull()?.let { 0 }, // Will be populated later
                        coverArtUrl = null, // Will be fetched separately
                        source = OnlineSource.MUSICBRAINZ,
                        albumTitle = releaseGroup.title
                    )
                } ?: emptyList()

                Result.success(releases)
            } else {
                Result.failure(Exception("Search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> = withContext(Dispatchers.IO) {
        try {
            // 统一查询格式：title artist (title在前，空格分隔)
            // MusicBrainz 支持简单的文本搜索，会自动匹配相关记录
            val query = SearchQueryBuilder.build(title, artist)

            val response = callWithTransientRetry("searchRecordings") {
                rateLimitedCall {
                    // 使用 recording 端点进行搜索
                    musicBrainzApi.searchRecordings(query = query)
                }
            }

            if (response.isSuccessful) {
                val searchResult = response.body()
                
                // 并发获取封面
                val recordings = searchResult?.recordings?.map { recording ->
                    coroutineScope {
                        val firstRelease = recording.releases?.firstOrNull()
                        val releaseId = firstRelease?.id
                        val releaseTitle = firstRelease?.title  // 专辑名
                        val coverJob = async {
                            releaseId?.let { getCoverArt(it).getOrNull() }
                        }
                        
                        OnlineRecordingMapper.fromMusicBrainz(
                            id = recording.id,
                            title = recording.title,
                            artistName = recording.getArtistName(),
                            durationMs = recording.getDurationMs(),
                            releaseId = releaseId,
                            coverArtBytes = coverJob.await(),
                            album = releaseTitle  // 传递专辑名
                        )
                    }
                } ?: emptyList()

                Result.success(recordings)
            } else {
                Result.failure(Exception("Search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReleaseDetails(
        releaseId: String
    ): Result<OnlineReleaseDetails> = withContext(Dispatchers.IO) {
        try {
            val response = callWithTransientRetry("getReleaseDetails") {
                rateLimitedCall {
                    musicBrainzApi.getReleaseDetails(releaseId = releaseId)
                }
            }

            if (response.isSuccessful) {
                val release = response.body()
                if (release != null) {
                    val details = OnlineReleaseDetails(
                        id = release.id,
                        title = release.title,
                        artist = release.getArtistName() ?: "Unknown",
                        year = release.getReleaseYear(),
                        genre = release.genres?.firstOrNull()?.name,
                        trackCount = release.getAllTracks().size,
                        tracks = release.getAllTracks().map { track ->
                            OnlineTrack(
                                number = track.getTrackNumber() ?: 0,
                                title = track.title,
                                duration = track.getDurationMs()?.toInt(),
                                artist = track.getArtistName()
                            )
                        },
                        coverArtUrl = null // Will be fetched from Cover Art Archive
                    )
                    Result.success(details)
                } else {
                    Result.failure(Exception("Release not found"))
                }
            } else {
                Result.failure(Exception("Failed to get release details: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                // Cover Art Archive API
                val coverArtUrl = "$COVER_ART_ARCHIVE_URL$releaseId/front"
                
                // Make HTTP request to fetch cover art
                val url = java.net.URL(coverArtUrl)
                val connection = url.openConnection()
                connection.setRequestProperty("User-Agent", NetworkConstants.USER_AGENT_APP)
                
                val responseCode = (connection as java.net.HttpURLConnection).responseCode
                
                if (responseCode == 200) {
                    val inputStream = connection.inputStream
                    val bytes = inputStream.readBytes()
                    inputStream.close()
                    Result.success(bytes)
                } else if (responseCode == 404) {
                    // No cover art available
                    Result.success(null)
                } else {
                    Result.failure(Exception("Failed to fetch cover art: HTTP $responseCode"))
                }
            } catch (e: java.io.FileNotFoundException) {
                // Cover art not available
                Result.success(null)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetches comprehensive metadata including cover art for a release.
     * @param releaseId The MusicBrainz release ID
     * @return Result containing complete metadata package
     */
    suspend fun fetchCompleteMetadata(releaseId: String): Result<CompleteMetadata> =
        withContext(Dispatchers.IO) {
            try {
                // Get release details
                val detailsResult = getReleaseDetails(releaseId)
                if (detailsResult.isFailure) {
                    return@withContext Result.failure(
                        detailsResult.exceptionOrNull() ?: Exception("Unknown error")
                    )
                }

                val details = detailsResult.getOrNull()!!

                // Get cover art
                val coverArtResult = getCoverArt(releaseId)
                val coverArt = coverArtResult.getOrNull()

                Result.success(
                    CompleteMetadata(
                        releaseDetails = details,
                        coverArt = coverArt
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Searches for releases using a free text query.
     * @param query Free text search query
     * @param limit Maximum number of results
     * @return Result containing list of releases
     */
    suspend fun searchFreeText(
        query: String,
        limit: Int = 25
    ): Result<List<OnlineRelease>> = withContext(Dispatchers.IO) {
        try {
            val response = callWithTransientRetry("searchFreeText") {
                rateLimitedCall {
                    musicBrainzApi.searchReleases(query = query, limit = limit)
                }
            }

            if (response.isSuccessful) {
                val searchResult = response.body()
                val releases = searchResult?.releaseGroups?.map { releaseGroup ->
                    OnlineRelease(
                        id = releaseGroup.id,
                        title = releaseGroup.title,
                        artist = releaseGroup.getArtistName() ?: "Unknown",
                        year = releaseGroup.getReleaseYear(),
                        format = releaseGroup.primaryType,
                        trackCount = null,
                        coverArtUrl = null,
                        source = OnlineSource.MUSICBRAINZ,
                        albumTitle = releaseGroup.title
                    )
                } ?: emptyList()

                Result.success(releases)
            } else {
                Result.failure(Exception("Search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class containing complete metadata package.
 */
data class CompleteMetadata(
    val releaseDetails: OnlineReleaseDetails,
    val coverArt: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompleteMetadata

        if (releaseDetails != other.releaseDetails) return false
        if (!coverArt.contentEquals(other.coverArt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = releaseDetails.hashCode()
        result = 31 * result + coverArt.contentHashCode()
        return result
    }
}
