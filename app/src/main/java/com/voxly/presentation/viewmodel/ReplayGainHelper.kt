package com.voxly.presentation.viewmodel

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.ClipMode
import com.voxly.domain.model.ReplayGainConfig
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.model.ScanModeConstants
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanMode
import com.voxly.domain.repository.ScanQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Sealed class representing ReplayGain scan errors for better error handling.
 */
sealed class ReplayGainScanError {
    data class DecodeFailed(val reason: String, val filePath: String) : ReplayGainScanError()
    data class NoAudioTrack(val filePath: String) : ReplayGainScanError()
    data class PermissionDenied(val filePath: String) : ReplayGainScanError()
    data class AllFallbacksFailed(val filePath: String) : ReplayGainScanError()
    data class Unknown(val message: String) : ReplayGainScanError()
}

/**
 * Helper ViewModel for ReplayGain scanning functionality in MetadataEditor.
 * Handles ReplayGain scan initiation, progress tracking, and result management.
 * 
 * Usage:
 * ```kotlin
 * // In MetadataEditorViewModel
 * private val replayGainHelper = ReplayGainHelper(replayGainRepository, settingsDataStore, context)
 * 
 * // Expose state from helper
 * val pendingReplayGainInfo = replayGainHelper.pendingReplayGainInfo
 * val isScanningReplayGain = replayGainHelper.isScanningReplayGain
 * ```
 */
@HiltViewModel
class ReplayGainHelper @Inject constructor(
    private val replayGainRepository: ReplayGainRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _pendingReplayGainInfo = MutableStateFlow<ReplayGainInfo?>(null)
    val pendingReplayGainInfo: StateFlow<ReplayGainInfo?> = _pendingReplayGainInfo.asStateFlow()

    private val _isScanningReplayGain = MutableStateFlow(false)
    val isScanningReplayGain: StateFlow<Boolean> = _isScanningReplayGain.asStateFlow()

    private val _replayGainScanError = MutableSharedFlow<String>()
    val replayGainScanError: SharedFlow<String> = _replayGainScanError.asSharedFlow()

    private var scanJob: Job? = null

    /**
     * Starts ReplayGain scan for the given file.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz)
     * will be automatically downsampled for optimal performance.
     * 
     * @param filePath Path to the audio file to scan
     */
    fun scanReplayGain(filePath: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanningReplayGain.update { true }

            // Using ACCURATE mode for best results - dynamic sampling handles high-res files
            val scanQuality = ScanQuality.ACCURATE

            try {
                val currentScanMode = settingsDataStore.scanMode.first().let { mode ->
                    when (mode) {
                        ScanModeConstants.SINGLE_ALBUM -> ScanMode.SINGLE_ALBUM
                        ScanModeConstants.ALBUMS -> ScanMode.ALBUMS
                        else -> ScanMode.TRACK_ONLY
                    }
                }
                val filesToScan: List<String>

                // Determine which files to scan based on scan mode (foobar2000 compatible)
                if (currentScanMode == ScanMode.TRACK_ONLY) {
                    // Track Only: scan single file only, no album gain
                    filesToScan = listOf(filePath)
                } else {
                    // Single Album or Albums mode: find same album files from MediaStore
                    val albumFiles = findAlbumFiles(filePath)
                    // Always scan all found album files (even if only one - foobar2000 behavior)
                    filesToScan = albumFiles.ifEmpty { listOf(filePath) }
                }

                Timber.tag("Voxly").i("ReplayGainHelper: Scan started. mode=${currentScanMode.name} files=${filesToScan.size}")

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

                val scanFlow = when (currentScanMode) {
                    ScanMode.TRACK_ONLY -> replayGainRepository.scanReplayGain(
                        filesToScan,
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                    ScanMode.SINGLE_ALBUM -> replayGainRepository.scanReplayGainByAlbum(
                        mapOf("single_album" to filesToScan),
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                    ScanMode.ALBUMS -> replayGainRepository.scanReplayGainWithAlbumGrouping(
                        filesToScan,
                        scanQuality,
                        targetLoudness,
                        scanConfig
                    )
                }

                scanFlow.collect { progress ->
                    when (progress.status) {
                        com.voxly.domain.repository.ScanStatus.COMPLETED -> {
                            // Use ReplayGainInfo directly from progress if available
                            val info = progress.replayGainInfo
                            if (info != null) {
                                _pendingReplayGainInfo.update { info }
                                Timber.tag("Voxly").i("ReplayGainHelper: Scan completed (from progress). mode=${currentScanMode.name}")
                            } else {
                                // Fallback: read from file if not in progress
                                val replayGainReadResult = replayGainRepository.readReplayGain(filePath)
                                replayGainReadResult.getOrNull()?.let { readInfo ->
                                    _pendingReplayGainInfo.update { readInfo }
                                }
                                Timber.tag("Voxly").i("ReplayGainHelper: Scan completed (from file). mode=${currentScanMode.name}")
                            }
                            _isScanningReplayGain.update { false }
                        }
                        com.voxly.domain.repository.ScanStatus.FAILED -> {
                            // Determine error type based on reason
                            val error: ReplayGainScanError = when {
                                progress.currentFilePath.contains("Permission") ||
                                progress.currentFilePath.contains("EACCES") ->
                                    ReplayGainScanError.PermissionDenied(progress.currentFilePath)
                                progress.currentFilePath.contains("audio track") ||
                                progress.currentFilePath.contains("no audio") ||
                                progress.currentFilePath.contains("NO_AUDIO_TRACK") ->
                                    ReplayGainScanError.NoAudioTrack(progress.currentFilePath)
                                progress.currentFilePath.contains("ALL_FALLBACKS_EXHAUSTED") ->
                                    ReplayGainScanError.AllFallbacksFailed(progress.currentFilePath)
                                progress.currentFilePath.contains("decode") ||
                                progress.currentFilePath.contains("codec") ||
                                progress.currentFilePath.contains("DECODER_INIT_FAILED") ||
                                progress.currentFilePath.contains("fallback") ->
                                    ReplayGainScanError.DecodeFailed("解码失败", progress.currentFilePath)
                                else ->
                                    ReplayGainScanError.Unknown(progress.currentFilePath)
                            }
                            _replayGainScanError.emit(error.toString())
                            _isScanningReplayGain.update { false }
                            Timber.tag("Voxly").e("ReplayGainHelper: Scan failed.")
                        }
                        else -> { /* scanning in progress */ }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("Voxly").e(e, "ReplayGainHelper: Scan exception: ${e.message}")
                _isScanningReplayGain.update { false }
            }
        }
    }

    /**
     * Updates the pending ReplayGain info.
     * This should be called when ReplayGain scanning completes.
     * @param replayGainInfo The new ReplayGain info to save
     */
    fun updateReplayGainInfo(replayGainInfo: ReplayGainInfo) {
        _pendingReplayGainInfo.update { replayGainInfo }
    }

    /**
     * Clears the pending ReplayGain info.
     */
    fun clearReplayGainInfo() {
        _pendingReplayGainInfo.update { null }
    }

    /**
     * Clears the ReplayGain scan error.
     */
    fun clearReplayGainScanError() {
        // SharedFlow has no explicit clear, error is consumed via collect
    }

    /**
     * Reads existing ReplayGain info from a file.
     * @param filePath Path to the audio file
     */
    suspend fun readReplayGain(filePath: String) {
        val replayGainResult = replayGainRepository.readReplayGain(filePath)
        replayGainResult.getOrNull()?.let { replayGainInfo ->
            _pendingReplayGainInfo.update { replayGainInfo }
        }
    }

    /**
     * Saves ReplayGain info to a file.
     * @param filePath Path to the audio file
     * @param replayGainInfo ReplayGain info to save
     * @return true if save was successful
     */
    suspend fun saveReplayGain(filePath: String, replayGainInfo: ReplayGainInfo): Boolean {
        val result = replayGainRepository.saveReplayGain(filePath, replayGainInfo)
        return result.isSuccess
    }

    /**
     * Cancels any ongoing scan.
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanningReplayGain.update { false }
    }

    /**
     * Finds files in the same album using MediaStore.
     */
    private suspend fun findAlbumFiles(filePath: String): List<String> = withContext(Dispatchers.IO) {
        // We need to read metadata from file to get album/artist info
        // For now, we'll use the context to query MediaStore
        // This is a simplified version - in practice you'd inject metadata read capability
        
        val files = mutableListOf<String>()
        
        try {
            // Query for files with same album/artist
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH
                ),
                "${MediaStore.Audio.Media.ALBUM} IS NOT NULL",
                null,
                null
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val relativeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameColumn) ?: continue
                    val relativePath = cursor.getString(relativeColumn)
                    val path = buildPathFromRelativePath(relativePath, displayName)
                    if (path.isNotBlank()) {
                        files.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("ReplayGainHelper: Failed to find album files: ${e.message}", e)
        }
        
        files
    }

    private fun buildPathFromRelativePath(relativePath: String?, displayName: String): String {
        val sanitizedRelative = relativePath?.trimStart('/')?.replace('\\', '/') ?: ""
        val base = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        return if (sanitizedRelative.isBlank()) {
            "$base/$displayName"
        } else {
            "$base/$sanitizedRelative$displayName"
        }
    }
}