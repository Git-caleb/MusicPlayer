package com.b230408.musicplayer.playlist.manager

import android.content.Context
import com.b230408.musicplayer.playlist.model.Playlist
import com.b230408.musicplayer.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 歌单管理器
 * 负责歌单的增删查改操作
 */
class PlaylistManager(private val context: Context) {
    
    private val playlists = mutableListOf<Playlist>()
    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val playlistsFile: File by lazy {
        File(context.filesDir, "playlists.json")
    }
    
    init {
        // 在协程作用域中加载歌单
        scope.launch {
            loadPlaylists()
        }
    }
    
    /**
     * 获取所有歌单
     */
    fun getAllPlaylists(): List<Playlist> {
        return playlists.toList()
    }
    
    /**
     * 获取所有歌单的Flow
     */
    fun getAllPlaylistsFlow(): StateFlow<List<Playlist>> {
        return playlistsFlow
    }
    
    /**
     * 根据ID获取歌单
     */
    fun getPlaylistById(id: Long): Playlist? {
        return playlists.find { it.id == id }
    }
    
    /**
     * 根据名称获取歌单
     */
    fun getPlaylistByName(name: String): Playlist? {
        return playlists.find { it.name == name }
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
        playlists.add(playlist)
        scope.launch { savePlaylists() }
        playlistsFlow.value = playlists.toList()
        return playlist
    }
    
    /**
     * 删除歌单
     */
    fun deletePlaylist(playlistId: Long): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        playlists.remove(playlist)
        scope.launch { savePlaylists() }
        playlistsFlow.value = playlists.toList()
        return true
    }
    
    /**
     * 重命名歌单
     */
    fun renamePlaylist(playlistId: Long, newName: String): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        val updatedPlaylist = playlist.copy(name = newName)
        val index = playlists.indexOf(playlist)
        if (index != -1) {
            playlists[index] = updatedPlaylist
            scope.launch { savePlaylists() }
            playlistsFlow.value = playlists.toList()
            return true
        }
        return false
    }
    
    /**
     * 向歌单添加歌曲
     */
    fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        if (!playlist.tracks.contains(track)) {
            playlist.tracks.add(track)
            scope.launch { savePlaylists() }
            playlistsFlow.value = playlists.toList()
            return true
        }
        return false
    }
    
    /**
     * 从歌单移除歌曲
     */
    fun removeTrackFromPlaylist(playlistId: Long, track: Track): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
            val removed = playlist.tracks.remove(track)
        if (removed) {
            scope.launch { savePlaylists() }
            playlistsFlow.value = playlists.toList()
        }
        return removed
    }
    
    /**
     * 添加多个歌曲到歌单
     */
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        var added = false
        tracks.forEach { track ->
            if (!playlist.tracks.contains(track)) {
                playlist.tracks.add(track)
                added = true
            }
        }
        if (added) {
            scope.launch { savePlaylists() }
            playlistsFlow.value = playlists.toList()
        }
        return added
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
        playlists.add(0, playlist) // 添加到开头
        scope.launch { savePlaylists() }
        playlistsFlow.value = playlists.toList()
        return playlist
    }
    
    /**
     * 保存歌单到文件
     */
    private suspend fun savePlaylists() = withContext(Dispatchers.IO) {
        try {
            // 简化版：使用JSON序列化（实际项目中可能需要更完善的序列化）
            val json = Json { ignoreUnknownKeys = true }
            val data = playlists.map { playlist ->
                PlaylistData(
                    id = playlist.id,
                    name = playlist.name,
                    trackIds = playlist.tracks.map { it.id },
                    dateCreated = playlist.dateCreated,
                    dateModified = System.currentTimeMillis()
                )
            }
            val jsonString = json.encodeToString(data)
            playlistsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 从文件加载歌单
     */
    private suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        try {
            if (playlistsFile.exists()) {
                val jsonString = playlistsFile.readText()
                val json = Json { ignoreUnknownKeys = true }
                // 注意：这里简化了加载逻辑，实际需要维护Track的映射关系
                // 建议使用数据库（Room）来持久化
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 清除所有歌单（测试用）
     */
    fun clearAllPlaylists() {
        playlists.clear()
        scope.launch { savePlaylists() }
        playlistsFlow.value = emptyList()
    }
    
    /**
     * 歌单数据序列化类
     */
    @Serializable
    private data class PlaylistData(
        val id: Long,
        val name: String,
        val trackIds: List<Long>,
        val dateCreated: Long,
        val dateModified: Long
    )
}
