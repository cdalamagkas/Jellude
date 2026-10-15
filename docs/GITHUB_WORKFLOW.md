# Working on Jellude with GitHub

Repository: https://github.com/cdalamagkas/Jellude.

The repository root is the Android Studio project root. The source includes Gradle wrapper scripts and JAR, Room schemas, application code, tests, and approved branding assets. Local SDKs, build caches, signing keys, machine-specific settings, APKs, ZIPs, and generated test reports are excluded.

## Open on another computer

1. Clone the repository using Android Studio's **Get from Version Control**, Git, or GitHub Desktop. While it is private, authenticate with an account that has access.
2. Open the cloned root directory in Android Studio.
3. Use Gradle JVM 21. Install Android SDK platform 37 and Build Tools 36.0.0 when prompted.
4. Let Android Studio create the machine-local `local.properties` SDK setting and finish Gradle sync.

## Everyday changes

```sh
git pull --ff-only
git switch -c feature/describe-the-change
# Edit and verify your change.
./gradlew :core:test :app:assembleDebug :app:lintDebug
git add <changed-files>
git commit -m "Describe the change"
git push -u origin feature/describe-the-change
```

Open a pull request into `main` to review a change. In Windows PowerShell use `.\gradlew.bat` instead of `./gradlew`.

## Build artifacts

GitHub Actions runs core unit tests, APK compilation, and lint for changes to `main` and pull requests. A manual run is also available. It uploads verification reports only: the source-only launch does not publish APKs. The existing Android device tests remain available locally; emulator runs are not part of the initial GitHub workflow. Review [CONTRIBUTING.md](../CONTRIBUTING.md) before running tests on a device.

The APK compiled for CI verification uses a runner-generated debug certificate and is not uploaded. Older workflow runs may still contain debug APK artifacts until explicitly removed or expired. Build locally for development; stable release signing is a future gate. Signing keys and credentials must remain outside Git history.

## Automation and repository controls

External actions are pinned to reviewed full commit SHAs, checkout does not retain credentials, and the workflow token has read-only repository contents access. Dependabot is configured to open weekly Gradle and GitHub Actions update PRs after its configuration reaches the default branch; it does not automatically approve or merge them.

Require the successful `build` check from the Android build workflow before merging into `main`, require PRs, and disallow force pushes/deletion when repository plan/visibility supports those rules. Do not require approval from a second person in a solo-maintainer repository unless another reviewer is available. GitHub settings are not enabled merely by committing these files; see the [publication checklist](PUBLICATION_CHECKLIST.md) for their actual activation status.

## Local checkout

The existing working directory is still named `JellyfinOffline`; its Git repository and Android Studio project are named Jellude. No second source copy is needed to continue working from this computer.
