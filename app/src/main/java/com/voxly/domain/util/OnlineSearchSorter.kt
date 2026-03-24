package com.voxly.domain.util

import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource
import timber.log.Timber

/**
 * 统一的在线搜索结果排序工具
 * 
 * 排序策略：
 * 1. 计算相关性分数（标题匹配度 + 艺术家匹配度，范围 2-6）
 * 2. 前 N 个位置预留给各优先级的最佳结果（默认前 3 个优先级）
 * 3. 剩余结果按综合分数排序（优先级权重 + 相关性分数）
 */
object OnlineSearchSorter {
    
    private const val PRIORITY_WEIGHT = 25  // 确保优先级差 × 25 > 相关性最大差(4)
    private const val DEFAULT_PRIORITY_SLOTS = 3  // 前 N 个位置预留给各优先级
    
    /**
     * 排序 OnlineRecording 列表（封面搜索）
     */
    fun sortRecordings(
        recordings: List<OnlineRecording>,
        title: String,
        artist: String?,
        sourcePriority: List<String>,
        prioritySlots: Int = DEFAULT_PRIORITY_SLOTS
    ): List<OnlineRecording> {
        if (recordings.isEmpty()) return emptyList()
        
        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        val actualPrioritySlots = minOf(prioritySlots, maxPriority)
        
        // 计算每个 recording 的排序分数
        val scored = recordings.map { recording ->
            val relevanceScore = calculateRelevanceScore(
                title = recording.title,
                artist = recording.artist,
                titleNeedle = titleNeedle,
                artistNeedle = artistNeedle
            )
            val sourcePriorityIndex = getSourcePriorityIndex(recording.source, sourcePriority)
            // 综合分数 = 优先级权重 * 优先级差距 + 相关性分数
            val totalScore = (maxPriority - 1 - sourcePriorityIndex.coerceAtLeast(0)) * PRIORITY_WEIGHT + relevanceScore
            
            RecordingSortKey(recording, relevanceScore, sourcePriorityIndex, totalScore)
        }
        
        // 策略：前 N 个位置预留给优先级 1、2、3... 的最佳结果
        val topByPriority = sourcePriority
            .take(actualPrioritySlots)
            .mapIndexedNotNull { priorityIndex, _ ->
                scored
                    .filter { it.sourcePriority == priorityIndex }
                    .maxByOrNull { it.relevanceScore }
            }
        
        // 排除已使用的前 N 个
        val usedRecordings = topByPriority.map { it.recording }.toSet()
        val remaining = scored
            .filter { it.recording !in usedRecordings }
            .sortedByDescending { it.totalScore }
        
        // 最终结果 = 前 N 名(优先级冠军) + 剩余按综合分数
        return (topByPriority + remaining).map { it.recording }
    }
    
    /**
     * 排序 OnlineRelease 列表（元数据搜索）
     */
    fun sortReleases(
        releases: List<OnlineRelease>,
        title: String,
        artist: String?,
        sourcePriority: List<String>,
        prioritySlots: Int = DEFAULT_PRIORITY_SLOTS
    ): List<OnlineRelease> {
        if (releases.isEmpty()) return emptyList()
        
        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        val actualPrioritySlots = minOf(prioritySlots, maxPriority)
        
        // 计算每个 release 的排序分数
        val scored = releases.map { release ->
            val candidateTitle = (release.songTitle ?: release.albumTitle ?: release.title).trim()
            val relevanceScore = calculateRelevanceScore(
                title = candidateTitle,
                artist = release.artist,
                titleNeedle = titleNeedle,
                artistNeedle = artistNeedle
            )
            val sourcePriorityIndex = getSourcePriorityIndex(release.source, sourcePriority)
            // 综合分数 = 优先级权重 * 优先级差距 + 相关性分数
            val totalScore = (maxPriority - 1 - sourcePriorityIndex.coerceAtLeast(0)) * PRIORITY_WEIGHT + relevanceScore
            
            ReleaseSortKey(release, relevanceScore, sourcePriorityIndex, totalScore)
        }
        
        // 策略：前 N 个位置预留给优先级 1、2、3... 的最佳结果
        val topByPriority = sourcePriority
            .take(actualPrioritySlots)
            .mapIndexedNotNull { priorityIndex, _ ->
                scored
                    .filter { it.sourcePriority == priorityIndex }
                    .maxByOrNull { it.relevanceScore }
            }
        
        // 排除已使用的前 N 个
        val usedReleases = topByPriority.map { it.release }.toSet()
        val remaining = scored
            .filter { it.release !in usedReleases }
            .sortedByDescending { it.totalScore }
        
        // 最终结果 = 前 N 名(优先级冠军) + 剩余按综合分数
        return (topByPriority + remaining).map { it.release }
    }
    
    /**
     * 排序 OnlineLyricsResult 列表（歌词搜索）
     * 
     * 排序策略：
     * 1. 相关性分数（标题 + 艺术家匹配度，范围 2-6）
     * 2. 同步歌词加分（如果有同步歌词，额外加分）
     * 3. 数据源优先级
     * 4. 前 N 个位置预留给各优先级的最佳结果
     */
    fun sortLyrics(
        lyrics: List<OnlineLyricsResult>,
        title: String,
        artist: String?,
        sourcePriority: List<String>,
        prioritySlots: Int = DEFAULT_PRIORITY_SLOTS
    ): List<OnlineLyricsResult> {
        if (lyrics.isEmpty()) return emptyList()
        
        val titleNeedle = title.trim()
        val artistNeedle = artist?.trim().orEmpty()
        val maxPriority = sourcePriority.size.coerceAtLeast(1)
        val actualPrioritySlots = minOf(prioritySlots, maxPriority)
        
        // 计算每个歌词结果的排序分数
        val scored = lyrics.map { lyric ->
            // 基础相关性分数（标题 + 艺术家）
            val relevanceScore = calculateRelevanceScore(
                title = lyric.trackName,
                artist = lyric.artistName,
                titleNeedle = titleNeedle,
                artistNeedle = artistNeedle
            )
            
            // 同步歌词加分（有同步歌词额外加 2 分）
            val syncedBonus = if (lyric.hasSyncedLyrics) 2 else 0
            val adjustedRelevanceScore = relevanceScore + syncedBonus
            
            // 获取数据源优先级索引
            val sourcePriorityIndex = getLyricsSourcePriorityIndex(lyric.source, sourcePriority)
            
            // 综合分数 = 优先级权重 * 优先级差距 + 调整后相关性分数
            val totalScore = (maxPriority - 1 - sourcePriorityIndex.coerceAtLeast(0)) * PRIORITY_WEIGHT + adjustedRelevanceScore
            
            LyricsSortKey(lyric, adjustedRelevanceScore, sourcePriorityIndex, totalScore, lyric.hasSyncedLyrics)
        }
        
        // 策略：前 N 个位置预留给优先级 1、2、3... 的最佳结果
        val topByPriority = sourcePriority
            .take(actualPrioritySlots)
            .mapIndexedNotNull { priorityIndex, _ ->
                scored
                    .filter { it.sourcePriority == priorityIndex }
                    .maxByOrNull { it.adjustedRelevanceScore }
            }
        
        // 排除已使用的前 N 个
        val usedLyrics = topByPriority.map { it.lyric }.toSet()
        val remaining = scored
            .filter { it.lyric !in usedLyrics }
            .sortedByDescending { it.totalScore }
        
        // 最终结果 = 前 N 名(优先级冠军) + 剩余按综合分数
        return (topByPriority + remaining).map { it.lyric }
    }
    
    /**
     * 计算相关性分数（范围 2-6）
     * 标题匹配分数(1-3) + 歌手名匹配分数(1-3)
     */
    private fun calculateRelevanceScore(
        title: String,
        artist: String,
        titleNeedle: String,
        artistNeedle: String
    ): Int {
        val titleScore = when {
            titleNeedle.isBlank() -> 1
            title.equals(titleNeedle, ignoreCase = true) -> 3
            title.contains(titleNeedle, ignoreCase = true) -> 2
            else -> 1
        }
        
        val artistScore = when {
            artistNeedle.isBlank() -> 1
            artist.equals(artistNeedle, ignoreCase = true) -> 3
            artist.contains(artistNeedle, ignoreCase = true) -> 2
            else -> 1
        }
        
        return titleScore + artistScore
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
     * 歌词数据源名称可能是 "NetEase", "QQ Music" 等格式
     */
    private fun getLyricsSourcePriorityIndex(source: String, priority: List<String>): Int {
        // 标准化歌词源名称
        val normalizedSource = source.lowercase().replace(" ", "_").replace("music", "")
        return priority.indexOfFirst {
            val normalizedPriority = it.lowercase().replace("_", "").replace("music", "")
            normalizedSource.contains(normalizedPriority) || normalizedPriority.contains(normalizedSource)
        }
    }

    /**
     * Recording 排序键
     */
    private data class RecordingSortKey(
        val recording: OnlineRecording,
        val relevanceScore: Int,
        val sourcePriority: Int,
        val totalScore: Int
    )

    /**
     * Release 排序键
     */
    private data class ReleaseSortKey(
        val release: OnlineRelease,
        val relevanceScore: Int,
        val sourcePriority: Int,
        val totalScore: Int
    )

    /**
     * 歌词排序键
     */
    private data class LyricsSortKey(
        val lyric: OnlineLyricsResult,
        val adjustedRelevanceScore: Int,
        val sourcePriority: Int,
        val totalScore: Int,
        val hasSyncedLyrics: Boolean
    )
}