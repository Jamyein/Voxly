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
 * 由 MetadataEditorViewModel 在字段编辑时写入（updateSeed），
 * 由 Online Search ViewModel 在启动搜索时读取（getAndClearSeed）。
 * 使用 getAndClearSeed 会在读取后清除种子，避免污染下次搜索。
 */
@ActivityRetainedScoped
class SearchSeedHolder @Inject constructor() : ViewModel() {

    private val _editedSearchSeed = MutableStateFlow<SearchSeed?>(null)
    val editedSearchSeed: StateFlow<SearchSeed?> = _editedSearchSeed.asStateFlow()

    /**
     * 更新搜索种子（由 MetadataEditorViewModel 调用）
     * @param filePath 文件路径，用于标识种子所属文件
     */
    fun updateSeed(filePath: String, title: String, artist: String?, album: String?) {
        _editedSearchSeed.value = SearchSeed(filePath, title, artist, album)
    }

    /**
     * 读取指定文件的搜索种子（不清除）
     * @param filePath 文件路径
     * @return 如果种子存在且匹配该文件路径则返回种子，否则返回 null
     */
    fun peekSeed(filePath: String): SearchSeed? {
        val seed = _editedSearchSeed.value
        return if (seed != null && seed.filePath == filePath) seed else null
    }

    /**
     * 获取并清除指定文件的搜索种子（由 Online Search ViewModel 调用）
     * 读取后自动清除，避免下次进入时残留旧数据。
     * @param filePath 文件路径
     * @return 如果种子存在且匹配该文件路径则返回种子，否则返回 null
     */
    fun getAndClearSeed(filePath: String): SearchSeed? {
        val seed = _editedSearchSeed.value
        return if (seed != null && seed.filePath == filePath) {
            _editedSearchSeed.value = null
            seed
        } else {
            null
        }
    }

    /**
     * 清除搜索种子（由 MetadataEditorViewModel 在保存/放弃时调用）
     */
    fun clearSeed() {
        _editedSearchSeed.value = null
    }
}
