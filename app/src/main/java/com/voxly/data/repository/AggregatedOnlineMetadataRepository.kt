package com.voxly.data.repository

import android.icu.text.Transliterator

import android.os.SystemClock

import timber.log.Timber
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.remote.musicbrainz.MusicBrainzMetadataRepository
import com.voxly.data.remote.itunes.ItunesMetadataRepository
import com.voxly.data.remote.netease.NetEaseMetadataRepository
import com.voxly.data.remote.qqmusic.QQMusicMetadataRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.OnlineSourceResult
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AggregatedMetadata"

/**
 * Aggregated repository that combines multiple online metadata sources.
 * Supports MusicBrainz, iTunes/Apple Music, NetEase Cloud Music, and QQ Music.
 * 
 * This repository queries all available sources and merges the results,
 * giving users the best metadata from multiple providers.
 * 
 * Uses underlying repositories for blocking calls and *MetadataRepository for Flow-based streaming.
 */
@Singleton
class AggregatedOnlineMetadataRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    // Strategies for single-source operations
    private val musicBrainzStrategy: MusicBrainzSourceStrategy,
    private val iTunesStrategy: ITunesSourceStrategy,
    private val netEaseStrategy: NetEaseSourceStrategy,
    private val qqMusicStrategy: QQMusicSourceStrategy,
    // Aggregation strategy for BOTH mode
    private val aggregationStrategy: SourceAggregationStrategy,
    // Metadata repositories for Flow-based streaming
    private val musicBrainzMetadataRepository: MusicBrainzMetadataRepository,
    private val itunesMetadataRepository: ItunesMetadataRepository,
    private val netEaseMetadataRepository: NetEaseMetadataRepository,
    private val qqMusicMetadataRepository: QQMusicMetadataRepository
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
        Timber.i(
            "Online query start type=artist_album artist=$artist album=$album source=$preferredSource",
            TAG
        )
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            Timber.w("Online query rejected: no metadata source enabled", TAG)
            return Result.failure(Exception("No metadata sources enabled"))
        }
        val result = when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzStrategy.searchByArtistAlbum(artist, album, settings.requestLimit)
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
                    iTunesStrategy.searchByArtistAlbum(artist, album, settings.requestLimit)
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
                    netEaseStrategy.searchByArtistAlbum(artist, album, settings.requestLimit)
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
                    qqMusicStrategy.searchByArtistAlbum(artist, album, settings.requestLimit)
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
            DataSource.BOTH -> aggregationStrategy.searchAllByArtistAlbum(artist, album, settings)
                .map { finalizeReleaseResults(it, artist, album, settings) }
        }
        Timber.i(
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

        supervisorScope {
            if (useITunes) {
                launch {
                    itunesMetadataRepository.searchByArtistAlbumFlow(artist, album, settings.getSourceLimit("iTunes"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useQQMusic) {
                launch {
                    qqMusicMetadataRepository.searchByArtistAlbumFlow(artist, album, settings.getSourceLimit("QQ Music"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useNetease) {
                launch {
                    netEaseMetadataRepository.searchByArtistAlbumFlow(artist, album, settings.getSourceLimit("NetEase"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useMusicBrainz) {
                launch {
                    musicBrainzMetadataRepository.searchByArtistAlbumFlow(artist, album, settings.getSourceLimit("MusicBrainz"))
                        .collect { result -> trySend(result) }
                }
            }
        }

        channel.close()

        awaitClose { }
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> {
        val requestStartedAt = SystemClock.elapsedRealtime()
        Timber.i(
            "Online query start type=track title=$title artist=${artist ?: ""} source=$preferredSource",
            TAG
        )
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            Timber.w("Online query rejected: no metadata source enabled", TAG)
            return Result.failure(Exception("No metadata sources enabled"))
        }
        val result = when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzStrategy.searchByTrack(title, artist, settings.searchLimit)
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
                    iTunesStrategy.searchByTrack(title, artist, settings.searchLimit)
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
                    netEaseStrategy.searchByTrack(title, artist, settings.requestLimit)
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
                    qqMusicStrategy.searchByTrack(title, artist, settings.requestLimit)
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
            DataSource.BOTH -> aggregationStrategy.searchAllByTrack(title, artist, settings)
                .map { results ->
                    finalizeRecordingResults(
                        recordings = results,
                        title = title,
                        artist = artist,
                        priority = settings.metadataPriority,
                        limit = settings.searchLimit
                    )
                }
        }
        Timber.i(
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
        Timber.i("searchByTrackFlow: title='$title', artist='$artist'")

        val useITunes = settings.enableITunes && (preferredSource == DataSource.ITUNES || preferredSource == DataSource.BOTH)
        val useQQMusic = settings.enableQQMusic && (preferredSource == DataSource.QQ_MUSIC || preferredSource == DataSource.BOTH)
        val useNetease = settings.enableNetease && (preferredSource == DataSource.NETEASE || preferredSource == DataSource.BOTH)
        val useMusicBrainz = settings.enableMusicBrainz && (preferredSource == DataSource.MUSICBRAINZ || preferredSource == DataSource.BOTH)

        if (!useITunes && !useQQMusic && !useNetease && !useMusicBrainz) {
            trySend(OnlineSourceResult.Error(OnlineSource.UNKNOWN, "No metadata sources enabled"))
            channel.close()
            return@callbackFlow
        }

        supervisorScope {
            if (useITunes) {
                launch {
                    itunesMetadataRepository.searchByTrackFlow(title, artist, settings.getSourceLimit("iTunes"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useQQMusic) {
                launch {
                    qqMusicMetadataRepository.searchByTrackFlow(title, artist, settings.getSourceLimit("QQ Music"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useNetease) {
                launch {
                    netEaseMetadataRepository.searchByTrackFlow(title, artist, settings.getSourceLimit("NetEase"))
                        .collect { result -> trySend(result) }
                }
            }

            if (useMusicBrainz) {
                launch {
                    musicBrainzMetadataRepository.searchByTrackFlow(title, artist, settings.getSourceLimit("MusicBrainz"))
                        .collect { result -> trySend(result) }
                }
            }
        }

        channel.close()

        awaitClose { }
    }

    suspend fun searchByTrackForCover(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> = supervisorScope {
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyCoverEnabledSource) {
            Timber.w(TAG, "searchByTrackForCover: no cover sources enabled")
            return@supervisorScope Result.failure(Exception("No cover sources enabled"))
        }

        Timber.i(TAG, "searchByTrackForCover: starting search for title='$title', artist='$artist'")
        val results = mutableListOf<OnlineRecording>()

        val musicBrainzDeferred: kotlinx.coroutines.Deferred<Result<List<OnlineRecording>>>? = if (settings.coverEnableMusicBrainz) {
            async {
                try {
                    musicBrainzStrategy.searchByTrack(title, artist, settings.requestLimit)
                } catch (e: Exception) {
                    Timber.w(TAG, "MusicBrainz search failed: ${e.message}")
                    Result.success(emptyList())
                }
            }
        } else null
        val iTunesDeferred: kotlinx.coroutines.Deferred<Result<List<OnlineRecording>>>? = if (settings.coverEnableITunes) {
            async {
                try {
                    iTunesStrategy.searchByTrack(title, artist, settings.requestLimit)
                } catch (e: Exception) {
                    Timber.w(TAG, "iTunes search failed: ${e.message}")
                    Result.success(emptyList())
                }
            }
        } else null
        val neteaseDeferred: kotlinx.coroutines.Deferred<Result<List<OnlineRecording>>>? = if (settings.coverEnableNetease) {
            async {
                try {
                    netEaseStrategy.searchByTrack(title, artist, settings.requestLimit)
                } catch (e: Exception) {
                    Timber.w(TAG, "NetEase search failed: ${e.message}")
                    Result.success(emptyList())
                }
            }
        } else null
        val qqDeferred: kotlinx.coroutines.Deferred<Result<List<OnlineRecording>>>? = if (settings.coverEnableQQMusic) {
            async {
                try {
                    qqMusicStrategy.searchByTrack(title, artist, settings.requestLimit)
                } catch (e: Exception) {
                    Timber.w(TAG, "QQ Music search failed: ${e.message}")
                    Result.success(emptyList())
                }
            }
        } else null

        musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        qqDeferred?.await()?.getOrNull()?.let { results.addAll(it) }

        Timber.d(TAG, "searchByTrackForCover: raw results count=${results.size}")

        val sorted = finalizeRecordingResults(
            recordings = results,
            title = title,
            artist = artist,
            priority = settings.coverPriority,
            limit = settings.searchLimit
        )
        
        Timber.i(TAG, "searchByTrackForCover: final results count=${sorted.size}")
        Result.success(sorted)
    }

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

        supervisorScope {
            if (settings.coverEnableMusicBrainz) {
                launch {
                    musicBrainzMetadataRepository.searchByTrackForCoverFlow(title, artist, settings.requestLimit)
                        .collect { result -> trySend(result) }
                    markSourceCompleted(OnlineSource.MUSICBRAINZ)
                }
            }

            if (settings.coverEnableITunes) {
                launch {
                    itunesMetadataRepository.searchByTrackForCoverFlow(title, artist, settings.requestLimit)
                        .collect { result -> trySend(result) }
                    markSourceCompleted(OnlineSource.ITUNES)
                }
            }

            if (settings.coverEnableNetease) {
                launch {
                    netEaseMetadataRepository.searchByTrackForCoverFlow(title, artist, settings.requestLimit)
                        .collect { result -> trySend(result) }
                    markSourceCompleted(OnlineSource.NETEASE)
                }
            }

            if (settings.coverEnableQQMusic) {
                launch {
                    qqMusicMetadataRepository.searchByTrackForCoverFlow(title, artist, settings.requestLimit)
                        .collect { result -> trySend(result) }
                    markSourceCompleted(OnlineSource.QQ_MUSIC)
                }
            }
        }

        awaitClose { }
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        Timber.d("AggregatedRepository.getReleaseDetails: releaseId=$releaseId, preferredSource=$preferredSource")
        val settings = getOnlineSourceSettings()
        Timber.d("AggregatedRepository: settings - MB=${settings.enableMusicBrainz}, iTunes=${settings.enableITunes}, NetEase=${settings.enableNetease}, QQ=${settings.enableQQMusic}")
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> if (settings.enableMusicBrainz) {
                musicBrainzStrategy.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("MusicBrainz source is disabled"))
            }
            DataSource.ITUNES -> if (settings.enableITunes) {
                iTunesStrategy.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("Apple Music source is disabled"))
            }
            DataSource.NETEASE -> if (settings.enableNetease) {
                netEaseStrategy.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("NetEase source is disabled"))
            }
            DataSource.QQ_MUSIC -> if (settings.enableQQMusic) {
                qqMusicStrategy.getReleaseDetails(releaseId)
            } else {
                Result.failure(Exception("QQ Music source is disabled"))
            }
            DataSource.BOTH -> {
                if (!settings.hasAnyEnabledSource) {
                    Timber.w("AggregatedRepository.getReleaseDetails: No metadata sources enabled")
                    return Result.failure(Exception("No metadata sources enabled"))
                }

                if (settings.enableITunes) {
                    Timber.i("AggregatedRepository: Trying iTunes for releaseId=$releaseId")
                    val iTunesResult = iTunesStrategy.getReleaseDetails(releaseId)
                    if (iTunesResult.isSuccess) {
                        Timber.i("AggregatedRepository: iTunes succeeded for releaseId=$releaseId")
                        return iTunesResult
                    } else {
                        Timber.w("AggregatedRepository: iTunes failed for releaseId=$releaseId, trying MusicBrainz")
                    }
                }
                if (settings.enableMusicBrainz) {
                    musicBrainzStrategy.getReleaseDetails(releaseId)
                } else {
                    Result.failure(Exception("No release details source enabled"))
                }
            }
        }
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        Timber.d("getCoverArt: releaseId=$releaseId, preferredSource=$preferredSource", TAG)
        val settings = getOnlineSourceSettings()
        Timber.d("getCoverArt: coverEnableMB=${settings.coverEnableMusicBrainz}, coverEnableITunes=${settings.coverEnableITunes}, coverEnableNetease=${settings.coverEnableNetease}, coverEnableQQ=${settings.coverEnableQQMusic}", TAG)

        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> if (settings.coverEnableMusicBrainz) {
                musicBrainzStrategy.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("MusicBrainz source is disabled"))
            }
            DataSource.ITUNES -> if (settings.coverEnableITunes) {
                iTunesStrategy.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("Apple Music source is disabled"))
            }
            DataSource.NETEASE -> if (settings.coverEnableNetease) {
                netEaseStrategy.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("NetEase source is disabled"))
            }
            DataSource.QQ_MUSIC -> if (settings.coverEnableQQMusic) {
                qqMusicStrategy.getCoverArt(releaseId)
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
                            val result = iTunesStrategy.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "musicbrainz" -> if (settings.coverEnableMusicBrainz) {
                            val result = musicBrainzStrategy.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "netease" -> if (settings.coverEnableNetease) {
                            val result = netEaseStrategy.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                        "qq_music" -> if (settings.coverEnableQQMusic) {
                            val result = qqMusicStrategy.getCoverArt(releaseId)
                            if (result.isSuccess && result.getOrNull() != null) return result
                        }
                    }
                }
                Result.success(null)
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
        return iTunesStrategy.searchByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Gets metadata specifically from MusicBrainz.
     */
    suspend fun getFromMusicBrainz(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return musicBrainzStrategy.searchByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Gets metadata specifically from NetEase Cloud Music.
     */
    suspend fun getFromNetease(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return netEaseStrategy.searchByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Gets metadata specifically from QQ Music.
     */
    suspend fun getFromQQMusic(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return qqMusicStrategy.searchByArtistAlbum(artist, album, getOnlineSourceSettings().requestLimit)
    }

    /**
     * Checks if two releases are likely the same based on title similarity.
     */
    private fun isSimilarRelease(release1: OnlineRelease, release2: OnlineRelease): Boolean {
        val title1 = release1.title.lowercase().trim()
        val title2 = release2.title.lowercase().trim()
        
        if (title1 == title2) return true
        
        if (title1.contains(title2) || title2.contains(title1)) return true
        
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

        if (recordings.isEmpty()) {
            return emptyList()
        }

        val withCovers = recordings.filter { !it.coverArtUrl.isNullOrBlank() }
        Timber.d("finalizeRecordingResults: ${withCovers.size} recordings have coverArtUrl")

        if (withCovers.isEmpty()) {
            Timber.d("finalizeRecordingResults: no covers found, returning empty")
            return emptyList()
        }

        val filtered = withCovers.filter { recording ->
            val titleMatch = matchesTitleFlexible(title, recording.title)
            Timber.d("Cover match check: queryTitle='$title', resultTitle='${recording.title}', match=$titleMatch")
            titleMatch
        }

        val resultsToReturn = if (filtered.isEmpty() && withCovers.isNotEmpty()) {
            Timber.d("finalizeRecordingResults: strict filter returned empty, using fallback (all covers)")
            withCovers
        } else {
            filtered
        }

        Timber.d("finalizeRecordingResults: returning ${resultsToReturn.size} recordings")

        val sorted = resultsToReturn.sortedWith(
            compareBy<OnlineRecording> { recording ->
                if (recording.coverArtUrl.isNullOrBlank()) 1 else 0
            }.thenBy { recording ->
                sourcePriorityIndex(recording.source, priority)
            }
        )

        return applyLimit(sorted, limit)
    }

    private fun matchesTitleFlexible(queryTitle: String, resultTitle: String): Boolean {
        if (queryTitle.isBlank() || resultTitle.isBlank()) return false

        val normalizedQuery = queryTitle.trim()
        val normalizedResult = resultTitle.trim()

        if (normalizedResult.equals(normalizedQuery, ignoreCase = true)) {
            return true
        }

        if (normalizedQuery.length <= 3) {
            return normalizedResult.contains(normalizedQuery, ignoreCase = true) ||
                   normalizedQuery.contains(normalizedResult, ignoreCase = true)
        }

        val queryNoBrackets = normalizedQuery.removeBracketContent()
        val resultNoBrackets = normalizedResult.removeBracketContent()

        if (resultNoBrackets.equals(queryNoBrackets, ignoreCase = true)) {
            return true
        }

        if (resultNoBrackets.contains(queryNoBrackets, ignoreCase = true) ||
            queryNoBrackets.contains(resultNoBrackets, ignoreCase = true)) {
            return true
        }

        val queryNoSpaces = queryNoBrackets.replace(" ", "")
        val resultNoSpaces = resultNoBrackets.replace(" ", "")
        if (resultNoSpaces.contains(queryNoSpaces, ignoreCase = true) ||
            queryNoSpaces.contains(resultNoSpaces, ignoreCase = true)) {
            return true
        }

        val querySimplified = queryNoBrackets.toSimplifiedChinese()
        val resultSimplified = resultNoBrackets.toSimplifiedChinese()
        if (resultSimplified.contains(querySimplified) ||
            querySimplified.contains(resultSimplified)) {
            return true
        }

        val querySimplifiedNoSpaces = querySimplified.replace(" ", "")
        val resultSimplifiedNoSpaces = resultSimplified.replace(" ", "")
        return resultSimplifiedNoSpaces.contains(querySimplifiedNoSpaces) ||
               querySimplifiedNoSpaces.contains(resultSimplifiedNoSpaces)
    }

    private fun matchesTitle(queryTitle: String, resultTitle: String): Boolean {
        val normalizedQuery = queryTitle.removeBracketContent()
        val normalizedResult = resultTitle.removeBracketContent()

        if (normalizedResult.contains(normalizedQuery, ignoreCase = true) ||
            normalizedQuery.contains(normalizedResult, ignoreCase = true)) {
            return true
        }

        val queryNoSpaces = normalizedQuery.replace(" ", "")
        val resultNoSpaces = normalizedResult.replace(" ", "")
        if (queryNoSpaces.contains(resultNoSpaces, ignoreCase = true) ||
            resultNoSpaces.contains(queryNoSpaces, ignoreCase = true)) {
            return true
        }

        val simplifiedQuery = normalizedQuery.toSimplifiedChinese()
        val simplifiedResult = normalizedResult.toSimplifiedChinese()

        if (simplifiedResult.contains(simplifiedQuery) || simplifiedQuery.contains(simplifiedResult)) {
            return true
        }

        val simplifiedQueryNoSpaces = simplifiedQuery.replace(" ", "")
        val simplifiedResultNoSpaces = simplifiedResult.replace(" ", "")
        return simplifiedResultNoSpaces.contains(simplifiedQueryNoSpaces) ||
               simplifiedQueryNoSpaces.contains(simplifiedResultNoSpaces)
    }

    private fun String.removeBracketContent(): String {
        return this
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace(Regex("（[^）]*）"), "")
            .trim()
    }

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
}
