# MP3 Tag Editor for Android

A modern Android application for editing music file metadata and scanning ReplayGain, built with Kotlin, Jetpack Compose, and Material Design 3.

## Features

### Core Features
- **Metadata Editing**: View and edit ID3 tags (title, artist, album, genre, year, etc.)
- **Album Art Management**: View, replace, or remove album artwork
- **Multi-format Support**: MP3, FLAC, OGG, M4A, WMA, WAV, APE, and more
- **ReplayGain Scanning**: Calculate and apply ReplayGain values for consistent volume
- **Online Metadata**: Fetch metadata from MusicBrainz database + Apple Music/iTunes
- **Multi-Source**: Combine results from multiple metadata providers
- **Material Design 3**: Modern UI with dynamic colors and dark mode support

### Architecture
- **Clean Architecture**: Separation of concerns with domain, data, and presentation layers
- **MVVM Pattern**: ViewModels for UI state management
- **Dependency Injection**: Hilt for dependency management
- **Reactive Programming**: Kotlin Coroutines and Flow for async operations

## Project Structure

```
app/src/main/java/com/mp3tag/android/
├── core/                    # Core utilities
├── data/                    # Data layer
│   ├── local/              # Local data sources
│   │   ├── AudioFileScanner.kt
│   │   ├── metadata/       # Jaudiotagger wrapper
│   │   └── replaygain/     # ReplayGain scanner
│   ├── remote/             # Remote APIs
│   │   ├── musicbrainz/    # MusicBrainz integration
│   │   └── itunes/         # Apple Music/iTunes integration
│   └── repository/         # Repository implementations
├── di/                     # Dependency injection modules
├── domain/                 # Domain layer
│   ├── model/              # Domain models (AudioFile, AudioMetadata, etc.)
│   └── repository/         # Repository interfaces
└── presentation/           # UI layer
    ├── MainActivity.kt
    ├── navigation/         # Navigation setup
    ├── screens/            # Screen composables
    ├── theme/              # Material Design 3 theme
    └── viewmodel/          # ViewModels
```

## Tech Stack

- **Language**: Kotlin 1.9.20
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt
- **Audio Processing**: jaudiotagger 3.0.1
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0
- **Image Loading**: Coil 2.5.0
- **Async**: Kotlin Coroutines + Flow

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Minimum SDK: 28 (Android 9.0)

### Building the Project

1. Clone the repository:
```bash
git clone https://github.com/yourusername/MP3-tag-Android.git
```

2. Open in Android Studio and sync Gradle files

3. Build the project:
```bash
./gradlew build
```

4. Run on device or emulator:
```bash
./gradlew installDebug
```

## Permissions

The app requires the following permissions:
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO`: Access audio files
- `WRITE_EXTERNAL_STORAGE`: Modify audio file metadata
- `INTERNET`: Fetch online metadata from MusicBrainz

## Features Implementation Status

### Completed ✅
- [x] Project setup and architecture
- [x] Material Design 3 theme and components
- [x] File browser with audio file scanning
- [x] Metadata reading and editing
- [x] Repository pattern implementation
- [x] Navigation setup with bottom bar
- [x] ReplayGain scanner implementation
- [x] MusicBrainz API integration

### In Progress 🚧
- [ ] Settings screen with preferences
- [ ] Batch operations UI
- [ ] Recent edits tracking
- [ ] Plugin system architecture (for future)

### Planned 📋
- [ ] Unit and UI tests
- [ ] Performance optimizations
- [ ] Export/Import functionality
- [ ] Custom themes

## API Integration

### MusicBrainz
The app uses the MusicBrainz API for online metadata lookup:
- Base URL: `https://musicbrainz.org/ws/2/`
- Rate limiting: 1 request per second
- Cover Art Archive: `https://coverartarchive.org/`

## Architecture Overview

### Data Flow
1. **UI Layer**: Composables observe ViewModel state
2. **ViewModel**: Manages UI state and calls Use Cases
3. **Repository**: Abstracts data sources (local/remote)
4. **Data Sources**: 
   - Local: MediaStore, File System, jaudiotagger
   - Remote: MusicBrainz API, Cover Art Archive

### Key Components

#### AudioFileScanner
Scans device storage for audio files using MediaStore API.

#### JaudiotaggerMetadataProcessor
Wraps the jaudiotagger library for reading/writing ID3 tags.

#### ReplayGainScanner
Analyzes audio files using Android's MediaExtractor to calculate loudness.

#### MusicBrainzRepository
Fetches metadata and cover art from MusicBrainz database.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Guidelines
- Follow Kotlin coding conventions
- Use meaningful commit messages
- Write tests for new features
- Update documentation as needed

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger) - Java library for audio metadata
- [MusicBrainz](https://musicbrainz.org/) - Open music encyclopedia
- [Material Design 3](https://m3.material.io/) - Google's design system

## Contact

For questions or suggestions, please open an issue on GitHub.
