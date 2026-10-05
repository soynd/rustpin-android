# rustpin-android

Android port of **rustpin** (Pinterest client, no login). No login required; unlike the Linux desktop build there is no bundled upscaler.

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/5b9a7afa-ea6d-4fa0-8886-bfb766a07880" />

## Layout (designed to make sense, not a random grid)

```
+------------------------------------------------+  no top bar - search lives at top of Browse
| Search...                                      |  1. search field (auto-searches as you type)
| +------------+ +----------------+ +---------+  |
| | tile       | | tile (taller)  | | tile    |  |  2. masonry feed (adaptive ~160dp cols)
| | title      | | title          | | title   |  |     endless scroll via bookmarks
| +------------+ +----------------+ +---------+  |
+------------------------------------------------+
| [Browse]        [Downloads (n)]    [Settings]  |  bottom nav
+------------------------------------------------+
```

* **Browse tab** - rounded search pill + settings gear on top (live, debounced), title-free masonry feed below, endless scroll. Empty = blank feed; errors show one line + Retry.
* **Detail** - one scrolling web page: hero wallpaper, Save (with % + progress bar on the button) + Open, then `More like this` rows. Tap the wallpaper for an immersive fullscreen viewer; tap again (or back) to close.
* **Settings** - accent colour swatches (default red), Auto accent colour (uses the wallpaper's dominant colour), Appearance: System / Light / Dark.

## Desktop parity

Desktop source lives in the sibling repo [soynd/rustpin](https://github.com/soynd/rustpin), under `native/src/`.

| desktop (`native/src/`) | android (`app/src/main/`) |
|---|---|
| `pinterest.rs` search + related, csrftoken/app-version, no login | `net/PinterestClient.kt` - same endpoints/headers via OkHttp |
| `settings.rs` PRESETS/Target/auto_scale/fit_size/JSON | `store/SettingsStore.kt` - same presets + box logic via DataStore |
| `jobs.rs` download -> upscale -> save queue | `work/DownloadWorker.kt` - download -> fit-inside-box -> `Pictures/rustpin` (WorkManager) |
| egui masonry/header/overlay | Compose staggered grid + bottom sheet + banner |

**Upscale note:** desktop upscales with the Upscayl CLI (needs a desktop GPU install). There is no equivalent
on-device upscaler bundled for phones, so Android saves the **true original** and only downscales when it is
bigger than the chosen target box. Same filenames (`title_pinid.png`), same `Pictures/rustpin` folder idea.

## Build

Requirements: JDK 17, Android SDK 34.

```bash
git clone https://github.com/soynd/rustpin-android.git && cd rustpin-android
export ANDROID_HOME=/path/to/sdk   # or create local.properties with sdk.dir=...
./scripts/build.sh        # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./scripts/build.sh release
./scripts/install.sh      # install debug build on connected device
```

`gradlew` is a small shim over a local Gradle 8.7+ install (no vendored jar). `scripts/build.sh` calls it; or run `gradle wrapper --gradle-version 8.9` once to generate the standard wrapper.
