package com.voxly.core.util

import android.content.Context
import android.os.Environment
import com.voxly.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    private const val LOG_DIR = "logs"
    private const val CRASH_DIR = "crashes"
    private const val LOG_PREFIX = "voxly_"
    private const val CRASH_PREFIX = "crash_"
    private const val LOG_EXTENSION = ".log"
    private const val MAX_LOG_AGE_DAYS = 7L
    const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L

    private lateinit var logDir: File
    private lateinit var crashDir: File

    fun getLogDirectory(): File = logDir
    fun getCrashDirectory(): File = crashDir

    var isLoggingEnabled: Boolean = true
    var isFileLoggingEnabled: Boolean = true
    var isConsoleLoggingEnabled: Boolean = BuildConfig.DEBUG
    var isCrashReportingEnabled: Boolean = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir

        logDir = File(externalDir, LOG_DIR).apply {
            if (!exists()) mkdirs()
        }

        crashDir = File(externalDir, CRASH_DIR).apply {
            if (!exists()) mkdirs()
        }

        cleanupOldLogs()
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles { file ->
            file.isFile &&
            file.name.startsWith(LOG_PREFIX) &&
            file.name.endsWith(LOG_EXTENSION)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getCrashFiles(): List<File> {
        return crashDir.listFiles { file ->
            file.isFile &&
            file.name.startsWith(CRASH_PREFIX) &&
            file.name.endsWith(LOG_EXTENSION)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getLogFileContent(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "Failed to read log file: ${e.message}"
        }
    }

    fun getLogFileForDate(date: Date): File {
        val dateStr = dateFormat.format(date)
        return File(logDir, "$LOG_PREFIX$dateStr$LOG_EXTENSION")
    }

    fun deleteLogFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCrashFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun cleanupOldLogs(): Int {
        val cutoffTime = System.currentTimeMillis() - (MAX_LOG_AGE_DAYS * 24 * 60 * 60 * 1000L)
        var deletedCount = 0

        getLogFiles().forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) deletedCount++
            }
        }

        getCrashFiles().forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) deletedCount++
            }
        }

        return deletedCount
    }

    /**
     * Clears all log files (not just old ones).
     * Used by the settings cleanup option.
     */
    fun clearAllLogs(): Int {
        var deletedCount = 0

        // Delete all log files
        getLogFiles().forEach { file ->
            if (file.delete()) deletedCount++
        }

        // Delete all crash files
        getCrashFiles().forEach { file ->
            if (file.delete()) deletedCount++
        }

        return deletedCount
    }

    fun getLogDirectorySize(): Long {
        var size = 0L
        getLogFiles().forEach { size += it.length() }
        getCrashFiles().forEach { size += it.length() }
        return size
    }

    fun formatLogSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            else -> String.format(Locale.US, "%.2f MB", sizeInBytes / (1024.0 * 1024.0))
        }
    }

    fun createLogHeader(): String {
        val deviceInfo = android.os.Build.MODEL ?: "Unknown"
        val osVersion = android.os.Build.VERSION.RELEASE ?: "Unknown"
        val appVersion = BuildConfig.VERSION_NAME

        return buildString {
            appendLine("================================================================================")
            appendLine("Voxly Log File")
            appendLine("Version: $appVersion")
            appendLine("Device: ${android.os.Build.MANUFACTURER}/${deviceInfo}/${android.os.Build.DEVICE}:$osVersion/${android.os.Build.VERSION.SDK_INT}")
            appendLine("Date: ${dateTimeFormat.format(Date())}")
            appendLine("================================================================================")
            appendLine()
        }
    }

    fun createCrashHeader(throwable: Throwable): String {
        val deviceInfo = android.os.Build.MODEL ?: "Unknown"
        val osVersion = android.os.Build.VERSION.RELEASE ?: "Unknown"
        val appVersion = BuildConfig.VERSION_NAME

        return buildString {
            appendLine("================================================================================")
            appendLine("Voxly Crash Report")
            appendLine("Version: $appVersion")
            appendLine("Device: ${android.os.Build.MANUFACTURER}/${deviceInfo}/${android.os.Build.DEVICE}:$osVersion/${android.os.Build.VERSION.SDK_INT}")
            appendLine("Date: ${dateTimeFormat.format(Date())}")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "No message"}")
            appendLine("================================================================================")
            appendLine()
        }
    }
}
