package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for background metadata enrichment jobs.
 * Persistent queue used to backfill missing year/sampleRate in background via WorkManager.
 */
@Entity(
    tableName = "enrichment_jobs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["status"])
    ]
)
data class EnrichmentJobEntity(
    @PrimaryKey
    val id: String,
    val filePath: String,
    val status: Int, // 0 = pending, 1 = running, 2 = completed, 3 = failed
    val attemptCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
    }
}
