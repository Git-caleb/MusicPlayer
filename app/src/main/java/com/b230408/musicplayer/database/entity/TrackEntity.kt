package com.b230408.musicplayer.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌曲实体类
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: Long,
    val path: String,
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val duration: Long = 0L,
    val format: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)


