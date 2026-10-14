# Owner compatibility target

Current documented development version: Jellude 0.1.3, application ID `dev.jellude`. The previous Jellyfin Offline builds used a separate installation identity; their app data is not migrated.

Reported on 14 October 2026:

- Phone: POCO F7 Ultra.
- Operating system: Android 16 / API 36.
- Server: Jellyfin Server 12.2.

## Server API review

Jellyfin 12.2 is an official release: https://github.com/jellyfin/jellyfin/releases/tag/v12.2

The v12.2 server source retains the original download, lyrics, collection query, and playlist entry operations used by this app:

- `Items/{id}/Download`, subject to account download authorization.
- `Audio/{id}/Lyrics`.
- `Users/{userId}/Items`, retained as a legacy compatibility route.
- Playlist entry retrieval with `PlaylistItemId`, and incremental add/remove/move operations.
- Playlist creation and update operations.

Reviewed sources:

- https://github.com/jellyfin/jellyfin/blob/v12.2/Jellyfin.Api/Controllers/LibraryController.cs
- https://github.com/jellyfin/jellyfin/blob/v12.2/Jellyfin.Api/Controllers/LyricsController.cs
- https://github.com/jellyfin/jellyfin/blob/v12.2/Jellyfin.Api/Controllers/ItemsController.cs
- https://github.com/jellyfin/jellyfin/blob/v12.2/Jellyfin.Api/Controllers/PlaylistsController.cs

This is a source-level contract review, not an integration test against the owner's server. Permissions, proxy behavior, library structure, and available lyrics still require testing on that installation.

## Android 16 priorities

The original four Android integration tests passed on Android 16 / API 36: complete album download and corrupt-file repair, offline playback and media browsing, playlist conflict recovery, and prevention of replay after a lost mutation response. Version 0.1.3 expanded this to seven passing Android 16 tests, adding collection navigation/download indicators, queue/repeat controls, and server-deletion/local-cleanup safeguards. The owner reports that 0.1.3 works on the target phone. The app still targets API 35; these results establish basic operation on API 36, not completion of a target-SDK upgrade.

Android 16 applies job quotas to long-running WorkManager jobs even when they use foreground services. The prototype uses WorkManager, so successful short emulator downloads do not establish long-session reliability. User-initiated data-transfer scheduling is the highest-priority scheduling follow-up before treating large album/artist downloads as dependable on the owner's phone.

Reference: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

## POCO physical-device checks

An emulator cannot reproduce this phone's firmware, power management, audio hardware, or Android Auto connection. The remaining owner-device checks are:

1. Install the APK and import the Jellyfin 12.2 catalogue.
2. Download a representative large album/playlist with the screen off, then verify the expected track count.
3. Interrupt Wi-Fi, reconnect, and run Repair. Confirm every missing track is retried or shows an explicit reason.
4. Restart the app and phone, enable airplane mode, and test browsing, playback, lyrics, and queue restoration.
5. Change metadata and playlist entries through another Jellyfin client and test synchronization.
6. Test Bluetooth and actual Android Auto playback, offline browsing, voice requests, and reconnection.

Do not mark these physical-device checks as passed based on emulator results.
