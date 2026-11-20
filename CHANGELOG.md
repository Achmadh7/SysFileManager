# Changelog

All notable changes to SysFileManager are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3.0] — 2026-11-20

### Added
- Biru Senja theme (dusk blue, seed #3F5A73) with solid surfaceContainerHighest cards throughout; the bottom navigation is a floating capsule (HS1) whose primary pill hugs the active tab.
- Encrypted RAR support: password-protected RAR archives open with a password prompt, including batch extraction from multi-select.
- APK dialog now lists the app's permissions (expandable section, short names).
- Payload screen now reports the payload version, block size, and the per-partition operation breakdown (REPLACE / REPLACE_BZ / REPLACE_XZ / ZERO / DISCARD).
- Storage-permission dialog at launch: appears when All Files Access is not yet granted, with a "Later" option (reappears on the next launch until granted); closes automatically once access is granted.
- First-launch welcome screen (two-page pager): an intro page and an All Files Access permission page with a live status chip; shown once until finished or skipped, with all copy in English.
- In-app audio player (MediaPlayer) with a mini-player bar; the video player was rebuilt with custom Compose controls in a floating capsule (previous/play/next, slider, tap to show/hide, 3 s auto-hide).
- Markdown preview: `.md` files now render formatted in the built-in viewer, and `.srt` files open as text.
- EPUB reader: `.epub` books open chapter by chapter with a table of contents, previous/next navigation, and a chapter counter.
- Office documents open as read-only text: `.docx`, `.xlsx`, `.pptx`, and `.odt` (older `.doc` / `.xls` / `.ppt` still open in an external app).
- `.obb` files open as ZIP archives (view contents, extract, password prompt for encrypted ones).
- Photo viewer: EXIF details dialog (camera, date taken, dimensions, size, GPS location) from the new camera button in the toolbar; photos without EXIF data show a "no EXIF data" message.
- OTG/USB: plugged-in USB drives are detected immediately (no app restart), and a "USB not showing? Open via SAF" action on the home screen opens unmounted drives through the system folder picker.
- `.prop`, `.config`, and `.conf` files open as text.
- When a restricted folder (Android/data or Android/obb) looks empty but is readable, the empty state offers to open it through the system folder picker (SAF).
- Font preview: `.ttf` files open a preview screen showing the font family, style, size, path, and a sample rendered in that font; `.toml` files open as text.
- `.jar` files open as ZIP archives (view contents, extract, read text inside).
- `.log` files are now listed under the Documents category.
- The text viewer now supports eight more formats: `.env` files (including `.env.*` variants), `.tsv`, `.jsonl`, `.sql`, `.jsonc`, `.editorconfig`, `.gitignore`, and `.reg` — 32 formats in total.
- Text files inside a plain unencrypted `.zip` archive can now be edited and saved back into the archive; the other entries are left untouched and the archive list reloads after saving. (APK, OBB, and JAR files are excluded, as rewriting them would break their signature; encrypted ZIPs are excluded too.)

### Changed
- Settings is now a grouped list (Display / Files / Apps) instead of 2×2 quick tiles; all settings keep their behaviour.
- Theme and language now toggle with a single tap (no picker dialog); the "Follow System" theme option is gone.
- Bottom tab (HS1): a 60dp floating surfaceContainer capsule with 48dp side margins; its primary pill (#9ACBFA, 44dp tall) slides and resizes to hug the active tab's icon and label; inactive tabs show an outlined icon only.
- Target SDK 34 → 35 (Android 15): edge-to-edge enabled (enableEdgeToEdge); the floating bottom bar now respects the system navigation-bar inset.
- Photo and video viewers now swipe between same-type media in the folder, with an "N / M" counter; swipe is disabled while a photo is zoomed.
- Root/SD/USB volume rows show the free-space label as "tersisa" instead of "kosong" (Indonesian; English "free" unchanged).
- Storage rows and the StorageCard now use 28dp corners.
- Logo is now the 'SF' mark with a diagonal slash.
- Opening a text file inside an archive no longer extracts it next to the archive — it opens read-only from memory; other files open from the app-private cache.
- Video player controls and the top bar now animate smoothly (fade + slide) when shown or hidden, including the 3 s auto-hide.
- The long-press selection bar now animates in and out smoothly, matching the video controls.
- Properties dialog redesigned as a compact two-column grid (label left, value right) with file info and permissions in a single card.
- Extract Archive dialog redesigned as an action list: Extract Here, Extract to Custom Path, and Open Archive are full-width rows (the split button and its dropdown are gone).
- Category tiles on the home screen are now compact (KG1): 64dp tall tiles with 32dp icon circles, 16dp corners, and 8dp gaps; the Trash tile follows the same sizing.
- The release APK is now named "SysFileManager-1.3.0-Release.apk".
- The welcome screen is now a single screen (the two-page pager is gone).
- Category lists load faster: the file walk stops at the cap, recent scans are cached for 60 seconds, roots are scanned in parallel, and media (photos, videos, audio) loads from MediaStore.
- App Manager dialog redesigned as a compact list: smaller header icon and package text, tighter chips, and compact action rows separated by dividers.
- Welcome screen redesigned (WS12): the logo, title, and pitch sit directly on the background (the hero card is gone) with no small permission rationale text; the All Files Access card is slightly bigger (larger padding and folder icon).
- Switching between dark and light themes now fades smoothly over 300 ms (a snapshot of the old theme fades out over the new one) instead of changing instantly; repeated taps during the fade no longer compound the effect.
- About, App Manager, and App Specs now slide in from the right with a fade when opened, matching the Supported Formats screen.

### Removed
- Glass/aurora interface and Material You dynamic colour (replaced by the fixed Biru Senja theme and solid cards).
- Tarball formats (.tar.gz / .tgz, .tar.bz2 / .tbz2, .tar.xz / .txz) are no longer recognised, extracted, created, or listed.
- Single-stream formats (.gz, .bz2, .xz) are no longer recognised or decompressed.
- "Follow System" theme option and the theme/language picker dialogs.
- The "USB not showing? Open via SAF" hint on the home screen (live USB detection still works).
- The "Manage archives & documents" feature card on the welcome screen.

### Fixed
- Copy/move of a folder no longer reports success when a child file fails; a failed move no longer deletes the source, and a failed copy cleans up its partial destination.
- Zip-slip guard in all six extractors now requires the destination path plus a separator; a bad entry aborts the whole extraction. Extraction is bounded (10,000 entries, 2 GiB total output).
- HTML preview WebView: JavaScript and DOM storage off, file-URL access from pages disabled.
- Root listing no longer breaks on newlines in filenames; a failed ZIP extraction now removes the files it wrote.
- Removed 14 unused strings and the unused FileOps.sizeOf; clipboard paste no longer uses !!; runOp keeps the selection when an operation fails.
- Build hygiene: merged the duplicate buildFeatures blocks, replaced the deprecated kotlinOptions block with compilerOptions, and removed the dead requestLegacyExternalStorage flag.
- Bottom bar no longer jumps on cold launch (opaque navy window background and navigation bar colour instead of transparent).
- Build failure: removed the invalid `androidx.compose.foundation.lazy.item` import (`item` is a member of `LazyListScope`, not a top-level function).
- Pressing Back from a file opened inside a ZIP subfolder now returns to that same subfolder instead of the archive root.
- Fixed a crash when the selection bar's exit animation ran after the selection was cleared.
- Swiping between videos now works — the tap detector no longer swallows the swipe gesture.
- Dialogs (Properties, the working indicator, and the storage-access dialog) now follow the app theme instead of showing the default light (purple) Material scheme in dark mode.
- The search bar on the browse screen no longer sits far below the header.
- Media categories (Audio, Images, Video) no longer show empty when files are not in the MediaStore index (for example audio in `.nomedia` folders such as WhatsApp): an empty MediaStore result now falls back to a direct filesystem scan.
- Editing a text file between 1 MB and 2 MB no longer truncates it on save: the viewer now reads up to 2 MB, matching the open limit.

## [1.2.0]

- Previous release. This changelog starts with 1.3.0.
