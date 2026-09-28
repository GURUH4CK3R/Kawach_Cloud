# Kawach Cloud

An open-source Android cloud storage client utilizing Telegram's Saved Messages as a backend.

> **Disclaimer**: Kawach Cloud is an independent open-source project and is not affiliated with, endorsed by, sponsored by, or officially connected to Telegram FZ-LLC.

---

## Features

- **Telegram MTProto Authentication**: Direct client login using phone number, official Telegram OTP, and 2FA cloud password challenge. Session keys persist securely on the device.
- **Selective Kawach Indexing**: Only files uploaded through Kawach Cloud with caption signatures (`[KawachCloud]`) are indexed and displayed. Your existing personal chats and private messages remain untouched.
- **Multi-File Selection & Upload Queue**: Select single or multiple files (images, videos, documents, archives) in a single operation. Controlled sequential upload queue with live progress tracking and cancellation.
- **In-App Image Viewer**: Inspect photos with pinch-to-zoom (up to 5x), pan gestures, double-tap zoom reset, and background retrieval from Telegram.
- **Built-In Video Player**: Integrated AndroidX Media3 (ExoPlayer) video playback supporting play/pause, 10-second seeking, duration tracking, mute toggle, and fullscreen viewing.
- **File Management & Folders**: Create custom folders, organize files, filter by category (Images, Videos, Documents, Audio, Archives), search by name, and sort by date, name, or size.
- **Public Downloads**: Download files directly to Android's public `Downloads/` directory using modern `MediaStore` with collision-safe duplicate naming (`file (1).ext`).
- **Synchronized Deletion**: Deleting a file removes the message from Telegram Saved Messages, purges the local Room database entry, and cleans up cached media.
- **Material 3 Dual Theme**: Responsive UI designed with Material 3 tokens, featuring Light Theme (`#F4F9FF`), Dark Theme (`#07111F`), and System Default modes.

---

## Architecture

```text
+-------------------------------------------------------------+
|                     Jetpack Compose UI                      |
|  (HomeScreen, FilesScreen, SettingsScreen, Media Preview)   |
+------------------------------+------------------------------+
                               | StateFlow / Events
+------------------------------v------------------------------+
|                       KawachViewModel                       |
+------------------------------+------------------------------+
                               |
+------------------------------v------------------------------+
|                      TelegramRepository                     |
+---------------+------------------------------+--------------+
                |                              |
+---------------v--------------+  +------------v--------------+
|     Room Database (SQLite)   |  |   Official TDLib Engine   |
| (FileEntity & FolderEntity)  |  |   (io.github.tdlib-android) |
+------------------------------+  +------------+--------------+
                                               | TLS / MTProto
                                  +------------v--------------+
                                  |   Telegram Cloud Servers  |
                                  |    (User Saved Messages)  |
                                  +---------------------------+
```

---

## Technology

- **Language**: Kotlin `2.2.10`
- **Target SDK**: `36` (Android 15+) | **Minimum SDK**: `26` (Android 8.0)
- **UI Framework**: Jetpack Compose + Material 3 (`2024.09.00` BOM)
- **Telegram Client**: Official TDLib (`io.github.tdlib-android:core:0.1.1`, `ktx:0.1.1`)
- **Local Persistence**: Room Database (`2.7.0`)
- **Media Playback**: AndroidX Media3 ExoPlayer (`1.5.1`)
- **Image Loading**: Coil (`2.7.0`)
- **Concurrency**: Kotlin Coroutines & Reactive Flows (`1.10.2`)
- **Build System**: Gradle `9.3.1`, AGP `9.1.1`

---

## Data Flow

### Upload Flow
1. User selects one or more files through Android's system document picker (`ActivityResultContracts.GetMultipleContents`).
2. Files are enqueued in the ViewModel's sequential upload queue.
3. For each file, a temporary local cache copy is prepared and streamed directly to Telegram Saved Messages using TDLib's `SendMessage` with `InputMessageDocument`.
4. The message caption receives the Kawach identifier:
   `[KawachCloud] folder:<folderId> | id:<uuid> | name:<fileName> #KawachCloud`
5. Upon confirmation (`UpdateMessageSendSucceeded`), metadata is committed to the local Room database.

### Download & Preview Flow
1. When viewing a photo or video, the app first checks the local preview cache (`cacheDir/previews/`).
2. If not cached locally, TDLib downloads the file chunks directly from Telegram into temporary storage (`TdApi.DownloadFile`).
3. For full downloads, the completed file is written to Android's public `Downloads/` directory using `MediaStore.Downloads` with automatic duplicate renaming.

---

## Privacy

- **No Third-Party Servers**: Kawach Cloud contains zero developer-hosted servers, proxy relays, or telemetry services. All network requests travel directly from the device to Telegram's official MTProto IP endpoints.
- **No Analytics or Trackers**: No analytics SDKs, advertising libraries, or behavioral trackers are included in the codebase.
- **Ephemeral Authentication Data**: Phone numbers, OTP codes, and 2FA passwords are kept exclusively in transient memory during the login handshake and are never saved to disk or logged.
- **Storage Scope**: Files are stored in Telegram Saved Messages under Telegram's standard cloud chat model. They are encrypted in transit and at rest on Telegram's infrastructure, but are **not** end-to-end encrypted like Telegram Secret Chats. Kawach Cloud does not claim zero-knowledge encryption or unlimited storage guarantees.

For more details, see [PRIVACY.md](PRIVACY.md).

---

## Build

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21
- Android SDK Platform 36

### Setup & Compilation
```bash
# 1. Clone the repository
git clone https://github.com/GURUH4CK3R/Kawach_Cloud.git
cd Kawach_Cloud

# 2. Copy the sample environment configuration
cp .env.example .env

# 3. Run unit tests
./gradlew testDebugUnitTest

# 4. Build debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Status

**Alpha (`1.0.0-alpha01`)**: Under active development. Core authentication, selective file indexing, multi-file upload queue, in-app media preview, and folder organization are fully functional.

---

## Security

Please report security issues responsibly. See [SECURITY.md](SECURITY.md) for details.

---

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for terms.

---

## Author

**Aravind(guru)**  
GitHub: [@GURUH4CK3R](https://github.com/GURUH4CK3R)  
Email: `darkwebaccess404@gmail.com`
