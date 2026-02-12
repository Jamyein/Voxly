package com.voxly.presentation.screens.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LogFileItem(
    val name: String,
    val file: File,
    val size: String,
    val lastModified: String
)

data class LogViewerUiState(
    val logFiles: List<LogFileItem> = emptyList(),
    val selectedLogFile: LogFileItem? = null,
    val logContent: String = "",
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: LogFilter = LogFilter.ALL
)

enum class LogFilter {
    ALL, ERROR, WARN, INFO, DEBUG
}

class LogViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    init {
        loadLogFiles()
    }

    fun loadLogFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val files = withContext(Dispatchers.IO) {
                LogManager.getLogFiles() + LogManager.getCrashFiles()
            }

            val logFileItems = files.map { file ->
                LogFileItem(
                    name = file.name,
                    file = file,
                    size = LogManager.formatLogSize(file.length()),
                    lastModified = dateFormat.format(Date(file.lastModified()))
                )
            }

            _uiState.value = _uiState.value.copy(
                logFiles = logFileItems,
                isLoading = false
            )
        }
    }

    fun selectLogFile(item: LogFileItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val content = withContext(Dispatchers.IO) {
                LogManager.getLogFileContent(item.file)
            }

            _uiState.value = _uiState.value.copy(
                selectedLogFile = item,
                logContent = content,
                isLoading = false
            )
        }
    }

    fun clearSelectedLog() {
        _uiState.value = _uiState.value.copy(
            selectedLogFile = null,
            logContent = ""
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setFilter(filter: LogFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun deleteLogFile(item: LogFileItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LogManager.deleteLogFile(item.file)
            }
            clearSelectedLog()
            loadLogFiles()
        }
    }

    fun exportLogs(context: Context, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            try {
                val files = _uiState.value.logFiles.map { it.file }
                if (files.isEmpty()) {
                    onComplete(null)
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val zipFile = File(exportDir, "voxly_logs_$timestamp.zip")

                withContext(Dispatchers.IO) {
                    ZipOutputStream(zipFile.outputStream()).use { zos ->
                        files.forEach { file ->
                            if (file.exists()) {
                                zos.putNextEntry(ZipEntry(file.name))
                                file.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                onComplete(uri)
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }

    fun shareLog(context: Context, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Voxly Log")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Log"))
    }

    fun getFilteredLogs(): List<String> {
        val state = _uiState.value
        val content = state.logContent
        val lines = content.lines()

        if (state.searchQuery.isBlank() && state.selectedFilter == LogFilter.ALL) {
            return lines
        }

        return lines.filter { line ->
            val matchesSearch = state.searchQuery.isBlank() ||
                    line.contains(state.searchQuery, ignoreCase = true)

            val matchesFilter = when (state.selectedFilter) {
                LogFilter.ALL -> true
                LogFilter.ERROR -> line.contains("[ERROR]") || line.contains("[WTF]")
                LogFilter.WARN -> line.contains("[WARN]")
                LogFilter.INFO -> line.contains("[INFO]")
                LogFilter.DEBUG -> line.contains("[DEBUG]")
            }

            matchesSearch && matchesFilter
        }
    }
}
