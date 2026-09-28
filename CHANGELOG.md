# Changelog

All notable changes to **Kawach Cloud** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-alpha01] - 2026-09-28

### Added
- **Telegram MTProto TDLib Backend**: Direct client-side integration using official TDLib (`io.github.tdlib-android:core:0.1.1` and `ktx:0.1.1`).
- **Interactive Authentication**:
  - International phone number entry with preloaded country code selector (defaulting to India +91).
  - Telegram direct OTP validation via official Telegram client message.
  - Two-Step Verification (Cloud Password / 2FA) challenge flow.
  - Persistent session management with instant logout capability.
- **Selective Kawach Cloud Storage**:
  - File upload streaming directly to Telegram Saved Messages (`peerSelf`).
  - Strict Kawach Cloud signature tagging (`[KawachCloud] folder:<id> | id:<uuid> | #KawachCloud`).
  - Only Kawach-uploaded files are indexed; unrelated personal chats, notes, and photos in Saved Messages are untouched.
- **Folder Management & Cloud Synchronization**:
  - User folder creation and deletion.
  - Multi-user isolation with compound Room database primary keys `(id, userId)`.
  - Cloud-backed folder metadata preservation via Telegram text records (`[KawachCloud:Folder]`).
- **File Transfer & Public Downloads**:
  - Live byte-level upload and download progress reporting via `TdApi.UpdateFile`.
  - Android Public `Downloads/` directory integration via `MediaStore.Downloads` (API 29+) and legacy storage support.
  - Collision-safe duplicate naming (`file (1).ext`) without overwriting existing files.
  - Native file opening (`ACTION_VIEW`) and sharing (`ACTION_SEND`) via Android `FileProvider`.
- **Search, Filtering & Organization**:
  - Dynamic file categorization (Images, Videos, Audio, Documents, Archives).
  - Instant text search across stored file names.
  - Multiple sorting modes: Date (Newest/Oldest), Name (A-Z/Z-A), and Size (Largest/Smallest).
  - Responsive List View and Grid View layouts.
- **Material 3 & Dual Theme**:
  - Complete Material 3 color system with functional System Default, Light Theme (`#F4F9FF`), and Dark Theme (`#07111F`).
  - Dynamic `GlassCard` container styling with responsive contrast on both light and dark backgrounds.
  - Edge-to-edge support with safe window inset padding.
- **Continuous Integration**:
  - Automated GitHub Actions workflow (`.github/workflows/android.yml`) building the `kawach-cloud-debug` APK artifact on push and pull request.

### Fixed
- Fixed light theme contrast issue where cards retained dark backgrounds regardless of theme setting.
- Fixed folder disappearance after creation by binding reactive Room database observation with active user ID transitions.
- Fixed file download handling to resolve live TDLib message instances rather than stale session cache identifiers.
- Fixed TDLib send failure error decoding in `UpdateMessageSendFailed`.

---

## [Unreleased]

### In Progress 🚧
- Background transfer service for resilient large-file transfers when the app is minimized.
- Thumbnail generation and disk caching for video files.
- Granular sync status indicators with per-folder synchronization counts.

### Planned 📋
- Client-side end-to-end file encryption before streaming to Telegram Saved Messages.
- Multi-file batch upload and download queue management.
- Offline-first cache sync with manual refresh pull-down.
- Biometric authentication lock (Fingerprint / Face Unlock).
