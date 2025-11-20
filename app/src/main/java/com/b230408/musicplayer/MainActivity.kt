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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.playlist.model.Playlist
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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
    val lyrics by viewModel.lyrics.collectAsState()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsState()
    val coverImageUrl by viewModel.coverImageUrl.collectAsState()
    
    var showPlayerScreen by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showPlaylistDetail by remember { mutableStateOf(false) }
    // 跟踪是否从播放列表详情页进入播放页面
    var cameFromPlaylistDetail by remember { mutableStateOf(false) }
    // 创建歌单对话框
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    // 编辑歌单对话框（重命名）
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    // 添加歌曲到歌单对话框
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var playlistToAddTrack by remember { mutableStateOf<Playlist?>(null) }
    // 播放历史记录界面
    var showPlayHistory by remember { mutableStateOf(false) }
    
    // 请求权限
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    
    // 请求权限
    LaunchedEffect(Unit) {
        try {
            if (!permissionState.allPermissionsGranted) {
                android.util.Log.d("MainActivity", "权限未授予，请求权限")
                permissionState.launchMultiplePermissionRequest()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "权限请求失败", e)
            e.printStackTrace()
        }
    }
    
    // 监听权限授予状态，授予后延迟扫描（避免启动时立即扫描导致卡顿）
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            android.util.Log.d("MainActivity", "权限已授予，延迟扫描音乐文件")
            // 延迟扫描，先让界面显示出来，提升启动速度
            kotlinx.coroutines.delay(500)
            viewModel.scanMusicFiles()
        }
    }
    
    when {
        showPlayerScreen && currentTrack != null -> {
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
                onBack = { 
                    showPlayerScreen = false
                    // 如果是从播放列表详情页进入的，恢复显示播放列表详情页
                    if (cameFromPlaylistDetail) {
                        showPlaylistDetail = true
                        cameFromPlaylistDetail = false
                    }
                },
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                coverImageUrl = coverImageUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        showPlaylistDetail && selectedPlaylist != null -> {
            // 播放列表详情页
            // 每次都从 playlists 获取最新数据，确保实时更新
            val currentPlaylistId = selectedPlaylist!!.id
            // 使用 playlists 中每个歌单的 tracks.size 和 tracks 的 ID 列表作为 key，确保变化时重新计算
            val currentPlaylistKey = playlists.find { it.id == currentPlaylistId }?.let { 
                "${it.id}:${it.tracks.size}:${it.tracks.map { t -> t.id }.sorted().joinToString(",")}"
            } ?: "$currentPlaylistId:0:"
            val updatedPlaylist = remember(currentPlaylistKey) {
                playlists.find { it.id == currentPlaylistId } ?: selectedPlaylist!!
            }
            
            // 当歌单更新时，自动同步 selectedPlaylist
            LaunchedEffect(currentPlaylistKey) {
                val latestPlaylist = playlists.find { it.id == currentPlaylistId }
                if (latestPlaylist != null && selectedPlaylist?.id == latestPlaylist.id) {
                    selectedPlaylist = latestPlaylist
                }
            }
            
            PlaylistDetailScreen(
                playlist = updatedPlaylist,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onBack = { 
                    showPlaylistDetail = false
                    selectedPlaylist = null
                },
                onTrackClick = { track: Track, index: Int ->
                    // 检查当前播放的歌曲是否就是点击的歌曲
                    val isCurrentTrack = currentTrack?.path == track.path || 
                                        currentTrack?.uri == track.uri
                    
                    if (isCurrentTrack) {
                        // 如果是同一首歌，只打开播放页面，不重新播放
                        cameFromPlaylistDetail = true
                        showPlaylistDetail = false
                        showPlayerScreen = true
                    } else {
                        // 如果不是同一首歌，设置播放列表并播放
                        viewModel.setPlaylist(selectedPlaylist!!, index)
                        viewModel.play()
                        // 标记是从播放列表详情页进入的
                        cameFromPlaylistDetail = true
                        showPlaylistDetail = false
                        showPlayerScreen = true
                    }
                },
                onPlayPause = {
                    if (isPlaying) viewModel.pause() else viewModel.play()
                },
                onAddTrack = {
                    // 使用最新的歌单数据
                    val latestPlaylist = playlists.find { it.id == selectedPlaylist?.id }
                    playlistToAddTrack = latestPlaylist ?: selectedPlaylist
                    showAddTrackDialog = true
                },
                onRename = {
                    playlistToRename = selectedPlaylist
                    showRenamePlaylistDialog = true
                },
                onDelete = {
                    viewModel.deletePlaylist(selectedPlaylist!!.id)
                    showPlaylistDetail = false
                    selectedPlaylist = null
                },
                onRemoveTrack = { track ->
                    viewModel.removeTrackFromPlaylist(selectedPlaylist!!.id, track)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        showPlayHistory -> {
            // 播放历史记录界面
            PlayHistoryScreen(
                viewModel = viewModel,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onBack = { showPlayHistory = false },
                onTrackClick = { track ->
                    // 查找歌曲所在的播放列表
                    val playlist = playlists.find { playlist ->
                        playlist.tracks.any { it.id == track.id }
                    }
                    if (playlist != null) {
                        val index = playlist.tracks.indexOfFirst { it.id == track.id }
                        if (index >= 0) {
                            viewModel.setPlaylist(playlist, index)
                            viewModel.play()
                            showPlayHistory = false
                            showPlayerScreen = true
                        }
                    }
                },
                onPlayPause = {
                    if (isPlaying) viewModel.pause() else viewModel.play()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        else -> {
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
                        IconButton(onClick = { showPlayHistory = true }) {
                            Icon(Icons.Filled.History, contentDescription = "播放历史")
                        }
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "创建歌单")
                        }
                        IconButton(onClick = { viewModel.scanMusicFiles() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "扫描音乐")
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
                                    selectedPlaylist = playlist
                                    showPlaylistDetail = true
                                },
                                onRename = {
                                    playlistToRename = playlist
                                    showRenamePlaylistDialog = true
                                },
                                onDelete = {
                                    viewModel.deletePlaylist(playlist.id)
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
                                            Icons.Filled.MusicNote,
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
                        onClick = { 
                            // 从主列表界面进入播放页面，标记不是从播放列表详情页进入
                            cameFromPlaylistDetail = false
                            showPlayerScreen = true 
                        }
                    )
                }
            }
        }
    }
    
    // 创建歌单对话框
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                }
            }
        )
    }
    
    // 重命名歌单对话框
    if (showRenamePlaylistDialog && playlistToRename != null) {
        RenamePlaylistDialog(
            currentName = playlistToRename!!.name,
            onDismiss = { 
                showRenamePlaylistDialog = false
                playlistToRename = null
            },
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    viewModel.renamePlaylist(playlistToRename!!.id, newName)
                    showRenamePlaylistDialog = false
                    playlistToRename = null
                }
            }
        )
    }
    
    // 添加歌曲到歌单对话框
    if (showAddTrackDialog && playlistToAddTrack != null) {
        // 使用 playlists 中对应歌单的 tracks.size 和 tracks 的 ID 列表作为 key，确保实时更新
        val dialogPlaylistKey = playlists.find { it.id == playlistToAddTrack!!.id }?.let { 
            "${it.id}:${it.tracks.size}:${it.tracks.map { t -> t.id }.sorted().joinToString(",")}"
        } ?: "${playlistToAddTrack!!.id}:0:"
        // 从 playlists 获取最新的歌单数据，确保对话框显示的是最新状态
        val latestPlaylistForDialog = remember(dialogPlaylistKey) {
            playlists.find { it.id == playlistToAddTrack!!.id } ?: playlistToAddTrack!!
        }
        AddTrackToPlaylistDialog(
            playlist = latestPlaylistForDialog,
            viewModel = viewModel,
            onDismiss = {
                showAddTrackDialog = false
                playlistToAddTrack = null
            },
            onAddTrack = { track ->
                viewModel.addTrackToPlaylist(latestPlaylistForDialog.id, track)
                // 添加后立即从 playlists 更新 playlistToAddTrack，以便对话框实时刷新
                val updatedPlaylist = playlists.find { it.id == latestPlaylistForDialog.id }
                if (updatedPlaylist != null) {
                    playlistToAddTrack = updatedPlaylist
                }
            }
        )
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
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
                Icons.Filled.MusicNote,
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
            
            Box {
                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    )
                }
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
                Icons.Filled.MusicNote,
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
                        Icons.Filled.Pause,
                        contentDescription = "暂停"
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放"
                    )
                }
            }
        }
    }
}

/**
 * 播放列表详情页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onPlayPause: () -> Unit,
    onAddTrack: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemoveTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        TopAppBar(
            title = { Text(playlist.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                // 只有非"所有音乐"歌单才显示编辑选项
                if (playlist.name != "所有音乐") {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加歌曲") },
                            onClick = {
                                showMenu = false
                                onAddTrack()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除歌单") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        )
        
        // 歌曲列表
        if (playlist.tracks.isEmpty()) {
            // 空歌单提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "歌单是空的",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (playlist.name != "所有音乐") {
                            "点击右上角菜单添加歌曲"
                        } else {
                            "请先扫描音乐文件"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 使用 key 确保当 tracks 变化时重新组合
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = playlist.tracks,
                    key = { track -> track.id }
                ) { track ->
                    val index = playlist.tracks.indexOf(track)
                    val isCurrentTrack = currentTrack?.path == track.path || 
                                        currentTrack?.uri == track.uri
                    TrackItem(
                        track = track,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isPlaying && isCurrentTrack,
                        onClick = { onTrackClick(track, index) },
                        onPlayPause = onPlayPause,
                        onRemove = if (playlist.name != "所有音乐") {
                            { onRemoveTrack(track) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

/**
 * 歌曲列表项
 */
@Composable
fun TrackItem(
    track: Track,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.getDisplayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.getDisplayArtist(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 如果是当前播放的歌曲，显示播放/暂停按钮；否则显示播放按钮
            if (isCurrentTrack) {
                IconButton(
                    onClick = {
                        onPlayPause()
                    }
                ) {
                    if (isPlaying) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = "暂停",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放"
                    )
                }
            }
            
            // 如果有删除功能，显示更多选项
            if (onRemove != null) {
                Box {
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("从歌单移除") },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Remove, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 创建歌单对话框
 */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建歌单") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(playlistName) },
                enabled = playlistName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 重命名歌单对话框
 */
@Composable
fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名歌单") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(playlistName) },
                enabled = playlistName.isNotBlank() && playlistName != currentName
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 添加歌曲到歌单对话框
 */
@Composable
fun AddTrackToPlaylistDialog(
    playlist: Playlist,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onAddTrack: (Track) -> Unit
) {
    // 使用 Flow 异步加载，避免阻塞 UI
    val allTracks by viewModel.allTracks.collectAsState()
    // 从 ViewModel 获取最新的歌单数据，确保实时更新
    val playlists by viewModel.playlists.collectAsState()
    // 使用 tracks.size 和 tracks 的 ID 列表作为 key，确保实时更新
    val playlistKey = playlists.find { it.id == playlist.id }?.let { 
        "${it.id}:${it.tracks.size}:${it.tracks.map { t -> t.id }.sorted().joinToString(",")}"
    } ?: "${playlist.id}:${playlist.tracks.size}:${playlist.tracks.map { t -> t.id }.sorted().joinToString(",")}"
    val currentPlaylist = remember(playlistKey) {
        playlists.find { it.id == playlist.id } ?: playlist
    }
    
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    
    // 初始化时异步加载
    LaunchedEffect(Unit) {
        if (allTracks.isEmpty()) {
            scope.launch {
                viewModel.getAllTracks()
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    
    // 监听 currentPlaylist.tracks 的变化，实时更新可用歌曲列表
    // 使用 tracks 的 ID 列表作为 key，确保实时更新
    val playlistTrackIdsKey = currentPlaylist.tracks.map { it.id }.sorted().joinToString(",")
    val playlistTrackIds = remember(currentPlaylist.tracks.size, playlistTrackIdsKey) {
        currentPlaylist.tracks.map { it.id }.toSet()
    }
    
    // 实时计算可用歌曲列表，当 currentPlaylist.tracks 变化时自动更新
    val availableTracks = remember(allTracks.size, playlistTrackIds.size, playlistTrackIdsKey) {
        if (allTracks.isEmpty()) {
            emptyList()
        } else {
            allTracks.filter { track ->
                track.id !in playlistTrackIds
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加歌曲到 ${currentPlaylist.name}") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (availableTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("没有可添加的歌曲")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(availableTracks) { track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onAddTrack(track) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.getDisplayTitle(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
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
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "添加",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // 不需要确认按钮，点击歌曲即可添加
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 播放历史记录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHistoryScreen(
    viewModel: PlayerViewModel,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playHistory: List<Pair<Track, com.b230408.musicplayer.database.entity.PlayHistoryEntity>> by 
        viewModel.getPlayHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        TopAppBar(
            title = { Text("播放历史") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = { showClearDialog = true },
                    enabled = playHistory.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空历史")
                }
            }
        )
        
        if (playHistory.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "暂无播放历史",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // 播放历史列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = playHistory,
                    key = { "${it.second.id}_${it.second.playTime}" }
                ) { item ->
                    val (track, history) = item
                    val isCurrentTrack = currentTrack?.id == track.id
                    val playTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(history.playTime))
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentTrack) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.getDisplayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
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
                                Text(
                                    text = playTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (isCurrentTrack) {
                                IconButton(onClick = onPlayPause) {
                                    Icon(
                                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "暂停" else "播放",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 清空历史对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空播放历史") },
            text = { Text("确定要清空所有播放历史吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearPlayHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
