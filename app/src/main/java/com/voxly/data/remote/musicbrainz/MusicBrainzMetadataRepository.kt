package com.voxly.data.remote.musicbrainz

import android.os.SystemClock

import timber.log.Timber
import com.voxly.data.remote.mapper.OnlineRecordingMapper
import com.voxly.data.remote.mapper.OnlineRecordingMapper.AlbumInfo
import com.voxly.data.remote.mapper.OnlineRecordingMapper.SingerData
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.OnlineSourceResult
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.remote.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "MusicBrainzMetadata"

/**
 * MusicBrainz-specific metadata repository.
 * Wraps MusicBrainzRepository with Flow-based streaming support and settings integration.
 */
@Singleton
class MusicBrainzMetadataRepository @Inject constructor(
    private val musicBrainzRepository: MusicBrainzRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val detailSemaphore = kotlinx.coroutines.sync.Semaphore(5)

    /**
     * Searches releases by artist and album with Flow-based streaming.
     */
    fun searchByArtistAlbumFlow(
        artist: String,
        album: String,
        limit: Int
    ): Flow<OnlineSourceResult> = callbackFlow {
        val startedAt = SystemClock.elapsedRealtime()
        Timber.i(
            "MusicBrainz query start type=artist_album artist=$artist album=$album",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = musicBrainzRepository.searchByArtistAlbum(artist, album)
                    result
                        .map { applyLimit(it, limit) }
                        .onSuccess { releases ->
                            releases.forEach { release ->
                                trySend(OnlineSourceResult.ReleaseResult(release, OnlineSource.MUSICBRAINZ))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.MUSICBRAINZ))
                }
            }
        }

        channel.close()
        awaitClose { }
    }

    /**
     * Searches recordings by track title and artist with Flow-based streaming.
     */
    fun searchByTrackFlow(
        title: String,
        artist: String?,
        limit: Int
    ): Flow<OnlineSourceResult> = callbackFlow {
        val startedAt = SystemClock.elapsedRealtime()
        Timber.i(
            "MusicBrainz query start type=track title=$title artist=${artist ?: ""}",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = musicBrainzRepository.searchByTrack(title, artist)
                    result
                        .map { applyLimit(it, limit) }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.MUSICBRAINZ))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, error.toUserFriendlyError()))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, e.toUserFriendlyError()))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.MUSICBRAINZ))
                }
            }
        }

        channel.close()
        awaitClose { }
    }

    /**
     * Searches for recordings with cover art (for cover search) with Flow-based streaming.
     */
    fun searchByTrackForCoverFlow(
        title: String,
        artist: String?,
        limit: Int
    ): Flow<OnlineSourceResult> = callbackFlow {
        supervisorScope {
            launch {
                try {
                    val result = musicBrainzRepository.searchByTrack(title, artist)
                    result.getOrNull()?.forEach { recording ->
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.MUSICBRAINZ))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.MUSICBRAINZ, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.MUSICBRAINZ))
                }
            }
        }

        channel.close()
        awaitClose { }
    }

    /**
     * Gets detailed metadata for a specific release.
     */
    suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return musicBrainzRepository.getReleaseDetails(releaseId)
    }

    /**
     * Gets cover art for a release.
     */
    suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return musicBrainzRepository.getCoverArt(releaseId)
    }

    /**
     * Gets cover art bytes from a URL.
     */
    suspend fun getCoverArtBytes(releaseId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        try {
            val coverArtUrl = "${MusicBrainzRepository.COVER_ART_ARCHIVE_URL}$releaseId/front"
            val bytes = downloadImageBytes(
                url = coverArtUrl,
                userAgent = NetworkConstants.USER_AGENT_ANDROID,
                referer = "https://musicbrainz.org"
            )
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> applyLimit(list: List<T>, limit: Int): List<T> {
        return if (limit <= 0) list else list.take(limit)
    }

    private fun Throwable.toUserFriendlyError(): String {
        val message = this.message ?: "Unknown error"
        
        if (this is SSLException || 
            message.contains("chain validation failed", ignoreCase = true) ||
            message.contains("SSL", ignoreCase = true) &&
            (message.contains("validation", ignoreCase = true) || 
             message.contains("certificate", ignoreCase = true))
        ) {
            return "网络连接失败：SSL 证书验证错误。请检查设备日期/时间设置，或联系网络管理员。"
        }
        
        if (this is UnknownHostException || message.contains("Unable to resolve host", ignoreCase = true)) {
            return "网络连接失败：无法解析服务器地址。请检查网络连接。"
        }
        
        if (message.contains("timeout", ignoreCase = true)) {
            return "网络连接超时：请检查网络连接后重试。"
        }
        
        if (message.contains("Connection refused", ignoreCase = true)) {
            return "网络连接被拒绝：请稍后重试。"
        }
        
        return message
    }
}
