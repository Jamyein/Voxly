@file:Suppress("DEPRECATION")

package com.voxly.core.util

import android.util.Log

object Logger {

    private var isInitialized = false
    private var fileLoggingTree: FileLoggingTree? = null

    fun init() {
        if (isInitialized) return
        isInitialized = true

        fileLoggingTree = FileLoggingTree()
    }

    fun d(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.d(resolvedTag, message)
        }
        fileLoggingTree?.log(Log.DEBUG, resolvedTag, message, null)
    }

    fun i(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.i(resolvedTag, message)
        }
        fileLoggingTree?.log(Log.INFO, resolvedTag, message, null)
    }

    fun w(message: String, tag: String? = null) {
        if (!LogManager.isLoggingEnabled) return
        val resolvedTag = tag ?: "Voxly"
        if (LogManager.isConsoleLoggingEnabled) {
            Log.w(resolvedTag, message)
        }
        fileLoggingTree?.log(Log.WARN, resolvedTag, message, null)
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
        fileLoggingTree?.log(Log.ERROR, resolvedTag, message, throwable)
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
        fileLoggingTree?.log(Log.ASSERT, "Voxly", message, throwable)
    }
}
