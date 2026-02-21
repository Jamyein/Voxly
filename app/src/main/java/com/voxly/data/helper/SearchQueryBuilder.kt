package com.voxly.data.helper

/**
 * 统一搜索查询构建器
 * 负责将 title 和 artist 组合成统一的搜索格式
 */
object SearchQueryBuilder {
    
    /**
     * 构建搜索查询字符串
     * 格式: "title artist" (title 在前，空格分隔)
     * 
     * @param title 歌曲标题
     * @param artist 艺术家名称 (可选)
     * @return 格式化的搜索查询字符串
     */
    fun build(title: String, artist: String?): String {
        return buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }
    }
    
    /**
     * 构建搜索查询字符串 (仅标题)
     * 当没有艺术家信息时使用
     */
    fun buildTitleOnly(title: String): String {
        return title.trim()
    }
}
