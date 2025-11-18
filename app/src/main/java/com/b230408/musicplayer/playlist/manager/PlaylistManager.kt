package com.b230408.musicplayer.playlist.manager

import android.content.Context
import com.b230408.musicplayer.database.MusicPlayerDatabase
import com.b230408.musicplayer.database.dao.PlaylistDao
import com.b230408.musicplayer.database.dao.PlaylistTrackDao
import com.b230408.musicplayer.database.dao.TrackDao
import com.b230408.musicplayer.database.entity.PlaylistEntity
import com.b230408.musicplayer.database.entity.PlaylistTrackEntity
import com.b230408.musicplayer.database.entity.TrackEntity
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.playlist.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

/**
 * 歌单管理器
 * 负责歌单的增删查改操作
 * 使用Room数据库进行持久化
 */
class PlaylistManager(private val context: Context) {
    
    private val database = MusicPlayerDatabase.getDatabase(context)
    private val playlistDao: PlaylistDao = database.playlistDao()
    private val trackDao: TrackDao = database.trackDao()
    private val playlistTrackDao: PlaylistTrackDao = database.playlistTrackDao()
    
    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    init {
        // 在协程作用域中加载歌单
        scope.launch {
            loadPlaylists()
        }
    }
    
    /**
     * 获取所有歌单
     */
    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            // 使用first()获取Flow的第一个值
            val entities = playlistDao.getAllPlaylists().first()
            entities.map { entity ->
                val tracks = loadTracksForPlaylist(entity.id)
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    tracks = tracks.toMutableList(),
                    dateCreated = entity.dateCreated,
                    dateModified = entity.dateModified
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 获取所有歌单的Flow
     */
    fun getAllPlaylistsFlow(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                val tracks = loadTracksForPlaylistSync(entity.id)
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    tracks = tracks.toMutableList(),
                    dateCreated = entity.dateCreated,
                    dateModified = entity.dateModified
                )
            }
        }
    }
    
    /**
     * 根据ID获取歌单
     */
    suspend fun getPlaylistById(id: Long): Playlist? = withContext(Dispatchers.IO) {
        val entity = playlistDao.getPlaylistById(id) ?: return@withContext null
        val tracks = loadTracksForPlaylist(id)
        Playlist(
            id = entity.id,
            name = entity.name,
            tracks = tracks.toMutableList(),
            dateCreated = entity.dateCreated,
            dateModified = entity.dateModified
        )
    }
    
    /**
     * 根据名称获取歌单
     */
    suspend fun getPlaylistByName(name: String): Playlist? = withContext(Dispatchers.IO) {
        // 需要添加查询方法，暂时通过获取所有歌单来查找
        val allPlaylists = getAllPlaylists()
        allPlaylists.find { it.name == name }
    }
    
    /**
     * 创建新歌单
     */
    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            tracks = mutableListOf(),
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
        scope.launch {
            savePlaylist(playlist)
            loadPlaylists()
        }
        return playlist
    }
    
    /**
     * 删除歌单
     */
    fun deletePlaylist(playlistId: Long): Boolean {
        scope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.deletePlaylistById(playlistId)
                playlistTrackDao.deleteTracksByPlaylistId(playlistId)
            }
            loadPlaylists()
        }
        return true
    }
    
    /**
     * 重命名歌单
     */
    fun renamePlaylist(playlistId: Long, newName: String): Boolean {
        scope.launch {
            withContext(Dispatchers.IO) {
                val entity = playlistDao.getPlaylistById(playlistId) ?: return@withContext
                val updatedEntity = entity.copy(
                    name = newName,
                    dateModified = System.currentTimeMillis()
                )
                playlistDao.updatePlaylist(updatedEntity)
            }
            loadPlaylists()
        }
        return true
    }
    
    /**
     * 向歌单添加歌曲
     */
    fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean {
        scope.launch {
            withContext(Dispatchers.IO) {
                // 保存Track到数据库
                val trackEntity = trackToEntity(track)
                trackDao.insertTrack(trackEntity)
                
                // 获取当前歌单的歌曲数量，作为新位置
                val existingTracks = playlistTrackDao.getTracksByPlaylistIdSync(playlistId)
                val position = existingTracks.size
                
                // 创建关联
                val playlistTrack = PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = position
                )
                playlistTrackDao.insertPlaylistTrack(playlistTrack)
                
                // 更新歌单修改时间
                val entity = playlistDao.getPlaylistById(playlistId) ?: return@withContext
                val updatedEntity = entity.copy(dateModified = System.currentTimeMillis())
                playlistDao.updatePlaylist(updatedEntity)
            }
            loadPlaylists()
        }
        return true
    }
    
    /**
     * 从歌单移除歌曲
     */
    fun removeTrackFromPlaylist(playlistId: Long, track: Track): Boolean {
        scope.launch {
            withContext(Dispatchers.IO) {
                playlistTrackDao.deleteTrackFromPlaylist(playlistId, track.id)
                
                // 更新歌单修改时间
                val entity = playlistDao.getPlaylistById(playlistId) ?: return@withContext
                val updatedEntity = entity.copy(dateModified = System.currentTimeMillis())
                playlistDao.updatePlaylist(updatedEntity)
            }
            loadPlaylists()
        }
        return true
    }
    
    /**
     * 添加多个歌曲到歌单
     */
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>): Boolean {
        scope.launch {
            withContext(Dispatchers.IO) {
                // 保存所有Track到数据库
                val trackEntities = tracks.map { trackToEntity(it) }
                trackDao.insertTracks(trackEntities)
                
                // 获取当前歌单的歌曲数量
                val existingTracks = playlistTrackDao.getTracksByPlaylistIdSync(playlistId)
                var position = existingTracks.size
                
                // 创建关联
                val playlistTracks = tracks.map { track ->
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = track.id,
                        position = position++
                    )
                }
                playlistTrackDao.insertPlaylistTracks(playlistTracks)
                
                // 更新歌单修改时间
                val entity = playlistDao.getPlaylistById(playlistId) ?: return@withContext
                val updatedEntity = entity.copy(dateModified = System.currentTimeMillis())
                playlistDao.updatePlaylist(updatedEntity)
            }
            loadPlaylists()
        }
        return true
    }
    
    /**
     * 创建默认歌单（所有音乐）
     */
    fun createDefaultPlaylist(name: String = "所有音乐", tracks: List<Track>): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            tracks = tracks.toMutableList(),
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
        scope.launch {
            savePlaylist(playlist)
            loadPlaylists()
        }
        return playlist
    }
    
    /**
     * 保存歌单到数据库
     */
    private suspend fun savePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        // 保存歌单实体
        val entity = PlaylistEntity(
            id = playlist.id,
            name = playlist.name,
            dateCreated = playlist.dateCreated,
            dateModified = playlist.dateModified
        )
        playlistDao.insertPlaylist(entity)
        
        // 保存所有Track到数据库
        val trackEntities = playlist.tracks.map { trackToEntity(it) }
        trackDao.insertTracks(trackEntities)
        
        // 删除旧的关联
        playlistTrackDao.deleteTracksByPlaylistId(playlist.id)
        
        // 创建新的关联
        val playlistTracks = playlist.tracks.mapIndexed { index, track ->
            PlaylistTrackEntity(
                playlistId = playlist.id,
                trackId = track.id,
                position = index
            )
        }
        playlistTrackDao.insertPlaylistTracks(playlistTracks)
    }
    
    /**
     * 从数据库加载歌单
     */
    private suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        try {
            val entities = playlistDao.getAllPlaylists().first()
            val playlists = entities.map { entity ->
                val tracks = loadTracksForPlaylist(entity.id)
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    tracks = tracks.toMutableList(),
                    dateCreated = entity.dateCreated,
                    dateModified = entity.dateModified
                )
            }
            playlistsFlow.value = playlists
        } catch (e: Exception) {
            e.printStackTrace()
            playlistsFlow.value = emptyList()
        }
    }
    
    /**
     * 加载歌单的歌曲列表（异步）
     */
    private suspend fun loadTracksForPlaylist(playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        val playlistTracks = playlistTrackDao.getTracksByPlaylistIdSync(playlistId)
        val trackIds = playlistTracks.map { it.trackId }
        val trackEntities = trackDao.getTracksByIds(trackIds)
        trackEntities.map { entityToTrack(it) }
    }
    
    /**
     * 加载歌单的歌曲列表（同步）
     */
    private fun loadTracksForPlaylistSync(playlistId: Long): List<Track> {
        return try {
            val playlistTracks = kotlinx.coroutines.runBlocking {
                playlistTrackDao.getTracksByPlaylistIdSync(playlistId)
            }
            val trackIds = playlistTracks.map { it.trackId }
            val trackEntities = kotlinx.coroutines.runBlocking {
                trackDao.getTracksByIds(trackIds)
            }
            trackEntities.map { entityToTrack(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Track转换为TrackEntity
     */
    private fun trackToEntity(track: Track): TrackEntity {
        return TrackEntity(
            id = track.id,
            path = track.path,
            uri = track.uri.toString(),
            fileName = track.fileName,
            fileSize = track.fileSize,
            duration = track.duration,
            format = track.format,
            title = track.metadata?.title,
            artist = track.metadata?.artist,
            album = track.metadata?.album,
            dateAdded = track.dateAdded
        )
    }
    
    /**
     * TrackEntity转换为Track
     */
    private fun entityToTrack(entity: TrackEntity): Track {
        val metadata = if (entity.title != null || entity.artist != null || entity.album != null) {
            com.b230408.musicplayer.metadata.model.Metadata(
                title = entity.title,
                artist = entity.artist,
                album = entity.album
            )
        } else {
            null
        }
        
        return Track(
            id = entity.id,
            path = entity.path,
            uri = Uri.parse(entity.uri),
            fileName = entity.fileName,
            fileSize = entity.fileSize,
            duration = entity.duration,
            format = entity.format,
            metadata = metadata,
            dateAdded = entity.dateAdded
        )
    }
    
    /**
     * 清除所有歌单（测试用）
     */
    fun clearAllPlaylists() {
        scope.launch {
            withContext(Dispatchers.IO) {
                // 删除所有关联
                val entities = playlistDao.getAllPlaylists().first()
                entities.forEach { entity ->
                    playlistTrackDao.deleteTracksByPlaylistId(entity.id)
                }
                // 删除所有歌单
                entities.forEach { entity ->
                    playlistDao.deletePlaylist(entity)
                }
            }
            playlistsFlow.value = emptyList()
        }
    }
}

