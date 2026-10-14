# Source launch and future binary releases

## Current distribution policy

The initial public launch is **source only**. CI compiles a debug APK as a verification step but uploads only verification reports. There are no official maintainer-signed production APKs, Play Store listings, or promised release dates. Local development APKs and source ZIPs are ignored by Git.

Build from source using the README instructions. A local debug build uses your development machine's debug certificate. It may not update a build signed elsewhere. Do not uninstall an app containing music you need offline without accounting for the loss of its app-managed downloads.

Before publication, inspect/remove older CI APK artifacts deliberately; changing the workflow does not remove artifacts from earlier runs. Keep local development APKs separate from public release assets.

## Source version/tag checklist

1. Review the changelog, version in `app/build.gradle.kts`, compatibility evidence, and open release gates.
2. Run core tests, compilation and lint, plus the relevant Android emulator/device tests.
3. Review dependencies, third-party license/NOTICE files, and source/artwork provenance. Update the runtime inventory when dependencies change.
4. Confirm that the source archive is built from the intended tag/commit and contains no machine-specific settings or secrets.
5. Tag only the intended commit, describe it as experimental, and distinguish source tags from binary-release announcements. Do not invent past tags from changelog entries.

## Before distributing APKs

- Decide whether public releases should update the existing development installation or use a distinct installation identity. Changing the signing key for the same application ID prevents normal in-place updates.
- Create/select a stable release-signing key under the maintainer's control, store it outside the repository, and keep an offline recovery backup. Never commit key material or passwords or expose signing secrets to pull-request workflows.
- Configure signing through external secrets only when the distribution process has been agreed. The current build has no release-signing configuration.
- Verify installation and same-key updates, Room migrations, offline-library preservation, long downloads, repair after interruption, storage exhaustion, airplane-mode cold start, and real Android Auto behavior.
- Package all applicable dependency license and NOTICE texts for the exact APK, preserve required notices, and publish matching complete source plus build instructions for the binary under GPLv3. Check the chosen distribution method against the license's source-distribution requirements.
- Verify the APK signature and package/version, publish SHA-256 checksums and release notes, and document how users verify the signing certificate.
- Test any release workflow without secrets first. Signing/publishing should run only for reviewed maintainer-controlled tags, with minimal permissions and an explicit approval boundary.

Useful references: [Android app signing](https://developer.android.com/studio/publish/app-signing) and [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html).
