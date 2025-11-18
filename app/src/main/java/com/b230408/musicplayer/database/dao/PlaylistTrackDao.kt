package com.b230408.musicplayer.database.dao

import androidx.room.*
import com.b230408.musicplayer.database.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌单与歌曲关联数据访问对象
 */
@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getTracksByPlaylistId(playlistId: Long): Flow<List<PlaylistTrackEntity>>
    
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksByPlaylistIdSync(playlistId: Long): List<PlaylistTrackEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrackEntity>)
    
    @Delete
    suspend fun deletePlaylistTrack(playlistTrack: PlaylistTrackEntity)
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksByPlaylistId(playlistId: Long)
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrackFromPlaylist(playlistId: Long, trackId: Long)
}


