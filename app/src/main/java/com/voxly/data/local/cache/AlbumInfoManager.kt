package com.voxly.data.local.cache

import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for album information caching.
 * Handles calculation of album metadata (year, sample rate, bitrate) and
 * maintains cache consistency using content hash based on album name + song count.
 */
@Singleton
class AlbumInfoManager @Inject constructor(
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val metadataProcessor: TagLibMetadataProcessor
) {
    companion object {
        private const val TAG = "AlbumInfoManager"
        private const val MAX_CONCURRENT_YEAR_READS = 4
    }

    private val albumInfoDao: AlbumInfoDao
        get() = databaseProvider.getDatabase().albumInfoDao()

    /**
     * Updates album info in cache if needed.
     * Checks content hash to detect changes in album composition.
     *
     * @param albumName The album name
     * @param albumArtist The album artist (can be null)
     * @param files List of audio files in this album
     * @return The updated or existing AlbumInfoEntity
     */
    suspend fun updateAlbumInfo(
        albumName: String,
        albumArtist: String?,
        files: List<AudioFile>
    ): AlbumInfoEntity = withContext(Dispatchers.IO) {
        val id = AlbumInfoEntity.generateId(albumName, albumArtist)
        val newContentHash = AlbumInfoEntity.generateContentHash(albumName, files.size)

        // Check existing cache
        val existing = albumInfoDao.getAlbumInfoById(id)

        // Always read year to detect tag changes, even if content hash unchanged
        val yearResult = readAlbumYearFromTagLib(files)
        val year = yearResult.year
        val yearHash = AlbumInfoEntity.generateYearHash(yearResult.years)

        // Get highest quality audio
        val (sampleRate, bitrate) = getHighestQuality(files)

        if (existing != null &&
            existing.contentHash == newContentHash &&
            existing.yearHash == yearHash &&
            existing.sampleRate == sampleRate &&
            existing.bitrate == bitrate
        ) {
            // Cache unchanged, return existing
            Timber.tag(TAG).d("Cache hit for album: $albumName (no changes)")
            return@withContext existing
        }

        // Content changed or year/audio info changed - recalculate everything
        Timber.tag(TAG).d("Updating album info: $albumName (${files.size} songs)")

        val entity = AlbumInfoEntity(
            id = id,
            albumName = albumName,
            albumArtist = albumArtist,
            year = year,
            yearHash = yearHash,
            sampleRate = sampleRate,
            bitrate = bitrate,
            contentHash = newContentHash,
            songCount = files.size,
            lastUpdatedAt = System.currentTimeMillis()
        )

        albumInfoDao.insertOrUpdate(entity)
        Timber.tag(TAG).d("Cached album info: $albumName, year=$year, sampleRate=$sampleRate, bitrate=$bitrate")

        entity
    }

    /**
     * Batch update album info for multiple albums.
     * More efficient than individual updates.
     */
    suspend fun updateAlbumInfoBatch(
        albums: Map<Pair<String, String?>, List<AudioFile>>
    ): List<AlbumInfoEntity> = withContext(Dispatchers.IO) {
        val entities = mutableListOf<AlbumInfoEntity>()

        albums.forEach { (key, files) ->
            val (albumName, albumArtist) = key
            val entity = updateAlbumInfo(albumName, albumArtist, files)
            entities.add(entity)
        }

        entities
    }

    /**
     * Gets album info from cache.
     * Returns null if not found.
     */
    suspend fun getAlbumInfo(
        albumName: String,
        albumArtist: String?
    ): AlbumInfoEntity? = withContext(Dispatchers.IO) {
        albumInfoDao.getAlbumInfo(albumName, albumArtist)
    }

    /**
     * Gets album info by ID.
     */
    suspend fun getAlbumInfoById(id: String): AlbumInfoEntity? = withContext(Dispatchers.IO) {
        albumInfoDao.getAlbumInfoById(id)
    }

    /**
     * Gets all album info as a flow.
     */
    fun getAllAlbumInfoFlow() = albumInfoDao.getAllAlbumInfoFlow()

    /**
     * Preloads album info for a list of albums.
     * Useful for list views to avoid individual queries.
     */
    suspend fun preloadAlbumInfo(
        albumKeys: List<Pair<String, String?>>
    ): Map<String, AlbumInfoEntity> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, AlbumInfoEntity>()

        albumKeys.forEach { (albumName, albumArtist) ->
            val id = AlbumInfoEntity.generateId(albumName, albumArtist)
            albumInfoDao.getAlbumInfo(albumName, albumArtist)?.let { entity ->
                result[id] = entity
            }
        }

        result
    }

    /**
     * Gets years for multiple albums efficiently.
     * Returns map of album ID to year.
     */
    suspend fun getAlbumYears(
        albumKeys: List<Pair<String, String?>>
    ): Map<String, String?> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String?>()

        albumKeys.forEach { (albumName, albumArtist) ->
            val id = AlbumInfoEntity.generateId(albumName, albumArtist)
            val year = albumInfoDao.getAlbumInfo(albumName, albumArtist)?.year
            result[id] = year
        }

        result
    }

    /**
     * Deletes album info for a specific album.
     * Call this when an album is deleted or modified.
     */
    suspend fun deleteAlbumInfo(albumName: String, albumArtist: String?) {
        withContext(Dispatchers.IO) {
            albumInfoDao.deleteByAlbum(albumName, albumArtist)
            Timber.tag(TAG).d("Deleted album info: $albumName")
        }
    }

    /**
     * Deletes album info by ID.
     */
    suspend fun deleteAlbumInfoById(id: String) {
        withContext(Dispatchers.IO) {
            albumInfoDao.deleteById(id)
        }
    }

    /**
     * Cleans up orphaned album info entries.
     * Should be called periodically or after bulk deletions.
     */
    suspend fun cleanupOrphanedAlbums() {
        withContext(Dispatchers.IO) {
            val countBefore = albumInfoDao.getCount()
            albumInfoDao.deleteOrphanedAlbums()
            val countAfter = albumInfoDao.getCount()
            val deleted = countBefore - countAfter
            if (deleted > 0) {
                Timber.tag(TAG).d("Cleaned up $deleted orphaned album info entries")
            }
        }
    }

    /**
     * Clears all album info cache.
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            albumInfoDao.deleteAll()
            Timber.tag(TAG).d("Cleared all album info cache")
        }
    }

    /**
     * Reads album year from TagLib metadata.
     * Returns the most common year across all songs.
     */
    private suspend fun readAlbumYearFromTagLib(files: List<AudioFile>): YearReadResult {
        if (files.isEmpty()) return YearReadResult(null, emptyList())

        return try {
            coroutineScope {
                // Read years in parallel with limited concurrency
                val years = files.chunked(MAX_CONCURRENT_YEAR_READS)
                    .flatMap { batch ->
                        batch.map { file ->
                            async {
                                try {
                                    // Force TagLib read for accurate year (bypass caches)
                                    val metadata = metadataProcessor
                                        .readAllMetadata(file.path, includeAlbumArt = false, bypassCache = true)
                                        ?.metadata
                                    val taglibYear = metadata?.year?.takeIf { it.isNotBlank() }
                                    if (taglibYear != null) {
                                        taglibYear
                                    } else {
                                        val mediaStoreYear = metadataProcessor
                                            .readMetadataFromMediaStoreOnly(file.path)
                                            ?.year
                                            ?.takeIf { it.isNotBlank() }
                                        mediaStoreYear
                                            ?: file.metadata.year?.takeIf { it.isNotBlank() }
                                    }
                                } catch (e: Exception) {
                                    // Fallback to cached metadata
                                    file.metadata.year?.takeIf { it.isNotBlank() }
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }

                // Find the most common year
                if (years.isEmpty()) return@coroutineScope YearReadResult(null, emptyList())

                val mostCommonYear = years.groupBy { it }
                    .maxByOrNull { it.value.size }
                    ?.key
                    ?: years.firstOrNull()

                YearReadResult(mostCommonYear, years)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to read album year from TagLib", e)
            // Fallback: use year from first file
            val fallbackYear = files.firstOrNull()?.metadata?.year?.takeIf { it.isNotBlank() }
            YearReadResult(fallbackYear, listOfNotNull(fallbackYear))
        }
    }

    /**
     * Gets the highest quality audio from a list of files.
     * Returns Pair of (sampleRate, bitrate).
     */
    private fun getHighestQuality(files: List<AudioFile>): Pair<Int, Int> {
        if (files.isEmpty()) return Pair(0, 0)

        var maxSampleRate = 0
        var maxBitrate = 0

        files.forEach { file ->
            if (file.sampleRate > maxSampleRate) {
                maxSampleRate = file.sampleRate
            }
            if (file.bitrate > maxBitrate) {
                maxBitrate = file.bitrate
            }
        }

        return Pair(maxSampleRate, maxBitrate)
    }

    /**
     * Checks if album info needs update.
     * Useful for UI to show refresh indicators.
     */
    suspend fun needsUpdate(
        albumName: String,
        albumArtist: String?,
        currentSongCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val id = AlbumInfoEntity.generateId(albumName, albumArtist)
        val existing = albumInfoDao.getAlbumInfoById(id)
            ?: return@withContext true

        val currentHash = AlbumInfoEntity.generateContentHash(albumName, currentSongCount)
        existing.contentHash != currentHash
    }

    /**
     * Gets cache statistics.
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        CacheStats(
            totalAlbums = albumInfoDao.getCount(),
            lastCleanup = System.currentTimeMillis() // We don't track this currently
        )
    }

    data class CacheStats(
        val totalAlbums: Int,
        val lastCleanup: Long
    )

    private data class YearReadResult(
        val year: String?,
        val years: List<String>
    )
}
