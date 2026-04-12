package com.voxly.data.remote

/**
 * 统一搜索查询构建器
 * 负责将 title 和 artist 组合成统一的搜索格式
 */
object SearchQueryBuilder {
    
    /**
     * 构建搜索查询字符串
     * 只使用 title，让 API 进行模糊匹配
     * artist 作为辅助在过滤阶段使用
     *
     * @param title 歌曲标题
     * @param artist 艺术家名称 (可选，仅在过滤阶段使用)
     * @return 搜索查询字符串
     */
    fun build(title: String, artist: String?): String {
        // 只使用 title，让 API 的模糊搜索能力生效
        // 例如搜索"以后别做朋"可以返回"以后别做朋友"
        return title.trim()
    }
    
    /**
     * 构建搜索查询字符串 (仅标题)
     * 当没有艺术家信息时使用
     */
    fun buildTitleOnly(title: String): String {
        return title.trim()
    }
}
