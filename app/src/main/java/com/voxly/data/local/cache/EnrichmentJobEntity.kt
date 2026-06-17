package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for background metadata enrichment jobs.
 * Persistent queue used to backfill missing year/sampleRate in background via WorkManager.
 *
 * `filePath` is the PRIMARY KEY. The previous `id: String` (a 32-bit `path.hashCode()`)
 * was removed in the same refactor as `CachedAudioFileEntity.id` — same bug class
 * (32-bit hash collision on cross-workspace duplicates), same fix.
 */
@Entity(
    tableName = "enrichment_jobs",
    indices = [
        // `filePath` is the PRIMARY KEY below — it is unique by definition.
        Index(value = ["status"])
    ]
)
data class EnrichmentJobEntity(
    @PrimaryKey
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
