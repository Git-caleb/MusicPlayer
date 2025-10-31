package com.b230408.musicplayer.lyrics.model

/**
 * 歌词行数据模型
 */
data class LyricLine(
    /**
     * 时间戳（毫秒）
     */
    val timeStamp: Long,
    
    /**
     * 歌词内容
     */
    val text: String
)
