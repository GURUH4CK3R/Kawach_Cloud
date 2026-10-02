# Changelog

All notable changes to **Kawach Cloud** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-02

### Added
- **Initial Public Release of Kawach Cloud** (`v1.0.0`).
- **Direct Telegram MTProto Storage Backend**:
  - Full client-to-cloud integration with official TDLib (`io.github.tdlib-android:core:0.1.1` and `ktx:0.1.1`).
  - Unlimited file storage hosted directly in your Telegram Saved Messages (`peerSelf`).
  - Zero intermediary servers — all data and MTProto streams flow directly between your device and Telegram datacenters.
- **Secure Authentication Flow**:
  - Direct Telegram phone number login with OTP confirmation.
  - Two-Step Verification (2FA / Cloud Password) challenge support.
  - Ephemeral authentication handling with zero password persistence in persistent storage or logs.
- **File & Folder Management**:
  - Multi-file sequential upload queue with live progress reporting.
  - Custom folder creation, organization, and cloud metadata sync.
  - Public `Downloads/` directory export via Android `MediaStore`.
  - Room local database persistence for offline caching and fast search/filtering.
  - Multi-criteria sorting (Date, Name, Size) and List/Grid view toggles.
- **In-App Media Experience**:
  - Full-resolution photo viewer with pinch-to-zoom and pan gestures.
  - Built-in video player powered by AndroidX Media3 ExoPlayer.
- **Material 3 Design & Theming**:
  - Modern glassmorphic interface with full support for System Default, Dark Theme, and Light Theme.
  - Edge-to-edge layout with full WindowInsets handling.
- **CI/CD & Security**:
  - Automated build verification and release packaging via GitHub Actions.
  - ProGuard/R8 code optimization and resource shrinking for release APKs.

---

## [1.0.0-alpha01] - 2026-09-28

### Added
- Initial development preview and core TDLib integration.
- Experimental file upload and download flows.
- Basic Room database caching schema.
