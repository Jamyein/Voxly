package com.voxly.core.util

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * A simple request throttler to prevent rate-limit violations and anti-scraping triggers.
 *
 * Usage:
 * ```
 * val throttler = RequestThrottler(minIntervalMs = 500L)
 *
 * suspend fun makeRequest() {
 *     throttler.throttle()
 *     // perform request...
 * }
 * ```
 *
 * @param minIntervalMs Minimum interval between requests in milliseconds.
 *                      Default 500ms provides good balance between throughput and rate limiting.
 * @param tag Logging tag for debug output.
 */
class RequestThrottler(
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val tag: String = "RequestThrottler"
) {
    companion object {
        /** Default interval of 500ms between requests */
        const val DEFAULT_INTERVAL_MS = 500L
    }

    @Volatile
    private var lastRequestTime = 0L

    /**
     * Waits if necessary to enforce the minimum interval since the last request.
     * Should be called before making each API request.
     */
    suspend fun throttle() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime

        if (timeSinceLastRequest < minIntervalMs) {
            val waitTime = minIntervalMs - timeSinceLastRequest
            Timber.d(tag, "Throttling request, waiting ${waitTime}ms")
            delay(waitTime)
        }

        lastRequestTime = System.currentTimeMillis()
    }

    /**
     * Resets the throttler state. Useful for testing or when a new session starts.
     */
    fun reset() {
        lastRequestTime = 0L
    }
}
