package com.voxly.data.remote.itunes

import android.os.SystemClock

import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.OnlineSourceResult
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
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "ItunesMetadata"

/**
 * iTunes/Apple Music-specific metadata repository.
 * Wraps ITunesRepository with Flow-based streaming support and settings integration.
 */
@Singleton
class ItunesMetadataRepository @Inject constructor(
    private val iTunesRepository: ITunesRepository,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * Searches releases by artist and album with Flow-based streaming.
     */
    fun searchByArtistAlbumFlow(
        artist: String,
        album: String,
        limit: Int
    ): Flow<OnlineSourceResult> = callbackFlow {
        val startedAt = SystemClock.elapsedRealtime()
        Logger.i(
            "iTunes query start type=artist_album artist=$artist album=$album",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = iTunesRepository.searchByArtistAlbum(artist, album)
                    result
                        .map { applyLimit(it, limit) }
                        .onSuccess { releases ->
                            releases.forEach { release ->
                                trySend(OnlineSourceResult.ReleaseResult(release, OnlineSource.ITUNES))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.ITUNES))
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
        Logger.i(
            "iTunes query start type=track title=$title artist=${artist ?: ""}",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = iTunesRepository.searchByTrack(title, artist)
                    Timber.d("iTunes raw results count: ${result.getOrNull()?.size ?: 0}")
                    result
                        .map { applyLimit(it, limit) }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.ITUNES))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, error.toUserFriendlyError()))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, e.toUserFriendlyError()))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.ITUNES))
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
                    val result = iTunesRepository.searchByTrack(title, artist)
                    result.getOrNull()?.forEach { recording ->
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.ITUNES))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.ITUNES, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.ITUNES))
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
        return iTunesRepository.getReleaseDetails(releaseId)
    }

    /**
     * Gets cover art bytes from a URL.
     */
    suspend fun getCoverArtBytes(releaseId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        iTunesRepository.getCoverArt(releaseId)
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
