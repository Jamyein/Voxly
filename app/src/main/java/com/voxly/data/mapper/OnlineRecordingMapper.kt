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
        trackCount: Int? = null
    ): OnlineRecording {
        return OnlineRecording(
            id = trackId?.toString() ?: "",
            title = trackName ?: "Unknown Track",
            artist = artistName ?: "Unknown Artist",
            duration = durationMs?.toInt(), // Convert Long to Int
            releaseId = collectionId?.toString(),
            source = "iTunes",
            coverArtUrl = artworkUrl100?.let { getHighResArtworkUrl(it, 3000) },
            genre = primaryGenreName,
            albumArtist = collectionArtistName,
            discNumber = discNumber,
            discCount = discCount,
            trackNumber = trackNumber,
            trackCount = trackCount
        )
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
        coverArtBytes: ByteArray? = null
    ): OnlineRecording {
        return OnlineRecording(
            id = id,
            title = title,
            artist = artistName ?: "Unknown Artist",
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
        return OnlineRecording(
            id = id.toString(),
            title = name,
            artist = artists?.joinToString(", ") { it.name } ?: "",
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
        album: AlbumInfo?
    ): OnlineRecording {
        val albumId = album?.id?.toString()
        val albumMid = album?.mid
        return OnlineRecording(
            id = id.toString(),
            title = name,
            artist = singers?.joinToString(", ") { it.name } ?: "",
            duration = interval,
            releaseId = albumMid ?: albumId,
            source = "QQ Music",
            coverArtUrl = buildQQCoverUrl(albumMid, album?.pic, albumId)
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
     */
    private fun buildQQCoverUrl(
        albumMid: String?,
        rawCoverUrl: String?,
        fallbackId: String?
    ): String? {
        return when {
            !albumMid.isNullOrBlank() -> "https://y.qq.com/music/photo_new/T002R$albumMid.jpg"
            !rawCoverUrl.isNullOrBlank() -> rawCoverUrl
            !fallbackId.isNullOrBlank() -> "https://y.qq.com/music/photo_new/T002R$fallbackId.jpg"
            else -> null
        }
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
