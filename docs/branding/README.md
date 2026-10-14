# Jellude brand assets

Approved artwork is retained unchanged in Android resources:

- `app/src/main/res/drawable-nodpi/jellude_logo.png` — full icon and wordmark, transparent background. Used on a light surface so the approved navy lettering remains legible.
- `app/src/main/res/drawable-nodpi/jellude_icon.png` — icon-only version, transparent background. Used in the app header and adaptive launcher foreground.

The full logo resource filenames use underscores (`jellude_logo.png`, `jellude_icon.png`) as required by Android. The source artwork was created and approved in the chat “Name the Jellyfin Music project”; its original generation prompts are retained alongside this file.

Launcher layers wrap the approved icon in Android resources with padding and a pale background to accommodate launcher masks. The source PNGs are not redrawn or regenerated. A simplified single-color vector provides the status-bar and themed-launcher silhouette.

Public naming includes the Android app label, in-app branding, media-browser root, notifications, Jellyfin client identity, Gradle project name, and release filenames.

At the owner's request, application ID, code packages, and internal brand identifiers change to `dev.jellude` / `jellude`. `JelludeApp` is the application class. This is a new Android installation identity and does not migrate the previous app's data. Old release files and recorded test reports remain historical evidence of the earlier identity.

The current working directory is still `JellyfinOffline` so the already-configured local SDK and Android Studio checkout paths continue to resolve. The Gradle project displayed by the IDE is named Jellude; distribution source ZIPs use the new name.
