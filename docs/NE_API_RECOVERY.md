# NE API 恢复指南

> 本文档提供恢复网易云音乐NE API的详细步骤。

## 恢复方式

有两种方式恢复NE API：

### 方式A: 从Git恢复（推荐）

如果代码还在Git历史中，可以直接恢复：

```bash
# 恢复NE API相关文件
git checkout HEAD -- app/src/main/java/com/voxly/data/remote/wangy/ne/

# 恢复LyricsRepositoryImpl中的引用
git checkout HEAD -- app/src/main/java/com/voxly/data/repository/LyricsRepositoryImpl.kt

# 恢复AggregatedOnlineMetadataRepository
git checkout HEAD -- app/src/main/java/com/voxly/data/repository/AggregatedOnlineMetadataRepository.kt

# 恢复DI配置
git checkout HEAD -- app/src/main/java/com/voxly/di/AppModule.kt
```

### 方式B: 手动重建

按照以下步骤手动恢复：

---

## 步骤1: 恢复NE API文件

创建以下文件：

```
app/src/main/java/com/voxly/data/remote/wangy/ne/
├── NeApi.kt
├── NeRepository.kt
├── NeModels.kt
├── NeCrypto.kt
└── YrcParser.kt
```

详细内容参考 `NE_API.md`

---

## 步骤2: 更新LyricsRepositoryImpl.kt

### 2.1 添加import
```kotlin
import com.voxly.data.remote.wangy.ne.NeRepository
```

### 2.2 添加依赖
```kotlin
private val neRepository: NeRepository,
```

### 2.3 添加搜索方法
```kotlin
/**
 * Searches lyrics from NetEase Cloud Music.
 * Uses new EAPI interface (neRepository).
 */
private suspend fun searchFromNetEase(
    trackName: String,
    artistName: String?
): Result<List<OnlineLyricsResult>> {
    Timber.d("NetEase lyrics search starting: trackName=$trackName, artistName=$artistName")

    // Use neRepository (new EAPI interface)
    val searchResult = neRepository.searchSongs(
        keywords = if (artistName != null) "$artistName $trackName" else trackName,
        page = 1,
        limit = 5
    )

    return if (searchResult.isSuccess) {
        val songs = searchResult.getOrNull() ?: emptyList()
        Timber.d("NetEase lyrics search success: found ${songs.size} songs for '$trackName'")

        val results = songs.map { song ->
            OnlineLyricsResult(
                id = song.id,
                trackName = song.trackName,
                artistName = song.artistName ?: "",
                albumName = song.albumName,
                duration = song.duration,
                hasSyncedLyrics = song.hasSyncedLyrics,
                hasPlainLyrics = song.hasPlainLyrics,
                isInstrumental = song.isInstrumental,
                source = "NetEase",
                sourceKey = song.sourceKey,
                preview = null
            )
        }
        Result.success(results)
    } else {
        val errorMsg = searchResult.exceptionOrNull()?.message ?: "Unknown error"
        Timber.e("NetEase lyrics search failed: $errorMsg")
        Result.failure(LyricsException("NetEase search failed: $errorMsg"))
    }
}
```

### 2.4 添加获取歌词方法
```kotlin
/**
 * Gets lyrics from NetEase by song ID.
 * Uses new EAPI interface (neRepository).
 */
suspend fun getNetEaseLyrics(songId: Long): Result<Lyrics> =
    withContext(Dispatchers.IO) {
        try {
            // Use neRepository (new EAPI interface)
            val response = neRepository.getLyrics(songId)

            if (response.isSuccess) {
                val lyrics = response.getOrNull()
                if (lyrics != null) {
                    Result.success(lyrics)
                } else {
                    Result.failure(LyricsException("No lyrics found"))
                }
            } else {
                val errorMsg = response.exceptionOrNull()?.message ?: "Unknown error"
                Result.failure(LyricsException("NetEase get lyrics failed: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(LyricsException("Network error", e))
        }
    }
```

### 2.5 更新searchOnlineLyrics方法
在LYRCLIB分支后添加NETEASE分支：
```kotlin
LyricsSource.NETEASE -> {
    if (settings.enableNetease) {
        searchFromNetEase(normalizedTrackName, normalizedArtistName)
            .map { applyLimit(it, settings.searchLimit) }
    } else {
        Result.success(emptyList())
    }
}
```

### 2.6 更新getOnlineLyrics方法
添加case：
```kotlin
"NetEase" -> getNetEaseLyrics(result.id)
```

### 2.7 更新searchFromAllSources方法
添加：
```kotlin
val neteaseDeferred = if (settings.enableNetease) {
    async { runCatching { searchFromNetEase(trackName, artistName).getOrNull() } }
} else null
```

---

## 步骤3: 更新AggregatedOnlineMetadataRepository.kt

### 3.1 添加import
```kotlin
import com.voxly.data.remote.wangy.ne.NeRepository
```

### 3.2 添加依赖
```kotlin
private val neRepository: NeRepository,
```

### 3.3 添加搜索方法
参考原文件中的`searchNetEaseByTrack()`和`searchNetEaseByAlbum()`方法

---

## 步骤4: 更新DI配置

### 4.1 AppModule.kt
在`provideLyricsRepository`方法中添加neRepository参数：
```kotlin
@Provides
@Singleton
fun provideLyricsRepository(
    @ApplicationContext context: Context,
    metadataProcessor: TagLibMetadataProcessor,
    settingsDataStore: SettingsDataStore,
    wangyRepository: WangyRepository,
    neRepository: NeRepository,  // 添加此行
    tengxRepository: TengxRepository
): LyricsRepository {
    return LyricsRepositoryImpl(
        context = context,
        metadataProcessor = metadataProcessor,
        settingsDataStore = settingsDataStore,
        wangyRepository = wangyRepository,
        neRepository = neRepository,  // 添加此行
        tengxRepository = tengxRepository
    )
}
```

---

## 步骤5: 验证编译

```bash
./gradlew assembleDebug
```

---

## 文件修改清单

| 文件 | 操作 | 优先级 |
|------|------|--------|
| `app/src/main/java/com/voxly/data/remote/wangy/ne/*` | 恢复 | P0 |
| `app/src/main/java/com/voxly/data/repository/LyricsRepositoryImpl.kt` | 修改 | P0 |
| `app/src/main/java/com/voxly/data/repository/AggregatedOnlineMetadataRepository.kt` | 修改 | P0 |
| `app/src/main/java/com/voxly/di/AppModule.kt` | 修改 | P0 |

---

## 注意事项

1. 确保NeCrypto中的常量（APP_VER, OS_VER）与当前网易云客户端版本匹配
2. 会话有效期为10天，需要处理过期情况
3. 匿名登录可能失败，需要有fallback策略

---

## 相关文档

- [NE_API.md](./NE_API.md) - NE API实现细节
