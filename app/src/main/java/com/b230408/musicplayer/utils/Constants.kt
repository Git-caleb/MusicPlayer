package com.b230408.musicplayer.utils

/**
 * 应用常量
 */
object Constants {
    /**
     * 支持的音乐文件格式
     */
    val SUPPORTED_AUDIO_FORMATS = listOf("mp3", "amr", "aac", "ogg", "m4a", "wav", "flac")
    
    /**
     * 默认扫描目录（示例）
     */
    const val DEFAULT_MUSIC_DIR = "/storage/emulated/0/Music"
    
    /**
     * SharedPreferences 键名
     */
    object Prefs {
        const val PREF_NAME = "music_player_prefs"
        const val KEY_LAST_PLAYED_TRACK_ID = "last_played_track_id"
        const val KEY_LAST_PLAYBACK_POSITION = "last_playback_position"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    }
    
    /**
     * 播放器动作 Intent Extra
     */
    object PlayerAction {
        const val ACTION_PLAY = "com.b230408.musicplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.b230408.musicplayer.ACTION_PAUSE"
        const val ACTION_STOP = "com.b230408.musicplayer.ACTION_STOP"
        const val ACTION_NEXT = "com.b230408.musicplayer.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.b230408.musicplayer.ACTION_PREVIOUS"
        const val ACTION_SEEK_TO = "com.b230408.musicplayer.ACTION_SEEK_TO"
    }
    
    /**
     * 权限请求码
     */
    object Permissions {
        const val REQUEST_STORAGE_PERMISSION = 1001
        const val REQUEST_AUDIO_PERMISSION = 1002
    }
    
    /**
     * Notification Channel
     */
    const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
    const val NOTIFICATION_CHANNEL_NAME = "音乐播放通知"
    const val NOTIFICATION_ID = 1001
}
