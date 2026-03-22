package com.voxly.data.repository

import android.icu.text.Transliterator

import android.os.SystemClock

import com.voxly.core.util.Logger
import com.voxly.data.helper.SearchQueryBuilder
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.mapper.OnlineRecordingMapper
import com.voxly.data.mapper.OnlineRecordingMapper.AlbumData
import com.voxly.data.mapper.OnlineRecordingMapper.AlbumInfo
import com.voxly.data.mapper.OnlineRecordingMapper.ArtistData
import com.voxly.data.mapper.OnlineRecordingMapper.SingerData
import com.voxly.data.remote.NetworkConstants
import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.remote.itunes.ITunesRepository
import com.voxly.data.remote.musicbrainz.MusicBrainzRepository
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxAlbumDetailData
import com.voxly.data.remote.tengx.model.TengxAlbumDetailInfo
import com.voxly.data.remote.tengx.model.TengxSong
import com.voxly.data.remote.tengx.model.TengxSinger
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "AggregatedMetadata"

/**
 * Streaming search result with payload and source marker.
 */
public sealed class OnlineSourceResult {
    public data class ReleaseResult(
        val release: OnlineRelease,
        val source: OnlineSource
    ) : OnlineSourceResult()

    public data class RecordingResult(
        val recording: OnlineRecording,
        val source: OnlineSource
    ) : OnlineSourceResult()

    public data class SourceCompleted(val source: OnlineSource) : OnlineSourceResult()

    public data class Error(val source: OnlineSource, val message: String) : OnlineSourceResult()
}

/**
 * Aggregated repository that combines multiple online metadata sources.
 * Supports MusicBrainz, iTunes/Apple Music, NetEase Cloud Music, and QQ Music.
 * 
 * This repository queries all available sources and merges the results,
 * giving users the best metadata from multiple providers.
 */
@Singleton
class AggregatedOnlineMetadataRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val musicBrainzRepository: MusicBrainzRepository,
    private val iTunesRepository: ITunesRepository,
    private val wangyRepository: WangyRepository,
    private val tengxRepository: TengxRepository
) : OnlineMetadataRepository {

    // Semaphore to limit concurrent detail/lyrics fetching (max 5 concurrent)
    private val detailSemaphore = Semaphore(5)

    /**
     * Data source preference for metadata lookup.
     */
    enum class DataSource {
        MUSICBRAINZ,
        ITUNES,
        NETEASE,
        QQ_MUSIC,
        BOTH
    }

    /**
     * Current preferred data source.
     */
    var preferredSource: DataSource = DataSource.BOTH

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        val requestStartedAt = SystemClock.elapsedRealtime()
        Logger.i(
            "Online query start type=artist_album artist=$artist album=$album source=$preferredSource",
            TAG
        )
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            Logger.w("Online query rejected: no metadata source enabled", TAG)
            return Result.failure(Exception("No metadata sources enabled"))
        }
        val result = when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.searchByArtistAlbum(artist, album)
                        .map {
                            finalizeReleaseResults(
                                releases = it,
                                artist = artist,
                                album = album,
                                settings = settings
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.ITUNES -> {
                if (settings.enableITunes) {
                    iTunesRepository.searchByArtistAlbum(artist, album)
                        .map {
                            finalizeReleaseResults(
                                releases = it,
                                artist = artist,
                                album = album,
                                settings = settings
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.NETEASE -> {
                if (settings.enableNetease) {
                    searchNeteaseByArtistAlbum(artist, album, settings.requestLimit)
                        .map {
                            finalizeReleaseResults(
                                releases = it,
                                artist = artist,
                                album = album,
                                settings = settings
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.QQ_MUSIC -> {
                if (settings.enableQQMusic) {
                    searchQQMusicByArtistAlbum(artist, album, settings.requestLimit)
                        .map {
                            finalizeReleaseResults(
                                releases = it,
                                artist = artist,
                                album = album,
                                settings = settings
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.BOTH -> searchAllSources(artist, album, settings)
        }
        Logger.i(
            "Online query end type=artist_album elapsedMs=${SystemClock.elapsedRealtime() - requestStartedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
            TAG
        )
        return result
    }

    fun searchByArtistAlbumFlow(
        artist: String,
        album: String
    ): Flow<OnlineSourceResult> = callbackFlow {
        val settings = getOnlineSourceSettings()

        val useITunes = settings.enableITunes && (preferredSource == DataSource.ITUNES || preferredSource == DataSource.BOTH)
        val useQQMusic = settings.enableQQMusic && (preferredSource == DataSource.QQ_MUSIC || preferredSource == DataSource.BOTH)
        val useNetease = settings.enableNetease && (preferredSource == DataSource.NETEASE || preferredSource == DataSource.BOTH)
        val useMusicBrainz = settings.enableMusicBrainz && (preferredSource == DataSource.MUSICBRAINZ || preferredSource == DataSource.BOTH)

        if (!useITunes && !useQQMusic && !useNetease && !useMusicBrainz) {
            trySend(OnlineSourceResult.Error(OnlineSource.UNKNOWN, "No metadata sources enabled"))
            channel.close()
            return@callbackFlow
        }

        // Use supervisorScope so each source fails independently - one failed source doesn't cancel others
        supervisorScope {
            if (useITunes) {
                launch {
                    try {
                        val result = iTunesRepository.searchByArtistAlbum(artist, album)
                        result
                            .map {
                                finalizeReleaseResults(
                                    releases = it,
                                    artist = artist,
                                    album = album,
                                    settings = settings
                                )
                            }
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

            if (useQQMusic) {
                launch {
                    try {
                        val result = searchQQMusicByArtistAlbum(artist, album, settings.requestLimit)
                        result
                            .map {
                                finalizeReleaseResults(
                                    releases = it,
                                    artist = artist,
                                    album = album,
                                    settings = settings
                                )
                            }
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

            if (useNetease) {
                launch {
                    try {
                        val result = searchNeteaseByArtistAlbum(artist, album, settings.requestLimit)
                        result
                            .map {
                                finalizeReleaseResults(
                                    releases = it,
                                    artist = artist,
                                    album = album,
                                    settings = settings
                                )
                            }
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

            if (useMusicBrainz) {
                launch {
                    try {
                        val result = musicBrainzRepository.searchByArtistAlbum(artist, album)
                        result
                            .map {
                                finalizeReleaseResults(
                                    releases = it,
                                    artist = artist,
                                    album = album,
                                    settings = settings
                                )
                            }
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
        }

        // supervisorScope completes when all child coroutines finish
        channel.close()

        awaitClose {
            // No explicit cleanup needed - supervisorScope children are cancelled automatically
            // when the parent callbackFlow scope is cancelled
        }
    }

    /**
     * Searches all sources concurrently and merges results.
     */
    private suspend fun searchAllSources(
        artist: String,
        album: String,
        settings: OnlineSourceSettings
    ): Result<List<OnlineRelease>> = coroutineScope {
        val musicBrainzDeferred = if (settings.enableMusicBrainz) {
            async { musicBrainzRepository.searchByArtistAlbum(artist, album) }
        } else null
        val iTunesDeferred = if (settings.enableITunes) {
            async { iTunesRepository.searchByArtistAlbum(artist, album) }
        } else null
        val neteaseDeferred = if (settings.enableNetease) {
            async { searchNeteaseByArtistAlbum(artist, album, settings.requestLimit) }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { searchQQMusicByArtistAlbum(artist, album, settings.requestLimit) }
        } else null

        val musicBrainzResult = musicBrainzDeferred?.await()?.map { applyLimit(it, settings.searchLimit) }
        val iTunesResult = iTunesDeferred?.await()?.map { applyLimit(it, settings.searchLimit) }
        val neteaseResult = neteaseDeferred?.await()
        val qqMusicResult = qqMusicDeferred?.await()

        // Merge results
        val mergedResults = mutableListOf<OnlineRelease>()
        
        // Add MusicBrainz results first
        if (musicBrainzResult?.isSuccess == true) {
            mergedResults.addAll(musicBrainzResult.getOrNull() ?: emptyList())
        }

        // Add iTunes results
        if (iTunesResult?.isSuccess == true) {
            val iTunesReleases = iTunesResult.getOrNull() ?: emptyList()
            iTunesReleases.forEach { release ->
                val isDuplicate = mergedResults.any { existing ->
                    isSimilarRelease(existing, release)
                }
                if (!isDuplicate) {
                    mergedResults.add(release)
                }
            }
        }

        // Add NetEase results (for Chinese music)
        if (neteaseResult?.isSuccess == true) {
            val neteaseReleases = neteaseResult.getOrNull() ?: emptyList()
            neteaseReleases.forEach { release ->
                val isDuplicate = mergedResults.any { existing ->
                    isSimilarRelease(existing, release)
                }
                if (!isDuplicate) {
                    mergedResults.add(release)
                }
            }
        }

        // Add QQ Music results (for Chinese music)
        if (qqMusicResult?.isSuccess == true) {
            val qqReleases = qqMusicResult.getOrNull() ?: emptyList()
            qqReleases.forEach { release ->
                val isDuplicate = mergedResults.any { existing ->
                    isSimilarRelease(existing, release)
                }
                if (!isDuplicate) {
                    mergedResults.add(release)
                }
            }
        }

        // Sort by user-defined priority (list order: first = highest priority)
        // Use sourcePriorityIndex function to properly map "QQ Music" -> "qq_music"
        val sortedResults = mergedResults.sortedWith(compareBy<OnlineRelease> { release ->
            sourcePriorityIndex(release.source, settings.metadataPriority)
        })

        val finalizedResults = finalizeReleaseResults(
            releases = sortedResults,
            artist = artist,
            album = album,
            settings = settings
        )
        Result.success(finalizedResults)
    }

    /**
     * Searches NetEase Cloud Music by artist and album.
     * Uses Simple API (WangyRepository).
     */
    private suspend fun searchNeteaseByArtistAlbum(
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
                        // API returned success but no data - log warning
                        Timber.w(TAG, "NetEase search returned empty results for '$artist $album'")
                        Result.success(emptyList())
                    } else {
                        // Group by album - try to get cover from search result first
                        val albums = songs
                            .filter { it.album?.name != null }
                            .groupBy { it.album?.name ?: "" }
                            .mapNotNull { (albumName, albumSongs) ->
                                if (albumName.isBlank()) return@mapNotNull null
                                val firstSong = albumSongs.first()
                                val songId = firstSong.id.toString()
                                val albumId = firstSong.album?.id
                                // Use song detail API which returns album.picUrl directly
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
                    // Log the actual error instead of returning empty success
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
     * Searches QQ Music by artist and album.
     */
    private suspend fun searchQQMusicByArtistAlbum(
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
                        // Group by album
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
            Logger.i(
                "Online query source=QQ_Music type=artist_album elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> {
        val requestStartedAt = SystemClock.elapsedRealtime()
        Logger.i(
            "Online query start type=track title=$title artist=${artist ?: ""} source=$preferredSource",
            TAG
        )
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            Logger.w("Online query rejected: no metadata source enabled", TAG)
            return Result.failure(Exception("No metadata sources enabled"))
        }
        val result = when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.searchByTrack(title, artist)
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.ITUNES -> {
                if (settings.enableITunes) {
                    iTunesRepository.searchByTrack(title, artist)
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.NETEASE -> {
                if (settings.enableNetease) {
                    searchNeteaseByTrack(title, artist, settings.requestLimit)
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.QQ_MUSIC -> {
                if (settings.enableQQMusic) {
                    searchQQMusicByTrack(title, artist, settings.requestLimit)
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.BOTH -> {
                coroutineScope {
                    val musicBrainzDeferred = if (settings.enableMusicBrainz) {
                        async { musicBrainzRepository.searchByTrack(title, artist) }
                    } else null
                    val iTunesDeferred = if (settings.enableITunes) {
                        async { iTunesRepository.searchByTrack(title, artist) }
                    } else null
                    val neteaseDeferred = if (settings.enableNetease) {
                        async { searchNeteaseByTrack(title, artist, settings.requestLimit) }
                    } else null
                    val qqMusicDeferred = if (settings.enableQQMusic) {
                        async { searchQQMusicByTrack(title, artist, settings.requestLimit) }
                    } else null

                    val results = mutableListOf<OnlineRecording>()
                    
                    musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.getSourceLimit("MusicBrainz"))) }
                    iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.getSourceLimit("iTunes"))) }
                    neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.getSourceLimit("NetEase"))) }
                    qqMusicDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.getSourceLimit("QQ Music"))) }

                    val sorted = finalizeRecordingResults(
                        recordings = results,
                        title = title,
                        artist = artist,
                        priority = settings.metadataPriority,
                        limit = settings.searchLimit
                    )
                    Result.success(sorted)
                }
            }
        }
        Logger.i(
            "Online query end type=track elapsedMs=${SystemClock.elapsedRealtime() - requestStartedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
            TAG
        )
        return result
    }

    fun searchByTrackFlow(
        title: String,
        artist: String?
    ): Flow<OnlineSourceResult> = callbackFlow {
        val settings = getOnlineSourceSettings()
        Timber.d("searchByTrackFlow: title='$title', artist='$artist'")

        val useITunes = settings.enableITunes && (preferredSource == DataSource.ITUNES || preferredSource == DataSource.BOTH)
        val useQQMusic = settings.enableQQMusic && (preferredSource == DataSource.QQ_MUSIC || preferredSource == DataSource.BOTH)
        val useNetease = settings.enableNetease && (preferredSource == DataSource.NETEASE || preferredSource == DataSource.BOTH)
        val useMusicBrainz = settings.enableMusicBrainz && (preferredSource == DataSource.MUSICBRAINZ || preferredSource == DataSource.BOTH)

        if (!useITunes && !useQQMusic && !useNetease && !useMusicBrainz) {
            trySend(OnlineSourceResult.Error(OnlineSource.UNKNOWN, "No metadata sources enabled"))
            channel.close()
            return@callbackFlow
        }

        // Use supervisorScope so each source fails independently - one failed source doesn't cancel others
        supervisorScope {
            if (useITunes) {
                launch {
                    try {
                        val result = iTunesRepository.searchByTrack(title, artist)
                        Timber.d("iTunes raw results count: ${result.getOrNull()?.size ?: 0}")
                        result
                            .map {
                                finalizeRecordingResults(
                                    recordings = it,
                                    title = title,
                                    artist = artist,
                                    priority = settings.metadataPriority,
                                    limit = settings.getSourceLimit("iTunes")
                                )
                            }
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

            if (useQQMusic) {
                launch {
                    try {
                        val result = searchQQMusicByTrack(title, artist, settings.requestLimit)
                        result
                            .map {
                                finalizeRecordingResults(
                                    recordings = it,
                                    title = title,
                                    artist = artist,
                                    priority = settings.metadataPriority,
                                    limit = settings.getSourceLimit("QQ Music")
                                )
                            }
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

            if (useNetease) {
                launch {
                    try {
                        val result = searchNeteaseByTrack(title, artist, settings.requestLimit)
                        result
                            .map {
                                finalizeRecordingResults(
                                    recordings = it,
                                    title = title,
                                    artist = artist,
                                    priority = settings.metadataPriority,
                                    limit = settings.getSourceLimit("NetEase")
                                )
                            }
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

            if (useMusicBrainz) {
                launch {
                    try {
                        val result = musicBrainzRepository.searchByTrack(title, artist)
                        result
                            .map {
                                finalizeRecordingResults(
                                    recordings = it,
                                    title = title,
                                    artist = artist,
                                    priority = settings.metadataPriority,
                                    limit = settings.getSourceLimit("MusicBrainz")
                                )
                            }
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
        }

        // supervisorScope completes when all child coroutines finish
        channel.close()

        awaitClose {
            // No explicit cleanup needed - supervisorScope children are cancelled automatically
            // when the parent callbackFlow scope is cancelled
        }
    }

    /**
     * Searches track candidates for cover fetching flow.
     * Uses cover source toggles and cover source priority.
     */
    suspend fun searchByTrackForCover(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> = coroutineScope {
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyCoverEnabledSource) {
            return@coroutineScope Result.failure(Exception("No cover sources enabled"))
        }

        val results = mutableListOf<OnlineRecording>()

        val musicBrainzDeferred = if (settings.coverEnableMusicBrainz) {
            async { musicBrainzRepository.searchByTrack(title, artist) }
        } else null
        val iTunesDeferred = if (settings.coverEnableITunes) {
            async { iTunesRepository.searchByTrack(title, artist) }
        } else null
        val neteaseDeferred = if (settings.coverEnableNetease) {
            async { searchNeteaseByTrack(title, artist, settings.requestLimit) }
        } else null
        val qqDeferred = if (settings.coverEnableQQMusic) {
            async { searchQQMusicByTrack(title, artist, settings.requestLimit) }
        } else null

        musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        qqDeferred?.await()?.getOrNull()?.let { results.addAll(it) }

        val sorted = finalizeRecordingResults(
            recordings = results,
            title = title,
            artist = artist,
            priority = settings.coverPriority,
            limit = settings.searchLimit
        )
        Result.success(sorted)
    }

    /**
     * Streaming version of searchByTrackForCover.
     * Emits results as they arrive from each source, enabling real-time UI updates.
     */
    fun searchByTrackForCoverFlow(
        title: String,
        artist: String?
    ): Flow<OnlineSourceResult> = callbackFlow {
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyCoverEnabledSource) {
            trySend(OnlineSourceResult.Error(OnlineSource.UNKNOWN, "No cover sources enabled"))
            close()
            return@callbackFlow
        }

        val enabledSources = mutableListOf<String>()
        if (settings.coverEnableMusicBrainz) enabledSources.add("MusicBrainz")
        if (settings.coverEnableITunes) enabledSources.add("iTunes")
        if (settings.coverEnableNetease) enabledSources.add("NetEase")
        if (settings.coverEnableQQMusic) enabledSources.add("QQ Music")

        val completedSources = java.util.concurrent.atomic.AtomicInteger(0)

        fun markSourceCompleted(source: OnlineSource) {
            trySend(OnlineSourceResult.SourceCompleted(source))
            if (completedSources.incrementAndGet() >= enabledSources.size) {
                close()
            }
        }

        if (settings.coverEnableMusicBrainz) {
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
                    markSourceCompleted(OnlineSource.MUSICBRAINZ)
                }
            }
        }

        if (settings.coverEnableITunes) {
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
                    markSourceCompleted(OnlineSource.ITUNES)
                }
            }
        }

        if (settings.coverEnableNetease) {
            launch {
                try {
                    val result = searchNeteaseByTrack(title, artist, settings.requestLimit)
                    result.getOrNull()?.forEach { recording ->
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.NETEASE))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.NETEASE, e.message ?: "Failed"))
                } finally {
                    markSourceCompleted(OnlineSource.NETEASE)
                }
            }
        }

        if (settings.coverEnableQQMusic) {
            launch {
                try {
                    val result = searchQQMusicByTrack(title, artist, settings.requestLimit)
                    result.getOrNull()?.forEach { recording ->
                        trySend(OnlineSourceResult.RecordingResult(recording, OnlineSource.QQ_MUSIC))
                    }
                    if (result.isFailure) {
                        trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, result.exceptionOrNull()?.message ?: "Failed"))
                    }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error(OnlineSource.QQ_MUSIC, e.message ?: "Failed"))
                } finally {
                    markSourceCompleted(OnlineSource.QQ_MUSIC)
                }
            }
        }

        // Note: No explicit cleanup needed - launches are children of callbackFlow scope
        // and will be cancelled automatically when channel closes or collection ends.
        awaitClose { }
    }

    /**
     * Searches NetEase by track title.
     * Uses search API to get song list, then detail API to get complete info including cover.
     * Flow: searchSongs -> getSongDetail (parallel for each song) -> return results
     */
    private suspend fun searchNeteaseByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            // 统一查询格式：title artist (title在前，空格分隔)
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
                        // 并行获取每首歌的详情（包含封面URL）和歌词
                        // 使用 semaphore 限制并发，防止资源耗尽
                        val detailJobs = searchSongs.map { song ->
                            coroutineScope {
                                async {
                                    detailSemaphore.withPermit {
                                        // 并行获取详情和歌词
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
                            // 优先使用详情API的数据（包含封面），fallback到搜索结果
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
                            // 详情API返回的封面URL
                            val coverUrl = detailAlbum?.picUrl?.takeIf { it.isNotBlank() }
                                ?.let { normalizeCoverUrl(it) }

                            // 获取歌词文本
                            val lyricsText = lyricsResponse?.lrc?.lyric?.takeIf { it.isNotBlank() }

                            // 解析碟号
                            val discNumber = searchSong.disc.toIntOrNull()
                            // 解析曲目号
                            val trackNumber = searchSong.trackNumber.takeIf { it > 0 }
                            // 别名/注释
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
                        Timber.d(TAG, "NetEase recordings: ${recordings.take(3)}")
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
     * Uses /api/song/detail which returns album.picUrl directly.
     */
    private suspend fun getNeteaseAlbumCoverUrl(albumId: Long, songId: Long? = null): String? {
        return try {
            // Prefer using songId if available, otherwise use albumId
            val searchId = songId ?: albumId
            val songDetail = wangyRepository.getSongDetail(searchId)
            songDetail.getOrNull()?.songs?.firstOrNull()?.album?.picUrl?.let { normalizeCoverUrl(it) }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to get NetEase album cover for id=$albumId: ${e.message}")
            null
        }
    }

    /**
     * Searches QQ Music by track title.
     */
    private suspend fun searchQQMusicByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            // 统一查询格式：title artist (title在前，空格分隔)
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
                        // 并行获取每首歌的详情和歌词
                        // 使用 semaphore 限制并发，防止资源耗尽
                        val detailJobs = songs.map { song ->
                            coroutineScope {
                                async {
                                    detailSemaphore.withPermit {
                                        // 并行获取详情和歌词
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
                            
                            // 从详情响应中获取更多信息（可选）
                            val detailSong = detailResponse?.data?.track?.firstOrNull()
                            
                            // 构建专辑信息 - 优先使用搜索结果中的专辑信息
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
                            
                            // 获取歌词文本
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
            Logger.i(
                "Online query source=QQ_Music type=track elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        Timber.d("AggregatedRepository.getReleaseDetails: releaseId=$releaseId, preferredSource=$preferredSource")
        val settings = getOnlineSourceSettings()
        Timber.d("AggregatedRepository: settings - MB=${settings.enableMusicBrainz}, iTunes=${settings.enableITunes}, NetEase=${settings.enableNetease}, QQ=${settings.enableQQMusic}")
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> if (settings.enableMusicBrainz) {
                musicBrainzRepository.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("MusicBrainz source is disabled"))
            }
            DataSource.ITUNES -> if (settings.enableITunes) {
                iTunesRepository.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("Apple Music source is disabled"))
            }
            DataSource.NETEASE -> if (settings.enableNetease) {
                getNeteaseAlbumDetails(releaseId)
            } else {
                Result.failure(Exception("NetEase source is disabled"))
            }
            DataSource.QQ_MUSIC -> if (settings.enableQQMusic) {
                getQQMusicAlbumDetails(releaseId)
            } else {
                Result.failure(Exception("QQ Music source is disabled"))
            }
            DataSource.BOTH -> {
                if (!settings.hasAnyEnabledSource) {
                    Timber.w("AggregatedRepository.getReleaseDetails: No metadata sources enabled")
                    return Result.failure(Exception("No metadata sources enabled"))
                }

                if (settings.enableITunes) {
                    Timber.d("AggregatedRepository: Trying iTunes for releaseId=$releaseId")
                    val iTunesResult = iTunesRepository.getReleaseDetails(releaseId)
                    if (iTunesResult.isSuccess) {
                        Timber.d("AggregatedRepository: iTunes succeeded for releaseId=$releaseId")
                        return iTunesResult
                    } else {
                        Timber.w("AggregatedRepository: iTunes failed for releaseId=$releaseId, trying MusicBrainz")
                    }
                }
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.getReleaseDetails(releaseId)
                } else {
                    Result.failure(Exception("No release details source enabled"))
                }
            }
        }
    }

    /**
     * Gets NetEase album details.
     * Uses Simple API (WangyRepository).
     */
    private suspend fun getNeteaseAlbumDetails(albumId: String): Result<OnlineReleaseDetails> {
        return try {
            val result = wangyRepository.getAlbumDetail(albumId.toLong())
            if (result.isSuccess) {
                val album = result.getOrNull()
                // Convert to OnlineReleaseDetails
                // This is a simplified conversion
                Result.success(OnlineReleaseDetails(
                    id = albumId,
                    title = album?.album?.name ?: "Unknown",
                    // Fix: Use artists list first, fallback to artist, then Unknown
                    artist = album?.album?.artists?.firstOrNull()?.name
                        ?: album?.album?.artist?.name
                        ?: "Unknown",
                    // Parse year from publishTime timestamp
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
                    // Use tags as genre (comma-separated)
                    genre = album?.album?.tags?.takeIf { it.isNotBlank() },
                    trackCount = album?.songs?.size ?: 0,
                    // Convert songs to tracks list
                    tracks = album?.songs?.map { song ->
                        com.voxly.domain.repository.OnlineTrack(
                            number = song.position ?: song.trackNo,
                            title = song.name,
                            artist = song.ar.firstOrNull()?.name ?: "",
                            duration = if (song.dt > 0) (song.dt / 1000).toInt() else null
                        )
                    } ?: emptyList(),
                    coverArtUrl = album?.album?.picUrl
                        ?.let(::normalizeCoverUrl)
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
     * Gets QQ Music album details.
     * Note: QQ Music albums can have either numeric IDs or alphanumeric "mid" identifiers.
     * Only numeric IDs are supported via API. For mid identifiers, we try to get song info as fallback.
     */
    private suspend fun getQQMusicAlbumDetails(albumId: String): Result<OnlineReleaseDetails> {
        return try {
            // QQ Music uses both numeric IDs and alphanumeric "mid" identifiers
            val numericAlbumId = albumId.toLongOrNull()
            
            val result = if (numericAlbumId != null && numericAlbumId > 0) {
                // Use numeric album ID API
                tengxRepository.getAlbumDetail(numericAlbumId)
            } else {
                // For mid-based album IDs (or invalid numeric IDs), try song search fallback
                // This handles cases where the "albumId" is actually a song's mid
                Timber.d(TAG, "QQ Music album ID '$albumId' is mid format or invalid, trying song search fallback")
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
     * Uses song search API to find song info when album info is unavailable.
     */
    private suspend fun searchQQMusicSongByMid(songMid: String): Result<TengxAlbumDetail> {
        return try {
            // Search for the song using the mid as keyword
            // We'll search for songs and use the first match to get album info
            val searchResult = tengxRepository.searchSongs(
                keywords = songMid,
                pageNum = 1,
                pageSize = 1,
                type = 0 // type 0 = song search
            )
            
            searchResult.fold(
                onSuccess = { response ->
                    val song = response.data?.song?.list?.firstOrNull()
                    if (song != null) {
                        // Try to get album details if we have album info
                        val albumId = song.album?.id
                        if (albumId != null && albumId > 0) {
                            tengxRepository.getAlbumDetail(albumId)
                        } else if (!song.album?.mid.isNullOrBlank()) {
                            // Album has mid but no numeric ID - need different approach
                            // For now, construct a basic album detail response
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

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        Logger.d("getCoverArt: releaseId=$releaseId, preferredSource=$preferredSource", TAG)
        val settings = getOnlineSourceSettings()
        Logger.d("getCoverArt: coverEnableMB=${settings.coverEnableMusicBrainz}, coverEnableITunes=${settings.coverEnableITunes}, coverEnableNetease=${settings.coverEnableNetease}, coverEnableQQ=${settings.coverEnableQQMusic}", TAG)
        
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> if (settings.coverEnableMusicBrainz) {
                musicBrainzRepository.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("MusicBrainz source is disabled"))
            }
            DataSource.ITUNES -> if (settings.coverEnableITunes) {
                iTunesRepository.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("Apple Music source is disabled"))
            }
            DataSource.NETEASE -> if (settings.coverEnableNetease) {
                getNeteaseCoverArt(releaseId)
            } else {
                Result.failure(Exception("NetEase source is disabled"))
            }
            DataSource.QQ_MUSIC -> if (settings.coverEnableQQMusic) {
                getQQMusicCoverArt(releaseId)
            } else {
                Result.failure(Exception("QQ Music source is disabled"))
            }
            DataSource.BOTH -> {
                if (!settings.hasAnyCoverEnabledSource) {
                    return Result.failure(Exception("No cover sources enabled"))
                }
                for (source in settings.coverPriority) {
                    when (source) {
                        "itunes" -> if (settings.coverEnableITunes) {
                            val result = iTunesRepository.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "musicbrainz" -> if (settings.coverEnableMusicBrainz) {
                            val result = musicBrainzRepository.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "netease" -> if (settings.coverEnableNetease) {
                            val result = getNeteaseCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "qq_music" -> if (settings.coverEnableQQMusic) {
                            val result = getQQMusicCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                    }
                }
                Result.success(null)
            }
        }
    }

    /**
     * Gets NetEase album cover art.
     * Uses Simple API (WangyRepository).
     */
    private suspend fun getNeteaseCoverArt(albumId: String): Result<ByteArray?> {
        return withContext(Dispatchers.IO) {
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
    }

    /**
     * Gets QQ Music album cover art.
     */
    private suspend fun getQQMusicCoverArt(albumId: String): Result<ByteArray?> {
        return withContext(Dispatchers.IO) {
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
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Gets metadata specifically from iTunes/Apple Music.
     */
    suspend fun getFromITunes(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return iTunesRepository.searchByArtistAlbum(artist, album)
    }

    /**
     * Gets metadata specifically from MusicBrainz.
     */
    suspend fun getFromMusicBrainz(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return musicBrainzRepository.searchByArtistAlbum(artist, album)
    }

    /**
     * Gets metadata specifically from NetEase Cloud Music.
     */
    suspend fun getFromNetease(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return searchNeteaseByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Gets metadata specifically from QQ Music.
     */
    suspend fun getFromQQMusic(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return searchQQMusicByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Checks if two releases are likely the same based on title similarity.
     */
    private fun isSimilarRelease(release1: OnlineRelease, release2: OnlineRelease): Boolean {
        val title1 = release1.title.lowercase().trim()
        val title2 = release2.title.lowercase().trim()
        
        // Exact match
        if (title1 == title2) return true
        
        // One contains the other
        if (title1.contains(title2) || title2.contains(title1)) return true
        
        // Similar artist names
        val artist1 = release1.artist.lowercase().trim()
        val artist2 = release2.artist.lowercase().trim()
        
        return artist1 == artist2 && (title1.contains(title2) || title2.contains(title1))
    }

    private fun finalizeReleaseResults(
        releases: List<OnlineRelease>,
        artist: String,
        album: String,
        settings: OnlineSourceSettings
    ): List<OnlineRelease> {
        // Skip fuzzy filtering - API already returns relevant results
        val sorted = releases.sortedWith(
            compareBy<OnlineRelease> { release ->
                sourcePriorityIndex(release.source, settings.metadataPriority)
            }
        )
        return applyLimit(sorted, settings.searchLimit)
    }

    private fun finalizeRecordingResults(
        recordings: List<OnlineRecording>,
        title: String,
        artist: String?,
        priority: List<String>,
        limit: Int
    ): List<OnlineRecording> {
        Timber.d("finalizeRecordingResults: input=${recordings.size} recordings, title='$title', artist='$artist'")
        // 宽松匹配过滤 + 括号处理 + 包含匹配
        // - 歌曲名匹配：移除括号后检查是否包含
        // - 歌手名匹配：宽松匹配（包含匹配，不区分大小写）
        val filtered = recordings.filter { recording ->
            val titleMatch = matchesTitle(title, recording.title)
            Timber.d("Filter check: queryTitle='$title', resultTitle='${recording.title}', match=$titleMatch")
            // 对于 iTunes/Apple Music 源：如果 title 匹配，则放宽 artist 检查
            // 因为 iTunes 返回英文艺术家名，而用户音乐库可能是中文名
            val artistMatch = if (titleMatch && recording.source == OnlineSource.ITUNES) {
                // iTunes 源：title 匹配时信任结果，跳过 artist 严格检查
                Timber.d("Artist check: queryArtist='$artist', resultArtist='${recording.artist}', source=iTunes, match=relaxed")
                true
            } else if (artist.isNullOrBlank()) {
                // 如果查询没有歌手名，只检查歌曲名
                true
            } else {
                val match = matchesArtist(artist, recording.artist)
                Timber.d("Artist check: queryArtist='$artist', resultArtist='${recording.artist}', match=$match")
                match
            }
            titleMatch && artistMatch
        }
        Timber.d("finalizeRecordingResults: filtered=${filtered.size} recordings")

        // 按优先级排序
        val sorted = filtered.sortedWith(
            compareBy<OnlineRecording> { recording ->
                sourcePriorityIndex(recording.source, priority)
            }
        )
        return applyLimit(sorted, limit)
    }

    /**
     * 歌曲名匹配：移除括号后检查是否包含，简繁体兼容
     * 例如：查询 "你好（live）" 可以匹配 "你好"、"你好 (Live)"、"你好 live"
     * 例如：查询 "你好" 可以匹配 "你好（live）"、"你好"、"Hello 你好"
     */
    private fun matchesTitle(queryTitle: String, resultTitle: String): Boolean {
        // 移除括号及其内容进行比较
        val normalizedQuery = queryTitle.removeBracketContent()
        val normalizedResult = resultTitle.removeBracketContent()

        // 检查是否包含（不区分大小写）
        if (normalizedResult.contains(normalizedQuery, ignoreCase = true) ||
            normalizedQuery.contains(normalizedResult, ignoreCase = true)) {
            return true
        }

        // 简繁体中文兼容
        val simplifiedQuery = normalizedQuery.toSimplifiedChinese()
        val simplifiedResult = normalizedResult.toSimplifiedChinese()

        return simplifiedResult.contains(simplifiedQuery) || simplifiedQuery.contains(simplifiedResult)
    }

    /**
     * 移除字符串中的括号及其内容
     * 例如："你好（live）" -> "你好"
     * 例如："Hello (Remastered)" -> "Hello"
     */
    private fun String.removeBracketContent(): String {
        return this
            .replace(Regex("\\([^)]*\\)"), "")  // 移除 ()
            .replace(Regex("\\[[^]]*\\]"), "")  // 移除 []
            .replace(Regex("\\{[^}]*\\}"), "") // 移除 {}
            .replace(Regex("（[^）]*）"), "")      // 移除中文括号（）
            .trim()
    }


    /**
     * 歌手名匹配：宽松匹配（包含匹配，不区分大小写，简繁体兼容）
     */
    private fun matchesArtist(queryArtist: String, resultArtist: String): Boolean {
        val normalizedQuery = queryArtist.lowercase().trim()
        val normalizedResult = resultArtist.lowercase().trim()
        
        if (normalizedResult.contains(normalizedQuery) || normalizedQuery.contains(normalizedResult)) {
            return true
        }
        
        val simplifiedQuery = normalizedQuery.toSimplifiedChinese()
        val simplifiedResult = normalizedResult.toSimplifiedChinese()
        
        return simplifiedResult.contains(simplifiedQuery) || simplifiedQuery.contains(simplifiedResult)
    }

    private fun String.toSimplifiedChinese(): String {
        return try {
            val transliterator = Transliterator.getInstance("Traditional-Simplified")
            transliterator.transliterate(this)
        } catch (e: Exception) {
            // Fallback: return original if ICU4J fails
            Timber.w(TAG, "ICU4J conversion failed: ${e.message}")
            this
        }
    }


    private suspend fun getOnlineSourceSettings(): OnlineSourceSettings {
        return OnlineSourceSettings(
            enableMusicBrainz = settingsDataStore.metadataSourceEnabledMusicBrainz.first(),
            enableITunes = settingsDataStore.metadataSourceEnabledITunes.first(),
            enableNetease = settingsDataStore.metadataSourceEnabledNetease.first(),
            enableQQMusic = settingsDataStore.metadataSourceEnabledQQMusic.first(),
            coverEnableMusicBrainz = settingsDataStore.coverSourceEnabledMusicBrainz.first(),
            coverEnableITunes = settingsDataStore.coverSourceEnabledITunes.first(),
            coverEnableNetease = settingsDataStore.coverSourceEnabledNetease.first(),
            coverEnableQQMusic = settingsDataStore.coverSourceEnabledQQMusic.first(),
            searchLimit = normalizeSearchLimit(settingsDataStore.onlineSearchLimit.first()),
            searchLimitMusicBrainz = settingsDataStore.onlineSearchLimitMusicBrainz.first(),
            searchLimitITunes = settingsDataStore.onlineSearchLimitITunes.first(),
            searchLimitNetease = settingsDataStore.onlineSearchLimitNetease.first(),
            searchLimitQQMusic = settingsDataStore.onlineSearchLimitQQMusic.first(),
            metadataPriority = settingsDataStore.metadataSourcePriority.first(),
            coverPriority = settingsDataStore.coverSourcePriority.first()
        )
    }

    private fun normalizeSearchLimit(limit: Int): Int {
        return if (limit <= 0) 0 else limit.coerceIn(5, 200)
    }

    private fun <T> applyLimit(list: List<T>, limit: Int): List<T> {
        return if (limit <= 0) list else list.take(limit)
    }

    private data class OnlineSourceSettings(
        val enableMusicBrainz: Boolean,
        val enableITunes: Boolean,
        val enableNetease: Boolean,
        val enableQQMusic: Boolean,
        val coverEnableMusicBrainz: Boolean,
        val coverEnableITunes: Boolean,
        val coverEnableNetease: Boolean,
        val coverEnableQQMusic: Boolean,
        val searchLimit: Int,
        val searchLimitMusicBrainz: Int,
        val searchLimitITunes: Int,
        val searchLimitNetease: Int,
        val searchLimitQQMusic: Int,
        val metadataPriority: List<String>,
        val coverPriority: List<String>
    ) {
        val requestLimit: Int
            get() = if (searchLimit <= 0) 200 else searchLimit

        /**
         * Get the effective search limit for a specific source.
         * Per-source limit takes precedence if set (> 0), otherwise falls back to global limit.
         */
        fun getSourceLimit(source: String): Int {
            val perSourceLimit = when (source) {
                "MusicBrainz" -> searchLimitMusicBrainz
                "iTunes" -> searchLimitITunes
                "NetEase" -> searchLimitNetease
                "QQ Music" -> searchLimitQQMusic
                else -> 0
            }
            return if (perSourceLimit > 0) perSourceLimit else requestLimit
        }

        val hasAnyEnabledSource: Boolean
            get() = enableMusicBrainz || enableITunes || enableNetease || enableQQMusic

        val hasAnyCoverEnabledSource: Boolean
            get() = coverEnableMusicBrainz || coverEnableITunes || coverEnableNetease || coverEnableQQMusic
    }

    private fun sourcePriorityIndex(source: OnlineSource, priority: List<String>): Int {
        val key = when (source) {
            OnlineSource.ITUNES -> "itunes"
            OnlineSource.MUSICBRAINZ -> "musicbrainz"
            OnlineSource.NETEASE -> "netease"
            OnlineSource.QQ_MUSIC -> "qq_music"
            OnlineSource.UNKNOWN -> "unknown"
        }
        val index = priority.indexOf(key)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun normalizeCoverUrl(url: String?): String? {
        val trimmed = url?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://${trimmed.removePrefix("http://")}"
        } else {
            trimmed
        }
    }

    private fun buildQQCoverUrl(
        albumMid: String?,
        rawCoverUrl: String?,
        fallbackId: String?
    ): String? {
        // Handle null or blank values
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

    /**
     * Converts technical SSL/network errors to user-friendly messages.
     */
    private fun Throwable.toUserFriendlyError(): String {
        val message = this.message ?: "Unknown error"
        
        // SSL certificate chain validation failed
        if (this is SSLException || 
            message.contains("chain validation failed", ignoreCase = true) ||
            message.contains("SSL", ignoreCase = true) &&
            (message.contains("validation", ignoreCase = true) || 
             message.contains("certificate", ignoreCase = true))
        ) {
            return "网络连接失败：SSL 证书验证错误。请检查设备日期/时间设置，或联系网络管理员。"
        }
        
        // Unknown host / DNS error
        if (this is UnknownHostException || message.contains("Unable to resolve host", ignoreCase = true)) {
            return "网络连接失败：无法解析服务器地址。请检查网络连接。"
        }
        
        // Connection timeout
        if (message.contains("timeout", ignoreCase = true)) {
            return "网络连接超时：请检查网络连接后重试。"
        }
        
        // Connection refused
        if (message.contains("Connection refused", ignoreCase = true)) {
            return "网络连接被拒绝：请稍后重试。"
        }
        
        return message
    }
}
