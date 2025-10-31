package com.b230408.musicplayer.player.model

import android.net.Uri
import com.b230408.musicplayer.metadata.model.Metadata

/**
 * 音轨数据模型
 */
data class Track(
    /**
     * 唯一标识符
     */
    val id: Long,
    
    /**
     * 文件路径
     */
    val path: String,
    
    /**
     * URI
     */
    val uri: Uri,
    
    /**
     * 文件名
     */
    val fileName: String,
    
    /**
     * 文件大小（字节）
     */
    val fileSize: Long,
    
    /**
     * 时长（毫秒）
     */
    val duration: Long = 0L,
    
    /**
     * 音频格式（MP3、AMR、AAC、OGG等）
     */
    val format: String,
    
    /**
     * 歌曲元数据
     */
    val metadata: Metadata? = null,
    
    /**
     * 添加时间（毫秒时间戳）
     */
    val dateAdded: Long = System.currentTimeMillis()
) {
    /**
     * 获取显示标题（优先使用元数据标题，否则使用文件名）
     */
    fun getDisplayTitle(): String {
        return metadata?.title ?: fileName.replace(Regex("\\.[^.]+$"), "")
    }
    
    /**
     * 获取显示艺术家（优先使用元数据艺术家，否则返回"未知艺术家"）
     */
    fun getDisplayArtist(): String {
        return metadata?.artist ?: "未知艺术家"
    }
    
    /**
     * 获取显示专辑（优先使用元数据专辑，否则返回"未知专辑"）
     */
    fun getDisplayAlbum(): String {
        return metadata?.album ?: "未知专辑"
    }
}
