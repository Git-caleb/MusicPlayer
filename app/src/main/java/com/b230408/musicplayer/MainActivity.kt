package com.b230408.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.ui.pages.PlayerUI
import com.b230408.musicplayer.ui.theme.MusicPlayerTheme
import com.b230408.musicplayer.ui.viewmodel.PlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * 主界面Activity
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MusicPlayerTheme {
                MainScreen()
            }
        }
    }
    
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.b230408.musicplayer.ui.viewmodel.PlayerViewModelFactory(context)
    )
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showPlayerScreen by remember { mutableStateOf(false) }
    
    // 请求权限
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(permissionState.allPermissionsGranted) {
        try {
            if (permissionState.allPermissionsGranted) {
                android.util.Log.d("MainActivity", "权限已授予，开始扫描音乐文件")
                viewModel.scanMusicFiles()
            } else {
                android.util.Log.d("MainActivity", "权限未授予，请求权限")
                permissionState.launchMultiplePermissionRequest()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "权限请求或扫描失败", e)
            e.printStackTrace()
            // 即使权限请求失败，也不让应用崩溃
        }
    }
    
    if (showPlayerScreen && currentTrack != null) {
        // 播放界面
        PlayerUI(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            playbackMode = playbackMode,
            onPlayPause = {
                if (isPlaying) viewModel.pause() else viewModel.play()
            },
            onPrevious = { viewModel.previous() },
            onNext = { viewModel.next() },
            onSeekTo = { viewModel.seekTo(it) },
            onPlaybackModeChange = { viewModel.setPlaybackMode(it) },
            onSeekForward = { viewModel.seekForward() },
            onSeekBackward = { viewModel.seekBackward() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // 主列表界面
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 顶部栏
            TopAppBar(
                title = { Text("音乐播放器") },
                actions = {
                    IconButton(onClick = { viewModel.scanMusicFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "扫描音乐")
                    }
                }
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 播放列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = {
                                viewModel.setPlaylist(playlist, 0)
                                showPlayerScreen = true
                            }
                        )
                    }
                    
                    // 如果列表为空，显示提示
                    if (playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "没有找到音乐文件",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 底部控制栏
            if (currentTrack != null) {
                BottomPlayerBar(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) viewModel.pause() else viewModel.play()
                    },
                    onClick = { showPlayerScreen = true }
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: com.b230408.musicplayer.playlist.model.Playlist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${playlist.getTrackCount()} 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放")
            }
        }
    }
}

@Composable
fun BottomPlayerBar(
    track: Track,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面缩略图
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.getDisplayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.getDisplayArtist(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 播放/暂停按钮
            IconButton(onClick = onPlayPause) {
                if (isPlaying) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "暂停"
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放"
                    )
                }
            }
        }
    }
}
