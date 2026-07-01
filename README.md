# Barcode Scanner

A barcode scanning Android app built with Kotlin, Jetpack Compose, CameraX, and ML Kit.

## Features

- **Camera Scanning**: Real-time barcode scanning using CameraX and ML Kit
- **Image Scanning**: Select images from gallery and scan barcodes
- **Multiple Formats**: Supports QR Code, EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Code 93, Codabar, ITF, Data Matrix, PDF417, Aztec
- **Result Actions**: Copy and share scanned results

## Tech Stack

- Kotlin 1.9.0
- Jetpack Compose
- CameraX 1.3.0
- ML Kit Barcode Scanning 17.2.0
- Navigation Compose 2.7.5
- Material Design 3

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 34

### Steps

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to `/workspace/BarcodeScanner` and open it
4. Wait for Gradle sync to complete
5. Connect an Android device or start an emulator
6. Click "Run" (or press Shift+F10)

### Build APK via Command Line

```bash
cd /workspace/BarcodeScanner
./gradlew assembleDebug
```

The debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
BarcodeScanner/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/barcodescanner/
│   │   │   ├── MainActivity.kt           # Main entry point with navigation
│   │   │   ├── ui/
│   │   │   │   ├── screen/               # Compose screens
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   ├── CameraScreen.kt
│   │   │   │   │   ├── ImageScanScreen.kt
│   │   │   │   │   └── ResultScreen.kt
│   │   │   │   ├── viewmodel/            # ViewModels
│   │   │   │   │   └── ScanViewModel.kt
│   │   │   │   └── theme/               # Material theme
│   │   │   └── utils/
│   │   │       └── BarcodeAnalyzer.kt   # ML Kit image analyzer
│   │   ├── res/                         # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
```

## Permissions

- `CAMERA`: For real-time barcode scanning
- `READ_EXTERNAL_STORAGE`: For reading images (Android 12 and below)
- `READ_MEDIA_IMAGES`: For reading images (Android 13+)"

## License

MIT License
