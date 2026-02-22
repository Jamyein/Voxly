# presentation/screens - UI Screens

## OVERVIEW
Jetpack Compose screens organized by feature.

## SCREENS
| Screen | Purpose | Location |
|--------|---------|----------|
| FileBrowser | Browse audio files | `filebrowser/FileBrowserScreen.kt` |
| MetadataEditor | Edit ID3 tags | `metadata/MetadataEditorScreen.kt` |
| OnlineMetadata | Fetch online metadata | `metadata/OnlineMetadataScreen.kt` |
| Settings | App preferences | `SettingsScreen.kt`, `EnhancedSettingsScreen.kt` |
| ReplayGain | Scan loudness | `ReplayGainScannerScreen.kt` |
| BatchOperations | Bulk edit | `BatchOperationsScreen.kt` |
| Statistics | Library stats | `StatisticsScreen.kt` |
| DirectoryManagement | Folder management | `DirectoryManagementScreen.kt` |
| RecentEdits | Edit history | `RecentEditsScreen.kt` |
| LogViewer | Debug logs | `log/LogViewerScreen.kt` |

## STRUCTURE
- Screen composables in root or feature subdirectories
- Related ViewModels co-located in `presentation/viewmodel/`
- Use Material Design 3 components

## CONVENTIONS
- Navigation via `presentation/navigation/MP3TagNavHost.kt`
- Screen routes defined in `presentation/navigation/Screen.kt`
- State via ViewModels with StateFlow

## ANTI-PATTERNS
- NEVER toggle UI state rapidly (use debounce)
- NEVER directly modify audio files in composables (delegate to repository)
