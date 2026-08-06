package com.voxly.domain.util

import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource

/**
 * 统一的在线搜索结果排序工具
 *
 * 排序策略（字典序分层，无权重魔法数字）：
 * 排序键 = (相关性档位 tier, 数据源排名 sourceRank, 相关性细分数 relevance)
 *
 * 1. tier 绝对主导：强相关永远在弱相关之前（高相关性更靠前）
 * 2. sourceRank 同档内生效：设置页拖拽的数据源优先级（高优先级更靠前）
 * 3. relevance 同档同源内细分：相似度越高越靠前
 *
 * 等价于组合分 tier × 源数量 + sourceRank：相关性每高一档跨越全部源排名，
 * 数据源优先级只在同一相关性档位内起作用 —— 高优先级 + 高相关性 = 最靠前。
 */
object OnlineSearchSorter {

    // 相关性匹配档位：rank 越低越相关
    private enum class MatchTier(val rank: Int) {
        EXACT(0),   // 完全匹配（忽略大小写 / 括号 / 简繁）
        PREFIX(1),  // 前缀匹配
        CONTAINS(2),// 包含匹配
        FUZZY(3),   // 模糊匹配（Levenshtein 达到阈值）
        NONE(4)     // 无匹配
    }

    // 排序键（字典序）
    private data class RankKey(
        val tier: Int,          // 相关性档位（绝对主导）
        val sourceRank: Int,    // 数据源优先级排名（同档内）
        val relevance: Int      // 细分数（同档同源内）
    )

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
            album = null,
            sourcePriority = sourcePriority,
            getTitle = { it.title },
            getArtist = { it.artist },
            getAlbum = { null },
            getSource = { it.source }
        )
    }

    /**
     * 排序 OnlineRelease 列表（元数据搜索）
     *
     * @param album 专辑查询词（searchByArtistAlbum 时 title 为空、album 为主查询词）
     */
    fun sortReleases(
        releases: List<OnlineRelease>,
        title: String,
        artist: String?,
        album: String? = null,
        sourcePriority: List<String>
    ): List<OnlineRelease> {
        return sortGenericItems(
            items = releases,
            title = title,
            artist = artist,
            album = album,
            sourcePriority = sourcePriority,
            getTitle = { (it.songTitle ?: it.albumTitle ?: it.title).trim() },
            getArtist = { it.artist },
            getAlbum = { it.albumTitle ?: it.title },
            getSource = { it.source }
        )
    }

    /**
     * 排序 OnlineLyricsResult 列表（歌词搜索）
     *
     * 与通用排序一致，额外增加同步歌词加分（同档同源内细分）。
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
        val priorityIndex = sourcePriority.withIndex()
            .associate { (idx, name) -> normalizeLyricsSourceName(name) to idx }

        // Decorate-sort-undecorate：每个元素只算一次排序键
        return lyrics
            .map { lyric ->
                val titleMatch = matchLevel(lyric.trackName, titleNeedle)
                val artistMatch = matchLevel(lyric.artistName, artistNeedle)
                val sourceRank = getLyricsSourcePriorityIndex(lyric.source, priorityIndex)
                    .let { if (it < 0) maxPriority else it }
                val syncedBonus = if (lyric.hasSyncedLyrics) 1 else 0
                val relevance =
                    (lyric.trackName to titleNeedle).similarity * 3 +
                    (lyric.artistName to artistNeedle).similarity +
                    syncedBonus
                lyric to RankKey(
                    computeTier(titleMatch, artistMatch, MatchTier.NONE),
                    sourceRank,
                    (relevance * 1000).toInt()
                )
            }
            .sortedWith(rankKeyComparator())
            .map { it.first }
    }

    /**
     * 通用排序逻辑：字典序 (tier, sourceRank, relevance)
     */
    private inline fun <T> sortGenericItems(
        items: List<T>,
        title: String,
        artist: String?,
        album: String?,
        sourcePriority: List<String>,
        crossinline getTitle: (T) -> String,
        crossinline getArtist: (T) -> String,
        crossinline getAlbum: (T) -> String?,
        crossinline getSource: (T) -> OnlineSource
    ): List<T> {
        if (items.isEmpty()) return emptyList()

        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val albumNeedle = album?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        val priorityIndex = sourcePriority.withIndex()
            .associate { (idx, name) -> name.lowercase() to idx }

        // Decorate-sort-undecorate：每个元素只计算一次排序键（Levenshtein 不重算），保持稳定排序。
        return items
            .map { item ->
                val itemTitle = getTitle(item)
                val itemArtist = getArtist(item)
                val itemAlbum = getAlbum(item)

                val titleMatch = matchLevel(itemTitle, titleNeedle)
                val artistMatch = matchLevel(itemArtist, artistNeedle)
                val albumMatch = if (itemAlbum != null) matchLevel(itemAlbum, albumNeedle) else MatchTier.NONE

                val sourceRank = getSourcePriorityIndex(getSource(item), priorityIndex)
                    .let { if (it < 0) maxPriority else it }
                val relevance =
                    (itemTitle to titleNeedle).similarity * 3 +
                    (itemAlbum?.let { it to albumNeedle }?.similarity ?: 0.0) * 2 +
                    (itemArtist to artistNeedle).similarity

                item to RankKey(
                    tier = computeTier(titleMatch, artistMatch, albumMatch),
                    sourceRank = sourceRank,
                    relevance = (relevance * 1000).toInt()
                )
            }
            .sortedWith(rankKeyComparator())
            .map { it.first }
    }

    /**
     * 相关性档位（绝对主导）：
     * tier 0 标题完全匹配
     * tier 1 标题前缀匹配，或专辑完全匹配
     * tier 2 标题包含匹配，或专辑包含匹配，或歌手完全匹配
     * tier 3 模糊匹配或仅歌手匹配
     * tier 4 无匹配
     */
    private fun computeTier(titleMatch: MatchTier, artistMatch: MatchTier, albumMatch: MatchTier): Int = when {
        titleMatch.rank <= MatchTier.EXACT.rank -> 0
        titleMatch.rank <= MatchTier.PREFIX.rank || albumMatch.rank <= MatchTier.EXACT.rank -> 1
        titleMatch.rank <= MatchTier.CONTAINS.rank ||
            albumMatch.rank <= MatchTier.CONTAINS.rank ||
            artistMatch.rank <= MatchTier.EXACT.rank -> 2
        titleMatch.rank <= MatchTier.FUZZY.rank ||
            albumMatch.rank <= MatchTier.FUZZY.rank ||
            artistMatch.rank <= MatchTier.CONTAINS.rank -> 3
        else -> 4
    }

    private fun rankKeyComparator(): Comparator<Pair<Any?, RankKey>> = compareBy<Pair<Any?, RankKey>> { it.second.tier }
        .thenBy { it.second.sourceRank }
        .thenByDescending { it.second.relevance }

    /**
     * 计算单个字段的匹配档位
     */
    private fun matchLevel(haystack: String, needle: String): MatchTier {
        if (needle.isBlank()) return MatchTier.NONE
        if (haystack.equals(needle, ignoreCase = true)) return MatchTier.EXACT
        if (haystack.startsWith(needle, ignoreCase = true)) return MatchTier.PREFIX
        if (haystack.contains(needle, ignoreCase = true)) return MatchTier.CONTAINS

        val similarity = levenshteinSimilarity(haystack.lowercase(), needle.lowercase())
        return if (similarity >= 0.7) MatchTier.FUZZY else MatchTier.NONE
    }

    /** 相似度（0.0-1.0），用于同档同源内细分；needle 为空时返回 0 */
    private val Pair<String, String>.similarity: Double
        get() {
            val (haystack, needle) = this
            if (needle.isBlank()) return 0.0
            if (haystack.equals(needle, ignoreCase = true)) return 1.0
            if (haystack.contains(needle, ignoreCase = true)) return 0.9
            return levenshteinSimilarity(haystack.lowercase(), needle.lowercase())
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
     * 获取数据源在优先级列表中的索引（-1 表示不在列表中）
     */
    private fun getSourcePriorityIndex(source: OnlineSource, priorityIndex: Map<String, Int>): Int {
        return priorityIndex[source.name.lowercase()] ?: -1
    }

    /**
     * 获取歌词数据源在优先级列表中的索引（-1 表示不在列表中）
     */
    private fun getLyricsSourcePriorityIndex(source: String, priorityIndex: Map<String, Int>): Int {
        return priorityIndex[normalizeLyricsSourceName(source)] ?: -1
    }

    /**
     * 标准化歌词源名称
     */
    private val SOURCE_NAME_NORMALIZER = Regex("[\\s_-]+")

    private fun normalizeLyricsSourceName(source: String): String {
        return source.lowercase().replace(SOURCE_NAME_NORMALIZER, "").replace("music", "")
    }
}
