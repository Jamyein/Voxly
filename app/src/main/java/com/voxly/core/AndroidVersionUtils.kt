package com.voxly.core

import android.os.Build

/**
 * Utility class for Android version compatibility checks.
 * Provides functions to check API levels and feature availability.
 */
object AndroidVersionUtils {

    /**
     * Checks if the device is running Android 12 (API 31) or higher.
     *
     * @return true if SDK >= 31 (Android 12/S), false otherwise
     */
    fun isAtLeastS(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Checks if the device is running Android 11 (API 30) or higher.
     *
     * @return true if SDK >= 30 (Android 11/R), false otherwise
     */
    fun isAtLeastR(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Checks if dynamic color (Material You) is available on the device.
     * Dynamic color requires Android 12+ (API 31).
     *
     * @return true if dynamic color is available, false otherwise
     */
    fun isDynamicColorAvailable(): Boolean {
        return isAtLeastS()
    }
}
