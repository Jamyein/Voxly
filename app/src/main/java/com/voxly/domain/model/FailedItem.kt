package com.voxly.domain.model

data class FailedItem(
    val filePath: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)