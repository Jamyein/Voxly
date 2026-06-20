package com.voxly.presentation.viewmodel

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.replaygain.AlbumGroupingProvider
import com.voxly.domain.model.ClipMode
import com.voxly.domain.model.ReplayGainConfig
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.model.ScanModeConstants
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanMode
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * Helper for ReplayGain scanning functionality in MetadataEditor.
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
class ReplayGainHelper @Inject constructor(
    private val replayGainRepository: ReplayGainRepository,
    private val audioRepository: AudioRepository,
    private val settingsDataStore: SettingsDataStore,
    private val albumGroupingProvider: AlbumGroupingProvider,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        scanJob = scope.launch {
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
                    ScanMode.ALBUMS -> {
                        val filesByAlbum = albumGroupingProvider.groupByAlbum(filesToScan)
                        replayGainRepository.scanReplayGainByAlbum(
                            filesByAlbum,
                            scanQuality,
                            targetLoudness,
                            scanConfig
                        )
                    }
                }

                scanFlow.collect { progress ->
                    when (progress.status) {
                        ScanStatus.COMPLETED -> {
                            progress.replayGainInfo?.let { _pendingReplayGainInfo.update { it } }
                            _isScanningReplayGain.update { false }
                        }
                        ScanStatus.FAILED,
                        ScanStatus.CANCELLED -> {
                            _replayGainScanError.tryEmit("Scan failed/cancelled for: ${progress.currentFilePath}")
                            _isScanningReplayGain.update { false }
                        }
                        else -> {
                            progress.replayGainInfo?.let { _pendingReplayGainInfo.update { it } }
                        }
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
        // Read the file's metadata to get album/artist identity, then query
        // MediaStore for other files in the same album.
        //
        // The previous implementation used `${ALBUM} IS NOT NULL` as the
        // selection, which matched EVERY album in the library. For SINGLE_ALBUM
        // mode this caused the scan to process the entire music library, the
        // UI stayed "scanning" until the very last track, and the user perceived
        // it as "stuck".
        val metadataResult = audioRepository.readMetadata(filePath)
        val metadata = metadataResult.getOrNull()
        val album = metadata?.album?.trim().orEmpty()
        val artist = metadata?.artist?.trim().orEmpty()

        if (album.isBlank()) {
            Timber.tag("Voxly").i("findAlbumFiles: source has no album info, returning just the file itself")
            return@withContext listOf(filePath)
        }

        val files = mutableListOf<String>()
        try {
            val selection = buildString {
                append("${MediaStore.Audio.Media.ALBUM} = ?")
                if (artist.isNotBlank()) append(" AND ${MediaStore.Audio.Media.ARTIST} = ?")
            }
            val selectionArgs = if (artist.isNotBlank()) {
                arrayOf(album, artist)
            } else {
                arrayOf(album)
            }

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH
                ),
                selection,
                selectionArgs,
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
            Timber.tag("Voxly").e(e, "findAlbumFiles: MediaStore query failed")
        }

        // Always include the source file (the user-initiated scan target) even if
        // MediaStore missed it (e.g. SAF-only path not indexed yet).
        if (files.none { it == filePath }) files.add(filePath)

        if (files.isEmpty()) listOf(filePath) else files
    }

    /**
     * Cancels any ongoing scan and disposes of the internal scope.
     *
     * MUST be called from [MetadataEditorViewModel.onCleared]. Previously the
     * internal `scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
     * was never cancelled: every navigation back into the editor created a fresh
     * `ReplayGainHelper` with its own scope, and the old scope kept running its
     * scanJob, leaking the helper reference and updating StateFlows that no
     * Compose subscriber was collecting. The user-visible symptom was the UI
     * staying "scanning" forever because the StateFlow update targeted the
     * disposed helper.
     */
    fun dispose() {
        scanJob?.cancel()
        scanJob = null
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        _isScanningReplayGain.update { false }
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