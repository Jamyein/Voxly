# 艺术家分隔符功能实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为艺术家 Tab 添加分隔符功能，支持在设置页面自定义分隔符

**Architecture:** 在现有 ViewModel 中添加艺术家分割逻辑，通过 DataStore 管理用户设置

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences

---

## 文件结构

- Modify: `app/src/main/java/com/voxly/data/local/SettingsDataStore.kt` - 添加新设置项
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt` - 添加分割逻辑
- Modify: `app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt` - 添加 UI 设置项

---

## 实现步骤

### Task 1: 添加 DataStore 设置项

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/SettingsDataStore.kt`

- [ ] **Step 1: 添加 Preferences Keys**

在 `companion object` 中添加：
```kotlin
val ARTIST_SEPARATOR_ENABLED = booleanPreferencesKey("artist_separator_enabled")
val ARTIST_SEPARATORS = stringPreferencesKey("artist_separators")
```

- [ ] **Step 2: 添加 Flow 属性**

在文件末尾（`proxyPort` 之后）添加：
```kotlin
/**
 * Artist separator enabled preference flow
 */
val artistSeparatorEnabled: Flow<Boolean> = context.settingsDataStore.data
    .map { preferences ->
        preferences[ARTIST_SEPARATOR_ENABLED] ?: true
    }

/**
 * Artist custom separators preference flow
 */
val artistSeparators: Flow<String> = context.settingsDataStore.data
    .map { preferences ->
        preferences[ARTIST_SEPARATORS] ?: "&\\"
    }
```

- [ ] **Step 3: 添加保存方法**

在 `setProxyPort` 方法之后添加：
```kotlin
/**
 * Save artist separator enabled preference
 */
suspend fun setArtistSeparatorEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { preferences ->
        preferences[ARTIST_SEPARATOR_ENABLED] = enabled
    }
}

/**
 * Save artist separators preference
 */
suspend fun setArtistSeparators(separators: String) {
    context.settingsDataStore.edit { preferences ->
        preferences[ARTIST_SEPARATORS] = separators.ifBlank { "&\"" }
    }
}
```

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/voxly/data/local/SettingsDataStore.kt
git commit -m "feat(settings): 添加艺术家分隔符设置项"
```

---

### Task 2: 修改艺术家聚合逻辑

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt:1210-1227`

- [ ] **Step 1: 读取分隔符设置**

在 `FileBrowserViewModel` 类中添加两个新的 Flow 属性（在 `_artists` 之后）：
```kotlin
val artistSeparatorEnabled: StateFlow<Boolean> = settingsDataStore.artistSeparatorEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

val artistSeparators: StateFlow<String> = settingsDataStore.artistSeparators
    .stateIn(viewModelScope, SharingStarted.Eagerly, "&\\")
```

- [ ] **Step 2: 添加分割辅助函数**

在 `aggregateData()` 函数之前添加：
```kotlin
/**
 * Split artist string by separators
 * @param artist The artist string to split
 * @param separators String containing separator characters (e.g., "&\")
 * @return List of split artist names (empty strings filtered out)
 */
private fun splitArtist(artist: String, separators: String): List<String> {
    if (artist.isBlank()) return emptyList()
    if (separators.isBlank()) return listOf(artist)

    val separatorChars = separators.toCharArray().filter { it.isWhitespace().not() }
    if (separatorChars.isEmpty()) return listOf(artist)

    val regex = separatorChars.joinToString("|") { Regex.escape(it.toString()) }
    return artist.split(Regex(regex))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
```

- [ ] **Step 3: 修改 aggregateData 中的艺术家聚合逻辑**

找到现有的艺术家聚合代码（行 1210-1227），替换为：
```kotlin
// Aggregate artists
val isSeparatorEnabled = artistSeparatorEnabled.value
val customSeparators = artistSeparators.value

val artistsMap = mutableMapOf<String, MutableList<AudioFile>>()

allFiles
    .filter { it.metadata.artist?.isNotBlank() == true }
    .forEach { file ->
        val artistField = file.metadata.artist!!

        if (isSeparatorEnabled && customSeparators.isNotBlank()) {
            // Split artist field
            val splitArtists = splitArtist(artistField, customSeparators)
            splitArtists.forEach { artistName ->
                artistsMap.getOrPut(artistName) { mutableListOf() }.add(file)
            }
        } else {
            // No splitting, use original artist field
            artistsMap.getOrPut(artistField) { mutableListOf() }.add(file)
        }
    }

val artistsList = artistsMap.map { (artistName, files) ->
    // 固定选择封面：优先选择有专辑封面的文件，否则使用第一个文件
    val coverFile = files.firstOrNull { it.metadata.album?.isNotBlank() == true }
        ?: files.firstOrNull()
    ArtistGroup(
        name = artistName,
        albums = files.mapNotNull { it.metadata.album }.distinct().sorted(),
        files = files.sortedBy { it.metadata.album },
        coverPath = coverFile?.path
    )
}.sortedBy { it.name.lowercase() }

_artists.value = artistsList
```

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt
git commit -m "feat(artist): 添加艺术家分隔符分割逻辑"
```

---

### Task 3: 添加设置页面 UI

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt`

- [ ] **Step 1: 添加字符串资源**

在 `res/values/strings.xml` 中添加：
```xml
<string name="artist_separator">Artist Separator</string>
<string name="artist_separator_summary">Split multi-artist fields for proper categorization</string>
<string name="artist_separators">Separators</string>
<string name="artist_separators_summary">Characters used to split artist names, default & \\</string>
```

在 `res/values-zh-rCN/strings.xml` 中添加：
```xml
<string name="artist_separator">艺术家分隔符</string>
<string name="artist_separator_summary">分割多艺术家字段以便正确分类</string>
<string name="artist_separators">分隔符</string>
<string name="artist_separators_summary">用于分割艺术家名称的字符，默认 & \\</string>
```

- [ ] **Step 2: 在 SettingsScreen 中添加设置项**

需要先读取 SettingsViewModel 中是否有相关的 Flow。如果没有，需要添加。

检查 `SettingsViewModel.kt` 是否有 `artistSeparatorEnabled` 和 `artistSeparators` 的暴露，如果没有，需要添加：
```kotlin
// In SettingsViewModel.kt, add:
val artistSeparatorEnabled = settingsDataStore.artistSeparatorEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

val artistSeparators = settingsDataStore.artistSeparators
    .stateIn(viewModelScope, SharingStarted.Eagerly, "&\\")
```

- [ ] **Step 3: 在 SettingsScreen 中添加 UI**

在 SettingsScreen.kt 中找到合适的位置添加新的设置区块（通常在"媒体库"相关设置附近）。

需要导入：
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
```

添加设置区块：
```kotlin
// Artist Separator Section
SettingsSection(title = stringResource(R.string.artist_separator)) {
    SettingsSwitchRow(
        title = stringResource(R.string.artist_separator),
        subtitle = stringResource(R.string.artist_separator_summary),
        checked = viewModel.artistSeparatorEnabled.value,
        onCheckedChange = { enabled ->
            viewModelScope.launch {
                settingsDataStore.setArtistSeparatorEnabled(enabled)
            }
        }
    )

    if (viewModel.artistSeparatorEnabled.value) {
        Spacer(modifier = Modifier.height(8.dp))

        var separatorText by remember { mutableStateOf(viewModel.artistSeparators.value) }

        SettingsClickableRow(
            title = stringResource(R.string.artist_separators),
            subtitle = separatorText.ifBlank { "& \\" },
            onClick = {
                // Show dialog to edit separators
                showSeparatorDialog = true
            }
        )

        if (showSeparatorDialog) {
            AlertDialog(
                onDismissRequest = { showSeparatorDialog = false },
                title = { Text(stringResource(R.string.artist_separators)) },
                text = {
                    OutlinedTextField(
                        value = separatorText,
                        onValueChange = { separatorText = it },
                        label = { Text(stringResource(R.string.artist_separators)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModelScope.launch {
                            settingsDataStore.setArtistSeparators(separatorText)
                        }
                        showSeparatorDialog = false
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSeparatorDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat(settings): 添加艺术家分隔符设置UI"
```

---

## 测试验证

1. 艺术家字段 "A & B" → 应显示 A、B 两个艺术家
2. 艺术家字段 "A \ B" → 应显示 A、B 两个艺术家
3. 关闭分隔符开关 → 艺术家字段保持原样
4. 自定义分隔符 → 按自定义字符分割

---

## 依赖关系

1. Task 1 (DataStore) → Task 2 (ViewModel) → Task 3 (UI)
2. 必须按顺序完成

---

**Plan complete and saved to `docs/superpowers/plans/2026-03-13-artist-separator-plan.md`. Ready to execute?**
