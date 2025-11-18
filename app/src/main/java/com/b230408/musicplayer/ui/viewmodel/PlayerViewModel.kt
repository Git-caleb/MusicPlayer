package com.b230408.musicplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.b230408.musicplayer.player.controller.MusicController
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.player.utils.PlaybackMode
import com.b230408.musicplayer.playlist.manager.PlaylistManager
import com.b230408.musicplayer.playlist.model.Playlist
import com.b230408.musicplayer.playlist.scanner.FileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器ViewModel
 */
class PlayerViewModel(
    private val context: android.content.Context
) : ViewModel() {
    
    private val musicController = try {
        MusicController.getInstance(context)
    } catch (e: Exception) {
        e.printStackTrace()
        // 如果初始化失败，创建一个备用实例（但这不应该发生）
        throw RuntimeException("Failed to initialize MusicController", e)
    }
    private val playlistManager = try {
        PlaylistManager(context)
    } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException("Failed to initialize PlaylistManager", e)
    }
    private val fileScanner = try {
        FileScanner(context)
    } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException("Failed to initialize FileScanner", e)
    }
    
    // 状态流
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode
    
    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist
    
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    init {
        try {
            loadPlaylists()
            startProgressUpdates()
            setupMusicControllerCallbacks()
        } catch (e: Exception) {
            e.printStackTrace()
            // 初始化失败不应该导致崩溃
        }
    }
    
    /**
     * 设置 MusicController 的回调
     */
    private fun setupMusicControllerCallbacks() {
        musicController.playbackStateCallback = { isPlaying ->
            _isPlaying.value = isPlaying
        }
        musicController.progressCallback = { position, duration ->
            _currentPosition.value = position
            if (duration > 0 && duration != Long.MAX_VALUE) {
                _duration.value = duration
            }
        }
        musicController.trackChangedCallback = { track ->
            _currentTrack.value = track
            _duration.value = musicController.getDuration()
        }
    }
    
    /**
     * 扫描音乐文件
     */
    fun scanMusicFiles(directoryPath: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 先触发媒体扫描，确保新文件被系统识别
                withContext(Dispatchers.IO) {
                    try {
                        fileScanner.triggerMusicDirectoryScan()
                        // 等待更长时间，让系统完成扫描（特别是 MediaStore）
                        kotlinx.coroutines.delay(2000) // 增加到 2 秒
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayer", "触发媒体扫描失败", e)
                        e.printStackTrace()
                        // 媒体扫描失败不影响后续扫描
                    }
                }
                
                val tracks = withContext(Dispatchers.IO) {
                    try {
                        val allTracks = mutableListOf<Track>()
                        
                        if (directoryPath != null) {
                            // 扫描指定目录
                            allTracks.addAll(fileScanner.scanPath(directoryPath))
                        } else {
                            // 先尝试 MediaStore
                            val mediaStoreTracks = try {
                                val tracks = fileScanner.scanMediaStore()
                                android.util.Log.d("MusicPlayer", "MediaStore 扫描到 ${tracks.size} 首歌曲")
                                tracks
                            } catch (e: Exception) {
                                android.util.Log.e("MusicPlayer", "MediaStore 扫描失败", e)
                                e.printStackTrace()
                                emptyList()
                            }
                            
                            // 如果 MediaStore 有结果，使用它
                            if (mediaStoreTracks.isNotEmpty()) {
                                allTracks.addAll(mediaStoreTracks)
                            } else {
                                // 如果 MediaStore 扫描不到，直接扫描 Music 目录
                                try {
                                    val directoryTracks = fileScanner.scanPath("/storage/emulated/0/Music")
                                    android.util.Log.d("MusicPlayer", "目录扫描到 ${directoryTracks.size} 首歌曲")
                                    allTracks.addAll(directoryTracks)
                                } catch (e: Exception) {
                                    android.util.Log.e("MusicPlayer", "目录扫描失败", e)
                                    e.printStackTrace()
                                }
                            }
                        }
                        
                        android.util.Log.d("MusicPlayer", "总共扫描到 ${allTracks.size} 首歌曲")
                        allTracks
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayer", "扫描音乐文件失败", e)
                        e.printStackTrace()
                        emptyList()
                    }
                }
                
                android.util.Log.d("MusicPlayer", "扫描完成，共 ${tracks.size} 首歌曲")
                
                if (tracks.isNotEmpty()) {
                    try {
                        // 先检查是否已存在"所有音乐"播放列表
                        val existingPlaylist = withContext(Dispatchers.IO) {
                            playlistManager.getPlaylistByName("所有音乐")
                        }
                        
                        if (existingPlaylist != null) {
                            // 如果存在，更新它
                            android.util.Log.d("MusicPlayer", "找到已存在的播放列表，更新中...")
                            withContext(Dispatchers.IO) {
                                playlistManager.updateDefaultPlaylist("所有音乐", tracks)
                            }
                        } else {
                            // 如果不存在，创建新的
                            android.util.Log.d("MusicPlayer", "创建新的播放列表")
                            withContext(Dispatchers.IO) {
                                playlistManager.createDefaultPlaylist("所有音乐", tracks)
                            }
                        }
                        
                        // 重新获取更新后的播放列表
                        val playlist = withContext(Dispatchers.IO) {
                            playlistManager.getPlaylistByName("所有音乐")
                        }
                        
                        android.util.Log.d("MusicPlayer", "播放列表处理成功，包含 ${playlist?.tracks?.size ?: 0} 首歌曲")
                        
                        // 更新播放列表列表
                        val updatedPlaylists = withContext(Dispatchers.IO) {
                            playlistManager.getAllPlaylists()
                        }
                        _playlists.value = updatedPlaylists
                        
                        // 如果当前没有播放列表，设置这个为当前播放列表
                        if (_currentPlaylist.value == null && playlist != null) {
                            setPlaylist(playlist, 0)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayer", "处理播放列表失败", e)
                        e.printStackTrace()
                        // 处理播放列表失败不影响UI显示
                    }
                } else {
                    android.util.Log.w("MusicPlayer", "未扫描到任何音乐文件")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 设置播放列表
     */
    fun setPlaylist(playlist: Playlist, startIndex: Int = 0) {
        try {
            musicController.setPlaylist(playlist, startIndex)
            _currentPlaylist.value = playlist
            updateCurrentTrack()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 播放
     */
    fun play() {
        try {
            musicController.play()
            // 不立即设置 isPlaying，等待 MusicService 的实际播放状态
            // _isPlaying.value = true
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "播放失败", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 暂停
     */
    fun pause() {
        try {
            musicController.pause()
            // 不立即设置 isPlaying，等待 MusicService 的实际播放状态
            // _isPlaying.value = false
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "暂停失败", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 停止
     */
    fun stop() {
        musicController.stop()
        _isPlaying.value = false
    }
    
    /**
     * 上一首
     */
    fun previous() {
        musicController.previous()
        updateCurrentTrack()
    }
    
    /**
     * 下一首
     */
    fun next() {
        musicController.next()
        updateCurrentTrack()
    }
    
    /**
     * 快进
     */
    fun seekForward() {
        try {
            musicController.seekForward()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 倒退
     */
    fun seekBackward() {
        try {
            musicController.seekBackward()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        try {
            musicController.seekTo(position)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 切换播放模式
     */
    fun togglePlaybackMode() {
        try {
            val current = _playbackMode.value
            val next = when (current) {
                PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
            }
            setPlaybackMode(next)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 设置播放模式
     */
    fun setPlaybackMode(mode: PlaybackMode) {
        try {
            musicController.setPlaybackMode(mode)
            _playbackMode.value = mode
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新当前音轨
     */
    private fun updateCurrentTrack() {
        try {
            _currentTrack.value = musicController.getCurrentTrack()
            _duration.value = musicController.getDuration()
        } catch (e: Exception) {
            e.printStackTrace()
            // 更新失败不应该导致崩溃
        }
    }
    
    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        viewModelScope.launch {
            try {
                while (true) {
                    kotlinx.coroutines.delay(1000) // 每秒更新一次
                    try {
                        if (_isPlaying.value) {
                            try {
                                _currentPosition.value = musicController.getCurrentPosition()
                                _duration.value = musicController.getDuration()
                                updateCurrentTrack()
                            } catch (e: Exception) {
                                // 获取播放信息失败时，不中断循环
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        // 更新失败不中断循环
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 进度更新失败不应该导致崩溃
            }
        }
    }
    
    /**
     * 加载播放列表
     */
    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val allPlaylists = playlistManager.getAllPlaylists()
                _playlists.value = allPlaylists
                if (allPlaylists.isNotEmpty()) {
                    _currentPlaylist.value = allPlaylists.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 加载失败时使用空列表
                _playlists.value = emptyList()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        musicController.unbindService()
    }
}
