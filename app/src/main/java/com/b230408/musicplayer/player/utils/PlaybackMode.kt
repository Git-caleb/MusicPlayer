package com.b230408.musicplayer.player.utils

/**
 * 播放模式枚举
 */
enum class PlaybackMode {
    /**
     * 顺序循环 - 按顺序播放，播放完最后一首后回到第一首
     */
    SEQUENTIAL,
    
    /**
     * 单曲循环 - 重复播放当前歌曲
     */
    REPEAT_ONE,
    
    /**
     * 随机播放 - 随机选择歌曲播放
     */
    SHUFFLE
}
