# Runtime dependency inventory

Resolved from `:app:releaseRuntimeClasspath` for the source-only public-launch preparation (app version 0.1.3). Coordinates are unique; BOMs, the local core module, build plugins, and test/debug-only dependencies are excluded. License declarations come from the resolved artifacts' published POMs, following parent POMs where necessary (including Guava).

This records declared licenses, not a complete file-by-file audit. Preserve upstream notices and review embedded/native components for the exact packaged APK before binary distribution. See [third-party notices](../THIRD_PARTY_NOTICES.md).

Regenerate from the repository root with JDK 21 and Python 3:

```sh
./gradlew -I tools/runtime-dependencies.init.gradle :app:runtimeDependencyInventory
python tools/update_dependency_inventory.py
```

Use `.\gradlew.bat` in Windows PowerShell. The script honors `GRADLE_USER_HOME` or accepts `--gradle-user-home PATH`. It stops on missing or unreviewed license declarations; do not replace them with guesses.

| Runtime coordinate | POM-declared license (SPDX) |
|---|---|
| `androidx.activity:activity:1.10.1` | Apache-2.0 |
| `androidx.activity:activity-compose:1.10.1` | Apache-2.0 |
| `androidx.activity:activity-ktx:1.10.1` | Apache-2.0 |
| `androidx.annotation:annotation-experimental:1.4.1` | Apache-2.0 |
| `androidx.annotation:annotation-jvm:1.9.1` | Apache-2.0 |
| `androidx.appcompat:appcompat-resources:1.6.1` | Apache-2.0 |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 |
| `androidx.compose.animation:animation-android:1.8.0` | Apache-2.0 |
| `androidx.compose.animation:animation-core-android:1.8.0` | Apache-2.0 |
| `androidx.compose.foundation:foundation-android:1.8.0` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android:1.8.0` | Apache-2.0 |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 |
| `androidx.compose.material:material-icons-extended-android:1.7.8` | Apache-2.0 |
| `androidx.compose.material:material-ripple-android:1.8.0` | Apache-2.0 |
| `androidx.compose.material3:material3-android:1.3.2` | Apache-2.0 |
| `androidx.compose.runtime:runtime-android:1.8.0` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-text-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android:1.8.0` | Apache-2.0 |
| `androidx.compose.ui:ui-util-android:1.8.0` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures-ktx:1.1.0` | Apache-2.0 |
| `androidx.core:core:1.13.1` | Apache-2.0 |
| `androidx.core:core-ktx:1.13.1` | Apache-2.0 |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 |
| `androidx.exifinterface:exifinterface:1.3.7` | Apache-2.0 |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-service:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.0` | Apache-2.0 |
| `androidx.media:media:1.7.0` | Apache-2.0 |
| `androidx.media3:media3-common:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-container:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-database:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-datasource:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-datasource-okhttp:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-decoder:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-exoplayer:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-extractor:1.6.1` | Apache-2.0 |
| `androidx.media3:media3-session:1.6.1` | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 |
| `androidx.room:room-common-jvm:2.7.1` | Apache-2.0 |
| `androidx.room:room-ktx:2.7.1` | Apache-2.0 |
| `androidx.room:room-runtime-android:2.7.1` | Apache-2.0 |
| `androidx.savedstate:savedstate-android:1.3.0` | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx:1.3.0` | Apache-2.0 |
| `androidx.sqlite:sqlite-android:2.5.0` | Apache-2.0 |
| `androidx.sqlite:sqlite-framework-android:2.5.0` | Apache-2.0 |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 |
| `androidx.tracing:tracing-ktx:1.2.0` | Apache-2.0 |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Apache-2.0 |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 |
| `androidx.work:work-runtime:2.10.1` | Apache-2.0 |
| `androidx.work:work-runtime-ktx:2.10.1` | Apache-2.0 |
| `com.google.accompanist:accompanist-drawablepainter:0.32.0` | Apache-2.0 |
| `com.google.code.findbugs:jsr305:3.0.2` | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations:2.28.0` | Apache-2.0 |
| `com.google.guava:failureaccess:1.0.2` | Apache-2.0 |
| `com.google.guava:guava:33.3.1-android` | Apache-2.0 |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | Apache-2.0 |
| `com.google.j2objc:j2objc-annotations:3.0.0` | Apache-2.0 |
| `com.squareup.okhttp3:okhttp:4.12.0` | Apache-2.0 |
| `com.squareup.okio:okio-jvm:3.9.0` | Apache-2.0 |
| `io.coil-kt:coil:2.7.0` | Apache-2.0 |
| `io.coil-kt:coil-base:2.7.0` | Apache-2.0 |
| `io.coil-kt:coil-compose:2.7.0` | Apache-2.0 |
| `io.coil-kt:coil-compose-base:2.7.0` | Apache-2.0 |
| `org.checkerframework:checker-qual:3.43.0` | MIT |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.4.21` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 |

Total: 100 external runtime coordinates.
