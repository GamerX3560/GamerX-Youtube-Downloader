# Contributing to GamerX Downloader

Thank you for your interest in contributing! Here's how you can help.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/YOUR_USERNAME/GamerX-Youtube-Downloader.git`
3. **Create a branch**: `git checkout -b feature/your-feature`
4. **Make your changes** and test them
5. **Commit**: `git commit -m "Add your feature"`
6. **Push**: `git push origin feature/your-feature`
7. **Open a Pull Request**

## Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Building
```bash
./gradlew assembleDebug
```

### Project Architecture

The app follows **MVVM + Repository** pattern with **Hilt** for DI:

- `data/` — Database, models, repositories
- `di/` — Dependency injection modules
- `download/` — yt-dlp integration (DownloadWorker, YtDlpManager)
- `ui/` — Compose screens and components

## Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public functions
- Keep composables small and focused

### Commit Messages
- Use present tense: "Add feature" not "Added feature"
- Keep the first line under 72 characters
- Reference issues when applicable: "Fix #123: Download failing on merge"

### Pull Requests
- One feature/fix per PR
- Include a clear description of changes
- Make sure the app builds successfully
- Test on a real device if possible

## Reporting Bugs

Open an issue with:
1. Device model and Android version
2. Steps to reproduce
3. Expected vs actual behavior
4. Logs (if available)

## Feature Requests

Open an issue with the "enhancement" label and describe:
1. What feature you'd like
2. Why it would be useful
3. Any implementation ideas

---

Thank you for contributing! 🎉
