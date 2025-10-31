package com.b230408.musicplayer.lyrics.player

import com.b230408.musicplayer.lyrics.model.LyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 歌词播放器
 * 负责歌词滚动显示和同步
 */
class LyricPlayer {
    
    private var lyrics: List<LyricLine> = emptyList()
    private val currentLineFlow = MutableStateFlow<LyricLine?>(null)
    private val progressFlow = MutableStateFlow(0f)
    
    /**
     * 设置歌词数据
     */
    fun setLyrics(lyrics: List<LyricLine>) {
        this.lyrics = lyrics.sortedBy { it.timeStamp }
    }
    
    /**
     * 根据播放位置更新当前歌词行
     */
    fun updatePosition(position: Long) {
        if (lyrics.isEmpty()) {
            currentLineFlow.value = null
            progressFlow.value = 0f
            return
        }
        
        // 找到当前应该显示的歌词行
        var currentLine: LyricLine? = null
        var nextLine: LyricLine? = null
        
        for (i in lyrics.indices) {
            val line = lyrics[i]
            if (line.timeStamp <= position) {
                currentLine = line
                if (i < lyrics.size - 1) {
                    nextLine = lyrics[i + 1]
                }
            } else {
                break
            }
        }
        
        currentLineFlow.value = currentLine
        
        // 计算当前行的进度（用于高亮显示）
        if (currentLine != null && nextLine != null) {
            val duration = nextLine.timeStamp - currentLine.timeStamp
            val elapsed = position - currentLine.timeStamp
            progressFlow.value = if (duration > 0) {
                (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            progressFlow.value = 0f
        }
    }
    
    /**
     * 获取当前歌词行
     */
    fun getCurrentLine(): LyricLine? {
        return currentLineFlow.value
    }
    
    /**
     * 获取当前歌词行的Flow
     */
    fun getCurrentLineFlow(): StateFlow<LyricLine?> {
        return currentLineFlow
    }
    
    /**
     * 获取进度Flow
     */
    fun getProgressFlow(): StateFlow<Float> {
        return progressFlow
    }
    
    /**
     * 获取所有歌词行
     */
    fun getAllLyrics(): List<LyricLine> {
        return lyrics
    }
    
    /**
     * 获取当前行在列表中的索引
     */
    fun getCurrentLineIndex(): Int {
        val current = currentLineFlow.value ?: return -1
        return lyrics.indexOf(current)
    }
    
    /**
     * 清除歌词
     */
    fun clear() {
        lyrics = emptyList()
        currentLineFlow.value = null
        progressFlow.value = 0f
    }
}
