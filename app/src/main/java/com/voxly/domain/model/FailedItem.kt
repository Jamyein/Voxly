package com.voxly.domain.model

import android.os.SystemClock

data class FailedItem(
    val filePath: String,
    val reason: String,
    val timestamp: Long = SystemClock.elapsedRealtime()
)