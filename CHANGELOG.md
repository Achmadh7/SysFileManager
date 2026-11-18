# Changelog

All notable changes to SysFileManager are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3.0] — 2026-11-18

### Added
- Full-glass interface (UX2, Home phase): glass storage cards and volume rows, a glass floating pill navigation, stronger aurora blobs (radius 1.15x, higher alphas) on every screen, and a cyan-to-violet gradient on the brand tile.
- Encrypted RAR support: password-protected RAR archives open with a password prompt, including batch extraction from multi-select.
- APK dialog now lists the app's permissions (expandable section, short names).
- Payload screen now reports the payload version, block size, and the per-partition operation breakdown (REPLACE / REPLACE_BZ / REPLACE_XZ / ZERO / DISCARD).
- Storage-permission dialog at launch: appears when All Files Access is not yet granted, with a "Later" option (reappears on the next launch until granted); closes automatically once access is granted.
- Dynamic colour (Material You) alignment: when enabled, the aurora blobs, brand tile, Properties header, and Analysis header follow the wallpaper colours; when off, everything looks exactly as before.

### Changed
- Settings is now a grouped list (Display / Files / Apps) instead of 2×2 quick tiles; all settings keep their behaviour.
- Theme and language now toggle with a single tap (no picker dialog); the "Follow System" theme option is gone.
- Bottom tab: the active pill now uses primaryContainer (follows dynamic colour) with no border.
- Photo and video viewers now swipe between same-type media in the folder, with an "N / M" counter; swipe is disabled while a photo is zoomed.
- Root/SD/USB volume rows show the free-space label as "tersisa" instead of "kosong" (Indonesian; English "free" unchanged).
- Category and Trash tiles, storage rows, and the StorageCard now use 28dp corners.
- Logo tile gradient changed from navy to cyan → violet; the "S" letter is now navy for contrast on the brighter gradient (dynamic-colour branch unchanged).

### Removed
- Tarball formats (.tar.gz / .tgz, .tar.bz2 / .tbz2, .tar.xz / .txz) are no longer recognised, extracted, created, or listed.
- Single-stream formats (.gz, .bz2, .xz) are no longer recognised or decompressed.
- The "Supported Formats" settings screen.
- "Follow System" theme option and the theme/language picker dialogs.

### Fixed
- Bottom bar no longer jumps on cold launch (opaque navy window background and navigation bar colour instead of transparent).
- Build failure: removed the invalid `androidx.compose.foundation.lazy.item` import (`item` is a member of `LazyListScope`, not a top-level function).

## [1.2.0]

- Previous release. This changelog starts with 1.3.0.
