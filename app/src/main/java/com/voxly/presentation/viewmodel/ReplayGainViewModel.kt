package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.ClipMode
import com.voxly.domain.model.ReplayGainConfig
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanMode
import com.voxly.presentation.navigation.ReplayGainScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for ReplayGain scanning screen.
 */
@HiltViewModel(assistedFactory = ReplayGainViewModel.Factory::class)
class ReplayGainViewModel @AssistedInject constructor(
    @Assisted val navKey: ReplayGainScanner,
    private val replayGainRepository: ReplayGainRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanComplete = MutableStateFlow(false)
    val scanComplete: StateFlow<Boolean> = _scanComplete.asStateFlow()

    private val _error = MutableSharedFlow<String>(replay = 0)
    val error: SharedFlow<String> = _error.asSharedFlow()

    init {
        // Auto-start scan with navKey filePaths
        if (navKey.filePaths.isNotEmpty()) {
            startScan(navKey.filePaths)
        }
    }

    /**
     * Starts scanning files for ReplayGain.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level for scanning
     */
    fun startScan(filePaths: List<String>, scanQuality: ScanQuality = ScanQuality.NORMAL) {
        viewModelScope.launch {
            Timber.tag("Voxly").i("ReplayGain: operation started")
            _isScanning.update { true }
            _scanComplete.update { false }

            try {
                // Get target loudness and clip mode from settings
                val targetLoudness = settingsDataStore.replayGainTargetLoudness.first()
                val clipModeStr = settingsDataStore.replayGainClipMode.first()
                val clipMode = ClipMode.fromString(clipModeStr)
                val scanConfig = ReplayGainConfig(
                    clipMode = clipMode,
                    truePeak = false,
                    dualMono = false,
                    albumAsAes77 = false,
                    skipExisting = false,
                    maxPeakLevel = 0.0
                )

                val scanMode = settingsDataStore.scanMode.first()

                val scanFlow = when (scanMode) {
                    ScanMode.TRACK_ONLY.name -> replayGainRepository.scanReplayGain(
                        filePaths,
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                    ScanMode.SINGLE_ALBUM.name -> replayGainRepository.scanReplayGainByAlbum(
                        mapOf("single_album" to filePaths),
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                    ScanMode.ALBUMS.name -> replayGainRepository.scanReplayGainWithAlbumGrouping(
                        filePaths,
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                    else -> replayGainRepository.scanReplayGain(
                        filePaths,
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                }

                scanFlow.collect { progress ->
                        _scanProgress.update { progress }

                        if (progress.status.name == "COMPLETED") {
                            _scanComplete.update { true }
                            _isScanning.update { false }
                        } else if (progress.status.name == "FAILED") {
                            _error.emit("Scan failed for: ${progress.currentFilePath}")
                            _isScanning.update { false }
                        }
                    }
            } catch (e: Exception) {
                _error.emit(e.message ?: "Unknown error during scan")
                _isScanning.update { false }
            }
        }
    }

    /**
     * Cancels the ongoing scan.
     */
    fun cancelScan() {
        // Note: Actual cancellation would require more complex implementation
        // with Job management and cooperative cancellation
        _isScanning.update { false }
    }

    /**
     * Resets the scan state.
     */
    fun resetScan() {
        _scanProgress.update { null }
        _scanComplete.update { false }
        _isScanning.update { false }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: ReplayGainScanner): ReplayGainViewModel
    }
}
