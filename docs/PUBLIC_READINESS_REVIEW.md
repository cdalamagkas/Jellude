# Public-readiness review — 14 October 2026

Scope: preparing Jellude 0.1.3's repository for a source-only public launch. This is a preparation record, not an independent security/legal audit, and does not change repository visibility.

## Checks performed

- Repeated the limited scan after the preparation commits: 94 reachable Git blobs across all local refs plus 75 current tracked/unignored working files were checked for common private-key, GitHub-token, AWS-key, Google-API-key, JWT-shaped-value and private-LAN-URL patterns. No matches were found. Also checked reachable historical filenames for `.env`, local SDK properties and keystores. This limited scan cannot prove absence of secrets or identify every sensitive value.
- Reviewed repository screenshot content: empty login screens, Android app info, and synthetic test-track playback/queue screens. No personal server credentials, personal library media, or private server details were visible.
- Reviewed visible source requests/storage/permissions for the privacy statement. No analytics, advertising or automatic remote crash-upload implementation was found. This does not audit the operating system, Jellyfin server, or dependencies' entire implementations.
- Generated the resolved release-runtime inventory: 100 unique external coordinates. All published POM declarations, including inherited Guava declarations, map to Apache-2.0 or MIT. No incompatible declaration was found; exact binary embedded/native notices still need review before APK distribution.
- Generated the inventory using the committed Gradle/Python tooling and checked the Python script compiles. The inventory contains no local cache/filesystem paths.
- Parsed all five GitHub YAML files with duplicate-key detection and checked issue-form IDs/types, read-only workflow permissions, 40-character action pins, disabled checkout credential persistence, and absence of APK artifact uploads/signing secrets.
- Checked relative Markdown/image links and `git diff --check`.
- Verified action commit hashes against the official repositories' existing version tags; no dependency or tool version upgrade was made.
- Saved a `main` branch protection rule after owner-approved GitHub reauthentication. GitHub explicitly reports that enforcement is unavailable while this repository is private on the current plan. No subscription was purchased or visibility changed.
- Enabled and verified the dependency graph, Dependabot vulnerability alerts/security-fix PRs, and malware alerts. Private vulnerability reporting and secret-scanning controls were not offered on this private repository's settings page and remain public-launch checks.
- With the owner's explicit approval, permanently deleted the three historical CI APK artifacts `Jellude-debug-2`, `Jellude-debug-3` and `Jellude-debug-4`. Verified that `verification-2`, `verification-3` and `verification-4` remain. Local APK files, workflow runs and Git history were retained. No GitHub Releases were published.

The app's functional code, dependency versions and Android resources are unchanged by this preparation. The preceding app revision passed 19 core and 7 Android 16 tests. The preparation PR's own GitHub build/lint/core-test results should be checked before merge; earlier results must not be substituted for a failed new workflow run.

## Decisions and remaining launch gates

The owner chose GPLv3, artwork under the same copyright license with distinct branding for modified distributions, a source-only launch, GitHub private vulnerability reporting, and retention of the current Git author identity/history.

Private reporting is a public-repository feature and must be enabled and verified at launch. The approved historical APK cleanup is complete; Actions logs and other public-facing metadata remain part of the final publication review. The author email is retained at the owner's request.

Use [PUBLICATION_CHECKLIST.md](PUBLICATION_CHECKLIST.md) for the live checklist. This record does not assert that GitHub security settings are enabled merely because policy files exist, nor that the repository is already ready for an unaudited binary release.
