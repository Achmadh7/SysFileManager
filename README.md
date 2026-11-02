# SysFileManager — File Manager for Android

An Android file manager written in Kotlin with Jetpack Compose and Material 3 Expressive. Browse internal storage, SD cards, and USB drives; copy, move, rename, delete, compress, and extract files; preview photos and text; search the whole storage; and see what is taking up space — all on-device, no account, no ads.

## Features

- **Browse everything** — internal storage plus removable SD card and USB/OTG volumes, with a breadcrumb path bar, an up-level row, and list or grid view.
- **All file operations** — copy, cut, paste (with automatic name de-duplication), rename, delete with confirmation, and new folders. Long-press to start multi-select; a bottom action bar appears while items are selected.
- **Previews** — photos open in a full-screen viewer with pinch zoom; text files open in a built-in viewer/editor; videos, audio, PDFs, APKs, and everything else hand over to the right installed app. Thumbnails are shown for photos and videos in the file list.
- **Search** — recursive name search from the current folder, with results that open in place.
- **Archive powerhouse** — create and extract **ZIP** (optionally protected with an AES password), **TAR**, **TAR.GZ**, **TAR.BZ2**, and **TAR.XZ**, and extract **7Z** and **RAR** — all in place, with a path-slip guard on every extractor. Single **.gz/.bz2/.xz** streams decompress in place, **APK/APKS/XAPK/AAB** files offer inspect/extract/install, and a built-in parser extracts full (non-delta) OTA **payload.bin** files into per-partition `.img` files. The compress dialog offers a format picker, encrypted ZIPs ask for their password before extracting, and Settings → **Supported Formats** lists exactly what the app can do.
- **Hidden files** — dotfiles can be shown or hidden from the browser menu or Settings.
- **Favorites** — star any folder and it is pinned on the Home screen.
- **Storage analysis** — per-volume scan with sizes per category (images, videos, audio, documents, apps, archives, other), file counts, and the 20 largest files.
- **Home dashboard** — a steel-blue storage card with live used/total/free figures and a health chip, colour-coded category tiles (violet images, pink video, teal audio, blue documents, green APKs, amber archives), and favorite folders.
- **Recent files** — a dedicated bottom-navigation tab lists the newest files across all volumes.
- **Themes & languages** — Light / Dark / Follow System in Settings, and an Indonesian/English interface switch; both choices are remembered.
- **Official Material 3 Expressive** — `MaterialExpressiveTheme` with `MotionScheme.expressive()` and the morphing `LoadingIndicator`, extra-rounded cards, and spring motion (Material3 pinned to 1.5.0-alpha08 — the Expressive APIs were removed from the 1.4.0 stable release).

## Permissions

SysFileManager needs **All files access** (Android 11+) so it can work outside its own sandbox: the first launch shows a card that sends you to the system settings screen where the permission is granted. On Android 8–10 it asks for the classic storage permission instead. Some folders (notably `Android/data` on newer Android versions) stay locked by the system even with this permission — SysFileManager shows an "inaccessible" message there instead of failing silently.

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose (BOM 2025.10.00), Material3 1.5.0-alpha08 |
| Thumbnails | Coil 2.7.0 (image + video frame decoder) |
| Archives | Apache Commons Compress 1.27.1, Tukaani XZ 1.10, zip4j 2.11.5 |
| Build | AGP 8.11.1, Gradle 8.13, JDK 17 |
| Android | minSdk 26 (Android 8.0), targetSdk 34, compileSdk 36 |
| Package | `id.sysfile.manager` |
| Version | 1.0.0 |
| Signing | Fixed keystore committed in this repo (`sysfile.p12`, PKCS12) — every CI build carries the same signature, so updates install in place |
| R8 | Minify + resource shrinking enabled on debug and release builds, using the default optimize rules plus keep rules for the archive libraries |

## Building the APK

**Without a computer (GitHub Actions):** open the **Actions** tab in this repo → workflow **Build APK** → **Run workflow** on branch `main` → download the **`app-debug-apk`** artifact → install it on your phone.

**With Android Studio:** open this repo folder, wait for Gradle Sync, then Build APK. (The CI workflow uses Gradle 8.13 and installs Android SDK 36; the same setup works locally.)

**Updating the app:** all builds share the one fixed signature, so a new APK installs directly over the previous one and settings (theme, language, favorites) are preserved.

## Project structure

- `app/src/main/java/id/sysfile/manager/MainActivity.kt` — the entry point and the `SysFileApp` shell: navigation state, file-open dispatch, the background operation runner, and dialog dispatch.
- `app/src/main/java/id/sysfile/manager/HomeScreens.kt` — the Home dashboard and the floating pill navigation bar.
- `app/src/main/java/id/sysfile/manager/BrowseScreens.kt` — the folder browser: breadcrumb bar, list/grid, selection and clipboard bars.
- `app/src/main/java/id/sysfile/manager/LibraryScreens.kt` — Recent files, per-category lists, Search, and the storage Analysis screen.
- `app/src/main/java/id/sysfile/manager/Dialogs.kt` — all dialogs: new folder/rename, properties, sort, compress (format picker + ZIP password), and the encrypted-ZIP password prompt.
- `app/src/main/java/id/sysfile/manager/SettingsScreens.kt` — Settings and the Supported Formats screen.
- `app/src/main/java/id/sysfile/manager/FileOps.kt` — the file engine: directory listing and sorting, storage volumes, recursive copy/move/delete, name de-duplication, search, ZIP compress/extract, the category/largest-files analysis, and `ArchiveOps` (TAR family, 7Z and RAR extraction, single-stream decompression, encrypted ZIP, archive content listing).
- `app/src/main/java/id/sysfile/manager/PayloadOps.kt` — the payload.bin extractor: a hand-written minimal protobuf reader that writes full-OTA partitions to `.img` files and rejects delta payloads before writing anything.
- `app/src/main/java/id/sysfile/manager/Components.kt` — reusable UI pieces: section cards, file rows and grid cells, thumbnails (Coil), category icons and colours, and the storage usage bar.
- `app/src/main/java/id/sysfile/manager/Viewers.kt` — the full-screen photo viewer (pinch zoom) and the text viewer/editor.
- `app/src/main/java/id/sysfile/manager/Theme.kt` — the hand-written light/dark colour schemes, typography, and shapes.
- `app/src/main/java/id/sysfile/manager/Strings.kt` — the two-language text table (Indonesian/English) for the whole interface.

## Notes

- File operations run on a background dispatcher with a progress dialog for long work, so the UI stays responsive.
- Deleting is permanent (there is no recycle bin) and always asks for confirmation first.
- Pasting a file into a folder that already contains the same name never overwrites: the copy is saved as `name (1)`, `name (2)`, and so on.
