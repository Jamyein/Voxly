package com.voxly.presentation.viewmodel

import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import timber.log.Timber
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.saf.SafGrantType
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.RecentEditsRepository
import com.voxly.domain.usecase.ApplyOnlineMetadataUseCase
import com.voxly.domain.usecase.SaveMetadataUseCase
import com.voxly.presentation.components.lyricsposter.ColorExtractor
import com.voxly.presentation.components.lyricsposter.ColorExtractor.M3EColors
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.ui.rotateJpegBytes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.voxly.presentation.viewmodel.SearchSeedHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.icu.text.Transliterator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * ViewModel for the metadata editor screen.
 * Handles loading, editing, and saving audio file metadata.
 * 
 * Delegated responsibilities:
 * - LyricsSearchHelper: Online lyrics search and results
 * - CoverSearchHelper: Online cover search and results  
 * - ReplayGainHelper: ReplayGain scanning and info management
 */
@HiltViewModel(assistedFactory = MetadataEditorViewModel.Factory::class)
class MetadataEditorViewModel @AssistedInject constructor(
    @Assisted val navKey: MetadataEditor,
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    private val lyricsRepository: LyricsRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    private val onlineLyricsSearchStrategy: OnlineLyricsSearchStrategy,
    private val coverSearchStrategy: CoverSearchStrategy,
    private val settingsDataStore: SettingsDataStore,
    private val safWriteAccessService: SafWriteAccessService,
    private val recentEditsRepository: RecentEditsRepository,
    private val audioFileScanner: AudioFileScanner,
    private val musicLibraryCache: MusicLibraryCache,
    private val searchSeedHolder: SearchSeedHolder,
    private val pendingMetadataHolder: PendingMetadataHolder,
    private val saveMetadataUseCase: SaveMetadataUseCase,
    private val applyOnlineMetadataUseCase: ApplyOnlineMetadataUseCase,
    // Delegated helpers for separation of concerns
    val lyricsSearchHelper: LyricsSearchHelper,
    val coverSearchHelper: CoverSearchHelper,
    val replayGainHelper: ReplayGainHelper,
    private val metadataSaveCoordinator: MetadataSaveCoordinator,
    @Named("ApplicationScope") private val applicationScope: kotlinx.coroutines.CoroutineScope
) : ViewModel() {

    private val TAG = "MetadataEditorVM"

    /**
     * Long-lived scope used for post-save cache sync. We MUST run the sync here
     * (not on `viewModelScope`) because users commonly navigate away from the
     * editor immediately after the success snackbar appears; on `viewModelScope`
     * the in-flight sync would be cancelled by `onCleared()` before the Room
     * write and `CacheChange.FileUpdated` emission happen, leaving the song
     * list page showing the OLD metadata.
     */

    // Get filePath from NavKey instead of SavedStateHandle
    private val filePath: String = navKey.filePath

    val metadataEditorDynamicAlbumColor: StateFlow<Boolean> = settingsDataStore.metadataEditorDynamicAlbumColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(15000),
            initialValue = true
        )

    private val _uiState = MutableStateFlow<MetadataEditorUiState>(MetadataEditorUiState.Loading)
    val uiState: StateFlow<MetadataEditorUiState> = _uiState.asStateFlow()

    private val _editedMetadata = MutableStateFlow<AudioMetadata?>(null)
    val editedMetadata: StateFlow<AudioMetadata?> = _editedMetadata.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _modifiedFields = MutableStateFlow<Set<MetadataField>>(emptySet())
    val modifiedFields: StateFlow<Set<MetadataField>> = _modifiedFields.asStateFlow()

    private val _saveResult = MutableSharedFlow<String>()
    val saveResult: SharedFlow<String> = _saveResult.asSharedFlow()

    // Re-expose lyrics search state from helper
    val onlineLyricsResults: StateFlow<List<OnlineLyricsResult>> = lyricsSearchHelper.onlineLyricsResults
    val isOnlineLyricsLoading: StateFlow<Boolean> = lyricsSearchHelper.isOnlineLyricsLoading
    val onlineLyricsError: StateFlow<String?> = lyricsSearchHelper.onlineLyricsError
    val lyricsSearchState: StateFlow<LyricsSearchState> = lyricsSearchHelper.lyricsSearchState

    // Re-expose cover search state from helper
    val coverFetchMessage: SharedFlow<String> = coverSearchHelper.coverFetchMessage
    val onlineCoverResults: StateFlow<List<OnlineRecording>> = coverSearchHelper.onlineCoverResults
    val isOnlineCoverLoading: StateFlow<Boolean> = coverSearchHelper.isOnlineCoverLoading
    val onlineCoverError: StateFlow<String?> = coverSearchHelper.onlineCoverError
    val coverSearchState: StateFlow<CoverSearchState> = coverSearchHelper.coverSearchState

    // Re-expose ReplayGain state from helper
    val pendingReplayGainInfo: StateFlow<ReplayGainInfo?> = replayGainHelper.pendingReplayGainInfo
    val isScanningReplayGain: StateFlow<Boolean> = replayGainHelper.isScanningReplayGain
    val replayGainScanError: SharedFlow<String> = replayGainHelper.replayGainScanError

    private var _originalMetadata: AudioMetadata? = null

    // Lyrics timestamp format state
    private val _isLyricsTimestampFormatted = MutableStateFlow(false)
    val isLyricsTimestampFormatted: StateFlow<Boolean> = _isLyricsTimestampFormatted.asStateFlow()

    // Album art colors extracted from cover image via Palette API
    private val _m3eColors = MutableStateFlow<M3EColors?>(null)
    val m3eColors: StateFlow<M3EColors?> = _m3eColors.asStateFlow()
    private val _isM3eColorsResolved = MutableStateFlow(false)
    val isM3eColorsResolved: StateFlow<Boolean> = _isM3eColorsResolved.asStateFlow()

    // Debounced text input StateFlows - moved from Composable to ViewModel to avoid recomposition issues
    private val _titleTextFlow = MutableStateFlow<String?>(null)
    private val _artistTextFlow = MutableStateFlow<String?>(null)
    private val _albumTextFlow = MutableStateFlow<String?>(null)
    private val _albumArtistTextFlow = MutableStateFlow<String?>(null)
    private val _yearTextFlow = MutableStateFlow<String?>(null)
    private val _genreTextFlow = MutableStateFlow<String?>(null)
    private val _composerTextFlow = MutableStateFlow<String?>(null)
    private val _lyricistTextFlow = MutableStateFlow<String?>(null)
    private val _commentTextFlow = MutableStateFlow<String?>(null)
    private val _lyricsTextFlow = MutableStateFlow<String?>(null)

    private val debounceJobs = mutableListOf<Job>()

    @Suppress("Unused")
    val editState: StateFlow<EditState> = combine(
        _hasUnsavedChanges,
        _modifiedFields
    ) { hasUnsavedChanges, modifiedFields ->
        EditState(
            hasUnsavedChanges = hasUnsavedChanges,
            modifiedFields = modifiedFields.toPersistentSet(),
            saveResult = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditState()
    )

    init {
        // Initialize modified fields to empty - will be populated only by actual edits
        _modifiedFields.update { emptySet() }
        _hasUnsavedChanges.update { false }
        
        loadAudioFile()
        viewModelScope.launch {
            pendingMetadataHolder.pending.collect { pendingMap ->
                if (pendingMap.containsKey(filePath) && _editedMetadata.value != null) {
                    tryApplyPendingOnlineMetadata()
                }
            }
        }

        // When ReplayGain scan completes and a result arrives, reload file metadata
        // to reflect the newly written ReplayGain tags in the UI.
        viewModelScope.launch {
            var lastScanState = replayGainHelper.isScanningReplayGain.value
            replayGainHelper.isScanningReplayGain.collect { scanning ->
                if (lastScanState && !scanning) {
                    // Transitioned from scanning → not scanning: reload metadata
                    val pending = replayGainHelper.pendingReplayGainInfo.value
                    if (pending != null) {
                        val currentState = _uiState.value as? MetadataEditorUiState.Success
                        if (currentState != null) {
                            _uiState.update {
                                currentState.copy(
                                    audioFile = currentState.audioFile.copy(replayGainInfo = pending)
                                )
                            }
                        }
                    }
                }
                lastScanState = scanning
            }
        }

        // Setup debounced text field updates - moved from Composable to avoid recomposition issues
        setupDebouncedTextField(MetadataField.TITLE, _titleTextFlow)
        setupDebouncedTextField(MetadataField.ARTIST, _artistTextFlow)
        setupDebouncedTextField(MetadataField.ALBUM, _albumTextFlow)
        setupDebouncedTextField(MetadataField.ALBUM_ARTIST, _albumArtistTextFlow)
        setupDebouncedTextField(MetadataField.YEAR, _yearTextFlow)
        setupDebouncedTextField(MetadataField.GENRE, _genreTextFlow)
        setupDebouncedTextField(MetadataField.COMPOSER, _composerTextFlow)
        setupDebouncedTextField(MetadataField.LYRICIST, _lyricistTextFlow)
        setupDebouncedTextField(MetadataField.COMMENT, _commentTextFlow)
        setupDebouncedTextField(MetadataField.LYRICS, _lyricsTextFlow)
    }

    /**
     * Sets up debounced collection for a text field.
     * The flow is debounced 300ms to reduce metadata processing on rapid keystrokes.
     */
        @OptIn(FlowPreview::class)
    private fun setupDebouncedTextField(field: MetadataField, flow: MutableStateFlow<String?>) {
        val job = viewModelScope.launch {
            flow
                .debounce(300L)
                .collect { value ->
                    value?.let { updateMetadataField(field, it) }
                }
        }
        debounceJobs.add(job)
    }

    /**
     * 当 ViewModel 被销毁时清理该文件的搜索种子。
     */
    override fun onCleared() {
        super.onCleared()
        searchSeedHolder.removeSeedForFile(filePath)
        debounceJobs.forEach { it.cancel() }
        debounceJobs.clear()
        // Cancel any in-flight ReplayGain scan and dispose the helper's scope.
        // Without this, the helper's internal CoroutineScope (which is independent
        // of viewModelScope) would keep running after the ViewModel is destroyed,
        // updating StateFlows that no one observes — and a subsequent open of the
        // editor would create a fresh helper that never sees the scan result.
        replayGainHelper.dispose()
    }

    /**
     * Updates the debounced text field for the given metadata field.
     * This is called from the Composable to update the ViewModel's debouncing flows.
     */
    fun updateDebouncedTextField(field: MetadataField, value: String?) {
        when (field) {
            MetadataField.TITLE -> _titleTextFlow.update { value }
            MetadataField.ARTIST -> _artistTextFlow.update { value }
            MetadataField.ALBUM -> _albumTextFlow.update { value }
            MetadataField.ALBUM_ARTIST -> _albumArtistTextFlow.update { value }
            MetadataField.YEAR -> _yearTextFlow.update { value }
            MetadataField.GENRE -> _genreTextFlow.update { value }
            MetadataField.COMPOSER -> _composerTextFlow.update { value }
            MetadataField.LYRICIST -> _lyricistTextFlow.update { value }
            MetadataField.COMMENT -> _commentTextFlow.update { value }
            MetadataField.LYRICS -> _lyricsTextFlow.update { value }
            MetadataField.CONDUCTOR -> updateMetadataField(field, value ?: "")
            MetadataField.ALBUM_ART -> { /* handled by updateAlbumArt */ }
        }
    }

/**
     * Loads the audio file and its metadata.
     * Uses getAudioFile() to get complete audio info (bitrate, sampleRate, channels, duration)
     * from TagLib + MediaStore. Cover art is loaded asynchronously via loadCoverArtAsync().
     */
    private fun loadAudioFile() {
        Timber.tag("Voxly").i("MetadataEditorViewModel: action=load filePath=$filePath")
        viewModelScope.launch {
            _uiState.update { MetadataEditorUiState.Loading }
            _isM3eColorsResolved.update { false }
            _m3eColors.update { null }

            try {
                val audioFileResult = audioRepository.getAudioFile(filePath)

                audioFileResult.fold(
                    onSuccess = { audioFile ->
                        val metadata = audioFile.metadata
                        _editedMetadata.update { metadata }
                        _originalMetadata = metadata

                        // 初始化搜索种子，供 Online Search 屏幕使用
                        searchSeedHolder.updateSeed(
                            filePath = filePath,
                            title = metadata.title.orEmpty(),
                            artist = metadata.artist,
                            album = metadata.album
                        )

                        _uiState.update {
                            MetadataEditorUiState.Success(
                                audioFile = audioFile,
                                editedMetadata = metadata
                            )
                        }

                        // Load cover art asynchronously — don't block UI
                        loadCoverArtAsync(filePath)

                        // Load ReplayGain asynchronously — don't block UI
                        replayGainHelper.readReplayGain(filePath)
                        
                        // 检查并应用待处理的在线元数据
                        tryApplyPendingOnlineMetadata()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            MetadataEditorUiState.Error(
                                error.message ?: "Failed to load audio file"
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load audio file")
                _uiState.update {
                    MetadataEditorUiState.Error(e.message ?: "Failed to load audio file")
                }
            }
        }
    }

    /**
     * Loads cover art bytes asynchronously and updates the edited metadata.
     * This runs in parallel with UI rendering for fast page load.
     * Uses getLocalCoverBytes which checks byte cache first, then extracts from file.
     */
    private fun loadCoverArtAsync(filePath: String) {
        viewModelScope.launch {
            val coverBytes = com.voxly.presentation.ui.getLocalCoverBytes(filePath)
            coverBytes?.let { bytes ->
                val currentMetadata = _editedMetadata.value ?: return@let
                if (currentMetadata.albumArt == null) {
                    _editedMetadata.update { currentMetadata.copy(albumArt = bytes) }
                    val currentState = _uiState.value
                    if (currentState is MetadataEditorUiState.Success) {
                        _uiState.update {
                            currentState.copy(
                                editedMetadata = currentMetadata.copy(albumArt = bytes)
                            )
                        }
                    }
                }

                // Extract M3E color scheme using BitmapFactory with software bitmap config
                // inPreferredConfig = ARGB_8888 ensures software bitmap for Palette pixel access
                val colors = ColorExtractor.extractM3EColorsFromBytes(bytes, 200)
                _m3eColors.update { colors }
            }
            _isM3eColorsResolved.update { true }
        }
    }

    /**
     * Gets the original field value from the saved metadata for comparison.
     */
    private fun getOriginalFieldValue(field: MetadataField): String? {
        val original = _originalMetadata ?: return null
        return when (field) {
            MetadataField.TITLE -> original.title
            MetadataField.ARTIST -> original.artist
            MetadataField.ALBUM -> original.album
            MetadataField.ALBUM_ARTIST -> original.albumArtist
            MetadataField.YEAR -> original.year
            MetadataField.GENRE -> original.genre
            MetadataField.COMPOSER -> original.composer
            MetadataField.LYRICIST -> original.lyricist
            MetadataField.CONDUCTOR -> original.conductor
            MetadataField.COMMENT -> original.comment
            MetadataField.LYRICS -> original.lyrics
            MetadataField.ALBUM_ART -> null
        }
    }

    /**
     * Updates a specific metadata field.
     * Only marks field as modified if the new value actually differs from original.
     * @param field The metadata field to update
     * @param value The new value
     */
    fun updateMetadataField(field: MetadataField, value: String) {
        val currentMetadata = _editedMetadata.value ?: return
        Timber.d(
            "Metadata field update file=$filePath field=$field valueLength=${value.length}",
            "MetadataEditor"
        )
        val originalValue = getOriginalFieldValue(field) ?: ""
        val isActuallyModified = value != originalValue
        val nonBlankValue = value.takeIf { it.isNotBlank() }
        val updatedMetadata = when (field) {
            MetadataField.TITLE -> currentMetadata.copy(title = nonBlankValue)
            MetadataField.ARTIST -> currentMetadata.copy(artist = nonBlankValue)
            MetadataField.ALBUM -> currentMetadata.copy(album = nonBlankValue)
            MetadataField.ALBUM_ARTIST -> currentMetadata.copy(albumArtist = nonBlankValue)
            MetadataField.YEAR -> currentMetadata.copy(year = nonBlankValue)
            MetadataField.GENRE -> currentMetadata.copy(genre = nonBlankValue)
            MetadataField.COMPOSER -> currentMetadata.copy(composer = nonBlankValue)
            MetadataField.LYRICIST -> currentMetadata.copy(lyricist = nonBlankValue)
            MetadataField.CONDUCTOR -> currentMetadata.copy(conductor = nonBlankValue)
            MetadataField.COMMENT -> currentMetadata.copy(comment = nonBlankValue)
            MetadataField.LYRICS -> currentMetadata.copy(lyrics = value)
            MetadataField.ALBUM_ART -> {
                // ALBUM_ART is handled by updateAlbumArt() which takes ByteArray, not String
                Timber.w("updateMetadataField called for ALBUM_ART with String value, ignoring. Use updateAlbumArt() instead.", "MetadataEditor")
                currentMetadata
            }
        }

        setEditedMetadata(updatedMetadata, if (isActuallyModified) field else null)
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

        setEditedMetadata(updatedMetadata)
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

        setEditedMetadata(updatedMetadata)
    }

    /**
     * Updates the album art.
     * @param albumArtBytes The new album art bytes
     */
    fun updateAlbumArt(albumArtBytes: ByteArray?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)
        setEditedMetadata(updatedMetadata)

        viewModelScope.launch(Dispatchers.Default) {
            val colors = albumArtBytes?.let { ColorExtractor.extractM3EColorsFromBytes(it, 200) }
            _m3eColors.update { colors }
            _isM3eColorsResolved.update { true }
        }
    }

    fun rotateAlbumArt(degrees: Float) {
        val bytes = _editedMetadata.value?.albumArt ?: return
        viewModelScope.launch(Dispatchers.Default) {
            rotateJpegBytes(bytes, degrees)?.let { updateAlbumArt(it) }
        }
    }

    private fun setEditedMetadata(updatedMetadata: AudioMetadata, modifiedField: MetadataField? = null) {
        Timber.d("setEditedMetadata: field=$modifiedField, title=${updatedMetadata.title}, artist=${updatedMetadata.artist}", "MetadataEditor")
        val actuallyChanged = _editedMetadata.value != updatedMetadata
        _editedMetadata.update { updatedMetadata }
        if (actuallyChanged) {
            _hasUnsavedChanges.update { true }
            if (modifiedField != null) {
                _modifiedFields.update { it + modifiedField }
            }

            // 同步更新搜索种子，供 Online Search 屏幕使用编辑中的实时值
            searchSeedHolder.updateSeed(
                filePath = filePath,
                title = updatedMetadata.title.orEmpty(),
                artist = updatedMetadata.artist,
                album = updatedMetadata.album
            )

            val currentState = _uiState.value
            if (currentState is MetadataEditorUiState.Success) {
                Timber.d("setEditedMetadata: updating uiState with new metadata", "MetadataEditor")
                _uiState.update { currentState.copy(editedMetadata = updatedMetadata) }
            }
        }
    }

    /**
     * Updates the pending ReplayGain info.
     * This should be called when ReplayGain scanning completes.
     * @param replayGainInfo The new ReplayGain info to save
     */
    fun updateReplayGainInfo(replayGainInfo: ReplayGainInfo) {
        replayGainHelper.updateReplayGainInfo(replayGainInfo)
        _hasUnsavedChanges.update { true }
    }

    /**
     * Clears the pending ReplayGain info.
     */
    fun clearReplayGainInfo() {
        replayGainHelper.clearReplayGainInfo()
        _hasUnsavedChanges.update { true }
    }

    /**
     * Clears the ReplayGain scan error.
     */
    fun clearReplayGainScanError() {
        replayGainHelper.clearReplayGainScanError()
    }

    /**
     * Scans the current file for ReplayGain using EBU R128.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz)
     * will be automatically downsampled for optimal performance.
     */
    fun scanReplayGain() {
        replayGainHelper.scanReplayGain(filePath)
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
            Timber.e("Failed to find album files: ${e.message}", e, "MetadataEditor")
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
    

    /**
     * Saves the edited metadata and ReplayGain to the file.
     * Delegates to MetadataSaveCoordinator for persistence work.
     */
    fun saveMetadata() {
        Timber.tag("Voxly").i("MetadataEditorViewModel: action=save filePath=$filePath")
        val baseMetadata = _editedMetadata.value ?: return
        val replayGainToSave = replayGainHelper.pendingReplayGainInfo.value

        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val currentSuccessState = _uiState.value as? MetadataEditorUiState.Success

            _uiState.update { MetadataEditorUiState.Saving }

            val result = metadataSaveCoordinator.save(
                filePath = filePath,
                baseMetadata = baseMetadata,
                originalMetadata = _originalMetadata,
                pendingReplayGainInfo = replayGainToSave,
                currentSuccessAudioFile = currentSuccessState?.audioFile,
            )

            when (result) {
                is MetadataSaveCoordinatorResult.Success -> {
                    _hasUnsavedChanges.update { false }
                    _modifiedFields.update { emptySet() }
                    _saveResult.emit("Save successful")

                    _uiState.update {
                        currentSuccessState?.copy(
                            editedMetadata = result.metadataToSave,
                            audioFile = currentSuccessState.audioFile.copy(
                                metadata = result.metadataToSave,
                                replayGainInfo = result.preservedRg
                                    ?: currentSuccessState.audioFile.replayGainInfo
                            )
                        ) ?: MetadataEditorUiState.Success(
                            audioFile = AudioFile(
                                path = filePath,
                                name = "",
                                size = 0,
                                duration = 0L,
                                format = com.voxly.domain.model.AudioFormat.OTHER,
                                bitrate = 0,
                                sampleRate = 0,
                                channels = 0,
                                metadata = result.metadataToSave,
                                replayGainInfo = result.preservedRg
                            ),
                            editedMetadata = result.metadataToSave
                        )
                    }

                    Timber.i(
                        "Save metadata success file=$filePath elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        "MetadataEditor"
                    )
                }
                is MetadataSaveCoordinatorResult.RecoverableError -> {
                    _saveResult.emit(result.message)
                    _uiState.update { MetadataEditorUiState.Error(result.message) }
                }
                is MetadataSaveCoordinatorResult.Error -> {
                    val errorMessage = result.message
                    _saveResult.emit(errorMessage)
                    val currentState = _uiState.value
                    if (currentState is MetadataEditorUiState.Saving) {
                        _uiState.update {
                            MetadataEditorUiState.Error(
                                errorMessage + if (result.requiresReauthorization)
                                    "\n\n请重新选择文件以恢复写入权限。" else ""
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Resets all changes and reloads the original metadata.
     */
    fun discardChanges() {
        viewModelScope.launch {
            val metadataReadResult = audioRepository.readMetadata(filePath)
            metadataReadResult.onSuccess { originalMetadata ->
                _editedMetadata.update { originalMetadata }
                _originalMetadata = originalMetadata
                _hasUnsavedChanges.update { false }
                _modifiedFields.update { emptySet() }
                // 清除当前文件的搜索种子（放弃修改后不再使用编辑中的值）
                searchSeedHolder.removeSeedForFile(filePath)
                val currentState = _uiState.value
                if (currentState is MetadataEditorUiState.Success) {
                    _uiState.update { currentState.copy(editedMetadata = originalMetadata) }
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
                    ConvertibleField.LYRICS -> updatedMetadata.copy(lyrics = converted)
                }
            }
        }
        
        setEditedMetadata(updatedMetadata)
    }

    private companion object {
        val TRAD_TO_SIMP_TRANSLITERATOR: Transliterator = Transliterator.getInstance("Traditional-Simplified")
        val SIMP_TO_TRAD_TRANSLITERATOR: Transliterator = Transliterator.getInstance("Simplified-Traditional")
    }

    private fun toSimplifiedChinese(text: String): String {
        return TRAD_TO_SIMP_TRANSLITERATOR.transliterate(text)
    }

    private fun toTraditionalChinese(text: String): String {
        return SIMP_TO_TRAD_TRANSLITERATOR.transliterate(text)
    }

    /**
     * Clears the save result after it has been handled.
     */
    fun clearSaveResult() {
        
    }

    fun reauthorizeAndRetrySave(uri: Uri, grantType: SafGrantType) {
        viewModelScope.launch {
            val permissionResult = safWriteAccessService.persistPermission(uri, grantType)
            permissionResult.fold(
                onSuccess = {
                    Timber.i("SAF permission updated for file=$filePath grantType=$grantType", TAG)
                    saveMetadata()
                },
                onFailure = { error ->
                    val message = error.message
                        ?: "Failed to persist SAF permission. Please retry selecting the file or directory."
                    Timber.e("SAF reauthorization failed file=$filePath reason=$message", error, TAG)
                    _saveResult.emit(message)
                    _uiState.update { MetadataEditorUiState.Error(message) }
                }
            )
        }
    }

    fun retrySaveAfterMediaStorePermission() {
        viewModelScope.launch {
            Timber.i("MediaStore permission granted, retrying save for file=$filePath", TAG)
            saveMetadata()
        }
    }

    fun searchOnlineCoverCandidates() {
        val metadata = _editedMetadata.value ?: return
        val title = metadata.title.orEmpty()
        val artist = metadata.artist?.takeIf { it.isNotBlank() }
        coverSearchHelper.searchOnlineCoverCandidates(title, artist)
    }

    fun applyOnlineCover(recording: OnlineRecording) {
        viewModelScope.launch {
            val bytes = coverSearchHelper.applyOnlineCover(recording)
            if (bytes != null) {
                updateAlbumArt(bytes)
            }
        }
    }

    fun clearCoverFetchMessage() {
        coverSearchHelper.clearCoverFetchMessage()
    }

    fun clearOnlineCoverResults() {
        coverSearchHelper.clearOnlineCoverResults()
    }

    fun searchOnlineLyrics() {
        val metadata = _editedMetadata.value ?: return
        val track = metadata.title.orEmpty()
        val artist = metadata.artist?.takeIf { it.isNotBlank() }
        val album = metadata.album?.takeIf { it.isNotBlank() }
        lyricsSearchHelper.searchOnlineLyrics(track, artist, album)
    }

    fun applyOnlineLyrics(result: OnlineLyricsResult) {
        viewModelScope.launch {
            val text = lyricsSearchHelper.getLyricsContent(result)
            if (text != null) {
                updateMetadataField(MetadataField.LYRICS, text)
            }
        }
    }

    fun clearOnlineLyricsResults() {
        lyricsSearchHelper.clearOnlineLyricsResults()
    }

    fun applyOnlineMetadata(metadata: AudioMetadata) {
        applyOnlineMetadataInternal(metadata)
    }

    /**
     * 检查并从 PendingMetadataHolder 中消费待处理的在线元数据。
     * 应在屏幕进入时调用（如 MetadataEditorScreen 的 LaunchedEffect(Unit) 中）。
     */
    fun tryApplyPendingOnlineMetadata() {
        val pending = pendingMetadataHolder.consume(filePath) ?: return
        Timber.d("tryApplyPendingOnlineMetadata: applying pending metadata for $filePath", "MetadataEditor")
        applyOnlineMetadataInternal(pending)
    }

    private fun applyOnlineMetadataInternal(metadata: AudioMetadata) {
        val currentMetadata = _editedMetadata.value ?: run {
            Timber.w("applyOnlineMetadata: _editedMetadata is null, re-putting pending for later", "MetadataEditor")
            pendingMetadataHolder.put(filePath, metadata)
            return
        }

        val result = applyOnlineMetadataUseCase(currentMetadata, metadata)
        val updatedMetadata = result.metadata
        val modifiedFieldNames = result.modifiedFields
        
        Timber.d("applyOnlineMetadata: setting edited metadata, title=${updatedMetadata.title}, modifiedFields=$modifiedFieldNames", "MetadataEditor")
        
        _editedMetadata.update { updatedMetadata }
        _hasUnsavedChanges.update { true }
        if (modifiedFieldNames.isNotEmpty()) {
            val enumFields = modifiedFieldNames.mapNotNull { name ->
                try { MetadataField.valueOf(name) } catch (_: Exception) { null }
            }.toSet()
            _modifiedFields.update { it + enumFields }
        }

        // 同步更新搜索种子，供 Online Search 屏幕使用编辑中的实时值
        searchSeedHolder.updateSeed(
            filePath = filePath,
            title = updatedMetadata.title.orEmpty(),
            artist = updatedMetadata.artist,
            album = updatedMetadata.album
        )

        // 更新 uiState
        val currentUiState = _uiState.value
        if (currentUiState is MetadataEditorUiState.Success) {
            Timber.d("applyOnlineMetadata: updating uiState with new metadata", "MetadataEditor")
            _uiState.update { currentUiState.copy(editedMetadata = updatedMetadata) }
        }
    }

    /**
     * Toggles lyrics timestamp format between [mm:ss.xxx] and [mm:ss.xx]
     */
    fun toggleLyricsTimestampFormat() {
        val currentMetadata = _editedMetadata.value ?: return
        val currentLyrics = currentMetadata.lyrics ?: return
        
        viewModelScope.launch {
            val hasThreeDigit = currentLyrics.contains(Regex("""\[\d{2}:\d{2}\.\d{3}\]"""))
            val currentFormatted = _isLyricsTimestampFormatted.value
            
            val newLyrics: String
            if (hasThreeDigit && !currentFormatted) {
                // If currently has 3-digit, convert to 2-digit
                _isLyricsTimestampFormatted.update { true }
                newLyrics = Lyrics.formatTimestamps(currentLyrics)
            } else {
                // If currently has 2-digit (manually formatted), we can't easily convert back
                // So just toggle the flag
                _isLyricsTimestampFormatted.update { !currentFormatted }
                newLyrics = currentLyrics
            }

            val updatedMetadata = currentMetadata.copy(lyrics = newLyrics)
            setEditedMetadata(updatedMetadata)

            // Track that lyrics field was modified
            _modifiedFields.update { it + MetadataField.LYRICS }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: MetadataEditor): MetadataEditorViewModel
    }
}
