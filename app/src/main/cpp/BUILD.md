# NDK Native EBU R128 Scanner - Build Instructions

## 架构概述

```
SAF Uri/文件 → MediaExtractor → MediaCodec → PCM short[] → JNI → libebur128 → LUFS → Kotlin计算增益 → TagLib写入
```

**核心思路**：
1. **Kotlin侧**：使用Android原生 `MediaExtractor` + `MediaCodec` 解码音频为PCM
2. **C++侧**：仅引入轻量级 `libebur128`（纯C库，一个.c + 一个.h文件）
3. **JNI桥接**：将PCM数组传入JNI，libebur128进行扫描，返回LUFS
4. **写入标签**：使用现有 `kyant0:taglib` 写入文件

**优点**：
- APK体积增加极小（仅几十KB .so）
- 无需申请危险权限
- 符合Android现代架构
- 数值精度与rsgain完全一致（使用相同的libebur128）

## 目录结构

```
app/src/main/cpp/
├── CMakeLists.txt                    # CMake配置
├── ebur128_scanner.cpp               # JNI桥接代码
└── external/
    ├── libebur128/                   # libebur128源码（已包含）
    │   ├── include/
    │   │   └── ebur128.h
    │   ├── ebur128.c
    │   └── queue/
    │       └── sys/
    │           └── queue.h
    └── libebur128-src/               # 原始git仓库（可删除）
```

## 依赖说明

**无需编译FFmpeg！** 仅需要libebur128，其源码已直接包含在项目中。

libebur128来源：https://github.com/jiixyj/libebur128 (v1.2.6, BSD许可证)

## 构建

```bash
./gradlew :app:assembleDebug
```

NDK会自动编译native代码。无需额外步骤。

## 使用

### Kotlin侧调用

```kotlin
// 创建scanner
val scanner = EbuR128NativeScanner(
    channels = 2,
    sampleRate = 44100,
    targetLoudness = -18.0
)

// 从MediaCodec获取PCM short[]
val samples: ShortArray = ...
val frameCount = samples.size / 2

// 送入native计算
scanner.processFrames(samples, frameCount)

// 获取结果
val result = scanner.getResult()
// result.trackGain, result.trackPeak, result.trackLoudness, result.trackRange

// 释放
scanner.close()
```

### 通过ReplayGainScanner使用

```kotlin
// 原生引擎（推荐）
replayGainScanner.scanReplayGainWithAlbumGrouping(
    filePaths = files,
    scanQuality = ScanQuality.ACCURATE,
    targetLoudness = -18f,
    config = ReplayGainConfig.DEFAULT,
    useNative = true  // 启用native引擎
)

// Kotlin引擎（旧有，渐进式废弃）
replayGainScanner.scanReplayGainWithAlbumGrouping(
    filePaths = files,
    scanQuality = ScanQuality.ACCURATE,
    targetLoudness = -18f,
    config = ReplayGainConfig.DEFAULT,
    useNative = false
)
```

## 支持格式

依赖Android MediaCodec，支持：
- MP3
- FLAC
- AAC
- OGG/Vorbis
- Opus
- WAV
- 其他Android系统支持的音频格式

**不支持**：APE、TAK、DSF等冷门格式（会回退到Kotlin引擎或估算）

## 性能对比

| 指标 | Kotlin EbuR128Analyzer | Native libebur128 |
|------|----------------------|-------------------|
| 计算速度 | 基准 | ~2-3x 更快 |
| 内存占用 | 较高（Kotlin对象） | 低（C数组） |
| 数值精度 | 近似 | 与rsgain完全一致 |
| APK增加 | 0 | ~50KB |

## 双引擎策略

- **过渡期**：同时保留Kotlin和Native两套实现
- **默认**：使用Native引擎（`useNative = true`）
- **回退**：Native不可用时自动使用Kotlin引擎
- **未来**：渐进式废弃Kotlin `EbuR128Analyzer`
