package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanMode
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.usecase.BatchAlbumArtUseCase
import com.voxly.domain.usecase.BatchEditMetadataUseCase
import com.voxly.domain.usecase.BatchProgress
import com.voxly.domain.usecase.BatchReplayGainUseCase
import com.voxly.domain.usecase.BatchStatus
import com.voxly.domain.usecase.MetadataField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for batch operations screen.
 */
@HiltViewModel
class BatchOperationsViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    private val batchEditMetadataUseCase: BatchEditMetadataUseCase,
    private val batchReplayGainUseCase: BatchReplayGainUseCase,
    private val batchAlbumArtUseCase: BatchAlbumArtUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _selectedFiles = MutableStateFlow<List<String>>(emptyList())
    val selectedFiles: StateFlow<List<String>> = _selectedFiles.asStateFlow()

    private val _batchProgress = MutableStateFlow<BatchProgress?>(null)
    val batchProgress: StateFlow<BatchProgress?> = _batchProgress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _operationComplete = MutableStateFlow(false)
    val operationComplete: StateFlow<Boolean> = _operationComplete.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Scan mode setting
    private val _scanMode = MutableStateFlow(ScanMode.TRACK_ONLY)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    // Initialize scan mode from settings
    init {
        viewModelScope.launch {
            val mode = settingsDataStore.scanMode.first()
            _scanMode.value = when (mode) {
                "SINGLE_ALBUM" -> ScanMode.SINGLE_ALBUM
                "ALBUMS" -> ScanMode.ALBUMS
                else -> ScanMode.TRACK_ONLY
            }
        }
    }

    /**
     * Sets the list of files to process.
     */
    fun setSelectedFiles(filePaths: List<String>) {
        _selectedFiles.value = filePaths
    }

    /**
     * Starts batch metadata editing.
     * @param metadata Metadata to apply
     * @param fieldsToUpdate Which fields to update
     */
    fun startBatchEdit(
        metadata: AudioMetadata,
        fieldsToUpdate: Set<MetadataField> = MetadataField.ALL
    ) {
        if (_selectedFiles.value.isEmpty()) {
            _error.value = "No files selected"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _operationComplete.value = false
            _error.value = null

            try {
                batchEditMetadataUseCase(
                    filePaths = _selectedFiles.value,
                    metadata = metadata,
                    fieldsToUpdate = fieldsToUpdate
                ).collect { progress ->
                    _batchProgress.value = progress

                    if (progress.status == BatchStatus.COMPLETED) {
                        _operationComplete.value = true
                        _isProcessing.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Batch edit failed"
                _isProcessing.value = false
            }
        }
    }

    /**
     * Starts batch ReplayGain scanning.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz) 
     * will be automatically downsampled for optimal performance.
     * Uses scan mode from settings (Track Only, Single Album, or Albums - foobar2000 compatible)
     * @param scanQuality Quality level (determines max sample rate for scanning)
     */
    fun startBatchReplayGain(scanQuality: ScanQuality = ScanQuality.ACCURATE) {
        startBatchReplayGainWithMode(scanQuality, _scanMode.value)
    }

    /**
     * Starts batch ReplayGain scanning with specified mode.
     * @param scanQuality Quality level (determines max sample rate for scanning)
     * @param scanMode Scan mode (Track Only, Single Album, or Albums - foobar2000 compatible)
     */
    fun startBatchReplayGainWithMode(scanQuality: ScanQuality = ScanQuality.ACCURATE, scanMode: ScanMode = ScanMode.TRACK_ONLY) {
        if (_selectedFiles.value.isEmpty()) {
            _error.value = "No files selected"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _operationComplete.value = false
            _error.value = null

            try {
                // For TRACK_ONLY: scan all files as individual tracks (no album gain)
                // For SINGLE_ALBUM: scan all selected files as ONE album
                // For ALBUMS: scan files grouped by album tags (auto-group)
                
                val filesToScan = when (scanMode) {
                    ScanMode.TRACK_ONLY -> _selectedFiles.value
                    ScanMode.SINGLE_ALBUM -> _selectedFiles.value
                    ScanMode.ALBUMS -> _selectedFiles.value  // Will be grouped by album in scanner
                }
                
                batchReplayGainUseCase(
                    filePaths = filesToScan,
                    scanQuality = scanQuality
                ).collect { progress ->
                    _batchProgress.value = BatchProgress(
                        currentFile = progress.currentFile,
                        totalFiles = progress.totalFiles,
                        percentage = progress.percentage,
                        currentFilePath = progress.currentFilePath,
                        status = when (progress.status.name) {
                            "COMPLETED" -> BatchStatus.COMPLETED
                            else -> BatchStatus.PROCESSING
                        }
                    )

                    if (progress.status.name == "COMPLETED") {
                        // For album modes, album gain is calculated after scanning all files
                        // The scanner calculates both track and album gain per file
                        _operationComplete.value = true
                        _isProcessing.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Batch ReplayGain failed"
                _isProcessing.value = false
            }
        }
    }

    /**
     * Starts batch album art setting.
     * @param albumArtBytes Album art bytes to set
     */
    fun startBatchAlbumArt(albumArtBytes: ByteArray) {
        if (_selectedFiles.value.isEmpty()) {
            _error.value = "No files selected"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _operationComplete.value = false
            _error.value = null

            try {
                batchAlbumArtUseCase(
                    filePaths = _selectedFiles.value,
                    albumArtBytes = albumArtBytes
                ).collect { progress ->
                    _batchProgress.value = progress

                    if (progress.status == BatchStatus.COMPLETED) {
                        _operationComplete.value = true
                        _isProcessing.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Batch album art failed"
                _isProcessing.value = false
            }
        }
    }

    /**
     * Removes album art from all selected files.
     */
    fun removeBatchAlbumArt() {
        if (_selectedFiles.value.isEmpty()) {
            _error.value = "No files selected"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _operationComplete.value = false
            _error.value = null

            try {
                batchAlbumArtUseCase.removeAlbumArt(_selectedFiles.value)
                    .collect { progress ->
                        _batchProgress.value = progress

                        if (progress.status == BatchStatus.COMPLETED) {
                            _operationComplete.value = true
                            _isProcessing.value = false
                        }
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Batch album art removal failed"
                _isProcessing.value = false
            }
        }
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Resets the batch operation state.
     */
    fun resetOperation() {
        _batchProgress.value = null
        _operationComplete.value = false
        _error.value = null
        _isProcessing.value = false
    }

    /**
     * Clears the selected files list.
     */
    fun clearSelection() {
        _selectedFiles.value = emptyList()
        resetOperation()
    }
}
