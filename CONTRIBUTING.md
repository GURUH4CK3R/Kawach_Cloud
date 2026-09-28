# Contributing to Kawach Cloud

Thank you for your interest in contributing to **Kawach Cloud**! 

Kawach Cloud is an open-source Android application utilizing Telegram's infrastructure as personal cloud storage. We welcome contributions from developers, designers, and testers.

**Repository:** [https://github.com/GURUH4CK3R/Kawach_Cloud](https://github.com/GURUH4CK3R/Kawach_Cloud)  
**Maintainer:** Aravind(guru) (`darkwebaccess404@gmail.com`)

---

## Code of Conduct

We are committed to providing a welcoming, inclusive, safe, and respectful environment for all contributors. Please communicate constructively and respectfully in all issues, discussions, and pull requests.

---

## Getting Started

### 1. Prerequisites
- **Android Studio**: Android Studio Ladybug (2024.2.1+) or newer.
- **Java Development Kit**: JDK 17 or JDK 21.
- **Android SDK**: Platform SDK 36 (Minimum SDK: 26).
- **Git**: Installed and configured locally.

### 2. Fork and Clone
Fork the repository on GitHub and clone your fork locally:
```bash
git clone https://github.com/<your-username>/Kawach_Cloud.git
cd Kawach_Cloud
```

### 3. Configure Local Credentials
Copy the example environment file:
```bash
cp .env.example .env
```
Optionally, provide your own Telegram Developer API credentials from [my.telegram.org](https://my.telegram.org):
```properties
TELEGRAM_API_ID=your_api_id
TELEGRAM_API_HASH=your_api_hash
```

---

## Development Workflow

### 1. Branching Strategy
Create a dedicated feature branch from `main`:
```bash
git checkout -b feature/your-feature-name
```
For bug fixes:
```bash
git checkout -b fix/issue-description
```

### 2. Code Standards & Architecture Guidelines
- **Language**: Use Kotlin exclusively (version 2.2.10+).
- **UI Framework**: Use Jetpack Compose and Material 3 design tokens.
  - Adhere to the established color scheme: Primary (`#60A5FA` / `#2563EB`), Accent (`#FF9F43`), Surface (`#101D30` dark / `#FFFFFF` light).
  - Use `MaterialTheme.colorScheme` instead of hardcoded hex values to maintain seamless Dark and Light theme compatibility.
- **State Management**: Use `ViewModel`, Kotlin Coroutines, and `StateFlow` / `SharedFlow`.
- **Database & Persistence**: Room Database (`FileDao`, `FolderDao`) for local indexing.
- **TDLib Integration**: Follow MTProto client best practices through `TelegramClientManager`. Never perform synchronous network or file I/O on the main thread.
- **Storage Rules**: Only index files tagged with Kawach Cloud signatures (`[KawachCloud]`). Do not scan or modify unrelated Telegram messages.

### 3. Testing and Building
Before submitting your changes, verify that the project builds cleanly and all tests pass:
```bash
# Run local unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## Security & Confidentiality Rules

> [!CAUTION]
> **NEVER commit sensitive credentials, tokens, or session files:**
> - Do **not** commit `.env` or files containing personal API IDs or API hashes.
> - Do **not** commit `tdlib_db/`, session keys, or keystores (`*.jks`, `*.keystore`).
> - Do **not** commit real user phone numbers or authentication codes in tests.

---

## Submitting a Pull Request

1. **Commit Messages**: Write clear, descriptive commit messages describing the *why* and *what* of the change.
2. **Push to Your Fork**:
   ```bash
   git push origin feature/your-feature-name
   ```
3. **Open a PR**: Submit a Pull Request against the `main` branch of [GURUH4CK3R/Kawach_Cloud](https://github.com/GURUH4CK3R/Kawach_Cloud).
4. **PR Description**: Include:
   - Summary of changes and motivation.
   - Screenshots or video recordings for UI changes.
   - Verification that unit tests and `./gradlew assembleDebug` passed.
   - Closes #issue_number if resolving an open issue.

---

## Reporting Issues & Feature Suggestions

Please use GitHub Issues to report bugs or propose enhancements:
- **Bug Reports**: Include device model, Android version, reproduction steps, expected vs. actual behavior, and relevant logs.
- **Feature Requests**: Describe the problem and proposed user experience clearly.

For sensitive security reports, follow our [Security Policy](SECURITY.md).
