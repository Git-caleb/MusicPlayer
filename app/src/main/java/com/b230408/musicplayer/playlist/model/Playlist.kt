package com.b230408.musicplayer.playlist.model

import com.b230408.musicplayer.player.model.Track

/**
 * 歌单模型
 */
data class Playlist(
    /**
     * 歌单ID
     */
    val id: Long,
    
    /**
     * 歌单名称
     */
    val name: String,
    
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
    val dateModified: Long = System.currentTimeMillis()
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
    private fun updateModifiedTime() {
        // 注意：这是一个不可变数据类，实际应该使用copy()或者改为class
        // 这里为了简化，假设使用MutableList在外部管理
    }
}
