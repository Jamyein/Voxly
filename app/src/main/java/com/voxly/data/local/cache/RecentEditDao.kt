package com.voxly.data.local.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for recent edit history operations.
 */
@Dao
interface RecentEditDao {

    /**
     * Gets recent edits ordered by timestamp descending.
     */
    @Query("SELECT * FROM recent_edits ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEdits(limit: Int): Flow<List<RecentEditEntity>>

    /**
     * Gets recent edits as a one-shot query.
     */
    @Query("SELECT * FROM recent_edits ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEditsOnce(limit: Int): List<RecentEditEntity>

    /**
     * Inserts a new recent edit entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edit: RecentEditEntity): Long

    /**
     * Deletes old entries keeping only the most recent ones.
     */
    @Query("""
        DELETE FROM recent_edits WHERE id NOT IN (
            SELECT id FROM recent_edits ORDER BY timestamp DESC LIMIT :keepCount
        )
    """)
    suspend fun deleteOldEntries(keepCount: Int)

    /**
     * Deletes all recent edits.
     */
    @Query("DELETE FROM recent_edits")
    suspend fun deleteAll()

    /**
     * Gets the count of recent edits.
     */
    @Query("SELECT COUNT(*) FROM recent_edits")
    suspend fun getCount(): Int

    /**
     * Gets edits within a time range.
     */
    @Query("SELECT * FROM recent_edits WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getEditsSince(startTime: Long): List<RecentEditEntity>
}
