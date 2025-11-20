package com.b230408.musicplayer.history.manager

import android.content.Context
import com.b230408.musicplayer.database.MusicPlayerDatabase
import com.b230408.musicplayer.database.dao.PlayHistoryDao
import com.b230408.musicplayer.database.dao.TrackDao
import com.b230408.musicplayer.database.entity.PlayHistoryEntity
import com.b230408.musicplayer.database.entity.TrackEntity
import com.b230408.musicplayer.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放历史管理器
 * 负责播放历史的记录和查询
 */
class PlayHistoryManager(private val context: Context) {
    
    private val database: MusicPlayerDatabase by lazy {
        MusicPlayerDatabase.getDatabase(context)
    }
    
    private val playHistoryDao: PlayHistoryDao by lazy { database.playHistoryDao() }
    private val trackDao: TrackDao by lazy { database.trackDao() }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 记录播放历史
     */
    fun recordPlayHistory(track: Track, playDuration: Long = 0L) {
        scope.launch {
            try {
                // 确保歌曲已保存到数据库
                val trackEntity = TrackEntity(
                    id = track.id,
                    path = track.path,
                    uri = track.uri.toString(),
                    fileName = track.fileName,
                    fileSize = track.fileSize,
                    duration = track.duration,
                    format = track.format,
                    title = track.metadata?.title,
                    artist = track.metadata?.artist,
                    album = track.metadata?.album,
                    dateAdded = track.dateAdded
                )
                trackDao.insertTrack(trackEntity)
                
                // 记录播放历史
                val history = PlayHistoryEntity(
                    trackId = track.id,
                    playTime = System.currentTimeMillis(),
                    playDuration = playDuration
                )
                playHistoryDao.insertHistory(history)
            } catch (e: Exception) {
                android.util.Log.e("PlayHistoryManager", "记录播放历史失败", e)
            }
        }
    }
    
    /**
     * 获取所有播放历史（Flow形式，按时间倒序）
     */
    fun getAllHistory(): Flow<List<Pair<Track, PlayHistoryEntity>>> {
        return playHistoryDao.getAllHistory().flatMapLatest { historyList ->
            flow {
                // 在 IO 线程上执行数据库查询
                val result = withContext(Dispatchers.IO) {
                    historyList.mapNotNull { history ->
                        try {
                            val trackEntity = trackDao.getTrackById(history.trackId)
                            trackEntity?.let { entity ->
                                // 转换为Track对象
                                val track = Track(
                                    id = entity.id,
                                    path = entity.path,
                                    uri = android.net.Uri.parse(entity.uri),
                                    fileName = entity.fileName,
                                    fileSize = entity.fileSize,
                                    duration = entity.duration,
                                    format = entity.format,
                                    metadata = com.b230408.musicplayer.metadata.model.Metadata(
                                        title = entity.title,
                                        artist = entity.artist,
                                        album = entity.album
                                    ),
                                    dateAdded = entity.dateAdded
                                )
                                Pair(track, history)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PlayHistoryManager", "获取歌曲信息失败: ${history.trackId}", e)
                            null
                        }
                    }
                }
                emit(result)
            }
        }.flowOn(Dispatchers.IO)
    }
    
    /**
     * 获取最近的播放历史（同步方法）
     */
    suspend fun getRecentHistory(limit: Int = 50): List<Pair<Track, PlayHistoryEntity>> {
        return withContext(Dispatchers.IO) {
            val historyList = playHistoryDao.getRecentHistory(limit)
            historyList.mapNotNull { history ->
                val trackEntity = trackDao.getTrackById(history.trackId)
                trackEntity?.let { entity ->
                    val track = Track(
                        id = entity.id,
                        path = entity.path,
                        uri = android.net.Uri.parse(entity.uri),
                        fileName = entity.fileName,
                        fileSize = entity.fileSize,
                        duration = entity.duration,
                        format = entity.format,
                        metadata = com.b230408.musicplayer.metadata.model.Metadata(
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album
                        ),
                        dateAdded = entity.dateAdded
                    )
                    Pair(track, history)
                }
            }
        }
    }
    
    /**
     * 删除播放历史
     */
    suspend fun deleteHistory(history: PlayHistoryEntity) {
        withContext(Dispatchers.IO) {
            playHistoryDao.deleteHistory(history)
        }
    }
    
    /**
     * 清空所有播放历史
     */
    suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            playHistoryDao.deleteAllHistory()
        }
    }
    
    /**
     * 删除指定时间之前的播放历史
     */
    suspend fun deleteHistoryBefore(beforeTime: Long) {
        withContext(Dispatchers.IO) {
            playHistoryDao.deleteHistoryBefore(beforeTime)
        }
    }
    
    /**
     * 获取播放历史数量
     */
    suspend fun getHistoryCount(): Int {
        return withContext(Dispatchers.IO) {
            playHistoryDao.getHistoryCount()
        }
    }
}

