# 状态栏遮挡顶栏修复方案

## 核心规则

**无底部导航栏的 Scaffold 页面**：TopAppBar **不要**设置 `windowInsets = WindowInsets(0.dp)`，让 Scaffold 自动处理。

## 场景对比

| 页面类型 | 布局方式 | TopAppBar insets |
|---------|---------|-----------------|
| 有底部导航栏 | Scaffold | 显式设为 `WindowInsets(0.dp)` + 外层 Box 用 `windowInsetsPadding(WindowInsets.statusBars)` |
| 无底部导航栏 | Scaffold | 使用默认行为（不设置 windowInsets） |
| 纯 Box 布局 | Box | 显式设为 `WindowInsets(0.dp)` + 外层用 `windowInsetsPadding()` |

## 修复案例

### MetadataEditorScreen.kt

**问题**：TopAppBar 设置了 `windowInsets = WindowInsets(0.dp)`，被状态栏遮挡

**解决**：删除该参数，让 Scaffold 自动处理

```kotlin
// 错误写法
TopAppBar(
    windowInsets = WindowInsets(0.dp),  // 删除这行
)

// 正确写法
TopAppBar(
    // 不设置 windowInsets，使用 Scaffold 默认行为
)
```

## 原理

- `windowInsets = WindowInsets(0.dp)` 是为 **Box 布局** 设计的
- Scaffold 的 TopAppBar 槽位会自动应用正确的 insets
- 设置为 0 会导致 TopAppBar 紧贴屏幕顶部，被状态栏遮挡
