# 艺术家详情页专辑轮播封面优化设计方案

## 1. 背景与目标

**问题描述：**
艺术家详情页（ArtistDetailScreen）使用 `HorizontalMultiBrowseCarousel` 展示专辑封面，存在以下问题：
1. 首次进入页面时封面无法秒加载，空白数秒后才显示
2. 反复进入同一艺术家页面时，封面仍然无法秒开
3. 封面在 3x+ 密度设备上因强制降采样到 300px 显示 420px+ 区域导致模糊

**目标：**
- 缓存命中时封面图秒开（< 50ms）
- 显示清晰度匹配屏幕密度（统一 512px 2 的幂次）
- 内存占用可控（预加载 3 张 ≈ 3MB）
- 每封面仅调用一次 MediaMetadataRetriever

---

## 2. 核心策略

### 2.1 双缓存架构（Bytes Cache + Bitmap Cache）

```
[音频文件]
    │MediaMetadataRetriever
    ▼
[Bytes Cache (coverArtByteCache)]
    │BitmapFactory.decodeByteArray
    ▼
[Bitmap Cache (localAlbumArtCache)]
    │Compose 显示
    ▼
[UI]
```

- **Bytes Cache**（`coverArtByteCache`，已有，MAX=30）：存储封面原始字节，key = filePath
- **Bitmap Cache**（`localAlbumArtCache`，已有，MAX=200）：存储降采样后 Bitmap，key = filePath + sizePx

**设计要点：** 预加载阶段将封面 bytes 写入 Bytes Cache，显示时优先从 Bytes Cache decode 到目标尺寸，避免每次都调用 MediaMetadataRetriever。

### 2.2 统一显示尺寸（512px 固定 2 的幂次）

| 设备密度 | 显示像素（140dp） | 旧方案 target=300 | 新方案 target=512 |
|---------|-----------------|------------------|------------------|
| xhdpi (2x) | 280px | 300px（略模糊） | 512px（略浪费但清晰） |
| xxhdpi (3x) | 420px | 300px（严重模糊） | 512px（足够清晰） |
| xxxhdpi (4x) | 560px | 300px（严重模糊） | 512px（轻微模糊） |

**选择 512px 的理由：**
- BitmapFactory 对 2 的幂次解码最友好
- 在所有常见密度下 >= 显示像素
- 统一缓存 key，命中率最高
- 每张封面 ≈ 1MB，200 张缓存上限 ≈ 200MB

### 2.3 预加载策略（首屏 + 相邻各 1 张）

预加载范围：当前页 ± 1（共 3 张，密集屏可覆盖 5 张）

```
[页0] [页1] [页2] [页3] [页4]
           ▲
       currentPage=2
       预加载: 1, 2, 3
```

**滚动监听触发：**
```kotlin
LaunchedEffect(carouselState.currentPage) {
    preloadJob?.cancel()
    preloadJob = launch {
        preloadAdjacentAlbumCovers(currentPage)
    }
}
```

**去抖机制：** `preloadJob?.cancel()` 确保旧任务被取消，新任务执行。

---

## 3. 改动详情

### 3.1 ImageLoader.kt

#### 新增函数：获取封面字节（优先缓存）
```kotlin
/**
 * 获取封面原始字节，优先从 Bytes Cache 读取，否则从文件提取并缓存。
 */
suspend fun getCoverBytes(filePath: String): ByteArray? {
    // 1. 检查 byteCache
    byteCacheLock.lock()
    val cached = coverArtByteCache[filePath]
    byteCacheLock.unlock()
    if (cached != null) return cached

    // 2. 从文件提取
    return withContext(Dispatchers.IO) {
        extractEmbeddedPictureBytes(filePath)
    }
}
```

#### 新增函数：尺寸感知的封面加载
```kotlin
/**
 * 加载封面 Bitmap，优先从 Bitmap Cache 命中。
 * 未命中时：从 Bytes Cache 获取 bytes → decodeByteArray 到目标尺寸。
 */
fun loadLocalAlbumArtOptimal(
    filePath: String,
    targetSizePx: Int = 512
): Bitmap? {
    // 1. 检查 Bitmap Cache
    localCacheLock.lock()
    val cached = localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)]
    localCacheLock.unlock()
    if (cached != null && !cached.isRecycled) return cached

    // 2. 从 Bytes Cache 获取 bytes
    val bytes = coverArtByteCache[filePath] ?: return null

    // 3. Decode 到目标尺寸
    val bitmap = decodeSampledBitmapFromBytes(bytes, targetSizePx) ?: return null

    // 4. 写入 Bitmap Cache
    localCacheLock.lock()
    while (localAlbumArtCache.size >= MAX_LOCAL_CACHE_SIZE) {
        localAlbumArtCache.keys.firstOrNull()?.let { localAlbumArtCache.remove(it) }
    }
    localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)] = bitmap
    localCacheLock.unlock()

    return bitmap
}
```

#### 新增函数：提取封面字节（IO）
```kotlin
/**
 * 从音频文件提取 embeddedPicture 字节。
 */
private fun extractEmbeddedPictureBytes(filePath: String): ByteArray? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val bytes = retriever.embeddedPicture
        retriever.release()
        bytes?.also { artBytes ->
            // 写入 Bytes Cache
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
    } catch (e: Exception) {
        null
    }
}
```

### 3.2 ArtistDetailViewModel.kt

#### 扩展预计算函数
```kotlin
/**
 * 预计算并预加载专辑封面。
 * 1. 计算 albumName → filePath 映射
 * 2. 在 IO 线程预加载封面 bytes → 写入 Bytes Cache
 */
private fun precomputeAndPreloadAlbumCovers(files: List<AudioFile>) {
    viewModelScope.launch {
        val covers = withContext(Dispatchers.IO) {
            val albumGroups = files.groupBy { it.metadata.album ?: "" }
            albumGroups.mapNotNull { (albumName, albumFiles) ->
                if (albumName.isEmpty()) return@mapNotNull null

                // 找第一张有封面的文件
                val fileWithArt = albumFiles.firstOrNull { file ->
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.path)
                        val hasArt = retriever.embeddedPicture != null
                        retriever.release()
                        hasArt
                    } catch (e: Exception) {
                        false
                    }
                }

                // 预加载 bytes 到缓存
                fileWithArt?.let { file ->
                    extractEmbeddedPictureBytes(file.path)
                }

                albumName to fileWithArt?.path
            }.toMap()
        }
        _albumCovers.value = covers
    }
}

private var preloadJob: Job? = null

/**
 * 预加载相邻专辑封面（currentPage ± 1）。
 * 由 Carousel 滚动监听调用。
 */
fun preloadAdjacentAlbumCovers(currentPage: Int) {
    val albumList = _albumCovers.value.keys.toList()
    if (albumList.isEmpty()) return

    preloadJob?.cancel()
    preloadJob = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val indices = listOf(currentPage - 1, currentPage, currentPage + 1)
                .filter { it in albumList.indices }

            indices.forEach { index ->
                val albumName = albumList[index]
                val path = _albumCovers.value[albumName]
                if (path != null) {
                    // 预加载 bytes（如果还未缓存）
                    getCoverBytes(path)
                    // 同时 decode 到 512px
                    loadLocalAlbumArtOptimal(path, 512)
                }
            }
        }
    }
}
```

### 3.3 ArtistDetailScreen.kt

#### AlbumCard 传递显示尺寸
```kotlin
@Composable
private fun AlbumCard(
    albumName: String,
    trackCount: Int,
    albumArtPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val displaySizePx = with(density) { 140.dp.toPx().toInt() }

    Card(
        ...
    ) {
        Column(...) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    filePath = albumArtPath,
                    mediaStoreAlbumId = null,
                    contentDescription = albumName,
                    size = 140.dp,
                    displaySizePx = 512,  // 新增
                    modifier = Modifier.fillMaxSize()
                ) {
                    // placeholder
                }
            }
            // ...
        }
    }
}
```

#### Carousel 滚动监听
```kotlin
// 在 LazyColumn 内
item {
    val albumList = albumsGrouped.keys.toList()
    if (albumList.isNotEmpty()) {
        val carouselState = rememberCarouselState { albumList.size }

        // 滚动监听：预加载相邻专辑封面
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

### 3.4 AlbumArtImage.kt

#### 新增 displaySizePx 参数
```kotlin
@Composable
fun AlbumArtImage(
    filePath: String?,
    mediaStoreAlbumId: Long? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentScale: ContentScale = ContentScale.Crop,
    displaySizePx: Int = 512,  // 新增参数
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val albumArtBitmap = produceAlbumArtBitmap(
        filePath = filePath,
        mediaStoreAlbumId = mediaStoreAlbumId,
        displaySizePx = displaySizePx
    )
    // ...
}

@Composable
private fun produceAlbumArtBitmap(
    filePath: String?,
    mediaStoreAlbumId: Long?,
    displaySizePx: Int = 512  // 新增
): androidx.compose.runtime.State<Bitmap?> {
    return androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath,
        key2 = mediaStoreAlbumId,
        key3 = displaySizePx
    ) {
        value = withContext(Dispatchers.IO) {
            // 优先使用新的优化加载路径
            if (!filePath.isNullOrBlank()) {
                val optimized = loadLocalAlbumArtOptimal(filePath, displaySizePx)
                if (optimized != null) return@withContext optimized
            }
            // fallback: 原逻辑
            if (!filePath.isNullOrBlank()) {
                val localArt = loadLocalAlbumArt(filePath, displaySizePx)
                if (localArt != null) return@withContext localArt
            }
            if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
                val mediaStoreArt = loadMediaStoreAlbumArt(
                    LocalContext.current,
                    mediaStoreAlbumId
                )
                if (mediaStoreArt != null) return@withContext mediaStoreArt
            }
            null
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
| Bitmap Cache 命中 | 否 | 是 | 是 |
| 封面显示延迟 | 200-500ms | < 50ms | < 50ms |
| 内存增量 | ~3MB | 0 | 0 |

---

## 5. 性能指标

| 指标 | 目标 | 测量方式 |
|------|------|---------|
| 缓存命中显示 | < 50ms | Log timestamps |
| 缓存未命中解码 | 200-500ms | Log timestamps |
| 首次加载完整预加载 | < 1s | 人工感知 |
| MediaMetadataRetriever 调用次数 | 每封面 1 次 | 代码审查 |
| 内存增量（预加载 3 张） | ≈ 3MB | Profiler |
| 缓存命中率（再次进入） | 100% | Log |

---

## 6. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| Bytes Cache 上限 30 较小，专辑多时被淘汰 | 预加载时更新 cache，下次命中直接用；上限可调 |
| 512px 在 xxxhdpi (4x) 上轻微模糊 | 实际 Canvas 渲染时会做 CSS 插值，视觉可接受 |
| 预加载与 UI 抢占 IO 线程 | 使用 `Dispatchers.IO` + 协程串行执行 |
| 滚动频繁触发预加载 | `preloadJob?.cancel()` 去抖 |

---

## 7. 文件改动清单

| 文件 | 改动类型 | 核心内容 |
|------|---------|---------|
| `ImageLoader.kt` | 新增函数 | `getCoverBytes`, `loadLocalAlbumArtOptimal`, `extractEmbeddedPictureBytes` |
| `ArtistDetailViewModel.kt` | 扩展函数 | `precomputeAndPreloadAlbumCovers`, `preloadAdjacentAlbumCovers` |
| `ArtistDetailScreen.kt` | 滚动监听 | `LaunchedEffect(currentPage)`, `displaySizePx = 512` |
| `AlbumArtImage.kt` | 新增参数 | `displaySizePx: Int = 512` |
