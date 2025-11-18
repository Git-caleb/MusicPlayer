package com.b230408.musicplayer.playlist.model

import com.b230408.musicplayer.player.model.Track

/**
 * 歌单模型
 */
class Playlist(
    /**
     * 歌单ID
     */
    val id: Long,
    
    /**
     * 歌单名称
     */
    var name: String,
    
    /**
     * 歌曲列表
     */
    val tracks: MutableList<Track> = mutableListOf(),
    
    /**
     * 创建时间（毫秒时间戳）
     */
    val dateCreated: Long = System.currentTimeMillis(),
    
    /**
     * 最后修改时间（毫秒时间戳）
     */
    var dateModified: Long = System.currentTimeMillis()
) {
    /**
     * 获取歌单中的歌曲数量
     */
    fun getTrackCount(): Int = tracks.size
    
    /**
     * 添加歌曲到歌单
     */
    fun addTrack(track: Track) {
        if (!tracks.contains(track)) {
            tracks.add(track)
            updateModifiedTime()
        }
    }
    
    /**
     * 从歌单移除歌曲
     */
    fun removeTrack(track: Track): Boolean {
        val removed = tracks.remove(track)
        if (removed) {
            updateModifiedTime()
        }
        return removed
    }
    
    /**
     * 更新修改时间
     */
    fun updateModifiedTime() {
        dateModified = System.currentTimeMillis()
    }
    
    /**
     * 复制方法，用于创建副本
     */
    fun copy(
        id: Long = this.id,
        name: String = this.name,
        tracks: MutableList<Track> = this.tracks.toMutableList(),
        dateCreated: Long = this.dateCreated,
        dateModified: Long = this.dateModified
    ): Playlist {
        return Playlist(id, name, tracks, dateCreated, dateModified)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Playlist
        
        if (id != other.id) return false
        if (name != other.name) return false
        if (tracks != other.tracks) return false
        if (dateCreated != other.dateCreated) return false
        if (dateModified != other.dateModified) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + tracks.hashCode()
        result = 31 * result + dateCreated.hashCode()
        result = 31 * result + dateModified.hashCode()
        return result
    }
}
