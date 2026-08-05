package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.util.OnlineSearchSorter
import com.voxly.presentation.navigation.OnlineCoverSearch
import com.voxly.presentation.viewmodel.SearchSeedHolder
import com.voxly.presentation.ui.getCoverArtBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.voxly.domain.repository.OnlineSourceResult
import timber.log.Timber
import java.io.File

/**
 * 流式搜索进度状态
 */
data class CoverSearchProgressState(
    val isSearching: Boolean = false,
    val results: List<OnlineRecording> = emptyList(),
    val startedSources: Set<OnlineSource> = emptySet(),
    val completedSources: Set<OnlineSource> = emptySet(),
    val errorSources: Map<OnlineSource, String> = emptyMap(),
    val hasAnyResults: Boolean = false
)

/**
 * ViewModel for online cover search screen.
 */
@HiltViewModel(assistedFactory = OnlineCoverSearchViewModel.Factory::class)
class OnlineCoverSearchViewModel @AssistedInject constructor(
    @Assisted val navKey: OnlineCoverSearch,
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    private val searchSeedHolder: SearchSeedHolder,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val filePath: String = navKey.filePath
    private val TAG = "OnlineCoverSearchVM"

    // Search query info (exposed for UI)
    private val _searchTitle = MutableStateFlow("")
    val searchTitle: StateFlow<String> = _searchTitle.asStateFlow()

    private val _searchArtist = MutableStateFlow<String?>(null)
    val searchArtist: StateFlow<String?> = _searchArtist.asStateFlow()

    // 搜索进度状态（新）
    private val _searchProgressState = MutableStateFlow(CoverSearchProgressState())
    val searchProgressState: StateFlow<CoverSearchProgressState> = _searchProgressState.asStateFlow()

    // 保留其他必要的状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(replay = 0)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _coverFetchMessage = MutableSharedFlow<String>(replay = 0)
    val coverFetchMessage: SharedFlow<String> = _coverFetchMessage.asSharedFlow()

    // 兼容旧 UI - 从新的 progress state 派生
    val coverResults: StateFlow<List<OnlineRecording>> = 
        _searchProgressState.map { it.results }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Search for cover art using the audio file's metadata.
     */
    fun search(path: String) {
        val targetPath = path.ifBlank { filePath }
        Timber.tag("Voxly").i("CoverSearch started: path=$targetPath")
        Timber.d(TAG, "search() called, path=$targetPath")

        viewModelScope.launch {
            _isLoading.update { true }
            _searchProgressState.update { CoverSearchProgressState() }

            // 优先从 SearchSeedHolder 获取实时编辑值
            val seed = searchSeedHolder.peekSeed(targetPath)
            Timber.d(TAG, "search() seed=$seed")

            val title: String
            val artist: String?

            if (seed != null) {
                // 使用编辑中的实时值，进行清理
                val sanitizedTitle = sanitizeSearchTerm(seed.title)
                val sanitizedArtist = sanitizeSearchTerm(seed.artist)
                title = sanitizedTitle.orEmpty()
                artist = sanitizedArtist
                _searchTitle.update { title }
                _searchArtist.update { artist }
                Timber.d(TAG, "search() using seed - title='$title', artist='$artist'")
                performCoverSearch(title, artist)
            } else {
                // 兜底：从文件读取
                Timber.d(TAG, "search() no seed, loading from file")
                val result = audioRepository.getAudioFile(targetPath)
                result.fold(
                    onSuccess = { audioFile ->
                        val metadata = audioFile.metadata
                        val rawTitle = metadata.title.orEmpty()
                        val rawArtist = metadata.artist?.takeIf { it.isNotBlank() }
                        title = sanitizeSearchTerm(rawTitle) ?: rawTitle
                        artist = sanitizeSearchTerm(rawArtist) ?: rawArtist

                        _searchTitle.update { title }
                        _searchArtist.update { artist }
                        Timber.d(TAG, "search() loaded from file - title='$title', artist='$artist'")

                        performCoverSearch(title, artist)
                    },
                    onFailure = { error ->
                        Timber.e(TAG, "search() failed to load audio file: ${error.message}")
                        _errorMessage.emit("Failed to load audio file: ${error.message}")
                        _isLoading.update { false }
                    }
                )
            }
        }
    }

    companion object {
        private const val IMMEDIATE_DISPLAY_COUNT = 5  // 前5个结果立即显示，不排序
        private const val BATCH_UPDATE_INTERVAL_MS = 200L  // 批量更新间隔 200ms
    }

    private fun performCoverSearch(title: String, artist: String?) {
        Timber.d(TAG, "performCoverSearch() title='$title', artist='$artist'")
        
        // 重置状态
        _searchProgressState.update { CoverSearchProgressState(isSearching = true) }
        _isLoading.update { true }
        
        viewModelScope.launch {
            try {
                // 获取封面搜索的数据源优先级
                val sourceConfigs = settingsDataStore.sourceConfigurations.first()
                val coverPriority = sourceConfigs.cover.sources
                    .sortedBy { it.order }
                    .map { it.sourceId }
                
                Timber.d(TAG, "performCoverSearch() starting streaming search with priority: $coverPriority")
                
                // 缓冲列表用于批量处理
                val pendingRecordings = mutableListOf<OnlineRecording>()
                var lastUpdateTime = System.currentTimeMillis()
                var totalReceivedCount = 0
                
                aggregatedOnlineMetadataRepository.searchByTrackForCoverFlow(title, artist)
                    .collect { result ->
                        when (result) {
                            is OnlineSourceResult.RecordingResult -> {
                                Timber.d(TAG, "Received result from ${result.source}: ${result.recording.title}")
                                totalReceivedCount++

                                // 策略：前5个结果立即增量显示（不排序），后续结果批量排序
                                if (totalReceivedCount <= IMMEDIATE_DISPLAY_COUNT) {
                                    // 立即显示，不排序
                                    _searchProgressState.update { state ->
                                        val mergedResults = mergeRecordingIntoList(state.results, result.recording)
                                        state.copy(
                                            results = mergedResults,
                                            hasAnyResults = true,
                                            startedSources = state.startedSources + result.source
                                        )
                                    }
                                } else {
                                    // 加入缓冲列表，批量处理
                                    pendingRecordings.add(result.recording)
                                    _searchProgressState.update { state ->
                                        state.copy(startedSources = state.startedSources + result.source)
                                    }
                                    
                                    // 检查是否需要批量更新（每200ms或缓冲满10个）
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastUpdateTime >= BATCH_UPDATE_INTERVAL_MS || pendingRecordings.size >= 10) {
                                        processPendingRecordings(pendingRecordings, title, artist, coverPriority)
                                        pendingRecordings.clear()
                                        lastUpdateTime = currentTime
                                    }
                                }
                            }
                            
                            is OnlineSourceResult.SourceCompleted -> {
                                Timber.d(TAG, "Source completed: ${result.source}")
                                // 立即处理剩余缓冲
                                if (pendingRecordings.isNotEmpty()) {
                                    processPendingRecordings(pendingRecordings, title, artist, coverPriority)
                                    pendingRecordings.clear()
                                }
                                _searchProgressState.update { state ->
                                    state.copy(
                                        completedSources = state.completedSources + result.source,
                                        startedSources = state.startedSources + result.source
                                    )
                                }
                            }
                            
                            is OnlineSourceResult.Error -> {
                                Timber.w(TAG, "Source error ${result.source}: ${result.message}")
                                _searchProgressState.update { state ->
                                    state.copy(
                                        errorSources = state.errorSources + (result.source to result.message),
                                        startedSources = state.startedSources + result.source
                                    )
                                }
                            }
                            
                            else -> {
                                // 其他结果类型忽略
                            }
                        }
                    }
                
                // 处理最后剩余的缓冲结果
                if (pendingRecordings.isNotEmpty()) {
                    processPendingRecordings(pendingRecordings, title, artist, coverPriority)
                }
                
                Timber.d(TAG, "performCoverSearch() streaming completed, total results: $totalReceivedCount")
                
            } catch (e: Exception) {
                Timber.e(TAG, "performCoverSearch() error: ${e.message}")
                if (e !is CancellationException) {
                    _errorMessage.emit(e.message ?: "Search failed")
                }
            } finally {
                _searchProgressState.update { it.copy(isSearching = false) }
                _isLoading.update { false }
            }
        }
    }
    
    /**
     * 批量处理缓冲的结果并排序
     */
    private suspend fun processPendingRecordings(
        pendingRecordings: List<OnlineRecording>,
        title: String,
        artist: String?,
        coverPriority: List<String>
    ) {
        _searchProgressState.update { state ->
            // 合并所有缓冲的结果
            var mergedResults = state.results
            pendingRecordings.forEach { recording ->
                mergedResults = mergeRecordingIntoList(mergedResults, recording)
            }
            
            // 批量排序（只针对超过5个的部分进行完整排序）
            val sortedResults = if (mergedResults.size > IMMEDIATE_DISPLAY_COUNT) {
                OnlineSearchSorter.sortRecordings(
                    recordings = mergedResults,
                    title = title,
                    artist = artist,
                    sourcePriority = coverPriority
                )
            } else {
                mergedResults
            }
            
            state.copy(
                results = sortedResults,
                hasAnyResults = sortedResults.isNotEmpty()
            )
        }
    }
    
    /**
     * 将新 recording 合并到列表中，避免重复
     */
    private fun mergeRecordingIntoList(
        existing: List<OnlineRecording>,
        newRecording: OnlineRecording
    ): List<OnlineRecording> {
        // 检查是否已存在相同 ID 的 recording
        val existingIndex = existing.indexOfFirst { it.id == newRecording.id }
        return if (existingIndex >= 0) {
            // 替换已有的（保留更多信息）
            existing.toMutableList().apply {
                this[existingIndex] = mergeRecordings(existing[existingIndex], newRecording)
            }
        } else {
            // 追加新的
            existing + newRecording
        }
    }
    
    /**
     * 合并两个 recording，保留非空字段
     */
    private fun mergeRecordings(old: OnlineRecording, new: OnlineRecording): OnlineRecording {
        return old.copy(
            title = old.title.takeIf { it.isNotBlank() } ?: new.title,
            artist = old.artist.takeIf { it.isNotBlank() } ?: new.artist,
            coverArtUrl = old.coverArtUrl ?: new.coverArtUrl,
            source = if (old.source == OnlineSource.UNKNOWN) new.source else old.source
        )
    }

    /**
     * Apply the selected cover art asynchronously.
     * Returns the cover art bytes or null if not available.
     */
    suspend fun applyCover(recording: OnlineRecording): ByteArray? {
        // First try to get from existing cover URL
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            return try {
                getCoverArtBytes(existingCoverUrl)
            } catch (e: Exception) {
                _coverFetchMessage.emit("Failed to load cover: ${e.message}")
                null
            }
        }

        val releaseId = recording.releaseId
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.emit("无法获取封面：该结果没有关联的专辑信息")
            return null
        }

        val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
        return try {
            val targetSource = when (recording.source) {
                OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
            }
            aggregatedOnlineMetadataRepository.preferredSource = targetSource

            val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
            coverResult.fold(
                onSuccess = { cover ->
                    if (cover != null) {
                        _coverFetchMessage.emit("Cover fetched successfully")
                    } else {
                        _coverFetchMessage.emit("No online cover found")
                    }
                    cover
                },
                onFailure = { error ->
                    _coverFetchMessage.emit(error.message ?: "Cover fetch failed")
                    null
                }
            )
        } finally {
            aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
        }
    }

    /**
     * Get cover art bytes for a recording synchronously (for immediate use).
     */
    suspend fun getCoverBytes(recording: OnlineRecording): ByteArray? {
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            // Use cache-first approach
            return getCoverArtBytes(existingCoverUrl)
        }

        val releaseId = recording.releaseId
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.emit("无法获取封面：该结果没有关联的专辑信息")
            return null
        }

        var resultBytes: ByteArray? = null

        val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
        try {
            val targetSource = when (recording.source) {
                OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
            }
            aggregatedOnlineMetadataRepository.preferredSource = targetSource

            val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
            coverResult.fold(
                onSuccess = { cover ->
                    resultBytes = cover
                    if (cover != null) {
                        _coverFetchMessage.emit("Cover fetched successfully")
                    } else {
                        _coverFetchMessage.emit("No online cover found")
                    }
                },
                onFailure = {
                    _coverFetchMessage.emit(it.message ?: "Cover fetch failed")
                }
            )
        } finally {
            aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
        }

        return resultBytes
    }

    private fun sanitizeSearchTerm(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val cleaned = value
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("^\\s*\\d{1,3}[\\s._-]+"), "")
            .replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeIf { it.length >= 2 }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: OnlineCoverSearch): OnlineCoverSearchViewModel
    }
}
