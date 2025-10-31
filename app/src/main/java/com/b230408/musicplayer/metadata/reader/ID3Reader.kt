package com.b230408.musicplayer.metadata.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.b230408.musicplayer.metadata.model.Metadata
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream

/**
 * ID3标签读取器
 * 用于读取MP3等音频文件的元数据信息
 */
object ID3Reader {
    
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
                // 如果读取失败（可能是文件格式不支持或损坏），返回 null
                e.printStackTrace()
                return null
            }
            
            val tag = audioFile.tag ?: return null
            
            val title = tag.getFirst(FieldKey.TITLE)
            val artist = tag.getFirst(FieldKey.ARTIST)
            val album = tag.getFirst(FieldKey.ALBUM)
            val year = tag.getFirst(FieldKey.YEAR).toIntOrNull()
            val genre = tag.getFirst(FieldKey.GENRE)
            val trackNumber = tag.getFirst(FieldKey.TRACK).toIntOrNull()
            val comment = tag.getFirst(FieldKey.COMMENT)
            
            // 读取封面
            val artwork = tag.firstArtwork
            val coverArt = artwork?.binaryData
            val coverBitmap = coverArt?.let { bytes ->
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
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
        } catch (e: Exception) {
            false
        }
    }
}
