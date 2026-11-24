# SysFileManager 

An Android file manager written in Kotlin with Jetpack Compose and Material 3 Expressive (the installed app is named **SysFile**), featuring dual themes: **M3 Pixel Indigo** (tiered surface hierarchy, primary `#3B52B0` light / `#A6B8FF` dark) and **Nothing Matrix** (AMOLED `#000000` dark / `#F2F2EF` light with `#D71921` Signal Red accents). Browse internal storage, SD cards, USB drives, and even the system root; copy, move, rename, delete, compress, and extract files; preview photos and text, and play videos in a built-in player; lock private folders behind your fingerprint; break into system-restricted folders (such as `Android/data`) through a one-time Storage Access Framework grant; manage installed apps; search the whole storage; and see what is taking up space — all on-device, no account, no ads.

## What's new in 1.4.0

- **Dual Themes (M3 Pixel Indigo & Nothing Dot-Matrix)** — switch between tiered Material 3 Pixel Indigo and monochrome `#000000` / `#F2F2EF` Nothing Dot-Matrix with `#D71921` Signal Red accents and a matching Split-Pocket Nothing logo.
- **iOS Spokes Pull-to-Refresh** — smooth 12-spoke pull-to-refresh across Home, Browse, Recent, Category, and App Manager screens for both themes.
- **Peek Storage Carousel & Root Save** — multi-volume storage hero carousel (`HorizontalPager`) and root-backed text saving (`RootShell.writeFile`).

Previous release notes in [CHANGELOG.md](CHANGELOG.md).

## Features

- **Browse everything** — internal storage plus removable SD card and USB/OTG volumes, with list or grid view and a search bar scoped to the current folder (the full path shown as a line of text under it). A **Root (Sistem)** entry (enabled in Settings → Files) browses `/`: a limited Android-permitted view without root, and the full filesystem through the root shell when Magisk/su grants access (the prompt appears only when you actually enter it). Recently opened folders are cached per session, so going back into a folder is instant while fresh data loads behind it.
- **All file operations** — copy, cut, paste (with automatic name de-duplication), rename, delete, and new folders. A speed-dial **+** button creates a new folder or a new empty file. Long-press to start multi-select; a floating pill with micro-labelled actions appears (Copy, Extract — highlighted as its own segment — Compress to ZIP, Delete, and a More menu with Cut, Rename, Share, Properties, Select All, and Lock/Unlock Folder).
- **Trash bin** — deleting moves items into the trash instead of erasing them: open it from the **Trash tile in the Home categories** (next to Other) to restore items to their original place, delete them forever one by one, or empty the whole bin. Trashed files stay out of Recent, category lists, search, and storage analysis.
- **Previews** — photos open in a full-screen viewer with pinch zoom and swipe between photos in the same folder (with a "N / M" counter); **videos play in a built-in player** with custom floating capsule controls (previous/play/next, slider, tap to show or hide, 3 s auto-hide) and swipe between videos the same way; **audio plays in-app too**, with a mini-player bar; text files open in a built-in viewer/editor with syntax highlighting for code and markup, and `.html`/`.htm` files get a **web preview mode** (WebView, relative images/CSS resolve, edits included); PDFs and everything else hand over to the right installed app. Thumbnails are shown for photos and videos in the file list, and APK files show their **real app icon**.
- **Properties that tell more** — a grouped-card dialog with a navy header: an identity card (parent folder, type, exact size in units and bytes, modification date; folders additionally report recursive total size, file and subfolder counts, and the largest item inside), a **Permissions & Ownership** card read from the native stat (mode string with octal value, owner, group), a **built-in permissions editor** (read/write/execute checkboxes for owner, group, and others, plus setuid/setgid/sticky, with a live preview — applied through `chmod` where the filesystem allows it), an expandable details card (full path with a copy button, access/change times, inode, link count), and a checksum view that computes **MD5 + SHA-256** automatically (one streaming pass, with copy buttons).
- **Locked folders** — lock any folder from the selection menu: it gets a lock badge, and opening it (from browse, favorites, search, or Recent) requires your **fingerprint or lock-screen PIN** via the system biometric prompt; one unlock lasts for the session. The contents of locked folders are hidden from Recent, category lists, search, and storage analysis. This is an in-app gate only — files are not moved or encrypted.
- **Restricted folders via SAF** — some folders (notably `Android/data` on Android 13+) refuse the normal file path even with All files access. SysFile shows a **Grant Access** button there instead: the system picker opens on that folder, you allow it once, and the app remembers the grant. The folder then opens in a dedicated SAF browser — explore it, open files, or **copy files out** to normal storage. SAF trees stay out of search, Recent, and storage analysis (SAF listing is slow); everything else keeps using the fast direct path.
- **App manager** — Settings → **App Manager** lists every installed app with its icon, version, and size (total including data and cache when Usage Access is granted, otherwise the APK size), with All/User/System filters and size/name sorting. Per app: open it, **copy its APK** to a folder of your choice, **share its APK**, or jump to Android's app-info page.
- **Search** — recursive name search with category filter chips, reachable from the search button in the Home top bar or from any folder's search bar; results open in place. Opening a file from a category always returns to that category on Back, never detours into the file's folder.
- **Archive powerhouse** — create and extract **ZIP** (optionally protected with an AES password) and **TAR**, and extract **7Z** and **RAR** (including password-protected RAR, which asks for its password first) — all in place, with a path-slip guard on every extractor. **APK/APKS/XAPK/AAB** files offer inspect/extract/install (the dialog shows the app's real icon, name, version, and package name first, plus an expandable list of the app's permissions, with a coloured action card per choice), and a built-in parser extracts full (non-delta) OTA **payload.bin** files into per-partition `.img` files — the payload screen reports the payload version, block size, and how many operations of each type every partition carries; tapping any `.bin` file checks whether it is a payload and falls back to a normal open when it is not. Tapping an archive opens its contents in a browser screen first — folders inside the archive can be entered, a single file can be extracted and opened, or everything can be extracted at once. Tapping an unencrypted ZIP first asks where to extract: into a new folder named after the archive, browse the contents first, or extract to a **custom folder** picked in a built-in folder browser. The compress dialog offers ZIP and TAR.
- **Hidden files** — dotfiles can be shown or hidden from the browser menu or Settings.
- **Favorites** — star any folder from the browser's overflow menu; the star is remembered across launches.
- **Storage analysis** — per-volume scan with sizes per category (images, videos, audio, documents, apps, archives, other), file counts, and the largest files, each category as its own card with a proportional bar.
- **Home dashboard** — a brand header (the SysFile name next to its Split-Pocket logo tile), then the **Storage** section: a **Peek Storage Hero Carousel** (`HorizontalPager`) with a `SplitGapSolidProgressBar` / `NothingMatrixProgressBar` carrying the used percentage, used-of-total figures, and a status chip tinted by storage health (Healthy / Almost Full / Full); volume rows with usage bars and free space ("tersisa"); and three-column category tiles (violet Images, pink Video, teal Audio, blue Documents, green APKs, amber Archives, grey Other, red Trash). The floating capsule navigation hugs the active tab (`#3B52B0` / `#A6B8FF` in M3 Pixel Indigo; white pill + Signal Red accents in Nothing Matrix), and all main lists support **iOS Spokes Pull-to-Refresh**.
- **Recent files** — a dedicated bottom-navigation tab groups the newest files across all volumes by their parent folder; tapping a group opens that folder.
- **About & specifications** — Settings → **About** opens a full screen with Bento quick-tiles: live storage summary (jumping straight into internal storage), Supported Formats shortcut, grouped **Specifications** page (`id.sysfile.manager`, target Android 15 / API 35, archive engines, UI & storage), and developer credit.
- **Themes & languages** — Light / Dark in Settings (one tap toggles each, no picker), a two-column **UI Style selector** (`M3 Pixel Indigo` vs `Nothing Matrix`), and a one-tap Indonesian/English interface switch; all choices are remembered.
- **Official Material 3 Expressive & Dual Themes** — `MaterialExpressiveTheme` with `MotionScheme.expressive()` and the morphing `LoadingIndicator`, extra-rounded cards, gentle 300 ms crossfades, and spring motion (Material3 pinned to 1.5.0-alpha08 — the Expressive APIs were removed from the 1.4.0 stable release). Switch between **M3 Pixel Indigo** and **Nothing Matrix**; the logo adapts automatically (Split-Pocket Indigo vs Nothing Monochrome + Signal Red).

## Permissions

SysFileManager needs **All files access** (Android 11+) so it can work outside its own sandbox: the first launch shows a dialog that sends you to the system settings screen where the permission is granted (tap **Later** and the dialog returns on the next launch). On Android 8–10 it asks for the classic storage permission instead. Some folders (notably `Android/data` on newer Android versions) still refuse the direct file path even with this permission — there SysFileManager offers a one-time **Storage Access Framework** grant through the system picker instead of failing silently (see *Restricted folders via SAF* above). The App Manager additionally uses package-visibility access to list installed apps, and reads total app sizes only when you grant **Usage Access** in the system settings (a shortcut card inside the App Manager screen takes you there).

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose (BOM 2025.10.00), Material3 1.5.0-alpha08 |
| Thumbnails | Coil 2.7.0 (image + video frame decoder) |
| Archives | Apache Commons Compress 1.27.1, Tukaani XZ 1.10, zip4j 2.11.5, Junrar 7.5.5 |
| Biometrics | AndroidX Biometric 1.1.0 (fingerprint / device credential) |
| Restricted folders | AndroidX DocumentFile 1.0.1 (Storage Access Framework) |
| Build | AGP 8.11.1, Gradle 8.13, JDK 17 |
| Android | minSdk 26 (Android 8.0), targetSdk 35, compileSdk 36 |
| Package | `id.sysfile.manager` |
| Version | 1.4.0 |
| Signing | Reads `SYSFILE_STORE_FILE` / password / alias env vars (wired to GitHub Secrets for release; `SYSFILE_STRICT_SIGNING=true` enforces them), falling back to the committed `sysfile.p12` (PKCS12) keystore for in-place updates |
| R8 | Minify + resource shrinking enabled on debug and release builds, using the default optimize rules plus keep rules for the archive libraries |

## Building the APK

**Without a computer (GitHub Actions):** open the **Actions** tab in this repo → workflow **Build APK** → **Run workflow** on branch `main` → download an artifact → install it on your phone. Each run publishes two artifacts with the same signature: **`app-debug-apk`** (the debug build) and **`Sysfile Manager`** (the release build, named `Sysfile Manager <version>-Release.apk` from the app versionName). (Always start a fresh run — re-running an old failed run rebuilds that old commit.)

**With Android Studio:** open this repo folder, wait for Gradle Sync, then Build APK. (The CI workflow uses Gradle 8.13 and installs Android SDK 36; the same setup works locally.)

**From the command line:** the repo ships the Gradle wrapper pinned to Gradle 8.13, so `./gradlew assembleDebug` or `./gradlew assembleRelease` (on Windows: `gradlew.bat ...`) works without a system Gradle install; outputs land in `app/build/outputs/apk/`. Set `JAVA_HOME` to a JDK 17 and `ANDROID_HOME` to your SDK first. Unit tests run with `./gradlew :app:testDebugUnitTest` (the CI workflow runs them before building).

**Updating the app:** all builds share the one fixed signature, so a new APK installs directly over the previous one and settings (theme, language, favorites, locked folders) are preserved.

## Project structure

- `app/src/main/java/id/sysfile/manager/MainActivity.kt` — the entry point (`FragmentActivity`) and the `SysFileApp` shell: navigation state (including the return-to-category overlay handling), the locked-folder biometric gate, file-open dispatch, the background operation runner, and dialog dispatch.
- `app/src/main/java/id/sysfile/manager/HomeScreens.kt` — the Home dashboard (brand header, Storage section with the hero card and volume rows, category tiles) and the floating capsule navigation bar (HS1).
- `app/src/main/java/id/sysfile/manager/BrowseScreens.kt` — the folder browser: search header bar, card file rows and grid cells, the micro-labelled selection pill, the clipboard bar, and the speed-dial create FAB.
- `app/src/main/java/id/sysfile/manager/LibraryScreens.kt` — Recent files (grouped by folder), per-category lists, Search, the storage Analysis screen, archive/payload content browsers, and the Trash screen.
- `app/src/main/java/id/sysfile/manager/Dialogs.kt` — all dialogs: new folder/file and rename, properties (grouped cards with permissions & ownership, the chmod permissions editor, extended stat details, and checksums), sort, compress (ZIP/TAR picker), the encrypted-ZIP and encrypted-RAR password prompts, the APK and ZIP action dialogs (coloured action cards), and the folder picker.
- `app/src/main/java/id/sysfile/manager/SettingsScreens.kt` — Settings as a grouped list (Display / Files / Apps rows with switches and one-tap toggles), the About screen, and the grouped Specifications screen.
- `app/src/main/java/id/sysfile/manager/SafScreens.kt` — SAF access for system-restricted folders: tree-URI helpers, the persisted grant mapping, and the SAF browser (browse, open, copy out).
- `app/src/main/java/id/sysfile/manager/AppsScreen.kt` — the App Manager: installed-app listing (StorageStats sizes), filter/sort, and the copy/share APK actions.
- `app/src/main/java/id/sysfile/manager/FileOps.kt` — the file engine: directory listing and sorting, storage volumes, recursive copy/move, name de-duplication, search, folder statistics, MD5/SHA-256 checksums, APK archive info and icons, ZIP compress/extract, the category/largest-files analysis, and `ArchiveOps` (ZIP/TAR, 7Z and RAR extraction, encrypted ZIP and encrypted RAR, archive content listing and browsing).
- `app/src/main/java/id/sysfile/manager/PayloadOps.kt` — the payload.bin extractor: a hand-written minimal protobuf reader that lists partitions, extracts all or one of them to `.img` files, and rejects delta payloads before writing anything.
- `app/src/main/java/id/sysfile/manager/TrashOps.kt` — the trash bin: an indexed `.SysFileTrash` folder with move-to-trash, restore (de-duplicated names), permanent delete, and empty-trash.
- `app/src/main/java/id/sysfile/manager/RootShell.kt` — root access via `su`: one-time grant check (`synchronized(rootLock)`), shell-based listing of `/` & `/mnt/media_rw` fallback, staging unreadable files to cache, and saving edited text files via `writeFile` while keeping existing file permissions.
- `app/src/main/java/id/sysfile/manager/BiometricGate.kt` — device-lock / biometric availability check (`canAuthenticate`) and prompt used by locked folders.
- `app/src/main/java/id/sysfile/manager/Components.kt` — reusable UI pieces: section cards, file rows and grid cells (with lock badges), Coil thumbnails + real APK icons, category icons/colours, `SplitGapSolidProgressBar`, and `IosSpokesPullToRefreshBox` / `IosSpokesSpinner`.
- `app/src/main/java/id/sysfile/manager/Viewers.kt` — the full-screen photo viewer (pinch zoom, swipe between photos) and video viewer (swipe between videos), the text viewer/editor (syntax highlighting + HTML web preview), and the built-in video player with custom floating capsule controls.
- `app/src/main/java/id/sysfile/manager/Theme.kt` — M3 Pixel Indigo (`SenjaLightScheme` / `SenjaDarkScheme`) and Nothing Dot-Matrix (`NothingLightScheme` / `NothingDarkScheme`) palettes, `DotMatrixBackground`, `nothingBorder`, `NothingMatrixProgressBar`, and the `MaterialExpressiveTheme` wrapper.
- `app/src/main/java/id/sysfile/manager/Strings.kt` — the two-language text table (Indonesian/English) for the whole interface.

## Notes

- File operations run on a background dispatcher with a progress dialog for long work, so the UI stays responsive.
- Deleting asks for confirmation and moves items to the trash bin first — permanent erasure only happens inside the Trash screen (or when deleting something that is already in the trash).
- Locked folders are an in-app privacy gate, not encryption: other file managers and a connected computer can still see the files.
- Root text/config editing uses `RootShell.writeFile` (preserving permissions on existing files), while copy/move/delete inside `/` still go through normal Android APIs where the OS allows.
- Pasting a file into a folder that already contains the same name never overwrites: the copy is saved as `name (1)`, `name (2)`, and so on.
