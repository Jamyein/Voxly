# M3E MaterialShapes 封面形状设计

## 概述

在Voxly应用中引入M3E MaterialShapes库，为不同场景的封面图片使用丰富的预定义图形，提升UI视觉表达力。

## 设计决策

| 场景 | 组件 | 新形状 |
|------|------|--------|
| 艺术家封面 | ArtistListItem | `MaterialShapes.Sunny` |
| 文件封面 | AudioFileStandardRow 等 | `MaterialShapes.Cookie9Sided` |
| 封面搜索封面 | CoverThumbnail | `MaterialShapes.SoftBurst` |
| 元数据搜索封面 | ReleaseCover | `MaterialShapes.SoftBurst` |

### 形状选择理由

- **Sunny**: 阳光形状，适合人物/艺人头像，传达积极形象
- **Cookie9Sided**: 9边饼干形，独特且美观，适合音乐文件
- **SoftBurst**: 柔和爆发形，适合在线搜索结果，有视觉吸引力

## 技术实现

### 依赖要求

M3E MaterialShapes 来自 `androidx.compose.material3:material3`，需要版本 1.5.0-alpha14+。

### 实现方式

使用 `Modifier.clip(RoundedPolygonShape(shape))` 包裹封面组件：

```kotlin
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedPolygonShape

// 示例
modifier.clip(RoundedPolygonShape(MaterialShapes.Sunny))
```

### 需要修改的文件

1. **FileBrowserItems.kt** - ArtistListItem (第242行)
2. **SegmentedListComponents.kt** - AudioFileStandardRow, AudioFileStandardRowWithMenu, AudioFileStandardRowCompact
3. **OnlineCoverSearchScreen.kt** - CoverThumbnail (第384行)
4. **OnlineMetadataScreen.kt** - ReleaseCover (第422行)

## 兼容性

- 需要 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` 注解
- RoundedPolygonShape 需要 Android API 21+
