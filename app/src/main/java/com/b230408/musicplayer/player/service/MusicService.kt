package com.b230408.musicplayer.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.b230408.musicplayer.MainActivity
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.utils.AssetsUtils
import com.b230408.musicplayer.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 音乐播放服务
 * 使用 Media3 ExoPlayer 进行后台播放
 */
class MusicService : Service() {
    
    private val binder = MusicBinder()
    private var exoPlayer: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentTrack: Track? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 播放状态监听器列表
    private val playbackStateListeners = mutableListOf<PlaybackStateListener>()
    
    // 进度更新Handler
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializePlayer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 状态恢复逻辑
        if (intent?.hasExtra("restore_state") == true) {
            val trackId = intent.getLongExtra("track_id", -1L)
            val position = intent.getLongExtra("position", 0L)
            val playbackMode = intent.getStringExtra("playback_mode")
            // 这里可以恢复播放状态，但需要从Controller获取Track
            // 暂时跳过，由Controller管理
        }
        
        when (intent?.action) {
            Constants.PlayerAction.ACTION_PLAY -> play()
            Constants.PlayerAction.ACTION_PAUSE -> pause()
            Constants.PlayerAction.ACTION_STOP -> stop()
            Constants.PlayerAction.ACTION_NEXT -> {
                // 由Controller处理
            }
            Constants.PlayerAction.ACTION_PREVIOUS -> {
                // 由Controller处理
            }
            Constants.PlayerAction.ACTION_SEEK_TO -> {
                val position = intent.getLongExtra("position", 0L)
                seekTo(position)
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        releaseAudioFocus()
    }
    
    /**
     * 初始化播放器
     */
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                android.util.Log.d("MusicService", "ExoPlayer 准备完成，时长: ${duration}ms")
                                // 通知时长更新
                                notifyProgressChanged(getCurrentPosition(), getDuration())
                            }
                            Player.STATE_ENDED -> {
                                android.util.Log.d("MusicService", "播放结束")
                                notifyPlaybackStateChanged(false)
                                notifyPlaybackEnded()
                            }
                            Player.STATE_BUFFERING -> {
                                android.util.Log.d("MusicService", "缓冲中...")
                            }
                            Player.STATE_IDLE -> {
                                android.util.Log.d("MusicService", "空闲状态")
                            }
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d("MusicService", "播放状态改变: $isPlaying")
                        updateNotification()
                        notifyPlaybackStateChanged(isPlaying)
                        if (isPlaying) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("MusicService", "播放错误", error)
                        error.printStackTrace()
                    }
                })
            }
    }
    
    /**
     * 播放指定音轨
     */
    fun playTrack(track: Track) {
        android.util.Log.d("MusicService", "开始播放音轨: ${track.getDisplayTitle()}, URI: ${track.uri}, Path: ${track.path}")
        currentTrack = track
        
        // 先请求音频焦点
        if (!requestAudioFocus()) {
            android.util.Log.w("MusicService", "音频焦点请求失败，但继续尝试播放")
            // 即使音频焦点请求失败，也尝试播放（某些设备可能允许）
        }
        
        exoPlayer?.apply {
            try {
                // 停止当前播放
                stop()
                
                // 如果是 assets 文件，需要先复制到临时文件
                val playUri = if (track.path.startsWith("assets://")) {
                    val fileName = track.fileName
                    val assetsUri = AssetsUtils.getAssetsMusicUri(this@MusicService, fileName)
                    if (assetsUri != null) {
                        android.util.Log.d("MusicService", "使用 assets 临时文件 URI: $assetsUri")
                        assetsUri
                    } else {
                        android.util.Log.e("MusicService", "无法获取 assets 文件 URI，使用原始 URI")
                        track.uri
                    }
                } else {
                    // 优先使用URI，如果URI无效则尝试从路径构建
                    try {
                        // 验证URI是否可访问
                        val uri = track.uri
                        android.util.Log.d("MusicService", "使用Track URI: $uri")
                        uri
                    } catch (e: Exception) {
                        android.util.Log.w("MusicService", "URI访问失败，尝试使用路径: ${track.path}", e)
                        // 如果URI失败，尝试从路径构建FileProvider URI
                        try {
                            val file = java.io.File(track.path)
                            if (file.exists() && file.canRead()) {
                                android.net.Uri.fromFile(file)
                            } else {
                                android.util.Log.e("MusicService", "文件不存在或不可读: ${track.path}")
                                track.uri // 降级使用原始URI
                            }
                        } catch (e2: Exception) {
                            android.util.Log.e("MusicService", "从路径构建URI失败", e2)
                            track.uri // 降级使用原始URI
                        }
                    }
                }
                
                val mediaItem = MediaItem.fromUri(playUri)
                setMediaItem(mediaItem)
                prepare()
                
                // 添加临时监听器，等待准备完成后播放
                val readyListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                android.util.Log.d("MusicService", "ExoPlayer 准备完成，开始播放")
                                // 准备完成后自动播放
                                play()
                                removeListener(this)
                            }
                            Player.STATE_IDLE, Player.STATE_BUFFERING -> {
                                // 加载中，等待
                            }
                            Player.STATE_ENDED -> {
                                removeListener(this)
                            }
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("MusicService", "播放错误", error)
                        error.printStackTrace()
                        removeListener(this)
                    }
                }
                addListener(readyListener)
                
                // 如果已经准备好，立即播放
                if (playbackState == Player.STATE_READY) {
                    android.util.Log.d("MusicService", "ExoPlayer 已准备好，立即播放")
                    play()
                    removeListener(readyListener)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "播放音轨失败", e)
                e.printStackTrace()
            }
        } ?: run {
            android.util.Log.e("MusicService", "ExoPlayer 为空，无法播放")
        }
        
        updateNotification()
        startProgressUpdates()
        notifyTrackChanged(track)
    }
    
    /**
     * 播放
     */
    fun play() {
        // 如果已经有焦点请求，直接播放（避免重复请求导致焦点丢失）
        // 如果没有焦点请求，先请求焦点
        if (audioFocusRequest == null) {
            android.util.Log.d("MusicService", "没有音频焦点，请求焦点")
            if (!requestAudioFocus()) {
                android.util.Log.w("MusicService", "无法获得音频焦点，播放失败")
                return
            }
        } else {
            android.util.Log.d("MusicService", "已有音频焦点，直接播放")
        }
        
        exoPlayer?.play()
        updateNotification()
    }
    
    /**
     * 暂停
     */
    fun pause() {
        exoPlayer?.pause()
        // 不释放音频焦点，保持焦点以便快速恢复播放
        // 只有在 stop() 时才释放焦点
        updateNotification()
    }
    
    /**
     * 停止
     */
    fun stop() {
        exoPlayer?.stop()
        releaseAudioFocus()
        stopProgressUpdates()
        updateNotification()
    }
    
    /**
     * 快进（向前跳转10秒）
     */
    fun seekForward() {
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val newPosition = (currentPosition + 10000).coerceAtMost(player.duration)
            seekTo(newPosition)
        }
    }
    
    /**
     * 倒退（向后跳转10秒）
     */
    fun seekBackward() {
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val newPosition = (currentPosition - 10000).coerceAtLeast(0)
            seekTo(newPosition)
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    /**
     * 获取总时长
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }
    
    /**
     * 获取当前播放的音轨
     */
    fun getCurrentTrack(): Track? {
        return currentTrack
    }
    
    /**
     * 获取播放器实例（供Controller使用）
     */
    fun getPlayer(): ExoPlayer? {
        return exoPlayer
    }
    
    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { manager ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 如果已经有音频焦点请求，直接返回 true（假设已经有焦点）
                    // 这样可以避免重复请求导致焦点丢失
                    if (audioFocusRequest != null) {
                        android.util.Log.d("MusicService", "已经有音频焦点请求，跳过重新请求")
                        return true
                    }
                    
                    // 创建新的音频焦点请求
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setOnAudioFocusChangeListener { focusChange ->
                            android.util.Log.d("MusicService", "音频焦点变化: $focusChange")
                            when (focusChange) {
                                AudioManager.AUDIOFOCUS_LOSS -> {
                                    android.util.Log.d("MusicService", "音频焦点永久丢失，暂停播放")
                                    // 永久丢失焦点，暂停播放但不释放焦点（等待重新获得）
                                    exoPlayer?.pause()
                                    notifyPlaybackStateChanged(false)
                                    updateNotification()
                                }
                                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                    android.util.Log.d("MusicService", "音频焦点临时丢失，暂停播放")
                                    // 临时丢失焦点，暂停播放
                                    exoPlayer?.pause()
                                    notifyPlaybackStateChanged(false)
                                    updateNotification()
                                }
                                AudioManager.AUDIOFOCUS_GAIN -> {
                                    android.util.Log.d("MusicService", "获得音频焦点")
                                    // 获得焦点时，如果之前正在播放，则恢复播放
                                    // 注意：这里不自动播放，由用户操作触发
                                }
                            }
                        }
                        .build()
                    
                    val result = manager.requestAudioFocus(audioFocusRequest!!)
                    android.util.Log.d("MusicService", "音频焦点请求结果: $result (${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "已授予" else "被拒绝"})")
                    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                } else {
                    @Suppress("DEPRECATION")
                    val result = manager.requestAudioFocus(
                        { focusChange ->
                            android.util.Log.d("MusicService", "音频焦点变化 (旧API): $focusChange")
                            when (focusChange) {
                                AudioManager.AUDIOFOCUS_LOSS -> {
                                    android.util.Log.d("MusicService", "音频焦点永久丢失，暂停播放")
                                    exoPlayer?.pause()
                                    notifyPlaybackStateChanged(false)
                                    updateNotification()
                                }
                                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                    android.util.Log.d("MusicService", "音频焦点临时丢失，暂停播放")
                                    exoPlayer?.pause()
                                    notifyPlaybackStateChanged(false)
                                    updateNotification()
                                }
                                AudioManager.AUDIOFOCUS_GAIN -> {
                                    android.util.Log.d("MusicService", "获得音频焦点")
                                }
                            }
                        },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                    )
                    android.util.Log.d("MusicService", "音频焦点请求结果 (旧API): $result")
                    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "请求音频焦点异常", e)
                e.printStackTrace()
                return false
            }
        }
        android.util.Log.w("MusicService", "AudioManager 为空")
        return false
    }
    
    /**
     * 释放音频焦点
     */
    private fun releaseAudioFocus() {
        audioFocusRequest?.let { request ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager?.abandonAudioFocusRequest(request)
            }
            audioFocusRequest = null
        } ?: run {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放器通知"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val track = currentTrack ?: return
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "暂停",
                createPendingIntent(Constants.PlayerAction.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "播放",
                createPendingIntent(Constants.PlayerAction.ACTION_PLAY)
            )
        }
        
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(track.getDisplayTitle())
            .setContentText(track.getDisplayArtist())
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            // MediaStyle 需要额外的依赖，暂时移除
            // .setStyle(
            //     NotificationCompat.MediaStyle()
            //         .setShowActionsInCompactView(0)
            // )
            .build()
        
        // Android 14+ 需要指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 创建PendingIntent
     */
    private fun createPendingIntent(action: String): PendingIntent {
        val requestCode = when (action) {
            Constants.PlayerAction.ACTION_PLAY -> 1
            Constants.PlayerAction.ACTION_PAUSE -> 2
            Constants.PlayerAction.ACTION_STOP -> 3
            Constants.PlayerAction.ACTION_NEXT -> 4
            Constants.PlayerAction.ACTION_PREVIOUS -> 5
            Constants.PlayerAction.ACTION_SEEK_TO -> 6
            else -> 0
        }
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MusicService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * 释放播放器
     */
    private fun releasePlayer() {
        stopProgressUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                notifyProgressChanged(getCurrentPosition(), getDuration())
                progressHandler.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        progressHandler.post(progressUpdateRunnable!!)
    }
    
    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let {
            progressHandler.removeCallbacks(it)
            progressUpdateRunnable = null
        }
    }
    
    /**
     * 添加播放状态监听器
     */
    fun addPlaybackStateListener(listener: PlaybackStateListener) {
        playbackStateListeners.add(listener)
    }
    
    /**
     * 移除播放状态监听器
     */
    fun removePlaybackStateListener(listener: PlaybackStateListener) {
        playbackStateListeners.remove(listener)
    }
    
    /**
     * 通知播放状态改变
     */
    private fun notifyPlaybackStateChanged(isPlaying: Boolean) {
        playbackStateListeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }
    
    /**
     * 通知进度改变
     */
    private fun notifyProgressChanged(position: Long, duration: Long) {
        playbackStateListeners.forEach { it.onProgressChanged(position, duration) }
    }
    
    /**
     * 通知音轨改变
     */
    private fun notifyTrackChanged(track: Track) {
        playbackStateListeners.forEach { it.onTrackChanged(track) }
    }
    
    /**
     * 通知播放结束
     */
    private fun notifyPlaybackEnded() {
        playbackStateListeners.forEach { it.onPlaybackEnded() }
    }
    
    /**
     * 播放状态监听器接口
     */
    interface PlaybackStateListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressChanged(position: Long, duration: Long)
        fun onTrackChanged(track: Track)
        fun onPlaybackEnded() // 播放结束回调
    }
    
    /**
     * Binder类，用于Activity绑定Service
     */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
}
