# Architecture and reliability


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


See [ROADMAP.md](ROADMAP.md) for remaining reliability gates and [PRIVACY.md](../PRIVACY.md) for data handling.
