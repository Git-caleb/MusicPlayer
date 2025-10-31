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
                                notifyPlaybackStateChanged(true)
                            }
                            Player.STATE_ENDED -> {
                                notifyPlaybackStateChanged(false)
                            }
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateNotification()
                        notifyPlaybackStateChanged(isPlaying)
                        if (isPlaying) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }
                })
            }
    }
    
    /**
     * 播放指定音轨
     */
    fun playTrack(track: Track) {
        currentTrack = track
        requestAudioFocus()
        
        exoPlayer?.apply {
            val mediaItem = MediaItem.fromUri(track.uri)
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        
        updateNotification()
        startProgressUpdates()
        notifyTrackChanged(track)
    }
    
    /**
     * 播放
     */
    fun play() {
        requestAudioFocus()
        exoPlayer?.play()
        updateNotification()
    }
    
    /**
     * 暂停
     */
    fun pause() {
        exoPlayer?.pause()
        releaseAudioFocus()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> pause()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                            AudioManager.AUDIOFOCUS_GAIN -> play()
                        }
                    }
                    .build()
                
                return manager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = manager.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> pause()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                            AudioManager.AUDIOFOCUS_GAIN -> play()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }
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
        return PendingIntent.getService(
            this,
            0,
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
     * 播放状态监听器接口
     */
    interface PlaybackStateListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressChanged(position: Long, duration: Long)
        fun onTrackChanged(track: Track)
    }
    
    /**
     * Binder类，用于Activity绑定Service
     */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
}
