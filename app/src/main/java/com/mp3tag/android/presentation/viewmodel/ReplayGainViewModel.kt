package com.mp3tag.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.domain.repository.ReplayGainRepository
import com.mp3tag.android.domain.repository.ScanProgress
import com.mp3tag.android.domain.repository.ScanQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for ReplayGain scanning screen.
 */
@HiltViewModel
class ReplayGainViewModel @Inject constructor(
    private val replayGainRepository: ReplayGainRepository
) : ViewModel() {

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanComplete = MutableStateFlow(false)
    val scanComplete: StateFlow<Boolean> = _scanComplete.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Starts scanning files for ReplayGain.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level for scanning
     */
    fun startScan(filePaths: List<String>, scanQuality: ScanQuality = ScanQuality.NORMAL) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanComplete.value = false
            _error.value = null

            try {
                replayGainRepository.scanReplayGain(filePaths, scanQuality)
                    .collect { progress ->
                        _scanProgress.value = progress

                        if (progress.status.name == "COMPLETED") {
                            _scanComplete.value = true
                            _isScanning.value = false
                        } else if (progress.status.name == "FAILED") {
                            _error.value = "Scan failed for: ${progress.currentFilePath}"
                            _isScanning.value = false
                        }
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error during scan"
                _isScanning.value = false
            }
        }
    }

    /**
     * Cancels the ongoing scan.
     */
    fun cancelScan() {
        // Note: Actual cancellation would require more complex implementation
        // with Job management and cooperative cancellation
        _isScanning.value = false
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Resets the scan state.
     */
    fun resetScan() {
        _scanProgress.value = null
        _scanComplete.value = false
        _error.value = null
        _isScanning.value = false
    }
}
