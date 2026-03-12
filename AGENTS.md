# PROJECT KNOWLEDGE BASE

**Generated:** 2026-03-11
**Commit:** 69bea87
**Branch:** main

## OVERVIEW
Android MP3 Tag Editor with Kotlin, Jetpack Compose, Material Design 3. Clean Architecture + MVVM.

## STRUCTURE
```
app/src/main/java/com/voxly/
├── core/          # Utils: logging, crash handling
├── data/          # Data layer
│   ├── local/     # AudioFileScanner, Settings, cache
│   └── remote/    # APIs: wangy, tengx, musicbrainz, itunes, lrclib
├── di/            # Hilt modules
├── domain/        # Models, repository interfaces
└── presentation/  # UI: screens, viewmodels, theme
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Metadata editing | `data/local/metadata/`, `presentation/screens/metadata/` |
| Online metadata | `data/remote/` (multiple APIs) |
| Audio scanning | `data/local/AudioFileScanner.kt` |
| ReplayGain | `data/local/replaygain/` |
| UI screens | `presentation/screens/` |
| ViewModels | `presentation/viewmodel/` |
| Lyrics poster | `presentation/components/lyricsposter/` |

---

## COMMANDS

### Build & Run
```bash
./gradlew build           # Debug build
./gradlew assembleDist    # Distribution build (requires signing)
./gradlew assembleDebug   # Debug APK only
./gradlew assembleRelease # Release APK (requires signing)
```

### Testing
```bash
./gradlew test                    # Run all unit tests
./gradlew test --tests "com.voxly.data.local.metadata.TagLibMetadataProcessorTest"  # Single test class
./gradlew test --tests "com.voxly.data.local.metadata.TagLibMetadataProcessorTest.isFormatSupported*"  # Single test method
./gradlew test --tests "*Test"    # Run all test classes matching pattern
./gradlew connectedAndroidTest    # Run Android instrumentation tests
```

### Linting
```bash
./gradlew lint                    # Run lint analysis
./gradlew lintRelease             # Lint release build
./gradlew ktlintCheck             # Check Kotlin style (if configured)
```

### Development
```bash
./gradlew clean                   # Clean build artifacts
./gradlew dependencyUpdates       # Check for dependency updates
./gradlew dependencies           # Show project dependencies
```

---

## CODE STYLE GUIDELINES

### Kotlin Conventions
- **Package**: `com.voxly` (NOT com.mp3tag.android)
- **DI**: Hilt with `@HiltViewModel`
- **Navigation**: Jetpack Navigation Compose
- **State**: ViewModel + StateFlow
- **Tests**: JUnit 4 + MockK + Turbine

### Import Order (Official Kotlin Style)
1. Android imports (`android.*`)
2. Kotlin standard library (`kotlin.*`)
3. Third-party libraries (`com.*`, `io.*`, `org.*`)
4. Project imports (`com.voxly.*`)
5. Java imports (`java.*`, `javax.*`)

Example:
```kotlin
package com.voxly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.Logger
import com.voxly.data.repository.XxxRepository
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
```

### Naming Conventions
- **Classes**: PascalCase (`MetadataEditorViewModel`, `AudioFileScanner`)
- **Functions**: camelCase (`readMetadata`, `isFormatSupported`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_SEARCH_RESULTS`, `TAG`)
- **Private properties**: camelCase with `_` prefix for backing fields (`_state`, `_items`)
- **ViewModel state**: `_camelCaseStateFlow` for mutable, `camelCaseStateFlow` for immutable

### Types & Null Safety
- **Prefer** `StateFlow<T>` over `LiveData<T>` for UI state
- **Prefer** `val` over `var` - use `var` only when mutation is necessary
- **Use** nullable types (`T?`) explicitly - avoid `!!` operator
- **Use** `by lazy` for expensive initialization
- **Use** `object` for singletons

### Error Handling
- **Use** Result wrapper for operations that can fail:
  ```kotlin
  sealed class Result<out T> {
      data class Success<T>(val data: T) : Result<T>()
      data class Error(val exception: Throwable) : Result<Nothing>()
  }
  ```
- **Never** use empty catch blocks (`catch(e) {}`)
- **Use** Timber for logging (`Timber.tag(TAG).d(...)`)
- **Never** suppress errors with `@Suppress` unless absolutely necessary

### Compose Guidelines
- **Always** use `@Composable` annotation for composable functions
- **Use** `remember` and `rememberSaveable` for state that survives recomposition
- **Use** `derivedStateOf` for expensive computations
- **Avoid** rapidly toggling UI state - use debounce for user input
- **Use** `ExpressiveScaffold` and `ExpressiveTopAppBar` for consistent insets handling

### Threading
- **Use** `viewModelScope` for ViewModel coroutines
- **Use** `Dispatchers.IO` for file I/O and network operations
- **Use** `Dispatchers.Main` for UI updates
- **Prefer** `withContext()` for explicit dispatcher switching

---

## ANTI-PATTERNS (THIS PROJECT)
- **NEVER** produce unsigned APKs (`build.gradle.kts`)
- **NEVER** use MediaStore for sampleRate/channels (always read from file)
- **NEVER** toggle UI state rapidly (debounce required)
- **NEVER** use `as any` or `@ts-ignore` to suppress errors
- **NEVER** use empty catch blocks

---

## UNIQUE STYLES
- Multi-source metadata: 5 APIs (Wangy, Tengx, MusicBrainz, iTunes, LRCLib)
- Custom crypto for Wangy API (`data/remote/wangy/crypto/`)
- Room database for music library caching
- MD3-style lyrics poster color extraction with tonal palette generation

---

## 重要规则（必须遵守）
> 不允许私自进行更改
1. 永远保持项目的轻量化
2. 需要得到用户的同意才能添加新的依赖，并且需要注明新依赖对项目性能的影响
3. 保持项目文件树结构简洁
4. 保持根目录整洁
5. 用于test的文件统一放置在tests/
6. 重构时或添加/修改新功能时应首先在单文件进行修改，单文件无法满足时才考虑建构目录。
7. 用户提出需求时需要利用提问问题工具获取更详细细节。
8. 不要每完成一个todo进行一次build测试，在所有todo完成后才进行一次build测试。

---

## 外部文件加载

重要提示：当遇到文件引用（例如 @rules/general.md）时，请根据需要动态加载。这些引用仅与当前具体任务相关。

操作说明：

- 请勿预先加载所有引用 - 根据实际需求进行懒加载
- 加载后，将内容视为必须遵守的指令，它们会覆盖默认设置
- 需要时可递归跟随引用
