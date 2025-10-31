package com.b230408.musicplayer.metadata.model

import android.graphics.Bitmap

/**
 * 歌曲元数据模型
 */
data class Metadata(
    /**
     * 标题
     */
    val title: String? = null,
    
    /**
     * 艺术家/歌手
     */
    val artist: String? = null,
    
    /**
     * 专辑
     */
    val album: String? = null,
    
    /**
     * 年份
     */
    val year: Int? = null,
    
    /**
     * 流派
     */
    val genre: String? = null,
    
    /**
     * 专辑封面图片路径或字节数组
     */
    val coverArt: ByteArray? = null,
    
    /**
     * 封面Bitmap（可选，用于UI显示）
     */
    val coverBitmap: Bitmap? = null,
    
    /**
     * 音轨编号
     */
    val trackNumber: Int? = null,
    
    /**
     * 总音轨数
     */
    val totalTracks: Int? = null,
    
    /**
     * 注释/描述
     */
    val comment: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Metadata
        
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (year != other.year) return false
        if (genre != other.genre) return false
        if (coverArt != null) {
            if (other.coverArt == null) return false
            if (!coverArt.contentEquals(other.coverArt)) return false
        } else if (other.coverArt != null) return false
        if (trackNumber != other.trackNumber) return false
        if (totalTracks != other.totalTracks) return false
        if (comment != other.comment) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (year ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (coverArt?.contentHashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (totalTracks ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        return result
    }
}
