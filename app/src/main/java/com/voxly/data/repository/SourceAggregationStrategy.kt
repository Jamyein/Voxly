package com.voxly.data.repository

import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class SourceAggregationStrategy @Inject constructor(
    private val musicBrainzStrategy: MusicBrainzSourceStrategy,
    private val iTunesStrategy: ITunesSourceStrategy,
    private val netEaseStrategy: NetEaseSourceStrategy,
    private val qqMusicStrategy: QQMusicSourceStrategy
) {

    suspend fun searchAllByArtistAlbum(
        artist: String,
        album: String,
        settings: OnlineSourceSettings
    ): Result<List<OnlineRelease>> = coroutineScope {
        val musicBrainzDeferred = if (settings.enableMusicBrainz) {
            async { musicBrainzStrategy.searchByArtistAlbum(artist, album, settings.requestLimit) }
        } else null
        val iTunesDeferred = if (settings.enableITunes) {
            async { iTunesStrategy.searchByArtistAlbum(artist, album, settings.requestLimit) }
        } else null
        val neteaseDeferred = if (settings.enableNetease) {
            async { netEaseStrategy.searchByArtistAlbum(artist, album, settings.requestLimit) }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { qqMusicStrategy.searchByArtistAlbum(artist, album, settings.requestLimit) }
        } else null

        val musicBrainzResult = musicBrainzDeferred?.await()?.map { applyLimit(it, settings.searchLimit) }
        val iTunesResult = iTunesDeferred?.await()?.map { applyLimit(it, settings.searchLimit) }
        val neteaseResult = neteaseDeferred?.await()
        val qqMusicResult = qqMusicDeferred?.await()

        val mergedResults = mutableListOf<OnlineRelease>()

        if (musicBrainzResult?.isSuccess == true) {
            mergedResults.addAll(musicBrainzResult.getOrNull() ?: emptyList())
        }

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

        val sortedResults = mergedResults.sortedWith(compareBy<OnlineRelease> { release ->
            sourcePriorityIndex(release.source, settings.metadataPriority)
        })

        Result.success(sortedResults)
    }

    suspend fun searchAllByTrack(
        title: String,
        artist: String?,
        settings: OnlineSourceSettings
    ): Result<List<OnlineRecording>> = coroutineScope {
        val musicBrainzDeferred = if (settings.enableMusicBrainz) {
            async { musicBrainzStrategy.searchByTrack(title, artist, settings.getSourceLimit("MusicBrainz")) }
        } else null
        val iTunesDeferred = if (settings.enableITunes) {
            async { iTunesStrategy.searchByTrack(title, artist, settings.getSourceLimit("iTunes")) }
        } else null
        val neteaseDeferred = if (settings.enableNetease) {
            async { netEaseStrategy.searchByTrack(title, artist, settings.getSourceLimit("NetEase")) }
        } else null
        val qqMusicDeferred = if (settings.enableQQMusic) {
            async { qqMusicStrategy.searchByTrack(title, artist, settings.getSourceLimit("QQ Music")) }
        } else null

        val results = mutableListOf<OnlineRecording>()

        musicBrainzDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        iTunesDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        neteaseDeferred?.await()?.getOrNull()?.let { results.addAll(it) }
        qqMusicDeferred?.await()?.getOrNull()?.let { results.addAll(it) }

        Result.success(results)
    }

    private fun <T> applyLimit(list: List<T>, limit: Int): List<T> {
        return if (limit <= 0) list else list.take(limit)
    }

    private fun isSimilarRelease(release1: OnlineRelease, release2: OnlineRelease): Boolean {
        val title1 = release1.title.lowercase().trim()
        val title2 = release2.title.lowercase().trim()

        if (title1 == title2) return true
        if (title1.contains(title2) || title2.contains(title1)) return true

        val artist1 = release1.artist.lowercase().trim()
        val artist2 = release2.artist.lowercase().trim()

        return artist1 == artist2 && (title1.contains(title2) || title2.contains(title1))
    }

    private fun sourcePriorityIndex(source: com.voxly.domain.repository.OnlineSource, priority: List<String>): Int {
        val key = when (source) {
            com.voxly.domain.repository.OnlineSource.ITUNES -> "itunes"
            com.voxly.domain.repository.OnlineSource.MUSICBRAINZ -> "musicbrainz"
            com.voxly.domain.repository.OnlineSource.NETEASE -> "netease"
            com.voxly.domain.repository.OnlineSource.QQ_MUSIC -> "qq_music"
            com.voxly.domain.repository.OnlineSource.UNKNOWN -> "unknown"
        }
        val index = priority.indexOf(key)
        return if (index >= 0) index else Int.MAX_VALUE
    }
}
