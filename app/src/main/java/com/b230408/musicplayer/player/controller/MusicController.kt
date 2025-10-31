package com.b230408.musicplayer.player.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.player.service.MusicService
import com.b230408.musicplayer.player.utils.PlaybackMode
import com.b230408.musicplayer.playlist.model.Playlist
import kotlin.random.Random

/**
 * 音乐播放控制器
 * 管理播放逻辑：播放/暂停/快进/倒退/播放模式/上一首下一首
 */
class MusicController private constructor(private val context: Context) {
    
    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var currentPlaylist: Playlist? = null
    private var currentIndex: Int = -1
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL
    private val playedIndices = mutableListOf<Int>() // 用于随机播放
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            
            // 添加播放状态监听
            musicService?.addPlaybackStateListener(playbackStateListener)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }
    
    private val playbackStateListener = object : MusicService.PlaybackStateListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            // 处理播放状态改变
        }
        
        override fun onProgressChanged(position: Long, duration: Long) {
            // 处理进度改变
        }
        
        override fun onTrackChanged(track: Track) {
            // 处理音轨改变
        }
    }
    
    init {
        try {
            bindService()
        } catch (e: Exception) {
            // 绑定服务失败不应该导致崩溃
            e.printStackTrace()
        }
    }
    
    /**
     * 绑定服务
     */
    private fun bindService() {
        try {
            val intent = Intent(context, MusicService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
            // 绑定失败不应该导致崩溃
        }
    }
    
    /**
     * 解绑服务
     */
    fun unbindService() {
        if (isServiceBound) {
            musicService?.removePlaybackStateListener(playbackStateListener)
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }
    
    /**
     * 设置播放列表并开始播放
     */
    fun setPlaylist(playlist: Playlist, startIndex: Int = 0) {
        try {
            if (playlist.tracks.isEmpty()) {
                return
            }
            currentPlaylist = playlist
            currentIndex = startIndex.coerceIn(0, playlist.tracks.size - 1)
            playedIndices.clear()
            playCurrentTrack()
        } catch (e: Exception) {
            e.printStackTrace()
            // 设置播放列表失败不应该导致崩溃
        }
    }
    
    /**
     * 播放指定音轨
     */
    fun playTrack(track: Track) {
        musicService?.playTrack(track)
    }
    
    /**
     * 播放当前音轨
     */
    private fun playCurrentTrack() {
        val track = getCurrentTrack() ?: return
        musicService?.playTrack(track)
    }
    
    /**
     * 播放
     */
    fun play() {
        if (getCurrentTrack() == null && currentPlaylist != null) {
            playCurrentTrack()
        } else {
            musicService?.play()
        }
    }
    
    /**
     * 暂停
     */
    fun pause() {
        musicService?.pause()
    }
    
    /**
     * 停止
     */
    fun stop() {
        musicService?.stop()
        currentIndex = -1
    }
    
    /**
     * 快进（向前跳转10秒）
     */
    fun seekForward() {
        musicService?.seekForward()
    }
    
    /**
     * 倒退（向后跳转10秒）
     */
    fun seekBackward() {
        musicService?.seekBackward()
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        musicService?.seekTo(position)
    }
    
    /**
     * 上一首
     */
    fun previous() {
        val playlist = currentPlaylist ?: return
        if (playlist.tracks.isEmpty()) return
        
        when (playbackMode) {
            PlaybackMode.SEQUENTIAL -> {
                currentIndex = if (currentIndex > 0) {
                    currentIndex - 1
                } else {
                    playlist.tracks.size - 1 // 循环到最后一首
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                // 单曲循环，不改变索引
            }
            PlaybackMode.SHUFFLE -> {
                // 随机选择上一首（简化处理，实际可以维护历史记录）
                currentIndex = Random.nextInt(playlist.tracks.size)
            }
        }
        
        playCurrentTrack()
    }
    
    /**
     * 下一首
     */
    fun next() {
        val playlist = currentPlaylist ?: return
        if (playlist.tracks.isEmpty()) return
        
        when (playbackMode) {
            PlaybackMode.SEQUENTIAL -> {
                currentIndex = if (currentIndex < playlist.tracks.size - 1) {
                    currentIndex + 1
                } else {
                    0 // 循环到第一首
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                // 单曲循环，重新播放当前歌曲
                playCurrentTrack()
                return
            }
            PlaybackMode.SHUFFLE -> {
                // 随机选择下一首
                if (playedIndices.size >= playlist.tracks.size) {
                    playedIndices.clear()
                }
                var nextIndex: Int
                do {
                    nextIndex = Random.nextInt(playlist.tracks.size)
                } while (nextIndex == currentIndex && playlist.tracks.size > 1)
                
                currentIndex = nextIndex
                playedIndices.add(currentIndex)
            }
        }
        
        playCurrentTrack()
    }
    
    /**
     * 设置播放模式
     */
    fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        if (mode == PlaybackMode.SHUFFLE) {
            playedIndices.clear()
            if (currentIndex >= 0) {
                playedIndices.add(currentIndex)
            }
        }
    }
    
    /**
     * 获取当前播放模式
     */
    fun getPlaybackMode(): PlaybackMode {
        return playbackMode
    }
    
    /**
     * 获取当前播放的音轨
     */
    fun getCurrentTrack(): Track? {
        val playlist = currentPlaylist ?: return null
        if (currentIndex < 0 || currentIndex >= playlist.tracks.size) {
            return null
        }
        return playlist.tracks[currentIndex]
    }
    
    /**
     * 获取当前索引
     */
    fun getCurrentIndex(): Int {
        return currentIndex
    }
    
    /**
     * 获取当前播放列表
     */
    fun getCurrentPlaylist(): Playlist? {
        return currentPlaylist
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return musicService?.isPlaying() ?: false
    }
    
    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Long {
        return musicService?.getCurrentPosition() ?: 0L
    }
    
    /**
     * 获取总时长
     */
    fun getDuration(): Long {
        return musicService?.getDuration() ?: 0L
    }
    
    /**
     * 获取服务实例（用于添加监听器等）
     */
    fun getService(): MusicService? {
        return musicService
    }
    
    companion object {
        @Volatile
        private var INSTANCE: MusicController? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): MusicController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
