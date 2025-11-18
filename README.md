# 音乐播放器 (MusicPlayer)

一个基于 Android Jetpack Compose 和 ExoPlayer 的现代化音乐播放器应用。

## 项目特性

### 核心功能
- ✅ **音乐文件扫描**: 自动扫描设备上的音乐文件（支持 MediaStore 和目录扫描）
- ✅ **播放列表管理**: 创建、编辑、删除播放列表，支持 Room 数据库持久化存储
- ✅ **播放列表详情页**: 查看播放列表中的所有歌曲，选择播放
- ✅ **音频播放控制**: 播放/暂停、上一首/下一首、快进/快退、进度控制
- ✅ **播放模式**: 顺序播放、单曲循环、随机播放（支持播放历史记录）
- ✅ **前台服务播放**: 后台播放，通知栏控制
- ✅ **ID3 标签读取**: 自动读取音乐文件的元数据（标题、艺术家、专辑等）
- ✅ **Material3 UI**: 现代化的 Material Design 3 界面
- ✅ **智能去重**: 自动清理重复的播放列表，确保"所有音乐"只有一个最新版本

### 技术栈
- **UI 框架**: Jetpack Compose + Material3
- **架构模式**: MVVM (Model-View-ViewModel)
- **播放引擎**: ExoPlayer (AndroidX Media3)
- **数据持久化**: Room Database
- **异步处理**: Kotlin Coroutines + Flow
- **元数据处理**: JAudioTagger
- **数据序列化**: Kotlinx Serialization
- **图片加载**: Coil
- **权限管理**: Accompanist Permissions

## 项目结构

```
com.b230408.musicplayer/
├── player/              # 播放器核心模块
│   ├── controller/     # 播放控制器（MusicController）
│   ├── service/        # 后台播放服务（MusicService）
│   ├── model/          # 数据模型（Track）
│   └── utils/          # 工具类（PlaybackMode）
│
├── playlist/            # 播放列表模块
│   ├── manager/        # 列表管理器（PlaylistManager）
│   ├── model/          # 数据模型（Playlist）
│   └── scanner/        # 文件扫描器（FileScanner）
│
├── database/            # 数据库模块
│   ├── entity/         # Room 实体（PlaylistEntity, TrackEntity, PlaylistTrackEntity）
│   ├── dao/            # 数据访问对象（PlaylistDao, TrackDao, PlaylistTrackDao）
│   ├── converter/      # 类型转换器（UriConverter, MetadataConverter）
│   └── MusicPlayerDatabase.kt  # 数据库实例
│
├── metadata/            # 元数据模块
│   ├── reader/         # ID3 读取（ID3Reader）
│   ├── editor/         # ID3 编辑（ID3Editor）
│   └── model/          # 数据模型（Metadata）
│
├── ui/                  # UI 模块
│   ├── pages/          # 页面组件（PlayerUI, SplashUI）
│   ├── viewmodel/      # ViewModel（PlayerViewModel, PlayerViewModelFactory）
│   ├── theme/          # 主题配置
│   └── widgets/        # 自定义组件
│
└── utils/               # 工具类模块
    ├── Constants.kt    # 应用常量
    └── FileUtils.kt    # 文件工具类
```

详细架构说明请查看 [ARCHITECTURE.md](ARCHITECTURE.md)

## 主要组件

### MusicService
后台播放服务，使用 ExoPlayer 实现音频播放。

**特性**:
- 前台服务（通知栏显示）
- AudioFocus 管理
- 播放状态监听和通知
- 通知栏控制（播放/暂停）
- 播放进度实时更新

### MusicController
播放逻辑控制器（单例模式）。

**功能**:
- 播放列表管理
- 播放模式切换（顺序/单曲循环/随机）
- 播放控制（播放/暂停/上一首/下一首）
- 进度控制（快进/快退）
- 播放历史记录（用于随机模式）

### FileScanner
音乐文件扫描器。

**扫描策略**:
1. 优先使用 MediaStore（Android 10+）
2. 回退到目录直接扫描（/storage/emulated/0/Music）
3. 自动触发 MediaScannerConnection 更新 MediaStore
4. 支持 MP3、AAC、OGG、WAV 格式

### PlaylistManager
播放列表管理器，使用 Room 数据库进行持久化。

**功能**:
- 创建/删除/更新播放列表
- Room 数据库持久化存储
- 播放列表查询（按名称、ID）
- 自动去重（确保"所有音乐"只有一个）
- 播放列表与歌曲的关联管理

### PlayerViewModel
播放器 ViewModel，管理 UI 状态。

**功能**:
- 播放状态管理（isPlaying, currentTrack, currentPosition, duration）
- 播放列表列表管理
- 音乐文件扫描
- 播放控制（播放/暂停/上一首/下一首/快进/快退）
- 播放模式切换

## 依赖库

主要依赖版本（详见 `gradle/libs.versions.toml`）：

```gradle
// Jetpack Compose
composeBom = "2024.09.00"
androidx-compose-material3

// Media3 ExoPlayer
media3 = "1.4.1"
androidx-media3-exoplayer
androidx-media3-ui

// Room Database
room = "2.6.1"
androidx-room-runtime
androidx-room-ktx
androidx-room-compiler

// Coroutines
coroutines = "1.9.0"
kotlinx-coroutines-android

// ViewModel
viewmodel = "2.9.4"
androidx-lifecycle-viewmodel-compose

// Coil (图片加载)
coil-compose = "2.6.0"

// Permissions
permissions = "0.34.0"
accompanist-permissions

// JAudioTagger (元数据处理)
jAudioTagger = "2.0.1"
org.jaudiotagger:jaudiotagger

// Serialization
kotlinx-serialization-json
```

## 权限要求

```xml
<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- 通知权限 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- 唤醒锁 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 使用说明

### 启动应用
1. 应用启动后会请求存储权限（Android 13+ 需要 READ_MEDIA_AUDIO）
2. 自动扫描设备上的音乐文件
3. 创建"所有音乐"播放列表并显示

### 播放音乐
1. 点击播放列表（如"所有音乐"）进入播放列表详情页
2. 在详情页中选择要播放的歌曲
3. 进入播放界面，使用控制按钮进行播放控制
4. 支持播放模式切换（顺序/单曲循环/随机）
5. 支持快进/快退（10秒）
6. 支持进度条拖拽定位

### 管理播放列表
- 自动创建"所有音乐"播放列表
- 点击右上角刷新按钮可重新扫描音乐文件
- 刷新时会自动更新"所有音乐"播放列表，不会创建重复的
- 播放列表数据持久化存储在 Room 数据库中

### 播放界面
- 显示专辑封面、歌曲标题、艺术家
- 实时显示播放进度和总时长
- 支持播放/暂停、上一首/下一首、快进/快退
- 支持播放模式切换
- 点击左上角返回按钮可返回播放列表

## 开发说明

### 编译要求
- Android Studio Hedgehog | 2023.1.1 或更高
- JDK 17 或更高
- Android SDK API 36 (Android 14)
- Gradle 8.0+
- Kotlin 2.0.21+

### 构建项目
```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 运行应用
```bash
# Windows
gradlew.bat installDebug

# Linux/Mac
./gradlew installDebug
```

### 数据库迁移
项目使用 Room 数据库，数据库版本为 1。如需修改数据库结构：
1. 更新 `MusicPlayerDatabase` 中的 `version`
2. 添加 Migration 对象
3. 更新相应的 Entity 和 DAO

## 已修复问题

- ✅ 播放进度条不动的问题
- ✅ 播放无声音的问题（音频焦点请求优化）
- ✅ 播放界面时长显示异常（加载状态处理）
- ✅ 播放界面返回按钮无反应
- ✅ 点击播放列表直接播放的问题（添加了详情页）
- ✅ 刷新时重复创建播放列表的问题（自动去重）

## 已知限制

- 部分设备上 MediaStore 扫描可能返回空结果（已实现目录扫描作为备用方案）
- ID3 标签读取在某些文件格式上可能失败（已添加异常处理）
- 当前仅支持本地音乐文件播放
- 歌词功能尚未实现

## 待实现功能

- [ ] 歌词同步显示
- [ ] ID3 标签编辑
- [ ] 播放历史记录
- [ ] 收藏功能
- [ ] 在线音乐搜索
- [ ] 播放列表导入/导出
- [ ] 主题切换（亮色/暗色）
- [ ] 播放列表排序和搜索
- [ ] 自定义播放列表创建
- [ ] 播放列表重命名和删除

## 技术亮点

1. **MVVM 架构**: 清晰的架构分层，便于维护和扩展
2. **Room 数据库**: 使用 Room 进行数据持久化，支持 Flow 响应式查询
3. **前台服务**: 使用前台服务确保后台播放稳定性
4. **AudioFocus 管理**: 正确处理音频焦点，避免与其他应用冲突
5. **Material3 UI**: 现代化的 Material Design 3 界面设计
6. **智能去重**: 自动清理重复的播放列表，确保数据一致性
7. **播放历史**: 随机模式下记录播放历史，支持"上一首"功能

## 许可证

本项目仅供学习和研究使用。

## 作者

b230408

## 更新日志

### v1.0 (2024)
- 初始版本发布
- 实现基本的音乐播放功能
- 使用 Room 数据库进行数据持久化
- 实现播放列表管理
- 添加播放列表详情页
- 修复播放进度和音频播放问题
- 实现播放列表自动去重功能
