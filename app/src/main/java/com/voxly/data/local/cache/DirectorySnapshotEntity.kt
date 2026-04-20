package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "directory_snapshots",
    indices = [Index(value = ["directoryUri"], unique = true)]
)
data class DirectorySnapshotEntity(
    @PrimaryKey
    val directoryUri: String,
    val fileCount: Int,
    val lastCheckTime: Long
)
