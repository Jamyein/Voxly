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
 */
class FileLoggingTree : Timber.Tree() {

    companion object {
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_FILES = 5
        private const val MAX_TOTAL_SIZE = 50 * 1024 * 1024L // 50MB

        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLoggingTree-Writer").apply { isDaemon = true }
    }
    private val writeQueue = ConcurrentLinkedQueue<LogEntry>()
    private val isDraining = AtomicBoolean(false)

    private var currentLogFile: File? = null
    private var currentFileIndex: Int = 0
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
        // Get existing log files and sort by index
        val existingFiles = getLogFilesWithIndex()
        
        // Find current file or create new one
        if (currentLogFile == null || !currentLogFile!!.exists()) {
            // Find the latest file with content
            currentFileIndex = existingFiles.maxOfOrNull { it.first } ?: 0
            currentLogFile = getLogFile(currentFileIndex)
            currentFileSize = currentLogFile?.length() ?: 0
        }

        // Check if we need to rotate
        if (currentFileSize >= MAX_FILE_SIZE) {
            currentFileIndex++
            if (currentFileIndex >= MAX_FILES) {
                // Rotate: delete oldest (index 0), shift others
                rotateFiles()
                currentFileIndex = MAX_FILES - 1
            }
            currentLogFile = getLogFile(currentFileIndex)
            currentFileSize = 0
            
            try {
                currentLogFile?.createNewFile()
            } catch (e: IOException) {
                Log.e("FileLoggingTree", "Failed to create new log file: ${e.message}")
                return null
            }
        }

        return currentLogFile
    }

    private fun getLogFilesWithIndex(): List<Pair<Int, File>> {
        val logDir = LogManager.getLogDirectory()
        val prefix = "voxly_"
        val suffix = ".log"
        
        return logDir.listFiles { file ->
            file.isFile &&
            file.name.startsWith(prefix) &&
            file.name.endsWith(suffix) &&
            file.name.removePrefix(prefix).removeSuffix(suffix).toIntOrNull() != null
        }?.mapNotNull { file ->
            val index = file.name.removePrefix(prefix).removeSuffix(suffix).toIntOrNull()
            index?.let { Pair(it, file) }
        } ?: emptyList()
    }

    private fun getLogFile(index: Int): File {
        val logDir = LogManager.getLogDirectory()
        return File(logDir, "voxly_${String.format(Locale.US, "%03d", index)}.log")
    }

    private fun rotateFiles() {
        // Delete the oldest file (index 0)
        val oldestFile = getLogFile(0)
        if (oldestFile.exists()) {
            oldestFile.delete()
        }

        // Shift all files: voxly_1 -> voxly_0, voxly_2 -> voxly_1, etc.
        for (i in 0 until MAX_FILES - 1) {
            val currentFile = getLogFile(i)
            val nextFile = getLogFile(i + 1)
            if (nextFile.exists()) {
                nextFile.renameTo(currentFile)
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
            val files = getLogFilesWithIndex().sortedBy { it.first }
            
            var totalSize = files.sumOf { it.second.length() }
            
            // Delete oldest files until we're under the limit
            for ((index, file) in files) {
                if (totalSize <= MAX_TOTAL_SIZE) break
                if (file.exists() && file.delete()) {
                    totalSize -= file.length()
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
                    getLogFilesWithIndex().sortedBy { it.first }.forEach { (_, file) ->
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
