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
- **Swipe actions** — configurable left/right swipe on a song: add to queue, like, share. **Auto theme** by ambient light via the device light sensor.
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
WAKE_LOCK                 # Reserved: declared, but no wakelock is currently acquired
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
- [ ] **Backup data** - Keep app data backed up (playlists, history, etc.)
  - [x] Serializar playlists y tracks a JSON (`playlists.json` + portadas en `covers/`, dentro de un único `.zip`)
  - [x] Un único botón **sync** en ajustes: si ya hay carpeta y el `plyr-sync.zip` sigue ahí, sincroniza; si no, abre el selector de carpeta. Sustituye a los botones de exportar/importar sueltos
  - [x] Carpeta elegida una vez (vía SAF `OpenDocumentTree` con permiso persistente), en lugar de un ZIP nuevo por cada exportación
    - [x] Archivo de nombre fijo `plyr-sync.zip` que se reescribe solo (vale igual para una carpeta de Drive que para una local)
    - [x] Se vuelca al salir de la app (`onStop`) y con retardo de 30 s si el usuario no hace nada más, no en cada acción
    - [x] Escritura atómica: primero a `plyr-sync.zip.part` y renombrado al terminar, para que un corte a medias no destruya la copia
    - [x] Se omite la escritura si la huella SHA-256 del contenido no ha cambiado (`ExportDigest`)
    - [x] Caché de portadas remotas (`CoverCache`) para no volver a descargarlas en cada volcado
    - [x] Sincronización en los **dos sentidos**: antes de escribir, la copia anterior de la carpeta se fusiona en la app (`plyr-sync.zip` no se pisa nunca sin leerlo primero); instalar de cero y pulsar sync restaura el archivo en lugar de sobrescribirlo con una copia vacía
    - [x] **Borrado propagado (tombstones)**: borrar una lista queda registrado y viaja en `deletedPlaylistIds` del manifiesto; la lista no resucita al sincronizar ni en otros dispositivos, y el zip se regenera sin ella
  - [x] **Android Auto Backup** como red de seguridad: `backup_rules.xml` y `data_extraction_rules.xml` dejan de ser las plantillas vacías e incluyen base de datos, portadas y ajustes
  - [x] Restaurar desde la carpeta sin selector de archivos: el único botón **sync** lee y fusiona la copia anterior de la carpeta antes de reescribirla
  - [ ] (Opcional) Compartir el archivo vía `ACTION_SEND`
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
  - [x] Reproducción: `loadingJob`/`loadingJobsActive` (B8-B9) eliminados; la precarga era un bucle secuencial no cancelable cuyo flag se quedaba en `true` para siempre, así que la siguiente canción no se precargaba y la reproducción se detenía al terminar
  - [x] Reproducción: `STATE_ENDED` gestionado, ventana deslizante de actual + 2 siguientes y `generation` para descartar resoluciones obsoletas (`QueueIndex` + tests)
  - [x] Reproducción: `onPlayerError` salta a la siguiente en vez de parar la cola, e invalida la URL cacheada ante un 403/410 para re-resolver
  - [x] Reproducción: `isLoading` ya no puede quedarse en `true` deshabilitando los controles; `setCurrentPlaylist` ya no lanza con lista vacía; el índice ya no se deduce comparando IDs
  - [x] Rendimiento: caché de URLs de audio en memoria con TTL, para que saltar a la siguiente canción no dependa de una extracción nueva
  - [x] Auriculares: `MediaButtonReceiver` registrado en el manifiesto y conectado al reproductor (antes era código muerto)
  - [x] Bugs triviales del reporte (1ª tanda): B10 (constante duplicada `MAX_CAUSE_DEPTH`), B24 (literal `$ load_error` en el recorte de portada → mensaje real/clave `loading`), B30 (llamadas NFC sin `try/catch`), B38 (permiso `WAKE_LOCK` sin uso), B43 (aserción `mediaSession!!` en `MusicService`)
  - [ ] Bugs: `isValidAudioUrl` (B1), clave `"Player not available"` (B5), `metadataCache` en Feed (B10), thumbnail `vi/undefined` (B16)
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
