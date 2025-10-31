# 音乐播放器 (MusicPlayer)

一个基于 Android Jetpack Compose 和 ExoPlayer 的现代化音乐播放器应用。

## 项目特性

### 核心功能
- ✅ **音乐文件扫描**: 自动扫描设备上的音乐文件（支持 MediaStore 和目录扫描）
- ✅ **播放列表管理**: 创建、编辑、删除播放列表，支持持久化存储
- ✅ **音频播放控制**: 播放/暂停、上一首/下一首、快进/快退、进度控制
- ✅ **播放模式**: 顺序播放、单曲循环、随机播放
- ✅ **前台服务播放**: 后台播放，通知栏控制
- ✅ **ID3 标签读取**: 自动读取音乐文件的元数据（标题、艺术家、专辑等）
- ✅ **Material3 UI**: 现代化的 Material Design 3 界面

### 技术栈
- **UI 框架**: Jetpack Compose + Material3
- **架构模式**: MVVM (Model-View-ViewModel)
- **播放引擎**: ExoPlayer (AndroidX Media3)
- **异步处理**: Kotlin Coroutines + Flow
- **元数据处理**: JAudioTagger
- **数据序列化**: Kotlinx Serialization
- **图片加载**: Coil
- **权限管理**: Accompanist Permissions

## 项目结构

```
com.b230408.musicplayer/
├── player/              # 播放器核心模块
│   ├── controller/     # 播放控制器
│   ├── service/        # 后台播放服务
│   ├── model/          # 数据模型
│   └── utils/          # 工具类
│
├── playlist/            # 播放列表模块
│   ├── manager/        # 列表管理器
│   ├── model/          # 数据模型
│   └── scanner/        # 文件扫描器
│
├── metadata/            # 元数据模块
│   ├── reader/         # ID3 读取
│   ├── editor/         # ID3 编辑
│   └── model/          # 数据模型
│
├── lyrics/              # 歌词模块
│   ├── player/         # 歌词播放
│   ├── fetcher/        # 歌词获取
│   ├── editor/         # 歌词编辑
│   └── model/          # 数据模型
│
├── ui/                  # UI 模块
│   ├── pages/          # 页面组件
│   ├── viewmodel/      # ViewModel
│   ├── theme/          # 主题配置
│   └── widgets/        # 自定义组件
│
└── utils/               # 工具类模块
```

详细架构说明请查看 [ARCHITECTURE.md](ARCHITECTURE.md)

## 主要组件

### MusicService
后台播放服务，使用 ExoPlayer 实现音频播放。

**特性**:
- 前台服务（通知栏显示）
- AudioFocus 管理
- 播放状态监听
- 通知栏控制

### MusicController
播放逻辑控制器（单例模式）。

**功能**:
- 播放列表管理
- 播放模式切换
- 播放控制（播放/暂停/上一首/下一首）
- 进度控制

### FileScanner
音乐文件扫描器。

**扫描策略**:
1. 优先使用 MediaStore（Android 10+）
2. 回退到目录直接扫描
3. 自动触发 MediaScannerConnection

### PlaylistManager
播放列表管理器。

**功能**:
- 创建/删除/更新播放列表
- JSON 格式持久化存储
- 播放列表查询

## 依赖库

```gradle
// Jetpack Compose
implementation("androidx.compose.ui:ui:$compose_version")
implementation("androidx.compose.material3:material3:$material3_version")
implementation("androidx.compose.material:material-icons-extended:$material_icons_version")

// Media3 ExoPlayer
implementation("androidx.media3:media3-exoplayer:$media3_version")
implementation("androidx.media3:media3-ui:$media3_version")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version")

// Navigation
implementation("androidx.navigation:navigation-compose:$nav_version")

// Coil (图片加载)
implementation("io.coil-kt:coil-compose:$coil_version")

// Permissions
implementation("com.google.accompanist:accompanist-permissions:$accompanist_version")

// JAudioTagger (元数据处理)
implementation("org.jaudiotagger:jaudiotagger:$jaudiotagger_version")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
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
1. 应用启动后会请求存储权限
2. 自动扫描设备上的音乐文件
3. 显示扫描到的音乐列表

### 播放音乐
- 点击播放列表中的歌曲开始播放
- 使用通知栏或播放界面控制播放
- 支持播放模式切换（顺序/单曲循环/随机）

### 管理播放列表
- 自动创建"所有音乐"播放列表
- 支持自定义播放列表（待实现）

## 开发说明

### 编译要求
- Android Studio Hedgehog | 2023.1.1 或更高
- JDK 17 或更高
- Android SDK API 34 (Android 14)
- Gradle 8.0+

### 构建项目
```bash
./gradlew build
```

### 运行应用
```bash
./gradlew installDebug
```

## 已知问题

- [ ] 部分设备上 MediaStore 扫描可能返回空结果
- [ ] ID3 标签读取在某些文件格式上可能失败
- [ ] 播放列表持久化需要改进（当前使用 JSON 文件）

## 待实现功能

- [ ] 歌词同步显示
- [ ] ID3 标签编辑
- [ ] 播放历史记录
- [ ] 收藏功能
- [ ] 在线音乐搜索
- [ ] 播放列表导入/导出
- [ ] 主题切换（亮色/暗色）
- [ ] 播放列表排序和搜索

## 许可证

本项目仅供学习和研究使用。

## 作者

b230408

