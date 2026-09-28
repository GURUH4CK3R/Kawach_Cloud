# Security Policy

The security and privacy of **Kawach Cloud** users are top priorities. As an open-source project interacting directly with Telegram's MTProto API, we adhere to strict security best practices and responsible disclosure guidelines.

---

## Supported Versions

| Version | Release Status | Supported |
| :--- | :--- | :--- |
| `1.0.0-alpha01` (Build 1) | Alpha | :white_check_mark: |
| `< 1.0.0` | Pre-alpha | :x: |

---

## Reporting a Security Vulnerability

If you identify a security issue, vulnerability, or sensitive credential exposure in Kawach Cloud, please **do NOT report it in public GitHub issues or discussions**.

Instead, report it responsibly via private email:

- **Primary Contact:** Aravind(guru)
- **Security Email:** `darkwebaccess404@gmail.com`
- **Subject Line:** `[SECURITY] Kawach Cloud Vulnerability Report`

### What to Include in Your Report
1. Detailed description of the vulnerability.
2. Step-by-step reproduction instructions or a minimal Proof of Concept (PoC).
3. Potential severity and impact assessment.
4. Suggested remediation or patch (if available).

### Response Timeline
- **Initial Acknowledgment:** Within **48 hours**.
- **Assessment & Triage:** Within **5 business days**.
- **Coordinated Disclosure:** We work closely with reporters to test fixes and prepare security advisories before public release.

---

## Technical Security Architecture & Assurances

### 1. Direct Client-to-Telegram Communication
- Kawach Cloud connects directly to official Telegram datacenters using **TDLib (Telegram Database Library)** and the MTProto protocol over TLS.
- There are **no intermediary proxy servers**, developer-controlled relay nodes, or external storage buckets.

### 2. Ephemeral Authentication Data
- Telegram account passwords (2FA) and One-Time Passwords (OTP) reside solely in transient RAM during the authentication handshake.
- Neither passwords nor OTPs are written to disk, shared preferences, SQLite/Room, or Android system logs.

### 3. Local Data Isolation
- Local metadata and TDLib session states are saved strictly in the Android application's protected private directory (`context.filesDir` / `context.getDatabasePath`), protected by Linux user-level file permissions on Android.
- No world-readable storage permissions are requested.

### 4. Honest Cryptographic Scope
- **Storage Model:** Files in Telegram Saved Messages are encrypted in transit and stored encrypted on Telegram's cloud storage infrastructure under standard Telegram cloud chat terms.
- **Scope Limitation:** Stored files are **not** end-to-end encrypted via Telegram Secret Chats. Telegram datacenters hold keys necessary to deliver cloud messages across devices. Client-side envelope encryption is tracked as a planned improvement.

---

## Contributor Security Guidelines

When submitting code or forks:
- Never commit `.env` files, production API IDs, API hashes, or test phone numbers.
- Never commit `debug.keystore`, release signing keys, or `*.jks` files.
- Always use Android `FileProvider` with restrictive `content://` URIs instead of exposing raw `file://` paths.
