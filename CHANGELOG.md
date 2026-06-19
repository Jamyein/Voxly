# Changelog

All notable changes to Voxly will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

### Fixed

- **Bottom-bar hide direction is now page-down / page-up instead of reversed.**
  The initial sign in `BottomBarVisibilityController.onPreScroll` was
  inverted — `shouldShow = dy < 0f` produced "hide on scroll-up, show on
  scroll-down" on actual devices, the opposite of the standard M3
  bottom-app-bar pattern. Flipped to `shouldShow = dy > 0f` (hide when
  the user drags the list downward to read more, show when they pull it
  back up to navigate). The accompanying KDoc now documents the
  verified `available.y` sign convention for `NestedScrollConnection`.

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

### Fixed

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

[Unreleased]: https://github.com/voxly/voxly/compare/v1.7.2...HEAD
[1.7.2]: https://github.com/voxly/voxly/compare/v1.7.1...v1.7.2
[1.7.1]: https://github.com/voxly/voxly/releases/tag/v1.7.1