# AGENTS.md

## Read first
- Load `lesson.md`
- Prefer executable sources of truth (Gradle config, CI workflows) over prose when they conflict.
- Update `lesson.md` automatically when encountering errors or issues. Format: `N. [Problem description]. Rule: [Correct approach that prevents this].` This becomes institutional knowledge for the repo.

## Project map (high signal)
- Single Android app module: `app/`.
- Main code: `app/src/main/java/com/voxly/`.
- Layer dirs: `core/`, `data/` (local/remote), `domain/`, `presentation/`, `di/`.
- Navigation uses Navigation3 (`androidx.navigation3`).

## Commands (verified)
- Build: `./gradlew build`
- Debug APK: `./gradlew compileGithubDebugKotlin`

## Signing and CI
- Release/dist builds require signing; build fails if unsigned.
- Local signing: set `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, optional `RELEASE_KEY_ALIAS` in `local.properties`.
- CI signing: `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, optional `SIGNING_KEY_ALIAS`.
- CI builds use `./gradlew assembleGithubRelease` and expect `app/voxly-release.keystore`.

## Native ReplayGain
- NDK/CMake build is enabled (`ndkVersion=26.1.10909125`, CMake 3.22.1). Keep this in mind when touching `app/src/main/cpp`.

## M3 Expressive UI rules (must follow)
- No hardcoded colors/shapes/typography; use `MaterialTheme` tokens.
- Use `spring()` animations and 4dp spacing grid.
- `ExpressiveScaffold` must disable default insets: `WindowInsets(0, 0, 0, 0)`.
- `ExpressiveTopAppBar` uses status bar insets only (top + horizontal).

## Anti-patterns
- Do not produce unsigned release/dist APKs.
- Do not use MediaStore for sampleRate/channels; read directly from file.
- Do not toggle UI state rapidly; debounce input.

## Workflow constraints (repo-specific)
- Keep project lightweight and the root directory tidy.
- Ensure the app's real-world performance is optimal.
- Ensure the app's resource usage is optimal.
- Prioritize the best possible user experience.
- New dependencies require user approval and note performance impact.
- For refactors/new features, start with single-file changes; only add new files/dirs if necessary.
- Place test-only files under `tests/`.
- For tasks with more than 3 steps, ask clarifying questions during planning phase.
- Large tasks: split into subtasks and run subagents in parallel.
- Use skills, MCP tools, and official docs for library/tool questions.
