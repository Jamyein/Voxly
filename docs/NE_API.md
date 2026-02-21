# NetEase Cloud Music (NE) API 实现文档

> **状态**: 已从代码中移除
> **移除日期**: 2026-02-21
> **原因**: 简化代码，保留Simple API

## 概述

本文档记录了网易云音乐API（NE API）的完整实现，用于后续可能的恢复。NE API使用EAPI加密接口，需要匿名登录机制。

## 目录结构

```
app/src/main/java/com/voxly/data/remote/wangy/ne/
├── NeApi.kt           # Retrofit API接口定义
├── NeRepository.kt    # Repository实现（核心逻辑）
├── NeModels.kt        # 数据模型
├── NeCrypto.kt       # 加密工具类
└── YrcParser.kt      # YRC歌词解析器
```

## 文件详细说明

### 1. NeApi.kt

**位置**: `app/src/main/java/com/voxly/data/remote/wangy/ne/NeApi.kt`

**功能**: Retrofit API接口，定义EAPI请求

**关键配置**:
- Base URL: `https://interface.music.163.com/`
- 特性: 支持EAPI加密、匿名登录

**接口方法**:
```kotlin
interface NeApi {
    @POST
    suspend fun request(
        @retrofit2.http.Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
```

**请求头构建**:
```kotlin
fun buildCommonHeaders(cookie: String): Map<String, String>
fun buildLoginHeaders(cookie: String): Map<String, String>
```

---

### 2. NeCrypto.kt

**位置**: `app/src/main/java/com/voxly/data/remote/wangy/ne/NeCrypto.kt`

**功能**: 提供EAPI加密和匿名登录支持

**关键常量**:
```kotlin
const val EAPI_BASE_URL = "https://interface.music.163.com"
const val APP_VER = "3.1.3.203419"
const val OS_VER = "Microsoft-Windows-10--build-19045-64bit"
const val SESSION_EXPIRE_TIME = 10 * 24 * 60 * 60 * 1000L  // 10天
```

**核心方法**:

1. **generateDeviceId()**: 生成设备ID
```kotlin
fun generateDeviceId(): String  // 返回UUID格式
```

2. **generateClientSign()**: 生成客户端签名
```kotlin
fun generateClientSign(): String  // 格式: MAC@@@RANDOM@@@@@@HASH
```

3. **getAnonymousUsername()**: 生成匿名用户名（用于登录）
```kotlin
fun getAnonymousUsername(deviceId: String): String  // XOR + MD5 + Base64
```

4. **encryptParams()**: EAPI参数加密
```kotlin
fun encryptParams(url: String, jsonParams: String): ByteArray
// 算法: AES-ECB加密 + MD5签名
```

5. **aesDecrypt()**: 解密响应数据
```kotlin
fun aesDecrypt(data: ByteArray): String
```

---

### 3. NeModels.kt

**位置**: `app/src/main/java/com/voxly/data/remote/wangy/ne/NeModels.kt`

**数据模型**:

```kotlin
// 搜索响应
data class NeSearchResponse(
    val code: Int = 0,
    val data: NeSearchData? = null
)

data class NeSearchData(
    val totalCount: Int = 0,
    val resources: List<NeSearchResource> = emptyList()
)

// 歌曲信息
data class NeSimpleSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<NeArtist> = emptyList(),
    val album: NeAlbum? = null,
    val duration: Long = 0,
    val publishTime: Long = 0
)

// 歌词响应
data class NeLyricResponse(
    val code: Int = 0,
    val lrc: NeLrcContainer? = null,
    val tlyric: NeLrcContainer? = null,
    val romalrc: NeYrcContainer? = null,
    val yrc: NeYrcContainer? = null
)

// 专辑详情
data class NeAlbumDetailResponse(
    val code: Int = 0,
    val data: NeAlbumDetailData? = null
)
```

---

### 4. NeRepository.kt

**位置**: `app/src/main/java/com/voxly/data/remote/wangy/ne/NeRepository.kt`

**功能**: 核心业务逻辑，提供歌曲搜索、歌词获取、专辑详情

**接口定义**:
```kotlin
interface NeRepository {
    suspend fun searchSongs(
        keywords: String,
        page: Int = 1,
        limit: Int = 30
    ): Result<List<OnlineLyricsResult>>

    suspend fun getLyrics(songId: Long): Result<Lyrics>
    
    suspend fun getAlbumDetail(albumId: Long): Result<WangyAlbumDetail>
}
```

**实现细节**:

1. **会话管理**:
   - 使用SharedPreferences缓存会话
   - 有效期: 10天
   - 保存: userId, cookies

2. **匿名登录流程**:
   ```
   1. 生成deviceId和clientSign
   2. 构建预置Cookie (os, deviceId, osver, clientSign, channel, mode, appver)
   3. 调用 /eapi/register/anonimous 获取session
   4. 解析响应获取userId
   5. 保存cookie到SharedPreferences
   ```

3. **加密请求流程**:
   ```
   1. 构建参数JSON
   2. 替换路径: /eapi/ -> /api/
   3. 加密参数: AES-ECB + MD5签名
   4. 发送POST请求
   5. 解密响应
   ```

---

### 5. YrcParser.kt

**位置**: `app/src/main/java/com/voxly/data/remote/wangy/ne/YrcParser.kt`

**功能**: 解析YRC格式（增强同步歌词）

**支持的歌词格式**:
- YRC: 增强同步歌词（带每个字的时间戳）
- LRC: 标准同步歌词
- TLRC: 翻译歌词
- ROMALRC: 罗马字歌词

---

## 依赖注入配置

### DI Module中的配置

**文件**: `app/src/main/java/com/voxly/di/AppModule.kt`

```kotlin
@Provides
@Singleton
fun provideLyricsRepository(
    ...
    neRepository: NeRepository,  // 需要注入
    ...
): LyricsRepository {
    return LyricsRepositoryImpl(
        ...
        neRepository = neRepository,
        ...
    )
}
```

---

## 使用位置

NE API在以下位置被使用：

### 1. LyricsRepositoryImpl.kt
- 搜索歌词: `searchFromNetEase()`
- 获取歌词: `getNetEaseLyrics()`

### 2. AggregatedOnlineMetadataRepository.kt
- 搜索歌曲: `searchNetEaseByTrack()`
- 搜索专辑: `searchNetEaseByAlbum()`

### 3. SettingsDataStore.kt
- `SOURCE_ENABLED_NETEASE`
- `LYRICS_SOURCE_ENABLED_NETEASE`
- `METADATA_SOURCE_ENABLED_NETEASE`
- `COVER_SOURCE_ENABLED_NETEASE`
- `ONLINE_SEARCH_LIMIT_NETEASE`

---

## API端点

| 功能 | 端点 | 方法 |
|------|------|------|
| 歌曲搜索 | `/eapi/search/song/list/page` | POST |
| 获取歌词 | `/eapi/song/lyric/v1` | POST |
| 专辑详情 | `/eapi/v1/album/detail` | POST |
| 匿名登录 | `/eapi/register/anonimous` | POST |

---

## 注意事项

1. **会话失效**: 响应包含code 301/401时需要重新登录
2. **设备模拟**: 使用随机生成的MAC地址和客户端签名
3. **加密算法**: 使用AES-ECB + 自定义Padding
4. **User-Agent**: 模拟Windows桌面客户端

---

## 恢复步骤

如需恢复NE API，请参考 `NE_API_RECOVERY.md`
