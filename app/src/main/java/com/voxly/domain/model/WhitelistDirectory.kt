package com.voxly.domain.model

data class WhitelistDirectory(
    val uri: String,
    val path: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isValid: Boolean = true
)
