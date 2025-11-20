package com.b230408.musicplayer.database.dao

import androidx.room.*
import com.b230408.musicplayer.database.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放历史数据访问对象
 */
@Dao
interface PlayHistoryDao {
    /**
     * 获取所有播放历史（按时间倒序）
     */
    @Query("SELECT * FROM play_history ORDER BY playTime DESC")
    fun getAllHistory(): Flow<List<PlayHistoryEntity>>
    
    /**
     * 获取所有播放历史（按时间倒序，同步方法）
     */
    @Query("SELECT * FROM play_history ORDER BY playTime DESC")
    suspend fun getAllHistorySync(): List<PlayHistoryEntity>
    
    /**
     * 获取最近的播放历史
     */
    @Query("SELECT * FROM play_history ORDER BY playTime DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<PlayHistoryEntity>
    
    /**
     * 根据歌曲ID获取播放历史
     */
    @Query("SELECT * FROM play_history WHERE trackId = :trackId ORDER BY playTime DESC")
    suspend fun getHistoryByTrackId(trackId: Long): List<PlayHistoryEntity>
    
    /**
     * 插入播放历史
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlayHistoryEntity)
    
    /**
     * 删除播放历史
     */
    @Delete
    suspend fun deleteHistory(history: PlayHistoryEntity)
    
    /**
     * 删除所有播放历史
     */
    @Query("DELETE FROM play_history")
    suspend fun deleteAllHistory()
    
    /**
     * 删除指定时间之前的播放历史
     */
    @Query("DELETE FROM play_history WHERE playTime < :beforeTime")
    suspend fun deleteHistoryBefore(beforeTime: Long)
    
    /**
     * 获取播放历史数量
     */
    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun getHistoryCount(): Int
}

