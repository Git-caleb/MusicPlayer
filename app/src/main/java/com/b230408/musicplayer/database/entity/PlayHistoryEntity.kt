package com.b230408.musicplayer.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史实体类
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["playTime"], name = "idx_play_time")]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 歌曲ID（关联到TrackEntity）
     */
    val trackId: Long,
    
    /**
     * 播放时间（毫秒时间戳）
     */
    val playTime: Long = System.currentTimeMillis(),
    
    /**
     * 播放时长（毫秒，用户实际播放了多长时间）
     */
    val playDuration: Long = 0L
)

