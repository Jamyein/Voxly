package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectorySnapshotDao {
    @Query("SELECT * FROM directory_snapshots WHERE directoryUri = :uri")
    suspend fun getSnapshot(uri: String): DirectorySnapshotEntity?

    @Query("SELECT * FROM directory_snapshots")
    suspend fun getAllSnapshots(): List<DirectorySnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DirectorySnapshotEntity)

    @Query("DELETE FROM directory_snapshots WHERE directoryUri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM directory_snapshots")
    suspend fun deleteAll()
}
