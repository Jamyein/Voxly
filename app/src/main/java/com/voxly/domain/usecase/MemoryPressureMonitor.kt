package com.voxly.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Monitors system memory pressure and provides concurrency recommendations.
 * Optimized with memory info caching to reduce system call overhead.
 */
class MemoryPressureMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "MemoryPressureMonitor"
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Low memory device flag from system
    private val isLowRamDevice = activityManager.isLowRamDevice

    // Cached memory info to reduce system call overhead
    @Volatile
    private var cachedMemoryInfo: ActivityManager.MemoryInfo? = null
    private var lastRefreshTime = 0L
    private val cacheValidityMs = 1000L // 1 second cache validity

    /**
     * Gets recommended concurrency level based on available memory.
     *
     * @param maxConcurrency Maximum concurrency allowed (default 4)
     * @return Recommended concurrency level (1 to maxConcurrency)
     */
    fun getCurrentConcurrency(maxConcurrency: Int = 4): Int {
        refreshMemoryInfoIfNeeded()
        val memoryInfo = cachedMemoryInfo ?: return 1

        val availablePercent = memoryInfo.availMem.toFloat() / memoryInfo.totalMem

        // Limit max concurrency for low RAM devices
        val effectiveMax = if (isLowRamDevice) {
            minOf(maxConcurrency, 2)
        } else {
            maxConcurrency
        }

        val concurrency = when {
            memoryInfo.lowMemory -> {
                Timber.w("$TAG: Low memory detected! Available: ${"%.1f".format(availablePercent * 100)}%")
                1 // System is in low memory state
            }
            availablePercent > 0.5f -> effectiveMax
            availablePercent > 0.3f -> 2
            availablePercent > 0.15f -> 1
            else -> 1
        }

        Timber.d("$TAG: getCurrentConcurrency=$concurrency (max=$effectiveMax, avail=${"%.1f".format(availablePercent * 100)}%)")
        return concurrency
    }

    /**
     * Checks if batch operations should continue.
     * Returns false when system is in low memory state.
     *
     * @return true if operations can continue, false otherwise
     */
    fun canContinueBatch(): Boolean {
        refreshMemoryInfoIfNeeded()
        val canContinue = cachedMemoryInfo?.lowMemory != true
        if (!canContinue) {
            Timber.w("$TAG: canContinueBatch=false (low memory)")
        }
        return canContinue
    }

    /**
     * Gets current memory status for logging/debugging.
     *
     * @return MemoryStatus with detailed information
     */
    fun getMemoryStatus(): MemoryStatus {
        refreshMemoryInfoIfNeeded()
        val info = cachedMemoryInfo ?: ActivityManager.MemoryInfo().also {
            activityManager.getMemoryInfo(it)
        }

        return MemoryStatus(
            totalMem = info.totalMem,
            availMem = info.availMem,
            availPercent = info.availMem.toFloat() / info.totalMem,
            lowMemory = info.lowMemory,
            threshold = info.threshold,
            isLowRamDevice = isLowRamDevice
        )
    }

    /**
     * Refreshes memory info if cache is expired or empty.
     * Uses double-checked locking for thread safety.
     */
    private fun refreshMemoryInfoIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRefreshTime > cacheValidityMs || cachedMemoryInfo == null) {
            synchronized(this) {
                if (now - lastRefreshTime > cacheValidityMs) {
                    val info = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(info)
                    cachedMemoryInfo = info
                    lastRefreshTime = now
                }
            }
        }
    }

    /**
     * Data class for memory status reporting.
     */
    data class MemoryStatus(
        val totalMem: Long,
        val availMem: Long,
        val availPercent: Float,
        val lowMemory: Boolean,
        val threshold: Long,
        val isLowRamDevice: Boolean
    ) {
        fun toLogString(): String {
            return "Memory: ${availMem / 1024 / 1024}MB/${totalMem / 1024 / 1024}MB (${"%.1f".format(availPercent * 100)}%), " +
                    "lowMemory=$lowMemory, isLowRamDevice=$isLowRamDevice"
        }
    }
}