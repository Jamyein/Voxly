@file:Suppress("DEPRECATION")

package com.voxly.core.util

import android.util.Log
import com.voxly.BuildConfig
import timber.log.Timber

object Logger {

    fun init() = Unit

    fun d(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        if (!BuildConfig.DEBUG) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.d(resolvedTag, message)
        }
        if (LogManager.isFileLoggingEnabled) {
            Timber.tag(resolvedTag).d(message)
        }
    }

    fun v(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        if (!BuildConfig.DEBUG) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.v(resolvedTag, message)
        }
        if (LogManager.isFileLoggingEnabled) {
            Timber.tag(resolvedTag).v(message)
        }
    }

    fun i(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.i(resolvedTag, message)
        }
        if (LogManager.isFileLoggingEnabled) {
            Timber.tag(resolvedTag).i(message)
        }
    }

    fun w(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.w(resolvedTag, message)
        }
        if (LogManager.isFileLoggingEnabled) {
            Timber.tag(resolvedTag).w(message)
        }
    }

    fun e(message: String, throwable: Throwable? = null, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            if (throwable != null) {
                Log.e(resolvedTag, message, throwable)
            } else {
                Log.e(resolvedTag, message)
            }
        }
        if (LogManager.isFileLoggingEnabled) {
            if (throwable != null) {
                Timber.tag(resolvedTag).e(throwable, message)
            } else {
                Timber.tag(resolvedTag).e(message)
            }
        }
    }

    fun wtf(message: String, throwable: Throwable? = null) {
        if (!LogManager.isLoggingEnabled) return
        if (LogManager.isConsoleLoggingEnabled) {
            if (throwable != null) {
                Log.wtf("Voxly", message, throwable)
            } else {
                Log.wtf("Voxly", message)
            }
        }
        if (LogManager.isFileLoggingEnabled) {
            if (throwable != null) {
                Timber.tag("Voxly").wtf(throwable, message)
            } else {
                Timber.tag("Voxly").wtf(message)
            }
        }
    }
}
