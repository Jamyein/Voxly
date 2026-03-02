package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for recent edit history.
 * Stores metadata changes for user editing history.
 */
@Entity(
    tableName = "recent_edits",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class RecentEditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val timestamp: Long,
    val originalMetadataJson: String,
    val newMetadataJson: String
)
