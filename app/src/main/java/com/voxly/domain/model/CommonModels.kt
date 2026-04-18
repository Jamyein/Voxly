package com.voxly.domain.model

data class FailedItem(
    val filePath: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class WhitelistDirectory(
    val uri: String,
    val path: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isValid: Boolean = true
)

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
