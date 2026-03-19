package com.voxly.domain.model

enum class BatchStatus {
    PROCESSING,
    COMPLETED,
    CANCELLED
}

data class BatchResult(
    val totalFiles: Int,
    val successCount: Int,
    val failedCount: Int,
    val failedItems: List<FailedItem>,
    val status: BatchStatus,
    val lastUpdatedFile: String = ""
)
