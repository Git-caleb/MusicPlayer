# 音乐播放器项目架构文档

## 项目结构

```
com.b230408.musicplayer/
├── MainActivity.kt              # 主界面 Activity
├── SplashActivity.kt            # 启动界面 Activity
│
├── player/                      # 播放器模块
│   ├── controller/
│   │   └── MusicController.kt  # 播放控制器（单例模式）
│   ├── service/
│   │   └── MusicService.kt     # 后台播放服务（ExoPlayer）
│   ├── model/
│   │   └── Track.kt            # 音轨数据模型
│   └── utils/
│       └── PlaybackMode.kt      # 播放模式枚举（顺序/单曲循环/随机）
│
├── playlist/                    # 播放列表模块
│   ├── manager/
│   │   └── PlaylistManager.kt  # 播放列表管理器（增删改查、持久化）
│   ├── model/
│   │   └── Playlist.kt         # 播放列表数据模型
│   └── scanner/
│       └── FileScanner.kt      # 文件扫描器（MediaStore + 目录扫描）
│
├── metadata/                    # 元数据模块
│   ├── reader/
│   │   └── ID3Reader.kt        # ID3标签读取器（JAudioTagger）
│   ├── editor/
│   │   └── ID3Editor.kt        # ID3标签编辑器
│   └── model/
│       └── Metadata.kt         # 元数据模型（标题、艺术家、专辑等）
│
├── lyrics/                      # 歌词模块
│   ├── player/
│   │   └── LyricPlayer.kt     # 歌词播放器（同步显示）
│   ├── fetcher/
│   │   └── LyricFetcher.kt     # 歌词获取器（网络/本地）
│   ├── editor/
│   │   └── LyricEditor.kt      # 歌词编辑器
│   └── model/
│       └── LyricLine.kt        # 歌词行模型
│
├── ui/                          # UI 模块
│   ├── pages/
│   │   ├── PlayerUI.kt         # 播放界面（Jetpack Compose）
│   │   └── SplashUI.kt         # 启动界面
│   ├── viewmodel/
│   │   ├── PlayerViewModel.kt  # 播放器 ViewModel
│   │   └── PlayerViewModelFactory.kt  # ViewModel 工厂
│   ├── theme/
│   │   ├── Theme.kt           # Material3 主题
│   │   ├── Color.kt           # 颜色定义
│   │   └── Type.kt            # 字体定义
│   └── widgets/
│       └── CustomButton.kt    # 自定义按钮组件
│
└── utils/                       # 工具类模块
    ├── Constants.kt            # 应用常量
    ├── FileUtils.kt            # 文件工具类
    └── PermissionUtils.kt      # 权限工具类
```

## 架构分层

### 1. 表现层 (UI Layer)
- **Activity**: `MainActivity`, `SplashActivity`
- **Composable UI**: `PlayerUI`, `SplashUI`
- **ViewModel**: `PlayerViewModel` - 管理 UI 状态和数据
- **Theme**: Material3 主题配置

### 2. 业务逻辑层 (Business Logic Layer)
- **MusicController**: 播放控制逻辑（单例）
  - 管理播放列表、播放模式
  - 控制播放/暂停/上一首/下一首
  - 与 MusicService 通信

- **PlaylistManager**: 播放列表管理
  - 创建/删除/更新播放列表
  - 持久化存储（JSON）

- **FileScanner**: 文件扫描
  - MediaStore 扫描（推荐）
  - 目录直接扫描（备用）

### 3. 数据层 (Data Layer)
- **MusicService**: 后台播放服务
  - 使用 ExoPlayer 播放音频
  - 前台服务（通知栏控制）
  - AudioFocus 管理

- **数据模型**: `Track`, `Playlist`, `Metadata`, `LyricLine`

- **持久化**: 
  - SharedPreferences（配置）
  - JSON 文件（播放列表）

### 4. 工具层 (Utility Layer)
- **ID3Reader/Editor**: 音频文件元数据读写
- **FileUtils**: 文件操作工具
- **PermissionUtils**: 权限管理工具
- **Constants**: 常量定义

## 数据流向

```
用户操作 (UI)
    ↓
PlayerViewModel
    ↓
MusicController
    ↓
MusicService (ExoPlayer)
    ↓
音频播放

文件扫描流程:
FileScanner → MediaStore/FileSystem
    ↓
Track 对象列表
    ↓
PlaylistManager
    ↓
Playlist
    ↓
PlayerViewModel
    ↓
UI 显示
```

## 关键组件说明

### MusicService
- **职责**: 音频播放核心服务
- **技术**: ExoPlayer + Media3
- **特性**: 
  - 前台服务（通知栏）
  - AudioFocus 管理
  - 播放状态监听

### MusicController
- **职责**: 播放逻辑控制
- **模式**: 单例模式
- **功能**: 
  - 播放列表管理
  - 播放模式切换（顺序/单曲循环/随机）
  - 播放进度控制

### FileScanner
- **职责**: 扫描设备上的音乐文件
- **策略**: 
  1. 优先使用 MediaStore（Android 10+）
  2. 回退到目录直接扫描
  3. 触发 MediaScannerConnection 更新 MediaStore

### PlaylistManager
- **职责**: 播放列表管理
- **存储**: JSON 文件（filesDir）
- **功能**: CRUD 操作

## 技术栈

- **UI**: Jetpack Compose + Material3
- **架构**: MVVM (Model-View-ViewModel)
- **播放**: ExoPlayer (Media3)
- **协程**: Kotlin Coroutines
- **元数据**: JAudioTagger
- **序列化**: Kotlinx Serialization
- **图片加载**: Coil
- **导航**: Navigation Compose
- **权限**: Accompanist Permissions

## 模块依赖关系

```
UI Layer
    ↓
ViewModel
    ↓
Controller/Manager Layer
    ↓
Service/Data Layer
    ↓
Utils/External Libraries
```

## 设计模式

1. **单例模式**: `MusicController`
2. **观察者模式**: StateFlow (ViewModel → UI)
3. **工厂模式**: `PlayerViewModelFactory`
4. **策略模式**: 播放模式切换
5. **MVC/MVVM**: UI 架构

## 文件组织原则

- **按功能模块划分**: player, playlist, metadata, lyrics, ui
- **每个模块内部按职责划分**: model, controller/service, utils
- **共享代码放在 utils**: 通用工具类
- **UI 组件独立**: pages, widgets, theme

## 扩展建议

### 已实现功能
✅ 音乐文件扫描
✅ 播放列表管理
✅ 音频播放控制
✅ ID3 标签读取
✅ 前台服务播放
✅ 通知栏控制
✅ Material3 UI

### 待扩展功能
- [ ] 歌词同步显示
- [ ] ID3 标签编辑
- [ ] 播放历史记录
- [ ] 收藏功能
- [ ] 在线音乐搜索
- [ ] 播放列表导入/导出
- [ ] 主题切换（亮色/暗色）
- [ ] 播放列表排序
- [ ] 搜索功能

