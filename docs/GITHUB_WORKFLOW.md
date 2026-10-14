# Working on Jellude with GitHub

Repository: https://github.com/cdalamagkas/Jellude (private).

The repository root is the Android Studio project root. The source includes Gradle wrapper scripts and JAR, Room schemas, application code, tests, and approved branding assets. Local SDKs, build caches, signing keys, machine-specific settings, APKs, ZIPs, and generated test reports are excluded.

## Open on another computer

1. Clone the private repository using Android Studio's **Get from Version Control** or GitHub Desktop while signed in to your account.
2. Open the cloned root directory in Android Studio.
3. Use Gradle JVM 21. Install Android SDK platform 35 and Build Tools 36.0.0 when prompted.
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

GitHub Actions runs core unit tests, APK compilation, and lint for changes to `main` and pull requests. A manual run is also available. Download the debug APK and reports from the workflow's artifacts. The existing Android device tests remain available locally; emulator runs are not part of the initial GitHub workflow.

The CI APK uses a runner-generated debug certificate. It will not necessarily update an APK signed by your development computer. Build on your original computer for same-key development updates, or configure a stable release-signing key later. Signing keys and credentials must remain outside Git history.

## Local checkout

The existing working directory is still named `JellyfinOffline`; its Git repository and Android Studio project are named Jellude. No second source copy is needed to continue working from this computer.
