# Voxly - MP3 Tag Editor for Android

A modern Android music tag editor with online metadata search, ReplayGain scanning, and adaptive UI. Built with Kotlin + Jetpack Compose + Material 3 Expressive.

## Features

- **Tag editing**: title, artist, album, genre, year, track number
- **Album art**: view, replace, remove
- **Multi-format**: MP3, FLAC, OGG, M4A, WMA, WAV, APE
- **Library browsing**: Files, Albums, Artists tabs with adaptive grids
- **ReplayGain scanning**: Native EBU R128 (libebur128/NDK) + Kotlin MediaCodec fallback
- **Lyrics poster**: 4 visual templates (classic, immersive, typography, collage)
- **5 online sources**: MusicBrainz, Apple Music, NetEase, QQ Music, LRCLib
- **Adaptive UI**: phones, foldables, tablets — Material 3 Expressive design

## Tech Stack

Kotlin · Jetpack Compose · Material 3 Expressive · Navigation 3 · MVVM + Clean Architecture · Hilt · Room · taglib · libebur128 (NDK) · Retrofit + OkHttp · Coil 3

## Build

```bash
./gradlew assembleGithubDebug
```

## Permissions

`READ_MEDIA_AUDIO` · `READ_EXTERNAL_STORAGE` · `WRITE_EXTERNAL_STORAGE` · `INTERNET` · `ACCESS_NETWORK_STATE`

---

# Voxly - 安卓音乐标签编辑器

现代安卓音乐标签编辑器，支持在线元数据搜索、ReplayGain 扫描和自适应界面。

## 功能

- **标签编辑**：标题、艺术家、专辑、流派、年份、曲目号
- **专辑封面**：查看、替换、删除
- **多格式支持**：MP3, FLAC, OGG, M4A, WMA, WAV, APE
- **音乐库浏览**：文件、专辑、艺术家标签页，自适应网格布局
- **ReplayGain 扫描**：原生 EBU R128（libebur128/NDK）+ Kotlin MediaCodec 回退
- **歌词海报**：4 种视觉模板（经典、沉浸、排版、拼贴）
- **5 个在线数据源**：MusicBrainz、Apple Music、网易云、QQ 音乐、LRCLib
- **自适应界面**：手机、折叠屏、平板 — Material 3 Expressive 设计

## 技术栈

Kotlin · Jetpack Compose · Material 3 Expressive · Navigation 3 · MVVM + 清洁架构 · Hilt · Room · taglib · libebur128 (NDK) · Retrofit + OkHttp · Coil 3

## 构建

```bash
./gradlew assembleGithubDebug
```

## 权限

`READ_MEDIA_AUDIO` · `READ_EXTERNAL_STORAGE` · `WRITE_EXTERNAL_STORAGE` · `INTERNET` · `ACCESS_NETWORK_STATE`
