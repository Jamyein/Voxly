package com.voxly.data.remote

/**
 * 统一搜索查询构建器
 * 负责将 title 和 artist 组合成统一的搜索格式
 */
object SearchQueryBuilder {
    
    /**
     * 构建搜索查询字符串: "title artist"。
     *
     * artist 必须进查询串, 否则对通用/短标题(如"青花瓷")各源会把翻唱、
     * 无关版本排到原曲前面。API 本身具备模糊匹配, 加上 artist 后既能
     * 召回原曲(如"青花瓷 周杰伦"), 又能继续匹配近似标题。
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
