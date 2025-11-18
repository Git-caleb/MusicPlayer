package com.b230408.musicplayer.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    lyrics: List<String> = emptyList(),
    currentLyricIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(currentPosition, duration) {
        mutableStateOf(
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部栏：返回按钮和播放模式
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                        PlaybackMode.SEQUENTIAL -> Icons.Default.List
                        PlaybackMode.REPEAT_ONE -> Icons.Default.Refresh
                        PlaybackMode.SHUFFLE -> Icons.Default.ShuffleOn
                    },
                    contentDescription = "播放模式"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 专辑封面
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentTrack?.metadata?.coverArt ?: android.R.drawable.ic_media_play)
                    .crossfade(true)
                    .build(),
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 歌词显示区域（可选）
        if (lyrics.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                lyrics.forEachIndexed { index, lyric ->
                    Text(
                        text = lyric,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == currentLyricIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 倒退10秒
            CustomIconButton(
                onClick = onSeekBackward,
                icon = Icons.Default.FastRewind,
                iconSize = 28.dp,
                buttonSize = 48.dp
            )
            
            // 上一首
            CustomIconButton(
                onClick = onPrevious,
                icon = Icons.Default.SkipPrevious,
                iconSize = 32.dp,
                buttonSize = 56.dp
            )
            
            // 播放/暂停
            if (isPlaying) {
                CustomIconButton(
                    onClick = onPlayPause,
                    icon = Icons.Default.Pause,
                    iconSize = 48.dp,
                    buttonSize = 72.dp,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                CustomIconButton(
                    onClick = onPlayPause,
                    icon = Icons.Default.PlayArrow,
                    iconSize = 48.dp,
                    buttonSize = 72.dp,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // 下一首
            CustomIconButton(
                onClick = onNext,
                icon = Icons.Default.SkipNext,
                iconSize = 32.dp,
                buttonSize = 56.dp
            )
            
            // 快进10秒
            CustomIconButton(
                onClick = onSeekForward,
                icon = Icons.Default.FastForward,
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
    return String.format("%02d:%02d", minutes, seconds)
}
