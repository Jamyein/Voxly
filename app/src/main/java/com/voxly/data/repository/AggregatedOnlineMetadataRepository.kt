package com.voxly.data.repository

import android.os.SystemClock
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.remote.itunes.ITunesRepository
import com.voxly.data.remote.musicbrainz.MusicBrainzRepository
import com.voxly.data.remote.tengx.TengxRepository
import com.voxly.data.remote.wangy.WangyRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineRecording
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AggregatedMetadata"
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

/**
 * Streaming search result with payload and source marker.
 */
sealed class OnlineSourceResult {
    data class ReleaseResult(
        val release: OnlineRelease,
        val source: String
    ) : OnlineSourceResult()

    data class RecordingResult(
        val recording: OnlineRecording,
        val source: String
    ) : OnlineSourceResult()

    data class SourceCompleted(val source: String) : OnlineSourceResult()

    data class Error(val source: String, val message: String) : OnlineSourceResult()
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
        val sourceJobs = mutableListOf<kotlinx.coroutines.Job>()

        val useITunes = settings.enableITunes && (preferredSource == DataSource.ITUNES || preferredSource == DataSource.BOTH)
        val useQQMusic = settings.enableQQMusic && (preferredSource == DataSource.QQ_MUSIC || preferredSource == DataSource.BOTH)
        val useNetease = settings.enableNetease && (preferredSource == DataSource.NETEASE || preferredSource == DataSource.BOTH)
        val useMusicBrainz = settings.enableMusicBrainz && (preferredSource == DataSource.MUSICBRAINZ || preferredSource == DataSource.BOTH)

        if (!useITunes && !useQQMusic && !useNetease && !useMusicBrainz) {
            trySend(OnlineSourceResult.Error("System", "No metadata sources enabled"))
            channel.close()
        }

        if (useITunes) {
            sourceJobs += launch {
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
                                trySend(OnlineSourceResult.ReleaseResult(release, "iTunes"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("iTunes", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("iTunes", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("iTunes"))
                }
            }
        }

        if (useQQMusic) {
            sourceJobs += launch {
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
                                trySend(OnlineSourceResult.ReleaseResult(release, "QQ Music"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("QQ Music", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("QQ Music", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("QQ Music"))
                }
            }
        }

        if (useNetease) {
            sourceJobs += launch {
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
                                trySend(OnlineSourceResult.ReleaseResult(release, "NetEase"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("NetEase", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("NetEase", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("NetEase"))
                }
            }
        }

        if (useMusicBrainz) {
            sourceJobs += launch {
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
                                trySend(OnlineSourceResult.ReleaseResult(release, "MusicBrainz"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("MusicBrainz", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("MusicBrainz", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("MusicBrainz"))
                }
            }
        }

        val completionJob = launch {
            sourceJobs.joinAll()
            channel.close()
        }

        awaitClose {
            completionJob.cancel()
            sourceJobs.forEach { it.cancel() }
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

        val sortedResults = finalizeReleaseResults(
            releases = mergedResults,
            artist = artist,
            album = album,
            settings = settings
        )
        Result.success(sortedResults)
    }

    /**
     * Searches NetEase Cloud Music by artist and album.
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

            if (searchResult.isSuccess) {
                val response = searchResult.getOrNull()
                val songs = response?.result?.songs ?: emptyList()
                
                // Group by album
                val albums = songs.groupBy { it.album?.id ?: -1L }.mapNotNull { (albumId, albumSongs) ->
                    if (albumId <= 0L) return@mapNotNull null
                    val firstSong = albumSongs.first()
                    OnlineRelease(
                        id = albumId.toString(),
                        title = firstSong.album?.name ?: "Unknown Album",
                        artist = firstSong.artists.joinToString(", ") { it.name },
                        year = null, // NetEase API doesn't provide year in search
                        format = "Digital",
                        trackCount = albumSongs.size,
                        coverArtUrl = normalizeCoverUrl(firstSong.album?.picUrl),
                        source = "NetEase",
                        albumTitle = firstSong.album?.name
                    )
                }
                Result.success(albums)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
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

            if (searchResult.isSuccess) {
                val response = searchResult.getOrNull()
                val songs = response?.data?.song?.list ?: emptyList()
                
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
                            source = "QQ Music",
                            albumTitle = firstSong.album?.name
                        )
                    }
                }
                Result.success(albums)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
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
                    
                    musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.searchLimit)) }
                    iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(applyLimit(it, settings.searchLimit)) }
                    neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
                    qqMusicDeferred?.await()?.getOrNull()?.let { results.addAll(it) }

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
        val sourceJobs = mutableListOf<kotlinx.coroutines.Job>()

        val useITunes = settings.enableITunes && (preferredSource == DataSource.ITUNES || preferredSource == DataSource.BOTH)
        val useQQMusic = settings.enableQQMusic && (preferredSource == DataSource.QQ_MUSIC || preferredSource == DataSource.BOTH)
        val useNetease = settings.enableNetease && (preferredSource == DataSource.NETEASE || preferredSource == DataSource.BOTH)
        val useMusicBrainz = settings.enableMusicBrainz && (preferredSource == DataSource.MUSICBRAINZ || preferredSource == DataSource.BOTH)

        if (!useITunes && !useQQMusic && !useNetease && !useMusicBrainz) {
            trySend(OnlineSourceResult.Error("System", "No metadata sources enabled"))
            channel.close()
        }

        if (useITunes) {
            sourceJobs += launch {
                try {
                    val result = iTunesRepository.searchByTrack(title, artist)
                    result
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, "iTunes"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("iTunes", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("iTunes", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("iTunes"))
                }
            }
        }

        if (useQQMusic) {
            sourceJobs += launch {
                try {
                    val result = searchQQMusicByTrack(title, artist, settings.requestLimit)
                    result
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, "QQ Music"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("QQ Music", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("QQ Music", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("QQ Music"))
                }
            }
        }

        if (useNetease) {
            sourceJobs += launch {
                try {
                    val result = searchNeteaseByTrack(title, artist, settings.requestLimit)
                    result
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, "NetEase"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("NetEase", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("NetEase", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("NetEase"))
                }
            }
        }

        if (useMusicBrainz) {
            sourceJobs += launch {
                try {
                    val result = musicBrainzRepository.searchByTrack(title, artist)
                    result
                        .map {
                            finalizeRecordingResults(
                                recordings = it,
                                title = title,
                                artist = artist,
                                priority = settings.metadataPriority,
                                limit = settings.searchLimit
                            )
                        }
                        .onSuccess { recordings ->
                            recordings.forEach { recording ->
                                trySend(OnlineSourceResult.RecordingResult(recording, "MusicBrainz"))
                            }
                        }
                        .onFailure { error ->
                            trySend(OnlineSourceResult.Error("MusicBrainz", error.message ?: "Failed"))
                        }
                } catch (e: Exception) {
                    trySend(OnlineSourceResult.Error("MusicBrainz", e.message ?: "Failed"))
                } finally {
                    trySend(OnlineSourceResult.SourceCompleted("MusicBrainz"))
                }
            }
        }

        val completionJob = launch {
            sourceJobs.joinAll()
            channel.close()
        }

        awaitClose {
            completionJob.cancel()
            sourceJobs.forEach { it.cancel() }
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
     * Searches NetEase by track title.
     */
    private suspend fun searchNeteaseByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val searchResult = wangyRepository.searchSongs(
                keywords = if (artist != null) "$artist $title" else title,
                page = 1,
                limit = limit
            )

            if (searchResult.isSuccess) {
                val response = searchResult.getOrNull()
                val songs = response?.result?.songs ?: emptyList()
                
                    val recordings = songs.map { song ->
                        OnlineRecording(
                            id = song.id.toString(),
                            title = song.name,
                            artist = song.artists.joinToString(", ") { it.name },
                            duration = (song.duration / 1000).toInt(),
                            releaseId = song.album?.id?.toString(),
                            source = "NetEase",
                            coverArtUrl = normalizeCoverUrl(song.album?.picUrl)
                        )
                }
                Result.success(recordings)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }.also { result ->
            Logger.i(
                "Online query source=NetEase type=track elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
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
            val searchResult = tengxRepository.searchSongs(
                keywords = title,
                pageNum = 1,
                pageSize = limit
            )

            if (searchResult.isSuccess) {
                val response = searchResult.getOrNull()
                val songs = response?.data?.song?.list ?: emptyList()
                
                val recordings = songs.map { song ->
                    OnlineRecording(
                        id = song.id.toString(),
                        title = song.name,
                        artist = song.singer.joinToString(", ") { it.name },
                        duration = song.interval,
                        releaseId = song.album?.mid?.takeIf { it.isNotBlank() } ?: song.album?.id?.toString(),
                        source = "QQ Music",
                        coverArtUrl = buildQQCoverUrl(
                            albumMid = song.album?.mid,
                            rawCoverUrl = song.album?.pic,
                            fallbackId = song.album?.id?.toString()
                        )
                    )
                }
                Result.success(recordings)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }.also { result ->
            Logger.i(
                "Online query source=QQ_Music type=track elapsedMs=${SystemClock.elapsedRealtime() - startedAt} resultCount=${result.getOrNull()?.size ?: 0} success=${result.isSuccess}",
                TAG
            )
        }
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        val settings = getOnlineSourceSettings()
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
                    return Result.failure(Exception("No metadata sources enabled"))
                }

                if (settings.enableITunes) {
                    val iTunesResult = iTunesRepository.getReleaseDetails(releaseId)
                    if (iTunesResult.isSuccess) {
                        return iTunesResult
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
                    artist = album?.album?.artist?.name ?: "Unknown",
                    year = null,
                    genre = null,
                    trackCount = album?.songs?.size ?: 0,
                    tracks = emptyList(),
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
     */
    private suspend fun getQQMusicAlbumDetails(albumId: String): Result<OnlineReleaseDetails> {
        return try {
            val result = tengxRepository.getAlbumDetail(albumId.toLong())
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

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        val settings = getOnlineSourceSettings()
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
     */
    private suspend fun getNeteaseCoverArt(albumId: String): Result<ByteArray?> {
        return try {
            val result = wangyRepository.getAlbumDetail(albumId.toLong())
            if (result.isSuccess) {
                val album = result.getOrNull()
                val coverUrl = normalizeCoverUrl(album?.album?.picUrl)
                if (coverUrl != null) {
                    // Download cover art
                    val url = java.net.URL(coverUrl)
                    val connection = url.openConnection()
                    connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    val bytes = connection.getInputStream().use { it.readBytes() }
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

    /**
     * Gets QQ Music album cover art.
     */
    private suspend fun getQQMusicCoverArt(albumId: String): Result<ByteArray?> {
        return try {
            val coverUrl = buildQQCoverUrl(
                albumMid = albumId.takeUnless { it.all(Char::isDigit) } ?: "",
                rawCoverUrl = null,
                fallbackId = albumId.takeIf { it.all(Char::isDigit) }
            ) ?: return Result.success(null)
            val url = java.net.URL(coverUrl)
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Referer", "https://y.qq.com")
            val bytes = connection.getInputStream().use { it.readBytes() }
            Result.success(bytes)
        } catch (e: Exception) {
            Timber.e(TAG, "QQ Music cover art failed: ${e.message}", e)
            Result.failure(e)
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
        val filtered = filterReleasesByQuery(
            releases = releases,
            artist = artist,
            album = album,
            limit = settings.searchLimit
        )
        val sorted = filtered.sortedWith(
            compareBy<OnlineRelease> { release ->
                sourcePriorityIndex(release.source, settings.metadataPriority)
            }.thenByDescending { release ->
                fuzzyMatchLevel(album, release.songTitle ?: release.title)
            }.thenByDescending { release ->
                fuzzyMatchLevel(album, release.albumTitle ?: release.title)
            }.thenByDescending { release ->
                fuzzyMatchLevel(artist, release.artist)
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
        val filtered = filterRecordingsByQuery(
            recordings = recordings,
            title = title,
            artist = artist,
            limit = limit
        )
        val sorted = filtered.sortedWith(
            compareBy<OnlineRecording> { recording ->
                sourcePriorityIndex(recording.source, priority)
            }.thenByDescending { recording ->
                fuzzyMatchLevel(title, recording.title)
            }.thenByDescending { recording ->
                fuzzyMatchLevel(artist.orEmpty(), recording.artist)
            }
        )
        return applyLimit(sorted, limit)
    }

    private fun filterReleasesByQuery(
        releases: List<OnlineRelease>,
        artist: String,
        album: String,
        limit: Int
    ): List<OnlineRelease> {
        if (album.isBlank()) return applyLimit(releases, limit)
        return releases.filter { release ->
            val candidate = release.songTitle?.takeIf { it.isNotBlank() } ?: release.title
            fuzzyTitleMatch(album, candidate)
        }
    }

    private fun filterRecordingsByQuery(
        recordings: List<OnlineRecording>,
        title: String,
        artist: String?,
        limit: Int
    ): List<OnlineRecording> {
        if (title.isBlank()) return applyLimit(recordings, limit)
        return recordings.filter { recording ->
            fuzzyTitleMatch(title, recording.title)
        }
    }

    /**
     * Title-only fuzzy matcher:
     * - exact/contains first
     * - then token hit ratio for latin-like titles
     */
    private fun fuzzyTitleMatch(query: String, candidate: String?): Boolean {
        return fuzzyMatchLevel(query, candidate) > 0
    }

    private fun fuzzyMatchLevel(query: String, candidate: String?): Int {
        val normalizedQuery = normalizeMatchText(query)
        val normalizedCandidate = normalizeMatchText(candidate)
        if (normalizedQuery.isBlank() || normalizedCandidate.isBlank()) return 0
        if (normalizedCandidate == normalizedQuery) return 3
        if (normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate)) {
            return 2
        }

        val queryTokens = tokenizeMatchText(normalizedQuery)
        if (queryTokens.isEmpty()) return 0

        val candidateTokens = tokenizeMatchText(normalizedCandidate)
        val matched = queryTokens.count { token ->
            normalizedCandidate.contains(token) || candidateTokens.contains(token)
        }
        val required = when {
            queryTokens.size <= 2 -> 1
            queryTokens.size <= 4 -> 2
            else -> (queryTokens.size * 6 + 9) / 10
        }
        return if (matched >= required) 1 else 0
    }

    private fun normalizeMatchText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private fun tokenizeMatchText(text: String): List<String> {
        return text
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
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
        val metadataPriority: List<String>,
        val coverPriority: List<String>
    ) {
        val requestLimit: Int
            get() = if (searchLimit <= 0) 200 else searchLimit

        val hasAnyEnabledSource: Boolean
            get() = enableMusicBrainz || enableITunes || enableNetease || enableQQMusic

        val hasAnyCoverEnabledSource: Boolean
            get() = coverEnableMusicBrainz || coverEnableITunes || coverEnableNetease || coverEnableQQMusic
    }

    private fun sourcePriorityIndex(source: String, priority: List<String>): Int {
        val key = when (source) {
            "iTunes" -> "itunes"
            "MusicBrainz" -> "musicbrainz"
            "NetEase" -> "netease"
            "QQ Music" -> "qq_music"
            else -> "unknown"
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
        val normalizedRaw = normalizeCoverUrl(rawCoverUrl)
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
}
