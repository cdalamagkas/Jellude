# Jellude brand assets

## License and modified distributions

The project's own artwork is offered under GPL-3.0-only, to the extent licensable rights apply, alongside its code. See [LICENSE](../../LICENSE), [NOTICE](../../NOTICE), and [third-party provenance](../../THIRD_PARTY_NOTICES.md). This does not relicense third-party names or marks.

The Jellude name and jellyfish/piano identity identify the maintainer's project and releases. Permission to reproduce artwork under GPLv3 does not grant permission to claim maintainer endorsement or distribute a modified app as an official Jellude release. Public distributions of modified apps must use a distinct app name and launcher identity. Preserve historical attribution and notices, and describe the upstream connection accurately (for example, “based on Jellude”). Unmodified source redistributions may preserve the project name and attribution.

This is separate branding/trade-name and origin guidance, not an additional copyright restriction on modifying or redistributing GPL-covered work. It asserts no registration of a trademark and grants no rights in Jellyfin's marks. The GPL copyright permissions remain available, including the right to modify the artwork. Nominative references and accurate attribution are permitted; do not imply affiliation with Jellyfin.

## Original artwork and provenance

Approved artwork is retained unchanged in Android resources:

- `app/src/main/res/drawable-nodpi/jellude_logo.png` — full icon and wordmark, transparent background. Used on a light surface so the approved navy lettering remains legible.
- `app/src/main/res/drawable-nodpi/jellude_icon.png` — icon-only version, transparent background. Used in the app header and adaptive launcher foreground.

The full logo resource filenames use underscores (`jellude_logo.png`, `jellude_icon.png`) as required by Android. The source artwork was created and approved in the chat “Name the Jellyfin Music project”; its original generation prompts are retained alongside this file.

`jellude-social-preview.png` is the 1280×640 white-background banner prepared with AI assistance from the full logo. The repository README uses this opaque image so its navy piano keys and lettering remain readable in both light and dark GitHub themes. Android resources remain unchanged.

Launcher layers wrap the approved icon in Android resources with padding and a pale background to accommodate launcher masks. The source PNGs are not redrawn or regenerated. A simplified single-color vector provides the status-bar and themed-launcher silhouette.

Public naming includes the Android app label, in-app branding, media-browser root, notifications, Jellyfin client identity, Gradle project name, and release filenames.

At the owner's request, application ID, code packages, and internal brand identifiers change to `dev.jellude` / `jellude`. `JelludeApp` is the application class. This is a new Android installation identity and does not migrate the previous app's data. Old release files and recorded test reports remain historical evidence of the earlier identity.

The current working directory is still `JellyfinOffline` so the already-configured local SDK and Android Studio checkout paths continue to resolve. The Gradle project displayed by the IDE is named Jellude; distribution source ZIPs use the new name.
