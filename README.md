<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://www.android.com)
[![Desktop](https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-blue.svg)](https://github.com/josemri/plyr/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org)

stream any song, build playlists, share, sync across devices. Terminal-inspired, runs on Android, Windows, Linux and macOS. Open source, no ads, no telemetry.

## features

- **YouTube streaming** — search and play any song (NewPipe Extractor).
- **Local playlists** — create, edit and reorder them; import a playlist from a **Spotify URL**.
- **Offline downloads** — download tracks and play them without a connection.
- **Scan & share** — share a track or playlist.
- **Backup** — choose a folder once and the `plyr-sync.zip` inside keeps itself up to date.
- **Recommendations feed** — community playlist recommendations synchronized via Supabase.
- **Multiplatform** — Android 7.0+, Windows, Linux (`.deb`, `.rpm`, `.AppImage`) and macOS (`.dmg`).
- **run.sh** — one script to build, test, package and analyze the whole project.

## install

Download the installers from the [latest release](https://github.com/josemri/plyr/releases/latest):

- **Android** — `plyr.apk` (Android 7.0+).
- **Windows** — `.msi`.
- **macOS** — `.dmg` (Intel and Apple Silicon).
- **Linux** — `.deb` (Debian/Ubuntu), `.rpm` (Fedora/openSUSE) or `.AppImage` (any distro, including Arch).

Desktop installers bundle their own Java runtime. On Linux, playback uses the system `libvlc`

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

## web

Not available yet. The plan for the browser port — a Ktor server for YouTube extraction and audio proxying plus a Compose/Wasm client with browser storage — is specified in [web-port.md](web-port.md). Milestones H0–H7 are all pending.

## build from source

`run.sh` needs JDK 21 (it downloads its own copy) and sets up the Android SDK and Gradle caches under `/tmp/plyr-android`:

```bash
./run.sh setup                  # instala el entorno en /tmp
./run.sh build                  # APK debug de Android
./run.sh build release          # APK release
./run.sh run desktop            # compila y abre el escritorio
./run.sh test                   # tests unitarios de Android
./run.sh test desktop           # tests de escritorio
./run.sh test device            # tests instrumentados (requiere un móvil)
./run.sh check                  # detekt + lint + cobertura
```

## android permissions

```xml
NFC                       # Tag scanning / sharing of playlists
INTERNET                  # Stream music and fetch metadata
FOREGROUND_SERVICE        # Background playback
FOREGROUND_SERVICE_MEDIA_PLAYBACK
POST_NOTIFICATIONS        # Playback controls
CAMERA                    # QR code scanning (optional hardware)
```

## roadmap

- [ ] **Web port** — browser client with the server-side extractor and audio proxy described in [web-port.md](web-port.md).
- [ ] **Android Auto** — support for the Android Auto interface
  - [ ] Declare `automotive_app_desc.xml` and the automotive permissions
  - [ ] Expose the library (playlists, queue) through the Media3 `MediaSession`
  - [ ] Playback screen and controls in the head unit
- [ ] **Architecture** — untangle UI, network, DB and business logic
  - [ ] Cover `PlayerViewModel` orchestration (deferred: Robolectric, or extract a pure orchestrator with injected dependencies)
  - [x] Split `PlaylistScreen.kt` into a `PlaylistViewModel` + Compose state
  - [x] Split `SearchScreen.kt` into a `SearchViewModel` + Compose state
  - [x] Split `ConfigScreen.kt` into a `ConfigViewModel` + Compose state
  - [ ] Make `MusicService` own the player instead of projecting onto the `PlayerViewModel`'s `ExoPlayer` (deferred: device-only-verifiable lifecycle change; lower priority than the web port)
- [ ] **Tests**
  - [ ] Run the instrumented tests on a device (`./run.sh test device`): NfcReader, QrCode, DataImporter
- [ ] **Build hygiene** — remove compiler warnings and migrate deprecated Gradle, AGP and Kotlin plugin APIs; keep modules and dependencies up to date
- [x] **rm NFC** — remove all NFC functionality
- [ ] **fix/clean share**
- [ ] **album/playlist** create a separate carrusel for albums and playlists (ANDORID)
- [ ] **clean names** clean song names (for example rm all content after - since it tends to be the artist name)

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
