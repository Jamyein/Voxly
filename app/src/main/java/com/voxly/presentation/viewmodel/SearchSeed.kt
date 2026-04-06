package com.voxly.presentation.viewmodel

import androidx.compose.runtime.Immutable

/**
 * 搜索种子数据类，持有实时编辑的元数据字段。
 * 在 MetadataEditorScreen 编辑字段时写入，供 Online Search 屏幕优先读取。
 */
@Immutable
data class SearchSeed(
    val filePath: String,
    val title: String,
    val artist: String?,
    val album: String?
)
