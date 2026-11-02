# SysFile — File Manager for Android

An Android file manager written in Kotlin with Jetpack Compose and Material 3 Expressive. Browse internal storage, SD cards, and USB drives; copy, move, rename, delete, compress, and extract files; preview photos and text; search the whole storage; and see what is taking up space — all on-device, no account, no ads.

## Features

- **Browse everything** — internal storage plus removable SD card and USB/OTG volumes, with a breadcrumb path bar, an up-level row, and list or grid view.
- **All file operations** — copy, cut, paste (with automatic name de-duplication), rename, delete with confirmation, and new folders. Long-press to start multi-select; a bottom action bar appears while items are selected.
- **Previews** — photos open in a full-screen viewer with pinch zoom; text files open in a built-in viewer/editor; videos, audio, PDFs, APKs, and everything else hand over to the right installed app. Thumbnails are shown for photos and videos in the file list.
- **Search** — recursive name search from the current folder, with results that open in place.
- **ZIP support** — compress any selection into a `.zip` in the current folder, and extract `.zip` files where they sit (with a zip-slip guard).
- **Hidden files** — dotfiles can be shown or hidden from the browser menu or Settings.
- **Favorites** — star any folder and it is pinned on the Home screen.
- **Storage analysis** — per-volume scan with sizes per category (images, videos, audio, documents, apps, archives, other), file counts, and the 20 largest files.
- **Home dashboard** — every storage volume with used/free bars, category shortcuts, and favorites.
- **Themes & languages** — Light / Dark / Follow System in Settings, and an Indonesian/English interface switch; both choices are remembered.
- **Official Material 3 Expressive** — `MaterialExpressiveTheme` with `MotionScheme.expressive()` and the morphing `LoadingIndicator`, extra-rounded cards, and spring motion (Material3 pinned to 1.5.0-alpha08 — the Expressive APIs were removed from the 1.4.0 stable release).

## Permissions

SysFile needs **All files access** (Android 11+) so it can work outside its own sandbox: the first launch shows a card that sends you to the system settings screen where the permission is granted. On Android 8–10 it asks for the classic storage permission instead. Some folders (notably `Android/data` on newer Android versions) stay locked by the system even with this permission — SysFile shows an "inaccessible" message there instead of failing silently.

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose (BOM 2025.10.00), Material3 1.5.0-alpha08 |
| Thumbnails | Coil 2.7.0 (image + video frame decoder) |
| Build | AGP 8.11.1, Gradle 8.13, JDK 17 |
| Android | minSdk 26 (Android 8.0), targetSdk 34, compileSdk 36 |
| Package | `id.sysfile` |
| Version | 1.0.0 |
| Signing | Fixed keystore committed in this repo (`sysfile.p12`, PKCS12) — every CI build carries the same signature, so updates install in place |
| R8 | Minify + resource shrinking enabled on the **debug** build (the APK installed day to day), using the default optimize rules |

## Building the APK

**Without a computer (GitHub Actions):** open the **Actions** tab in this repo → workflow **Build APK** → **Run workflow** on branch `main` → download the **`app-debug-apk`** artifact → install it on your phone.

**With Android Studio:** open this repo folder, wait for Gradle Sync, then Build APK. (The CI workflow uses Gradle 8.13 and installs Android SDK 36; the same setup works locally.)

**Updating the app:** all builds share the one fixed signature, so a new APK installs directly over the previous one and settings (theme, language, favorites) are preserved.

## Project structure

- `app/src/main/java/id/sysfile/MainActivity.kt` — the entry point and main screens: Home dashboard, folder browser, search, storage analysis, all dialogs (operations, properties, sort, settings, about), and the selection/clipboard bars.
- `app/src/main/java/id/sysfile/FileOps.kt` — the file engine: directory listing and sorting, storage volumes, recursive copy/move/delete, name de-duplication, search, ZIP compress/extract, and the category/largest-files analysis.
- `app/src/main/java/id/sysfile/Components.kt` — reusable UI pieces: section cards, file rows and grid cells, thumbnails (Coil), category icons and colours, and the storage usage bar.
- `app/src/main/java/id/sysfile/Viewers.kt` — the full-screen photo viewer (pinch zoom) and the text viewer/editor.
- `app/src/main/java/id/sysfile/Theme.kt` — the hand-written light/dark colour schemes, typography, and shapes.
- `app/src/main/java/id/sysfile/Strings.kt` — the two-language text table (Indonesian/English) for the whole interface.

## Notes

- File operations run on a background dispatcher with a progress dialog for long work, so the UI stays responsive.
- Deleting is permanent (there is no recycle bin) and always asks for confirmation first.
- Pasting a file into a folder that already contains the same name never overwrites: the copy is saved as `name (1)`, `name (2)`, and so on.
