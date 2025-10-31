package com.b230408.musicplayer.lyrics.editor

import com.b230408.musicplayer.lyrics.model.LyricLine
import java.io.File

/**
 * 歌词编辑器
 * 手动添加时间戳制作歌词
 */
class LyricEditor {
    
    private val lyrics = mutableListOf<LyricLine>()
    
    /**
     * 添加歌词行（带时间戳）
     */
    fun addLyricLine(timeStamp: Long, text: String) {
        lyrics.add(LyricLine(timeStamp = timeStamp, text = text))
        // 按时间戳排序
        lyrics.sortBy { it.timeStamp }
    }
    
    /**
     * 添加歌词行（不带时间戳，使用当前播放位置）
     */
    fun addLyricLineAtCurrentPosition(currentPosition: Long, text: String) {
        addLyricLine(currentPosition, text)
    }
    
    /**
     * 移除指定索引的歌词行
     */
    fun removeLyricLine(index: Int): Boolean {
        return if (index in lyrics.indices) {
            lyrics.removeAt(index)
            true
        } else {
            false
        }
    }
    
    /**
     * 更新歌词行
     */
    fun updateLyricLine(index: Int, timeStamp: Long? = null, text: String? = null): Boolean {
        return if (index in lyrics.indices) {
            val current = lyrics[index]
            val updated = current.copy(
                timeStamp = timeStamp ?: current.timeStamp,
                text = text ?: current.text
            )
            lyrics[index] = updated
            // 重新排序
            lyrics.sortBy { it.timeStamp }
            true
        } else {
            false
        }
    }
    
    /**
     * 获取所有歌词行
     */
    fun getAllLyrics(): List<LyricLine> {
        return lyrics.toList()
    }
    
    /**
     * 获取指定索引的歌词行
     */
    fun getLyricLine(index: Int): LyricLine? {
        return lyrics.getOrNull(index)
    }
    
    /**
     * 清除所有歌词
     */
    fun clear() {
        lyrics.clear()
    }
    
    /**
     * 保存歌词到LRC格式文件
     */
    fun saveToLRCFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val content = buildString {
                lyrics.forEach { line ->
                    val minutes = line.timeStamp / 60000
                    val seconds = (line.timeStamp % 60000) / 1000
                    val milliseconds = (line.timeStamp % 1000) / 10
                    append("[%02d:%02d.%02d]%s\n".format(minutes, seconds, milliseconds, line.text))
                }
            }
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 从LRC格式文件加载歌词
     */
    fun loadFromLRCFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return false
            }
            
            clear()
            val content = file.readText(charset = Charsets.UTF_8)
            val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
            
            content.lineSequence().forEach { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val minutes = match.groupValues[1].toInt()
                    val seconds = match.groupValues[2].toInt()
                    val milliseconds = match.groupValues[3].toInt()
                    val text = match.groupValues[4].trim()
                    
                    val timeStamp = (minutes * 60 + seconds) * 1000L + milliseconds * 10L
                    
                    if (text.isNotEmpty()) {
                        lyrics.add(LyricLine(timeStamp = timeStamp, text = text))
                    }
                }
            }
            
            lyrics.sortBy { it.timeStamp }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取歌词行数
     */
    fun getLyricCount(): Int {
        return lyrics.size
    }
}
