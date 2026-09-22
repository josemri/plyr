<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player for Android**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org)

Built this music player because I wanted something simple with a terminal aesthetic. Streams audio from YouTube (via NewPipe Extractor, no API key required), manages local playlists, and has gesture controls for quick actions. The UI is ASCII-inspired with monospace fonts everywhere.

## features

- **YouTube streaming** — search and play any song; no API keys needed (NewPipe Extractor under the hood).
- **Local playlists** — create, edit and reorder playlists; import a playlist from a **Spotify URL** (each track is resolved to its YouTube video); **Liked Songs** saved on swipe.
- **Scan & share** — share a playing track/playlist as QR or NFC tag; scan to open it.
- **Gesture controls** — configurable left/right **swipe** actions on a song, **shake** for transport controls, device **orientation** for volume/skip.
- **Recommendations feed** — community playlist recommendations synchronized via Supabase.
- **Background playback** — Media3 (ExoPlayer) foreground service with media notification controls.

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
│   ├── receivers/     # MediaButtonReceiver
│   ├── service/       # MusicService, YouTubeSearchManager, YouTubePlaylistCreator
│   ├── ui/            # Compose screens & components
│   ├── utils/         # Config, UrlParser, Translations, NfcReader, SpotifyImporter
│   └── viewmodel/     # PlayerViewModel, ImportViewModel
├── gradle/            # Dependencies (libs.versions.toml)
└── run.sh             # Build/install/test script
```

## permissions

```xml
NFC                       # Tag scanning / sharing of playlists
INTERNET                  # Stream music and fetch metadata
FOREGROUND_SERVICE        # Background playback
WAKE_LOCK                 # Keep playing when screen off
FOREGROUND_SERVICE_MEDIA_PLAYBACK
POST_NOTIFICATIONS        # Playback controls
CAMERA                    # QR code scanning (optional hardware)
```

`chmod 600 local.properties` and keep your keystore out of the repo — no secrets are tracked.

## roadmap

- [x] **Eliminar warnings** - Clean up compiler/linter warnings
  - [x] Borrar código muerto (`getLastfmApiKey`, `getAutomaticKeys`, `loadLikedSongs`, dominios obsoletos en `network_security_config`)
  - [x] Sustituir APIs deprecadas (`getPackageInfo(...,0)` → `getPackageInfoCompat`, `startService` → `startForegroundService`, `searchYouTubeIdsForPlaylist` eliminada)
  - [x] Limpiar `@Suppress`/`@SuppressLint` innecesarios (`formatTime` con `Locale.US`, `PlyrLoadingIndicator` con `modifier` primero; los `DEPRECATION` restantes son fallbacks API <33 legítimos)
- [ ] **Export data** - Export app data (playlists, history, etc.)
  - [ ] Serializar playlists y tracks a JSON
  - [ ] Compartir archivo vía `ACTION_SEND` / SAF
  - [ ] (Opcional) Exportar historial de búsqueda
- [ ] **Download lists** - Download playlists for offline use
  - [ ] Descargar audio de cada track y guardarlo localmente
  - [ ] Reproducir desde local cuando esté disponible
  - [ ] Indicador de progreso/estado por track y gestión de espacio
- [ ] **Android Auto** - Support for Android Auto interface
  - [ ] Declarar `automotive_app_desc.xml` y permisos de automoción
  - [ ] Exponer biblioteca (playlists, cola) con el `MediaSession` de Media3
  - [ ] Pantalla de reproducción y controles en el head unit
- [ ] **Drag & Drop** - Reorder songs in playlists with long press and drag
  - [ ] Gestos de long-press + arrastre en la lista de tracks
  - [ ] Persistir el nuevo orden (posición en `TrackEntity`/`TrackDao`)
- [x] **Fix timeout crash** - if load a song and wait for the url to timeout app will crash
  - [x] Timeout explícito en `YouTubeManager.getAudioUrl` (ahora `suspend` + `withTimeoutOrNull` 30s en IO)
  - [x] Fallo de extracción → saltar la pista / mostrar error, sin crash (ya gestionado en `PlayerViewModel`)
- [ ] **Apply report.md** - fix general issues reported
  - [ ] Bugs: `isValidAudioUrl` (B1), clave `"Player not available"` (B5), `loadingJob`/`loadingJobsActive` (B8-B9), `metadataCache` en Feed (B10), thumbnail `vi/undefined` (B16)
  - [ ] Seguridad: activar R8 (`isMinifyEnabled`), reducir logs de cuerpos en `SupabaseClient`
  - [ ] Rendimiento: `shutdown()`/`unbindAll` en `QrScannerDialog`, `key` en LazyLists de PlaylistScreen, `LazyColumn` en Feed
  - [ ] Pruebas: añadir tests instrumentados (import, QR/cámara, NFC)

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
