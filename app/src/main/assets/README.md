# 内置资源文件说明

本目录用于存放应用内置的 MP3 音乐文件和歌词文件。

## 目录结构

```
app/src/main/assets/
├── music/          # MP3 音乐文件目录
│   ├── song1.mp3
│   ├── song2.mp3
│   └── ...
└── lyrics/         # 歌词文件目录
    ├── song1.lrc
    ├── song2.lrc
    └── ...
```

## 使用方法

### 1. 添加音乐文件

1. 将音乐文件复制到 `app/src/main/assets/music/` 目录
2. 文件名可以包含中文、空格等任意字符
3. 支持的文件格式：**MP3、AAC、OGG、WAV、FLAC、M4A、AMR**

**示例：**
```
app/src/main/assets/music/
├── 示例歌曲1.mp3
├── 示例歌曲2.flac
├── demo_song.aac
└── high_quality.wav
```

### 2. 添加歌词文件

1. 将歌词文件复制到 `app/src/main/assets/lyrics/` 目录
2. 歌词文件名应与对应的音乐文件名（不含扩展名）相同，扩展名为 `.lrc`
3. 歌词文件格式应为 LRC 格式（标准歌词文件格式）

**示例：**
- 如果音乐文件名为 `示例歌曲1.mp3`，则对应的歌词文件应为 `示例歌曲1.lrc`
- 如果音乐文件名为 `示例歌曲2.flac`，则对应的歌词文件应为 `示例歌曲2.lrc`

```
app/src/main/assets/lyrics/
├── 示例歌曲1.lrc
├── 示例歌曲2.lrc
└── demo_song.lrc
```

### 3. LRC 歌词文件格式

LRC 歌词文件的标准格式如下：

```
[00:00.00]歌曲标题
[00:00.50]艺术家名称
[00:01.00]第一句歌词
[00:05.50]第二句歌词
[00:10.00]第三句歌词
...
```

时间标签格式：`[mm:ss.ff]` 或 `[mm:ss:ff]`
- `mm`: 分钟（00-59）
- `ss`: 秒（00-59）
- `ff`: 百分秒或帧（00-99）

## 工作原理

1. **扫描阶段**：应用启动时会自动扫描 `assets/music/` 目录中的音乐文件
2. **播放阶段**：由于 ExoPlayer 无法直接播放 assets 中的文件，系统会自动将文件复制到应用的内部存储目录（`files/assets_music/`）
3. **歌词加载**：播放音乐时，系统会自动查找对应的歌词文件并加载

## 注意事项

1. **文件大小**：assets 中的文件会打包到 APK 中，增加 APK 体积。建议：
   - 内置音乐文件不要太大（建议每个文件 < 5MB）
   - 如果文件较大，考虑使用网络下载或外部存储

2. **文件数量**：建议内置音乐文件数量不要过多（建议 < 10 首），以免 APK 体积过大

3. **文件命名**：
   - 避免使用特殊字符（如 `\`, `/`, `:`, `*`, `?`, `"`, `<`, `>`, `|`）
   - 建议使用英文或中文文件名

4. **ID3 标签**：建议为 MP3 文件添加 ID3 标签（标题、艺术家、专辑等），这样应用可以正确显示歌曲信息

5. **歌词文件**：
   - 歌词文件是可选的，如果没有对应的歌词文件，应用仍可正常播放音乐
   - 歌词文件名必须与 MP3 文件名（不含扩展名）完全匹配

## 代码示例

### 在代码中访问内置音乐

```kotlin
// 扫描 assets 中的音乐文件（支持所有格式：MP3、FLAC、AAC、OGG、WAV、M4A、AMR）
val assetsTracks = AssetsUtils.scanAssetsMusic(context)

// 获取特定音乐文件的 URI（用于播放）
val uri = AssetsUtils.getAssetsMusicUri(context, "示例歌曲1.mp3")
val flacUri = AssetsUtils.getAssetsMusicUri(context, "高音质歌曲.flac")

// 读取歌词文件（歌词文件名应与音乐文件名匹配，不含扩展名）
val lyrics = AssetsUtils.readLyricsFromAssets(context, "示例歌曲1.mp3")
val flacLyrics = AssetsUtils.readLyricsFromAssets(context, "高音质歌曲.flac")
```

## 常见问题

**Q: 为什么我的音乐文件没有被扫描到？**
A: 请检查：
1. 文件是否放在 `app/src/main/assets/music/` 目录中
2. 文件格式是否支持（MP3、AAC、OGG、WAV、FLAC、M4A、AMR）
3. 文件名是否包含特殊字符（避免使用 `\`, `/`, `:`, `*`, `?`, `"`, `<`, `>`, `|`）

**Q: 支持 FLAC 格式吗？**
A: 是的！应用支持 FLAC、M4A、AMR 等多种格式。只需将文件放入 `app/src/main/assets/music/` 目录即可。

**Q: 歌词文件没有被加载？**
A: 请检查：
1. 歌词文件是否放在 `app/src/main/assets/lyrics/` 目录中
2. 歌词文件名是否与音乐文件名（不含扩展名）完全匹配（例如：`song.flac` 对应 `song.lrc`）
3. 歌词文件扩展名是否为 `.lrc`

**Q: 播放内置音乐时没有声音？**
A: 请检查：
1. 文件是否损坏
2. 文件格式是否被 ExoPlayer 支持
3. 查看 Logcat 中的错误信息

## 更新日志

- v1.0: 初始版本，支持内置 MP3 和 LRC 歌词文件


