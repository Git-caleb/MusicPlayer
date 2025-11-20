package com.b230408.musicplayer.metadata.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.b230408.musicplayer.metadata.model.Metadata
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * ID3标签读取器
 * 用于读取MP3等音频文件的元数据信息
 */
object ID3Reader {
    
    /**
     * 尝试从文件名解析元数据（当 ID3 标签读取失败时使用）
     */
    private fun tryParseFromFileName(file: File): Metadata? {
        return try {
            val fileName = file.nameWithoutExtension
            android.util.Log.d("ID3Reader", "从文件名解析元数据: '$fileName'")
            
            val parts = fileName.split(Regex("\\s*-\\s*"), limit = 2)
            val title: String?
            val artist: String?
            
            if (parts.size == 2) {
                title = parts[0].trim().takeIf { it.isNotEmpty() }
                artist = parts[1].trim().takeIf { it.isNotEmpty() && it.length < 50 }
                android.util.Log.d("ID3Reader", "从文件名解析出 - title: '$title', artist: '$artist'")
            } else if (parts.size == 1) {
                title = parts[0].trim().takeIf { it.isNotEmpty() }
                artist = null
                android.util.Log.d("ID3Reader", "从文件名解析出 - title: '$title'")
            } else {
                return null
            }
            
            // 如果至少解析出了标题或艺术家，返回 Metadata
            if (title != null || artist != null) {
                Metadata(
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
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ID3Reader", "从文件名解析失败", e)
            null
        }
    }
    
    /**
     * 从文件读取元数据
     */
    fun readMetadata(file: File): Metadata? {
        return try {
            // 检查文件是否存在且可读
            if (!file.exists() || !file.canRead()) {
                return null
            }
            
            // 使用 try-catch 包裹 AudioFileIO.read，避免抛出未捕获的异常
            val audioFile = try {
                AudioFileIO.read(file)
            } catch (e: Exception) {
                // 如果读取失败（可能是文件格式不支持或损坏），尝试从文件名解析
                android.util.Log.w("ID3Reader", "AudioFileIO.read 失败: ${file.name}, 尝试从文件名解析", e)
                // 即使读取失败，也尝试从文件名解析元数据
                return tryParseFromFileName(file)
            }
            
            val tag = audioFile.tag
            
            // 对于 FLAC 等格式，JAudioTagger 使用 Vorbis 注释，字段名可能不同
            // 尝试读取标准字段，如果为空则尝试其他可能的字段名
            var title = tag?.getFirst(FieldKey.TITLE) ?: ""
            var artist = tag?.getFirst(FieldKey.ARTIST) ?: ""
            var album = tag?.getFirst(FieldKey.ALBUM) ?: ""
            
            android.util.Log.d("ID3Reader", "读取元数据 - 文件: ${file.name}, tag存在: ${tag != null}, 格式: ${audioFile.audioHeader.format}")
            android.util.Log.d("ID3Reader", "初始值 - title: '$title', artist: '$artist', album: '$album'")
            
            // 如果 artist 为空，立即尝试从文件名解析（在读取其他字段之前）
            if (artist.isEmpty()) {
                val fileName = file.nameWithoutExtension
                android.util.Log.d("ID3Reader", "artist 为空，尝试从文件名解析: '$fileName'")
                val parts = fileName.split(Regex("\\s*-\\s*"), limit = 2)
                if (parts.size == 2) {
                    val possibleTitle = parts[0].trim()
                    val possibleArtist = parts[1].trim()
                    if (possibleArtist.isNotEmpty() && possibleArtist.length < 50) {
                        if (title.isEmpty()) {
                            title = possibleTitle
                            android.util.Log.d("ID3Reader", "从文件名解析出 title: '$title'")
                        }
                        artist = possibleArtist
                        android.util.Log.d("ID3Reader", "从文件名解析出 artist: '$artist'")
                    }
                }
            }
            
            // 如果标准字段为空，尝试其他可能的字段（适用于某些格式）
            if (title.isEmpty() && tag != null) {
                title = tag.getFirst(FieldKey.TITLE_SORT).ifEmpty { 
                    tag.getFirst(FieldKey.TITLE).ifEmpty { "" }
                }
            }
            if (artist.isEmpty() && tag != null) {
                // 尝试多个可能的字段名
                val artistSort = tag.getFirst(FieldKey.ARTIST_SORT)
                if (artistSort.isNotEmpty()) {
                    artist = artistSort
                    android.util.Log.d("ID3Reader", "从 ARTIST_SORT 找到 artist: '$artist'")
                } else {
                    // 尝试其他可能的字段
                    val alternativeFields = listOf(
                        FieldKey.ALBUM_ARTIST,
                        FieldKey.COMPOSER,
                        FieldKey.CONDUCTOR
                    )
                    for (fieldKey in alternativeFields) {
                        try {
                            val value = tag.getFirst(fieldKey)
                            if (value.isNotEmpty()) {
                                artist = value
                                android.util.Log.d("ID3Reader", "从 $fieldKey 找到 artist: '$artist'")
                                break
                            }
                        } catch (_: Exception) {
                            // 忽略不支持的字段
                        }
                    }
                    
                    // 如果还是为空，尝试遍历所有字段
                    if (artist.isEmpty() && tag != null) {
                        try {
                            val allFields = tag.fields
                            for (field in allFields) {
                                val fieldId = field.id
                                if (fieldId.contains("ARTIST", ignoreCase = true) || 
                                    fieldId.contains("PERFORMER", ignoreCase = true) ||
                                    fieldId.contains("AUTHOR", ignoreCase = true)) {
                                    // 使用 getFirst 方法获取字段值
                                    val value = try {
                                        // 尝试根据字段ID获取值
                                        when {
                                            fieldId.contains("ARTIST", ignoreCase = true) && !fieldId.contains("SORT", ignoreCase = true) -> {
                                                tag.getFirst(FieldKey.ARTIST)
                                            }
                                            fieldId.contains("ALBUM_ARTIST", ignoreCase = true) -> {
                                                tag.getFirst(FieldKey.ALBUM_ARTIST)
                                            }
                                            else -> {
                                                // 尝试直接获取字段的字符串表示
                                                val fieldStr = field.toString()
                                                // 如果包含实际内容（不是对象引用），提取值
                                                if (fieldStr.length > 2 && !fieldStr.contains("@")) {
                                                    fieldStr
                                                } else {
                                                    // 尝试通过反射获取值
                                                    try {
                                                        val getValueMethod = field.javaClass.getMethod("getValue")
                                                        getValueMethod.invoke(field)?.toString() ?: ""
                                                    } catch (_: Exception) {
                                                        ""
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        ""
                                    }
                                    
                                    if (value.isNotEmpty() && value.trim().isNotEmpty()) {
                                        android.util.Log.d("ID3Reader", "从字段 $fieldId 找到 artist: '$value'")
                                        artist = value.trim()
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ID3Reader", "遍历字段失败", e)
                            // 记录错误但继续执行
                        }
                    }
                    
                    // 如果仍然为空，尝试从标题字段解析（格式：歌曲名 - 艺术家）
                    if (artist.isEmpty() && title.isNotEmpty()) {
                        val titleParts = title.split(Regex("\\s*-\\s*"), limit = 2)
                        if (titleParts.size == 2) {
                            val possibleTitle = titleParts[0].trim()
                            val possibleArtist = titleParts[1].trim()
                            if (possibleArtist.isNotEmpty() && possibleArtist.length < 50) {
                                // 更新标题和艺术家
                                title = possibleTitle
                                artist = possibleArtist
                                android.util.Log.d("ID3Reader", "从标题字段解析出 - title: '$title', artist: '$artist'")
                            }
                        }
                    }
                    
                    // 如果仍然为空，尝试从文件名解析（格式：歌曲名 - 艺术家）
                    if (artist.isEmpty()) {
                        val fileName = file.nameWithoutExtension
                        val parts = fileName.split(Regex("\\s*-\\s*"), limit = 2)
                        if (parts.size == 2) {
                            val possibleArtist = parts[1].trim()
                            if (possibleArtist.isNotEmpty() && possibleArtist.length < 50) { // 合理的艺术家名称长度
                                artist = possibleArtist
                                android.util.Log.d("ID3Reader", "从文件名解析出 artist: '$artist'")
                            }
                        }
                    }
                }
            }
            if (album.isEmpty() && tag != null) {
                album = tag.getFirst(FieldKey.ALBUM_SORT).ifEmpty {
                    tag.getFirst(FieldKey.ALBUM).ifEmpty { "" }
                }
            }
            
            // 如果仍然为空，尝试从文件名解析（格式：歌曲名 - 艺术家）
            // 这个逻辑应该在所有 ID3 标签读取之后执行
            val fileName = file.nameWithoutExtension
            if (title.isEmpty() || artist.isEmpty()) {
                val parts = fileName.split(Regex("\\s*-\\s*"), limit = 2)
                if (parts.size == 2) {
                    val possibleTitle = parts[0].trim()
                    val possibleArtist = parts[1].trim()
                    if (possibleTitle.isNotEmpty() && possibleArtist.isNotEmpty() && possibleArtist.length < 50) {
                        if (title.isEmpty()) {
                            title = possibleTitle
                            android.util.Log.d("ID3Reader", "从文件名解析出 title: '$title'")
                        }
                        if (artist.isEmpty()) {
                            artist = possibleArtist
                            android.util.Log.d("ID3Reader", "从文件名解析出 artist: '$artist'")
                        }
                    }
                } else if (title.isEmpty() && parts.size == 1) {
                    // 如果文件名不包含分隔符，使用整个文件名作为标题
                    title = parts[0].trim()
                    android.util.Log.d("ID3Reader", "从文件名解析出 title: '$title'")
                }
            }
            
            android.util.Log.d("ID3Reader", "最终值 - title: '$title', artist: '$artist', album: '$album'")
            
            // 安全地读取其他字段，避免 NPE
            val year = try {
                tag?.getFirst(FieldKey.YEAR)?.toIntOrNull()
            } catch (e: Exception) {
                android.util.Log.w("ID3Reader", "读取 YEAR 字段失败", e)
                null
            }
            
            val genre = try {
                tag?.getFirst(FieldKey.GENRE) ?: ""
            } catch (e: Exception) {
                android.util.Log.w("ID3Reader", "读取 GENRE 字段失败", e)
                ""
            }
            
            val trackNumber = try {
                tag?.getFirst(FieldKey.TRACK)?.toIntOrNull()
            } catch (e: Exception) {
                android.util.Log.w("ID3Reader", "读取 TRACK 字段失败", e)
                null
            }
            
            val comment = try {
                tag?.getFirst(FieldKey.COMMENT) ?: ""
            } catch (e: Exception) {
                android.util.Log.w("ID3Reader", "读取 COMMENT 字段失败", e)
                ""
            }
            
            // 读取封面
            val artwork = try {
                tag?.firstArtwork
            } catch (e: Exception) {
                android.util.Log.w("ID3Reader", "读取封面失败", e)
                null
            }
            val coverArt = artwork?.binaryData
            if (coverArt != null) {
                android.util.Log.d("ID3Reader", "成功读取封面，大小: ${coverArt.size} 字节")
            } else {
                android.util.Log.d("ID3Reader", "未找到封面图片")
            }
            val coverBitmap = coverArt?.let { bytes ->
                try {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        android.util.Log.d("ID3Reader", "成功解码封面 Bitmap，尺寸: ${bitmap.width}x${bitmap.height}")
                    } else {
                        android.util.Log.w("ID3Reader", "封面字节数组解码失败")
                    }
                    bitmap
                } catch (e: Exception) {
                    android.util.Log.e("ID3Reader", "解码封面 Bitmap 时出错", e)
                    null
                }
            }
            
            Metadata(
                title = title.ifEmpty { null },
                artist = artist.ifEmpty { null },
                album = album.ifEmpty { null },
                year = year,
                genre = genre.ifEmpty { null },
                coverArt = coverArt,
                coverBitmap = coverBitmap,
                trackNumber = trackNumber,
                comment = comment.ifEmpty { null }
            )
        } catch (e: Exception) {
            android.util.Log.e("ID3Reader", "读取元数据失败: ${file.name}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从URI读取元数据
     */
    fun readMetadata(context: Context, uri: Uri): Metadata? {
        return try {
            // 对于URI，先尝试复制到临时文件
            val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (tempFile.exists()) {
                val metadata = readMetadata(tempFile)
                tempFile.delete()
                return metadata
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("ID3Reader", "从URI读取元数据失败", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从文件路径读取元数据
     */
    fun readMetadata(filePath: String): Metadata? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                readMetadata(file)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ID3Reader", "从文件路径读取元数据失败: $filePath", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 检查文件是否有有效的元数据标签
     */
    fun hasMetadata(file: File): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            audioFile.tag != null
        } catch (_: Exception) {
            false
        }
    }
}
