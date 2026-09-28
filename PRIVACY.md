# Privacy Policy for Kawach Cloud

**Last Updated:** September 28, 2026

**Kawach Cloud** ("the Application") is an open-source Android client designed to provide personal cloud storage powered by Telegram infrastructure.

**Developer:** Aravind(guru)  
**Email:** `darkwebaccess404@gmail.com`  
**GitHub Repository:** [https://github.com/GURUH4CK3R/Kawach_Cloud](https://github.com/GURUH4CK3R/Kawach_Cloud)

---

## 1. Core Privacy Philosophy
Kawach Cloud operates under a direct client-to-cloud model:
- **No Developer Servers**: There are no intermediary or developer-hosted servers. Your files, credentials, and metadata are **never** routed through any third-party or developer-controlled proxy.
- **Direct Telegram Communication**: The application communicates directly from your device to official Telegram datacenters using Telegram's MTProto encryption protocol via TDLib (Telegram Database Library).
- **Personal Storage**: Files uploaded via Kawach Cloud are stored exclusively in your own Telegram **"Saved Messages"** chat (`peerSelf`).

---

## 2. Information Handled by the Application

### A. Telegram Authentication Credentials
- **Phone Number**: When logging in, your phone number is transmitted directly to Telegram servers to request a verification code (OTP).
- **One-Time Password (OTP)**: The login code received in your Telegram application is held in transient memory solely to complete the authentication handshake with Telegram. It is never logged or written to permanent storage.
- **Two-Step Verification Password (2FA)**: If enabled on your Telegram account, your cloud password is used strictly during authentication to compute the cryptographic challenge for Telegram servers. It is never saved locally or transmitted anywhere else.
- **TDLib Session Database**: Once authenticated, Telegram session keys and encryption tokens are maintained locally in your device's private app sandbox (`tdlib_db/`).

### B. User Files and Metadata
- **File Payloads**: Files selected for upload are sent directly from your device to your Telegram Saved Messages.
- **Metadata Tagging**: Files uploaded through Kawach Cloud receive a lightweight signature in the message caption:
  ```text
  [KawachCloud] folder:<folderId> | id:<fileUuid> | #KawachCloud
  ```
  This signature allows Kawach Cloud to recognize its own files and avoid scanning or displaying unrelated personal messages from your Telegram history.
- **Local Index Cache**: For fast offline browsing and responsive UI, a local cache of your Kawach-managed files and folders is stored in an encrypted/private Room database on your device.

---

## 3. Analytics, Tracking & Advertising
- **Zero Third-Party Trackers**: Kawach Cloud contains **no third-party tracking SDKs**, analytics packages, or behavioral monitoring tools.
- **Zero Advertising**: There are no advertisements or marketing trackers embedded in the application.
- **No Telemetry**: No crash logs, device telemetry, or usage metrics are collected by the developer.

---

## 4. User Controls and Data Retention

### A. File Deletion
- When you delete a file in Kawach Cloud, the application sends a `DeleteMessages` command to Telegram servers to permanently remove the message from your Saved Messages.
- The corresponding entry in the local device database is removed immediately.

### B. Account Disconnection / Logout
- Tapping **Disconnect Account** in Settings terminates your active TDLib session, clears local authentication tokens, and resets the local database index.

### C. Public Downloads
- Files downloaded via the "Download" option are saved directly into your device's public Android `Downloads/` directory via `MediaStore`. You maintain full control over these files and can delete, move, or share them through any system file manager.

---

## 5. Third-Party Infrastructure (Telegram)
Kawach Cloud relies on Telegram's public API and network infrastructure. By using your Telegram account with Kawach Cloud, your data storage and network transmission are subject to [Telegram's Terms of Service](https://telegram.org/tos) and [Telegram's Privacy Policy](https://telegram.org/privacy).

---

## 6. Security Limitations & Transparency
- **Standard Telegram Cloud Storage**: Files stored in Telegram Saved Messages are encrypted in transit and at rest on Telegram's distributed cloud servers according to Telegram's cloud chat security model. They are **not** client-side end-to-end encrypted (Secret Chat encryption is not supported for cloud file storage).
- **Future Roadmap**: Client-side end-to-end encryption prior to upload is planned for future releases.

---

## 7. Telegram Disclaimer
Kawach Cloud is an independent, open-source project and is not affiliated with, endorsed by, sponsored by, or officially connected to Telegram FZ-LLC or Telegram Messenger Inc.

---

## 8. Contact & Inquiries
For privacy questions, feedback, or concerns, please contact:
- **Developer:** Aravind(guru)
- **Email:** `darkwebaccess404@gmail.com`
- **GitHub:** [https://github.com/GURUH4CK3R/Kawach_Cloud](https://github.com/GURUH4CK3R/Kawach_Cloud)
