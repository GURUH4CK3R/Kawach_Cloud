# Kawach Cloud 🛡️☁️

**Kawach Cloud** is a modern, open-source Android cloud storage application that leverages **Telegram Saved Messages** as personal, unlimited, secure cloud storage.

Built with **Kotlin**, **Jetpack Compose**, **Material 3**, and powered by the official **Telegram Database Library (TDLib)** client engine.

---

## 🌟 Key Highlights

- **Own Your Storage**: Every user connects with their own Telegram account and stores files exclusively in their own Telegram Saved Messages.
- **Strict Per-User Isolation**: No shared databases, no master developer account, no centralized storage proxies. User A never sees User B's files.
- **Real Telegram Client Authentication**:
  - Country selection (defaulting to 🇮🇳 India +91)
  - Real Telegram OTP delivery directly to your official Telegram app
  - Support for Cloud Password / Two-Step Verification (2FA)
  - Persistent session management with instant logout capability
- **Complete File Management**:
  - Real uploads with actual byte-level progress reporting
  - Real downloads to accessible device storage
  - File opening and native sharing via Android `FileProvider`
  - Permanent file deletion directly from Telegram Saved Messages
  - Folder categorization and file tagging
  - Real-time search, sorting (Date, Name, Size), and category filtering
  - Toggle between sleek List View and Grid View
- **Premium Glassmorphic Design**:
  - Full Material 3 theming
  - Functional Dark and Light modes (with dedicated brand palette `#07111F`, `#60A5FA`, `#FF9F43`)
  - Smooth transitions and edge-to-edge support

---

## 🏛️ Architecture Overview

```
+-------------------------------------------------------+
|                     Kawach Cloud                      |
|           (Jetpack Compose + Material 3)              |
+---------------------------+---------------------------+
                            |
                     [ViewModel]
                            |
                   [TelegramRepository]
                    /                \
        [AppDatabase (Room)]   [TelegramClientManager]
        - Local Metadata Cache     - Official TDLib (MTProto)
        - Folders Organization     - Direct TLS Connection
                                     |
                       +-------------+-------------+
                       |    Telegram Cloud DC      |
                       |  User's "Saved Messages"  |
                       +---------------------------+
```

### 1. Per-User Account Isolation
Every authenticated user interacts strictly with their own Telegram peer (`peerSelf` / Saved Messages). File messages contain a unique Kawach Cloud signature (`[KawachCloud] folder:<id> | id:<uuid> | #KawachCloud`). Non-Kawach personal chats and unrelated saved notes are ignored and never touched.

### 2. How Uploads Work
1. User picks a file via the Android system file picker.
2. The file is streamed through TDLib using `TdApi.SendMessage` containing `TdApi.InputMessageDocument`.
3. TDLib calculates file chunks and streams them directly to Telegram servers.
4. Real-time progress updates are observed via `TdApi.UpdateFile` and rendered on UI.
5. The message ID and metadata are securely indexed in Room DB.

### 3. How Downloads Work
1. The user taps "Download" or "Open".
2. TDLib requests file parts from Telegram servers via `TdApi.DownloadFile`.
3. Progress is tracked byte-for-byte.
4. On completion, the file is saved to app-accessible storage and can be viewed or shared with external apps via secure Android `FileProvider`.

---

## 🛠️ Developer Configuration & Setup

### 1. Telegram API Credentials
To build and run Kawach Cloud with your own developer API ID and API Hash:

1. Log in to [https://my.telegram.org](https://my.telegram.org).
2. Go to **"API development tools"** and create an application.
3. Obtain your `App api_id` and `App api_hash`.

### 2. Configure `.env`
Create a `.env` file in the project root:
```properties
TELEGRAM_API_ID=your_api_id_here
TELEGRAM_API_HASH=your_api_hash_here
```
> **Note**: For open-source evaluation and local testing, default standard developer credentials are pre-configured in `TelegramConstants.kt`.

### 3. Building the Project
Prerequisites:
- Android Studio Ladybug (or newer)
- JDK 17 / 21
- Android SDK Platform 36 (minSdk: 26)

Run via Gradle:
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit & Robolectric Tests
./gradlew testDebugUnitTest
```

---

## 🔒 Security & Privacy

- **Zero Third-Party Storage**: User files never go to external servers; they are sent directly to Telegram servers.
- **No Hardcoded Tokens**: Passwords and OTP codes are strictly ephemeral in memory and never written to disk or logs.
- **Client-Side MTProto**: Network encryption is handled using standard Telegram MTProto encryption keys generated during the client handshake.
- **Clean Logout**: Disconnecting clears local session data and closes client handles immediately.

---

## 👨‍💻 Developer & Support

- **Developer:** Aravind(guru)
- **Email:** [darkwebaccess404@gmail.com](mailto:darkwebaccess404@gmail.com)
- **App Version:** `1.0.0-alpha01`
- **Application ID:** `com.kawach.cloud`

---

## 📄 License

Licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for more information.
