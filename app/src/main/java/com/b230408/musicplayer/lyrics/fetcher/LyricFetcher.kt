package com.b230408.musicplayer.lyrics.fetcher

import com.b230408.musicplayer.lyrics.model.LyricLine
import com.b230408.musicplayer.metadata.model.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 歌词获取器
 * 从网络爬取歌词
 */
object LyricFetcher {
    
    /**
     * 从网络获取歌词
     * 这里提供一个示例实现，实际项目中可能需要对接具体的歌词API
     */
    suspend fun fetchLyrics(metadata: Metadata): List<LyricLine>? = withContext(Dispatchers.IO) {
        val title = metadata.title ?: return@withContext null
        val artist = metadata.artist ?: return@withContext null
        
        return@withContext try {
            // 示例：使用通用的歌词API（这里使用示例URL，实际需要替换为真实的API）
            fetchLyricsFromAPI(title, artist)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从API获取歌词
     */
    private suspend fun fetchLyricsFromAPI(title: String, artist: String): List<LyricLine>? {
        return try {
            // 示例URL（实际项目中需要替换为真实的歌词API）
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val urlString = "https://api.lyrics.example.com/lyrics?title=$encodedTitle&artist=$encodedArtist"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = reader.use { it.readText() }
                
                // 解析歌词（示例：LRC格式）
                parseLRCFormat(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 解析LRC格式歌词
     */
    private fun parseLRCFormat(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lrcText.lineSequence().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val milliseconds = match.groupValues[3].toInt()
                val text = match.groupValues[4].trim()
                
                // 转换为毫秒时间戳
                val timeStamp = (minutes * 60 + seconds) * 1000L + milliseconds * 10L
                
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timeStamp = timeStamp, text = text))
                }
            }
        }
        
        return lines.sortedBy { it.timeStamp }
    }
    
    /**
     * 从本地文件读取歌词
     */
    suspend fun loadLyricsFromFile(filePath: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = java.io.File(filePath)
            if (file.exists() && file.isFile) {
                val content = file.readText(charset = Charsets.UTF_8)
                parseLRCFormat(content)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 查找匹配的歌词文件路径（与音频文件同名，扩展名为.lrc）
     */
    fun findLyricsFile(audioFilePath: String): String? {
        val audioFile = java.io.File(audioFilePath)
        if (!audioFile.exists()) return null
        
        val parent = audioFile.parent ?: return null
        val nameWithoutExt = audioFile.nameWithoutExtension
        
        // 尝试查找.lrc文件
        val lrcFile = java.io.File(parent, "$nameWithoutExt.lrc")
        return if (lrcFile.exists() && lrcFile.isFile) {
            lrcFile.absolutePath
        } else {
            null
        }
    }
}
