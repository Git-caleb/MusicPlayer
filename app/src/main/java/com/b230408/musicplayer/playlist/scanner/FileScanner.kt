package com.b230408.musicplayer.playlist.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.b230408.musicplayer.metadata.reader.ID3Reader
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 文件扫描器
 * 扫描指定目录下的音乐文件并创建Track对象
 */
class FileScanner(private val context: Context) {
    
    private val contentResolver: ContentResolver = context.contentResolver
    
    /**
     * 扫描指定目录下的音乐文件
     */
    suspend fun scanDirectory(directory: File): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext tracks
        }
        
        scanDirectoryRecursive(directory, tracks)
        
        tracks
    }
    
    /**
     * 递归扫描目录
     */
    private fun scanDirectoryRecursive(directory: File, tracks: MutableList<Track>) {
        try {
            // 检查目录权限
            if (!directory.exists() || !directory.canRead()) {
                return
            }
            
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        // 跳过隐藏文件夹（如 .thumbnails）
                        if (!file.name.startsWith(".") && file.canRead()) {
                            // 递归扫描子目录
                            scanDirectoryRecursive(file, tracks)
                        }
                    } else if (file.isFile && !file.name.startsWith(".") && file.canRead()) {
                        // 使用 FileUtils.isAudioFile 判断是否为音频文件
                        if (FileUtils.isAudioFile(file)) {
                            // 创建Track对象
                            val track = createTrackFromFile(file)
                            track?.let { tracks.add(it) }
                        } else {
                            // 记录未识别的文件，便于调试
                            val extension = FileUtils.getFileExtension(file.name)
                            if (file.length() > 10000) { // 大于10KB的文件才记录
                                android.util.Log.d("FileScanner", "跳过文件（格式不支持或格式未知）: ${file.name}, 扩展名: $extension, 大小: ${file.length()} bytes")
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    // 跳过权限不足的文件
                    e.printStackTrace()
                } catch (e: Exception) {
                    // 跳过处理失败的文件，继续处理其他文件
                    e.printStackTrace()
                }
            }
        } catch (e: SecurityException) {
            // 权限不足，记录但不崩溃
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 从文件创建Track对象
     */
    private fun createTrackFromFile(file: File): Track? {
        return try {
            // 检查文件是否存在且可读
            if (!file.exists() || !file.canRead()) {
                return null
            }
            
            // 尝试读取文件扩展名，如果没有扩展名，尝试从文件内容判断
            var format = FileUtils.getFileExtension(file.name)
            if (format.isEmpty()) {
                // 没有扩展名时，尝试读取文件的 MIME 类型或使用默认值
                // 根据文件大小和常见音频文件特征判断
                format = try {
                    if (file.length() > 0) {
                        // 尝试读取文件的前几个字节来判断格式
                        val buffer = ByteArray(12)
                        java.io.FileInputStream(file).use { input ->
                            val bytesRead = input.read(buffer)
                            if (bytesRead >= 4) {
                                // 检查常见音频文件头
                                when {
                                    buffer[0] == 0xFF.toByte() && buffer[1] == 0xFB.toByte() -> "mp3"
                                    buffer[0] == 0x49.toByte() && buffer[1] == 0x44.toByte() && buffer[2] == 0x33.toByte() -> "mp3"
                                    buffer[4] == 0x66.toByte() && buffer[5] == 0x74.toByte() && buffer[6] == 0x79.toByte() && buffer[7] == 0x70.toByte() -> "m4a"
                                    else -> "mp3" // 默认假设为 mp3
                                }
                            } else {
                                "mp3" // 默认
                            }
                        }
                    } else {
                        "mp3"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "mp3" // 如果读取失败，默认设为 mp3
                }
            }
            
            val uri = Uri.fromFile(file)
            
            // 安全地读取元数据，如果失败则返回 null
            val metadata = try {
                ID3Reader.readMetadata(file)
            } catch (e: Exception) {
                e.printStackTrace()
                null // 元数据读取失败不影响创建 Track
            }
            
            Track(
                id = System.currentTimeMillis() + file.hashCode().toLong(), // 生成唯一ID
                path = file.absolutePath,
                uri = uri,
                fileName = file.name,
                fileSize = file.length(),
                duration = 0L, // 需要在播放时获取
                format = format,
                metadata = metadata
            )
        } catch (e: SecurityException) {
            // 权限异常，记录但不崩溃
            e.printStackTrace()
            null
        } catch (e: Exception) {
            // 其他异常
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 触发媒体扫描，使新添加的音乐文件能被 MediaStore 识别
     */
    fun triggerMediaScan(filePath: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                arrayOf("audio/*"),
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 触发整个 Music 目录的媒体扫描
     */
    fun triggerMusicDirectoryScan() {
        try {
            // 扫描多个可能的音乐目录
            val musicDirs = listOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/QQMusic",
                "/storage/emulated/0/Android/data/com.tencent.qqmusic/files/QQMusic",
                "/storage/emulated/0/netease/cloudmusic/Music",
                "/storage/emulated/0/Android/data/com.netease.cloudmusic/files/Music"
            )
            
            musicDirs.forEach { dirPath ->
                try {
                    val musicDir = java.io.File(dirPath)
                    if (musicDir.exists() && musicDir.isDirectory && musicDir.canRead()) {
                        android.util.Log.d("FileScanner", "扫描目录: $dirPath")
                        scanDirectoryForMediaScan(musicDir)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FileScanner", "无法访问目录: $dirPath", e)
                }
            }
        } catch (e: SecurityException) {
            // 权限不足，记录但不崩溃
            android.util.Log.e("FileScanner", "权限不足", e)
        } catch (e: Exception) {
            android.util.Log.e("FileScanner", "扫描失败", e)
        }
    }
    
    /**
     * 递归扫描目录并触发媒体扫描
     */
    private fun scanDirectoryForMediaScan(directory: File) {
        try {
            if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
                return
            }
            
            val files = directory.listFiles() ?: return
            
            files.forEach { file ->
                try {
                    if (file.isDirectory && !file.name.startsWith(".") && file.canRead()) {
                        // 递归扫描子目录
                        scanDirectoryForMediaScan(file)
                    } else if (file.isFile && !file.name.startsWith(".") && file.canRead()) {
                        // 检查文件大小和格式
                        if (file.length() > 1000 && FileUtils.isAudioFile(file)) {
                            triggerMediaScan(file.absolutePath)
                            android.util.Log.d("FileScanner", "触发媒体扫描: ${file.absolutePath}")
                        }
                    }
                } catch (e: Exception) {
                    // 跳过无法访问的文件
                    android.util.Log.w("FileScanner", "跳过文件: ${file.absolutePath}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FileScanner", "扫描目录失败: ${directory.absolutePath}", e)
        }
    }
    
    /**
     * 从封面路径读取封面数据（ByteArray）
     */
    private fun loadCoverArtFromPath(coverPath: String?): ByteArray? {
        if (coverPath.isNullOrEmpty()) {
            return null
        }
        
        return try {
            val coverFile = File(coverPath)
            if (coverFile.exists() && coverFile.canRead()) {
                val bytes = coverFile.readBytes()
                android.util.Log.d("FileScanner", "成功加载封面: $coverPath, 大小: ${bytes.size} 字节")
                bytes
            } else {
                android.util.Log.w("FileScanner", "封面文件不存在或不可读: $coverPath")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("FileScanner", "读取封面文件失败: $coverPath", e)
            null
        }
    }
    
    /**
     * 从MediaStore.Albums表获取专辑封面路径
     */
    private fun getAlbumArtPath(albumId: Long?): String? {
        if (albumId == null || albumId <= 0) {
            return null
        }
        
        return try {
            val projection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
            val selection = "${MediaStore.Audio.Albums._ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())
            
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.count > 0 && cursor.moveToFirst()) {
                    try {
                        val albumArtColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
                        val albumArtPath = cursor.getString(albumArtColumn)
                        if (!albumArtPath.isNullOrEmpty()) {
                            android.util.Log.d("FileScanner", "从Albums表获取封面路径成功: albumId=$albumId, path=$albumArtPath")
                            albumArtPath
                        } else {
                            android.util.Log.d("FileScanner", "Albums表封面路径为空: albumId=$albumId")
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FileScanner", "读取Albums表封面列失败: albumId=$albumId", e)
                        null
                    }
                } else {
                    android.util.Log.d("FileScanner", "Albums表未找到记录: albumId=$albumId")
                    null
                }
            } ?: run {
                android.util.Log.w("FileScanner", "Albums表查询返回null: albumId=$albumId")
                null
            }
        } catch (e: SecurityException) {
            android.util.Log.e("FileScanner", "查询Albums表权限不足: albumId=$albumId", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("FileScanner", "查询Albums表失败: albumId=$albumId", e)
            null
        }
    }
    
    /**
     * 使用MediaStore扫描音乐文件（推荐用于Android 10+）
     */
    suspend fun scanMediaStore(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE
        ).apply {
            // 在 Android 10 以下添加 DATA 列，Android 10+ 可能没有
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                plus(MediaStore.Audio.Media.DATA)
            }
        }
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                android.util.Log.d("FileScanner", "MediaStore 查询结果：${cursor.count} 条记录")
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataColumn = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                } else {
                    -1 // Android 10+ 不支持 DATA 列
                }
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val path = if (dataColumn >= 0) {
                            cursor.getString(dataColumn) ?: ""
                        } else {
                            // Android 10+ 需要通过 URI 访问，但我们可以尝试构建
                            ""
                        }
                        val fileName = cursor.getString(nameColumn) ?: "未知"
                        val fileSize = cursor.getLong(sizeColumn)
                        val duration = cursor.getLong(durationColumn)
                        val title = cursor.getString(titleColumn)
                        val artist = cursor.getString(artistColumn)
                        val album = cursor.getString(albumColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val mimeType = cursor.getString(mimeTypeColumn)
                        
                        // 从MediaStore.Albums表获取封面路径
                        val albumArtPath = getAlbumArtPath(albumId)
                        val albumArtBytes = albumArtPath?.let { loadCoverArtFromPath(it) }
                        val albumArtBitmap = albumArtBytes?.let { bytes ->
                            try {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                android.util.Log.e("FileScanner", "解码封面Bitmap失败", e)
                                null
                            }
                        }
                        
                        // 构建URI
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                        } else {
                            if (path.isNotEmpty()) {
                                Uri.fromFile(File(path))
                            } else {
                                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                            }
                        }
                        
                        // 创建元数据
                        // 先尝试使用 MediaStore 的元数据（包含封面）
                        val metadata = if ((title != null && title.isNotEmpty()) || 
                                           (artist != null && artist.isNotEmpty()) || 
                                           (album != null && album.isNotEmpty()) ||
                                           albumArtBytes != null) {
                            // MediaStore 有元数据，使用它（包含封面）
                            com.b230408.musicplayer.metadata.model.Metadata(
                                title = title?.takeIf { it.isNotEmpty() },
                                artist = artist?.takeIf { it.isNotEmpty() },
                                album = album?.takeIf { it.isNotEmpty() },
                                coverArt = albumArtBytes,
                                coverBitmap = albumArtBitmap
                            )
                        } else {
                            // MediaStore 没有元数据
                            null
                        }
                        
                        // 如果 MediaStore 的元数据为空，或者 artist 为空，尝试从文件读取 ID3 标签
                        val finalMetadata = if (path.isNotEmpty()) {
                            try {
                                val file = File(path)
                                if (file.exists()) {
                                    val id3Metadata = ID3Reader.readMetadata(file)
                                    if (id3Metadata != null) {
                                        // 合并 MediaStore 和 ID3 标签的元数据
                                        // 优先使用 ID3 标签的封面（更准确），如果没有则使用 MediaStore 的封面
                                        val finalCoverArt = id3Metadata.coverArt ?: metadata?.coverArt ?: albumArtBytes
                                        val finalCoverBitmap = id3Metadata.coverBitmap ?: metadata?.coverBitmap ?: albumArtBitmap
                                        
                                        android.util.Log.d("FileScanner", "合并元数据 - 文件: $fileName")
                                        android.util.Log.d("PlayerUI", "ID3封面: ${id3Metadata.coverArt != null}, MediaStore封面: ${albumArtBytes != null}, 最终封面: ${finalCoverArt != null}")
                                        
                                        com.b230408.musicplayer.metadata.model.Metadata(
                                            title = id3Metadata.title ?: metadata?.title,
                                            artist = id3Metadata.artist ?: metadata?.artist,
                                            album = id3Metadata.album ?: metadata?.album,
                                            year = id3Metadata.year ?: metadata?.year,
                                            genre = id3Metadata.genre ?: metadata?.genre,
                                            // 优先使用ID3标签的封面，如果没有则使用MediaStore的封面
                                            coverArt = finalCoverArt,
                                            coverBitmap = finalCoverBitmap,
                                            trackNumber = id3Metadata.trackNumber ?: metadata?.trackNumber,
                                            totalTracks = id3Metadata.totalTracks ?: metadata?.totalTracks,
                                            comment = id3Metadata.comment ?: metadata?.comment
                                        )
                                    } else {
                                        // ID3读取失败，使用MediaStore的元数据
                                        android.util.Log.d("FileScanner", "ID3读取失败，使用MediaStore元数据: $fileName")
                                        metadata
                                    }
                                } else {
                                    // 文件不存在，使用MediaStore的元数据
                                    android.util.Log.d("FileScanner", "文件不存在，使用MediaStore元数据: $path")
                                    metadata
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FileScanner", "读取 ID3 标签失败: $path", e)
                                // 读取失败，使用MediaStore的元数据
                                metadata
                            }
                        } else {
                            // 路径为空，使用MediaStore的元数据
                            android.util.Log.d("FileScanner", "路径为空，使用MediaStore元数据")
                            metadata
                        }
                        
                        val format = mimeType?.substringAfterLast("/") ?: FileUtils.getFileExtension(fileName)
                        
                        val track = Track(
                            id = id,
                            path = path.ifEmpty { uri.toString() }, // 如果路径为空，使用 URI
                            uri = uri,
                            fileName = fileName,
                            fileSize = fileSize,
                            duration = duration,
                            format = format,
                            metadata = finalMetadata
                        )
                        
                        tracks.add(track)
                    } catch (e: Exception) {
                        // 跳过处理失败的文件
                        android.util.Log.e("FileScanner", "处理音乐文件失败", e)
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileScanner", "MediaStore 查询失败", e)
            e.printStackTrace()
        }
        
        tracks
    }
    
    /**
     * 扫描指定路径（可以是目录路径或文件路径）
     */
    suspend fun scanPath(path: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            
            if (file.isDirectory) {
                scanDirectory(file)
            } else if (file.isFile && file.canRead()) {
                // 使用 FileUtils.isAudioFile 判断是否为音频文件
                if (FileUtils.isAudioFile(file)) {
                    createTrackFromFile(file)?.let { listOf(it) } ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
