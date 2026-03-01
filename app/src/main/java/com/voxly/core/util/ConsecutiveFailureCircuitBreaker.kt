package com.voxly.core.util

import timber.log.Timber

/**
 * Opens the circuit for a cooldown window after N consecutive failures.
 * The circuit automatically closes after cooldown expiry.
 */
class ConsecutiveFailureCircuitBreaker(
    private val failureThreshold: Int,
    private val cooldownMs: Long,
    private val tag: String
) {
    @Volatile
    private var consecutiveFailures = 0

    @Volatile
    private var cooldownUntilMs = 0L

    fun canAttempt(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (cooldownUntilMs == 0L) {
            return true
        }

        if (nowMs >= cooldownUntilMs) {
            cooldownUntilMs = 0L
            consecutiveFailures = 0
            Timber.i(tag, "Circuit breaker cooldown expired, requests are re-enabled")
            return true
        }

        return false
    }

    fun remainingCooldownMs(nowMs: Long = System.currentTimeMillis()): Long {
        return (cooldownUntilMs - nowMs).coerceAtLeast(0L)
    }

    fun onSuccess() {
        consecutiveFailures = 0
        cooldownUntilMs = 0L
    }

    fun onFailure(nowMs: Long = System.currentTimeMillis()) {
        if (cooldownUntilMs != 0L && nowMs >= cooldownUntilMs) {
            cooldownUntilMs = 0L
            consecutiveFailures = 0
        }

        if (cooldownUntilMs > nowMs) {
            return
        }

        consecutiveFailures++

        if (consecutiveFailures >= failureThreshold) {
            cooldownUntilMs = nowMs + cooldownMs
            consecutiveFailures = 0
            Timber.w(tag, "Circuit breaker opened for ${cooldownMs}ms after $failureThreshold consecutive failures")
        }
    }
}
