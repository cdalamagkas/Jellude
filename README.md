# Jellude

Private repository: [cdalamagkas/Jellude](https://github.com/cdalamagkas/Jellude). See [GitHub workflow](docs/GITHUB_WORKFLOW.md) for cloning, branches, checks, and APK artifacts.

A native Jellyfin music player focused on a verifiable offline library. Application ID and code namespace: `dev.jellude`. Android 10 or newer.

![Jellude logo](app/src/main/res/drawable-nodpi/jellude_logo.png)

Jellude 0.1.3 adds download status icons, system-back navigation within collections, a visible playback queue, and repeat/shuffle feedback. It updates Jellude 0.1.2 in place. Jellude uses a separate application ID from the older Jellyfin Offline builds.

Owner target: **POCO F7 Ultra, Android 16, Jellyfin Server 12.2**. Integration tests have passed on Android 15 and Android 16 emulators. See `docs/COMPATIBILITY.md` for the server API review and remaining physical-device checks.

## Run

Open this directory in Android Studio and run the `app` configuration. Enter your Jellyfin server URL (including any reverse-proxy base path), username, and password on the phone. HTTPS is recommended. The app uses your account's download and playlist permissions.

Build from a terminal with **JDK 21**, Android SDK platform 35, and Build Tools 36.0.0:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Packaged builds use the name `Jellude-<version>-debug.apk` in `releases/`. This is a development build signed with a debug key, not a Play Store release.

This workspace also contains ignored project-local tools under `.tools/`, and a machine-local SDK location in `local.properties`. These are not required when opening the source on another machine with Android Studio.

The project uses AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21, and KSP 2.3.12. In Android Studio, keep **Gradle JVM = JVM 21**, then sync the project. See `docs/BUILD_TOOLS_UPGRADE.md` for the migration details.

## Use

1. Connect, then allow the catalogue import to finish. The refresh button repeats metadata and playlist synchronization.
2. Browse Albums, Artists (album artists), Songs, or Playlists. Search works against the local catalogue.
3. Tap download to keep a track or collection offline. Choose a snapshot or synchronized collection. Playlist selections default to synchronized. A download check mark means complete; a downloading symbol means partial, pending, or needing attention. Tap either to manage or repair the download. A failed collection discovery never shows as complete.
4. Downloads shows the exact verified/expected track count, discovery errors, and per-track failures. **Repair** verifies hashes and requeues damaged/missing files. **Refresh tracks** fetches a new collection membership snapshot.
5. The bottom player opens playback controls and lyrics. Timestamped lyrics follow playback and can be tapped to seek. The queue icon or **View queue** shows playback order, current and next tracks, track selection/removal, and **Clear queue**. Clearing playback does not change playlists or downloads and the cleared queue stays cleared after restarting. Repeat cycles **off → queue → song → off**, with an active icon and mode label; shuffle also shows its active state.
6. Offline mode blocks server requests. Downloads default to unmetered networks; streaming is permitted while online. The network setting can be changed in Settings.
7. Playlists supports online creation and queued rename/add/remove/reorder operations for existing playlists. Removing an entry changes playlist membership only. Only one outstanding edit per playlist is accepted in this prototype.

**Stop keeping offline** removes an item's automatic download/repair requirement and leaves its downloaded files on this phone. **Settings → Clean up unused phone downloads** removes only audio copies no longer needed by any offline selection. Tracks shared with another offline album, artist, song, or playlist stay downloaded. App-managed files are removed on uninstall. USB export is intentionally excluded.

Jellude does not support deleting music files or whole playlists from the server. Its API and outbound HTTP policy block generic server item deletion; only playlist-entry removal can use DELETE. Unsupported deletion edits left by earlier versions are blocked before synchronization. All audio cleanup is local to this phone.

## Reliability design

- Room stores catalogue data, selection manifests, local assets, lyrics, playlist entries, and pending mutations.
- A collection's membership is published only after every API page succeeds. A changed total or repeated page causes a visible discovery error.
- Original audio transfers use temporary files. HTTP resume requires a strong ETag, matching byte range, and unchanged source validator. Servers that ignore Range trigger a clean restart.
- Completed transfers must pass byte-length checks when available, SHA-256 calculation, and Android audio-container inspection before publication.
- Atomic file replacement publishes audio; startup reconciliation detects database/filesystem disagreements. A crash can require a fresh download, but does not make a partial file playable.
- A source update keeps the previous verified copy playable while its replacement downloads. Source change detection uses Jellyfin media-source ETags when supplied. A local hash alone does not prove equality to server contents.
- A single track can satisfy multiple selections. Removing a selection does not delete shared audio.
- Background jobs are durable and constrained; transfers have bounded retries and three active transfer slots. Manual repair can retry exhausted jobs.
- Metadata updates do not rewrite embedded audio tags. Lyrics and artwork failures do not invalidate playable music.
- Playback uses a MediaLibraryService for phone controls and Android Auto. Verified files take precedence over streaming. Queue, position, shuffle, and repeat settings are saved per account namespace.
- Playlist edits record a base snapshot including entry IDs and order. Conflicting snapshots stop the mutation. Before sending, a mutation is marked Sending; unknown outcomes require inspection. HTTP automatic connection retries are disabled to prevent implicit mutation replay.
- Tokens are encrypted with an Android Keystore key; passwords are not persisted. Account-specific databases and asset directories use a server/user namespace. Backups and device transfers of app data are excluded.

## Tests

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

The core tests exercise incomplete albums, overlap, duplicate playlist entries, lyric seeking, validated HTTP ranges, ignored ranges, changed validators, interrupted responses, cancellation, and invalid responses.

Android integration tests run against an in-process mock Jellyfin server with generated WAV audio, without personal server credentials. They exercise the real Room database, WorkManager downloads, Compose library/download screens, MediaLibraryService, offline audio playback, duplicate playlist entry removal, conflicts, and uncertain mutation outcomes.

## Prototype limits and remaining validation

The owner reports that the initial APK works on the POCO F7 Ultra with Android 16 and Jellyfin Server 12.2. This report does not replace the individual long-session, corruption, and Android Auto release gates below.

- Real Jellyfin server/version, reverse proxy, FLAC/MP3/AAC/Opus library, and account permission compatibility must be checked with your installation. No personal server was accessed during development.
- Android Auto browsing is implemented and its service can be exercised by tests; a car or Desktop Head Unit must validate the full driving interface, voice integration, and reconnection. A sideloaded debug build may require Android Auto's developer option for unknown sources.
- WorkManager currently owns long transfers as foreground work. Android quotas and manufacturer battery restrictions still apply. User-initiated data-transfer jobs are a follow-up for large, immediate collections on newer Android versions. Force-stopped apps resume maintenance after reopening.
- Only one outstanding edit per playlist is allowed. Conflicts and unknown outcomes are resolved by refreshing, inspecting, discarding the local edit, and reapplying if appropriate. There is no automatic three-way playlist merge. Offline creation of new playlists is not yet implemented.
- Concurrent external playlist edits can occur between comparison and mutation; Jellyfin does not provide an assumed atomic compare-and-swap here. Incremental operations reduce the affected scope.
- The active account can be reauthenticated. Switching to a different account within the same process is deliberately rejected; a dedicated account management screen remains to be implemented.
- Original-format playback depends on device codec support. No transcoded downloads or conversion are implemented.
- No automatic source equality proof is possible when Jellyfin supplies no reliable source validator. Manual server-side file replacement compatibility needs testing.
- Large-library paging, filesystem/database crash recovery, low-space behavior, reboot/force-stop behavior, and Android 16 scheduling need additional physical-device soak testing.
- The first schema is exported. Future releases must add explicit migrations; destructive migration fallback is not enabled.

See `docs/ROADMAP.md` for the remaining release gates.
