package com.voxly.domain.usecase

import kotlinx.coroutines.flow.StateFlow

sealed class RebuildDatabaseState {
    object Idle : RebuildDatabaseState()
    data class InProgress(
        val progress: Float,
        val currentFile: String?,
        val scannedCount: Int
    ) : RebuildDatabaseState()
    data class Completed(
        val totalScanned: Int,
        val durationMs: Long
    ) : RebuildDatabaseState()
    data class Error(val message: String) : RebuildDatabaseState()
}

interface RebuildDatabaseManager {
    val rebuildState: StateFlow<RebuildDatabaseState>
    suspend fun rebuild()
}