# Artist Carousel Cover Optimization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize album cover loading in Artist Detail page carousel with two-tier cache (mainCache + carouselCache), single MediaMetadataRetriever call per cover, and adjacent-item preloading.

**Architecture:** Add carouselCache (15 entries @ 384px) alongside existing mainCache (200 entries @ 300px). Extract and cache cover bytes once, then decode to target size on demand. Preload currentPage ± 1 covers on scroll.

**Tech Stack:** Kotlin, Jetpack Compose, MediaMetadataRetriever, LinkedHashMap LRU cache, Hilt ViewModel

---

## Chunk 1: ImageLoader.kt — Add carouselCache and helper functions

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/ui/ImageLoader.kt`

**Changes:**
1. Add `carouselCoverCache`, `carouselCacheLock`, `MAX_CAROUSEL_CACHE_SIZE = 15`, `CAROUSEL_TARGET_SIZE = 384`
2. Add `extractAndCacheCoverBytes()` — **public**, single MediaMetadataRetriever call, writes to bytes cache
3. Add `loadCarouselCoverArt()` — **public**, carousel-specific loading with 384px decode
4. Add `getLocalCoverBytes()` — **public**, thread-safe bytes retrieval (renamed from `getCoverBytes` to avoid conflict with existing `getCoverArtBytes`)

---

- [ ] **Step 1: Add carouselCache constants and cache map**

Locate line ~34 in `ImageLoader.kt` after `localAlbumArtCache` declaration. Add:

```kotlin
// Carousel专用封面缓存（15 entries, 384px）
private val carouselCoverCache = LinkedHashMap<String, Bitmap>(15, 0.75f, true)
private val carouselCacheLock = ReentrantLock()
private const val MAX_CAROUSEL_CACHE_SIZE = 15
private const val CAROUSEL_TARGET_SIZE = 384
```

- [ ] **Step 2: Add extractAndCacheCoverBytes() function (public for ViewModel use)**

Locate the existing `loadEmbeddedAlbumArtSized()` function (~line 283). Add the following new function before it:

```kotlin
/**
 * 提取封面字节并写入Bytes Cache。
 * 单次MediaMetadataRetriever调用，同时完成existence check和bytes提取。
 * 对同一filePath重复调用是安全的（幂等）。
 */
@PublishedApi
internal fun extractAndCacheCoverBytes(filePath: String): ByteArray? {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture  // 一次性获取
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
        } finally {
            retriever.release()  // 在finally中确保释放
        }
    } catch (e: Exception) {
        null
    }
}
```

Note: `@PublishedApi internal` makes this function callable from the same module (ViewModel) but not from outside the module. `try/finally` ensures `retriever.release()` is always called even if `setDataSource` throws.

- [ ] **Step 3: Add getLocalCoverBytes() public function (renamed to avoid conflict)**

Locate the end of the file, before the closing `getMediaStoreCacheKey()` function. Add:

```kotlin
/**
 * 获取本地文件封面原始字节，优先从Bytes Cache读取，否则从文件提取。
 * 线程安全：提取过程在锁内执行。
 * 注意：与getCoverArtBytes(url)不同，后者用于网络图片。
 */
suspend fun getLocalCoverBytes(filePath: String): ByteArray? {
    // 1. 检查Bytes Cache
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

- [ ] **Step 4: Add loadCarouselCoverArt() function (public, suspend)**

Add after `getLocalCoverBytes()`:

```kotlin
/**
 * 加载轮播封面Bitmap（384px）。
 * 1. 检查carouselCache
 * 2. 从Bytes Cache decode
 * 3. 未缓存则提取+decode
 */
suspend fun loadCarouselCoverArt(filePath: String): Bitmap? {
    if (filePath.isBlank()) return null

    // 1. 检查carouselCache
    carouselCacheLock.lock()
    val cached = carouselCoverCache[filePath]
    carouselCacheLock.unlock()
    if (cached != null && !cached.isRecycled) return cached

    // 2. 检查Bytes Cache
    byteCacheLock.lock()
    val bytes = coverArtByteCache[filePath]
    byteCacheLock.unlock()

    val bitmap = withContext(Dispatchers.IO) {
        if (bytes != null) {
            decodeSampledBitmapFromBytes(bytes, CAROUSEL_TARGET_SIZE)
        } else {
            val extractedBytes = extractAndCacheCoverBytes(filePath)
            extractedBytes?.let { decodeSampledBitmapFromBytes(it, CAROUSEL_TARGET_SIZE) }
        }
    } ?: return null

    // 3. 写入carouselCache
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

- [ ] **Step 5: Commit Chunk 1**

```bash
git add app/src/main/java/com/voxly/presentation/ui/ImageLoader.kt
git commit -m "feat(cover): add carouselCache and extractAndCacheCoverBytes

- Add carouselCoverCache (15 entries @ 384px) with dedicated lock
- Add extractAndCacheCoverBytes: single MediaMetadataRetriever call, public
- Add loadCarouselCoverArt: public suspend, carousel-specific 384px loading
- Add getLocalCoverBytes: public suspend (renamed from getCoverBytes)

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Chunk 2: ArtistDetailViewModel.kt — Fix precomputeAlbumCovers and add preloading

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/ArtistDetailViewModel.kt`

**Changes:**
1. Fix `precomputeAlbumCovers()` — use `extractAndCacheCoverBytes()` directly, single call per file
2. Add `preloadJob`, `preloadMutex` (as field), `Job` import, `preloadAdjacentAlbumCovers()`

---

- [ ] **Step 1: Read the current precomputeAlbumCovers function**

Locate the `precomputeAlbumCovers` function (~line 112). Understand its current implementation before modifying.

- [ ] **Step 2: Replace precomputeAlbumCovers with single-call version using extractAndCacheCoverBytes**

Replace the entire `precomputeAlbumCovers` function with:

```kotlin
/**
 * 预计算专辑封面路径。
 * 修复：每封面仅调用一次MediaMetadataRetriever。
 * 使用extractAndCacheCoverBytes直接获取bytes，null表示无封面。
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
                        // extractAndCacheCoverBytes会写入Bytes Cache
                        extractAndCacheCoverBytes(file.path) != null
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
```

Note: Uses `extractAndCacheCoverBytes` (public, @PublishedApi internal) which handles the MediaMetadataRetriever call and cache writing in one shot. This replaces the old code that made 2 calls per file.

- [ ] **Step 3: Add Job import and preloadMutex field**

Locate the existing field declarations. Add after `private val _albumCovers` field (~line 49):

```kotlin
private var preloadJob: Job? = null
private val preloadMutex = kotlinx.coroutines.sync.Mutex()
```

Add to imports section (around line 16-21):

```kotlin
import kotlinx.coroutines.Job
```

Note: `Mutex` is from `kotlinx.coroutines.sync.Mutex` (fully qualified in declaration to avoid import confusion).

- [ ] **Step 4: Add preloadAdjacentAlbumCovers function (NO duplicate mutex declaration)**

Add after the `precomputeAlbumCovers` function (after line ~137):

```kotlin
/**
 * 预加载相邻专辑封面（currentPage ± 1）。
 * 并发保护：使用Mutex确保同时只有一个预加载任务执行。
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

Important: `preloadMutex` is declared as a **field** in Step 3, not inside this function. Do NOT redeclare it here.

- [ ] **Step 5: Commit Chunk 2**

```bash
git add app/src/main/java/com/voxly/presentation/viewmodel/ArtistDetailViewModel.kt
git commit -m "feat(artist): fix precomputeAlbumCovers single-call and add preloading

- Fix: uses extractAndCacheCoverBytes for single MediaMetadataRetriever call
- Add: preloadAdjacentAlbumCovers with Mutex for concurrent protection
- Add: Job import and preloadMutex field
- Add: scroll-triggered preloading for currentPage ± 1

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Chunk 3: ArtistDetailScreen.kt — Add CarouselAlbumArtImage and scroll listener

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/artist/ArtistDetailScreen.kt`

**Changes:**
1. Add `CarouselAlbumArtImage` composable
2. Update `AlbumCard` to use `CarouselAlbumArtImage`
3. Add `LaunchedEffect` for carousel scroll preloading

---

- [ ] **Step 1: Add imports**

Locate the imports section. Add:

```kotlin
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.voxly.presentation.ui.loadCarouselCoverArt
```

Note: `LocalDensity`, `Dp` may already be imported. Verify before adding duplicates.

- [ ] **Step 2: Add CarouselAlbumArtImage composable**

Locate the `AlbumCard` function (~line 315). Add the new `CarouselAlbumArtImage` composable right before it:

```kotlin
/**
 * 轮播封面专用图片组件（384px）。
 * 使用produceState在IO线程加载，避免主线程阻塞。
 * key1 = filePath确保路径变化时重新加载。
 */
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

- [ ] **Step 3: Update AlbumCard to use CarouselAlbumArtImage**

Find the `AlbumCard` composable. Replace the `AlbumArtImage` call with `CarouselAlbumArtImage`:

```kotlin
// 在 AlbumCard 的 Box 内，将：
AlbumArtImage(
    filePath = albumArtPath,
    mediaStoreAlbumId = null,
    contentDescription = albumName,
    size = 140.dp,
    modifier = Modifier.fillMaxSize()
) { ... }

// 替换为：
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
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
```

Remove the now-unused `AlbumArtImage` import if no other usage remains.

- [ ] **Step 4: Add carousel scroll listener with LaunchedEffect**

Find the carousel setup in the LazyColumn (~line 224-248). Add `LaunchedEffect` for scroll preloading immediately after the `carouselState` declaration:

```kotlin
val carouselState = rememberCarouselState { albumList.size }

// 添加滚动监听：预加载相邻专辑封面
LaunchedEffect(carouselState.currentPage) {
    viewModel.preloadAdjacentAlbumCovers(carouselState.currentPage)
}
```

- [ ] **Step 5: Commit Chunk 3**

```bash
git add app/src/main/java/com/voxly/presentation/screens/artist/ArtistDetailScreen.kt
git commit -m "feat(artist): add CarouselAlbumArtImage and scroll preloading

- Add CarouselAlbumArtImage: dedicated 384px cover component
- AlbumCard now uses CarouselAlbumArtImage instead of AlbumArtImage
- Add LaunchedEffect on carouselState.currentPage for preloading

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Verification

After all chunks are committed, verify:

1. **Build succeeds:** `./gradlew assembleGithubDebug`
2. **Unit tests pass:** `./gradlew test` (if tests exist for affected files)
3. **Manual verification:**
   - Enter an artist with multiple albums
   - First entry: covers should appear within ~500ms
   - Navigate back and re-enter: covers should appear instantly (< 50ms)
   - Scroll carousel: adjacent covers preload without stutter
   - On xxhdpi/xxxhdpi device: covers should appear sharp (not blurry)

---

## Spec Reference

Full design specification: `docs/superpowers/specs/2026-03-21-artist-carousel-cover-optimization-design.md`
