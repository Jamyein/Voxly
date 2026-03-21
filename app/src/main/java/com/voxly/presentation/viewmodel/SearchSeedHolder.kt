package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.ActivityRetainedScoped
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
     */
    fun updateSeed(title: String, artist: String?, album: String?) {
        _editedSearchSeed.value = SearchSeed(title, artist, album)
    }

    /**
     * 获取并清除搜索种子（由 Online Search ViewModel 调用）
     * 读取后自动清除，避免下次进入时残留旧数据。
     */
    fun getAndClearSeed(): SearchSeed? {
        val seed = _editedSearchSeed.value
        _editedSearchSeed.value = null
        return seed
    }

    /**
     * 清除搜索种子（由 MetadataEditorViewModel 在保存/放弃时调用）
     */
    fun clearSeed() {
        _editedSearchSeed.value = null
    }
}
