# Jellude

<img src="docs/branding/jellude-social-preview.png" width="360" alt="Jellude logo on a white background">

An Android music player for your Jellyfin server, focused on a verifiable offline library: download collections, see what is missing, and repair incomplete or damaged phone copies.

**Experimental · Source-only distribution · Android 10+**

Jellude is an independent project, unaffiliated with Jellyfin. It is under active development and has not been independently audited. Public availability is not a production-readiness claim. Official signed APK releases are planned for later; [build from source](#build-from-source) for now.

## AI development disclosure

Jellude's own application code was written entirely using AI coding tools, under human direction. The project owner defines the requirements, tests the app, and approves changes and releases. Third-party libraries retain their respective authorship and licenses. Human-written contributions are welcome.

AI-generated code can contain bugs, security issues, and incorrect assumptions. Passing tests and successful personal use do not establish production readiness or constitute an independent audit. Review the known limitations and report problems through GitHub Issues. Never include passwords, access tokens, or private server details in public reports.

## Features

- Stream from Jellyfin or play verified downloads while offline.
- Download songs, albums, album artists, and playlists; keep a snapshot or synchronize collection membership.
- See partial/complete download states and expected/verified track counts; verify and repair missing or corrupt downloads.
- Synchronize server metadata, artwork, and lyrics without unnecessarily replacing audio.
- Read lyrics, including timestamped lyrics when supplied by the server.
- View the playing queue, see the next track, jump/remove/clear tracks, shuffle, or repeat the queue or one song.
- Create server playlists and queue entry additions/removals/reordering or renaming, with conflict/uncertain-outcome handling.
- Browse the media library through Android Auto. Full car/head-unit validation remains outstanding.

Jellude cannot delete music files from your Jellyfin server. It does not offer whole-playlist deletion. Phone-download cleanup is local; removing a playlist entry changes membership only. USB export is outside the project's scope.

<p>
  <img src="docs/screenshots/jellude-013-now-playing.png" width="260" alt="Now playing with lyrics and repeat-song feedback, using generated test music">
  <img src="docs/screenshots/jellude-013-playing-queue.png" width="260" alt="Playing queue showing current and next tracks, remove actions and Clear queue, using generated test music">
</p>

## Build from source

1. Clone [cdalamagkas/Jellude](https://github.com/cdalamagkas/Jellude) and open the repository root in Android Studio.
2. Use **Gradle JVM/JDK 21**, Android SDK **platform 35**, and **Build Tools 36.0.0**. Let Android Studio create the machine-local SDK configuration.
3. Sync Gradle and run the `app` configuration, or build from a terminal:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

On Windows PowerShell use `.\gradlew.bat` instead of `./gradlew`. The development APK is written to `app/build/outputs/apk/debug/app-debug.apk`; install it on your phone yourself. It is signed with your machine's debug key and may not update a build signed on another computer. Debug builds and CI reports are development tools, not official production releases.

The project uses AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21 and KSP 2.3.12. Compile/target SDK is 35; minimum SDK is 29. The application ID is `dev.jellude`.

## Connect and keep music offline

1. Enter your Jellyfin URL (including any reverse-proxy base path), username and password. Prefer HTTPS and an account with only the download/playlist permissions you need. Wait for catalogue synchronization.
2. Browse or search Albums, Artists (album artists), Songs, or Playlists. Tap a download icon and choose a snapshot or synchronized collection. Playlists default to synchronized membership.
3. A downloading symbol means partial, pending, or needing attention; a download check mark means complete according to the local catalogue/verified asset records. Tap the action or open Downloads to inspect it. A failed discovery is never shown as complete.
4. In Downloads, **Verify & repair** checks phone files and requeues missing or damaged tracks. **Refresh tracks** updates collection membership. Metadata/playlist synchronization is also available in Settings.
5. Enable **Offline mode** to block the app's server requests. Downloads default to unmetered networks. Original audio playback depends on your device's codec support.

**Stop keeping offline** stops automatic retention/repair for that selection but leaves existing phone copies visible in Downloads as a local, unsynchronized copy. Use **Keep offline again** to resume retention. **Settings → Clean up unused phone downloads** removes only audio no remaining offline selection needs. Tracks shared by another downloaded song/album/artist/playlist are preserved. Uninstalling or clearing the app's storage removes app-managed downloads. Offline browsing shows downloaded tracks and collections containing downloaded tracks; unavailable tracks are hidden inside collections too.

Tap the bottom player's artwork or title to expand it. Both player sizes have a draggable seek bar. The expanded player shows cover art, elapsed/total/remaining time, and encoding details when supplied by Jellyfin. Its lyrics button lights up when lyrics are available and switches the cover to lyrics; timestamped lyrics follow playback. The queue icon opens the playing queue. Collection pages show cover art, and multi-disc albums have disc headings. Repeat cycles **off → queue → song → off**. Clearing the queue does not remove playlists or downloads and stays cleared after the playback service restarts.

## Compatibility and current limits

The owner reports successful use of development version 0.1.3 on a **POCO F7 Ultra with Android 16 and Jellyfin Server 12.2**. Automated mock-server integration tests have run on Android 15 and 16 emulators; the latest 0.1.3 regression run passed 19 core tests and 7 Android 16 integration tests. These results do not establish compatibility with every server, phone, format, or proxy. See [compatibility evidence](docs/COMPATIBILITY.md) and [test results](docs/TEST_RESULTS.md).

- Long background downloads still depend on Android job quotas and manufacturer battery restrictions. Large-library, reboot, low-space and extended-offline reliability need more physical-device testing.
- Real Android Auto driving UI, voice control and reconnection need car/head-unit validation.
- Original-format downloads only; no transcoded downloads or conversion.
- One outstanding edit per playlist; no automatic three-way merge or offline playlist creation. Concurrent server edits can still race a mutation.
- Account switching/sign-out with local-retention controls is unfinished. No broad multi-account support is claimed.
- Room schema updates use explicit migrations to preserve existing library and download records.

See the [roadmap](docs/ROADMAP.md), [architecture](docs/ARCHITECTURE.md), and [release policy](docs/RELEASING.md).

## Contributing, privacy and security

- [Contributing](CONTRIBUTING.md): setup, tests, PR expectations and AI-assistance disclosure.
- [Report a bug or request a feature](https://github.com/cdalamagkas/Jellude/issues/new/choose). Use test data and redact sensitive information.
- [Security policy](SECURITY.md): report vulnerabilities privately; do not disclose exploits in public issues.
- [Privacy](PRIVACY.md): connections, credentials, local storage, permissions and cleanup.
- [Changelog](CHANGELOG.md) and [GitHub workflow](docs/GITHUB_WORKFLOW.md).

## License and attribution

Jellude's own code and artwork are offered under **GNU GPL version 3 only (`GPL-3.0-only`)**, to the extent licensable rights apply. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Third-party components retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md). Separate [branding guidance](docs/branding/README.md) requires modified distributions to use a distinct identity and avoid implying maintainer endorsement, without restricting GPL copyright permissions.
