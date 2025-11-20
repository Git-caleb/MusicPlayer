package com.b230408.musicplayer.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import com.b230408.musicplayer.player.model.Track
import com.b230408.musicplayer.player.utils.PlaybackMode
import com.b230408.musicplayer.ui.widgets.CustomIconButton

/**
 * 播放界面UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerUI(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackMode: PlaybackMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    lyrics: List<String> = emptyList(),
    currentLyricIndex: Int = -1
) {
    var sliderPosition by remember(currentPosition, duration) {
        mutableFloatStateOf(
            if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f
        )
    }
    
    val animatedPosition by animateFloatAsState(
        targetValue = sliderPosition,
        label = "slider_position"
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部栏：返回按钮和播放模式
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            
            // 播放模式按钮
            IconButton(onClick = {
                val nextMode = when (playbackMode) {
                    PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
                    PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                    PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
                }
                onPlaybackModeChange(nextMode)
            }) {
                Icon(
                    imageVector = when (playbackMode) {
                        PlaybackMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.List
                        PlaybackMode.REPEAT_ONE -> Icons.Filled.Refresh
                        PlaybackMode.SHUFFLE -> Icons.Filled.ShuffleOn
                    },
                    contentDescription = "播放模式"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 专辑封面（缩小尺寸以节省空间）
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 优先使用 coverBitmap，如果没有则使用 coverArt（ByteArray），最后使用默认图标
            val coverBitmap = remember(currentTrack?.metadata) {
                when {
                    currentTrack?.metadata?.coverBitmap != null -> {
                        // 直接使用 Bitmap
                        android.util.Log.d("PlayerUI", "使用 coverBitmap")
                        currentTrack.metadata.coverBitmap
                    }
                    currentTrack?.metadata?.coverArt != null -> {
                        // 从 ByteArray 解码 Bitmap
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(
                                currentTrack.metadata.coverArt,
                                0,
                                currentTrack.metadata.coverArt.size
                            )
                            android.util.Log.d("PlayerUI", "从 coverArt 解码 Bitmap: ${bitmap?.width}x${bitmap?.height}")
                            bitmap
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerUI", "解码 coverArt 失败", e)
                            null
                        }
                    }
                    else -> {
                        android.util.Log.d("PlayerUI", "没有封面数据，使用默认图标")
                        null
                    }
                }
            }
            
            if (coverBitmap != null) {
                // 使用 Image 组件直接显示 Bitmap
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 使用 AsyncImage 显示默认图标
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(android.R.drawable.ic_media_play)
                        .crossfade(true)
                        .build(),
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 歌曲信息
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTrack?.getDisplayTitle() ?: "未知标题",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = currentTrack?.getDisplayArtist() ?: "未知艺术家",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 进度条
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = if (duration > 0 && duration != Long.MAX_VALUE) animatedPosition else 0f,
                onValueChange = { newValue ->
                    if (duration > 0 && duration != Long.MAX_VALUE) {
                        sliderPosition = newValue
                        val newPosition = (newValue * duration).toLong()
                        onSeekTo(newPosition)
                    }
                },
                enabled = duration > 0 && duration != Long.MAX_VALUE,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = if (duration > 0 && duration != Long.MAX_VALUE) {
                        formatTime(duration)
                    } else {
                        "--:--"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 歌词显示区域（可选）
        if (lyrics.isNotEmpty()) {
            val lazyListState = rememberLazyListState()
            val density = LocalDensity.current
            
            // 自动滚动到当前歌词行，使其居中显示
            LaunchedEffect(currentLyricIndex, lyrics.size) {
                if (currentLyricIndex >= 0 && currentLyricIndex < lyrics.size) {
                    kotlinx.coroutines.delay(200) // 等待布局完成
                    try {
                        // 获取视口高度（歌词区域高度）
                        val viewportHeightPx = with(density) { 180.dp.toPx() }
                        // 估算每行高度（包括 padding）
                        val estimatedLineHeightPx = with(density) { 48.dp.toPx() }
                        
                        // 计算 scrollOffset，使项目中心对齐到视口中心
                        // scrollOffset 的含义：
                        // - 0: 项目顶部对齐到视口顶部
                        // - 正数: 项目顶部在视口顶部上方（项目被向下推）
                        // - 负数: 项目顶部在视口顶部下方（项目被向上推）
                        // 要让项目中心在视口中心：
                        // 项目顶部应该在 = 视口高度/2 - 项目高度/2（相对于视口顶部）
                        // 所以 scrollOffset 应该是负数：-(视口高度/2 - 项目高度/2)
                        val centerOffset = (viewportHeightPx / 2f) - (estimatedLineHeightPx / 2f)
                        val scrollOffset = -centerOffset.toInt()
                        
                        android.util.Log.d("PlayerUI", "滚动到歌词行 $currentLyricIndex, scrollOffset=$scrollOffset, 视口高度=${viewportHeightPx}px, 行高度=${estimatedLineHeightPx}px, centerOffset=$centerOffset")
                        
                        lazyListState.animateScrollToItem(
                            index = currentLyricIndex,
                            scrollOffset = scrollOffset
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerUI", "歌词滚动失败", e)
                        // 如果滚动失败，尝试直接滚动到项目（不居中，但至少可见）
                        try {
                            lazyListState.scrollToItem(currentLyricIndex)
                        } catch (e2: Exception) {
                            android.util.Log.e("PlayerUI", "歌词滚动失败（备用方案）", e2)
                        }
                    }
                }
            }
            
            // 使用 LazyColumn 实现更好的滚动性能
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 180.dp)
                    .fillMaxWidth(),
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
            ) {
                items(lyrics.size, key = { it }) { index ->
                    val lyric = lyrics[index]
                    val isCurrent = index == currentLyricIndex
                    
                    Text(
                        text = lyric,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = if (isCurrent) 18.sp else 16.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            // 没有歌词时，使用 Spacer 填充空间
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // 控制按钮（始终在底部，统一位置）
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 倒退10秒
            CustomIconButton(
                onClick = onSeekBackward,
                icon = Icons.Filled.FastRewind,
                iconSize = 28.dp,
                buttonSize = 48.dp
            )
            
            // 上一首
            CustomIconButton(
                onClick = onPrevious,
                icon = Icons.Filled.SkipPrevious,
                iconSize = 32.dp,
                buttonSize = 56.dp
            )
            
            // 播放/暂停
            if (isPlaying) {
                CustomIconButton(
                    onClick = onPlayPause,
                    icon = Icons.Filled.Pause,
                    iconSize = 48.dp,
                    buttonSize = 72.dp,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                CustomIconButton(
                    onClick = onPlayPause,
                    icon = Icons.Filled.PlayArrow,
                    iconSize = 48.dp,
                    buttonSize = 72.dp,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // 下一首
            CustomIconButton(
                onClick = onNext,
                icon = Icons.Filled.SkipNext,
                iconSize = 32.dp,
                buttonSize = 56.dp
            )
            
            // 快进10秒
            CustomIconButton(
                onClick = onSeekForward,
                icon = Icons.Filled.FastForward,
                iconSize = 28.dp,
                buttonSize = 48.dp
            )
        }
    }
}

/**
 * 格式化时间（毫秒转分:秒）
 */
private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
