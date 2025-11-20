package com.b230408.musicplayer.lyrics.fetcher

import android.content.Context
import android.util.Log
import com.b230408.musicplayer.lyrics.model.LyricLine
import com.b230408.musicplayer.lyrics.model.LyricMatchResult
import com.b230408.musicplayer.lyrics.model.LyricSource
import com.b230408.musicplayer.metadata.model.Metadata
import com.b230408.musicplayer.utils.AssetsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 歌词获取器
 * 支持从多个网络源自动匹配歌词
 */
object LyricFetcher {
    
    private const val TAG = "LyricFetcher"
    private const val CONNECT_TIMEOUT = 8000
    private const val READ_TIMEOUT = 10000
    
    // JSON解析器
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 从网络获取歌词（向后兼容方法）
     * 优先从本地加载，然后尝试网络匹配
     */
    suspend fun fetchLyrics(
        context: Context?,
        metadata: Metadata,
        musicFileName: String? = null
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        // 优先从 assets 读取歌词
        if (context != null && musicFileName != null) {
            val assetsLyrics = AssetsUtils.readLyricsFromAssets(context, musicFileName)
            if (assetsLyrics != null) {
                val lines = parseLRCFormat(assetsLyrics)
                if (lines.isNotEmpty()) {
                    return@withContext lines
                }
            }
        }
        
        val title = metadata.title ?: return@withContext null
        val artist = metadata.artist ?: return@withContext null
        
        // 尝试网络自动匹配
        val matchResult = fetchLyricsWithAutoMatch(context, metadata, musicFileName)
        return@withContext matchResult?.lyricLines
    }
    
    /**
     * 自动匹配歌词（多源）
     * 从多个歌词源并行搜索，返回最佳匹配结果
     */
    suspend fun fetchLyricsWithAutoMatch(
        context: Context?,
        metadata: Metadata,
        musicFileName: String? = null
    ): LyricMatchResult? = withContext(Dispatchers.IO) {
        val title = metadata.title ?: return@withContext null
        val artist = metadata.artist ?: return@withContext null
        
        Log.d(TAG, "开始自动匹配歌词: $artist - $title")
        
        // 并行从多个源搜索（QQ音乐优先，版权更全）
        val results = listOf(
            async { fetchFromQQMusic(title, artist) },
            async { fetchFromNetEase(title, artist) }
        ).awaitAll().filterNotNull()
        
        if (results.isEmpty()) {
            Log.w(TAG, "未找到匹配的歌词")
            return@withContext null
        }
        
        // 计算匹配度并排序
        // QQ音乐优先级更高：在匹配度相同时优先选择QQ音乐
        val scoredResults = results.map { result ->
            val baseScore = calculateMatchScore(title, artist, result.matchedTitle, result.matchedArtist)
            // QQ音乐给予小的优先级加成（0.01），确保在匹配度相近时优先选择
            val priorityBonus = if (result.source == LyricSource.QQ_MUSIC) 0.01f else 0f
            val finalScore = (baseScore + priorityBonus).coerceIn(0f, 1f)
            result.copy(matchScore = finalScore)
        }.sortedWith(compareByDescending<LyricMatchResult> { it.matchScore }
            .thenBy { if (it.source == LyricSource.QQ_MUSIC) 0 else 1 }) // 匹配度相同时，QQ音乐优先
        
        val bestMatch = scoredResults.first()
        Log.d(TAG, "最佳匹配: ${bestMatch.source}, 匹配度: ${bestMatch.matchScore}, 歌词行数: ${bestMatch.lyricLines.size}")
        
        return@withContext bestMatch
    }
    
    /**
     * 从网易云音乐获取歌词
     */
    private suspend fun fetchFromNetEase(title: String, artist: String): LyricMatchResult? {
        return try {
            Log.d(TAG, "尝试从网易云音乐获取歌词: $artist - $title")
            
            // 第一步：搜索歌曲ID
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&s=${URLEncoder.encode("$artist $title", "UTF-8")}&type=1&offset=0&total=true&limit=1"
            val searchResponse = makeHttpRequest(searchUrl, mapOf(
                "Referer" to "https://music.163.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            ))
            
            if (searchResponse == null) {
                Log.w(TAG, "网易云音乐搜索失败")
                return null
            }
            
            val searchData = json.decodeFromString<NetEaseSearchResponse>(searchResponse)
            val song = searchData.result?.songs?.firstOrNull() ?: run {
                Log.w(TAG, "网易云音乐未找到歌曲")
                return null
            }
            
            val songId = song.id
            val matchedTitle = song.name
            val matchedArtist = song.artists?.firstOrNull()?.name ?: artist
            
            // 第二步：获取歌词
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=-1&kv=-1&tv=-1"
            val lyricResponse = makeHttpRequest(lyricUrl, mapOf(
                "Referer" to "https://music.163.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            ))
            
            if (lyricResponse == null) {
                Log.w(TAG, "网易云音乐获取歌词失败")
                return null
            }
            
            val lyricData = json.decodeFromString<NetEaseLyricResponse>(lyricResponse)
            val lrcText = lyricData.lrc?.lyric
            
            if (lrcText.isNullOrEmpty()) {
                Log.w(TAG, "网易云音乐歌词为空")
                return null
            }
            
            val lyricLines = parseLRCFormat(lrcText)
            if (lyricLines.isEmpty()) {
                Log.w(TAG, "网易云音乐歌词解析失败")
                return null
            }
            
            Log.d(TAG, "网易云音乐获取成功，歌词行数: ${lyricLines.size}")
            LyricMatchResult(
                lyricLines = lyricLines,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                source = LyricSource.NETEASE
            )
        } catch (e: Exception) {
            Log.e(TAG, "从网易云音乐获取歌词失败", e)
            null
        }
    }
    
    /**
     * 从QQ音乐获取歌词
     */
    private suspend fun fetchFromQQMusic(title: String, artist: String): LyricMatchResult? {
        return try {
            Log.d(TAG, "尝试从QQ音乐获取歌词: $artist - $title")
            
            // 第一步：搜索歌曲
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&searchid=&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=1&w=${URLEncoder.encode("$artist $title", "UTF-8")}&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val searchResponse = makeHttpRequest(searchUrl, mapOf(
                "Referer" to "https://y.qq.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            ))
            
            if (searchResponse == null) {
                Log.w(TAG, "QQ音乐搜索失败")
                return null
            }
            
            // QQ音乐返回的JSON可能有callback包装，需要处理
            val cleanResponse = searchResponse.removePrefix("callback(").removeSuffix(")")
            val searchData = json.decodeFromString<QQMusicSearchResponse>(cleanResponse)
            val song = searchData.data?.song?.list?.firstOrNull() ?: run {
                Log.w(TAG, "QQ音乐未找到歌曲")
                return null
            }
            
            val songId = song.songid
            val matchedTitle = song.songname
            val matchedArtist = song.singer?.firstOrNull()?.name ?: artist
            
            // 第二步：获取歌词
            val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?format=json&nobase64=1&musicid=$songId&-=jsonp1&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val lyricResponse = makeHttpRequest(lyricUrl, mapOf(
                "Referer" to "https://y.qq.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            ))
            
            if (lyricResponse == null) {
                Log.w(TAG, "QQ音乐获取歌词失败")
                return null
            }
            
            // QQ音乐歌词API也可能有callback包装
            val cleanLyricResponse = lyricResponse.removePrefix("jsonp1(").removeSuffix(")")
            val lyricData = json.decodeFromString<QQMusicLyricResponse>(cleanLyricResponse)
            val lrcText = lyricData.lyric
            
            if (lrcText.isNullOrEmpty()) {
                Log.w(TAG, "QQ音乐歌词为空")
                return null
            }
            
            val lyricLines = parseLRCFormat(lrcText)
            if (lyricLines.isEmpty()) {
                Log.w(TAG, "QQ音乐歌词解析失败")
                return null
            }
            
            Log.d(TAG, "QQ音乐获取成功，歌词行数: ${lyricLines.size}")
            LyricMatchResult(
                lyricLines = lyricLines,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                source = LyricSource.QQ_MUSIC
            )
        } catch (e: Exception) {
            Log.e(TAG, "从QQ音乐获取歌词失败", e)
            null
        }
    }
    
    /**
     * 计算匹配度
     */
    private fun calculateMatchScore(
        originalTitle: String,
        originalArtist: String,
        matchedTitle: String,
        matchedArtist: String
    ): Float {
        var score = 0.0f
        
        // 标题匹配度（权重0.6）
        val titleSimilarity = calculateSimilarity(
            normalizeString(originalTitle),
            normalizeString(matchedTitle)
        )
        score += titleSimilarity * 0.6f
        
        // 艺术家匹配度（权重0.4）
        val artistSimilarity = calculateSimilarity(
            normalizeString(originalArtist),
            normalizeString(matchedArtist)
        )
        score += artistSimilarity * 0.4f
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * 计算字符串相似度（简单的编辑距离算法）
     */
    private fun calculateSimilarity(str1: String, str2: String): Float {
        if (str1 == str2) return 1.0f
        if (str1.isEmpty() || str2.isEmpty()) return 0.0f
        
        // 使用包含关系作为快速检查
        if (str1.contains(str2, ignoreCase = true) || str2.contains(str1, ignoreCase = true)) {
            return 0.8f
        }
        
        // 简单的字符重叠度计算
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        val commonChars = shorter.toCharArray().count { char ->
            longer.contains(char, ignoreCase = true)
        }
        
        return (commonChars.toFloat() / longer.length).coerceIn(0f, 1f)
    }
    
    /**
     * 标准化字符串（移除空格、标点等）
     */
    private fun normalizeString(str: String): String {
        return str.lowercase()
            .replace(Regex("[\\s\\-_()（）【】\\[\\]]"), "")
            .trim()
    }
    
    /**
     * 发送HTTP请求
     */
    private suspend fun makeHttpRequest(
        urlString: String,
        headers: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            // 设置请求头
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                reader.use { it.readText() }
            } else {
                Log.w(TAG, "HTTP请求失败，状态码: $responseCode, URL: $urlString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP请求异常: $urlString", e)
            null
        }
    }
    
    /**
     * 解析LRC格式歌词
     */
    fun parseLRCFormat(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // 支持两种格式：[mm:ss.ff] 和 [mm:ss:ff]
        val regex = Regex("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")
        
        lrcText.lineSequence().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            
            val match = regex.find(trimmedLine)
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toIntOrNull() ?: return@forEach
                val milliseconds = match.groupValues[3].toIntOrNull() ?: return@forEach
                val text = match.groupValues[4].trim()
                
                // 转换为毫秒时间戳
                val timeStamp = (minutes * 60 + seconds) * 1000L + 
                    if (milliseconds < 100) milliseconds * 10L else milliseconds.toLong()
                
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
            Log.e(TAG, "读取本地歌词文件失败", e)
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

// ========== 数据模型 ==========

/**
 * 网易云音乐搜索响应
 */
@Serializable
private data class NetEaseSearchResponse(
    val result: NetEaseSearchResult? = null
)

@Serializable
private data class NetEaseSearchResult(
    val songs: List<NetEaseSong>? = null
)

@Serializable
private data class NetEaseSong(
    val id: Long,
    val name: String,
    val artists: List<NetEaseArtist>? = null
)

@Serializable
private data class NetEaseArtist(
    val name: String
)

/**
 * 网易云音乐歌词响应
 */
@Serializable
private data class NetEaseLyricResponse(
    val lrc: NetEaseLyric? = null
)

@Serializable
private data class NetEaseLyric(
    val lyric: String? = null
)

/**
 * QQ音乐搜索响应
 */
@Serializable
private data class QQMusicSearchResponse(
    val data: QQMusicSearchData? = null
)

@Serializable
private data class QQMusicSearchData(
    val song: QQMusicSongList? = null
)

@Serializable
private data class QQMusicSongList(
    val list: List<QQMusicSong>? = null
)

@Serializable
private data class QQMusicSong(
    val songid: String,
    val songname: String,
    val singer: List<QQMusicSinger>? = null
)

@Serializable
private data class QQMusicSinger(
    val name: String
)

/**
 * QQ音乐歌词响应
 */
@Serializable
private data class QQMusicLyricResponse(
    val lyric: String? = null
)
