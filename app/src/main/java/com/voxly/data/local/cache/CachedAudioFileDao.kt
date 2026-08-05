package com.voxly.data.local.cache

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.voxly.core.util.PathUtils
import kotlinx.coroutines.flow.Flow
import kotlin.text.RegexOption

/**
 * DAO for cached audio files.
 * Provides efficient queries for instant app startup.
 */
@Dao
interface CachedAudioFileDao {

    // ==================== Queries ====================
    
    /**
     * Gets all cached audio files, sorted by title.
     * Returns Flow for reactive UI updates.
     * Uses pre-computed sortTitle column for B-tree index usage.
     */
    @Query("SELECT * FROM cached_audio_files ORDER BY sortTitle ASC")
    fun getAllAudioFiles(): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets audio files by directory path prefix.
     * Uses range query (>= / <) instead of GLOB for B-tree index usage on the primary key.
     */
    @Query("SELECT * FROM cached_audio_files WHERE path >= :directoryPath AND path < :directoryPath || 'zzzzzzzz' ORDER BY path ASC")
    fun getAudioFilesByDirectory(directoryPath: String): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets a single audio file by path.
     */
    @Query("SELECT * FROM cached_audio_files WHERE path = :path LIMIT 1")
    suspend fun getAudioFileByPath(path: String): CachedAudioFileEntity?
    
    /**
     * Searches audio files by title, artist, or album using FTS4 full-text search.
     * Uses MATCH for efficient indexed queries instead of slow LIKE '%query%'.
     *
     * Join note: the FTS4 `contentEntity` shadow table is keyed on the base
     * table's `rowid` (SQLite's implicit 64-bit rowid), NOT on the user-defined
     * primary key. Previously the join was `cached_audio_files.id = fts.rowid`
     * which only worked because the previous `id: String` happened to be the
     * rowid alias. After the v15 refactor `id` no longer exists — we join on
     * the implicit `rowid` directly.
     */
    @Query("""
        SELECT cached_audio_files.* FROM cached_audio_files
        JOIN cached_audio_files_fts ON cached_audio_files.rowid = cached_audio_files_fts.rowid
        WHERE cached_audio_files_fts MATCH :query || '*'
        ORDER BY cached_audio_files.sortTitle ASC
    """)
    fun searchAudioFiles(query: String): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets count of cached files.
     */
    @Query("SELECT COUNT(*) FROM cached_audio_files")
    suspend fun getCachedFileCount(): Int
    
    /**
     * Gets the last scan timestamp.
     */
    @Query("SELECT MAX(lastScannedAt) FROM cached_audio_files")
    suspend fun getLastScanTime(): Long?
    
    /**
     * Checks if cache has any data.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_audio_files LIMIT 1)")
    suspend fun hasCache(): Boolean

    /**
     * Deterministic fingerprint of the cached file rows: count + the most
     * recent per-row write time + the most recent file mtime observed at
     * write time. Any add/update/delete/sync changes at least one of the
     * three, so the fingerprint changes whenever the cache content changes.
     * Used to validate the persisted [com.voxly.data.local.cache.AggregateSnapshotEntity].
     */
    @Query("""
        SELECT CAST(COUNT(*) AS TEXT) || '|' ||
               IFNULL(CAST(MAX(lastScannedAt) AS TEXT), '0') || '|' ||
               IFNULL(CAST(MAX(fileLastModifiedAt) AS TEXT), '0')
        FROM cached_audio_files
    """)
    suspend fun getContentFingerprint(): String
    
    /**
     * Gets file paths and modification times for incremental scan.
     */
    @Query("SELECT path, fileLastModifiedAt FROM cached_audio_files")
    suspend fun getFilePathsWithModificationTimes(): List<FilePathWithModification>
    
    /**
     * Data class for file path with modification time.
     */
    data class FilePathWithModification(
        val path: String,
        val fileLastModifiedAt: Long
    )
    
    @Query("SELECT path, album, albumArtist, artist FROM cached_audio_files WHERE path IN (:paths)")
    suspend fun getAlbumInfoByPaths(paths: List<String>): List<AlbumPathInfo>

    /** Full entities for a bounded set of paths (used to materialize only the backfill candidates). */
    @Query("SELECT * FROM cached_audio_files WHERE path IN (:paths)")
    suspend fun getAudioFilesByPaths(paths: List<String>): List<CachedAudioFileEntity>

    /**
     * Paths of cached files missing core metadata (year, sampleRate, or album) —
     * the backfill candidate set. Path-only projection avoids materializing the
     * whole library just to find what needs enrichment.
     */
    @Query("""
        SELECT path FROM cached_audio_files
        WHERE year IS NULL OR year = '' OR sampleRate = 0 OR album IS NULL OR album = ''
    """)
    suspend fun getPathsMissingMetadata(): List<String>

    /**
     * Minimal projection used by [com.voxly.data.local.replaygain.AlbumGroupingProvider].
     * Contains the album identity fields needed for ReplayGain album grouping.
     */
    data class AlbumPathInfo(
        val path: String,
        val album: String?,
        val albumArtist: String?,
        val artist: String?
    )
    
    // ==================== Inserts/Updates ====================
    
    /**
     * Inserts a single audio file.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audioFile: CachedAudioFileEntity)
    
    /**
     * Inserts multiple audio files efficiently.
     * Uses transaction internally for batch insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioFiles: List<CachedAudioFileEntity>)
    
    /**
     * Inserts multiple audio files in chunks for very large libraries.
     * Prevents memory issues with 10,000+ files.
     */
    @Transaction
    suspend fun insertAllChunked(audioFiles: List<CachedAudioFileEntity>, chunkSize: Int = 500) {
        audioFiles.chunked(chunkSize).forEach { chunk ->
            insertAll(chunk)
        }
    }
    
    /**
     * Updates a single audio file.
     */
    @Update
    suspend fun update(audioFile: CachedAudioFileEntity)
    
    /**
     * Updates the lastEditedByUserAt timestamp for a specific file path.
     * Used to mark that user has edited this file, preventing EnrichmentWorker overwrites.
     */
    @Query("UPDATE cached_audio_files SET lastEditedByUserAt = :timestamp WHERE path = :path")
    suspend fun updateLastEditedByUserAt(path: String, timestamp: Long)
    
    // ==================== Deletes ====================
    
    /**
     * Deletes a single audio file by path.
     */
    @Query("DELETE FROM cached_audio_files WHERE path = :path")
    suspend fun deleteByPath(path: String)
    
    /**
     * Deletes multiple audio files by paths.
     * Uses batch DELETE for efficiency - single query instead of N queries.
     */
    @Query("DELETE FROM cached_audio_files WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
    
    /**
     * Deletes audio files not in the provided list of paths.
     * Used to clean up deleted files.
     */
    @Query("DELETE FROM cached_audio_files WHERE path NOT IN (:validPaths)")
    suspend fun deleteNotInPaths(validPaths: List<String>): Int
    
    /**
     * Clears all cached audio files.
     */
    @Query("DELETE FROM cached_audio_files")
    suspend fun deleteAll()

    // ==================== Statistics Queries ====================

    /**
     * Gets total count of audio files.
     */
    @Query("SELECT COUNT(*) FROM cached_audio_files")
    suspend fun getTotalFileCount(): Int

    /**
     * Gets total duration in milliseconds.
     */
    @Query("SELECT COALESCE(SUM(duration), 0) FROM cached_audio_files")
    suspend fun getTotalDuration(): Long

    /**
     * Gets total file size in bytes.
     */
    @Query("SELECT COALESCE(SUM(size), 0) FROM cached_audio_files")
    suspend fun getTotalSize(): Long

    /**
     * Gets format distribution - format and count.
     */
    @Query("SELECT format, COUNT(*) as count FROM cached_audio_files GROUP BY format ORDER BY count DESC")
    suspend fun getFormatDistribution(): List<FormatCount>

    /**
     * Gets top artists by file count.
     */
    @Query("""
        SELECT artist, COUNT(*) as count
        FROM cached_audio_files
        WHERE artist IS NOT NULL AND artist != ''
        GROUP BY artist
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(limit: Int): List<ArtistCount>

    /**
     * Gets top albums by file count.
     */
    @Query("""
        SELECT album, artist, COUNT(*) as count
        FROM cached_audio_files
        WHERE album IS NOT NULL AND album != ''
        GROUP BY album, artist
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getTopAlbums(limit: Int): List<AlbumCount>

    /**
     * Gets genre distribution - genre and count.
     */
    @Query("""
        SELECT genre, COUNT(*) as count
        FROM cached_audio_files
        WHERE genre IS NOT NULL AND genre != ''
        GROUP BY genre
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getGenreDistribution(limit: Int): List<GenreCount>

    /**
     * Gets year distribution - year group and count.
     * Year is grouped into ranges dynamically based on current year.
     */
    @Query("""
        SELECT year, COUNT(*) as count
        FROM cached_audio_files
        WHERE year IS NOT NULL AND year != '' AND year != '0'
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearDistribution(): List<YearCount>

    /**
     * Gets bitrate distribution - bitrate group and count.
     * Grouping: SQ (<192), HQ (192-320), HiFi (>320)
     */
    @Query("SELECT bitrate, COUNT(*) as count FROM cached_audio_files GROUP BY bitrate ORDER BY bitrate ASC")
    suspend fun getBitrateDistribution(): List<BitrateRawCount>

    // ==================== Statistics Queries with Path Filtering ====================

    /**
     * Gets total count of audio files with optional path filtering.
     * @param query SupportSQLiteQuery with path filter clause
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getTotalFileCountFiltered(query: SupportSQLiteQuery): Int

    /**
     * Gets total duration with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getTotalDurationFiltered(query: SupportSQLiteQuery): Long

    /**
     * Gets total file size with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getTotalSizeFiltered(query: SupportSQLiteQuery): Long

    /**
     * Gets format distribution with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getFormatDistributionFiltered(query: SupportSQLiteQuery): List<FormatCount>

    /**
     * Gets top artists with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getTopArtistsFiltered(query: SupportSQLiteQuery): List<ArtistCount>

    /**
     * Gets top albums with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getTopAlbumsFiltered(query: SupportSQLiteQuery): List<AlbumCount>

    /**
     * Gets genre distribution with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getGenreDistributionFiltered(query: SupportSQLiteQuery): List<GenreCount>

    /**
     * Gets year distribution with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getYearDistributionFiltered(query: SupportSQLiteQuery): List<YearCount>

    /**
     * Gets bitrate distribution with optional path filtering.
     */
    @RawQuery(observedEntities = [CachedAudioFileEntity::class])
    suspend fun getBitrateDistributionFiltered(query: SupportSQLiteQuery): List<BitrateRawCount>

    /**
     * Builds a SupportSQLiteQuery for path filtering from whitelist and blacklist paths.
     * @param whitelistPaths List of allowed directory paths (null or empty means no whitelist)
     * @param blacklistPaths List of blocked directory paths (null or empty means no blacklist)
     * @param baseSql The base SQL query (e.g., "SELECT COUNT(*) FROM cached_audio_files")
     * @param limit Limit for TOP queries (optional)
     * @return SupportSQLiteQuery or null if no filtering needed
     */
    fun buildPathFilterQuery(
        whitelistPaths: List<String>?,
        blacklistPaths: List<String>?,
        baseSql: String,
        limit: Int? = null
    ): SupportSQLiteQuery? {
        val whitelist = whitelistPaths?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        val blacklist = blacklistPaths?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

        // No filtering needed - return query without WHERE clause
        if (whitelist.isNullOrEmpty() && blacklist.isNullOrEmpty()) {
            return if (limit != null) {
                SimpleSQLiteQuery("$baseSql LIMIT ?", arrayOf(limit))
            } else {
                SimpleSQLiteQuery(baseSql)
            }
        }

        val args = mutableListOf<Any>()
        val conditions = mutableListOf<String>()

        // Whitelist: file must start with one of the whitelist prefixes
        if (!whitelist.isNullOrEmpty()) {
            val whitelistConditions = whitelist.map { path ->
                val normalizedPath = PathUtils.normalizeFilePath(path)
                args.add("$normalizedPath/%")
                "path LIKE ?"
            }
            conditions.add("(${whitelistConditions.joinToString(" OR ")})")
        }

        // Blacklist: file must NOT start with any of the blacklist prefixes
        if (!blacklist.isNullOrEmpty()) {
            val blacklistConditions = blacklist.map { path ->
                val normalizedPath = PathUtils.normalizeFilePath(path)
                args.add("$normalizedPath/%")
                "path LIKE ?"
            }
            conditions.add("NOT (${blacklistConditions.joinToString(" OR ")})")
        }

        val whereClause = conditions.joinToString(" AND ")

        // Insert WHERE clause at the correct position in SQL
        // Order: WHERE -> GROUP BY -> ORDER BY -> LIMIT
        val finalSql = when {
            // Case 1: Has WHERE (append to existing WHERE) - must check this FIRST
            baseSql.uppercase().contains(" WHERE ") -> {
                // Append to existing WHERE - need to find the position before GROUP BY/ORDER BY/LIMIT
                val groupByMatch = Regex("(?i) GROUP BY ", RegexOption.IGNORE_CASE).find(baseSql)
                val orderByMatch = Regex("(?i) ORDER BY ", RegexOption.IGNORE_CASE).find(baseSql)
                val limitMatch = Regex(" LIMIT ", RegexOption.IGNORE_CASE).find(baseSql)

                val insertPos = listOfNotNull(groupByMatch, orderByMatch, limitMatch)
                    .minByOrNull { it.range.first }
                    ?.range?.first
                    ?: baseSql.length

                val before = baseSql.substring(0, insertPos)
                val after = baseSql.substring(insertPos)
                "$before AND $whereClause$after"
            }
            // Case 2: Has GROUP BY (with or without ORDER BY) - WHERE must come before GROUP BY
            baseSql.uppercase().contains(" GROUP BY ") -> {
                baseSql.replace(Regex("(?i)( GROUP BY )", RegexOption.IGNORE_CASE),
                    " WHERE $whereClause$1")
            }
            // Case 3: Has ORDER BY but no GROUP BY
            baseSql.uppercase().contains(" ORDER BY ") -> {
                baseSql.replace(Regex("(?i)( ORDER BY )", RegexOption.IGNORE_CASE),
                    " WHERE $whereClause$1")
            }
            // Case 4: No special clauses
            else -> "$baseSql WHERE $whereClause"
        } + if (limit != null) " LIMIT ?" else ""

        if (limit != null) {
            args.add(limit)
        }

        return SimpleSQLiteQuery(finalSql, args.toTypedArray())
    }
}

/**
 * Data class for format count.
 */
data class FormatCount(
    val format: String,
    val count: Int
)

/**
 * Data class for artist count.
 */
data class ArtistCount(
    val artist: String,
    val count: Int
)

/**
 * Data class for album count.
 */
data class AlbumCount(
    val album: String,
    val artist: String?,
    val count: Int
)

/**
 * Data class for bitrate count.
 */
data class BitrateCount(
    val bitrateGroup: String,  // "SQ", "HQ", "HiFi"
    val count: Int
)

/**
 * Data class for genre count.
 */
data class GenreCount(
    val genre: String,
    val count: Int
)

/**
 * Data class for year count (raw year value from DB).
 */
data class YearCount(
    val year: String,
    val count: Int
)

/**
 * Data class for raw bitrate count (before grouping into SQ/HQ/HiFi).
 */
data class BitrateRawCount(
    val bitrate: Int,
    val count: Int
)
