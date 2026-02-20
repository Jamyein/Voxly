package com.voxly.core.util

import android.util.Log
import com.voxly.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Timber Tree that writes logs to files asynchronously.
 * Extends Timber.Tree to integrate with Timber's logging system.
 * Creates a new log file each time the app is started.
 */
class FileLoggingTree : Timber.Tree() {

    companion object {
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_FILES = 10
        private const val MAX_TOTAL_SIZE = 50 * 1024 * 1024L // 50MB

        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val fileNameDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLoggingTree-Writer").apply { isDaemon = true }
    }
    private val writeQueue = ConcurrentLinkedQueue<LogEntry>()
    private val isDraining = AtomicBoolean(false)

    private var currentLogFile: File? = null
    private var currentFileSize: Long = 0

    private val lock = Any()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!LogManager.isFileLoggingEnabled) return
        if (!BuildConfig.DEBUG && (priority == Log.VERBOSE || priority == Log.DEBUG)) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "Voxly",
            message = message,
            throwable = t
        )

        writeQueue.offer(entry)
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!isDraining.compareAndSet(false, true)) return
        executor.execute {
            try {
                processQueue()
            } finally {
                isDraining.set(false)
                if (writeQueue.isNotEmpty()) {
                    scheduleDrain()
                }
            }
        }
    }

    private fun processQueue() {
        while (true) {
            val entry = writeQueue.poll() ?: return
            writeToFile(entry)
        }
    }

    private fun writeToFile(entry: LogEntry) {
        synchronized(lock) {
            val logFile = getOrCreateLogFile()
            if (logFile == null) {
                Log.e("FileLoggingTree", "Failed to get log file")
                return
            }

            try {
                val formattedTime = dateTimeFormat.format(Date(entry.timestamp))
                val priorityStr = getPriorityString(entry.priority)
                val logLine = buildString {
                    append("[")
                    append(formattedTime)
                    append("] [")
                    append(priorityStr)
                    append("] [")
                    append(entry.tag.take(12).padEnd(12))
                    append("] ")
                    appendLine(entry.message)

                    entry.throwable?.let { throwable ->
                        throwable.stackTraceToString().lines().forEach { line ->
                            appendLine("    $line")
                        }
                    }
                }

                FileOutputStream(logFile, true).use { fos ->
                    fos.write(logLine.toByteArray())
                }

                currentFileSize = logFile.length()
            } catch (e: IOException) {
                Log.e("FileLoggingTree", "Failed to write log: ${e.message}")
            }
        }
    }

    private fun getOrCreateLogFile(): File? {
        // If we already have a current log file, use it
        if (currentLogFile != null && currentLogFile!!.exists()) {
            // Check if file size exceeds limit
            if (currentFileSize >= MAX_FILE_SIZE) {
                // Create new file with timestamp when size limit reached
                currentLogFile = createNewLogFile()
                currentFileSize = 0
            }
            return currentLogFile
        }

        // Create a new log file for this app session
        currentLogFile = createNewLogFile()
        currentFileSize = 0
        return currentLogFile
    }

    private fun createNewLogFile(): File {
        val logDir = LogManager.getLogDirectory()
        val timestamp = fileNameDateFormat.format(Date())
        val fileName = "voxly_$timestamp.log"
        val file = File(logDir, fileName)
        try {
            file.createNewFile()
            // Write header to new file
            val header = LogManager.createLogHeader()
            FileOutputStream(file, true).use { fos ->
                fos.write(header.toByteArray())
            }
        } catch (e: IOException) {
            Log.e("FileLoggingTree", "Failed to create new log file: ${e.message}")
        }
        return file
    }

    private fun getLogFilesWithIndex(): List<Pair<Int, File>> {
        val logDir = LogManager.getLogDirectory()
        val prefix = "voxly_"
        val suffix = ".log"
        
        return logDir.listFiles { file ->
            file.isFile &&
            file.name.startsWith(prefix) &&
            file.name.endsWith(suffix)
        }?.sortedByDescending { it.lastModified() }?.mapIndexed { index, file ->
            Pair(index, file)
        } ?: emptyList()
    }

    private fun rotateFiles() {
        // Delete oldest files until we're under MAX_FILES
        val files = getLogFilesWithIndex().sortedBy { it.first }
        for ((index, file) in files) {
            if (getLogFilesWithIndex().size <= MAX_FILES) break
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }
    }

    /**
     * Cleanup old log files to maintain total size limit.
     * Called at application startup.
     */
    fun cleanupExcessLogs() {
        synchronized(lock) {
            val logDir = LogManager.getLogDirectory()
            val files = getLogFilesWithIndex().sortedByDescending { it.second.lastModified() }
            
            var totalSize = files.sumOf { it.second.length() }
            
            // Delete oldest files until we're under the limit
            for ((_, file) in files) {
                if (totalSize <= MAX_TOTAL_SIZE) break
                if (file.exists() && file.delete()) {
                    totalSize -= file.length()
                }
            }

            // Also enforce max file count
            val sortedByTime = files.sortedByDescending { it.second.lastModified() }
            for ((_, file) in sortedByTime.drop(MAX_FILES)) {
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Export all log files to a ZIP archive.
     * @return ZIP file path, or null if failed
     */
    fun exportToZip(): File? {
        synchronized(lock) {
            val logDir = LogManager.getLogDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(logDir.parentFile, "logs_$timestamp.zip")

            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    getLogFilesWithIndex().sortedByDescending { it.second.lastModified() }.forEach { (_, file) ->
                        if (file.exists()) {
                            zos.putNextEntry(ZipEntry(file.name))
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
                return zipFile
            } catch (e: IOException) {
                Log.e("FileLoggingTree", "Failed to export logs to ZIP: ${e.message}")
                return null
            }
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
