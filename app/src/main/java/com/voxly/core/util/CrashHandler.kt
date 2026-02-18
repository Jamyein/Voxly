package com.voxly.core.util

import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler : Thread.UncaughtExceptionHandler {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Logger.wtf(
            "Uncaught exception thread=${thread.name} id=${thread.id}",
            throwable
        )
        if (LogManager.isCrashReportingEnabled) {
            saveCrashReport(throwable)
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashReport(throwable: Throwable) {
        try {
            val timestamp = dateTimeFormat.format(Date())
            val fileName = "crash_${timestamp}.log"

            val crashDir = runCatching {
                LogManager.getCrashDirectory()
            }.getOrElse {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "crashes"
                )
            }.apply {
                if (!exists()) mkdirs()
            }

            val crashFile = File(crashDir, fileName)

            FileWriter(crashFile).use { writer ->
                writer.append(LogManager.createCrashHeader(throwable))
                writer.appendLine()

                val stringWriter = StringWriter()
                val printWriter = PrintWriter(stringWriter)
                throwable.printStackTrace(printWriter)

                writer.append("Stack Trace:")
                writer.appendLine()
                writer.append(stringWriter.toString())
                writer.appendLine()
                writer.appendLine()
                writer.appendLine("================================================================================")
                writer.appendLine("End of Crash Report")
                writer.appendLine("================================================================================")
            }

            Timber.e("Crash report saved to: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("CrashHandler", "Failed to save crash report: ${e.message}")
        }
    }

    fun getCrashReports(): List<File> {
        return LogManager.getCrashFiles()
    }

    fun clearAllCrashReports(): Int {
        var deletedCount = 0
        getCrashReports().forEach { file ->
            if (file.delete()) deletedCount++
        }
        return deletedCount
    }
}

fun logCrash(tag: String, message: String, throwable: Throwable) {
    Timber.e("[$tag] $message", throwable)
}
