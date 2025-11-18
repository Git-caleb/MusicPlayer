package com.b230408.musicplayer.database.dao

import androidx.room.*
import com.b230408.musicplayer.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌单数据访问对象
 */
@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY dateModified DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)
    
    @Query("SELECT * FROM playlists WHERE name = :name")
    suspend fun getPlaylistsByName(name: String): List<PlaylistEntity>
}


