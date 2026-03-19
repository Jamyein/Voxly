# 艺术家分隔符功能优化 — 设计文档

## 1. 概述

**问题：** 当前艺术家分隔符配置 UI 不直观，默认分隔符 `&\\` 不包含 `/`，且 `/` 在文本框中输入后会被自动删除。分隔符存储为拼接字符串（`"&\\"`），无法支持多字符分隔符。

**目标：** 优化分隔符配置 UI，提供标签式管理，修复 `/` 无法保存的问题，默认分隔符改为 `["&", "/", "\\"]`。

---

## 2. 数据层改动

### 2.1 存储格式

| 字段 | 当前 | 改进后 |
|---|---|---|
| `artistSeparatorEnabled` | `Boolean` | `Boolean`（不变） |
| `artistSeparators` | `String` (`"&\\"`) | `String` (JSON 序列化 `Set<String>`) |

**示例：** `["&", "/", "\\"]`

- 使用 Kotlinx Serialization 进行 JSON 序列化/反序列化
- 反斜杠 `\` 在 JSON 中自动序列化为 `\\`，读取时自动还原，用户无感知
- 底层 split 时通过 `Regex.escape()` 处理，无需手动转义

### 2.2 拆分逻辑改进

**文件：** `FileBrowserViewModel.kt`

```kotlin
private fun splitArtist(artist: String, separators: Set<String>): List<String> {
    if (artist.isBlank()) return emptyList()
    if (separators.isEmpty()) return listOf(artist)

    // 按长度降序排列，避免短分隔符优先匹配
    // 例如 "feat." 长度 6 大于 "f" 长度 1，避免 "f" 先匹配破坏 "feat."
    val sortedSeparators = separators.sortedByDescending { it.length }
    val regex = sortedSeparators.joinToString("|") { Regex.escape(it) }

    return artist.split(Regex(regex))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
```

### 2.3 向后兼容

首次读取时检测旧格式（不含 `[` 则为旧格式），一次性迁移：

```kotlin
// 旧格式示例: "&\\"
// 新格式示例: ["&","\\"]
private fun migrateArtistSeparators(raw: String): Set<String> {
    return if (raw.startsWith("[")) {
        // 新格式，直接解析
        try {
            json.decodeFromString<Set<String>>(raw)
        } catch (e: Exception) {
            emptySet()
        }
    } else {
        // 旧格式迁移：逐字符拆分（过滤空白字符）
        raw.toCharArray().filter { !it.isWhitespace() }.map { it.toString() }.toSet()
    }
}
```

---

## 3. UI 层改动

### 3.1 设置页面 — 分隔符配置入口

**文件：** `SettingsScreen.kt`

在"媒体设置"区域中，分隔符配置保持原有入口位置，仅更新 subtitle 显示：

```kotlin
SegmentedSwitchRow(
    title = stringResource(R.string.artist_separator),
    subtitle = stringResource(R.string.artist_separator_summary),
    checked = viewModel.artistSeparatorEnabled.value,
    onCheckedChange = { viewModel.setArtistSeparatorEnabled(it) },
    index = 1,
    count = 6
)

if (viewModel.artistSeparatorEnabled.value) {
    SegmentedClickableRow(
        title = stringResource(R.string.artist_separators),
        subtitle = buildSeparatorSubtitle(viewModel.artistSeparators.value),
        onClick = { showSeparatorDialog = true },
        index = 2,
        count = 6
    )
}
```

**Subtitle 格式：** 用空格连接所有分隔符，例如 `"& / \\"`

### 3.2 分隔符配置弹窗

**交互流程：**

1. 点击分隔符设置行 → 弹出 `AlertDialog`
2. 用户在弹窗内管理分隔符标签
3. 点击"确认"保存所有更改，"取消"放弃

**弹窗布局：**

```
┌─────────────────────────────────┐
│  艺术家分隔符                      │
│                                  │
│  [& ×]  [/ ×]  [\ ×]            │
│                                  │
│  ┌─────────────────────┐ [添加] │
│  │                     │        │
│  └─────────────────────┘        │
│                                  │
│           [取消]  [确认]          │
└─────────────────────────────────┘
```

**已有分隔符标签区：**
- 使用 `FlowRow` 排列，自动换行
- 每个标签样式：`[符号 ×]`
- **点击 X 按钮**：删除该分隔符
- **长按标签**：弹出确认删除对话框

**输入区：**
- `OutlinedTextField` — 用户输入任意字符串
- "添加"按钮 — 输入非空内容后点击，生成新标签并清空输入框
- 空输入点击添加：无反应（不会清空或触发任何操作）

**底部按钮：**
- "取消"：关闭弹窗，不保存
- "确认"：保存当前分隔符列表，关闭弹窗

### 3.3 标签显示规则

所有分隔符仅显示字符本身，不添加任何描述文字：

| 存储值 | UI 显示 |
|---|---|
| `&` | `&` |
| `/` | `/` |
| `\` | `\` |
| `feat.` | `feat.` |
| `ft.` | `ft.` |

### 3.4 默认分隔符

应用首次安装时，默认分隔符为：`["&", "/", "\\"]`

---

## 4. ViewModel 改动

### 4.1 SettingsViewModel

`artistSeparators` 类型从 `StateFlow<String>` 保持不变（DataStore 层仍为 String），但：

- 内部逻辑：存储为 JSON 字符串，读取时反序列化
- 新增 `artistSeparatorsSet: StateFlow<Set<String>>` 供 UI 层直接使用（显示标签列表）

### 4.2 FileBrowserViewModel

`splitArtist()` 方法按第 2.2 节改进。

---

## 5. 修复的问题

- `/` 分隔符无法保存的问题 — 标签式 UI 天然解决，不存在字符串拼接/转义问题
- `\` 在字符串中可能被错误处理的问题 — JSON 序列化自动处理
- 无法添加多字符分隔符的问题 — 新 UI 支持任意字符串分隔符

---

## 6. 影响范围

| 文件 | 改动类型 |
|---|---|
| `SettingsDataStore.kt` | 存储格式变更（JSON 序列化）+ 迁移逻辑 |
| `SettingsViewModel.kt` | 新增 `artistSeparatorsSet` StateFlow |
| `FileBrowserViewModel.kt` | `splitArtist()` 逻辑改进 |
| `SettingsScreen.kt` | 新增分隔符配置弹窗 UI |
| `strings.xml` | 新增分隔符相关字符串资源（如需要） |

---

## 7. 测试要点

1. 添加 `/` 分隔符后关闭弹窗，重新打开确认已保存
2. 添加 `\` 分隔符，验证 JSON 序列化/反序列化正确
3. 输入 `feat.` 等多字符分隔符，验证存储和拆分都正常
4. 验证旧数据迁移：模拟旧格式 `"&\\"` 读取后正确迁移为 `["&", "\\"]`
5. 验证拆分效果：`"Artist1 & Artist2"` → `["Artist1", "Artist2"]`
6. 长按标签删除确认弹窗正常显示
7. 空输入点击添加无异常
