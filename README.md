<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player for Android**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org)

Built this music player because I wanted something simple with a terminal aesthetic. Streams audio from YouTube (via NewPipe Extractor, no API key required), manages local playlists, and has swipe actions for quick operations. The UI is ASCII-inspired with monospace fonts everywhere.

## features

- **YouTube streaming** — search and play any song; no API keys needed (NewPipe Extractor under the hood).
- **Local playlists** — create, edit and reorder playlists; import a playlist from a **Spotify URL** (each track is resolved to its YouTube video); **Liked Songs** saved on swipe.
- **Scan & share** — share a playing track/playlist as QR or NFC tag; scan to open it.
- **Backup** — pick a folder once and a `plyr-sync.zip` inside it keeps itself up to date on its own (a single `.zip` with `playlists.json` plus the artwork in `covers/`). It works the same in Google Drive as on the device. Sync goes **both ways**: before writing, the previous copy is merged back into the app (so a fresh install restores everything instead of overwriting a good archive with an empty one), and deletions travel too — a playlist you remove stays removed on any device. Settings has a single **sync** button: it syncs if the file is already there, or opens the folder picker when there is nothing to sync to yet.
- **Swipe actions** — configurable left/right swipe on a song: add to queue, like, add to a playlist or share. **Auto theme** by ambient light via the device light sensor.
- **Recommendations feed** — community playlist recommendations synchronized via Supabase.
- **Background playback** — Media3 (ExoPlayer) foreground service with media notification controls. Playback continues with the screen off and stops when the app is closed from recents.
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

`chmod 600 local.properties` and keep your keystore out of the repo — no secrets are tracked.

## roadmap

Everything already fixed is documented in [`report.md`](report.md). This section only lists what is left: **56 bugs resolved, 4 open** (`B49`–`B51`, `B53`), plus the feature requests in report §11.

- [ ] **Share** — the URL that goes into the QR / the NFC tag / the recommendation feed is wrong for a **playlist** (`B53`)
  - [x] ~~A track shares its **YouTube** video, not the row id it has in the database~~ — done, `ShareUrlPolicy` decides the URL and the real `youtubeVideoId` wins over a pre-built one
  - [ ] A playlist imported from **Spotify** shares `open.spotify.com/playlist/<id>`; the origin is stored instead of inferred from the `youtube_` prefix
  - [ ] `liked_songs` and locally created lists don't offer a share at all
  - [x] ~~The share dialog says so instead of opening blank when there is no video to share~~ — done
- [ ] **Playback** — previous/next from the notification don't go through the queue (`B49`, `B50`)
  - [ ] The media session routes `seekToNext` / `seekToPrevious` to `QueueIndex` instead of letting ExoPlayer move inside its sliding window
  - [ ] The notification is rebuilt on timeline changes too, so the **next** button doesn't vanish after two skips in a row
  - [ ] Going back doesn't leave the prepared window (today it re-resolves over the network with the controls disabled) and never skips *forward* when the previous track fails to resolve
  - [x] ~~The notification stops lying when nothing is playing~~ — done, it no longer sits there saying **"Plyr / Reproducendo"**
- [ ] **Sync** — a liked track you deleted comes back from the zip (`B51`)
  - [ ] Decide the policy: tombstones per liked track, or a `liked_songs` digest, so a local deletion wins without breaking the restore-a-fresh-install case
- [ ] **Wanted behaviour / small features**
  - [ ] `liked` only shows up as a playlist while it has songs (filter it out of the two listings, don't drop the row)
  - [x] ~~**add to list** in the `*` menu~~ — done, the `*` menu now opens the existing playlist picker
- [ ] **Apply report.md** — remaining refactors and polish from the audit
  - [ ] Move the UI literals that don't go through `Translations` to real keys (report §7.1)
  - [ ] Reduce the request/response body logs in `SupabaseClient` (S7: PII in logcat)
  - [ ] Performance: `key` in the PlaylistScreen lazy lists, `LazyColumn` in Feed
  - [ ] Instrumented tests (import, QR/camera, NFC)
  - [ ] `metadataCache` in Feed (O(n²) read-modify-write)
- [ ] **Download lists** — download playlists for offline use
  - [ ] Download the audio of each track and store it locally
  - [ ] Play from local when available
  - [ ] Per-track progress/status and storage management
- [ ] **Android Auto** — support for the Android Auto interface
  - [ ] Declare `automotive_app_desc.xml` and the automotive permissions
  - [ ] Expose the library (playlists, queue) through the Media3 `MediaSession`
  - [ ] Playback screen and controls in the head unit
- [ ] **Drag & Drop** — reorder songs in playlists with long press and drag
  - [ ] Long-press + drag gestures in the track list
  - [ ] Persist the new order (`TrackEntity`/`TrackDao`)
- [ ] **Backup data (optional extras)**
  - [ ] Share the `plyr-sync.zip` via `ACTION_SEND`
  - [ ] Export search history

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
