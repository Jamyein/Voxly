# Changelog

All notable changes to Voxly will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.7.4]

### Fixed

- **8 critical + 10 high code-audit findings resolved across the codebase.**
- **CR-1: NDK decoder readSampleData zero-copy bug** — `AMediaExtractor_readSampleData` called with buffer size 0, decoder decoded uninitialized memory. Changed to `bufSize`.
- **CR-2: JNI function name mismatch** — C++ exported `nativeDecodeFileGain` but Kotlin declared `decodeFileGain`, causing `UnsatisfiedLinkError` at runtime. Renamed symbol and updated linker version script.
- **CR-3: Stack buffer overflow in decimation path** — Removed fixed-size `batchBuf[16384]`, feeds PCM directly to ebur128 from codec output buffer.
- **CR-4: MediaExtractor FD leak** — Extractor never released in success paths, leaking ~10k FDs per large scan. Extracted shared `decodeFile()` with `try-finally` lifecycle management.
- **CR-5: Silent album gain 0dB fallback** — When `nativeGetAlbumGain` returned null, album gain silently defaulted to 0 dB. Falls back to energy-average now.
- **CR-6: PCM data loss on >8MB decoder output** — Batch copy replaced with chunked-read loop flushing between iterations.
- **CR-7: Non-reentrant Mutex deadlock** — Removed deprecated `loadAudioFiles()` that wrapped `scan()` in the same `scanMutex`.
- **CR-8: Fragile album-scan freshness check** — Replaced `trackScanners.size == trackGains.size` with `all { scanner != null }` for correct mixed cache/fresh results.
- **HI-1: Decoder loop infinite hang** — Added consecutive-error counter with 100-try abort.
- **HI-2: Missing JNI exception checks** — Added `ExceptionCheck` after `GetShortArrayRegion`, `SetDoubleArrayRegion`, `GetLongArrayElements`.
- **HI-3: Channel count change ignored** — Aborts decode instead of silently using stale channel count.
- **HI-4: God ViewModel refactoring** — Extracted `MetadataSaveCoordinator` from 1072-line `MetadataEditorViewModel`.
- **HI-5: Large composable breaking** — Extracted `MetadataEditorTopAppBar` and `MetadataEditorLaunchers` from 1007-line screen.
- **HI-6: Dead code in navigation** — Removed unused `computeTransition` + `TransitionType` (50 lines).
- **HI-7: Channel leak on source error** — Wrapped 4 source `launch` blocks in `try-finally` to guarantee channel closure.
- **HI-8: Extraneous TagLib I/O on cache hit** — Cache-hit path skips TagLib parsing when `includeAlbumArt=false`.
- **HI-9: Sequential DataStore `.first()` storm** — Combined 15 individual settings flows into a single `StateFlow` via `combine()`.
- **HI-10: Missing navigation tests** — Added `TopLevelNavigationTest` with 6 test cases for backstack, pop, and multi-tab behavior.

## [1.7.3]

### Added

- **Floating bottom navigation bar now hides on scroll-down and reappears on scroll-up.**
  The pill-shaped floating bottom bar (and the standard M3 NavigationBar
  variant) now slides out of view when the user scrolls the active list
  (Files / Albums / Artists) down, and slides back in when they scroll up.
  The bar also re-appears whenever the user lands at the top of a list.
  Implementation follows the official M3 app-bars pattern
  (`kb://android/develop/ui/compose/components/app-bars`) — since M3 does not
  ship a `BottomAppBarScrollBehavior` (only the top-app-bar variant), the
  equivalent is wired by hand: a shared `BottomBarVisibilityController`
  receives scroll deltas from each screen's `NestedScrollConnection` and
  the `Scaffold`'s `bottomBar` slot wraps its content in
  `AnimatedBottomBarContainer`, an `AnimatedVisibility`-based slide-in /
  slide-out container. The collapsing top-app-bar scroll behavior is chained
  with the new hide/show connection via `chainNestedScrollConnections`, so
  both behaviors react to the same scroll gesture.
- **M3E Floating Pill / Capsule bottom navigation bar** as an alternative
  to the standard M3 `NavigationBar`, toggleable from settings. Uses the
  expressive pill / capsule shape from Material 3 Expressive.
- **EBU R128-compliant album gain** in the native libebur128 scanner.
  Computed via `ebur128_loudness_global_multiple` for true multi-track
  loudness integration. Adds a `nativeGetAlbumGain` JNI bridge,
  true-peak support, null-safety hardening, and album-grouping-aware
  cache keys.
- **Track-level / album-level scan events** for ReplayGain. `ScanStatus`
  now exposes `TRACK_COMPLETED` and `ALBUM_COMPLETED` per-track /
  per-album events, with a single terminal `COMPLETED` guaranteeing no
  duplicate completion callbacks and a progress-semantics fix in the
  helper `ALBUMS` mode.
- **`AlbumGroupingProvider` with cache-first album grouping.** Replaces
  disk reads during scan aggregation with a Room-backed projection DAO;
  the helper `ALBUMS` mode reuses the same provider. Includes DI wiring
  and unit tests covering fallback / chunking paths.
- **`skipExisting` ReplayGain scan mode.** A scan started with
  `skipExisting` reuses the cached result for any file already in the
  cache, dramatically shortening re-scans and library touch-ups.
- **Chainable `MediaQueryDispatcher` builder** for MediaStore-backed
  scans. Composes `MediaStore` queries through a fluent Builder,
  enabling incremental `DATE_MODIFIED` filtering and progressive
  emission without re-implementing the cursor lifecycle for each query
  variant.
- **SAF tree watcher with G1 orphan detection** across SAF-backed
  libraries. Reacts within milliseconds to external file additions /
  removals without polling.
- **`FilesBatchUpdated` event stream + cross-screen refresh sync.**
  Scan results stream add / remove / update deltas (`albumDiff`,
  `artistDiff`) to every consumer through a single broadcast channel,
  eliminating the full-list-replacement pattern in `LazyColumn`
  consumers.
- **Albums / Artists pages: search icon + pull-to-refresh** matching
  the existing Files-page UX.
- **FlexBox wrapping layout** via the `foundation-layout` artifact for
  adaptive row / wrap content. Enables the slot-table link-buffer
  composer flag in `Application.onCreate` for faster first-frame
  composition.
- **OkHttp 5.4 `EventListener`** wired to the existing `FileLoggingTree`
  so network errors (cache misses, connect / call / response body
  metrics) appear in logcat alongside scan errors.
- **Paging `totalCount` caching.** `AudioFilePagingSource` now caches
  `totalCount` across pages and refreshes on `LoadType.REFRESH`,
  removing redundant count queries during incremental pagination.

### Changed

- **Files / Albums / Artists pages now show adaptive grids on large screens.**
  On tablets, foldables (unfolded), and desktop-width windows, each page
  renders a single-pane adaptive grid instead of a side-by-side list-detail
  layout. Column count is calculated automatically based on container width
  via `GridCells.Adaptive`. Clicking an item navigates to the respective
  detail screen rather than opening a second pane:
  - **Files** page: audio files and directory overview both use
    `LazyVerticalGrid` + `GridCells.Adaptive(300.dp)` for directories
    and all-audios tabs. Click a file → metadata editor; click a directory →
    directory content.
  - **Albums** page: regular sort stays with `GridCells.Adaptive(160.dp)`;
    year-sort now renders year-group headers spanning the full grid row
    with `AlbumGridItem` cards beneath.
  - **Artists** page: changed from `LazyColumn` to
    `LazyVerticalGrid` + `GridCells.Adaptive(160.dp)` with new square-avatar
    `ArtistGridItem` cards.
  - Downlevel (phones, portrait foldable): the original side-by-side
    `ListDetailPaneScaffold` is preserved.
- **`ScanRepository` infrastructure moved to the domain layer.** The
  scan pipeline and the legacy `LibraryDataHolder` migrated from
  `data/` to `domain/`, mirroring the BoomingMusic layered architecture
  so the data layer can stay focused on Room / SAF / cache concerns.
- **Adaptive top-level navigation now uses the common-ui
  `Scaffold + NavigationBar` recipe**, with `WindowInsets(0)` on the
  outer Scaffold so the top app bar reaches the status bar and
  gesture-nav areas stay immersive.
- **QQ Music search migrated to the POST API with `zzcSign` signature**,
  with a GET fallback path for environments where the signed POST is
  unavailable.
- **`OnlineSearchSorter` rewritten with Levenshtein distance** for
  relevance scoring across MusicBrainz / NetEase / QQ Music results.
- **Compose stability, Room queries, and coroutine-dispatch patterns
  tuned.** Removed 45+ redundant `stateIn()` wrappers, switched hot
  Room queries to `Dispatchers.IO`-bound flows, and tightened
  `@Stable` / `@Immutable` annotations on shared models.
- **ReplayGain scanner performance hardened.** `EBUR128_MODE_HISTOGRAM`
  enabled for faster `loudness_range`; a `Semaphore` limits concurrent
  scans to prevent codec starvation; `scanQuality` downsampling
  delivers faster hi-res analysis (with PCM-level downsampling
  replacing decoder-format modification); a `CodecPool` reuses
  `MediaCodec` instances across same-format files with proper
  `flush` on stopped codecs and `closeAll` cleanup.
- **ReplayGain decoder selection strictly follows the Android docs.**
  Uses `findDecoderForFormat` instead of `createDecoderByType` for
  proper codec selection. MIME is overridden from `audio/raw` to the
  file-extension-suggested compressed format on a clean `MediaFormat`
  that includes sample rate and channel count.

### Fixed

- **Bottom-bar hide direction is now page-down / page-up instead of reversed.**
  The initial sign in `BottomBarVisibilityController.onPreScroll` was
  inverted — `shouldShow = dy < 0f` produced "hide on scroll-up, show on
  scroll-down" on actual devices, the opposite of the standard M3
  bottom-app-bar pattern. Flipped to `shouldShow = dy > 0f` (hide when
  the user drags the list downward to read more, show when they pull it
  back up to navigate). The accompanying KDoc now documents the
  verified `available.y` sign convention for `NestedScrollConnection`.
- **Song list now updates immediately after editing metadata.**
  Saving a metadata change in the editor updates the file's title, artist,
  album, cover, etc. in the Files / Albums / Artists lists without requiring
  a manual refresh or app restart. Previously, the song list could stay stale
  until the user navigated away and back, or pulled to refresh.
- **ReplayGain scan no longer appears to hang.**
  Starting a ReplayGain scan from the metadata editor finishes in seconds for
  a single track and no longer takes many minutes for "scan album" mode.
  The scanning indicator now transitions to the result as soon as the scan
  completes, even if all tracks in an album failed to decode.
- **ReplayGain cache no longer serves garbage results.** Non-finite
  gains are rejected at insert time; `pendingReplayGainInfo` null
  overwrite is guarded; album-grouping falls back to `albumArtist`
  then `artist`.
- **ReplayGain album scan reports accurate progress.** Filters by
  source album; always emits terminal `COMPLETED`; disposes the helper
  scope; updates the "remaining" list after the aggregator fallback;
  only marks the scan complete on the terminal `COMPLETED` event.
- **ReplayGain auto-refreshes the metadata editor UI on scan
  completion** and preserves the scan result across save and
  clear / rescan cycles so users no longer have to re-scan after
  editing metadata.
- **ReplayGain falls back to the original format** when the FLAC
  decoder fails at runtime on emulators, so scans succeed even when
  the system codec is missing.
- **ReplayGain scanner no longer leaks resources** — close-out paths
  release `MediaCodec` / extractor / `FileDescriptor` handles; cached
  tracks are excluded from album-gain aggregation.
- **Audio file cache uses path as the sole identity key**, with
  change events emitted via `SharedFlow` to prevent conflation drops
  during high-frequency scans. Post-save cache sync moved to
  `applicationScope`.
- **Album detail navigation from the artist page** now resolves to the
  correct album rather than the first item.
- **Whitelist directory songs sync after external deletion** — files
  removed from a whitelist path while the app is open are pruned on
  the next scan instead of lingering in the cache.
- **Launcher icon sizing and foreground color** corrected for adaptive
  icon masks on Android 13+.
- **CI: `CommitBuild` APK resolution is now robust** against
  intermediate path changes — uses the final APK path directly,
  handles `-PbuildAbi` correctly, and the workflow YAML is
  consistently indented.

### Removed

- **`LibraryDataHolder`** (legacy in-memory scan cache) — fully
  replaced by `ScanRepository` in the domain layer.
- **CI `-Pandroid.injected.build.abi`** internal property — replaced
  by the documented `-PbuildAbi` Gradle property.

## [1.7.2]

### Added

- **Adaptive layouts** for the file browser and metadata editor, supporting
  phones, foldables, and tablets side-by-side.
- **Albums and Artists views** with dedicated browsing tabs, pull-to-refresh,
  and a search entry point that matches the existing Files page search.
- **Lyrics poster generator** with four visual templates (classic, immersive,
  typography, collage) and a style selector.
- **Online metadata search** across MusicBrainz, NetEase, and QQ Music, with
  automatic artist / album / cover / lyrics merging.
- **Translated lyrics** are merged with original lyrics in dual-line LRC
  format for NetEase and QQ Music results.
- **Native ReplayGain** via libebur128 (JNI) with Kotlin fallback for devices
  where the native scanner cannot load.

### Changed

- **Scan pipeline rewritten.** Incremental scans now use `MediaStore`'s
  `DATE_MODIFIED` filter, the SAF tree watcher, and a periodic ContentObserver,
  reducing full-library scan times substantially for typical libraries.
- **Diff-based album / artist updates.** LazyColumn consumers now receive
  granular add/remove/update events instead of full-list replacements,
  reducing UI recomposition work.
- **Navigation rewritten** on top of Navigation 3 with an adaptive
  `ListDetailPaneScaffold` and a Material 3 Expressive bottom navigation bar.
- **OkHttp 5.4 instrumentation** wired to the existing file logging tree so
  network errors now appear in logcat alongside scan errors.

### Removed

- **WorkManager-based periodic scan.** Replaced by the SAF tree watcher and
  ContentObserver, which react faster and don't require WorkManager initialization.

## [1.7.1]

### Added

- Initial public release.

[Unreleased]: https://github.com/voxly/voxly/compare/v1.7.3...HEAD
[1.7.3]: https://github.com/voxly/voxly/compare/v1.7.2...v1.7.3
[1.7.2]: https://github.com/voxly/voxly/compare/v1.7.1...v1.7.2
[1.7.1]: https://github.com/voxly/voxly/releases/tag/v1.7.1