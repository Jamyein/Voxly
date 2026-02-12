package com.voxly.data.repository

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

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
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            return Result.failure(Exception("No metadata sources enabled"))
        }
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.searchByArtistAlbum(artist, album)
                        .map { it.take(settings.searchLimit) }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.ITUNES -> {
                if (settings.enableITunes) {
                    iTunesRepository.searchByArtistAlbum(artist, album)
                        .map { it.take(settings.searchLimit) }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.NETEASE -> {
                if (settings.enableNetease) {
                    searchNeteaseByArtistAlbum(artist, album, settings.searchLimit)
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.QQ_MUSIC -> {
                if (settings.enableQQMusic) {
                    searchQQMusicByArtistAlbum(artist, album, settings.searchLimit)
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.BOTH -> searchAllSources(artist, album, settings)
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
            async { searchNeteaseByArtistAlbum(artist, album, settings.searchLimit) }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { searchQQMusicByArtistAlbum(artist, album, settings.searchLimit) }
        } else null

        val musicBrainzResult = musicBrainzDeferred?.await()?.map { it.take(settings.searchLimit) }
        val iTunesResult = iTunesDeferred?.await()?.map { it.take(settings.searchLimit) }
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

        // Sort by relevance
        val sortedResults = mergedResults.sortedByDescending { release ->
            var score = 0
            if (release.coverArtUrl != null) score += 2
            if (release.year != null) score += 1
            if (release.trackCount != null) score += 1
            score
        }

        Result.success(sortedResults.take(settings.searchLimit))
    }

    /**
     * Searches NetEase Cloud Music by artist and album.
     */
    private suspend fun searchNeteaseByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
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
                        coverArtUrl = firstSong.album?.picUrl
                    )
                }
                Result.success(albums)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
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
                            coverArtUrl = "https://y.gtimg.cn/music/photo_new/T002R500x500M000${id}.jpg"
                        )
                    }
                }
                Result.success(albums)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> {
        val settings = getOnlineSourceSettings()
        if (!settings.hasAnyEnabledSource) {
            return Result.failure(Exception("No metadata sources enabled"))
        }
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> {
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.searchByTrack(title, artist)
                        .map { it.take(settings.searchLimit) }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.ITUNES -> {
                if (settings.enableITunes) {
                    iTunesRepository.searchByTrack(title, artist)
                        .map { it.take(settings.searchLimit) }
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.NETEASE -> {
                if (settings.enableNetease) {
                    searchNeteaseByTrack(title, artist, settings.searchLimit)
                } else {
                    Result.success(emptyList())
                }
            }
            DataSource.QQ_MUSIC -> {
                if (settings.enableQQMusic) {
                    searchQQMusicByTrack(title, artist, settings.searchLimit)
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
                        async { searchNeteaseByTrack(title, artist, settings.searchLimit) }
                    } else null
                    val qqMusicDeferred = if (settings.enableQQMusic) {
                        async { searchQQMusicByTrack(title, artist, settings.searchLimit) }
                    } else null

                    val results = mutableListOf<OnlineRecording>()
                    
                    musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(it.take(settings.searchLimit)) }
                    iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(it.take(settings.searchLimit)) }
                    neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
                    qqMusicDeferred?.await()?.getOrNull()?.let { results.addAll(it) }

                    Result.success(results.take(settings.searchLimit))
                }
            }
        }
    }

    /**
     * Searches NetEase by track title.
     */
    private suspend fun searchNeteaseByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
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
                        releaseId = song.album?.id?.toString()
                    )
                }
                Result.success(recordings)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
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
                        releaseId = song.album?.id?.toString()
                    )
                }
                Result.success(recordings)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
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
                ))
            } else {
                Result.failure(Exception("Failed to get NetEase album details"))
            }
        } catch (e: Exception) {
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
                        "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg"
                    } else null
                ))
            } else {
                Result.failure(Exception("Failed to get QQ Music album details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        val settings = getOnlineSourceSettings()
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> if (settings.enableMusicBrainz) {
                musicBrainzRepository.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("MusicBrainz source is disabled"))
            }
            DataSource.ITUNES -> if (settings.enableITunes) {
                iTunesRepository.getCoverArt(releaseId)
            } else {
                Result.failure(Exception("Apple Music source is disabled"))
            }
            DataSource.NETEASE -> if (settings.enableNetease) {
                getNeteaseCoverArt(releaseId)
            } else {
                Result.failure(Exception("NetEase source is disabled"))
            }
            DataSource.QQ_MUSIC -> if (settings.enableQQMusic) {
                getQQMusicCoverArt(releaseId)
            } else {
                Result.failure(Exception("QQ Music source is disabled"))
            }
            DataSource.BOTH -> {
                if (!settings.hasAnyEnabledSource) {
                    return Result.failure(Exception("No metadata sources enabled"))
                }

                if (settings.enableITunes) {
                    val iTunesResult = iTunesRepository.getCoverArt(releaseId)
                    if (iTunesResult.isSuccess && iTunesResult.getOrNull() != null) {
                        return iTunesResult
                    }
                }
                if (settings.enableMusicBrainz) {
                    musicBrainzRepository.getCoverArt(releaseId)
                } else {
                    Result.success(null)
                }
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
                val coverUrl = album?.album?.picUrl
                if (coverUrl != null) {
                    // Download cover art
                    val url = java.net.URL(coverUrl)
                    val connection = url.openConnection()
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    val bytes = connection.getInputStream().use { it.readBytes() }
                    Result.success(bytes)
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets QQ Music album cover art.
     */
    private suspend fun getQQMusicCoverArt(albumId: String): Result<ByteArray?> {
        return try {
            val coverUrl = "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg"
            val url = java.net.URL(coverUrl)
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Referer", "https://y.qq.com")
            val bytes = connection.getInputStream().use { it.readBytes() }
            Result.success(bytes)
        } catch (e: Exception) {
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
        return searchNeteaseByArtistAlbum(artist, album, getOnlineSourceSettings().searchLimit)
    }

    /**
     * Gets metadata specifically from QQ Music.
     */
    suspend fun getFromQQMusic(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return searchQQMusicByArtistAlbum(artist, album, getOnlineSourceSettings().searchLimit)
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

    private suspend fun getOnlineSourceSettings(): OnlineSourceSettings {
        return OnlineSourceSettings(
            enableMusicBrainz = settingsDataStore.sourceEnabledMusicBrainz.first(),
            enableITunes = settingsDataStore.sourceEnabledITunes.first(),
            enableNetease = settingsDataStore.sourceEnabledNetease.first(),
            enableQQMusic = settingsDataStore.sourceEnabledQQMusic.first(),
            searchLimit = settingsDataStore.onlineSearchLimit.first().coerceIn(5, 50)
        )
    }

    private data class OnlineSourceSettings(
        val enableMusicBrainz: Boolean,
        val enableITunes: Boolean,
        val enableNetease: Boolean,
        val enableQQMusic: Boolean,
        val searchLimit: Int
    ) {
        val hasAnyEnabledSource: Boolean
            get() = enableMusicBrainz || enableITunes || enableNetease || enableQQMusic
    }
}
