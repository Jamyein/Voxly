package com.voxly.core.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class FileLoggingTree {

    private val dateTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val executor = Executors.newSingleThreadExecutor()
    private val writeQueue = ConcurrentLinkedQueue<LogEntry>()

    private var currentLogFile: File? = null
    private var currentFileDate: String? = null
    private var currentFileSize: Long = 0

    private val lock = Any()

    init {
        executor.submit {
            processQueue()
        }
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!LogManager.isFileLoggingEnabled) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "UNKNOWN",
            message = message,
            throwable = t
        )

        writeQueue.offer(entry)
    }

    private fun processQueue() {
        while (true) {
            val entry = writeQueue.poll() ?: break
            writeToFile(entry)
        }
    }

    private fun writeToFile(entry: LogEntry) {
        synchronized(lock) {
            val logFile = getOrCreateLogFile()
            if (logFile == null) return

            try {
                val formattedTime = dateTimeFormat.format(Date(entry.timestamp))
                val priorityChar = getPriorityChar(entry.priority)
                val logLine = buildString {
                    append("[")
                    append(formattedTime)
                    append("][")
                    append(priorityChar.padEnd(5))
                    append("][")
                    append(entry.tag.take(12).padEnd(12))
                    append("] ")
                    appendLine(entry.message)

                    entry.throwable?.let { throwable ->
                        throwable.stackTraceToString().lines().forEach { line ->
                            appendLine("    $line")
                        }
                    }
                }

                FileWriter(logFile, true).use { writer ->
                    writer.append(logLine)
                }

                currentFileSize = logFile.length()
            } catch (e: IOException) {
                Log.e("FileLoggingTree", "Failed to write log: ${e.message}")
            }
        }
    }

    private fun getOrCreateLogFile(): File? {
        val today = fileDateFormat.format(Date())
        val fileName = "voxly_$today.log"

        if (currentLogFile == null || currentFileDate != today) {
            val existingFile = LogManager.getLogFiles().find { it.name == fileName }
            currentLogFile = existingFile ?: File(LogManager.getLogDirectory(), fileName)
            currentFileDate = today
            currentFileSize = currentLogFile!!.length()

            if (existingFile == null) {
                try {
                    FileWriter(currentLogFile!!, true).use { writer ->
                        writer.append(LogManager.createLogHeader())
                    }
                } catch (e: IOException) {
                    Log.e("FileLoggingTree", "Failed to create log file header: ${e.message}")
                    return null
                }
            }
        }

        if (currentFileSize >= LogManager.MAX_LOG_FILE_SIZE) {
            val newFileName = "voxly_${today}_1.log"
            currentLogFile = File(LogManager.getLogDirectory(), newFileName)
            currentFileSize = 0

            try {
                FileWriter(currentLogFile!!, true).use { writer ->
                    writer.append(LogManager.createLogHeader())
                }
            } catch (e: IOException) {
                Log.e("FileLoggingTree", "Failed to create rotated log file: ${e.message}")
                return null
            }
        }

        return currentLogFile
    }

    private fun getPriorityChar(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private data class LogEntry(
        val timestamp: Long,
        val priority: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )
}
