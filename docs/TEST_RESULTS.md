# Validation — 14 October 2026

Build: 0.1.0 development APK, minimum Android API 29, target/compile API 35.

| Check | Result |
|---|---|
| Kotlin/JVM core tests | 17 passed; 0 failures |
| Android integration tests | 4 passed; 0 failures |
| Debug APK compilation | Passed |
| Android lint | Passed: 0 errors, 21 warnings |
| Packaged APK install and cold launch | Passed on Android 15 emulator |
| Login screen visual inspection | Passed; screenshot in `screenshots/login.png` |

Device: project-local Pixel 6 emulator, Android 15 / API 35, x86_64, Windows Hypervisor Platform acceleration.

Follow-up for the owner's reported POCO F7 Ultra / Android 16: the same four integration tests also passed on a separate Android 16 / API 36 x86_64 emulator, with zero failures. No application code change was needed for this run. This is emulator evidence, not a physical POCO test or a long-running job-quota test.

The Android tests used a mock Jellyfin server and generated two-second WAV files. They validated:

1. All twelve expected album tracks become verified downloads; changing one downloaded file's contents is detected by deep repair and only that track is downloaded again. The Downloads screen reports 12 / 12.
2. A downloaded song reaches active playback with server access disabled, and a MediaBrowser sees twelve downloaded tracks through the service used by Android Auto.
3. An offline queued removal targets one playlist entry while preserving a duplicate occurrence. A subsequent conflicting remote order prevents the queued mutation from being sent.
4. A server applies a playlist addition but drops the response. The client leaves the edit needing review, and synchronization does not send it again.

The core tests separately cover strong/weak validators, ignored and invalid byte ranges, changed sources, incomplete ranges, interrupted bodies, invalid HTML, empty files, cancellation, permission failures, collection overlap, and lyric seek timing.

Lint warnings concern pinned dependency versions, the intentionally exported media browser service, backup configuration compatibility guidance, and optional KTX preference-edit style. They are not suppressed globally.

Not validated: the owner's Jellyfin installation, actual car/Android Auto UI or voice experience, physical-device battery restrictions, large real music libraries, extended offline use, and reboot/low-space fault campaigns. These remain release gates in `ROADMAP.md`.

## Version 0.1.1 — coordinated tools upgrade

The owner subsequently reported that the original APK works on the physical POCO F7 Ultra and authorized the build-tools upgrade.

AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21, KSP 2.3.12, Build Tools 36.0.0, JVM 21:

| Check | Result |
|---|---|
| Core tests after compiler upgrade | 17 passed; 0 failures |
| Android 16 emulator integration tests | 4 passed; 0 failures |
| Debug APK and regenerated wrapper | Passed |
| Android lint | Passed: 0 errors, 26 warnings |
| Room schema comparison with backup | Identical |
| APK signing certificate comparison | Identical |
| In-place update from 0.1.0 on Android 16 | Passed; app-data marker retained |

See `BUILD_TOOLS_UPGRADE.md` for the exact scope and evidence. The updated APK still needs an owner-device smoke test; the original Android 15 emulator results above apply to 0.1.0.

## Jellude 0.1.2 — name, artwork, and new identity

The owner also confirmed that 0.1.1 works, approved the Jellude name/logo, and requested that the installation and code identifiers change without retaining previous data.

- Android application ID, namespace, and Kotlin packages: `dev.jellude`.
- Application class: `JelludeApp`; token encryption key alias: `jellude-session`.
- Launcher and app label, login logo, library header, notification icons, Android Auto library root, Jellyfin client header, Gradle project name, and release filenames use Jellude.
- Full and icon-only PNG resources match the approved source artwork byte-for-byte.
- Android 16 integration assertions verify the `dev.jellude` package, Jellude app label, and Jellude media-library root alongside the existing download/repair/playback/playlist checks.
- Visual inspection of the login screen and Android App info confirms logo rendering and adaptive-icon masking. Screenshots: `screenshots/jellude-login.png` and `screenshots/jellude-app-info.png`.

Jellude installs independently of the old application. No data migration is attempted. Earlier reports and old release files retain their original names as historical evidence.

Final 0.1.2 checks: all 17 core tests and all 4 Android 16 integration tests passed; debug APK compilation and Android lint passed. The final source/resource configuration includes the full-color and monochrome adaptive-icon layers.
