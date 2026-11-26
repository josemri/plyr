<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://www.android.com)
[![Desktop](https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-blue.svg)](https://github.com/josemri/plyr/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org)

Stream any song, build playlists, share them with a QR code, sync across devices. Terminal-inspired, runs on Android, Windows, Linux and macOS. Open source, no ads, no telemetry. Features: YouTube streaming, local playlists, offline downloads, QR share, cross-device sync, desktop integration.

## android

<div align="center">
  <img src="screenshots/home_screen.jpeg" width="270" />
  <img src="screenshots/playlist_screen.jpeg" width="270" />
  <img src="screenshots/search_screen.jpeg" width="270" />
</div>

## desktop

<div align="center">
  <img src="screenshots/home_screen_desktop.jpeg" width="270" />
  <img src="screenshots/playlist_screen_desktop.jpeg" width="270" />
  <img src="screenshots/search_screen_desktop.jpeg" width="270" />
</div>

## roadmap

- [ ] **Web port** — browser client (proposal in `okf/proposals/web-port.md`).
- [ ] **Android Auto** — descriptor, library and playback implemented; pending device validation.
- [ ] **Architecture** — `PlayerViewModel` orchestration and `MusicService` owning the player (partially done).
- [ ] **Tests** — run instrumented tests on a device: QrCode, DataImporter.
- [x] **Build hygiene** — compiler warnings removed, Gradle/AGP/Kotlin migrated.

## license

[![GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

**_plyr** is Free Software: You can use, study, share, and improve it at will. Specifically you can redistribute and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl-3.0.en.html) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This project uses:

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), originally created by [Team NewPipe](https://github.com/TeamNewPipe), licensed under GPL-3.0.
- [ExoPlayer / Media3](https://developer.android.com/media/media3), [Room](https://developer.android.com/jetpack/androidx/releases/room), [Compose](https://developer.android.com/jetpack/compose), [OkHttp](https://square.github.io/okhttp/), [Coil](https://coil-kt.github.io/coil/) and [CameraX](https://developer.android.com/training/camerax), each under their respective licenses.

---

<div align="center">
  <b>Made with ♫ by <a href="https://github.com/josemri">josemri</a></b>
</div>
