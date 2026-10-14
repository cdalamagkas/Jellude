# Third-party notices and provenance

The GPL-3.0-only offer for Jellude applies to its own work. It does not replace the licenses or authorship of third-party software. Dependencies are resolved by Gradle from Google Maven, Maven Central and the Gradle Plugin Portal; their source is not represented as AI-written Jellude code.

## App runtime

The resolved [runtime inventory](docs/DEPENDENCIES.md) records exact versions and published POM license declarations, including inherited Guava licenses. The declarations reviewed for this snapshot are Apache-2.0 and MIT; no incompatible runtime license declaration was found. This is a metadata-level compatibility review, not an independent legal or file-by-file binary audit. Apache-2.0 may be combined with GPLv3 while retaining its notices; see the [Apache Software Foundation's explanation](https://www.apache.org/licenses/GPL-compatibility).

| Component family | Upstream / attribution | Declared license |
|---|---|---|
| AndroidX: Activity, Compose/Material icons, Lifecycle, Room/SQLite, WorkManager, Media/Media3 and supporting libraries | [Android Open Source Project / AndroidX contributors](https://android.googlesource.com/platform/frameworks/support/) | Apache-2.0 |
| Kotlin standard library | [JetBrains / Kotlin contributors](https://github.com/JetBrains/kotlin) | Apache-2.0 |
| Kotlin coroutines and serialization | [Kotlin libraries contributors](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| OkHttp and Okio | [Square and contributors](https://github.com/square/okhttp) | Apache-2.0 |
| Coil | [Coil contributors](https://github.com/coil-kt/coil) | Apache-2.0 |
| Guava, failureaccess, listenablefuture placeholder | [Google / Guava contributors](https://github.com/google/guava) | Apache-2.0, inherited from Guava parent POMs |
| Accompanist Drawable Painter | [Google / Accompanist contributors](https://github.com/google/accompanist) | Apache-2.0 |
| Error Prone, JSR-305, J2ObjC annotations | Respective Google/project contributors; see runtime coordinates | Apache-2.0 |
| JetBrains annotations / JSpecify | [JetBrains](https://github.com/JetBrains/java-annotations), [JSpecify contributors](https://github.com/jspecify/jspecify) | Apache-2.0 |
| Checker Framework `checker-qual` only | [Checker Framework contributors](https://github.com/typetools/checker-framework) | MIT; upstream explicitly distinguishes annotation artifacts from its GPLv2 checker implementation |

Reference copies: [Apache-2.0](third_party/licenses/Apache-2.0.txt) and the versioned [Checker Framework license statement](third_party/licenses/Checker-Framework-3.43.0-LICENSE.txt), which contains the MIT grant applicable to `checker-qual`. The latter's GPLv2/classpath sections describe other Checker Framework components, not a runtime checker bundled by Jellude.

## Source/build/test tooling

The committed Gradle wrapper scripts and JAR come from Gradle and retain their upstream Apache-2.0 license/headers. Kotlin/Compose compiler, AGP, KSP, Gradle, Android SDK tools and CI actions are build tooling, not Jellude-authored application code. They retain their upstream licenses; Android SDK components are obtained separately under the SDK's terms. JUnit and Android test tooling are used for testing, not included as production app functionality. Do not assume every build/test-tool license is the app's GPL license.

This repository does not vendor those dependency implementations or the Android SDK. The runtime inventory is intentionally not an inventory of every build plugin and test tool. Binary releases must separately inventory the exact distributed components and preserve applicable copyright, license, NOTICE and source-availability requirements. See [release gates](docs/RELEASING.md).

## Artwork, screenshots and test media

- The original Jellude jellyfish/piano icon and wordmark were generated using AI tools under the maintainer's direction, then approved by the maintainer. Original prompts are retained in [docs/branding](docs/branding/README.md). These project PNGs are offered under GPL-3.0-only to the extent licensable rights apply, with separate branding guidance.
- The simplified notification/themed-launcher silhouettes are project assets. Material icons used through Compose remain AndroidX upstream assets under Apache-2.0.
- Repository app screenshots use synthetic catalogue data or empty/login screens. Integration-test audio is generated as a short WAV tone; no personal music, commercial album artwork, or song lyrics are bundled as fixtures. Mock lyric text is synthetic.
- Album art, lyrics and audio retrieved from a user's server belong to their respective rights holders. The Jellude license does not grant rights in that media.

Jellude is an independent project and does not claim endorsement by Jellyfin or any dependency author.
