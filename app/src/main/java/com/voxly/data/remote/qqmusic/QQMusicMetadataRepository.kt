package com.voxly.data.remote.qqmusic

import android.os.SystemClock

import timber.log.Timber
import com.voxly.data.remote.SearchQueryBuilder
import com.voxly.data.remote.mapper.OnlineRecordingMapper
import com.voxly.data.remote.mapper.OnlineRecordingMapper.AlbumInfo
import com.voxly.data.remote.mapper.OnlineRecordingMapper.SingerData
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxAlbumDetailData
import com.voxly.data.remote.tengx.model.TengxAlbumDetailInfo
import com.voxly.data.remote.tengx.model.TengxSong
import com.voxly.data.remote.tengx.model.TengxSinger
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
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "QQMusicMetadata"

/**
 * QQ Music-specific metadata repository.
 * Wraps TengxRepository with Flow-based streaming support and album/track search logic.
 */
@Singleton
class QQMusicMetadataRepository @Inject constructor(
    private val tengxRepository: TengxRepository
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
        Timber.i(
            "QQ Music query start type=artist_album artist=$artist album=$album",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = searchQQMusicByArtistAlbum(artist, album, limit)
                    result
                        .onSuccess { releases ->
                            releases.forEach { release ->
                                trySend(OnlineSourceResult.ReleaseResult(release, OnlineSource.QQ_MUSIC))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.QQ_MUSIC))
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
            "QQ Music query start type=track title=$title artist=${artist ?: ""}",
            TAG
        )

        supervisorScope {
            launch {
                try {
                    val result = searchByTrackBlocking(title, artist, limit)
                    result
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.QQ_MUSIC))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, error.toUserFriendlyError()))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, e.toUserFriendlyError()))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.QQ_MUSIC))
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
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.QQ_MUSIC))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted(OnlineSource.QQ_MUSIC))
                }
            }
        }

        channel.close()
        awaitClose { }
    }

    /**
     * Searches QQ Music by artist and album.
     */
    suspend fun searchQQMusicByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val searchResult = tengxRepository.searchSongs(
                keywords = "$artist $album",
                pageNum = 1,
                pageSize = limit
            )

            searchResult.fold(
                onSuccess = { response ->
                    val songs = response.data?.song?.list ?: emptyList()
                    
                    if (songs.isEmpty()) {
                        Timber.w(TAG, "QQ Music search returned empty results for '$artist $album'")
                        Result.success(emptyList())
                    } else {
                        val albums = songs.groupBy { it.album?.id }.mapNotNull { (albumId, albumSongs) ->
                            albumId?.let { id ->
                                val firstSong = albumSongs.first()
                                OnlineRelease(
                                    id = id.toString(),
                                    title = firstSong.album?.name ?: "Unknown Album",
                                    artist = firstSong.singer.joinToString(", ") { singer -> singer.name },
                                    year = null,
                                    format = "Digital",
                                    trackCount = albumSongs.size,
                                    coverArtUrl = buildQQCoverUrl(
                                        albumMid = firstSong.album?.mid,
                                        rawCoverUrl = firstSong.album?.pic,
                                        fallbackId = id.toString()
                                    ),
                                    source = OnlineSource.QQ_MUSIC,
                                    albumTitle = firstSong.album?.name
                                )
                            }
                        }
                        Result.success(albums)
                    }
                },
                onFailure = { error ->
                    Timber.e(TAG, "QQ Music search failed: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music search exception: ${e.message}", e)
            Result.failure(e)
        }.also { result ->
            Timber.i(
                "Online query source=QQ_Music type=artist_album elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    /**
     * Searches QQ Music by track title.
     */
    /**
     * Searches QQ Music by track title (blocking call).
     */
    suspend fun searchByTrackBlocking(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val keywords = SearchQueryBuilder.build(title, artist)
            
            val searchResult = tengxRepository.searchSongs(
                keywords = keywords,
                pageNum = 1,
                pageSize = limit
            )

            searchResult.fold(
                onSuccess = { response ->
                    val songs = response.data?.song?.list ?: emptyList()
                    
                    if (songs.isEmpty()) {
                        Timber.w(TAG, "QQ Music track search returned empty results for '$title' artist='$artist'")
                        Result.success(emptyList())
                    } else {
                        val detailJobs = songs.map { song ->
                            coroutineScope {
                                async {
                                    detailSemaphore.withPermit {
                                        val detailDeferred = async {
                                            if (song.id > 0) {
                                                tengxRepository.getSongDetail(listOf(song.id))
                                            } else {
                                                Result.failure(Exception("Invalid song id"))
                                            }
                                        }
                                        val lyricsDeferred = async {
                                            if (song.mid.isNotBlank()) {
                                                tengxRepository.getLyrics(song.mid)
                                            } else {
                                                Result.failure(Exception("Invalid song mid"))
                                            }
                                        }

                                        val detail = detailDeferred.await().getOrNull()
                                        val lyricsResult = lyricsDeferred.await().getOrNull()

                                        Triple(song, detail, lyricsResult)
                                    }
                                }
                            }
                        }

                        val recordings = detailJobs.mapNotNull { job ->
                            val (searchSong, detailResponse, lyricsResult) = job.await()
                            
                            val detailSong = detailResponse?.data?.track?.firstOrNull()
                            
                            val albumInfo = searchSong.album?.let { album ->
                                AlbumInfo(
                                    id = album.id,
                                    mid = album.mid,
                                    name = album.name,
                                    pic = album.pic
                                )
                            } ?: detailSong?.album?.let { album ->
                                AlbumInfo(
                                    id = album.id,
                                    mid = album.mid,
                                    name = album.name,
                                    pic = album.pic
                                )
                            }
                            
                            val lyricsText = lyricsResult?.lyrics?.takeIf { it.isNotBlank() }
                            
                            OnlineRecordingMapper.fromQQMusic(
                                id = searchSong.id.toInt(),
                                name = searchSong.name,
                                singers = searchSong.singer.map { SingerData(it.name) },
                                interval = searchSong.interval,
                                album = albumInfo,
                                lyrics = lyricsText
                            )
                        }
                        Result.success(recordings)
                    }
                },
                onFailure = { error ->
                    Timber.e(TAG, "QQ Music track search failed: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music track search exception: ${e.message}", e)
            Result.failure(e)
        }.also { result ->
            Timber.i(
                "Online query source=QQ_Music type=track elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    /**
     * Gets QQ Music album details.
     */
    suspend fun getAlbumDetails(albumId: String): Result<OnlineReleaseDetails> {
        return try {
            val numericAlbumId = albumId.toLongOrNull()
            
            val result = if (numericAlbumId != null && numericAlbumId > 0) {
                tengxRepository.getAlbumDetail(numericAlbumId)
            } else {
                searchQQMusicSongByMid(albumId)
            }
            
            if (result.isSuccess) {
                val album = result.getOrNull()
                Result.success(OnlineReleaseDetails(
                    id = albumId,
                    title = album?.data?.album?.name ?: "Unknown",
                    artist = album?.data?.album?.singer?.name ?: "Unknown",
                    year = null,
                    genre = null,
                    trackCount = album?.data?.list?.size ?: 0,
                    tracks = emptyList(),
                    coverArtUrl = if (albumId.isNotEmpty()) {
                        buildQQCoverUrl(
                            albumMid = album?.data?.album?.mid,
                            rawCoverUrl = album?.data?.album?.pic,
                            fallbackId = albumId
                        )
                    } else null
                ))
            } else {
                Result.failure(Exception("Failed to get QQ Music album details"))
            }
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music album details failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Searches for QQ Music song by song mid as fallback.
     */
    private suspend fun searchQQMusicSongByMid(songMid: String): Result<TengxAlbumDetail> {
        return try {
            val searchResult = tengxRepository.searchSongs(
                keywords = songMid,
                pageNum = 1,
                pageSize = 1,
                type = 0
            )
            
            searchResult.fold(
                onSuccess = { response ->
                    val song = response.data?.song?.list?.firstOrNull()
                    if (song != null) {
                        val albumId = song.album?.id
                        if (albumId != null && albumId > 0) {
                            tengxRepository.getAlbumDetail(albumId)
                        } else if (!song.album?.mid.isNullOrBlank()) {
                            Result.success(createBasicAlbumDetail(song))
                        } else {
                            Result.failure(Exception("No album info available for song: ${song.name}"))
                        }
                    } else {
                        Result.failure(Exception("Song not found by mid: $songMid"))
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a basic album detail from song info when album API is unavailable.
     */
    private fun createBasicAlbumDetail(song: TengxSong): TengxAlbumDetail {
        return TengxAlbumDetail(
            code = 0,
            data = TengxAlbumDetailData(
                album = TengxAlbumDetailInfo(
                    id = song.album?.id ?: 0,
                    mid = song.album?.mid ?: "",
                    name = song.album?.name ?: "Unknown Album",
                    singer = TengxSinger(
                        id = song.singer.firstOrNull()?.id ?: 0,
                        name = song.singer.firstOrNull()?.name ?: "Unknown Artist",
                        title = "",
                        type = 0,
                        gender = 0,
                        pic = ""
                    ),
                    pic = song.album?.pic ?: "",
                    publicTime = ""
                ),
                list = emptyList()
            )
        )
    }

    /**
     * Gets cover art bytes from a URL.
     */
    suspend fun getCoverArtBytes(albumId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        try {
            val coverUrl = buildQQCoverUrl(
                albumMid = albumId.takeUnless { it.all(Char::isDigit) } ?: "",
                rawCoverUrl = null,
                fallbackId = albumId.takeIf { it.all(Char::isDigit) }
            ) ?: return@withContext Result.success(null)
            val bytes = downloadImageBytes(
                url = coverUrl,
                userAgent = NetworkConstants.USER_AGENT_ANDROID,
                referer = "https://y.qq.com"
            )
            Result.success(bytes)
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music cover art failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildQQCoverUrl(
        albumMid: String?,
        rawCoverUrl: String?,
        fallbackId: String?
    ): String? {
        val raw = rawCoverUrl?.takeIf { it.isNotBlank() }
        val normalizedRaw = normalizeCoverUrl(raw)
        if (!normalizedRaw.isNullOrBlank()) return normalizedRaw

        val mid = albumMid?.trim().orEmpty()
        if (mid.isNotBlank()) {
            return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${mid}.jpg"
        }

        val id = fallbackId?.trim().orEmpty()
        if (id.isNotBlank()) {
            return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${id}.jpg"
        }
        return null
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
