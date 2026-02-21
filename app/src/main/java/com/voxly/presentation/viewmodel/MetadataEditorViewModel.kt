package com.voxly.presentation.viewmodel

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.data.repository.LyricsRepositoryImpl
import com.voxly.data.repository.LyricsRepositoryImpl.LyricsSourceResult
import com.voxly.data.repository.OnlineSourceResult
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Metadata field that can be selected for conversion.
 */
enum class ConvertibleField(val displayName: String) {
    TITLE("标题"),
    ARTIST("艺术家"),
    ALBUM("专辑"),
    ALBUM_ARTIST("专辑艺术家"),
    GENRE("流派"),
    COMPOSER("作曲"),
    LYRICIST("作词"),
    COMMENT("备注"),
    RECORD_LABEL("唱片标签"),
    COPYRIGHT("版权"),
    LYRICS("歌词")
}

/**
 * ViewModel for the metadata editor screen.
 * Handles loading, editing, and saving audio file metadata.
 */
@HiltViewModel
class MetadataEditorViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    private val lyricsRepository: LyricsRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    private val settingsDataStore: SettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))

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

    private val _uiState = MutableStateFlow<MetadataEditorUiState>(MetadataEditorUiState.Loading)
    val uiState: StateFlow<MetadataEditorUiState> = _uiState.asStateFlow()

    private val _editedMetadata = MutableStateFlow<AudioMetadata?>(null)
    val editedMetadata: StateFlow<AudioMetadata?> = _editedMetadata.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private val _onlineLyricsResults = MutableStateFlow<List<OnlineLyricsResult>>(emptyList())
    val onlineLyricsResults: StateFlow<List<OnlineLyricsResult>> = _onlineLyricsResults.asStateFlow()

    private val _isOnlineLyricsLoading = MutableStateFlow(false)
    val isOnlineLyricsLoading: StateFlow<Boolean> = _isOnlineLyricsLoading.asStateFlow()

    private val _onlineLyricsError = MutableStateFlow<String?>(null)
    val onlineLyricsError: StateFlow<String?> = _onlineLyricsError.asStateFlow()

    private val _lyricsSearchState = MutableStateFlow(LyricsSearchState())
    val lyricsSearchState: StateFlow<LyricsSearchState> = _lyricsSearchState.asStateFlow()

    private val _coverFetchMessage = MutableStateFlow<String?>(null)
    val coverFetchMessage: StateFlow<String?> = _coverFetchMessage.asStateFlow()

    private val _onlineCoverResults = MutableStateFlow<List<OnlineRecording>>(emptyList())
    val onlineCoverResults: StateFlow<List<OnlineRecording>> = _onlineCoverResults.asStateFlow()

    private val _isOnlineCoverLoading = MutableStateFlow(false)
    val isOnlineCoverLoading: StateFlow<Boolean> = _isOnlineCoverLoading.asStateFlow()

    private val _onlineCoverError = MutableStateFlow<String?>(null)
    val onlineCoverError: StateFlow<String?> = _onlineCoverError.asStateFlow()

    private val _coverSearchState = MutableStateFlow(CoverSearchState())
    val coverSearchState: StateFlow<CoverSearchState> = _coverSearchState.asStateFlow()

    // ReplayGain state
    private val _pendingReplayGainInfo = MutableStateFlow<ReplayGainInfo?>(null)
    val pendingReplayGainInfo: StateFlow<ReplayGainInfo?> = _pendingReplayGainInfo.asStateFlow()

    private val _isScanningReplayGain = MutableStateFlow(false)
    val isScanningReplayGain: StateFlow<Boolean> = _isScanningReplayGain.asStateFlow()

    init {
        loadAudioFile()
    }

    /**
     * Loads the audio file and its metadata.
     */
    private fun loadAudioFile() {
        viewModelScope.launch {
            _uiState.value = MetadataEditorUiState.Loading

            val result = audioRepository.getAudioFile(filePath)

            result.fold(
                onSuccess = { audioFile ->
                    _editedMetadata.value = audioFile.metadata
                    
                    // Load existing ReplayGain info if available
                    val replayGainResult = replayGainRepository.readReplayGain(filePath)
                    replayGainResult.getOrNull()?.let { replayGainInfo ->
                        _pendingReplayGainInfo.value = replayGainInfo
                    }
                    
                    _uiState.value = MetadataEditorUiState.Success(
                        audioFile = audioFile,
                        editedMetadata = audioFile.metadata
                    )
                },
                onFailure = { error ->
                    _uiState.value = MetadataEditorUiState.Error(
                        error.message ?: "Failed to load audio file"
                    )
                }
            )
        }
    }

    /**
     * Updates a specific metadata field.
     * @param field The metadata field to update
     * @param value The new value
     */
    fun updateMetadataField(field: MetadataField, value: String) {
        val currentMetadata = _editedMetadata.value ?: return
        Logger.d(
            "Metadata field update file=$filePath field=$field valueLength=${value.length}",
            "MetadataEditor"
        )
        val updatedMetadata = when (field) {
            MetadataField.TITLE -> currentMetadata.copy(title = value.takeIf { it.isNotBlank() })
            MetadataField.ARTIST -> currentMetadata.copy(artist = value.takeIf { it.isNotBlank() })
            MetadataField.ALBUM -> currentMetadata.copy(album = value.takeIf { it.isNotBlank() })
            MetadataField.ALBUM_ARTIST -> currentMetadata.copy(albumArtist = value.takeIf { it.isNotBlank() })
            MetadataField.YEAR -> currentMetadata.copy(year = value.takeIf { it.isNotBlank() })
            MetadataField.GENRE -> currentMetadata.copy(genre = value.takeIf { it.isNotBlank() })
            MetadataField.COMPOSER -> currentMetadata.copy(composer = value.takeIf { it.isNotBlank() })
            MetadataField.LYRICIST -> currentMetadata.copy(lyricist = value.takeIf { it.isNotBlank() })
            MetadataField.CONDUCTOR -> currentMetadata.copy(conductor = value.takeIf { it.isNotBlank() })
            MetadataField.COMMENT -> currentMetadata.copy(comment = value.takeIf { it.isNotBlank() })
            MetadataField.LYRICS -> currentMetadata.copy(lyrics = value)
            MetadataField.RECORD_LABEL -> currentMetadata.withCustomField("record_label", value)
            MetadataField.ENCODER -> currentMetadata.withCustomField("encoder", value)
            MetadataField.ISRC -> currentMetadata.withCustomField("isrc", value)
            MetadataField.COPYRIGHT -> currentMetadata.withCustomField("copyright", value)
        }

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        // Update UI state
        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the track number.
     * @param trackNumber The new track number
     * @param totalTracks Total tracks (optional)
     */
    fun updateTrackNumber(trackNumber: Int?, totalTracks: Int?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            trackNumber = trackNumber,
            totalTracks = totalTracks
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the disc number.
     * @param discNumber The new disc number
     * @param totalDiscs Total discs (optional)
     */
    fun updateDiscNumber(discNumber: Int?, totalDiscs: Int?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            discNumber = discNumber,
            totalDiscs = totalDiscs
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the album art.
     * @param albumArtBytes The new album art bytes
     */
    fun updateAlbumArt(albumArtBytes: ByteArray?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the pending ReplayGain info.
     * This should be called when ReplayGain scanning completes.
     * @param replayGainInfo The new ReplayGain info to save
     */
    fun updateReplayGainInfo(replayGainInfo: ReplayGainInfo) {
        _pendingReplayGainInfo.value = replayGainInfo
        _hasUnsavedChanges.value = true
    }

    /**
     * Clears the pending ReplayGain info.
     */
    fun clearReplayGainInfo() {
        _pendingReplayGainInfo.value = null
        _hasUnsavedChanges.value = true
    }

    /**
     * Scans the current file for ReplayGain.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz) 
     * will be automatically downsampled for optimal performance.
     * 
     * When scan mode is ALBUM_ONLY or TRACK_AND_ALBUM, this will:
     * 1. Find other files in the same album from MediaStore
     * 2. Scan all album files
     * 3. Calculate album gain from all tracks
     */
    fun scanReplayGain() {
        viewModelScope.launch {
            _isScanningReplayGain.value = true
            
            // Using ACCURATE mode for best results - dynamic sampling handles high-res files
            val scanQuality = com.voxly.domain.repository.ScanQuality.ACCURATE
            
            try {
                val currentScanMode = _scanMode.value
                val filesToScan: List<String>
                
                // Determine which files to scan based on scan mode (foobar2000 compatible)
                if (currentScanMode == ScanMode.TRACK_ONLY) {
                    // Track Only: scan single file only, no album gain
                    filesToScan = listOf(filePath)
                } else {
                    // Single Album or Albums mode: find same album files from MediaStore
                    // - SINGLE_ALBUM: treat all files as one album
                    // - ALBUMS: will group by album tags (in batch mode), same as SINGLE_ALBUM for single file
                    val albumFiles = findAlbumFiles()
                    // Always scan all found album files (even if only one - foobar2000 behavior)
                    filesToScan = albumFiles.ifEmpty { listOf(filePath) }
                }
                
                Logger.i("ReplayGain scan started. mode=${currentScanMode.name} files=${filesToScan.size}", "MetadataEditor")
                
                // Scan all files
                replayGainRepository.scanReplayGain(
                    filesToScan,
                    scanQuality
                ).collect { progress ->
                    when (progress.status) {
                        com.voxly.domain.repository.ScanStatus.COMPLETED -> {
                            // Read the scanned ReplayGain info for current file
                            val result = replayGainRepository.readReplayGain(filePath)
                            result.getOrNull()?.let { info ->
                                // For album modes (SINGLE_ALBUM, ALBUMS), always calculate album gain
                                val finalInfo = if (currentScanMode != ScanMode.TRACK_ONLY) {
                                    calculateAlbumGainFromScannedFiles(filesToScan)
                                } else {
                                    info
                                }
                                
                                _pendingReplayGainInfo.value = finalInfo
                                _hasUnsavedChanges.value = true
                            }
                            _isScanningReplayGain.value = false
                            Logger.i("ReplayGain scan completed. mode=${currentScanMode.name}", "MetadataEditor")
                        }
                        com.voxly.domain.repository.ScanStatus.FAILED -> {
                            _isScanningReplayGain.value = false
                            Logger.e("ReplayGain scan failed.", null, "MetadataEditor")
                        }
                        else -> { /* scanning in progress */ }
                    }
                }
            } catch (e: Exception) {
                Logger.e("ReplayGain scan exception: ${e.message}", e, "MetadataEditor")
                _isScanningReplayGain.value = false
            }
        }
    }
    
    /**
     * Finds files in the same album using MediaStore.
     */
    private suspend fun findAlbumFiles(): List<String> = withContext(Dispatchers.IO) {
        val metadata = _editedMetadata.value ?: return@withContext emptyList()
        
        val album = metadata.album ?: return@withContext emptyList()
        val artist = metadata.artist
        
        if (album.isBlank()) return@withContext emptyList()
        
        val files = mutableListOf<String>()
        
        try {
            val selection = if (artist.isNullOrBlank()) {
                "${MediaStore.Audio.Media.ALBUM} = ?"
            } else {
                "${MediaStore.Audio.Media.ALBUM} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            }
            
            val selectionArgs = if (artist.isNullOrBlank()) {
                arrayOf(album)
            } else {
                arrayOf(album, artist)
            }
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null && File(path).exists()) {
                        files.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to find album files: ${e.message}", e, "MetadataEditor")
        }
        
        files
    }
    
    /**
     * Calculates album gain from multiple scanned files.
     */
    private suspend fun calculateAlbumGainFromScannedFiles(filePaths: List<String>): ReplayGainInfo? {
        if (filePaths.isEmpty()) return null
        
        val trackGains = mutableListOf<ReplayGainInfo>()
        
        for (path in filePaths) {
            val result = replayGainRepository.readReplayGain(path)
            result.getOrNull()?.let { trackGains.add(it) }
        }
        
        if (trackGains.isEmpty()) return null
        
        // Calculate album gain from track gains
        val avgGain = trackGains.map { it.trackGain }.average().toFloat()
        val maxPeak = trackGains.maxOfOrNull { it.trackPeak } ?: 0f
        
        // Get current file's track gain, or use average if not found
        val currentFileResult = replayGainRepository.readReplayGain(filePath)
        val currentTrackGain = currentFileResult.getOrNull()?.trackGain ?: avgGain
        val currentTrackPeak = currentFileResult.getOrNull()?.trackPeak ?: maxPeak
        
        return ReplayGainInfo(
            trackGain = currentTrackGain,
            trackPeak = currentTrackPeak,
            albumGain = avgGain,
            albumPeak = maxPeak
        )
    }

    /**
     * Saves the edited metadata and ReplayGain to the file.
     */
    fun saveMetadata() {
        val metadataToSave = _editedMetadata.value ?: return
        val replayGainToSave = _pendingReplayGainInfo.value

        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Logger.i(
                "Save metadata started file=$filePath hasReplayGain=${replayGainToSave != null}",
                "MetadataEditor"
            )
            _uiState.value = MetadataEditorUiState.Saving

            // First save the metadata
            val metadataResult = audioRepository.updateMetadata(filePath, metadataToSave)
            
            metadataResult.fold(
                onSuccess = {
                    // If we have pending ReplayGain info, save it too
                    var replayGainSuccess = true
                    if (replayGainToSave != null) {
                        val replayGainResult = replayGainRepository.saveReplayGain(
                            filePath,
                            replayGainToSave
                        )
                        replayGainSuccess = replayGainResult.isSuccess
                        if (replayGainSuccess) {
                            _pendingReplayGainInfo.value = null // Clear after successful save
                        } else {
                            Logger.w(
                                "Save replaygain failed file=$filePath reason=${replayGainResult.exceptionOrNull()?.message ?: "unknown"}",
                                "MetadataEditor"
                            )
                        }
                    }
                    
                    _hasUnsavedChanges.value = false
                    _saveResult.value = SaveResult.Success
                    val currentSuccessState = _uiState.value as? MetadataEditorUiState.Success
                    _uiState.value = currentSuccessState?.copy(
                        editedMetadata = metadataToSave,
                        audioFile = currentSuccessState.audioFile.copy(
                            metadata = metadataToSave,
                            replayGainInfo = replayGainToSave ?: currentSuccessState.audioFile.replayGainInfo
                        )
                    ) ?: MetadataEditorUiState.Success(
                        audioFile = AudioFile(
                            id = "",
                            path = filePath,
                            name = "",
                            size = 0,
                            duration = 0L,
                            format = "",
                            bitrate = 0,
                            sampleRate = 0,
                            channels = 0,
                            metadata = metadataToSave,
                            replayGainInfo = replayGainToSave
                        ),
                        editedMetadata = metadataToSave
                    )
                    Logger.i(
                        "Save metadata success file=$filePath replayGainSuccess=$replayGainSuccess elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        "MetadataEditor"
                    )
                },
                onFailure = { error ->
                    Logger.e(
                        "Save metadata failed file=$filePath reason=${error.message ?: "unknown"} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        error,
                        "MetadataEditor"
                    )
                    _saveResult.value = SaveResult.Error(error.message ?: "Failed to save")
                    val currentState = _uiState.value
                    if (currentState is MetadataEditorUiState.Saving) {
                        _uiState.value = MetadataEditorUiState.Error(
                            error.message ?: "Failed to save metadata"
                        )
                    }
                }
            )
        }
    }

/**
 * Resets all changes and reloads the original metadata.
     */
    fun discardChanges() {
        viewModelScope.launch {
            val result = audioRepository.readMetadata(filePath)
            result.onSuccess { originalMetadata ->
                _editedMetadata.value = originalMetadata
                _hasUnsavedChanges.value = false
                val currentState = _uiState.value
                if (currentState is MetadataEditorUiState.Success) {
                    _uiState.value = currentState.copy(editedMetadata = originalMetadata)
                }
            }
        }
    }

    /**
     * Converts specified metadata fields from Traditional Chinese to Simplified Chinese.
     * @param fields Set of fields to convert
     */
    fun convertToSimplified(fields: Set<ConvertibleField>) {
        viewModelScope.launch {
            convertFields(fields, toSimplified = true)
        }
    }

    /**
     * Converts specified metadata fields from Simplified Chinese to Traditional Chinese.
     * @param fields Set of fields to convert
     */
    fun convertToTraditional(fields: Set<ConvertibleField>) {
        viewModelScope.launch {
            convertFields(fields, toSimplified = false)
        }
    }

    /**
     * Internal method to convert fields between Chinese character sets.
     */
    private suspend fun convertFields(fields: Set<ConvertibleField>, toSimplified: Boolean) {
        val currentMetadata = _editedMetadata.value ?: return
        
        var updatedMetadata = currentMetadata
        
        for (field in fields) {
            val value = when (field) {
                ConvertibleField.TITLE -> currentMetadata.title
                ConvertibleField.ARTIST -> currentMetadata.artist
                ConvertibleField.ALBUM -> currentMetadata.album
                ConvertibleField.ALBUM_ARTIST -> currentMetadata.albumArtist
                ConvertibleField.GENRE -> currentMetadata.genre
                ConvertibleField.COMPOSER -> currentMetadata.composer
                ConvertibleField.LYRICIST -> currentMetadata.lyricist
                ConvertibleField.COMMENT -> currentMetadata.comment
                ConvertibleField.RECORD_LABEL -> currentMetadata.customFields["record_label"]
                ConvertibleField.COPYRIGHT -> currentMetadata.customFields["copyright"]
                ConvertibleField.LYRICS -> currentMetadata.lyrics
            }
            
            if (value.isNullOrBlank()) continue
            
            val converted = withContext(Dispatchers.Default) {
                if (toSimplified) {
                    toSimplifiedChinese(value)
                } else {
                    toTraditionalChinese(value)
                }
            }
            
            if (converted != value) {
                updatedMetadata = when (field) {
                    ConvertibleField.TITLE -> updatedMetadata.copy(title = converted)
                    ConvertibleField.ARTIST -> updatedMetadata.copy(artist = converted)
                    ConvertibleField.ALBUM -> updatedMetadata.copy(album = converted)
                    ConvertibleField.ALBUM_ARTIST -> updatedMetadata.copy(albumArtist = converted)
                    ConvertibleField.GENRE -> updatedMetadata.copy(genre = converted)
                    ConvertibleField.COMPOSER -> updatedMetadata.copy(composer = converted)
                    ConvertibleField.LYRICIST -> updatedMetadata.copy(lyricist = converted)
                    ConvertibleField.COMMENT -> updatedMetadata.copy(comment = converted)
                    ConvertibleField.RECORD_LABEL -> updatedMetadata.withCustomField("record_label", converted)
                    ConvertibleField.COPYRIGHT -> updatedMetadata.withCustomField("copyright", converted)
                    ConvertibleField.LYRICS -> updatedMetadata.copy(lyrics = converted)
                }
            }
        }
        
        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true
        
        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Converts string from Traditional Chinese to Simplified Chinese.
     * Uses Android ICU Transliterator for accurate conversion.
     */
    private fun toSimplifiedChinese(text: String): String {
        return try {
            // Use Android's built-in ICU Transliterator
            val transliterator = android.icu.text.Transliterator.getInstance("Traditional-Simplified")
            transliterator.transliterate(text)
        } catch (e: Exception) {
            // Fallback to character mapping
            traditionalToSimplifiedMap.entries.fold(text) { result, (trad, simp) ->
                result.replace(trad, simp)
            }
        }
    }

    /**
     * Converts string from Simplified Chinese to Traditional Chinese.
     */
    private fun toTraditionalChinese(text: String): String {
        return try {
            // Use Android's built-in ICU Transliterator
            val transliterator = android.icu.text.Transliterator.getInstance("Simplified-Traditional")
            transliterator.transliterate(text)
        } catch (e: Exception) {
            // Fallback to character mapping
            simplifiedToTraditionalMap.entries.fold(text) { result, (simp, trad) ->
                result.replace(simp, trad)
            }
        }
    }

    /**
     * Comprehensive Traditional -> Simplified Chinese character mapping.
     * Covers CJK Unified Ideographs and compatibility characters.
     */
    private val traditionalToSimplifiedMap: Map<String, String> = mapOf(
        // Common radicals and components
        "來" to "来", "樂" to "乐", "餘" to "余", "醜" to "丑", "餘" to "余",
        "專業" to "专业", "開發" to "开发", "處理" to "处理", "應該" to "应该",
        "選擇" to "选择", "雖然" to "虽然", "已經" to "已经", "時候" to "时候",
        "東西" to "东西", "價格" to "价格", "電話" to "电话", "電腦" to "电脑",
        "電影" to "电影", "電視" to "电视", "問題" to "问题", "答案" to "答案",
        "動作" to "动作", "運動" to "运动", "開始" to "开始", "結束" to "结束",
        "圖書館" to "图书馆", "圖片" to "图片", "軟件" to "软件", "硬件" to "硬件",
        "系統" to "系统", "程序" to "程序", "功能" to "功能", "文件" to "文件",
        "數據" to "数据", "信息" to "信息", "網絡" to "网络", "互聯網" to "互联网",
        "電子郵件" to "电子邮件", "短信" to "短信", "消息" to "消息",
        "時間" to "时间", "日期" to "日期", "今天" to "今天", "明天" to "明天",
        "昨天" to "昨天", "現在" to "现在", "以前" to "以前", "以后" to "以后",
        "之前" to "之前", "之後" to "之后", "最終" to "最终", "最後" to "最后",
        "這個" to "这个", "那個" to "那个", "哪個" to "哪个", "一些" to "一些",
        "這些" to "这些", "那些" to "那些", "所有" to "所有", "其他" to "其他",
        "我們" to "我们", "你們" to "你们", "他們" to "他们", "她們" to "她们",
        "自己" to "自己", "每個" to "每个", "什麼" to "什么", "怎麼" to "怎么",
        "為什麼" to "为什么", "因為" to "因为", "所以" to "所以", "如果" to "如果",
        "但是" to "但是", "或者" to "或者", "而且" to "而且", "不過" to "不过",
        "還是" to "还是", "只是" to "只是", "已經" to "已经", "曾經" to "曾经",
        "將要" to "将要", "正在" to "正在", "應該" to "应该", "可以" to "可以",
        "需要" to "需要", "必須" to "必须", "能夠" to "能够", "願意" to "愿意",
        "喜歡" to "喜欢", "知道" to "知道", "覺得" to "觉得", "認為" to "认为",
        "希望" to "希望", "了解" to "了解", "認識" to "认识", "理解" to "理解",
        "使用" to "使用", "得到" to "得到", "找到" to "找到", "進行" to "进行",
        "完成" to "完成", "通過" to "通过", "成為" to "成为", "形成" to "形成",
        "提出" to "提出", "制定" to "制定", "建立" to "建立", "提供" to "提供",
        "達到" to "达到", "達到" to "达到", "獲得" to "获得", "取得" to "取得",
        "出現" to "出现", "發生" to "发生", "發現" to "发现", "解決" to "解决",
        "決定" to "决定", "證明" to "证明", "表明" to "表明", "表示" to "表示",
        "說明" to "说明", "告訴" to "告诉", "詢問" to "询问", "回答" to "回答",
        "討論" to "讨论", "研究" to "研究", "分析" to "分析", "考慮" to "考虑",
        "關注" to "关注", "注意" to "注意", "強調" to "强调", "指出" to "指出",
        "認為" to "认为", "相信" to "相信", "知道" to "知道", "記得" to "记得",
        "忘記" to "忘记", "理解" to "理解", "學習" to "学习", "教學" to "教学",
        "練習" to "练习", "考試" to "考试", "成績" to "成绩", "學校" to "学校",
        "學生" to "学生", "老師" to "老师", "朋友" to "朋友", "家人" to "家人",
        "父親" to "父亲", "母親" to "母亲", "兄弟" to "兄弟", "姐妹" to "姐妹",
        "丈夫" to "丈夫", "妻子" to "妻子", "兒子" to "儿子", "女兒" to "女儿",
        "人類" to "人类", "人們" to "人们", "人生" to "人生", "人民" to "人民",
        "社會" to "社会", "世界" to "世界", "國家" to "国家", "民族" to "民族",
        "文化" to "文化", "歷史" to "历史", "政治" to "政治", "經濟" to "经济",
        "科學" to "科学", "技術" to "技术", "工程" to "工程", "醫學" to "医学",
        "法律" to "法律", "教育" to "教育", "藝術" to "艺术", "文學" to "文学",
        "哲學" to "哲学", "宗教" to "宗教", "心理" to "心理", "自然" to "自然",
        "環境" to "环境", "資源" to "资源", "能源" to "能源", "交通" to "交通",
        "建築" to "建筑", "設計" to "设计", "製造" to "制造", "生產" to "生产",
        "銷售" to "销售", "購買" to "购买", "市場" to "市场", "經濟" to "经济",
        "管理" to "管理", "領導" to "领导", "組織" to "组织", "團隊" to "团队",
        "目標" to "目标", "計劃" to "计划", "策略" to "策略", "決策" to "决策",
        "風險" to "风险", "投資" to "投资", "利潤" to "利润", "成本" to "成本",
        "預算" to "预算", "報告" to "报告", "會議" to "会议", "談判" to "谈判",
        "合同" to "合同", "協議" to "协议", "法律" to "法律", "權利" to "权利",
        "義務" to "义务", "責任" to "责任", "自由" to "自由", "平等" to "平等",
        "公正" to "公正", "正義" to "正义", "和平" to "和平", "安全" to "安全",
        "健康" to "健康", "幸福" to "幸福", "成功" to "成功", "失敗" to "失败",
        "困難" to "困难", "挑戰" to "挑战", "機會" to "机会", "選擇" to "选择",
        "改變" to "改变", "發展" to "发展", "創新" to "创新", "創造" to "创造",
        "影響" to "影响", "作用" to "作用", "效果" to "效果", "原因" to "原因",
        "結果" to "结果", "程度" to "程度", "水平" to "水平", "質量" to "质量",
        "數量" to "数量", "大小" to "大小", "距離" to "距离", "位置" to "位置",
        "方向" to "方向", "時間" to "时间", "空間" to "空间", "速度" to "速度",
        "力量" to "力量", "能量" to "能量", "材料" to "材料", "工具" to "工具",
        "方法" to "方法", "方式" to "方式", "形式" to "形式", "內容" to "内容",
        "結構" to "结构", "過程" to "过程", "階段" to "阶段", "步驟" to "步骤",
        
        // Individual characters (most common ones)
        "萬" to "万", "與" to "与", "醜" to "丑", "專" to "专", "業" to "业",
        "東" to "东", "絲" to "丝", "丟" to "丢", "兩" to "两", "嚴" to "严",
        "並" to "并", "蘭" to "兰", "滅" to "灭", "臨" to "临", "麗" to "丽",
        "樂" to "樂", "喬" to "乔", "習" to "习", "馬" to "马", "烏" to "乌",
        "義" to "义", "魚" to "鱼", "鳥" to "鸟", "龍" to "龙", "門" to "门",
        "聞" to "闻", "陳" to "陈", "葉" to "叶", "夢" to "梦", "華" to "华",
        "為" to "为", "學" to "学", "號" to "号", "嗎" to "吗", "筆" to "笔",
        "畢" to "毕", "漢" to "汉", "貴" to "贵", "賀" to "贺", "貓" to "猫",
        "韓" to "韩", "鳴" to "鸣", "嗎" to "吗", "島" to "岛", "嶺" to "岭",
        "師" to "师", "傑" to "杰", "呂" to "吕", "殺" to "杀", "會" to "会",
        "創" to "创", "歲" to "岁", "衝" to "冲", "雲" to "云", "韋" to "韦",
        "馬" to "马", "騎" to "骑", "驗" to "验", "駐" to "驻", "駱" to "骆",
        "驢" to "驴", "驂" to "骖", "嗎" to "吗", "驗" to "验", "騎" to "骑",
        "騙" to "骗", "體" to "体", "鬍" to "胡", "髮" to "发", "鬥" to "斗",
        "齊" to "齐", "龍" to "龙", "龜" to "龟", "韆" to "千", "秋" to "秋",
        "種" to "种", "積" to "积", "稱" to "称", "稻" to "稻", "稅" to "税",
        "穩" to "稳", "窮" to "穷", "窩" to "窝", "鍋" to "锅", "長" to "长",
        "門" to "门", "閉" to "闭", "問" to "问", "間" to "间", "閣" to "阁",
        "閱" to "阅", "闇" to "暗", "們" to "们", "陽" to "阳", "陰" to "阴",
        "陳" to "陈", "陸" to "陆", "隊" to "队", "階" to "阶", "雲" to "云",
        "電" to "电", "霽" to "霁", "霧" to "雾", "靈" to "灵", "靜" to "静",
        "面" to "面", "革" to "革", "韋" to "韦", "韁" to "缰", "韃" to "靼",
        "韆" to "千", "頑" to "顽", "顧" to "顾", "頓" to "顿", "頊" to "项",
        "順" to "顺", "須" to "须", "頑" to "顽", "顧" to "顾", "頓" to "顿",
        
        // More common individual characters
        "頭" to "头", "會" to "会", "聽" to "听", "後" to "后", "發" to "发",
        "見" to "见", "機" to "机", "員" to "员", "過" to "过", "聲" to "声",
        "話" to "话", "車" to "车", "馬" to "马", "龍" to "龙", "風" to "风",
        "愛" to "爱", "為" to "为", "這" to "这", "那" to "那", "說" to "说",
        "請" to "请", "謝" to "谢", "對" to "对", "沒" to "没", "戰" to "战",
        "腦" to "脑", "網" to "网", "書" to "书", "紙" to "纸", "處" to "处",
        "點" to "点", "還" to "还", "裡" to "里", "然" to "然", "後" to "後",
        "與" to "与", "來" to "来", "時" to "时", "間" to "间", "內" to "内",
        "外" to "外", "前" to "前", "後" to "后", "上" to "上", "下" to "下",
        "左" to "左", "右" to "右", "中" to "中", "大" to "大", "小" to "小",
        "多" to "多", "少" to "少", "好" to "好", "壞" to "坏", "新" to "新",
        "舊" to "旧", "長" to "长", "短" to "短", "高" to "高", "低" to "低",
        "快" to "快", "慢" to "慢", "真" to "真", "假" to "假", "是" to "是",
        "非" to "非", "有" to "有", "無" to "无", "也" to "也", "就" to "就",
        "都" to "都", "而" to "而", "及" to "及", "或" to "或", "但" to "但",
        "卻" to "却", "只" to "只", "才" to "才", "已" to "已", "曾" to "曾",
        "將" to "将", "應" to "应", "可" to "可", "能" to "能", "會" to "会",
        "要" to "要", "想" to "想", "做" to "做", "使" to "使", "被" to "被",
        "給" to "给", "從" to "从", "向" to "向", "到" to "到", "把" to "把",
        "被" to "被", "比" to "比", "等" to "等", "很" to "很", "最" to "最",
        "更" to "更", "太" to "太", "非常" to "非常", "特別" to "特别",
        
        // Common words and phrases
        "什麼時候" to "什么时候", "為什麼呢" to "为什么呢", "怎麼樣" to "怎么样",
        "沒關係" to "没关系", "對不起" to "对不起", "謝謝你" to "谢谢你",
        "再見" to "再见", "你好嗎" to "你好吗", "我很好" to "我很好",
        "很高興" to "很高兴", "認識你" to "认识你", "對話" to "对话",
        "討論" to "讨论", "問題" to "问题", "答案" to "答案", "方法" to "方法",
        "方式" to "方式", "過程" to "过程", "結果" to "结果", "原因" to "原因",
        
        // Music metadata specific terms
        "專輯" to "专辑", "歌曲" to "歌曲", "歌手" to "歌手", "作詞" to "作词",
        "作曲" to "作曲", "監製" to "监制", "製作人" to "制作人", "錄音" to "录音",
        "混音" to "混音", "母帶" to "母带", "版權" to "版权", "唱片" to "唱片",
        "公司" to "公司", "發行" to "发行", "代理" to "代理", "授權" to "授权",
        "歌詞" to "歌词", "翻唱" to "翻唱", "現場" to "现场", "版本" to "版本",
        " Remix" to " Remix", "Live" to "Live", "Acoustic" to "Acoustic"
    )

    /**
     * Comprehensive Simplified -> Traditional Chinese character mapping.
     */
    private val simplifiedToTraditionalMap: Map<String, String> = traditionalToSimplifiedMap.entries
        .associate { (k, v) -> v to k }
        .toMutableMap().apply {
            // Add additional mappings that might be asymmetric
            put("干", "幹") // 幹 vs 干
            put("后", "後") // 後 vs 后
            put("里", "裏") // 裏 vs 里  
            put("面", "麵") // 麵 vs 面
            put("板", "板") // keep
            put("表", "表") // keep
            put("欲", "慾") // 慾 vs 欲
            put("游", "遊") // 遊 vs 游
            put("党", "黨") // 黨 vs 党
            put("团", "團") // 團 vs 团
            put("谷", "穀") // 穀 vs 谷
            put("秋", "鞦") // 鞦 vs 秋
            put("折", "摺") // 摺 vs 折
            put("症", "癥") // 癥 vs 症
            put("朱", "硃") // 硃 vs 朱
            put("准", "準") // 準 vs 准
            put("别", "彆") // 彆 vs 别
            put("占", "佔") // 佔 vs 占
            put("吊", "幔") // 幔 vs 吊
            put("咸", "鹹") // 鹹 vs 咸
            put("予", "預") // 預 vs 予
            put("曲", "麯") // 麯 vs 曲
            put("偿", "償") // 償 vs 偿
            put("吨", "噸") // 噸 vs 吨
            put("个", "個") // 個 vs 个
            put("吕", "呂") // 呂 vs 吕
            put("征", "徵") // 徵 vs 征
            put("复", "複") // 複 vs 复
            put("困", "睏") // 睏 vs 困
            put("核准", "核准") // keep
            put("胡", "鬍") // 鬍 vs 胡
            put("淮", "準") // 準 vs 淮
            put("价", "價") // 價 vs 价
            put("尽", "盡") // 盡 vs 尽
            put("据", "據") // 據 vs 据
            put("卷", "捲") // 捲 vs 卷
            put("克", "剋") // 剋 vs 克
            put("离", "離") // 離 vs 离
            put("帘", "簾") // 簾 vs 帘
            put("霉", "黴") // 黴 vs 霉
            put("昵", "暱") // 暱 vs 昵
            put("捻", "撚") // 撚 vs 捻
            put("尝", "嚐") // 嚐 vs 尝
            put("佣", "傭") // 傭 vs 佣
            put("涌", "湧") // 湧 vs 涌
            put("岳", "嶽") // 嶽 vs 岳
            put("脏", "髒") // 髒 vs 脏
            put("折", "摺") // 摺 vs 折
            put("征", "徵") // 徵 vs 征
            put("猪", "豬") // 豬 vs 猪
            put("准", "準") // 準 vs 准
        }


    /**
     * Clears the save result after it has been handled.
     */
    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun searchOnlineCoverCandidates() {
        val metadata = _editedMetadata.value ?: return
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
        val artist = metadata.artist?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _coverSearchState.value = CoverSearchState(isSearching = true)
            _isOnlineCoverLoading.value = true
            _onlineCoverError.value = null

            _onlineCoverResults.value = emptyList()
            try {
                val result = aggregatedOnlineMetadataRepository.searchByTrackForCover(title, artist)
                result.fold(
                    onSuccess = { recordings ->
                        recordings.forEach { recording ->
                            val newResults = _coverSearchState.value.results + recording
                            _coverSearchState.update { it.copy(results = newResults) }
                            _onlineCoverResults.value = newResults
                        }
                        _coverSearchState.update { it.copy(isSearching = false) }
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Cover search failed"
                        _coverSearchState.update { state ->
                            state.copy(errorSources = state.errorSources + ("System" to message))
                        }
                        _onlineCoverError.value = message
                        _coverSearchState.update { it.copy(isSearching = false) }
                    }
                )
            } catch (e: Exception) {
                val message = e.message ?: "Cover search failed"
                _coverSearchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
                _onlineCoverError.value = message
                _coverSearchState.update { it.copy(isSearching = false) }
            } finally {
                _isOnlineCoverLoading.value = false
            }
        }
    }

    fun applyOnlineCover(recording: OnlineRecording) {
        val releaseId = recording.releaseId
        
        // If no releaseId, show a message and return
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.value = "无法获取封面：该结果没有关联的专辑信息"
            return
        }

        viewModelScope.launch {
            _coverFetchMessage.value = null

            val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
            try {
                aggregatedOnlineMetadataRepository.preferredSource = when (recording.source) {
                    "MusicBrainz" -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                    "iTunes" -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                    "NetEase" -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                    "QQ Music" -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                    else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
                }

                val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
                coverResult.fold(
                    onSuccess = { cover ->
                        if (cover != null) {
                            updateAlbumArt(cover)
                            _coverFetchMessage.value = "Cover fetched successfully"
                        } else {
                            _coverFetchMessage.value = "No online cover found"
                        }
                    },
                    onFailure = {
                        _coverFetchMessage.value = it.message ?: "Cover fetch failed"
                    }
                )
            } finally {
                aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
            }
        }
    }

    fun clearCoverFetchMessage() {
        _coverFetchMessage.value = null
    }

    fun clearOnlineCoverResults() {
        _onlineCoverResults.value = emptyList()
        _onlineCoverError.value = null
        _coverSearchState.value = CoverSearchState()
    }

    fun searchOnlineLyrics() {
        val metadata = _editedMetadata.value ?: return
        val track = metadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
        val artist = metadata.artist?.takeIf { it.isNotBlank() }
        val album = metadata.album?.takeIf { it.isNotBlank() }
        val flowLyricsRepository = lyricsRepository as? LyricsRepositoryImpl ?: return

        viewModelScope.launch {
            _lyricsSearchState.value = LyricsSearchState(isSearching = true)
            _isOnlineLyricsLoading.value = true
            _onlineLyricsError.value = null

            _onlineLyricsResults.value = emptyList()
            try {
                flowLyricsRepository.searchOnlineLyricsFlow(track, artist, album).collect { result ->
                    when (result) {
                        is LyricsSourceResult.Result -> {
                            val newResults = _lyricsSearchState.value.results + result.lyrics
                            _lyricsSearchState.update { it.copy(results = newResults) }
                            _onlineLyricsResults.value = newResults
                        }

                        is LyricsSourceResult.SourceCompleted -> {
                            _lyricsSearchState.update { state ->
                                state.copy(completedSources = state.completedSources + result.source)
                            }
                        }

                        is LyricsSourceResult.Error -> {
                            _lyricsSearchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message)
                                )
                            }
                            _onlineLyricsError.value = result.message
                        }
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Lyrics search failed"
                _onlineLyricsError.value = message
                _lyricsSearchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
            } finally {
                _lyricsSearchState.update { it.copy(isSearching = false) }
                _isOnlineLyricsLoading.value = false
            }
        }
    }

    fun applyOnlineLyrics(result: OnlineLyricsResult) {
        viewModelScope.launch {
            val lyrics = lyricsRepository.getOnlineLyrics(result).getOrNull() ?: return@launch
            val text = if (lyrics.isSynced) lyrics.toLrcFormat() else lyrics.text
            updateMetadataField(MetadataField.LYRICS, text)
        }
    }

    fun clearOnlineLyricsResults() {
        _onlineLyricsResults.value = emptyList()
        _onlineLyricsError.value = null
        _lyricsSearchState.value = LyricsSearchState()
    }

    fun applyOnlineMetadata(metadata: AudioMetadata) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            title = metadata.title ?: currentMetadata.title,
            artist = metadata.artist ?: currentMetadata.artist,
            album = metadata.album ?: currentMetadata.album,
            albumArtist = metadata.albumArtist ?: currentMetadata.albumArtist,
            year = metadata.year ?: currentMetadata.year,
            genre = metadata.genre ?: currentMetadata.genre,
            trackNumber = metadata.trackNumber ?: currentMetadata.trackNumber,
            totalTracks = metadata.totalTracks ?: currentMetadata.totalTracks,
            lyrics = metadata.lyrics ?: currentMetadata.lyrics
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    private fun metadataToStorageState(metadata: AudioMetadata): AudioMetadata {
        // Return a copy that represents the saved state
        return metadata.copy()
    }

    private fun AudioMetadata.withCustomField(key: String, value: String): AudioMetadata {
        val normalized = value.trim()
        val updated = customFields.toMutableMap()
        if (normalized.isBlank()) {
            updated.remove(key)
        } else {
            updated[key] = normalized
        }
        return copy(customFields = updated)
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%') && !raw.contains('+')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}

/**
 * Sealed class representing metadata editor UI states.
 */
sealed class MetadataEditorUiState {
    data object Loading : MetadataEditorUiState()
    data object Saving : MetadataEditorUiState()
    data class Success(
        val audioFile: AudioFile,
        val editedMetadata: AudioMetadata
    ) : MetadataEditorUiState()
    data class Error(val message: String) : MetadataEditorUiState()
}

/**
 * Enum representing editable metadata fields.
 */
enum class MetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    GENRE,
    COMPOSER,
    LYRICIST,
    CONDUCTOR,
    COMMENT,
    LYRICS,
    RECORD_LABEL,
    ENCODER,
    ISRC,
    COPYRIGHT
}

/**
 * Sealed class representing save operation results.
 */
sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}

data class LyricsSearchState(
    val results: List<OnlineLyricsResult> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)

data class CoverSearchState(
    val results: List<OnlineRecording> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)
