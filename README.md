<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player for Android**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org)

Built this music player because I wanted something simple with a terminal aesthetic. Streams audio from YouTube (via NewPipe Extractor, no API key required), manages local playlists, and has swipe actions for quick operations. The UI is ASCII-inspired with monospace fonts everywhere.

## features

- **YouTube streaming** — search and play any song (NewPipe Extractor).
- **Local playlists** — create, edit and reorder playlists; import a playlist from a **Spotify URL**
- **Scan & share** — share a playing track/playlist as QR or NFC tag; scan to open it.
- **Backup** — pick a folder once and a `plyr-sync.zip` inside it keeps itself up to date on its own .
- **Swipe actions** — configurable left/right swipe on a song: queue, like, playlist or share. 
- **Auto theme** by ambient light via the device light sensor.
- **Recommendations feed** — community playlist recommendations synchronized via Supabase.
- **Background playback** — Media3 (ExoPlayer) foreground service with media notification controls.
- **Media buttons** — play/pause, next, previous and 10 s skip from wireless headsets, Bluetooth and the notification.

## screenshots

<div align="center">
  <img src="screenshots/home_screen.jpeg" width="270" />
  <img src="screenshots/playlist_screen.jpeg" width="270" />
  <img src="screenshots/search_screen.jpeg" width="270" />
</div>

## build from source

There is a bash script in case you want to build from source; take a look at `./run.sh help` for all available commands.
```bash
git clone https://github.com/josemri/plyr.git
cd plyr
./run.sh build        # builds the debug APK
./run.sh run          # compiles, installs and launches the app on the device
./run.sh test         # runs the unit tests
./run.sh check        # code analysis: detekt (dead code/structure), lint and test coverage
```

The script mounts its own environment (Java, Android SDK) under `/tmp`, so nothing is written to your home directory. Run `./run.sh clean` to wipe everything it generates.

## project structure

```
plyr/
├── app/src/main/java/com/plyr/
│   ├── database/      # Room entities, DAOs & migrations
│   ├── model/         # Pure data models (ScanResult, AppTrack, Recommendation...)
│   ├── network/       # SupabaseClient, YouTubeManager, SimpleDownloader
│   ├── receivers/     # MediaButtonReceiver, MediaButtonCommand
│   ├── service/       # MusicService, YouTubeSearchManager, YouTubePlaylistCreator
│   ├── ui/            # Compose screens & components
│   ├── utils/         # Config, UrlParser, Translations, NfcReader, SpotifyImporter, DataSync, BackupFolder, CoverCache
│   └── viewmodel/     # PlayerViewModel, ImportViewModel
├── gradle/            # Dependencies (libs.versions.toml)
└── run.sh             # Build/install/test script
```

## permissions

```xml
NFC                       # Tag scanning / sharing of playlists
INTERNET                  # Stream music and fetch metadata
FOREGROUND_SERVICE        # Background playback
FOREGROUND_SERVICE_MEDIA_PLAYBACK
POST_NOTIFICATIONS        # Playback controls
CAMERA                    # QR code scanning (optional hardware)
```

`chmod 600 local.properties` and keep your keystore out of the repo — no secrets are tracked. See [`local.properties.example`](local.properties.example) for the optional overrides.

## roadmap

- [ ] **Android Auto** — support for the Android Auto interface
  - [ ] Declare `automotive_app_desc.xml` and the automotive permissions
  - [ ] Expose the library (playlists, queue) through the Media3 `MediaSession`
  - [ ] Playback screen and controls in the head unit
- [ ] **Architecture** — untangle UI, network, DB and business logic
  - [ ] Cover `PlayerViewModel` orchestration (Robolectric, or extract a pure orchestrator with injected dependencies)
  - [ ] Split `PlaylistScreen.kt` into a `PlaylistViewModel` + Compose state
  - [ ] Do the same for `ConfigScreen.kt` and `SearchScreen.kt`
  - [ ] Make `MusicService` own the player instead of projecting onto the `PlayerViewModel`'s `ExoPlayer`
- [ ] **Security**
  - [ ] Review the Supabase RLS policies (`groups`, `group_members`, `recommendations`, `automatic`) — not verifiable from the repo
  - [x] Document a `local.properties.example` (`SUPABASE_URL` / `SUPABASE_ANON_KEY`)
- [ ] **Tests**
  - [ ] Run the instrumented tests on a device (`./run.sh test device`): NfcReader, QrCode, DataImporter
- [ ] **Detekt / lint debt**
  - [x] Refactor the ~66 `complexity` findings frozen in `detekt-baseline.xml` and regenerate the baseline (down to 4: see below)
  - [ ] Shrink the remaining 4 structural findings frozen in `detekt-baseline.xml`: split the god-objects `Config` (31 functions), `PlaylistLocalRepository` (22) and `PlayerViewModel` (41 + `LargeClass`)
  - [x] Triage the 58 `UnusedResources` warnings (dynamic `drawable-nodpi/ascii_*.png`)

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
