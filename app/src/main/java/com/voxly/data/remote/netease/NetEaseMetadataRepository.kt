package com.voxly.data.remote.netease

import android.os.SystemClock

import com.voxly.core.util.Logger
import com.voxly.data.remote.SearchQueryBuilder
import com.voxly.data.remote.mapper.OnlineRecordingMapper
import com.voxly.data.remote.mapper.OnlineRecordingMapper.AlbumInfo
import com.voxly.data.remote.mapper.OnlineRecordingMapper.SingerData
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.remote.NetworkConstants
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.OnlineSourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "NetEaseMetadata"

/**
 * NetEase Cloud Music-specific metadata repository.
 * Wraps WangyRepository with Flow-based streaming support and album/track search logic.
 */
@Singleton
class NetEaseMetadataRepository @Inject constructor(
    private val wangyRepository: WangyRepository
) {
    // Semaphore to limit concurrent detail/lyrics fetching (max 5 concurrent)
    private val detailSemaphore = Semaphore(5)

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
            "NetEase query start type=artist_album artist=$artist album=$album",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = searchNeteaseByArtistAlbum(artist, album, limit)
                    result
                        .onSuccess { releases ->
                            releases.forEach { release ->
                                trySend(OnlineSourceResult.ReleaseResult(release, OnlineSource.NETEASE))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.NETEASE))
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
            "NetEase query start type=track title=$title artist=${artist ?: ""}",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = searchByTrackBlocking(title, artist, limit)
                    result
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.NETEASE))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, error.toUserFriendlyError()))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, e.toUserFriendlyError()))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.NETEASE))
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
                    val result = searchByTrackBlocking(title, artist, limit)
                    result.getOrNull()?.forEach { recording ->
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.NETEASE))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.NETEASE))
                }
            }
        }

        channel.close()
        awaitClose { }
    }

    /**
     * Searches NetEase Cloud Music by artist and album.
     * Uses Simple API (WangyRepository).
     */
    suspend fun searchNeteaseByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val searchResult = wangyRepository.searchSongs(
                keywords = "$artist $album",
                page = 1,
                limit = limit
            )

            searchResult.fold(
                onSuccess = { response ->
                    val songs = response.result?.songs ?: emptyList()
                    if (songs.isEmpty()) {
                        Timber.w(TAG, "NetEase search returned empty results for '$artist $album'")
                        Result.success(emptyList())
                    } else {
                        val albums = songs
                            .filter { it.album?.name != null }
                            .groupBy { it.album?.name ?: "" }
                            .mapNotNull { (albumName, albumSongs) ->
                                if (albumName.isBlank()) return@mapNotNull null
                                val firstSong = albumSongs.first()
                                val songId = firstSong.id.toString()
                                val albumId = firstSong.album?.id
                                val coverUrl = if (albumId != null && albumId > 0) {
                                    getNeteaseAlbumCoverUrl(albumId, firstSong.id)
                                } else null
                                OnlineRelease(
                                    id = songId,
                                    title = albumName,
                                    artist = firstSong.artists.joinToString(", ") { it.name },
                                    year = null,
                                    format = "Digital",
                                    trackCount = albumSongs.size,
                                    coverArtUrl = coverUrl,
                                    source = OnlineSource.NETEASE,
                                    albumTitle = albumName
                                )
                            }
                        Result.success(albums)
                    }
                },
                onFailure = { error ->
                    Timber.e(TAG, "NetEase search failed: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(TAG, "NetEase search exception: ${e.message}", e)
            Result.failure(e)
        }.also { result ->
            Logger.i(
                "Online query source=NetEase type=artist_album elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    /**
     * Searches NetEase by track title.
     */
    /**
     * Searches NetEase by track title (blocking call).
     */
    suspend fun searchByTrackBlocking(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val keywords = SearchQueryBuilder.build(title, artist)
            val searchResult = wangyRepository.searchSongs(
                keywords = keywords,
                page = 1,
                limit = limit
            )

            searchResult.fold(
                onSuccess = { response ->
                    val searchSongs = response.result?.songs ?: emptyList()
                    Timber.d(TAG, "NetEase search returned ${searchSongs.size} songs")
                    if (searchSongs.isEmpty()) {
                        Timber.w(TAG, "NetEase track search returned empty results for '$title' artist='$artist'")
                        Result.success(emptyList())
                    } else {
                        val detailJobs = searchSongs.map { song ->
                            coroutineScope {
                                async {
                                    detailSemaphore.withPermit {
                                        val detailDeferred = async { wangyRepository.getSongDetail(song.id) }
                                        val lyricsDeferred = async { wangyRepository.getLyrics(song.id) }
                                        
                                        val detail = detailDeferred.await().getOrNull()
                                        val lyrics = lyricsDeferred.await().getOrNull()
                                        
                                        Triple(song, detail, lyrics)
                                    }
                                }
                            }
                        }

                        val recordings = detailJobs.mapNotNull { job ->
                            val (searchSong, detail, lyricsResponse) = job.await()
                            val detailSong = detail?.songs?.firstOrNull()
                            val detailAlbum = detailSong?.album ?: detailSong?.al

                            val artistName = detailSong?.artists?.firstOrNull()?.name
                                ?: detailSong?.ar?.firstOrNull()?.name
                                ?: searchSong.artists.firstOrNull()?.name
                                ?: ""
                            val albumName = detailAlbum?.name
                                ?: searchSong.album?.name
                                ?: ""
                            val albumId = detailAlbum?.id
                                ?: searchSong.album?.id

                            val coverUrl = detailAlbum?.picUrl?.takeIf { it.isNotBlank() }
                                ?.let { normalizeCoverUrl(it) }
                                ?: searchSong.album?.picUrl?.takeIf { it.isNotBlank() }
                                    ?.let { normalizeCoverUrl(it) }

                            val lyricsText = lyricsResponse?.lrc?.lyric?.takeIf { it.isNotBlank() }
                            val discNumber = searchSong.disc.toIntOrNull()
                            val trackNumber = searchSong.trackNumber.takeIf { it > 0 }
                            val alias = searchSong.alias.takeIf { it.isNotEmpty() }?.joinToString("; ")

                            OnlineRecording(
                                id = searchSong.id.toString(),
                                title = searchSong.name,
                                artist = artistName,
                                album = albumName,
                                duration = (searchSong.duration / 1000).toInt(),
                                releaseId = albumId?.toString() ?: searchSong.id.toString(),
                                source = OnlineSource.NETEASE,
                                coverArtUrl = coverUrl,
                                discNumber = discNumber,
                                trackNumber = trackNumber,
                                comment = alias,
                                lyrics = lyricsText
                            )
                        }
                        Timber.d(TAG, "NetEase recordings: ${recordings.take(3).map { "${it.title}(${it.coverArtUrl?.take(30)}...)" }}")
                        Result.success(recordings)
                    }
                },
                onFailure = { error ->
                    Timber.e(TAG, "NetEase track search failed: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(TAG, "NetEase track search exception: ${e.message}", e)
            Result.failure(e)
        }.also { result ->
            Logger.i(
                "Online query source=NetEase type=track elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    /**
     * Gets NetEase album cover URL using song detail API.
     */
    private suspend fun getNeteaseAlbumCoverUrl(albumId: Long, songId: Long? = null): String? {
        return try {
            val searchId = songId ?: albumId
            val songDetail = wangyRepository.getSongDetail(searchId)
            songDetail.getOrNull()?.songs?.firstOrNull()?.album?.picUrl?.let { normalizeCoverUrl(it) }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to get NetEase album cover for id=$albumId: ${e.message}")
            null
        }
    }

    /**
     * Gets album details.
     */
    suspend fun getAlbumDetails(albumId: String): Result<OnlineReleaseDetails> {
        return try {
            val result = wangyRepository.getAlbumDetail(albumId.toLong())
            if (result.isSuccess) {
                val album = result.getOrNull()
                Result.success(OnlineReleaseDetails(
                    id = albumId,
                    title = album?.album?.name ?: "Unknown",
                    artist = album?.album?.artists?.firstOrNull()?.name
                        ?: album?.album?.artist?.name
                        ?: "Unknown",
                    year = album?.album?.publishTime?.let { timestamp ->
                        if (timestamp > 0) {
                            try {
                                java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(timestamp))
                                    .toIntOrNull()
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    },
                    genre = album?.album?.tags?.takeIf { it.isNotBlank() },
                    trackCount = album?.songs?.size ?: 0,
                    tracks = album?.songs?.map { song ->
                        com.voxly.domain.repository.OnlineTrack(
                            number = song.position ?: song.trackNo,
                            title = song.name,
                            artist = song.ar.firstOrNull()?.name ?: "",
                            duration = if (song.dt > 0) (song.dt / 1000).toInt() else null
                        )
                    } ?: emptyList(),
                    coverArtUrl = album?.album?.picUrl?.let(::normalizeCoverUrl)
                ))
            } else {
                Result.failure(Exception("Failed to get NetEase album details"))
            }
        } catch (e: Exception) {
            Timber.e(TAG, "NetEase album details failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets cover art bytes from a URL.
     */
    suspend fun getCoverArtBytes(albumId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        try {
            val result = wangyRepository.getAlbumDetail(albumId.toLong())
            if (result.isSuccess) {
                val album = result.getOrNull()
                val coverUrl = normalizeCoverUrl(album?.album?.picUrl)
                if (coverUrl != null) {
                    val bytes = downloadImageBytes(
                        url = coverUrl,
                        userAgent = NetworkConstants.USER_AGENT_ANDROID,
                        referer = "https://music.163.com"
                    )
                    Result.success(bytes)
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Timber.e(TAG, "NetEase cover art failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun normalizeCoverUrl(url: String?): String? {
        val trimmed = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://${trimmed.removePrefix("http://")}"
        } else {
            trimmed
        }
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
