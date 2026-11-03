# SysFileManager — File Manager for Android

An Android file manager written in Kotlin with Jetpack Compose and Material 3 Expressive (the installed app is named **SysFile**). Browse internal storage, SD cards, USB drives, and even the system root; copy, move, rename, delete, compress, and extract files; preview photos, text, and HTML; lock private folders behind your fingerprint; manage installed apps; search the whole storage; and see what is taking up space — all on-device, no account, no ads.

## Features

- **Browse everything** — internal storage plus removable SD card and USB/OTG volumes, with a breadcrumb path bar, an up-level row, and list or grid view. A **Root (Sistem)** entry (enabled in Settings → Other) browses `/`: a limited Android-permitted view without root, and the full filesystem through the root shell when Magisk/su grants access (the prompt appears only when you actually enter it). Recently opened folders are cached per session, so going back into a folder is instant while fresh data loads behind it.
- **All file operations** — copy, cut, paste (with automatic name de-duplication), rename, delete, and new folders. Long-press to start multi-select; a labeled action pill appears (Copy, Extract, Compress, Delete, and a More menu with Cut, Rename, Share, Properties, Select All, and Lock/Unlock Folder).
- **Trash bin** — deleting moves items into the trash (Settings → Files → **Trash**) instead of erasing them: restore them to their original place, delete them forever one by one, or empty the whole bin. Trashed files stay out of Recent, category lists, search, and storage analysis.
- **Previews** — photos open in a full-screen viewer with pinch zoom; text files open in a built-in viewer/editor with syntax highlighting for code and markup, and `.html`/`.htm` files get a **web preview mode** (WebView, relative images/CSS resolve, edits included); videos, audio, PDFs, and everything else hand over to the right installed app. Thumbnails are shown for photos and videos in the file list.
- **Properties that tell more** — files show type, path (with a copy button), size, modification date, and on-demand **MD5 + SHA-256 checksums** (one streaming pass, with copy buttons). Folders additionally report their recursive total size, file count, subfolder count, and the largest item inside.
- **Locked folders** — lock any folder from the selection menu: it gets a lock badge, and opening it (from browse, favorites, search, or Recent) requires your **fingerprint or lock-screen PIN** via the system biometric prompt; one unlock lasts for the session. The contents of locked folders are hidden from Recent, category lists, search, and storage analysis. This is an in-app gate only — files are not moved or encrypted.
- **App manager** — Settings → Other → **App Manager** lists every installed app with its icon, version, and size (total including data and cache when Usage Access is granted, otherwise the APK size), with All/User/System filters and size/name sorting. Per app: open it, **copy its APK** to a folder of your choice, **share its APK**, or jump to Android's app-info page.
- **Search** — recursive name search from the current folder, grouped into folders and files, with category filter chips; results open in place.
- **Archive powerhouse** — create and extract **ZIP** (optionally protected with an AES password), **TAR**, **TAR.GZ**, **TAR.BZ2**, and **TAR.XZ**, and extract **7Z** and **RAR** — all in place, with a path-slip guard on every extractor. Single **.gz/.bz2/.xz** streams decompress in place, **APK/APKS/XAPK/AAB** files offer inspect/extract/install (the dialog shows the app's real icon, name, version, and package name first), and a built-in parser extracts full (non-delta) OTA **payload.bin** files into per-partition `.img` files — tapping any `.bin` file checks whether it is a payload and falls back to a normal open when it is not. Tapping an archive opens its contents in a browser screen first — folders inside the archive can be entered, a single file can be extracted and opened, or everything can be extracted at once. Tapping an unencrypted ZIP first asks where to extract: into a new folder named after the archive, browse the contents first, or extract to a **custom folder** picked in a built-in folder browser. The compress dialog offers a format picker, encrypted ZIPs ask for their password before extracting, and Settings → **Supported Formats** lists exactly what the app can do.
- **Hidden files** — dotfiles can be shown or hidden from the browser menu or Settings.
- **Favorites** — star any folder and it is pinned on the Home screen.
- **Storage analysis** — per-volume scan with sizes per category (images, videos, audio, documents, apps, archives, other), file counts, and the 20 largest files.
- **Home dashboard** — a steel-blue storage card (used amount, total/free detail, a full-width wavy progress bar, the used percentage, and a health chip — Healthy / Almost Full / Full), dark navy rows for the other volumes with their free space at a glance, colour-coded category tiles (violet images, pink video, teal audio, blue documents, green APKs, amber archives), and favorite folders.
- **Recent files** — a dedicated bottom-navigation tab lists the newest files across all volumes.
- **Themes & languages** — Light / Dark / Follow System in Settings, and an Indonesian/English interface switch; both choices are remembered.
- **Official Material 3 Expressive** — `MaterialExpressiveTheme` with `MotionScheme.expressive()` and the morphing `LoadingIndicator`, extra-rounded cards, gentle 300 ms crossfades between tabs and category/archive screens, and spring motion (Material3 pinned to 1.5.0-alpha08 — the Expressive APIs were removed from the 1.4.0 stable release).

## Permissions

SysFileManager needs **All files access** (Android 11+) so it can work outside its own sandbox: the first launch shows a card that sends you to the system settings screen where the permission is granted. On Android 8–10 it asks for the classic storage permission instead. Some folders (notably `Android/data` on newer Android versions) stay locked by the system even with this permission — SysFileManager shows an "inaccessible" message there instead of failing silently. The App Manager additionally uses package-visibility access to list installed apps, and reads total app sizes only when you grant **Usage Access** in the system settings (a shortcut card inside the App Manager screen takes you there).

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose (BOM 2025.10.00), Material3 1.5.0-alpha08 |
| Thumbnails | Coil 2.7.0 (image + video frame decoder) |
| Archives | Apache Commons Compress 1.27.1, Tukaani XZ 1.10, zip4j 2.11.5, Junrar 7.5.5 |
| Biometrics | AndroidX Biometric 1.1.0 (fingerprint / device credential) |
| Build | AGP 8.11.1, Gradle 8.13, JDK 17 |
| Android | minSdk 26 (Android 8.0), targetSdk 34, compileSdk 36 |
| Package | `id.sysfile.manager` |
| Version | 1.0.0 |
| Signing | Fixed keystore committed in this repo (`sysfile.p12`, PKCS12) — every CI build carries the same signature, so updates install in place |
| R8 | Minify + resource shrinking enabled on debug and release builds, using the default optimize rules plus keep rules for the archive libraries |

## Building the APK

**Without a computer (GitHub Actions):** open the **Actions** tab in this repo → workflow **Build APK** → **Run workflow** on branch `main` → download the **`app-debug-apk`** artifact → install it on your phone.

**With Android Studio:** open this repo folder, wait for Gradle Sync, then Build APK. (The CI workflow uses Gradle 8.13 and installs Android SDK 36; the same setup works locally.)

**Updating the app:** all builds share the one fixed signature, so a new APK installs directly over the previous one and settings (theme, language, favorites, locked folders) are preserved.

## Project structure

- `app/src/main/java/id/sysfile/manager/MainActivity.kt` — the entry point (`FragmentActivity`) and the `SysFileApp` shell: navigation state, the locked-folder biometric gate, file-open dispatch, the background operation runner, and dialog dispatch.
- `app/src/main/java/id/sysfile/manager/HomeScreens.kt` — the Home dashboard (storage cards and volume rows) and the floating pill navigation bar.
- `app/src/main/java/id/sysfile/manager/BrowseScreens.kt` — the folder browser: breadcrumb bar, list/grid, the labeled selection pill and the clipboard bar.
- `app/src/main/java/id/sysfile/manager/LibraryScreens.kt` — Recent files, per-category lists, Search, the storage Analysis screen, archive/payload content browsers, and the Trash screen.
- `app/src/main/java/id/sysfile/manager/Dialogs.kt` — all dialogs: new folder/rename, properties (checksums, folder statistics), sort, compress (format picker + ZIP password), the encrypted-ZIP password prompt, APK actions (with app info), the ZIP extract-choice dialog, and the folder picker.
- `app/src/main/java/id/sysfile/manager/SettingsScreens.kt` — Settings (flat Android-style rows) and the Supported Formats screen.
- `app/src/main/java/id/sysfile/manager/AppsScreen.kt` — the App Manager: installed-app listing (StorageStats sizes), filter/sort, and the copy/share APK actions.
- `app/src/main/java/id/sysfile/manager/FileOps.kt` — the file engine: directory listing and sorting, storage volumes, recursive copy/move, name de-duplication, search, folder statistics, MD5/SHA-256 checksums, APK archive info, ZIP compress/extract, the category/largest-files analysis, and `ArchiveOps` (TAR family, 7Z and RAR extraction, single-stream decompression, encrypted ZIP, archive content listing and browsing).
- `app/src/main/java/id/sysfile/manager/PayloadOps.kt` — the payload.bin extractor: a hand-written minimal protobuf reader that lists partitions, extracts all or one of them to `.img` files, and rejects delta payloads before writing anything.
- `app/src/main/java/id/sysfile/manager/TrashOps.kt` — the trash bin: an indexed `.SysFileTrash` folder with move-to-trash, restore (de-duplicated names), permanent delete, and empty-trash.
- `app/src/main/java/id/sysfile/manager/RootShell.kt` — root access via `su`: one-time grant check (the Magisk prompt fires on first entry), shell-based listing of `/`, and staging unreadable files into the cache so they can be opened.
- `app/src/main/java/id/sysfile/manager/BiometricGate.kt` — the biometric/PIN prompt used by locked folders.
- `app/src/main/java/id/sysfile/manager/Components.kt` — reusable UI pieces: section cards, file rows and grid cells (with lock badges), thumbnails (Coil), category icons and colours, and the storage card.
- `app/src/main/java/id/sysfile/manager/Viewers.kt` — the full-screen photo viewer (pinch zoom) and the text viewer/editor (syntax highlighting + HTML web preview).
- `app/src/main/java/id/sysfile/manager/Theme.kt` — the hand-written light/dark colour schemes and the Material 3 Expressive theme wrapper.
- `app/src/main/java/id/sysfile/manager/Strings.kt` — the two-language text table (Indonesian/English) for the whole interface.

## Notes

- File operations run on a background dispatcher with a progress dialog for long work, so the UI stays responsive.
- Deleting asks for confirmation and moves items to the trash bin first — permanent erasure only happens inside the Trash screen (or when deleting something that is already in the trash).
- Locked folders are an in-app privacy gate, not encryption: other file managers and a connected computer can still see the files.
- Root write operations are not implemented yet: inside the Root volume you can browse and open files, while copy/move/delete still go through the normal file APIs and fail where Android denies them.
- Pasting a file into a folder that already contains the same name never overwrites: the copy is saved as `name (1)`, `name (2)`, and so on.
