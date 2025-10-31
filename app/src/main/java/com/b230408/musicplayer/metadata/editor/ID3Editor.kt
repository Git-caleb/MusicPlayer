package com.b230408.musicplayer.metadata.editor

import android.graphics.Bitmap
import com.b230408.musicplayer.metadata.model.Metadata
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * ID3标签编辑器
 * 用于编辑音频文件的元数据信息
 */
object ID3Editor {
    
    /**
     * 更新元数据到文件
     */
    fun updateMetadata(file: File, metadata: Metadata): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // 更新标题
            metadata.title?.let {
                tag.setField(FieldKey.TITLE, it)
            }
            
            // 更新艺术家
            metadata.artist?.let {
                tag.setField(FieldKey.ARTIST, it)
            }
            
            // 更新专辑
            metadata.album?.let {
                tag.setField(FieldKey.ALBUM, it)
            }
            
            // 更新年份
            metadata.year?.let {
                tag.setField(FieldKey.YEAR, it.toString())
            }
            
            // 更新流派
            metadata.genre?.let {
                tag.setField(FieldKey.GENRE, it)
            }
            
            // 更新音轨编号
            metadata.trackNumber?.let {
                tag.setField(FieldKey.TRACK, it.toString())
            }
            
            // 更新注释
            metadata.comment?.let {
                tag.setField(FieldKey.COMMENT, it)
            }
            
            // 更新封面
            metadata.coverArt?.let { coverBytes ->
                try {
                    // 删除旧的封面
                    tag.deleteArtworkField()
                    // 创建新的封面 - 使用现有的 artwork 或创建新的
                    val artwork = tag.firstArtwork
                    if (artwork != null) {
                        artwork.binaryData = coverBytes
                        tag.setField(artwork)
                    } else {
                        // 如果不存在 artwork，尝试使用 setField 设置封面
                        // 注意：某些格式可能需要特殊处理
                        val existingArtwork = tag.artworkList.firstOrNull()
                        if (existingArtwork != null) {
                            existingArtwork.binaryData = coverBytes
                            tag.setField(existingArtwork)
                        } else {
                            // 如果都不存在，尝试直接设置二进制数据
                            // 某些标签格式可能需要特殊处理
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 如果设置封面失败，不影响其他元数据的保存
                }
            }
            
            // 保存到文件
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新标题
     */
    fun updateTitle(file: File, title: String): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, title)
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新艺术家
     */
    fun updateArtist(file: File, artist: String): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.ARTIST, artist)
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新专辑
     */
    fun updateAlbum(file: File, album: String): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.ALBUM, album)
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新封面
     */
    fun updateCoverArt(file: File, coverBitmap: Bitmap): Boolean {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val coverBytes = outputStream.toByteArray()
            
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            // 移除旧的封面
            tag.deleteArtworkField()
            // 创建新的封面
            val artwork = tag.firstArtwork
            if (artwork != null) {
                artwork.binaryData = coverBytes
                tag.setField(artwork)
            } else {
                // 如果没有现有的 artwork，尝试从 artworkList 获取或创建
                val existingArtwork = tag.artworkList.firstOrNull()
                if (existingArtwork != null) {
                    existingArtwork.binaryData = coverBytes
                    tag.setField(existingArtwork)
                }
            }
            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 删除元数据标签
     */
    fun deleteMetadata(file: File): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            if (tag != null) {
                tag.deleteField(FieldKey.TITLE)
                tag.deleteField(FieldKey.ARTIST)
                tag.deleteField(FieldKey.ALBUM)
                tag.deleteField(FieldKey.YEAR)
                tag.deleteField(FieldKey.GENRE)
                tag.deleteField(FieldKey.COVER_ART)
                audioFile.commit()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
