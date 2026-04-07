package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Activity 级别作用域的搜索种子持有者。
 *
 * 为每个文件路径维护独立的搜索种子，避免不同文件间的种子混淆。
 * 当文件不再需要时（ViewModel被清除），应调用 removeSeedForFile 清理对应的种子。
 */
@ActivityRetainedScoped
class SearchSeedHolder @Inject constructor() : ViewModel() {

    private val _seedsByFile = MutableStateFlow<Map<String, SearchSeed>>(emptyMap())
    
    /**
     * 更新指定文件的搜索种子。
     * @param filePath 文件路径
     * @param title 标题
     * @param artist 艺术家
     * @param album 专辑
     */
    fun updateSeed(filePath: String, title: String, artist: String?, album: String?) {
        _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
            put(filePath, SearchSeed(filePath, title, artist, album))
        }
    }

    /**
     * 获取指定文件的搜索种子（不清除）。
     * @param filePath 文件路径
     * @return 该文件的搜索种子，如果不存在则返回 null
     */
    fun peekSeed(filePath: String): SearchSeed? {
        return _seedsByFile.value[filePath]
    }

    /**
     * 获取并清除指定文件的搜索种子。
     * @param filePath 文件路径
     * @return 该文件的搜索种子，如果不存在则返回 null
     */
    fun getAndClearSeed(filePath: String): SearchSeed? {
        val seed = _seedsByFile.value[filePath]
        if (seed != null) {
            _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
                remove(filePath)
            }
        }
        return seed
    }

    /**
     * 移除指定文件的种子（当 ViewModel 被清除时调用）。
     * @param filePath 文件路径
     */
    fun removeSeedForFile(filePath: String) {
        _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
            remove(filePath)
        }
    }

    /**
     * 清除所有种子。
     */
    fun clearAllSeeds() {
        _seedsByFile.value = emptyMap()
    }
}