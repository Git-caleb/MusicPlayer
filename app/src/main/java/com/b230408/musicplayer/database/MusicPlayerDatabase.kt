package com.b230408.musicplayer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.b230408.musicplayer.database.dao.PlaylistDao
import com.b230408.musicplayer.database.dao.PlaylistTrackDao
import com.b230408.musicplayer.database.dao.TrackDao
import com.b230408.musicplayer.database.entity.PlaylistEntity
import com.b230408.musicplayer.database.entity.PlaylistTrackEntity
import com.b230408.musicplayer.database.entity.TrackEntity

/**
 * 音乐播放器数据库
 */
@Database(
    entities = [PlaylistEntity::class, TrackEntity::class, PlaylistTrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MusicPlayerDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    
    companion object {
        @Volatile
        private var INSTANCE: MusicPlayerDatabase? = null
        
        fun getDatabase(context: Context): MusicPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicPlayerDatabase::class.java,
                    "music_player_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


