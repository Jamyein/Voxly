package com.voxly.domain.usecase

import android.app.ActivityManager
import android.content.Context
import javax.inject.Inject

class MemoryPressureMonitor @Inject constructor(
    context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getCurrentConcurrency(maxConcurrency: Int = 4): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availablePercent = memoryInfo.availMem.toFloat() / memoryInfo.totalMem

        return when {
            availablePercent > 0.5f -> maxConcurrency
            availablePercent > 0.2f -> 2
            else -> 1
        }
    }
}