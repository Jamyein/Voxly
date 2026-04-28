# AGENTS.md

This is a **living knowledge base**, not a static config. Every entry has one purpose: prevent an agent from making a preventable mistake. Entries are added from hard-earned debugging sessions and removed when the codebase evolves past them.

## 知识沉淀 (Knowledge Distillation)

**How entries get in**: An agent hits an error, finds the real root cause (not the surface symptom), and writes a rule general enough to prevent future occurrences. Entries in `lesson.md` and AGENTS.md come from this same process.

**How entries leave**: When a dependency version changes, code is refactored, or a framework fix makes a workaround obsolete — the stale entry must be removed or updated. Outdated instructions are worse than none.

**What stays out**: Generic software advice ("write clean code"), obvious language conventions, long tutorials. If an agent wouldn't likely miss it, don't write it.

### lesson.md update protocol (mandatory)
When any error occurs (build break, crash, test failure, wrong behavior):
1. **Diagnose root cause** — dig past the surface symptom. Ask: "What coding pattern or knowledge gap caused this?"
2. **Extract a general rule** — express the fix as a reusable principle, not a one-off patch. The rule should prevent ANY future occurrence of the same class of error.
3. **Append to lesson.md** using format: `N. [Root cause / what went wrong]. Rule: [Preventive principle].`
4. **Do NOT skip** — even if the fix was trivial. The lesson is for the class of error, not the specific instance.
5. **Existing entries are reference** — if the same root cause already has a lesson, you can skip (but verify it's truly the same).

## Read first
- **Prefer Gradle config / CI workflows** over README/docs when they conflict (the README incorrectly says `jaudiotagger` — the real dep is `io.github.kyant0:taglib`).
- **Ask the user** when requirements, bugs, or plans are unclear — never assume intent.
- **验证版本是关键**: AGP 9.2.0 / Kotlin 2.3.21 / Compose BOM 2026.04.01 / Material3 1.5.0-alpha18. 使用错误版本会导致不可编译。
- **Lint 不影响构建**: `abortOnError=false`, `warningsAsErrors=false`. 无需修复 lint 警告，专注于编译错误即可。

## Project map
- **Single app module**: `app/` (root `foundation/`, `domain/` dirs are stale build artifacts — ignore)
- **Package**: `com.voxly`, entrypoint `MP3TagApplication.kt`
- **Source tree**: `app/src/main/java/com/voxly/`
  - `core/util/` — utilities (CrashHandler, LogManager, etc.)
  - `data/` — `local/` (Room, DataStore, SAF), `remote/` (Retrofit APIs), `lyrics/`, `repository/`
  - `domain/` — `model/`, `repository/`, `usecase/`, `util/`
  - `presentation/` — `navigation/`, `screens/`, `theme/`, `ui/`, `components/`, `viewmodel/`
  - `di/` — Hilt modules

## Key versions (verified from build.gradle.kts)
| Component | Version |
|-----------|---------|
| AGP | 9.2.0 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.04.01 |
| Material3 | 1.5.0-alpha18 (overrides BOM) |
| Compose Animation | 1.11.0 (overrides BOM) |
| Navigation3 | 1.1.1 |
| Hilt | 2.59.2 |
| Room | 2.8.4 (KSP, not kapt) |
| Coil | 3.4.0 |
| Retrofit | 3.0.0 |
| taglib (Kyant0) | 1.0.6 |
| NDK | 29.0.14206865 |
| CMake | 4.1.2 |
| minSdk / targetSdk / compileSdk | 30 / 36 / 37 |
| Java | 21 (toolchain + compileOptions) |

## Commands
- Full build: `./gradlew build`
- Fast Kotlin compile check: `./gradlew compileGithubDebugKotlin` (or `build_verify.bat` on Windows)
- Debug build: `./gradlew assembleGithubDebug`
- Release build: `./gradlew assembleGithubRelease` (requires signing)
- Shrink/mapping diagnostics: `./gradlew :app:minifyGithubReleaseWithR8`
- Unit tests: `./gradlew testGithubDebugUnitTest`
- **lint is non-blocking** (`abortOnError=false`, `warningsAsErrors=false`)

## Signing & CI
- Release/dist builds fail at configuration time if unsigned (GradleException in build.gradle.kts:152-161)
- Local signing: set `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, optional `RELEASE_KEY_ALIAS` in `local.properties`
- CI signing: env vars `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS` (default: voxly)
- CI keystore: `SIGNING_KEYSTORE_BASE64` secret → decoded to `app/voxly-release.keystore`
- CI workflows: CommitBuild (JDK 21, push to main/dev + PRs to main), NightlyBuild (manual), ReleaseBuild (manual, JDK 25)
- **ABI split**: only `arm64-v8a`, no universal APK. CI finds APK by `*arm64*.apk` pattern.

## Native ReplayGain (NDK)
- NDK/CMake is enabled: `libebur128` (pure C, ~50KB .so)
- **Dual-engine**: Native (default) + Kotlin fallback. `UnsatisfiedLinkError` is `Error` not `Exception` — wrap with `Throwable` handling.
- `@CriticalNative` for primitive-only JNI methods (minSdk=30 OK).
- CMake flags: `-fno-exceptions`, `-fno-rtti`, linker version script, 16KB page size.
- `ENABLE_LINKER_WARNINGS_AS_ERROR` CMake option enabled via `gradle.properties`.

## Testing
- Unit tests in `app/src/test/`: JUnit 4, MockK, Turbine (`app.cash.turbine`), `kotlinx-coroutines-test`
- Instrumentation tests in `app/src/androidTest/`: Compose UI test + Espresso
- **Not** in root `tests/` dir

## Framework pitfalls
- **Navigation3**: Routes are `@Serializable` data classes/objects implementing `NavKey`. `NavDisplay` + `TopLevelBackStack`. Shared element transitions use `SharedTransitionLayout`.
- **Hilt + WorkManager**: Default `WorkManagerInitializer` is disabled in manifest; `HiltWorkerFactory` is used.
- **Opt-in required**: `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on all MD3 Expressive composables. Compiler arg: `-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`.
- **Coil3**: `SingletonImageLoader.Factory` on Application. All `ImageRequest.Builder` must call `.size(px)` — never rely on View layout for size inference.
- **Single flavor** "github" (default). No other build flavors.
- **No version catalog** — deps declared inline in app/build.gradle.kts.
- **KSP** replaces kapt for all annotation processing (Hilt, Room).
- **`OverlayClip`** is an interface in `SharedTransitionScope`, not constructible from user code. Apply `.clip(shape)` directly on render targets.
- **`ExpressiveScaffold`** must disable default insets: `WindowInsets(0, 0, 0, 0)`.
- **`ExpressiveTopAppBar`** uses status bar insets only (top + horizontal).
- Theme uses `MotionScheme.expressive()`, dynamic colors (Android 12+), MD3 Expressive color schemes.

## Workflow rules
- Keep project lightweight and the root directory tidy.
- New dependencies require user approval with performance impact noted.
- For refactors/new features, start with single-file changes; only add new files/dirs if necessary.
- For tasks with more than 3 steps, ask clarifying questions during planning phase.
- Large tasks: split into subtasks and run subagents in parallel.
- Use skills, MCP tools, and official docs for library/tool questions.
- Baseline Profile exists at `app/src/main/baseline-prof/baseline-prof.txt` — keep it updated when adding hot paths.
