package com.b230408.musicplayer.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌单实体类
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val dateCreated: Long,
    var dateModified: Long
)


