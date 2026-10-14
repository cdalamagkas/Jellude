# Build tools upgrade — 14 October 2026

The owner confirmed that the initial APK works on the POCO F7 Ultra, and requested a coordinated build-tools upgrade.

| Component | Previous | Upgraded |
|---|---|---|
| Android Gradle Plugin | 8.9.2 | 9.3.1 |
| Gradle wrapper | 8.13 | 9.5.0 |
| Kotlin / Compose compiler plugin | 2.1.20 | 2.4.21 |
| KSP | 2.1.20-1.0.32 | 2.3.12 |
| SDK Build Tools | 35.0.0 | 36.0.0 (AGP default) |
| Gradle JVM | Previously tested with 23; owner selected 21 | 21 |

AGP 9.3.1 is within Kotlin 2.4.21's documented fully supported AGP range. Android Studio 2026.2.1 is installed and supports this AGP generation. A newer AGP release can still appear in IDE recommendations; it does not need to be applied automatically.

The Android module now uses AGP's built-in Kotlin support. The obsolete Kotlin Android plugin and `android.kotlinOptions` configuration were removed. The plain JVM core module retains the Kotlin JVM plugin. Java bytecode targeting remains at 17.

This update is packaged as app version 0.1.1 / versionCode 2. The application ID, minimum/target/compile SDK levels, database version, and application-library versions are retained. The debug certificate must match 0.1.0 for an in-place APK update.

The original source is preserved in `releases/JellyfinOffline-before-toolchain-upgrade.zip`; the original 0.1.0 APK remains in `releases`. Project-local tools, signing keys, IDE files, build outputs, and machine-specific SDK settings are excluded from the source backup.

## Android Studio

Keep Gradle JVM set to **JVM 21**. Run **Sync Project with Gradle Files** to load the new wrapper and plugins. Keep using the project's configured SDK. The wrapper downloads its pinned Gradle version and verifies the published SHA-256 checksum.

No AGP Upgrade Assistant action is needed after this migration.

## References

- AGP 9.3 compatibility: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Built-in Kotlin migration: https://developer.android.com/build/migrate-to-built-in-kotlin
- Kotlin compatibility matrix: https://kotlinlang.org/docs/gradle-configure-project.html
- KSP release: https://github.com/google/ksp/releases/tag/2.3.12

## Validation

- Debug APK compilation passed using JVM 21.
- The regenerated Gradle wrapper downloaded and verified the Gradle 9.5.0 distribution.
- All 17 core tests passed with Kotlin 2.4.21.
- All 4 Android integration tests passed on Android 16 / API 36 using the upgraded build.
- Android lint passed with 0 errors and 26 warnings. Warnings concern retained dependency/API versions, optional Kotlin preference-edit style, and existing media-service/backup guidance. No global warning suppressions were added.
- The exported Room schema is byte-for-byte unchanged: database version 1, identity hash `ac6a66bb1d7ea999999adee172a2dd83`.
- Old and new APK signing certificate SHA-256: `2bdb2c0bf63253ec7516ea6a6d01fc9d5e1847ec10b69fd8b5c3a3550f84e68a`.
- An Android 16 emulator accepted an in-place update from the original 0.1.0 APK to 0.1.1, reported versionCode 2, and retained a file placed in the app's private storage before the update.

The updated app has not yet been tested on the owner's physical phone. Install 0.1.1 over 0.1.0 without uninstalling; uninstalling removes app-managed data.
