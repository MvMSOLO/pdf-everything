# PDF Everything

A Compose Multiplatform project for Android and Windows Desktop.

## Project Structure

- `composeApp/`: The main application module.
    - `src/commonMain/`: Shared UI and logic across all platforms.
    - `src/androidMain/`: Android-specific implementation and resources.
    - `src/desktopMain/`: Windows Desktop-specific implementation and resources.
- `gradle/`: Gradle configuration and version catalog (`libs.versions.toml`).
- `build_all.bat`: A batch script to build artifacts for all supported platforms.

## Building the Project

You can build the application for both Android and Windows using the provided `build_all.bat` script.

### Using the Build Script (Windows)

Simply run the following command in your terminal:

```cmd
build_all.bat
```

This script will:
1. Generate the Android debug APK using `:composeApp:assembleDebug`.
2. Generate a runnable Windows app image using `:composeApp:createDistributable`.

### Manual Build Commands

If you prefer to run commands manually:

- **Android APK**: `./gradlew :composeApp:assembleDebug`
- **Windows EXE**: `./gradlew :composeApp:createDistributable`

The build artifacts will be located in:
- Android: `composeApp/build/outputs/apk/debug/`
- Windows: `composeApp/build/compose/binaries/main/app/pdf-everything/pdf-everything.exe`

## Requirements

- JDK 17 or higher
- Android SDK (for Android build)
- Windows OS (for Windows desktop build)
