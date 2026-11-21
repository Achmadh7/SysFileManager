# SysFileManager 

An Android file manager written in Kotlin with Jetpack Compose and Material 3 Expressive (the installed app is named **SysFile**), wearing the Biru Senja theme — a fixed dusk-blue palette with solid cards (dark background #101418, primary #9ACBFA; light #F7F9FF with primary #2E628C). Browse internal storage, SD cards, USB drives, and even the system root; copy, move, rename, delete, compress, and extract files; preview photos and text, and play videos in a built-in player; lock private folders behind your fingerprint; break into system-restricted folders (such as `Android/data`) through a one-time Storage Access Framework grant; manage installed apps; search the whole storage; and see what is taking up space — all on-device, no account, no ads.

## What's new in 1.3.0

- **Biru Senja theme** — the interface now uses a fixed dusk-blue palette with solid cards throughout (no glass, no aurora, no dynamic colour), and the bottom navigation is a floating surfaceContainer capsule whose primary pill hugs the active tab.
- **Settings, reorganised** — the quick tiles are gone; Settings is now a grouped list (Display, Files, Apps). Theme and language switch with a single tap, no picker dialog.
- **Specialist tools** — encrypted RAR archives open with a password prompt, the APK dialog lists the app's permissions, and the payload screen reports the payload version, block size, and per-partition operation counts.
- **Smoother first run** — a permission dialog appears at launch instead of a static card, and the bottom bar no longer jumps on cold start.

Full details in [CHANGELOG.md](CHANGELOG.md).

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
- **Home dashboard** — a brand header (the SysFile name next to its logo tile), then the **Storage** section: a hero card with a wavy progress bar carrying the used percentage, with the used-of-total figures and a status chip tinted by storage health (Healthy / Almost Full / Full); rows for the other volumes with usage bars and free space at a glance ("tersisa"); and three-column category tiles (violet Images, pink Video, teal Audio, blue Documents, green APKs, amber Archives, grey Other, red Trash). The floating capsule navigation (48dp, surfaceContainer) has a primary pill (#9ACBFA) that hugs the active tab's icon and label; inactive tabs show an outlined icon only.
- **Recent files** — a dedicated bottom-navigation tab groups the newest files across all volumes by their parent folder; tapping a group opens that folder.
- **About & specifications** — Settings → **About** opens a full screen: a live storage summary (tapping it jumps straight into the internal storage), a grouped **Specifications** page (app identity, archive engine, UI & storage — including the storage-engine line: All Files Access, SAF & Java NIO), and the developer credit.
- **Themes & languages** — Light / Dark in Settings (one tap toggles each, no picker), and an Indonesian/English interface switch (also one tap); all choices are remembered. There is no dynamic colour — the Biru Senja palette is fixed in both themes.
- **Official Material 3 Expressive** — `MaterialExpressiveTheme` with `MotionScheme.expressive()` and the morphing `LoadingIndicator`, extra-rounded cards, gentle 300 ms crossfades between tabs and category/archive screens, and spring motion (Material3 pinned to 1.5.0-alpha08 — the Expressive APIs were removed from the 1.4.0 stable release). The colour schemes are the hand-tuned Biru Senja palettes (dark background `#101418`, primary `#9ACBFA`; light `#F7F9FF` with primary `#2E628C`), with solid surfaceContainerHighest cards throughout; the logo is the **SF** mark with a diagonal slash.

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
| Version | 1.3.0 |
| Signing | Fixed keystore committed in this repo (`sysfile.p12`, PKCS12) — every CI build carries the same signature, so updates install in place |
| R8 | Minify + resource shrinking enabled on debug and release builds, using the default optimize rules plus keep rules for the archive libraries |

## Building the APK

**Without a computer (GitHub Actions):** open the **Actions** tab in this repo → workflow **Build APK** → **Run workflow** on branch `main` → download an artifact → install it on your phone. Each run publishes two artifacts with the same signature: **`app-debug-apk`** (the debug build) and **`SysFile Manager`** (the release build, named `SysFile Manager Release <version>.apk` from the app versionName). (Always start a fresh run — re-running an old failed run rebuilds that old commit.)

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
- `app/src/main/java/id/sysfile/manager/RootShell.kt` — root access via `su`: one-time grant check (the Magisk prompt fires on first entry), shell-based listing of `/`, and staging unreadable files into the cache so they can be opened.
- `app/src/main/java/id/sysfile/manager/BiometricGate.kt` — the biometric/PIN prompt used by locked folders.
- `app/src/main/java/id/sysfile/manager/Components.kt` — reusable UI pieces: section cards, file rows as cards and grid cells (with lock badges), thumbnails (Coil, plus real APK icons), category icons and colours, and the storage hero card with its health status chip.
- `app/src/main/java/id/sysfile/manager/Viewers.kt` — the full-screen photo viewer (pinch zoom, swipe between photos) and video viewer (swipe between videos), the text viewer/editor (syntax highlighting + HTML web preview), and the built-in video player with custom floating capsule controls.
- `app/src/main/java/id/sysfile/manager/Theme.kt` — the hand-written Biru Senja light/dark colour schemes and the Material 3 Expressive theme wrapper.
- `app/src/main/java/id/sysfile/manager/Strings.kt` — the two-language text table (Indonesian/English) for the whole interface.

## Notes

- File operations run on a background dispatcher with a progress dialog for long work, so the UI stays responsive.
- Deleting asks for confirmation and moves items to the trash bin first — permanent erasure only happens inside the Trash screen (or when deleting something that is already in the trash).
- Locked folders are an in-app privacy gate, not encryption: other file managers and a connected computer can still see the files.
- Root write operations are not implemented yet: inside the Root volume you can browse and open files, while copy/move/delete still go through the normal file APIs and fail where Android denies them.
- Pasting a file into a folder that already contains the same name never overwrites: the copy is saved as `name (1)`, `name (2)`, and so on.
