package com.voxly.domain.util

import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource

/**
 * 统一的在线搜索结果排序工具
 * 
 * 排序策略（相关性绝对主导）：
 * 1. 标题匹配权重 2x，艺术家匹配权重 1x
 * 2. 相关性分数放大 100 倍作为基础分
 * 3. 源优先级仅在难分胜负时生效（每级差 2 分）
 * 4. 未知源大幅降权（-500），确保垫底
 */
object OnlineSearchSorter {
    
    private const val RELEVANCE_SCALE = 100        // 相关性放大系数
    private const val PRIORITY_BONUS_STEP = 2      // 每级优先级差 2 分（仅打破平局）
    private const val UNKNOWN_SOURCE_PENALTY = -500 // 未知源大幅降权
    
    /**
     * 排序 OnlineRecording 列表（封面搜索）
     */
    fun sortRecordings(
        recordings: List<OnlineRecording>,
        title: String,
        artist: String?,
        sourcePriority: List<String>
    ): List<OnlineRecording> {
        return sortGenericItems(
            items = recordings,
            title = title,
            artist = artist,
            sourcePriority = sourcePriority,
            getTitle = { it.title },
            getArtist = { it.artist },
            getSource = { it.source }
        )
    }
    
    /**
     * 排序 OnlineRelease 列表（元数据搜索）
     */
    fun sortReleases(
        releases: List<OnlineRelease>,
        title: String,
        artist: String?,
        sourcePriority: List<String>
    ): List<OnlineRelease> {
        return sortGenericItems(
            items = releases,
            title = title,
            artist = artist,
            sourcePriority = sourcePriority,
            getTitle = { (it.songTitle ?: it.albumTitle ?: it.title).trim() },
            getArtist = { it.artist },
            getSource = { it.source }
        )
    }
    
    /**
     * 通用排序逻辑，被 sortRecordings 和 sortReleases 复用
     * 
     * 排序原则：相关性绝对主导，优先级仅在同一匹配度层级内生效
     */
    private inline fun <T> sortGenericItems(
        items: List<T>,
        title: String,
        artist: String?,
        sourcePriority: List<String>,
        crossinline getTitle: (T) -> String,
        crossinline getArtist: (T) -> String,
        crossinline getSource: (T) -> OnlineSource
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        
        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        
        return items.sortedByDescending { item ->
            val relevanceScore = calculateRelevanceScore(
                title = getTitle(item),
                artist = getArtist(item),
                titleNeedle = titleNeedle,
                artistNeedle = artistNeedle
            )
            val sourcePriorityIndex = getSourcePriorityIndex(getSource(item), sourcePriority)
            
            when {
                sourcePriorityIndex == -1 -> relevanceScore * RELEVANCE_SCALE + UNKNOWN_SOURCE_PENALTY
                else -> relevanceScore * RELEVANCE_SCALE + (maxPriority - 1 - sourcePriorityIndex) * PRIORITY_BONUS_STEP
            }
        }
    }
    
    /**
     * 排序 OnlineLyricsResult 列表（歌词搜索）
     * 
     * 与通用排序一致，额外增加同步歌词加分
     */
    fun sortLyrics(
        lyrics: List<OnlineLyricsResult>,
        title: String,
        artist: String?,
        sourcePriority: List<String>
    ): List<OnlineLyricsResult> {
        if (lyrics.isEmpty()) return emptyList()
        
        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        
        return lyrics.sortedByDescending { lyric ->
            val relevanceScore = calculateRelevanceScore(
                title = lyric.trackName,
                artist = lyric.artistName,
                titleNeedle = titleNeedle,
                artistNeedle = artistNeedle
            )
            
            val syncedBonus = if (lyric.hasSyncedLyrics) 2 else 0
            val adjustedRelevanceScore = relevanceScore + syncedBonus
            
            val sourcePriorityIndex = getLyricsSourcePriorityIndex(lyric.source, sourcePriority)
            
            when {
                sourcePriorityIndex == -1 -> adjustedRelevanceScore * RELEVANCE_SCALE + UNKNOWN_SOURCE_PENALTY
                else -> adjustedRelevanceScore * RELEVANCE_SCALE + (maxPriority - 1 - sourcePriorityIndex) * PRIORITY_BONUS_STEP
            }
        }
    }
    
    /**
     * 计算相关性分数（范围 3-11）
     * 标题匹配分数(2-6) + 歌手名匹配分数(1-3) + 同步歌词加分(0-2)
     *
     * 匹配等级：
     * - 完全匹配: 3
     * - 包含: 2
     * - 部分匹配(基于Levenshtein距离): 1.0-2.0
     * - 不匹配: 1
     */
    private fun calculateRelevanceScore(
        title: String,
        artist: String,
        titleNeedle: String,
        artistNeedle: String
    ): Int {
        val titleScore = calculateMatchScore(title, titleNeedle) * 2   // 标题权重 2x：2-6
        val artistScore = calculateMatchScore(artist, artistNeedle)     // 艺术家权重 1x：1-3
        return titleScore + artistScore                                  // 总计：3-9
    }

    /**
     * 计算单个字段的匹配分数 (1-3)
     * 
     * 精确匹配始终获得最高分，Levenshtein 部分匹配永远不会超过精确匹配
     */
    private fun calculateMatchScore(haystack: String, needle: String): Int {
        if (needle.isBlank()) return 1
        if (haystack.equals(needle, ignoreCase = true)) return 3
        if (haystack.contains(needle, ignoreCase = true)) return 2

        val similarity = levenshteinSimilarity(haystack.lowercase(), needle.lowercase())
        return when {
            similarity >= 0.7 -> 2  // 高相似度视为包含级别
            similarity >= 0.4 -> {
                // 线性映射 0.4-0.7 到 1.0-2.0，确保不超过精确匹配
                val normalized = (similarity - 0.4) / 0.3  // 0.0-1.0
                1.0 + normalized  // 1.0-2.0
            }
            else -> 1
        }.toInt()
    }

    /**
     * 计算两个字符串的 Levenshtein 相似度 (0.0-1.0)
     * 1.0 = 完全相同, 0.0 = 完全不同
     */
    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * 计算 Levenshtein 编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1

        // 使用滚动数组优化空间复杂度 O(min(n,m))
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // 删除
                    curr[j - 1] + 1,  // 插入
                    prev[j - 1] + cost // 替换
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[len2]
    }
    
    /**
     * 获取数据源在优先级列表中的索引
     */
    private fun getSourcePriorityIndex(source: OnlineSource, priority: List<String>): Int {
        return priority.indexOfFirst {
            it.equals(source.name, ignoreCase = true)
        }
    }

    /**
     * 获取歌词数据源在优先级列表中的索引
     */
    private fun getLyricsSourcePriorityIndex(source: String, priority: List<String>): Int {
        val normalizedSource = normalizeLyricsSourceName(source)
        return priority.indexOfFirst { priorityName ->
            normalizeLyricsSourceName(priorityName) == normalizedSource
        }
    }

    /**
     * 标准化歌词源名称
     */
    private val SOURCE_NAME_NORMALIZER = Regex("[\\s_-]+")
    
    private fun normalizeLyricsSourceName(source: String): String {
        return source.lowercase().replace(SOURCE_NAME_NORMALIZER, "").replace("music", "")
    }
}
