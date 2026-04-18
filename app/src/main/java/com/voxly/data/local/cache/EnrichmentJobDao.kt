package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO for background metadata enrichment jobs.
 */
@Dao
interface EnrichmentJobDao {

    @Query("SELECT * FROM enrichment_jobs WHERE status IN (0, 1) ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingJobs(limit: Int): List<EnrichmentJobEntity>

    @Query("SELECT COUNT(*) FROM enrichment_jobs WHERE status IN (0, 1)")
    suspend fun getPendingJobCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM enrichment_jobs WHERE filePath = :path LIMIT 1)")
    suspend fun hasJobForPath(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<EnrichmentJobEntity>)

    @Query("UPDATE enrichment_jobs SET status = :status, attemptCount = attemptCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM enrichment_jobs WHERE status = :status")
    suspend fun deleteByStatus(status: Int)

    @Query("DELETE FROM enrichment_jobs WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM enrichment_jobs")
    suspend fun deleteAll()

    @Transaction
    suspend fun upsertPendingJobs(jobs: List<EnrichmentJobEntity>) {
        insertAll(jobs)
    }
}
