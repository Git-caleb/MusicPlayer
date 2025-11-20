package com.b230408.musicplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.b230408.musicplayer.history.manager.PlayHistoryManager
import com.b230408.musicplayer.lyrics.fetcher.LyricFetcher
import com.b230408.musicplayer.lyrics.model.LyricLine
import com.b230408.musicplayer.lyrics.player.LyricPlayer
import com.b230408.musicplayer.metadata.fetcher.CoverFetcher
import com.b230408.musicplayer.player.controller.MusicController
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.player.utils.PlaybackMode
import com.b230408.musicplayer.playlist.manager.PlaylistManager
import com.b230408.musicplayer.playlist.model.Playlist
import com.b230408.musicplayer.playlist.scanner.FileScanner
import com.b230408.musicplayer.utils.AssetsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    
    // 延迟初始化 PlaylistManager，避免阻塞启动
    private val playlistManager: PlaylistManager by lazy {
        try {
            PlaylistManager(context)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to initialize PlaylistManager", e)
        }
    }
    
    // 播放历史管理器
    private val playHistoryManager: PlayHistoryManager by lazy {
        PlayHistoryManager(context)
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
    
    // 缓存所有歌曲列表，避免重复加载
    private val _allTracksCache = MutableStateFlow<List<Track>>(emptyList())
    private var allTracksCacheInitialized = false
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // 歌词相关状态
    private val lyricPlayer = LyricPlayer()
    private val _lyrics = MutableStateFlow<List<String>>(emptyList())
    val lyrics: StateFlow<List<String>> = _lyrics
    
    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex
    
    // 封面URL状态
    private val _coverImageUrl = MutableStateFlow<String?>(null)
    val coverImageUrl: StateFlow<String?> = _coverImageUrl
    
    init {
        try {
            // 延迟加载播放列表，避免阻塞启动
            viewModelScope.launch {
                kotlinx.coroutines.delay(100) // 短暂延迟，让UI先渲染
                loadPlaylists()
            }
            startProgressUpdates()
            setupMusicControllerCallbacks()
            setupLyricPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
            // 初始化失败不应该导致崩溃
        }
    }
    
    /**
     * 设置歌词播放器
     */
    private fun setupLyricPlayer() {
        // 监听歌词变化，更新歌词列表和当前索引
        viewModelScope.launch {
            lyricPlayer.getCurrentLineFlow().collect { currentLine ->
                val allLyrics = lyricPlayer.getAllLyrics()
                _lyrics.value = allLyrics.map { it.text }
                _currentLyricIndex.value = lyricPlayer.getCurrentLineIndex()
            }
        }
        
        // 监听播放位置变化，更新歌词同步
        viewModelScope.launch {
            _currentPosition.collect { position ->
                lyricPlayer.updatePosition(position)
                // 同时更新索引（因为 updatePosition 会触发 Flow 更新）
                _currentLyricIndex.value = lyricPlayer.getCurrentLineIndex()
            }
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
            // 切换歌曲时加载歌词
            loadLyricsForTrack(track)
            // 获取网络封面
            loadCoverForTrack(track)
            // 记录播放历史
            playHistoryManager.recordPlayHistory(track)
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
                        // 减少等待时间，提升启动速度（MediaStore 扫描可以在后台进行）
                        kotlinx.coroutines.delay(500) // 减少到 0.5 秒
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayer", "触发媒体扫描失败", e)
                        e.printStackTrace()
                        // 媒体扫描失败不影响后续扫描
                    }
                }
                
                val tracks = withContext(Dispatchers.IO) {
                    try {
                        val allTracks = mutableListOf<Track>()
                        
                        // 先扫描 assets 中的内置音乐文件
                        try {
                            val assetsTracks = AssetsUtils.scanAssetsMusic(context)
                            android.util.Log.d("MusicPlayer", "Assets 扫描到 ${assetsTracks.size} 首内置歌曲")
                            allTracks.addAll(assetsTracks)
                        } catch (e: Exception) {
                            android.util.Log.e("MusicPlayer", "Assets 扫描失败", e)
                            e.printStackTrace()
                        }
                        
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
     * 为当前歌曲加载网络封面
     */
    private fun loadCoverForTrack(track: Track) {
        viewModelScope.launch {
            try {
                // 先清除之前的封面
                _coverImageUrl.value = null
                
                // 如果有本地封面，优先使用本地封面
                if (track.metadata?.coverBitmap != null || track.metadata?.coverArt != null) {
                    return@launch
                }
                
                // 尝试从网络获取封面
                val title = track.metadata?.title ?: return@launch
                val artist = track.metadata?.artist ?: return@launch
                
                val coverUrl = withContext(Dispatchers.IO) {
                    CoverFetcher.fetchCoverUrl(title, artist)
                }
                
                if (coverUrl != null) {
                    _coverImageUrl.value = coverUrl
                    android.util.Log.d("PlayerViewModel", "获取封面成功: $coverUrl")
                } else {
                    android.util.Log.d("PlayerViewModel", "未找到网络封面")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "加载封面失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 为当前歌曲加载歌词
     */
    private fun loadLyricsForTrack(track: Track) {
        viewModelScope.launch {
            try {
                // 先清除之前的歌词
                lyricPlayer.clear()
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                
                // 尝试加载歌词
                val lyricLines = withContext(Dispatchers.IO) {
                    // 优先从 assets 加载
                    val assetsLyrics = AssetsUtils.readLyricsFromAssets(context, track.fileName)
                    if (assetsLyrics != null) {
                        // 解析 LRC 格式
                        parseLRCFormat(assetsLyrics)
                    } else {
                        // 尝试从本地文件加载（与音频文件同目录）
                        // 对于 assets 文件，path 是 "assets://music/xxx.mp3"，需要特殊处理
                        if (!track.path.startsWith("assets://")) {
                            val audioFile = java.io.File(track.path)
                            if (audioFile.exists()) {
                                val parent = audioFile.parent
                                val nameWithoutExt = audioFile.nameWithoutExtension
                                val lrcFile = java.io.File(parent, "$nameWithoutExt.lrc")
                                if (lrcFile.exists() && lrcFile.isFile) {
                                    val content = lrcFile.readText(charset = Charsets.UTF_8)
                                    parseLRCFormat(content)
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        } else {
                            // assets 文件的歌词应该已经在上面尝试加载了，这里返回 null
                            null
                        } ?: run {
                            // 如果本地文件也没有，尝试从网络获取（如果有元数据）
                            track.metadata?.let { metadata ->
                                LyricFetcher.fetchLyrics(context, metadata, track.fileName)
                            }
                        }
                    }
                }
                
                // 设置歌词
                lyricLines?.let { lines ->
                    lyricPlayer.setLyrics(lines)
                    // 立即更新歌词列表
                    _lyrics.value = lines.map { it.text }
                    android.util.Log.d("PlayerViewModel", "加载歌词成功，共 ${lines.size} 行")
                } ?: run {
                    _lyrics.value = emptyList()
                    _currentLyricIndex.value = -1
                    android.util.Log.d("PlayerViewModel", "未找到歌词文件")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "加载歌词失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 解析 LRC 格式歌词
     */
    private fun parseLRCFormat(lrcText: String): List<LyricLine>? {
        return try {
            val lines = mutableListOf<LyricLine>()
            // 支持两种格式：[mm:ss.ff] 和 [mm:ss:ff]
            val regex = Regex("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")
            
            lrcText.lineSequence().forEach { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val minutes = match.groupValues[1].toInt()
                    val seconds = match.groupValues[2].toInt()
                    val milliseconds = match.groupValues[3].toInt()
                    val text = match.groupValues[4].trim()
                    
                    // 转换为毫秒时间戳
                    val timeStamp = (minutes * 60 + seconds) * 1000L + 
                        if (milliseconds < 100) milliseconds * 10L else milliseconds.toLong()
                    
                    if (text.isNotEmpty()) {
                        lines.add(LyricLine(timeStamp = timeStamp, text = text))
                    }
                }
            }
            
            lines.sortedBy { it.timeStamp }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "解析歌词失败", e)
            null
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
                // 更新所有歌曲缓存
                updateAllTracksCache(allPlaylists)
            } catch (e: Exception) {
                e.printStackTrace()
                // 加载失败时使用空列表
                _playlists.value = emptyList()
            }
        }
    }
    
    /**
     * 更新所有歌曲缓存
     */
    private fun updateAllTracksCache(playlists: List<Playlist>) {
        viewModelScope.launch {
            try {
                val allMusicPlaylist = playlists.find { it.name == "所有音乐" }
                if (allMusicPlaylist != null) {
                    _allTracksCache.value = allMusicPlaylist.tracks
                    allTracksCacheInitialized = true
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "更新歌曲缓存失败", e)
            }
        }
    }
    
    /**
     * 创建新歌单
     */
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                // 先创建歌单对象
                val newPlaylist = Playlist(
                    id = System.currentTimeMillis(),
                    name = name,
                    tracks = mutableListOf(),
                    dateCreated = System.currentTimeMillis(),
                    dateModified = System.currentTimeMillis()
                )
                
                // 立即添加到列表中，提供即时反馈
                val currentPlaylists = _playlists.value.toMutableList()
                currentPlaylists.add(newPlaylist)
                _playlists.value = currentPlaylists.toList()
                
                // 然后异步保存到数据库
                playlistManager.savePlaylist(newPlaylist)
                
                // 重新加载确保数据一致性（可能会调整顺序等）
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "创建歌单失败", e)
                e.printStackTrace()
                // 出错时重新加载
                loadPlaylists()
            }
        }
    }
    
    /**
     * 删除歌单
     */
    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                // 先立即从列表中移除，提供即时反馈
                val currentPlaylists = _playlists.value.toMutableList()
                val removed = currentPlaylists.removeAll { it.id == playlistId }
                if (removed) {
                    _playlists.value = currentPlaylists.toList()
                }
                
                // 如果删除的是当前播放列表，清空当前播放列表
                if (_currentPlaylist.value?.id == playlistId) {
                    _currentPlaylist.value = null
                    _currentTrack.value = null
                }
                
                // 然后从数据库删除
                playlistManager.deletePlaylistById(playlistId)
                // 重新加载确保数据一致性
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "删除歌单失败", e)
                e.printStackTrace()
                // 出错时重新加载
                loadPlaylists()
            }
        }
    }
    
    /**
     * 重命名歌单
     */
    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            try {
                playlistManager.renamePlaylist(playlistId, newName)
                loadPlaylists()
                // 如果重命名的是当前播放列表，更新当前播放列表
                if (_currentPlaylist.value?.id == playlistId) {
                    val current = _currentPlaylist.value
                    if (current != null) {
                        current.name = newName
                        _currentPlaylist.value = current
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "重命名歌单失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 向歌单添加歌曲
     * 优化：只更新当前歌单，不重新加载所有歌单
     */
    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            try {
                playlistManager.addTrackToPlaylist(playlistId, track)
                
                // 只更新当前歌单，而不是重新加载所有歌单
                val currentPlaylist = _playlists.value.find { it.id == playlistId }
                if (currentPlaylist != null) {
                    // 检查是否已存在，避免重复添加
                    if (!currentPlaylist.tracks.any { it.id == track.id }) {
                        // 创建新的 tracks 列表，添加新歌曲
                        val newTracks = currentPlaylist.tracks.toMutableList()
                        newTracks.add(track)
                        
                        // 创建新的 Playlist 对象，确保引用变化
                        val updatedPlaylist = Playlist(
                            id = currentPlaylist.id,
                            name = currentPlaylist.name,
                            tracks = newTracks,
                            dateCreated = currentPlaylist.dateCreated,
                            dateModified = System.currentTimeMillis()
                        )
                        
                        // 创建新的列表引用，触发 StateFlow 更新
                        // 使用 toList() 确保创建新列表，触发重组
                        val newPlaylists = _playlists.value.map { 
                            if (it.id == playlistId) updatedPlaylist else it
                        }.toList()
                        _playlists.value = newPlaylists
                        
                        // 如果这是当前播放的歌单，更新当前播放列表
                        if (_currentPlaylist.value?.id == playlistId) {
                            _currentPlaylist.value = updatedPlaylist
                        }
                    } else {
                        // 歌曲已存在，不需要更新
                    }
                } else {
                    // 如果找不到，才重新加载所有歌单
                    loadPlaylists()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "添加歌曲到歌单失败", e)
                e.printStackTrace()
                // 出错时重新加载
                loadPlaylists()
            }
        }
    }
    
    /**
     * 从歌单移除歌曲
     * 优化：只更新当前歌单，不重新加载所有歌单
     */
    fun removeTrackFromPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            try {
                playlistManager.removeTrackFromPlaylist(playlistId, track)
                
                // 只更新当前歌单，而不是重新加载所有歌单
                val currentPlaylist = _playlists.value.find { it.id == playlistId }
                if (currentPlaylist != null) {
                    // 创建新的 tracks 列表，移除指定歌曲
                    val newTracks = currentPlaylist.tracks.filter { it.id != track.id }.toMutableList()
                    
                    // 创建新的 Playlist 对象，确保引用变化
                    val updatedPlaylist = Playlist(
                        id = currentPlaylist.id,
                        name = currentPlaylist.name,
                        tracks = newTracks,
                        dateCreated = currentPlaylist.dateCreated,
                        dateModified = System.currentTimeMillis()
                    )
                    
                    // 创建新的列表引用，触发 StateFlow 更新
                    // 使用 toList() 确保创建新列表，触发重组
                    val newPlaylists = _playlists.value.map { 
                        if (it.id == playlistId) updatedPlaylist else it
                    }.toList()
                    _playlists.value = newPlaylists
                    
                    // 如果这是当前播放的歌单，更新当前播放列表
                    if (_currentPlaylist.value?.id == playlistId) {
                        _currentPlaylist.value = updatedPlaylist
                    }
                } else {
                    // 如果找不到，才重新加载所有歌单
                    loadPlaylists()
                }
                // 如果移除的是当前播放的歌曲，需要处理
                if (_currentTrack.value?.id == track.id && _currentPlaylist.value?.id == playlistId) {
                    // 如果歌单中还有其他歌曲，播放下一首
                    val updatedPlaylist = playlists.value.find { it.id == playlistId }
                    if (updatedPlaylist != null && updatedPlaylist.tracks.isNotEmpty()) {
                        val currentIndex = updatedPlaylist.tracks.indexOfFirst { it.id == track.id }
                        if (currentIndex >= 0) {
                            val nextIndex = if (currentIndex < updatedPlaylist.tracks.size - 1) {
                                currentIndex
                            } else {
                                maxOf(0, updatedPlaylist.tracks.size - 1)
                            }
                            if (nextIndex >= 0 && nextIndex < updatedPlaylist.tracks.size) {
                                setPlaylist(updatedPlaylist, nextIndex)
                            }
                        }
                    } else {
                        // 歌单为空，停止播放
                        _currentTrack.value = null
                        _currentPlaylist.value = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "从歌单移除歌曲失败", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 获取所有歌曲（用于添加到歌单）
     * 使用缓存，避免重复加载
     */
    suspend fun getAllTracks(): List<Track> {
        return withContext(Dispatchers.IO) {
            try {
                // 如果缓存已初始化，直接返回缓存
                if (allTracksCacheInitialized && _allTracksCache.value.isNotEmpty()) {
                    return@withContext _allTracksCache.value
                }
                
                // 否则从数据库加载
                val allPlaylists = playlistManager.getAllPlaylists()
                val allMusicPlaylist = allPlaylists.find { it.name == "所有音乐" }
                val tracks = allMusicPlaylist?.tracks ?: emptyList()
                
                // 更新缓存
                _allTracksCache.value = tracks
                allTracksCacheInitialized = true
                
                tracks
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "获取所有歌曲失败", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * 获取所有歌曲的 Flow（用于 UI 观察）
     */
    val allTracks: StateFlow<List<Track>> = _allTracksCache
    
    /**
     * 获取播放历史（Flow形式）
     */
    fun getPlayHistory(): Flow<List<Pair<Track, com.b230408.musicplayer.database.entity.PlayHistoryEntity>>> {
        return playHistoryManager.getAllHistory()
    }
    
    /**
     * 获取最近的播放历史
     */
    suspend fun getRecentHistory(limit: Int = 50): List<Pair<Track, com.b230408.musicplayer.database.entity.PlayHistoryEntity>> {
        return playHistoryManager.getRecentHistory(limit)
    }
    
    /**
     * 清空播放历史
     */
    fun clearPlayHistory() {
        viewModelScope.launch {
            playHistoryManager.clearAllHistory()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        musicController.unbindService()
    }
}
