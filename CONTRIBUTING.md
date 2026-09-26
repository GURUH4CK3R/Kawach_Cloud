# Contributing to Kawach Cloud

Thank you for your interest in contributing to Kawach Cloud!

## Code of Conduct
We are committed to providing a welcoming, inclusive, and harassment-free environment for all contributors.

## How to Contribute
1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/kawach-cloud.git
   cd kawach-cloud
   ```
3. **Set up developer configuration**:
   - Copy `.env.example` to `.env`:
     ```bash
     cp .env.example .env
     ```
   - Enter your `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` from [https://my.telegram.org](https://my.telegram.org).
4. **Create a topic branch**:
   ```bash
   git checkout -b feature/my-new-feature
   ```
5. **Make your changes**:
   - Follow Kotlin and Jetpack Compose best practices.
   - Keep UI components accessible and aligned with Material 3 design tokens.
   - Verify that all builds pass:
     ```bash
     gradle assembleDebug
     ```
6. **Submit a Pull Request**:
   - Provide a clear summary of your changes.
   - Never commit sensitive secrets, `.env` files, or session databases.

## Reporting Issues
If you encounter a bug or have a feature request, please open an issue on GitHub describing:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Relevant non-sensitive log output
