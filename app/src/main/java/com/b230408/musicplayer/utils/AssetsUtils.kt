package com.b230408.musicplayer.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.b230408.musicplayer.metadata.reader.ID3Reader
import com.b230408.musicplayer.player.model.Track
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Assets 资源工具类
 * 用于加载和应用打包的内置资源（MP3 文件、歌词文件等）
 */
object AssetsUtils {
    
    private const val TAG = "AssetsUtils"
    
    /**
     * 内置音乐文件目录
     */
    private const val ASSETS_MUSIC_DIR = "music"
    
    /**
     * 内置歌词文件目录
     */
    private const val ASSETS_LYRICS_DIR = "lyrics"
    
    /**
     * 扫描 assets/music 目录中的 MP3 文件
     * @return Track 列表
     */
    fun scanAssetsMusic(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        
        try {
            val assetManager = context.assets
            val musicFiles = assetManager.list(ASSETS_MUSIC_DIR) ?: emptyArray()
            
            Log.d(TAG, "在 assets/music 中找到 ${musicFiles.size} 个文件")
            
            musicFiles.forEach { fileName ->
                // 检查文件扩展名是否为支持的音频格式
                val extension = FileUtils.getFileExtension(fileName).lowercase()
                if (Constants.SUPPORTED_AUDIO_FORMATS.contains(extension)) {
                    try {
                        val track = createTrackFromAssets(context, fileName)
                        track?.let { tracks.add(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "创建 Track 失败: $fileName", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 assets 音乐文件失败", e)
        }
        
        return tracks
    }
    
    /**
     * 从 assets 创建 Track 对象
     */
    private fun createTrackFromAssets(context: Context, fileName: String): Track? {
        return try {
            val assetPath = "$ASSETS_MUSIC_DIR/$fileName"
            val uri = Uri.parse("android.resource://${context.packageName}/assets/$assetPath")
            
            // 获取文件大小（需要读取文件流）
            val fileSize = getAssetFileSize(context, assetPath)
            
            // 读取 ID3 元数据（启动时快速模式，不加载封面）
            val metadata = readMetadataFromAssets(context, assetPath, loadCover = false)
            
            // 生成唯一 ID（使用文件名哈希）
            val id = fileName.hashCode().toLong()
            
            // 获取文件扩展名
            val format = FileUtils.getFileExtension(fileName).lowercase()
            
            Track(
                id = id,
                path = "assets://$assetPath", // 使用特殊路径标识
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                duration = 0L, // 时长需要播放时获取
                format = format,
                metadata = metadata,
                dateAdded = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建 Track 失败: $fileName", e)
            null
        }
    }
    
    /**
     * 获取 assets 文件大小
     */
    private fun getAssetFileSize(context: Context, assetPath: String): Long {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                inputStream.available().toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件大小失败: $assetPath", e)
            0L
        }
    }
    
    /**
     * 从 assets 读取 ID3 元数据
     * @param loadCover 是否加载封面（默认 false，启动时跳过封面以提升速度）
     */
    fun readMetadataFromAssets(
        context: Context, 
        assetPath: String,
        loadCover: Boolean = false
    ): com.b230408.musicplayer.metadata.model.Metadata? {
        return try {
            // 获取原始文件名和扩展名
            val fileName = File(assetPath).name
            val extension = FileUtils.getFileExtension(fileName)
            
            // 将 assets 文件复制到临时文件以读取 ID3 标签，使用原始扩展名
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.$extension")
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 尝试读取元数据
            Log.d(TAG, "开始读取 assets 文件元数据: $assetPath, loadCover=$loadCover")
            var metadata = if (loadCover) {
                // 完整读取（包括封面）
                ID3Reader.readMetadata(tempFile)
            } else {
                // 快速读取（跳过封面，只读取基本元数据）
                ID3Reader.readMetadataFast(tempFile)
            }
            
            // 检查元数据
            if (metadata != null) {
                if (loadCover) {
                    Log.d(TAG, "元数据读取成功（含封面） - 标题: ${metadata.title}, 艺术家: ${metadata.artist}")
                    Log.d(TAG, "封面数据 - coverArt: ${metadata.coverArt != null} (${metadata.coverArt?.size ?: 0} 字节), coverBitmap: ${metadata.coverBitmap != null}")
                    if (metadata.coverBitmap != null) {
                        Log.d(TAG, "封面 Bitmap 尺寸: ${metadata.coverBitmap.width}x${metadata.coverBitmap.height}")
                    }
                } else {
                    Log.d(TAG, "元数据读取成功（快速模式，不含封面） - 标题: ${metadata.title}, 艺术家: ${metadata.artist}")
                }
            } else {
                Log.w(TAG, "元数据读取失败: $assetPath")
            }
            
            // 如果读取失败或艺术家为空，尝试从原始文件名解析
            if (metadata == null || metadata.artist == null || metadata.artist.isEmpty()) {
                val originalFileName = File(assetPath).nameWithoutExtension
                val parts = originalFileName.split(Regex("\\s*-\\s*"), limit = 2)
                if (parts.size == 2) {
                    val title = parts[0].trim().takeIf { it.isNotEmpty() }
                    val artist = parts[1].trim().takeIf { it.isNotEmpty() && it.length < 50 }
                    
                    if (title != null || artist != null) {
                        // 合并元数据：优先使用从文件读取的，如果为空则使用从文件名解析的
                        metadata = com.b230408.musicplayer.metadata.model.Metadata(
                            title = metadata?.title ?: title,
                            artist = metadata?.artist ?: artist,
                            album = metadata?.album,
                            year = metadata?.year,
                            genre = metadata?.genre,
                            coverArt = metadata?.coverArt,
                            coverBitmap = metadata?.coverBitmap,
                            trackNumber = metadata?.trackNumber,
                            comment = metadata?.comment
                        )
                        Log.d(TAG, "从原始文件名解析元数据: title='$title', artist='$artist'")
                    }
                }
            }
            
            // 清理临时文件
            tempFile.delete()
            
            metadata
        } catch (e: Exception) {
            Log.e(TAG, "读取元数据失败: $assetPath", e)
            // 即使读取失败，也尝试从文件名解析
            try {
                val originalFileName = File(assetPath).nameWithoutExtension
                val parts = originalFileName.split(Regex("\\s*-\\s*"), limit = 2)
                if (parts.size == 2) {
                    val title = parts[0].trim().takeIf { it.isNotEmpty() }
                    val artist = parts[1].trim().takeIf { it.isNotEmpty() && it.length < 50 }
                    if (title != null || artist != null) {
                        return com.b230408.musicplayer.metadata.model.Metadata(
                            title = title,
                            artist = artist,
                            album = null,
                            year = null,
                            genre = null,
                            coverArt = null,
                            coverBitmap = null,
                            trackNumber = null,
                            comment = null
                        )
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "从文件名解析也失败", e2)
            }
            null
        }
    }
    
    /**
     * 获取 assets 中音乐文件的 URI
     * 注意：ExoPlayer 不能直接播放 assets 中的文件，需要先复制到临时文件
     */
    fun getAssetsMusicUri(context: Context, fileName: String): Uri? {
        return try {
            val assetPath = "$ASSETS_MUSIC_DIR/$fileName"
            
            // 将 assets 文件复制到应用的 files 目录
            val outputFile = File(context.filesDir, "assets_music/$fileName")
            outputFile.parentFile?.mkdirs()
            
            // 如果文件已存在且大小相同，直接返回
            if (outputFile.exists()) {
                val assetSize = getAssetFileSize(context, assetPath)
                if (outputFile.length() == assetSize) {
                    return Uri.fromFile(outputFile)
                }
            }
            
            // 复制文件
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "获取 assets 音乐 URI 失败: $fileName", e)
            null
        }
    }
    
    /**
     * 读取 assets 中的歌词文件
     * @param musicFileName 音乐文件名（不含路径）
     * @return 歌词内容，如果不存在返回 null
     */
    fun readLyricsFromAssets(context: Context, musicFileName: String): String? {
        return try {
            // 将音乐文件名转换为歌词文件名（替换扩展名为 .lrc）
            val baseName = File(musicFileName).nameWithoutExtension
            val lyricFileName = "$baseName.lrc"
            val assetPath = "$ASSETS_LYRICS_DIR/$lyricFileName"
            
            context.assets.open(assetPath).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "未找到歌词文件: $musicFileName")
            null
        }
    }
    
    /**
     * 列出 assets 中的所有音乐文件
     */
    fun listAssetsMusicFiles(context: Context): List<String> {
        return try {
            val assetManager = context.assets
            val musicFiles = assetManager.list(ASSETS_MUSIC_DIR) ?: emptyArray()
            musicFiles.filter { FileUtils.isAudioFile(File(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "列出 assets 音乐文件失败", e)
            emptyList()
        }
    }
    
    /**
     * 列出 assets 中的所有歌词文件
     */
    fun listAssetsLyricsFiles(context: Context): List<String> {
        return try {
            val assetManager = context.assets
            val lyricFiles = assetManager.list(ASSETS_LYRICS_DIR) ?: emptyArray()
            lyricFiles.filter { it.endsWith(".lrc", ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "列出 assets 歌词文件失败", e)
            emptyList()
        }
    }
}

