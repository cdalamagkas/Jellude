# Changelog

Dates below record development milestones. These entries are not binary-release announcements; the initial public launch distributes source only. No maintainer-signed production APK is available yet.

## Unreleased

- Optional direct ListenBrainz submission with encrypted token setup, durable offline listen queue, retry/error controls, and custom-recording metadata without inferred MusicBrainz IDs. Tracking excludes pauses, buffering, and seek jumps; Offline mode blocks uploads.
- Offline library and Android Auto browsing hide unavailable music and empty collections.
- Stopped offline selections remain visible as local, unsynchronized copies until cleanup removes their files.
- Expanded artwork player with elapsed/total/remaining time, encoding details, compact queue/lyrics buttons, and scrolling timed lyrics; artwork and seeking in the mini player.
- Collection cover headers and disc sections for multi-disc albums.
- Room v1 → v2 migration preserves existing records while adding retention state and audio format metadata.
- Public-facing documentation, AI development disclosure, GPLv3 licensing, privacy/security policies, contribution and issue/PR templates.
- Dependency inventory and attribution, branding guidance, release/publication checklists.
- GitHub Actions pinned to commit SHAs; checkout credentials are not persisted. Dependabot configuration added.
- CI builds and checks the app but no longer uploads APKs for the source-only launch.

## 0.1.3 — 2026-10-14

- Distinct absent, partial/pending, and complete offline-download indicators.
- System Back returns from collection detail screens to their lists.
- Playing queue with current/next tracks, jump/remove/clear controls, and persistent clearing.
- Visible off/queue/song repeat modes and shuffle state.
- Clearer offline retention and phone-only cleanup wording; shared downloads are preserved.
- Whole-playlist deletion removed; unsupported server mutations and legacy queued deletion edits blocked.
- Verified with 19 core tests, 7 Android 16 emulator integration tests, compilation, and lint. Owner reports the development APK works on the target phone.

## 0.1.2 — 2026-10-14

- Jellude name, approved artwork, and `dev.jellude` installation/code identity.
- Installs separately from the earlier Jellyfin Offline prototype; old app data is not migrated.

## 0.1.1 — 2026-10-14

- Coordinated update to AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21, KSP 2.3.12, and JDK 21.

## 0.1.0 — 2026-10-14

- Initial prototype: Jellyfin catalogue, streaming, validated offline downloads and repair, metadata sync, lyrics, playback service/Android Auto browsing, and server-side playlist editing.
