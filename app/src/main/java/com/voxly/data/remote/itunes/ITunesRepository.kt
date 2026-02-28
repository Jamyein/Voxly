package com.voxly.data.remote.itunes

import com.voxly.data.helper.SearchQueryBuilder
import com.voxly.data.mapper.OnlineRecordingMapper
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of OnlineMetadataRepository using iTunes Search API.
 * Provides access to Apple's music catalog metadata.
 * 
 * Advantages:
 * - No API key required (public API)
 * - High-quality artwork images
 * - Fast response times
 * - Comprehensive metadata
 */
@Singleton
class ITunesRepository @Inject constructor(
    private val iTunesApi: ITunesApi,
    private val settingsDataStore: SettingsDataStore
) : OnlineMetadataRepository {

    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 200
    }

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String
    ): Result<List<OnlineRelease>> = withContext(Dispatchers.IO) {
        try {
            val searchTerm = buildString {
                append(artist)
                if (album.isNotBlank()) {
                    append(" ")
                    append(album)
                }
            }
            val searchSettings = getSearchSettings()

            val response = iTunesApi.searchAlbums(
                term = searchTerm,
                limit = searchSettings.limit,
                country = searchSettings.countryCode
            )

            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                
                // Filter and map to OnlineRelease
                val releases = results
                    .filter { it.wrapperType == "collection" || it.collectionId != null }
                    .map { result ->
                        OnlineRelease(
                            id = result.collectionId?.toString() ?: result.artistId.toString(),
                            title = result.collectionName ?: "Unknown Album",
                            artist = result.artistName ?: "Unknown Artist",
                            year = result.getReleaseYear(),
                            format = "iTunes Album",
                            trackCount = result.trackCount,
                            coverArtUrl = getHighResArtworkUrl(result.getBestArtworkUrl(), 3000),
                            source = "iTunes",
                            albumTitle = result.collectionName,
                            genre = result.primaryGenreName,
                            albumArtist = result.collectionArtistName ?: result.artistName,
                            discNumber = result.discNumber,
                            discCount = result.discCount,
                            trackNumber = result.trackNumber
                        )
                    }
                    .distinctBy { it.id } // Remove duplicates

                Result.success(releases)
            } else {
                Result.failure(Exception("iTunes search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> = withContext(Dispatchers.IO) {
        try {
            // 统一查询格式：title artist (title在前，空格分隔)
            val searchTerm = SearchQueryBuilder.build(title, artist)
            val searchSettings = getSearchSettings()

            val response = iTunesApi.searchSongs(
                term = searchTerm,
                limit = searchSettings.limit,
                country = searchSettings.countryCode
            )

            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                
                val recordings = results
                    .filter { it.wrapperType == "track" }
                    .map { result ->
                        OnlineRecordingMapper.fromITunes(
                            trackId = result.trackId,
                            trackName = result.trackName,
                            artistName = result.artistName,
                            durationMs = result.trackTimeMillis,
                            collectionId = result.collectionId,
                            artworkUrl100 = result.artworkUrl100,
                            primaryGenreName = result.primaryGenreName,
                            collectionArtistName = result.collectionArtistName,
                            discNumber = result.discNumber,
                            discCount = result.discCount,
                            trackNumber = result.trackNumber,
                            trackCount = result.trackCount,
                            collectionName = result.collectionName,  // 传递专辑名
                            releaseDate = result.releaseDate  // 传递发布日期
                        )
                    }
                    .filter { it.id.isNotBlank() }

                Result.success(recordings)
            } else {
                Result.failure(Exception("iTunes track search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReleaseDetails(
        releaseId: String
    ): Result<OnlineReleaseDetails> = withContext(Dispatchers.IO) {
        try {
            Timber.d("ITunesRepository.getReleaseDetails: releaseId=$releaseId")
            val searchSettings = getSearchSettings()
            Timber.d("ITunesRepository: countryCode=${searchSettings.countryCode}, limit=${searchSettings.limit}")
            
            // First lookup the album
            val lookupResponse = iTunesApi.lookup(
                id = releaseId.toLong(),
                country = searchSettings.countryCode
            )
            
            Timber.d("ITunesRepository.lookup: isSuccessful=${lookupResponse.isSuccessful}, resultCount=${lookupResponse.body()?.resultCount}")
            
            if (!lookupResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to lookup release: ${lookupResponse.errorBody()?.string()}")
                )
            }

            val albumResult = lookupResponse.body()?.results?.firstOrNull()
                ?: return@withContext Result.failure(Exception("Release not found in iTunes: results is empty"))

            // Then search for tracks in this album
            val tracksResponse = iTunesApi.search(
                term = "${albumResult.artistName} ${albumResult.collectionName}",
                entity = ITunesEntity.MUSIC_TRACK.value,
                limit = MAX_LIMIT,
                country = searchSettings.countryCode
            )

            val tracks = if (tracksResponse.isSuccessful) {
                tracksResponse.body()?.results
                    ?.filter { it.collectionId?.toString() == releaseId }
                    ?.sortedBy { it.trackNumber ?: 0 }
                    ?.map { track ->
                        OnlineTrack(
                            number = track.trackNumber ?: 0,
                            title = track.trackName ?: "Unknown Track",
                            duration = track.getDurationSeconds(),
                            artist = track.artistName,
                            discNumber = track.discNumber
                        )
                    } ?: emptyList()
            } else {
                emptyList()
            }

            val details = OnlineReleaseDetails(
                id = releaseId,
                title = albumResult.collectionName ?: "Unknown Album",
                artist = albumResult.artistName ?: "Unknown Artist",
                year = albumResult.getReleaseYear(),
                genre = albumResult.primaryGenreName,
                trackCount = albumResult.trackCount ?: tracks.size,
                tracks = tracks,
                coverArtUrl = getHighResArtworkUrl(albumResult.getBestArtworkUrl(), 3000),
                discCount = albumResult.discCount,
                albumArtist = albumResult.collectionArtistName ?: albumResult.artistName
            )

            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                val searchSettings = getSearchSettings()
                // Get the album details to find the artwork URL
                val lookupResponse = iTunesApi.lookup(
                    id = releaseId.toLong(),
                    country = searchSettings.countryCode
                )
                
                if (!lookupResponse.isSuccessful) {
                    return@withContext Result.success(null)
                }

                val artworkUrl = lookupResponse.body()?.results?.firstOrNull()
                    ?.getBestArtworkUrl()
                    ?.let { getHighResArtworkUrl(it, 3000) } // Get higher resolution

                if (artworkUrl.isNullOrBlank()) {
                    return@withContext Result.success(null)
                }

                // Download the artwork
                val url = URL(artworkUrl)
                val connection = url.openConnection()
                connection.setRequestProperty("User-Agent", "MP3TagAndroid/1.0")
                
                val inputStream = connection.getInputStream()
                val bytes = inputStream.readBytes()
                inputStream.close()

                Result.success(bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Searches for songs with advanced filtering options.
     * iTunes-specific extension.
     * 
     * @param term Search term
     * @param entity Entity type (song, album, artist)
     * @param limit Maximum results (1-200)
     * @param country Country code
     * @return Search results
     */
    suspend fun searchAdvanced(
        term: String,
        entity: ITunesEntity = ITunesEntity.MUSIC_TRACK,
        limit: Int = DEFAULT_LIMIT,
        country: ITunesCountry? = null
    ): Result<List<ITunesResult>> = withContext(Dispatchers.IO) {
        try {
            val fallbackCountry = getSearchSettings().countryCode
            val response = iTunesApi.search(
                term = term,
                entity = entity.value,
                limit = limit.coerceIn(1, MAX_LIMIT),
                country = country?.code ?: fallbackCountry
            )

            if (response.isSuccessful) {
                Result.success(response.body()?.results ?: emptyList())
            } else {
                Result.failure(Exception("Search failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets high-resolution artwork URL for a track or album.
     * iTunes provides high-quality artwork that can be accessed by modifying the URL.
     * 
     * @param artworkUrl Original artwork URL (100x100)
     * @param size Desired size (e.g., 600, 1200, 1400)
     * @return High-resolution artwork URL
     */
    fun getHighResArtworkUrl(artworkUrl: String?, size: Int = 3000): String? {
        return artworkUrl
            ?.replace(Regex("\\d+x\\d+"), "${size}x${size}")
            ?.replace("http://", "https://")
    }

    private suspend fun getSearchSettings(): SearchSettings {
        val countryCode = settingsDataStore.appleCountryCode.first()
            .trim()
            .uppercase()
            .ifBlank { "US" }
        val rawLimit = settingsDataStore.onlineSearchLimit.first()
        val limit = if (rawLimit <= 0) MAX_LIMIT else rawLimit.coerceIn(1, MAX_LIMIT)
        return SearchSettings(countryCode, limit)
    }

    private data class SearchSettings(
        val countryCode: String,
        val limit: Int
    )
}
