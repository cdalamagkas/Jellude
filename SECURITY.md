# Security policy

Jellude is experimental and has not received an independent security audit. The current development branch and latest tagged version are the focus of fixes; older versions do not have a separate maintenance commitment. There is no guaranteed response or resolution time.

## Private vulnerability reports

Use [GitHub private vulnerability reporting](https://github.com/cdalamagkas/Jellude/security/advisories/new), available through **Security → Advisories → Report a vulnerability** when enabled. Do not open a public issue containing an exploit, credentials, or private server information.

Public-launch prerequisite: the maintainer must enable private vulnerability reporting after the repository becomes public and verify that the reporting button is available. While the repository is private, or if the button is unavailable, do not publish vulnerability details. You may open an issue containing only a request to enable a private reporting channel, with no technical details or sensitive information.

Include the affected version/commit, Android and Jellyfin versions, impact, and a minimal reproduction using generated or non-sensitive data. Never provide a real password or access token, even in a private report. Coordinate disclosure with the maintainer while a fix is investigated.

## Design boundaries

- Use HTTPS and a Jellyfin account with only the permissions you need. Playlist editing requires permission to modify those playlists.
- Session records use Android Keystore encryption. Passwords are not persisted; downloaded media and databases have no additional app-level encryption.
- Server file deletion is outside the app's scope and is blocked at the API and outbound request layers. Only removing playlist entries uses DELETE.
- Downloads are validated before publication. Hash checks detect changes to local files; they do not prove equality to a server file when the server supplies no reliable validator.
- Android Auto exposes the media browsing service to supported controllers. A real car/head-unit security and compatibility review remains a release gate.

See [PRIVACY.md](PRIVACY.md), [architecture](docs/ARCHITECTURE.md), and the [roadmap](docs/ROADMAP.md) for data handling and known limits. Reports about server software itself should go to that project's security process.
