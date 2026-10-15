# Contributing to Jellude

Jellude welcomes focused bug reports and improvements to reliable offline music playback. It is an experimental project with limited maintainer capacity, not a support service. Check existing issues and the [roadmap](docs/ROADMAP.md) before proposing work. Discuss large features in an issue first.

Use the bug/feature forms for public reports. Security vulnerabilities use the private process in [SECURITY.md](SECURITY.md). Never upload credentials, private server addresses, signing keys, personal music, or unredacted logs.

## Build and test

1. Clone the repository and open its root in Android Studio.
2. Use JDK/Gradle JVM 21, Android SDK platform 37, and Build Tools 36.0.0. Let Android Studio create your local SDK configuration.
3. Create a topic branch from `main` and make a focused change.
4. Run the following from the root (Windows PowerShell: replace `./gradlew` with `.\gradlew.bat`):

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

For download, data, playback, navigation, or playlist behavior, run relevant Android tests on a disposable emulator:

```sh
./gradlew :app:connectedDebugAndroidTest
```

**Disconnect personal devices first.** The integration suite installs the app, replaces its test catalogue, and clears its app databases/files. Use a disposable emulator with no personal account or music. Tests use a local mock server and generated WAV files; do not substitute a real server.

CI currently runs core tests, APK compilation, and lint, not emulator tests. Describe device/emulator verification in your PR. For UI changes, include screenshots with test data. Review [architecture](docs/ARCHITECTURE.md) before changing download or synchronization behavior.

## Preserve these behaviors

- Never expose partial files as complete downloads or publish a partial collection membership snapshot.
- Preserve tracks shared between offline selections and the last verified copy during replacement.
- Keep local cleanup separate from server playlist edits. Do not add server file deletion.
- Preserve duplicate playlist entries and stop on conflicts/uncertain mutation outcomes.
- Keep offline mode usable without a server and make failures visible and repairable.
- Add explicit Room migrations for schema changes; do not silently destroy user libraries.

## Pull requests and AI assistance

Explain the user problem, resulting behavior, relevant tests, and remaining limitations. Keep unrelated formatting/refactoring out of a bug fix. Do not merge a dependency update solely because it is newer; verify the Kotlin/Compose/KSP/AGP/Gradle combination together when affected.

The initial application implementation was generated entirely using AI coding tools under human direction. Human-written contributions are welcome. Disclose material AI assistance in your PR and explain how you checked the behavior. Generated code, passing CI, or a model's review does not replace understanding the change. Do not submit code copied from another project without documenting its source and compatible license.

Contributions are provided under GPL-3.0-only as described in [LICENSE](LICENSE), unless an existing third-party file explicitly states otherwise. By submitting, you confirm you have the rights needed to contribute. There is no contributor license agreement or copyright assignment. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [branding](docs/branding/README.md) for dependency and artwork treatment.

Be respectful, discuss behavior and evidence, and avoid personal attacks. Maintainers may close abusive, duplicate, or out-of-scope discussions. Follow GitHub's community guidelines.
