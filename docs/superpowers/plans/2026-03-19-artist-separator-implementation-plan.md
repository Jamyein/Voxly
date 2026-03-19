# 艺术家分隔符功能优化 — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化艺术家分隔符配置 UI，提供标签式管理，存储从字符串 `"&\\"` 改为 JSON `["&","/","\\"]`，修复 `/` 无法保存的问题。

**Architecture:** 分三层改动：DataStore（JSON 序列化+迁移）、ViewModel（Set<String> StateFlow）、UI（弹窗标签管理）。

**Tech Stack:** Kotlin + Jetpack Compose + Kotlinx Serialization + DataStore

---

## Chunk 1: DataStore 层 — JSON 序列化 + 迁移逻辑

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/SettingsDataStore.kt`

**Changes:**
- `artistSeparators` Flow 默认值从 `"&\\"` 改为 `"""["&","/","\\"]"""`
- `setArtistSeparators(String)` 内部改为 JSON 序列化
- 新增 `setArtistSeparators(Set<String>)` 重载
- 新增 `migrateArtistSeparators(raw: String): Set<String>` 私有方法

**Details:**

### Task 1.1: Update `artistSeparators` Flow 默认值

- [ ] **Step 1: 修改默认值**

Locate `SettingsDataStore.kt:909-912`:
```kotlin
val artistSeparators: Flow<String> = context.settingsDataStore.data
    .map { preferences ->
        preferences[ARTIST_SEPARATORS] ?: "&\\"
    }
```

Replace with:
```kotlin
val artistSeparators: Flow<String> = context.settingsDataStore.data
    .map { preferences ->
        preferences[ARTIST_SEPARATORS] ?: """["&","/","\\"]"""
    }
```

### Task 1.2: 新增 `migrateArtistSeparators` 迁移方法

- [ ] **Step 1: 在 `SettingsDataStore` 中添加迁移方法**

在 `setArtistSeparators` 方法之后添加（位置约 line 967）:

```kotlin
/**
 * 迁移旧格式分隔符字符串为 Set<String>
 * 旧格式: "&\\" → 新格式: ["&","\\"]
 */
private fun migrateArtistSeparators(raw: String): Set<String> {
    return if (raw.startsWith("[")) {
        // 新格式 JSON，直接解析
        try {
            json.decodeFromString<Set<String>>(raw)
        } catch (e: Exception) {
            // 解析失败，返回默认
            setOf("&", "/", "\\")
        }
    } else {
        // 旧格式：逐字符拆分（过滤空白字符）
        raw.toCharArray().filter { !it.isWhitespace() }.map { it.toString() }.toSet()
    }
}
```

### Task 1.3: 新增 `setArtistSeparators(Set<String>)` 重载

- [ ] **Step 1: 在 `setArtistSeparators(String)` 之后添加新重载**

Replace `setArtistSeparators(String)` at line 962-966:
```kotlin
suspend fun setArtistSeparators(separators: String) {
    context.settingsDataStore.edit { preferences ->
        preferences[ARTIST_SEPARATORS] = separators.ifBlank { """["&","/","\\"]""" }
    }
}
```

With both methods:
```kotlin
/**
 * Save artist separators preference (legacy String version for backward compat)
 */
suspend fun setArtistSeparators(separators: String) {
    context.settingsDataStore.edit { preferences ->
        preferences[ARTIST_SEPARATORS] = separators.ifBlank { """["&","/","\\"]""" }
    }
}

/**
 * Save artist separators preference (Set version — JSON serialization)
 */
suspend fun setArtistSeparators(separators: Set<String>) {
    context.settingsDataStore.edit { preferences ->
        preferences[ARTIST_SEPARATORS] = json.encodeToString(separators)
    }
}
```

- [ ] **Step 2: 验证 build**

Run: `./gradlew :app:compileGithubDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

---

## Chunk 2: SettingsViewModel 层 — Set<String> StateFlow + 新方法

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/SettingsViewModel.kt`

**Changes:**
- `artistSeparators` 默认值从 `"&\\"` 改为 `"""["&","/","\\"]"""`
- 新增 `artistSeparatorsSet: StateFlow<Set<String>>` — UI 层使用
- 新增 `setArtistSeparators(separators: Set<String>)` 方法

**Details:**

### Task 2.1: 更新 `artistSeparators` 默认值并新增 `artistSeparatorsSet`

- [ ] **Step 1: 更新 `artistSeparators` 默认值**

Locate `SettingsViewModel.kt:597-602`:
```kotlin
val artistSeparators: StateFlow<String> = settingsDataStore.artistSeparators
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = "&\\"
    )
```

Replace with:
```kotlin
val artistSeparators: StateFlow<String> = settingsDataStore.artistSeparators
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = """["&","/","\\"]"""
    )
```

- [ ] **Step 2: 新增 `artistSeparatorsSet` StateFlow**

After the `artistSeparators` block (after line 602), add:

```kotlin
/**
 * Artist separators as Set<String> for UI layer (tag display)
 */
val artistSeparatorsSet: StateFlow<Set<String>> = settingsDataStore.artistSeparators
    .map { raw ->
        migrateArtistSeparators(raw)
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = setOf("&", "/", "\\")
    )
```

Note: `migrateArtistSeparators` is in `SettingsDataStore`, so you need to call it through the dataStore. If you prefer to keep migration logic only in dataStore, you can instead expose a `artistSeparatorsSet: Flow<Set<String>>` from dataStore and then expose it in ViewModel. See the note below.

> **Note on architecture:** Since `migrateArtistSeparators` is a private method in `SettingsDataStore`, you have two options:
> - **Option A:** Make `migrateArtistSeparators` internal/public in dataStore and call it from ViewModel
> - **Option B:** Expose `artistSeparatorsSet: Flow<Set<String>>` from dataStore directly
>
> **Recommended: Option B** — keep the migration logic in the data layer. Update `SettingsDataStore` to also expose `artistSeparatorsSet: Flow<Set<String>>` (see Task 1.4), then ViewModel just forwards it.

### Task 2.2: 新增 `setArtistSeparators(Set<String>)` ViewModel 方法

- [ ] **Step 1: 添加新方法**

Locate `setArtistSeparators(String)` in `SettingsViewModel.kt` (line 652-656):
```kotlin
fun setArtistSeparators(separators: String) {
    viewModelScope.launch {
        settingsDataStore.setArtistSeparators(separators)
    }
}
```

Add after it:
```kotlin
/**
 * Set artist separators from UI (Set version — preferred)
 */
fun setArtistSeparators(separators: Set<String>) {
    viewModelScope.launch {
        settingsDataStore.setArtistSeparators(separators)
    }
}
```

- [ ] **Step 2: 验证 build**

Run: `./gradlew :app:compileGithubDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

---

## Chunk 3: FileBrowserViewModel 层 — splitArtist() 改造

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt`

**Changes:**
- `splitArtist()` 签名从 `(String, String)` 改为 `(String, Set<String>)`
- 内部按长度降序排列分隔符

**Details:**

### Task 3.1: 更新 `splitArtist()` 方法签名和实现

- [ ] **Step 1: 更新 `splitArtist` 方法**

Locate `FileBrowserViewModel.kt:1190-1201`:
```kotlin
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

Replace with:
```kotlin
private fun splitArtist(artist: String, separators: Set<String>): List<String> {
    if (artist.isBlank()) return emptyList()
    if (separators.isEmpty()) return listOf(artist)

    // 按长度降序排列，避免短分隔符优先匹配
    val sortedSeparators = separators.sortedByDescending { it.length }
    val regex = sortedSeparators.joinToString("|") { Regex.escape(it) }

    return artist.split(Regex(regex))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
```

### Task 3.2: 更新 `aggregateData()` 调用处

- [ ] **Step 1: 更新调用处**

Locate `FileBrowserViewModel.kt:1231-1244`:
```kotlin
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
```

The current code uses `artistSeparators.value` which is a `String`. You need to add `artistSeparatorsSet` to `FileBrowserViewModel` that provides `Set<String>`, or call the migration in the ViewModel.

**Option (recommended):** Add `artistSeparatorsSet: StateFlow<Set<String>>` to `FileBrowserViewModel` by forwarding from `settingsDataStore.artistSeparatorsSet` (exposed from dataStore in Chunk 1). See Chunk 1 Task 1.4.

After that, replace the calling code with:
```kotlin
val isSeparatorEnabled = artistSeparatorEnabled.value
val customSeparators = artistSeparatorsSet.value  // Set<String> now

val artistsMap = mutableMapOf<String, MutableList<AudioFile>>()

allFiles
    .filter { it.metadata.artist?.isNotBlank() == true }
    .forEach { file ->
        val artistField = file.metadata.artist!!

        if (isSeparatorEnabled && customSeparators.isNotEmpty()) {
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
```

### Task 3.3: 验证 build

- [ ] **Step 1: 编译验证**

Run: `./gradlew :app:compileGithubDebugKotlin 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

---

## Chunk 4: SettingsScreen 层 — 分隔符配置弹窗 UI

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt`

**Changes:**
- 新增 `SeparatorTag` 可组合函数（标签 + X 按钮 + 长按删除）
- 新增分隔符配置弹窗 `AlertDialog`
- 弹窗内：FlowRow 排列的标签列表 + 输入框 + 添加按钮
- 弹窗外：更新 subtitle 显示当前分隔符

**Details:**

### Task 4.1: 添加弹窗状态变量

- [ ] **Step 1: 添加弹窗状态**

Locate `SettingsScreen.kt` where `showSeparatorDialog` is defined (line 944):
```kotlin
var showSeparatorDialog by remember { mutableStateOf(false) }
```

This already exists. We need to also add:
- `separatorInput` — 输入框文本
- `separatorTags` — 弹窗内的工作副本（`MutableStateFlow<List<String>>`）

Add after `showSeparatorDialog`:
```kotlin
var separatorInput by remember { mutableStateOf("") }
val separatorTags = remember { mutableStateOf(viewModel.artistSeparatorsSet.value.toList()) }
```

### Task 4.2: 同步外部状态到弹窗

- [ ] **Step 1: 添加 LaunchedEffect 同步分隔符列表**

Find the area where `LaunchedEffect(viewModel.artistSeparators.value)` is used for `separatorText` (around line 1073). Add after that:

```kotlin
LaunchedEffect(viewModel.artistSeparatorsSet.value) {
    separatorTags.value = viewModel.artistSeparatorsSet.value.toList()
}
```

### Task 4.3: 新增 `SeparatorTag` 可组合组件

- [ ] **Step 1: 添加 SeparatorChip 组件**

在 `SettingsScreen.kt` 中找到合适位置（其他 Dialog 定义附近，或文件末尾），添加：

```kotlin
@Composable
private fun SeparatorChip(
    separator: String,
    onDelete: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .pointerInput(separator) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = separator,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(18.dp),
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
```

### Task 4.4: 替换现有分隔符弹窗

- [ ] **Step 1: 找到现有弹窗代码**

Locate the existing separator dialog in `SettingsScreen.kt` (around line 1209-1237).

Replace the **entire** `if (showSeparatorDialog)` block with:

```kotlin
if (showSeparatorDialog) {
    AlertDialog(
        onDismissRequest = { showSeparatorDialog = false },
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.artist_separators)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tags display area using FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    separatorTags.value.forEach { separator ->
                        SeparatorChip(
                            separator = separator,
                            onDelete = {
                                separatorTags.value = separatorTags.value - separator
                            },
                            onLongPress = {
                                // Show confirmation dialog (reuse current dialog mechanism)
                            }
                        )
                    }
                }

                // Long-press delete confirmation is handled by showing a small AlertDialog
                // For simplicity, onLongPress can immediately delete OR show a snackbar
                // The design spec says "long press → confirmation dialog"
                // We implement this via a local state for pending delete

                // Input area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = separatorInput,
                        onValueChange = { separatorInput = it },
                        label = { Text(stringResource(R.string.artist_separators)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val trimmed = separatorInput.trim()
                            if (trimmed.isNotBlank() && trimmed !in separatorTags.value) {
                                separatorTags.value = separatorTags.value + trimmed
                                separatorInput = ""
                            }
                        },
                        enabled = separatorInput.isNotBlank()
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setArtistSeparators(separatorTags.value.toSet())
                    showSeparatorDialog = false
                }
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { showSeparatorDialog = false }) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
```

### Task 4.5: 添加长按删除确认

- [ ] **Step 1: 添加待删除状态**

Add a new state variable near other dialog states:
```kotlin
var showSeparatorDialog by remember { mutableStateOf(false) }
var separatorInput by remember { mutableStateOf("") }
var pendingDeleteSeparator by remember { mutableStateOf<String?>(null) }
val separatorTags = remember { mutableStateOf(viewModel.artistSeparatorsSet.value.toList()) }
```

- [ ] **Step 2: 更新 SeparatorChip 的 onLongPress**

Change `onLongPress = { }` to:
```kotlin
onLongPress = { pendingDeleteSeparator = separator }
```

- [ ] **Step 3: 添加待删除确认弹窗**

Add this dialog AFTER the main separator dialog:
```kotlin
if (pendingDeleteSeparator != null) {
    AlertDialog(
        onDismissRequest = { pendingDeleteSeparator = null },
        shape = MaterialTheme.shapes.large,
        title = { Text("删除分隔符") },
        text = { Text("确定删除分隔符 \"${pendingDeleteSeparator}\" 吗？") },
        confirmButton = {
            TextButton(
                onClick = {
                    separatorTags.value = separatorTags.value - pendingDeleteSeparator!!
                    pendingDeleteSeparator = null
                }
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingDeleteSeparator = null }) {
                Text("取消")
            }
        }
    )
}
```

### Task 4.6: 更新入口 subtitle

- [ ] **Step 1: 更新 SegmentedClickableRow subtitle**

Locate the SegmentedClickableRow for artist_separators (around line 1098-1108):
```kotlin
SegmentedClickableRow(
    title = stringResource(R.string.artist_separators),
    subtitle = separatorText.ifBlank { "& \\" },
    onClick = {
        separatorText = viewModel.artistSeparators.value
        showSeparatorDialog = true
    },
    index = 2,
    count = 6
)
```

Replace `subtitle = separatorText.ifBlank { "& \\" }` with:
```kotlin
subtitle = viewModel.artistSeparatorsSet.value.joinToString(" "),
onClick = {
    // Sync latest before opening
    separatorTags.value = viewModel.artistSeparatorsSet.value.toList()
    separatorInput = ""
    showSeparatorDialog = true
},
```

And remove the now-unused `separatorText` variable and its LaunchedEffect (around line 1070-1075).

### Task 4.7: 验证 build

- [ ] **Step 1: 编译验证**

Run: `./gradlew :app:compileGithubDebugKotlin 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

---

## Chunk 5: 集成测试 + 收尾

**Files modified:**
- `app/src/main/java/com/voxly/data/local/SettingsDataStore.kt`
- `app/src/main/java/com/voxly/presentation/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt`
- `app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt`

### Task 5.1: 完整编译

- [ ] **Step 1: 编译整个项目**

Run: `./gradlew :app:compileGithubDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

### Task 5.2: 单元测试（如有现有测试）

- [ ] **Step 1: 运行现有相关测试**

Run: `./gradlew :app:testGithubDebugUnitTest 2>&1 | tail -20`
Expected: All tests pass

### Task 5.3: 提交

- [ ] **Step 1: 提交所有更改**

```bash
git add app/src/main/java/com/voxly/data/local/SettingsDataStore.kt \
       app/src/main/java/com/voxly/presentation/viewmodel/SettingsViewModel.kt \
       app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt \
       app/src/main/java/com/voxly/presentation/screens/SettingsScreen.kt
git commit -m "feat(artist-separator): tag-based separator UI with JSON storage

- Change separator storage from string '&\\' to JSON ['&','/','\\']
- Add migration from old format for backward compatibility
- Add tag-based separator dialog with add/delete support
- Update splitArtist() to use Set<String> with length-based sorting
- Fix '/' separator not saving issue

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```
