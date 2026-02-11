package com.mp3tag.android.data.repository

import com.mp3tag.android.data.remote.itunes.ITunesRepository
import com.mp3tag.android.data.remote.musicbrainz.MusicBrainzRepository
import com.mp3tag.android.domain.repository.OnlineMetadataRepository
import com.mp3tag.android.domain.repository.OnlineRelease
import com.mp3tag.android.domain.repository.OnlineReleaseDetails
import com.mp3tag.android.domain.repository.OnlineRecording
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregated repository that combines multiple online metadata sources.
 * Currently supports MusicBrainz and iTunes/Apple Music.
 * 
 * This repository queries all available sources and merges the results,
 * giving users the best metadata from multiple providers.
 */
@Singleton
class AggregatedOnlineMetadataRepository @Inject constructor(
    private val musicBrainzRepository: MusicBrainzRepository,
    private val iTunesRepository: ITunesRepository
) : OnlineMetadataRepository {

    /**
     * Data source preference for metadata lookup.
     */
    enum class DataSource {
        MUSICBRAINZ,
        ITUNES,
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
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> musicBrainzRepository.searchByArtistAlbum(artist, album)
            DataSource.ITUNES -> iTunesRepository.searchByArtistAlbum(artist, album)
            DataSource.BOTH -> searchBothSources(artist, album)
        }
    }

    /**
     * Searches both MusicBrainz and iTunes and merges results.
     */
    private suspend fun searchBothSources(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> = coroutineScope {
        // Query both sources concurrently
        val musicBrainzDeferred = async {
            musicBrainzRepository.searchByArtistAlbum(artist, album)
        }
        val iTunesDeferred = async {
            iTunesRepository.searchByArtistAlbum(artist, album)
        }

        val musicBrainzResult = musicBrainzDeferred.await()
        val iTunesResult = iTunesDeferred.await()

        // Merge results, prioritizing based on completeness
        val mergedResults = mutableListOf<OnlineRelease>()
        
        // Add MusicBrainz results first
        if (musicBrainzResult.isSuccess) {
            mergedResults.addAll(musicBrainzResult.getOrNull() ?: emptyList())
        }

        // Add iTunes results, avoiding duplicates based on title similarity
        if (iTunesResult.isSuccess) {
            val iTunesReleases = iTunesResult.getOrNull() ?: emptyList()
            
            iTunesReleases.forEach { iTunesRelease ->
                val isDuplicate = mergedResults.any { existing ->
                    isSimilarRelease(existing, iTunesRelease)
                }
                
                if (!isDuplicate) {
                    mergedResults.add(iTunesRelease)
                }
            }
        }

        // Sort by relevance (you could implement more sophisticated sorting)
        val sortedResults = mergedResults.sortedByDescending { release ->
            // Prioritize releases with cover art and complete metadata
            var score = 0
            if (release.coverArtUrl != null) score += 2
            if (release.year != null) score += 1
            if (release.trackCount != null) score += 1
            score
        }

        Result.success(sortedResults)
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> {
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> musicBrainzRepository.searchByTrack(title, artist)
            DataSource.ITUNES -> iTunesRepository.searchByTrack(title, artist)
            DataSource.BOTH -> {
                // For track search, prefer iTunes as it tends to have better track-level data
                val iTunesResult = iTunesRepository.searchByTrack(title, artist)
                if (iTunesResult.isSuccess && !iTunesResult.getOrNull().isNullOrEmpty()) {
                    iTunesResult
                } else {
                    musicBrainzRepository.searchByTrack(title, artist)
                }
            }
        }
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> musicBrainzRepository.getReleaseDetails(releaseId)
            DataSource.ITUNES -> iTunesRepository.getReleaseDetails(releaseId)
            DataSource.BOTH -> {
                // Try iTunes first for better cover art and track info
                val iTunesResult = iTunesRepository.getReleaseDetails(releaseId)
                if (iTunesResult.isSuccess) {
                    iTunesResult
                } else {
                    musicBrainzRepository.getReleaseDetails(releaseId)
                }
            }
        }
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return when (preferredSource) {
            DataSource.MUSICBRAINZ -> musicBrainzRepository.getCoverArt(releaseId)
            DataSource.ITUNES -> iTunesRepository.getCoverArt(releaseId)
            DataSource.BOTH -> {
                // iTunes generally has higher quality artwork
                val iTunesResult = iTunesRepository.getCoverArt(releaseId)
                if (iTunesResult.isSuccess && iTunesResult.getOrNull() != null) {
                    iTunesResult
                } else {
                    musicBrainzRepository.getCoverArt(releaseId)
                }
            }
        }
    }

    /**
     * Gets metadata specifically from iTunes/Apple Music.
     * Use this when you want Apple Music specific data.
     */
    suspend fun getFromITunes(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return iTunesRepository.searchByArtistAlbum(artist, album)
    }

    /**
     * Gets metadata specifically from MusicBrainz.
     * Use this when you want MusicBrainz specific data.
     */
    suspend fun getFromMusicBrainz(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> {
        return musicBrainzRepository.searchByArtistAlbum(artist, album)
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
}
