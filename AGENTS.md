# AGENTS.md

This is a **living knowledge base**, not a static config. Every entry has one purpose: prevent an agent from making a preventable mistake. Entries are added from hard-earned debugging sessions and removed when the codebase evolves past them.

## Knowledge Distillation

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

**Enforcement**: A `.githooks/pre-commit` hook warns when source files change without updating `lesson.md`. Run `git config core.hooksPath .githooks` on fresh clones to enable it. If `build_verify.bat` detects a compile failure, it also prints a reminder.

## Read first (mandatory session checklist)
**Before any code changes, do this in order:**
1. **Read `lesson.md`** — must load it into context with the Read tool before any code changes. Skipping this will re-introduce known errors.
2. **Review AGENTS.md versions table** — verify Compose BOM / Kotlin / AGP versions match `build.gradle.kts`
3. **Lint is non-blocking**: `abortOnError=false`. Ignore lint warnings; focus on compile errors only.
4. This file `repomix-output.xml` contains all the files in the repository combined into one. If user want to refactor the code, please review it first.

**During the session, on every error:**
- Append root cause + rule to `lesson.md` immediately (before fixing). See protocol above.

**At session end / before commit:**
- Check: did any error occur that isn't in `lesson.md`? If so, add it now.

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
| AGP | 9.2.1 |
| Kotlin | 2.4.0 (via AGP built-in + Compose/Serialization plugins) |
| Gradle | 9.4.1 |
| KSP | 2.3.9 (KSP2 semver — supports current Kotlin) |
| Compose BOM | 2026.05.01 |
| Material3 | 1.5.0-alpha21 (overrides BOM) |
| Compose Animation | 1.11.2 (overrides BOM) |
| Foundation-layout | BOM-resolved (separate artifact for FlexBox/Grid APIs) |
| Navigation3 | 1.1.2 |
| adaptive-navigation3 | 1.3.0-beta02 |
| Hilt | 2.59.2 (requires `kotlin-metadata-jvm` override — see lesson #18) |
| Room | 2.8.4 (KSP, not kapt) |
| Coil | 3.5.0 |
| Retrofit | 3.0.0 |
| OkHttp | 5.4.0 |
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
- **ABI split**: `build.gradle.kts` includes `arm64-v8a` only by default (no universal APK). minSdk=30 means no 32-bit-only devices exist; NDK 29 deprecates `armeabi-v7a`. The `-PbuildAbi` Gradle property is **comma-separated** (e.g. `-PbuildAbi=arm64-v8a,x86_64`) and a single invocation produces one split APK per ABI. CI defaults to `arm64-v8a` via `-PbuildAbi=arm64-v8a` (ReleaseBuild, NightlyBuild) or both arm64-v8a and x86_64 (CommitBuild for emulator/Chromebook testers) and finds APKs in `app/build/outputs/apk/github/{release,debug}/`.

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
- **FlexBox** (`foundation-layout` artifact): Requires `@OptIn(ExperimentalFlexBoxApi::class)` + `import androidx.compose.foundation.layout.ExperimentalFlexBoxApi`. Config DSL uses `direction(FlexDirection.Row)`, `wrap(FlexWrap.Wrap)`, `justifyContent(FlexJustifyContent.Start)`, `alignItems(FlexAlignItems.Center)`, `gap(dp)`.
- **SlotTable link buffer**: Set `ComposeRuntimeFlags.isLinkBufferComposerEnabled = true` in `Application.onCreate()` BEFORE `setContent()`. Requires `@OptIn(ExperimentalComposeApi::class)`. Must add `-assumevalues class androidx.compose.runtime.ComposeRuntimeFlags { boolean isLinkBufferComposerEnabled return true; }` in proguard-rules.pro to prevent R8 compile-time evaluation from forcing it false in release builds.
- Theme uses `MotionScheme.expressive()`, dynamic colors (Android 12+), MD3 Expressive color schemes.

## Workflow rules
- Keep project lightweight and the root directory tidy.
- New dependencies require user approval with performance impact noted.
- For refactors/new features, start with single-file changes; only add new files/dirs if necessary.
- For tasks with more than 3 steps, ask clarifying questions during planning phase.
- Large tasks: split into subtasks and run subagents in parallel.
- Use skills, MCP tools, and official docs for library/tool questions.
- Baseline Profile exists at `app/src/main/baseline-prof/baseline-prof.txt` — keep it updated when adding hot paths.
