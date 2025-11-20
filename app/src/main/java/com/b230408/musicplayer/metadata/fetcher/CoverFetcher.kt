package com.b230408.musicplayer.metadata.fetcher

import android.util.Log
import com.b230408.musicplayer.metadata.model.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 封面获取器
 * 从网络获取歌曲封面图片
 */
object CoverFetcher {
    
    private const val TAG = "CoverFetcher"
    private const val CONNECT_TIMEOUT = 8000
    private const val READ_TIMEOUT = 10000
    
    // JSON解析器
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 从网络获取封面URL（多源）
     * 返回最佳匹配的封面URL
     */
    suspend fun fetchCoverUrl(
        title: String,
        artist: String
    ): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始获取封面: $artist - $title")
        
        // 并行从多个源搜索
        val results = listOf(
            async { fetchFromQQMusic(title, artist) },
            async { fetchFromNetEase(title, artist) }
        ).awaitAll().filterNotNull()
        
        if (results.isEmpty()) {
            Log.w(TAG, "未找到封面")
            return@withContext null
        }
        
        // 优先使用QQ音乐的封面（版权更全）
        val bestCover = results.firstOrNull()
        Log.d(TAG, "最佳封面URL: $bestCover")
        
        return@withContext bestCover
    }
    
    /**
     * 从QQ音乐获取封面URL
     */
    private suspend fun fetchFromQQMusic(title: String, artist: String): String? {
        return try {
            Log.d(TAG, "尝试从QQ音乐获取封面: $artist - $title")
            
            // 搜索歌曲
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
            
            // 获取封面URL（QQ音乐的封面URL格式）
            val coverUrl = song.albummid?.let { albummid ->
                // QQ音乐封面URL格式：https://y.gtimg.cn/music/photo_new/T002R300x300M000{albummid}.jpg
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albummid.jpg"
            }
            
            if (coverUrl != null) {
                Log.d(TAG, "QQ音乐获取封面成功: $coverUrl")
                coverUrl
            } else {
                Log.w(TAG, "QQ音乐封面URL为空")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "从QQ音乐获取封面失败", e)
            null
        }
    }
    
    /**
     * 从网易云音乐获取封面URL
     */
    private suspend fun fetchFromNetEase(title: String, artist: String): String? {
        return try {
            Log.d(TAG, "尝试从网易云音乐获取封面: $artist - $title")
            
            // 搜索歌曲
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
            
            // 获取封面URL（网易云音乐的封面URL格式）
            val coverUrl = song.album?.let { album ->
                // 网易云音乐封面URL格式：https://p1.music.126.net/{picId}/{picId}.jpg
                val picId = album.picId ?: album.pic?.toString()
                if (picId != null) {
                    // 使用picId构建封面URL
                    "https://p1.music.126.net/$picId/$picId.jpg"
                } else {
                    null
                }
            }
            
            if (coverUrl != null) {
                Log.d(TAG, "网易云音乐获取封面成功: $coverUrl")
                coverUrl
            } else {
                Log.w(TAG, "网易云音乐封面URL为空")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "从网易云音乐获取封面失败", e)
            null
        }
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
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8"))
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
}

// ========== 数据模型 ==========

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
    val albummid: String? = null,
    val singer: List<QQMusicSinger>? = null
)

@Serializable
private data class QQMusicSinger(
    val name: String
)

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
    val artists: List<NetEaseArtist>? = null,
    val album: NetEaseAlbum? = null
)

@Serializable
private data class NetEaseArtist(
    val name: String
)

@Serializable
private data class NetEaseAlbum(
    val id: Long,
    val name: String,
    val picId: Long? = null,
    val pic: Long? = null
)

