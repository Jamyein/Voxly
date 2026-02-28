package com.voxly.data.mapper

import com.voxly.domain.repository.OnlineRecording

/**
 * 统一搜索结果映射器
 * 将各数据源返回的原始数据转换为标准化的 OnlineRecording
 */
object OnlineRecordingMapper {
    
    /**
     * 从 iTunes 结果映射
     */
    fun fromITunes(
        trackId: Long?,
        trackName: String?,
        artistName: String?,
        durationMs: Long?,  // Changed from Int? to Long?
        collectionId: Long?,
        artworkUrl100: String?,
        primaryGenreName: String? = null,
        collectionArtistName: String? = null,
        discNumber: Int? = null,
        discCount: Int? = null,
        trackNumber: Int? = null,
        trackCount: Int? = null,
        collectionName: String? = null,  // 专辑名
        releaseDate: String? = null  // 发布日期
    ): OnlineRecording {
        return OnlineRecording(
            id = trackId?.toString() ?: "",
            title = trackName ?: "Unknown Track",
            artist = artistName ?: "Unknown Artist",
            album = collectionName,  // 填充专辑名
            duration = durationMs?.toInt(), // Convert Long to Int
            releaseId = collectionId?.toString(),
            source = "iTunes",
            coverArtUrl = artworkUrl100?.let { getHighResArtworkUrl(it, 3000) },
            genre = primaryGenreName,
            albumArtist = collectionArtistName,
            discNumber = discNumber,
            discCount = discCount,
            trackNumber = trackNumber,
            trackCount = trackCount,
            year = releaseDate?.let { getReleaseYear(it) }
        )
    }

    /**
     * 从日期字符串提取年份
     */
    private fun getReleaseYear(releaseDate: String?): Int? {
        return releaseDate?.take(4)?.toIntOrNull()
    }
    
    /**
     * 从 MusicBrainz 结果映射
     */
    fun fromMusicBrainz(
        id: String,
        title: String,
        artistName: String?,
        durationMs: Long?,
        releaseId: String?,
        coverArtBytes: ByteArray? = null,
        album: String? = null  // 专辑名
    ): OnlineRecording {
        return OnlineRecording(
            id = id,
            title = title,
            artist = artistName ?: "Unknown Artist",
            album = album,  // 填充专辑名
            duration = durationMs?.toInt(),
            releaseId = releaseId,
            source = "MusicBrainz",
            coverArtUrl = null,
            coverArtBytes = coverArtBytes
        )
    }
    
    /**
     * 从 NetEase 结果映射
     * 注意：NetEase 搜索结果中已包含专辑封面 (al.picUrl)
     */
    fun fromNetEase(
        id: Long,
        name: String,
        artists: List<ArtistData>?,
        album: AlbumData?,
        duration: Long?
    ): OnlineRecording? {
        if (id <= 0) return null
        val albumName = album?.name  // 专辑名
        return OnlineRecording(
            id = id.toString(),
            title = name,
            artist = artists?.joinToString(", ") { it.name } ?: "",
            album = albumName,  // 填充专辑名
            duration = duration?.toInt(),
            releaseId = album?.id?.toString(),
            source = "NetEase",
            coverArtUrl = album?.picUrl?.takeIf { it.isNotBlank() }
        )
    }
    
    /**
     * 从 QQ Music 结果映射
     */
    fun fromQQMusic(
        id: Int,
        name: String,
        singers: List<SingerData>?,
        interval: Int?,
        album: AlbumInfo?,
        lyrics: String? = null
    ): OnlineRecording {
        val albumId = album?.id?.toString()
        val albumMid = album?.mid
        val albumName = album?.name  // 专辑名
        return OnlineRecording(
            id = id.toString(),
            title = name,
            artist = singers?.joinToString(", ") { it.name } ?: "",
            album = albumName,  // 填充专辑名
            duration = interval,
            releaseId = albumMid ?: albumId,
            source = "QQ Music",
            coverArtUrl = buildQQCoverUrl(albumMid, album?.pic, albumId),
            lyrics = lyrics
        )
    }
    
    /**
     * 获取高分辨率封面 URL
     */
    private fun getHighResArtworkUrl(artworkUrl: String?, size: Int = 3000): String? {
        return artworkUrl
            ?.replace(Regex("\\d+x\\d+"), "${size}x${size}")
            ?.replace("http://", "https://")
    }
    
    /**
     * 构建 QQ Music 封面 URL
     * 正确格式: https://y.gtimg.cn/music/photo_new/T002R500x500M000{albumMid}.jpg
     */
    private fun buildQQCoverUrl(
        albumMid: String?,
        rawCoverUrl: String?,
        fallbackId: String?
    ): String? {
        // 先尝试使用原始URL，转换为https
        val raw = rawCoverUrl?.takeIf { it.isNotBlank() }
        if (!raw.isNullOrBlank()) {
            return if (raw.startsWith("http://", ignoreCase = true)) {
                "https://${raw.removePrefix("http://")}"
            } else {
                raw
            }
        }
        
        // 使用albumMid构建URL (格式: T002R500x500M000 + mid)
        val mid = albumMid?.trim().orEmpty()
        if (mid.isNotBlank()) {
            return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${mid}.jpg"
        }
        
        // 使用fallbackId构建URL
        val id = fallbackId?.trim().orEmpty()
        if (id.isNotBlank()) {
            return "https://y.gtimg.cn/music/photo_new/T002R500x500M000${id}.jpg"
        }
        
        return null
    }
    
    // ========== 数据类 ==========
    
    /** NetEase 歌手数据 */
    data class ArtistData(val name: String)
    
    /** NetEase 专辑数据 */
    data class AlbumData(
        val id: Long?,
        val name: String?,
        val picUrl: String?
    )
    
    /** QQ Music 歌手数据 */
    data class SingerData(val name: String)
    
    /** QQ Music 专辑数据 */
    data class AlbumInfo(
        val id: Long?,
        val mid: String?,
        val name: String?,
        val pic: String?
    )
}
