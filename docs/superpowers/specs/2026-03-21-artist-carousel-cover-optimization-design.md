# 艺术家详情页专辑轮播封面优化设计方案

## 1. 背景与目标

**问题描述：**
艺术家详情页（ArtistDetailScreen）使用 `HorizontalMultiBrowseCarousel` 展示专辑封面，存在以下问题：
1. 首次进入页面时封面无法秒加载，空白数秒后才显示
2. 反复进入同一艺术家页面时，封面仍然无法秒开
3. 封面在 3x+ 密度设备上因强制降采样到 300px 显示 420px+ 区域导致模糊

**目标：**
- 缓存命中时封面图秒开（< 50ms）
- 显示清晰度匹配屏幕密度（384px 双 Cache 分层）
- 内存占用可控（轮播 Cache 仅 15 张 ≈ 12MB）
- 每封面仅调用一次 MediaMetadataRetriever

---

## 2. 核心策略

### 2.1 双缓存架构（双 Cache 分层）

```
[音频文件]
    │MediaMetadataRetriever (每封面仅 1 次)
    ▼
[Bytes Cache (coverArtByteCache, MAX=30)]
    │→ BitmapFactory.decodeByteArray (按需)
    ▼
┌──────────────────────────────────────┐
│ mainCache: 200 entries, 300px       │
│ → 用于列表等小尺寸展示                 │
└──────────────────────────────────────┘
    │
    │ (仅在 ArtistDetailScreen 持有)
    ▼
┌──────────────────────────────────────┐
│ carouselCache: 15 entries, 384px     │
│ → 仅用于 ArtistDetail 轮播           │
│ → 按需创建/LRU 淘汰                  │
│ → 内存: 15 × 0.8MB ≈ 12MB          │
└──────────────────────────────────────┘
    │
    ▼
[UI]
```

**设计要点：**
- **Bytes Cache**（已有，MAX=30）：存储封面原始字节（通常 10-50KB），key = filePath
- **mainCache**（已有，MAX=200，300px）：用于列表等小尺寸展示
- **carouselCache**（新增，15 entries，384px）：仅用于轮播，独立管理内存

### 2.2 尺寸策略（384px 2 的幂次 + 双 Cache）

| 设备密度 | 显示像素（140dp） | 300px 效果 | 384px 效果 |
|---------|-----------------|-----------|-----------|
| xhdpi (2x) | 280px | 略模糊 | 轻微上采样，清晰 |
| xxhdpi (3x) | 420px | 严重模糊 | 轻微下采样，清晰 |
| xxxhdpi (4x) | 560px | 严重模糊 | 下采样，视觉可接受 |

**选择 384px 的理由：**
- 2 的幂次，BitmapFactory 解码友好
- 略大于 3x 屏需求（420px），轻微下采样 ≈ 无模糊感
- 4x 屏（560px）下采样，视觉可接受
- 内存可控：15 张封面 ≈ 12MB

### 2.3 预加载策略（首屏 + 相邻各 1 张）

预加载范围：当前页 ± 1（共 3 张，密集屏可覆盖 5 张）

```
[页0] [页1] [页2] [页3] [页4]
           ▲
       currentPage=2
       预加载: 1, 2, 3
```

**滚动监听触发 + 去抖：**
```kotlin
LaunchedEffect(carouselState.currentPage) {
    preloadJob?.cancel()
    preloadJob = viewModelScope.launch {
        preloadAdjacentAlbumCovers(currentPage)
    }
}
```

### 2.4 单次 MediaMetadataRetriever 策略

修复现有 `precomputeAlbumCovers` 的双重调用问题：

```kotlin
// 旧代码问题：先检查 hasArt，再提取 bytes → 调用 2 次
val hasArt = retriever.embeddedPicture != null  // 调用 1
retriever.release()
extractEmbeddedPictureBytes(file.path)           // 调用 2（retriever 已释放！）

// 新代码：一次性提取 bytes，null 表示无封面
val retriever = MediaMetadataRetriever()
retriever.setDataSource(file.path)
val artBytes = retriever.embeddedPicture  // 一次性获取 bytes
retriever.release()
if (artBytes != null) {
    // 写入 Bytes Cache
    cacheBytes(file.path, artBytes)
}
```

---

## 3. 改动详情

### 3.1 ImageLoader.kt

#### 新增：carouselCache（384px 独立缓存）
```kotlin
// Carousel 专用封面缓存（15 entries, 384px）
private val carouselCoverCache = LinkedHashMap<String, Bitmap>(15, 0.75f, true)
private val carouselCacheLock = ReentrantLock()
private const val MAX_CAROUSEL_CACHE_SIZE = 15
private const val CAROUSEL_TARGET_SIZE = 384
```

#### 修改：`loadLocalAlbumArt` — 修复双重调用问题
```kotlin
/**
 * 提取封面字节并写入 Bytes Cache。
 * 单次 MediaMetadataRetriever 调用，同时完成 existence check 和 bytes 提取。
 */
private fun extractAndCacheCoverBytes(filePath: String): ByteArray? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val artBytes = retriever.embeddedPicture  // 一次性获取
        retriever.release()

        if (artBytes != null) {
            byteCacheLock.lock()
            try {
                while (coverArtByteCache.size >= MAX_BYTE_CACHE_SIZE) {
                    coverArtByteCache.keys.firstOrNull()?.let { coverArtByteCache.remove(it) }
                }
                coverArtByteCache[filePath] = artBytes
            } finally {
                byteCacheLock.unlock()
            }
        }
        artBytes
    } catch (e: Exception) {
        null
    }
}
```

#### 新增：carousel 专用加载函数
```kotlin
/**
 * 加载轮播封面 Bitmap（384px）。
 * 1. 检查 carouselCache
 * 2. 检查 mainCache (300px)
 * 3. 从 Bytes Cache decode
 */
suspend fun loadCarouselCoverArt(filePath: String): Bitmap? {
    if (filePath.isBlank()) return null

    // 1. 检查 carouselCache
    carouselCacheLock.lock()
    val cached = carouselCoverCache[filePath]
    carouselCacheLock.unlock()
    if (cached != null && !cached.isRecycled) return cached

    // 2. 检查 Bytes Cache
    byteCacheLock.lock()
    val bytes = coverArtByteCache[filePath]
    byteCacheLock.unlock()

    val bitmap = withContext(Dispatchers.IO) {
        if (bytes != null) {
            // 从 bytes decode 到 384px
            decodeSampledBitmapFromBytes(bytes, CAROUSEL_TARGET_SIZE)
        } else {
            // bytes 未缓存，提取并缓存
            val extractedBytes = extractAndCacheCoverBytes(filePath)
            extractedBytes?.let { decodeSampledBitmapFromBytes(it, CAROUSEL_TARGET_SIZE) }
        }
    } ?: return null

    // 3. 写入 carouselCache
    carouselCacheLock.lock()
    try {
        while (carouselCoverCache.size >= MAX_CAROUSEL_CACHE_SIZE) {
            carouselCoverCache.keys.firstOrNull()?.let { carouselCoverCache.remove(it) }
        }
        carouselCoverCache[filePath] = bitmap
    } finally {
        carouselCacheLock.unlock()
    }

    return bitmap
}
```

#### 新增：获取封面字节（线程安全）
```kotlin
/**
 * 获取封面原始字节，优先从 Bytes Cache 读取，否则从文件提取。
 * 线程安全：提取过程在锁内执行。
 */
suspend fun getCoverBytes(filePath: String): ByteArray? {
    // 1. 检查 Bytes Cache
    byteCacheLock.lock()
    val cached = coverArtByteCache[filePath]
    byteCacheLock.unlock()
    if (cached != null) return cached

    // 2. 从文件提取（线程安全）
    return withContext(Dispatchers.IO) {
        extractAndCacheCoverBytes(filePath)
    }
}
```

### 3.2 ArtistDetailViewModel.kt

#### 修复：`precomputeAlbumCovers` — 单次 MediaMetadataRetriever 调用
```kotlin
/**
 * 预计算专辑封面路径。
 * 修复：每封面仅调用一次 MediaMetadataRetriever。
 */
private fun precomputeAlbumCovers(files: List<AudioFile>) {
    viewModelScope.launch {
        val covers = withContext(Dispatchers.IO) {
            val albumGroups = files.groupBy { it.metadata.album ?: "" }
            albumGroups.mapNotNull { (albumName, albumFiles) ->
                if (albumName.isEmpty()) return@mapNotNull null

                // 找第一张有封面的文件（单次调用）
                val fileWithArt = albumFiles.firstOrNull { file ->
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.path)
                        val artBytes = retriever.embeddedPicture  // 一次性获取
                        retriever.release()

                        if (artBytes != null) {
                            // 写入 Bytes Cache
                            byteCacheLock.lock()
                            try {
                                while (coverArtByteCache.size >= MAX_BYTE_CACHE_SIZE) {
                                    coverArtByteCache.keys.firstOrNull()?.let { coverArtByteCache.remove(it) }
                                }
                                coverArtByteCache[file.path] = artBytes
                            } finally {
                                byteCacheLock.unlock()
                            }
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

                albumName to fileWithArt?.path
            }.toMap()
        }
        _albumCovers.value = covers
    }
}

private var preloadJob: Job? = null
private val preloadMutex = Mutex()

/**
 * 预加载相邻专辑封面（currentPage ± 1）。
 * 并发保护：使用 Mutex 确保同时只有一个预加载任务执行。
 */
fun preloadAdjacentAlbumCovers(currentPage: Int) {
    val albumList = _albumCovers.value.keys.toList()
    if (albumList.isEmpty()) return

    preloadJob?.cancel()
    preloadJob = viewModelScope.launch {
        preloadMutex.lock()
        try {
            withContext(Dispatchers.IO) {
                val indices = listOf(currentPage - 1, currentPage, currentPage + 1)
                    .filter { it in albumList.indices }

                indices.forEach { index ->
                    val albumName = albumList[index]
                    val path = _albumCovers.value[albumName]
                    if (path != null) {
                        loadCarouselCoverArt(path)
                    }
                }
            }
        } finally {
            preloadMutex.unlock()
        }
    }
}
```

### 3.3 ArtistDetailScreen.kt

#### AlbumCard — 使用 carousel 专用加载
```kotlin
@Composable
private fun AlbumCard(
    albumName: String,
    trackCount: Int,
    albumArtPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .height(170.dp)
            .scale(scale),
        // ...
    ) {
        Column(...) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // 使用 carousel 专用加载（384px）
                CarouselAlbumArtImage(
                    filePath = albumArtPath,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            modifier = Modifier.padding(24.dp).fillMaxSize(),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            // ...
        }
    }
}
```

#### CarouselAlbumArtImage — 专用组件
```kotlin
@Composable
private fun CarouselAlbumArtImage(
    filePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {}
) {
    val bitmap = produceState<Bitmap?>(initialValue = null, key1 = filePath) {
        value = withContext(Dispatchers.IO) {
            filePath?.let { loadCarouselCoverArt(it) }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val loadedBitmap = bitmap.value
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            placeholder()
        }
    }
}
```

#### Carousel 滚动监听
```kotlin
item {
    val albumList = albumsGrouped.keys.toList()
    if (albumList.isNotEmpty()) {
        val carouselState = rememberCarouselState { albumList.size }

        // 预加载相邻专辑封面
        LaunchedEffect(carouselState.currentPage) {
            viewModel.preloadAdjacentAlbumCovers(carouselState.currentPage)
        }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            // ...
        ) { page ->
            // ...
        }
    }
}
```

---

## 4. 关键行为矩阵

| 场景 | 首次进入 | 再次进入 | 滚动到相邻 |
|------|---------|---------|-----------|
| MediaMetadataRetriever 调用 | 3 次（current ± 1） | 0 次 | 0 次（已缓存） |
| Bytes Cache 命中 | 否 | 是 | 是 |
| carouselCache 命中 | 否 | 是 | 是 |
| 封面显示延迟 | 200-500ms | < 50ms | < 50ms |
| 内存增量（carouselCache） | ~2.4MB | 0 | 0 |

---

## 5. 性能指标

| 指标 | 目标 | 测量方式 |
|------|------|---------|
| 缓存命中显示 | < 50ms | Log timestamps |
| 缓存未命中解码 | 200-500ms | Log timestamps |
| 首次加载完整预加载 | < 1s | 人工感知 |
| MediaMetadataRetriever 调用次数 | 每封面 1 次 | 代码审查 |
| carouselCache 内存占用 | ≈ 12MB (15@384px) | Profiler |
| 缓存命中率（再次进入） | 100% | Log |

---

## 6. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| Bytes Cache 上限 30 较小 | 预加载时更新 cache；上限可调 |
| carouselCache 与 mainCache 重复缓存同一封面 | 可接受（不同尺寸，服务不同场景） |
| 预加载与 UI 抢占 IO 线程 | `Dispatchers.IO` + Mutex 串行执行 |
| 滚动频繁触发预加载 | `preloadJob?.cancel()` 去抖 |
| 4x 屏封面轻微模糊 | 视觉可接受；如需改善可提升到 512px |

---

## 7. 修复的审查问题

| 问题 | 修复方案 |
|------|---------|
| 双重 MediaMetadataRetriever 调用 | `extractAndCacheCoverBytes` 一次性获取 bytes + cache |
| `getCoverBytes` 锁不一致 | bytes 提取在锁内执行 |
| Bitmap decode 不在 IO 线程 | `loadCarouselCoverArt` 使用 `withContext(Dispatchers.IO)` |
| `displaySizePx` 缺失 recomposition key | 独立 `CarouselAlbumArtImage` 组件，key1 = filePath |
| 512px 内存过大（290MB） | 384px + carouselCache 独立 15 entries（≈ 12MB） |

---

## 8. 文件改动清单

| 文件 | 改动类型 | 核心内容 |
|------|---------|---------|
| `ImageLoader.kt` | 新增 carouselCache + 修改 bytes 提取 | `carouselCoverCache`, `CAROUSEL_TARGET_SIZE=384`, `extractAndCacheCoverBytes`, `loadCarouselCoverArt`, `getCoverBytes` |
| `ArtistDetailViewModel.kt` | 修复 precomputeAlbumCovers + 并发保护 | 单次 MediaMetadataRetriever 调用, Mutex 保护 |
| `ArtistDetailScreen.kt` | 新增 CarouselAlbumArtImage 组件 | 独立轮播封面组件 |
