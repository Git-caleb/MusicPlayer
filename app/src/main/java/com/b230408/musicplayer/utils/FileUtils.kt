package com.b230408.musicplayer.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 文件工具类
 */
object FileUtils {
    /**
     * 获取文件扩展名
     */
    fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex != -1 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }
    
    /**
     * 获取文件名（不含扩展名）
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }
    
    /**
     * 检查文件是否是支持的音乐格式
     */
    fun isSupportedAudioFile(fileName: String): Boolean {
        val extension = getFileExtension(fileName)
        return Constants.SUPPORTED_AUDIO_FORMATS.contains(extension)
    }
    
    /**
     * 判断文件是否为支持的音乐格式（MP3、AAC、OGG、WAV）
     */
    fun isAudioFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            return false
        }
        val extension = getFileExtension(file.name)
        return listOf("mp3", "aac", "ogg", "wav").contains(extension.lowercase())
    }
    
    /**
     * 获取文件大小
     */
    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) {
            file.length()
        } else {
            0L
        }
    }
    
    /**
     * 从URI获取文件名
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 如果无法从ContentResolver获取，尝试从路径中提取
        if (fileName.isNullOrEmpty()) {
            val path = uri.path
            if (path != null) {
                fileName = File(path).name
            }
        }
        
        return fileName
    }
    
    /**
     * 从URI获取文件路径
     */
    fun getFilePathFromUri(context: Context, uri: Uri): String? {
        var filePath: String? = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用MediaStore
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        filePath = cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Android 9及以下，直接从path获取
            filePath = uri.path
        }
        
        return filePath
    }
    
    /**
     * 从URI读取文件到临时文件
     */
    fun copyUriToTempFile(context: Context, uri: Uri, tempFileName: String): File? {
        return try {
            val tempFile = File(context.cacheDir, tempFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
