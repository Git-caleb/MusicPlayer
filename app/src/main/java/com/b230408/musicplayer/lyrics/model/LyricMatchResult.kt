package com.b230408.musicplayer.lyrics.model

/**
 * 歌词匹配结果
 */
data class LyricMatchResult(
    /**
     * 歌词行列表
     */
    val lyricLines: List<LyricLine>,
    
    /**
     * 匹配的歌曲标题
     */
    val matchedTitle: String,
    
    /**
     * 匹配的艺术家
     */
    val matchedArtist: String,
    
    /**
     * 歌词来源
     */
    val source: LyricSource,
    
    /**
     * 匹配度（0.0-1.0）
     */
    val matchScore: Float = 1.0f
)

/**
 * 歌词来源枚举
 */
enum class LyricSource {
    /**
     * 网易云音乐
     */
    NETEASE,
    
    /**
     * QQ音乐
     */
    QQ_MUSIC,
    
    /**
     * 本地文件
     */
    LOCAL,
    
    /**
     * Assets资源
     */
    ASSETS,
    
    /**
     * 其他来源
     */
    OTHER
}

