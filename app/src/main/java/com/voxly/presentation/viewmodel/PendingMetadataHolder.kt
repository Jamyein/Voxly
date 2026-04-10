package com.voxly.presentation.viewmodel

import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Activity 级别作用域的待处理元数据持有者。
 *
 * 用于在 OnlineMetadataScreen 和 MetadataEditorScreen 之间可靠传递回填的元数据，
 * 避免依赖 AnimatedContent transition 中脆弱的 rememberSaveable + LaunchedEffect 模式。
 */
@ActivityRetainedScoped
class PendingMetadataHolder @Inject constructor() {

    private val _pending = MutableStateFlow<Map<String, AudioMetadata>>(emptyMap())
    val pending: StateFlow<Map<String, AudioMetadata>> = _pending.asStateFlow()

    /**
     * 为指定文件路径设置待处理的元数据。
     * 如果该文件路径已有待处理数据，将会被覆盖。
     */
    fun put(filePath: String, metadata: AudioMetadata) {
        _pending.update { it + (filePath to metadata) }
    }

    /**
     * 消费（获取并移除）指定文件路径的待处理元数据。
     * @return 如果有待处理数据则返回，否则返回 null
     */
    fun consume(filePath: String): AudioMetadata? {
        val metadata = _pending.value[filePath]
        if (metadata != null) {
            _pending.update { it - filePath }
        }
        return metadata
    }

    /**
     * 清除所有待处理的元数据（通常不需要手动调用）。
     */
    fun clearAll() {
        _pending.value = emptyMap()
    }
}
