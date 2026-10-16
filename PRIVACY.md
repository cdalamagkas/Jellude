# Privacy

Applies to Jellude 0.1.3 and the source in this repository. Updated 16 October 2026.

## Connections and data sent

Jellude connects to the Jellyfin server address you enter. Signing in sends your username and password to that server. Later requests use its access token and request catalogue information, audio, artwork, lyrics, or playlists. The server receives normal connection information such as your IP address, an app/device identifier, and the app version, and may record requests under its own policies.

Playlist creation, renaming, adding/removing entries, and reordering send changes to your server when online. Offline edits are saved locally for later synchronization. Removing a playlist entry does not delete its audio file. Jellude blocks generic server item/file deletion and does not offer whole-playlist deletion.

ListenBrainz submission is optional and off by default. Connecting validates your user token with `api.listenbrainz.org` over HTTPS and enables submission. Qualified listens send artist, track title, album if available, playback start time, track duration, and the Jellude client name/version. ListenBrainz also receives your IP address and token for authentication. No audio, artwork, Jellyfin address, Jellyfin token, or catalogue identifiers are sent. Custom recordings do not need MusicBrainz IDs. Listens may be public on your ListenBrainz profile and are subject to [ListenBrainz's policies](https://listenbrainz.org/terms-of-service/).

The current app contains no advertising SDK, analytics SDK, or automatic crash-report upload service. It does not send your library or credentials to the project maintainer or an AI provider. Its use of AI during development does not add an AI service to the app. Your server, reverse proxy, Android, Android Auto, device vendor, and any services configured on your Jellyfin server have their own behavior and policies.

Use HTTPS. HTTP is currently permitted for self-hosted installations, but sends credentials and media without transport encryption. App offline mode blocks Jellyfin and ListenBrainz requests; it does not disable networking for other apps or the operating system. Qualified listens collected during offline playback remain queued for later upload. ListenBrainz uploads can use mobile data independently of the music-download network setting.

## Data on this phone

- The server URL, account/server IDs, and access token are stored in an encrypted session record using an Android Keystore key. The password is not saved by the app.
- If connected, the ListenBrainz token and account name are stored in a separate encrypted record using Android Keystore. Queued listens retain their metadata and original timestamps in a separate app-private database. They are tied to the connected ListenBrainz account. Submission status and preferences are stored locally.
- Catalogue metadata, collection membership, download status/hashes, lyrics, playlist entries, and pending edits are stored in app-private databases.
- Downloaded audio, artwork, image caches, and partial downloads occupy app-managed storage. Jellude does not separately encrypt these media/database files; protection depends on Android's app isolation and device encryption.
- The playback queue, position, repeat/shuffle choices, and app preferences are stored locally.
- Track titles may appear in download/playback notifications and in Android Auto or connected media controls. Their visibility depends on your Android and lock-screen settings.

Android backup and device-transfer rules exclude app data. This is not a substitute for backing up your server or music collection.

## Permissions

Network access is used for Jellyfin and, when enabled, ListenBrainz. Network-state access supports scheduling. Foreground-service permissions support playback and downloads. On Android versions that request it, notification permission permits playback/download notifications. The current app does not request contacts, location, camera, microphone, notification-listener access, or general access to shared music storage.

## Removing local data

**Stop keeping offline** removes a download/repair requirement and keeps existing phone copies. **Clean up unused local downloads** removes only audio copies no remaining offline selection requires. Shared tracks are preserved. These actions do not delete server music.

Turning ListenBrainz submission off stops tracking and uploading and keeps queued listens. **Clear queue** removes queued listens from this phone. **Disconnect** also forgets the ListenBrainz token. Neither action deletes listens already submitted to ListenBrainz; manage those through ListenBrainz itself. Disable Jellude in other scrobblers if using direct submission to avoid duplicate submissions.

Android's **Clear storage/data** for Jellude, or uninstalling the app, removes app-managed local data and downloads. Account switching and an in-app sign-out/data-retention screen remain unfinished. Revoke the client's access through your Jellyfin server if you need to invalidate its token; clearing phone data alone does not revoke a server-issued token.

## Reports and contributions

GitHub Issues, pull requests, and their attachments will be public when this repository is public. Redact credentials, access tokens, server addresses, account identifiers, sensitive file paths, and private artwork/media before posting. Report security problems privately as described in [SECURITY.md](SECURITY.md). No personal music is needed to reproduce a bug; the test suite uses generated audio.
