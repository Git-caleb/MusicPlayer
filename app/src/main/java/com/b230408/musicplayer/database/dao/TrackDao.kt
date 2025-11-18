package com.b230408.musicplayer.database.dao

import androidx.room.*
import com.b230408.musicplayer.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲数据访问对象
 */
@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?
    
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)
    
    @Update
    suspend fun updateTrack(track: TrackEntity)
    
    @Delete
    suspend fun deleteTrack(track: TrackEntity)
}


