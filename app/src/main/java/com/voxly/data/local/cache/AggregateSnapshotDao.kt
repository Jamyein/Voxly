package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AggregateSnapshotDao {
    /** The single snapshot row (id = 1); null when never saved / DB was cleared. */
    @Query("SELECT * FROM aggregate_snapshot WHERE id = 1 LIMIT 1")
    suspend fun getSnapshot(): AggregateSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: AggregateSnapshotEntity)

    @Query("DELETE FROM aggregate_snapshot")
    suspend fun deleteAll()
}
