# Release gates

Jellude is an initial development build, not a production reliability claim. Version 0.1.3 uses application ID `dev.jellude` and the approved Jellude artwork.

Owner target: POCO F7 Ultra on Android 16, Jellyfin Server 12.2. See `COMPATIBILITY.md` for version-specific evidence and remaining device checks.

## Implemented foundation

- Native Kotlin/Compose app, encrypted login persistence, namespaced Room catalogue.
- Local browsing/search; original audio streaming and persistent offline assets.
- Track/album/album-artist/playlist selection manifests, snapshot/live policies, shared assets.
- Validated temporary transfers, safe resume/restart, bounded retry, manual verification and repair.
- Metadata and playlist membership refresh, cached lyrics and artwork.
- Media3 background service, browsable Android Auto library and search callbacks.
- Persistent playback queue with view/jump/remove/clear controls, shuffle and off/queue/song repeat feedback, headset/audio-focus handling.
- Existing-playlist add/remove/reorder/rename with persistent offline edit records and conservative conflict handling; online playlist creation. Server file and whole-playlist deletion are disabled and blocked at the request layer.
- Core unit tests and Android emulator integration tests.

## Personal beta gates

1. Test with the owner's server version, permissions, authentication, and proxy base path.
2. Verify representative audio formats, long tracks, multi-disc albums, compilations, and playlists with duplicates.
3. Exercise airplane-mode cold start and playback after at least a day offline.
4. Interrupt collections with network changes, process termination, reboot, storage exhaustion, and server restarts. Verify every expected song is either ready or explicitly unresolved.
5. Test actual server metadata/artwork/lyrics edits and source audio replacements.
6. Verify Android Auto on the owner's phone and car, including voice, offline search, audio focus, and reconnection.
7. Test updates using the same application ID and signing key, preserving the full offline library.

## Further engineering before wider release

- Priority for the owner's Android 16 phone: user-initiated data-transfer scheduling and quota-aware fallbacks for long download sessions.
- Offline creation of playlists; multiple ordered pending edits and conflict-resolution UI.
- Item-ID replacement suggestions and remote deletion/tombstone handling.
- Streaming network constraints distinct from download constraints.
- Explicit source refresh/download-again controls and stronger publication journal/recovery tests.
- Account switching/sign-out controls with explicit local-data retention choices.
- Byte progress, pause/cancel controls, storage estimates and reservations, diagnostic export.
- Artwork refresh/versioning tests and large-library performance profiling.
- Accessibility, localization, UI refinement, and target-SDK/dependency upgrade review.
- Release signing, distribution, and sustained physical-device testing.

USB export is out of scope.
