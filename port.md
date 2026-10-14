# Análisis de portabilidad de **plyr** a escritorio (Windows / Linux / macOS)

**Fecha:** 2026-10-09 · **Actualizado:** 2026-10-14
**Alcance analizado:** `app/src/main/java/com/plyr` (83 archivos Kotlin, ~16.500 líneas de producción) + 35 archivos de test JVM (~5.400 líneas), Gradle, `AndroidManifest.xml`.
**Objetivo del documento:** dejar claro el lenguaje/stack elegido, el **roadmap por hitos** (los `M0`–`M19` ya están completados, §6) y el **backlog de paridad pendiente** con Android (§6.4).

---

## 1. Qué es plyr (inventario funcional)

Reproductor de música minimalista con estética de terminal (fuente monoespaciada, ASCII) para Android 7.0+.

| # | Funcionalidad | Implementación actual | Portabilidad |
|---|---|---|---|
| F1 | Streaming de YouTube (búsqueda + audio) | NewPipeExtractor + OkHttp | **Alta** (Java puro) |
| F2 | Playlists locales (crear/editar/reordenar) | Room (SQLite) | Media (reescribir capa BD) |
| F3 | Importar playlist desde URL de Spotify | `SpotifyImporter` + OkHttp + WebView-like | Alta |
| F4 | Reproducción en segundo plano | Media3 ExoPlayer + foreground service | Baja (sustituir motor + servicio) |
| F5 | Controles de medios (auriculares/BT/notificación) | Media3 MediaSession + MediaButtonReceiver | Baja → integración nativa |
| F6 | Compartir pista/playlist por QR / NFC | ZXing + NfcAdapter | QR sí; NFC no |
| F7 | Escanear QR de otra pista | CameraX + ML | Sustituir (pegar URL / abrir archivo) |
| F8 | Backup automático a carpeta | Storage Access Framework (`OpenDocumentTree`) | Sustituir por `java.nio` |
| F9 | Tema automático por luz ambiente | `SensorManager` (TYPE_LIGHT) | Descartar (o seguir tema del sistema) |
| F10 | Feed de recomendaciones comunitarias | Supabase REST (`HttpURLConnection` + `org.json`) | **Alta** |
| F11 | Drag & drop / swipe actions | Compose gestures | Alta (Compose soporta drag) |
| F12 | Multi-idioma (es/en/ca/ja) | `Translations` propio (mapa en código) | **Alta** (Kotlin puro) |
| F13 | Auto‑update / update checker | `UpdateChecker` | Adaptar (repo/URL) |

### 1.1 Funciones de Android **aún no presentes** en escritorio

Detectadas al comparar `:app` con `:desktop` después del cierre de M15. Las que tienen justificación (NFC, cámara, sensor de luz, swipe, notificaciones nativas) se listan al final del backlog (§6.4).

| # | Funcionalidad | Implementación Android | Estado en escritorio | Prioridad |
|---|---|---|---|---|
| F14 | Descarga de audio offline y reproducción sin conexión | `AudioDownloader` + `DownloadedAudioStore` + `DownloadPlan` (OkHttp + SAF/`filesDir`) | **Presente (M16)**: `AudioDownloader` + `DownloadedAudioStore` + `DownloadController` (OkHttp + `java.nio`); el resolutor prefiere el fichero local | **Alta** |
| F15 | UI de almacenamiento offline (descargar/limpiar y resumen) | `PlaylistScreen` + `PlaylistDownloadBar` + `OfflineStorageDialog` | **Presente (M17)**: `DownloadStatusBar` + `OfflineStorageDialog` + botón descargar/limpiar e indicador por pista (`PlaylistDetailView`/`PlaylistTrackItem`); sección de almacén en `ConfigScreen` | Media |
| F16 | Carrusel de playlists en Home | `HomeScreen` (`HomePlaylistCarousel`) | **Presente (M18)**: `HomePlaylistCarousel`/`HomePlaylistItem`/`HomePlaylistCover` en `HomeScreen`, con las listas visibles y clic para abrir el detalle | Media |
| F17 | Búsqueda y detalle de *playlists* de YouTube | `YouTubeSearchManager.searchYouTubeAll` + `YouTubePlaylistDetailView` | **Presente (M19)**: `YouTubeManager.searchAll`/`getPlaylistInfo`/`getPlaylistVideos` en `:core`, sección plegable de listas en `SearchScreen` y `YouTubePlaylistDetailView` (reproducir todo y guardar como lista local) | **Alta** |
| F18 | Reproducción aleatoria (`<rnd>`) en playlists | `PlaylistScreen` + `PlaylistViewModel.startRandomizing` | Sin shuffle (MPRIS `Shuffle` es de solo lectura y falso) | **Alta** |
| F19 | Historial de búsqueda (no es hueco real) | `SearchViewModel` lo persiste, pero **`getAllSearches()` nunca se llama**: Android tampoco lo muestra | `SearchHistoryRepository` persiste igual; la UI no lo usa en ninguno de los dos | — |
| F20 | Paginación / «cargar más» en búsqueda | `SearchViewModel.onLoadMore` | Una sola consulta, sin paginar | Media |
| F21 | Crear playlist buscando en YouTube | `CreatePlaylistScreen` | `CreatePlaylistView` es solo título+descripción | Media |
| F22 | Acciones por pista (like / añadir a cola / añadir a playlist) | `SongListItem` (menú contextual) | Solo compartir `⤴` | Baja |
| F23 | Metadatos del vídeo en el feed + reproducir desde el feed | `FeedScreen` + `MediaMetadataExtractor` | Feed sin metadatos ni clic-para-reproducir | **Alta** |
| F24 | «Compartir app» por QR en Config | `ShareAppSection` + `ShareType.APP` | Ausente | Baja |
| F25 | Recomendar al feed desde el diálogo de compartir | `QRDialog.RecommendButton` + `requestRecommendation` | `QrShareDialog` lo omite explícitamente («sin… recomendaciones a grupos») | Media |
| F26 | Editar una playlist añadiendo pistas buscando en YouTube | `PlaylistScreen` (modo edición: buscador + `+`/`x`) | El modo edición solo tiene título/descripción/portada y borrar/reordenar | Media |

> **Nota:** las claves de traducción de F14/F22/F24 (`storage_*`, `delete_download`, `like`, `add_to_queue`, `share_me`, `add_to_playlist`) ya están en el catálogo compartido `:core` (`TranslationCatalog`). El escritorio ya consume `add_to_playlist` y, desde M17, `storage_*` (almacén offline); `delete_download` y el resto de F22/F24 siguen listos para usarse cuando se implementen M22/M24. M17 añadió además las claves `download`, `download_clean`, `download_downloading`, `download_done`, `download_partial`, `download_already` y `download_cancelled`; M19 añadió `youtube_playlists` y `saved`.

> **Nota:** `MediaMetadataExtractor`, `ImportManifest`, `QrCodec`, `SpotifyImport` y `CoverCacheKey` ya viven en `:core` (`jvmSharedMain`), así que F23 es más barato de lo que sugiere su kdoc (que aún dice «no portado»).

### 1.2 Lenguaje y stack seleccionados

- **Lenguaje:** seguir en **Kotlin** (no reescribir). Se reutiliza ~65% del código y el 100% de los 455 tests.
- **Compartición de lógica:** **Kotlin Multiplatform** (módulo `core`, targets `jvm` + `android`).
- **UI:** **Compose Multiplatform** (mismo código en Windows, Linux y macOS).
- **Base de datos:** **SQLDelight** en lugar de Room.
- **Audio:** **VLCJ / libVLC** en lugar de ExoPlayer/Media3.
- **Preferencias:** `multiplatform-settings` / `java.util.prefs` en lugar de `SharedPreferences`.
- **Portadas:** **Coil 3** (soporte Compose Multiplatform).
- **Archivos/tema/idioma:** `java.nio` y rutas por SO en lugar de SAF/sensores.
- **Integración nativa:** **MPRIS** (Linux), **SMTC** (Windows), **Now Playing** (macOS) tras una interfaz común.
- **Empaquetado:** `jpackage` (o Conveyor).

### 1.3 ¿Es multiplataforma?

**Sí, Windows, Linux y macOS** desde el mismo código. Solo cambian las capas nativas:

| Pieza | Windows | Linux | macOS |
|---|---|---|---|
| Compose Desktop (UI) | ✅ | ✅ | ✅ |
| JVM / coroutines / SQLDelight | ✅ | ✅ | ✅ |
| Motor de audio (VLCJ + libVLC) | ✅ | ✅ | ✅ |
| Teclas multimedia / controles del SO | **SMTC** | **MPRIS** | **Now Playing** |
| Bandeja del sistema | ✅ | ✅ | ✅ |
| Rutas de datos | `%APPDATA%` | `~/.config`, `~/.local/share` | `~/Library/Application Support` |
| Empaquetado | `.msi` / `.exe` | `.deb`, `.rpm`, AppImage, Flatpak | `.dmg` / `.pkg` |

Se implementa una sola vez el núcleo y la UI; las tres integraciones nativas quedan aisladas detrás de `DesktopMediaIntegration`. Se empieza por **Linux (MPRIS)** y Windows/macOS se añaden después sin tocar el resto.

---

## 2. Mapa de acoplamiento con Android

### 2.1 Reparto por capa

| Capa | Archivos | Acoplamiento Android | Reutilización estimada |
|---|---|---|---|
| `model/` | 4 | **Ninguno** | **100%** |
| `utils/` (puros) | 10 | **Ninguno** | **100%** |
| `viewmodel/` (helpers) | 5 | **Ninguno** | **100%** |
| `service/` (helpers) | 4 | **Ninguno** | **100%** |
| `network/` | 4 | Solo `android.util.Log` | ~95% |
| `database/` | 9 | Room + `Context` | ~30% (reescribir DAOs) |
| `viewmodel/PlayerViewModel` | 1 | ExoPlayer + LiveData + `Application` | ~40% (lógica sí, motor no) |
| `service/MusicService` | 1 | Notificación + MediaSession + `Service` | ~10% |
| `receivers/` | 2 | `BroadcastReceiver` | ~20% |
| `ui/` | 42 | Compose Android + `Context` + `LiveData` + `Intent` | ~55–70% |
| `utils/` (Android) | 9 | SAF, NFC, sensores, `SharedPreferences` | ~30% |

### 2.2 Archivos reutilizables **sin cambios** (sin import de Android)

Estos son la joya del port. No requieren ni una línea de cambio para compilar en JVM desktop:

```
model/                    AudioItem, Recommendation, ScanResult, Group/GroupMember, AppModels
viewmodel/                QueueIndex, WindowState, LoadingState, PendingSkips, IdlePlayback
service/                  CoverCropMath, PlaybackNotificationState, NotificationRefreshPolicy, YouTubePlaylistCreator
utils/                    UrlParser, ImportManifest, ExportManifest, ExportDigest, ImportArchive,
                          LikedSongsMerge, MediaMetadataExtractor, NfcScanEvent, NfcTagEvent, PlaylistSource
network/                  YouTubeManager, NewPipeHolder
```

### 2.3 Puntos de fricción reales (por orden de importancia)

1. **`PlayerViewModel.kt` (1.124 líneas) está pegado a ExoPlayer.** Toda la lógica de cola/ventana/transición vive aquí, pero usa `ExoPlayer`, `MediaItem`, `mediaItemCount`, `currentMediaItemIndex`, `setMediaItems`/`addMediaItems`/`removeMediaItems`, `PlaybackException`… Es el refactor más grande del proyecto (ver §3.2).
2. **`PlaylistLocalRepository` + Room.** 502 líneas con `LiveData`, `withTransaction` y DAOs de Room. La lógica de negocio (fusión de liked, tombstones) es portable; el acceso a datos hay que rehacerlo.
3. **UI con `LiveData` y `Context`.** El patrón es `playerViewModel.currentTitle.observeAsState()`. En Compose Multiplatform se usa `StateFlow`/`collectAsState`. `Context` (para `Translations`, `Config`, `Intent`) debe pasar a un singleton de app inyectado.
4. **`MusicService` (245 líneas) es 100% Android.** Notificación, `PendingIntent`, `KeyEvent`. Se descarta y se reimplementa con la integración nativa del SO.
5. **`Config`** (430 líneas) usa `SharedPreferences`. La lógica de claves/migraciones es reusable; solo hay que cambiar el backend de almacenamiento.
6. **`DataSync` + `BackupFolder`** usan SAF (`DocumentsContract`, `Uri`). La política de sincronización/tombstones es reusable; el I/O de archivos no.

---

## 3. Arquitectura objetivo

```
plyr/
├── core/                     # Kotlin Multiplatform (targets jvm + android) ← compartido por las 3 apps
│   ├── model/                #   modelos (hoy en com/plyr/model)
│   ├── domain/               #   QueueIndex, WindowState, LoadingState, PendingSkips,
│   │                         #   IdlePlayback, ImportManifest, ExportManifest, UrlParser,
│   │                         #   LikedSongsMerge, YouTubePlaylistCreator, CoverCropMath…
│   ├── network/              #   YouTubeManager, NewPipeHolder, SupabaseClient, Downloader
│   └── player/               #   AudioPlayer (interfaz) + QueueEngine (lógica de cola)
│
├── android/                  # app Android actual (usa core)
├── desktop/                  # app Compose Desktop (usa core)
│   ├── ui/                   #   pantallas portadas (Home, Search, Playlist, Queue, Feed, Config)
│   ├── player/               #   VlcAudioPlayer : AudioPlayer
│   ├── db/                   #   SQLDelight + repositorios
│   ├── platform/             #   Config, backup, rutas por SO, tema
│   └── integration/          #   MPRIS/SMTC/Now Playing, bandeja, atajos
└── gradle/libs.versions.toml
```

### 3.1 El paso clave: sacar un módulo `core` sin Android

Antes de tocar UI o motor, hay que **extraer los archivos de §2.2 a un módulo `core`** (Kotlin Multiplatform con targets `androidTarget()` + `jvm()`, sin dependencias Android en `commonMain`) y hacer que:
1. `app` (Android) y `desktop` (las tres plataformas) dependan de `core`.
2. Los **455 tests se muevan a `core/src/jvmTest`** y sigan en verde en un JDK de escritorio.

*Fallback:* si el wiring KMP con librerías JVM-only (NewPipe/OkHttp) resulta incómodo, `core` puede empezar como `kotlin("jvm")` puro —Android consume un módulo JVM sin problema— y convertirse a KMP después. No cambia el resultado para escritorio.

Esto se hace en días, no semanas, y **desriesga todo el port**: si el núcleo compila y sus tests pasan en JVM, el resto es trabajo conocido. Es la recomendación más importante de este documento: **empezar por aquí**.

### 3.2 Abstraer el reproductor

Introducir en `core` una interfaz mínima que capture lo que `PlayerViewModel` necesita de ExoPlayer:

```kotlin
interface AudioPlayer {
    val mediaItemCount: Int
    val currentMediaItemIndex: Int
    val currentPosition: Long
    val isPlaying: Boolean
    fun setMediaItems(items: List<AudioTrack>, startIndex: Int)
    fun addMediaItems(items: List<AudioTrack>)
    fun removeMediaItems(from: Int, toExclusive: Int)
    fun seekTo(index: Int, positionMs: Long)
    fun play(); fun pause(); fun stop(); fun clear()
    fun setListener(listener: AudioPlayerListener)
}
```

- Android: implementación fina sobre `ExoPlayer`.
- Desktop: implementación sobre **VLCJ**.
- `PlayerViewModel` pasa a depender de `AudioPlayer` y de una `QueueEngine` pura. Así, **la lógica de 60+ bugs corregidos se conserva y se testea** sin Android.

Esto es lo único que impide que el núcleo sea 100% puro hoy, y es un refactor acotado (una interfaz + dos adaptadores).

---

## 4. Migración componente a componente

| Componente actual | Acción | Destino desktop | Dificultad |
|---|---|---|---|
| `model/*`, helpers de `utils/`, `QueueIndex`, `WindowState`… | **Copiar tal cual** a `core` | — | ⭐ |
| `YouTubeManager`, `NewPipeHolder` | Quitar `android.util.Log` | logger multiplataforma | ⭐ |
| `SimpleDownloader` (OkHttp) | Igual | OkHttp funciona en JVM | ⭐ |
| `SupabaseClient` (HttpURLConnection + org.json) | Igual, quitar `Log` | igual | ⭐ |
| `Translations` | Igual (mapa en código) | igual | ⭐ |
| `Config` | Cambiar backend | `java.util.prefs` / JSON en carpeta del SO | ⭐⭐ |
| `PlayerViewModel` | Depender de `AudioPlayer` | VLCJ | ⭐⭐⭐⭐ |
| `PlaylistDatabase` + DAOs + `PlaylistLocalRepository` | Reescribir con SQLDelight | SQLDelight + repositorios | ⭐⭐⭐ |
| UI Compose (42 archivos) | `androidx.compose` → `org.jetbrains.compose`; `LiveData` → `StateFlow`; quitar `Context`/`Intent` | Compose Desktop | ⭐⭐⭐ |
| Coil (portadas) | Subir a Coil 3 | Coil3 CMP | ⭐⭐ |
| `SpotifyImporter` | Desacoplar de `Context` y del repositorio | mismo algoritmo | ⭐⭐ |
| `QRDialog` (generar) | ZXing JVM | igual | ⭐⭐ |
| `QrScannerDialog` (leer) | **Sustituir**: elegir archivo o pegar URL | sin cámara | ⭐⭐ |
| `NfcReader` / `NfcTagEvent` / `NfcPulse` | **Descartar** (o lector NFC USB opcional más adelante) | — | — (se elimina) |
| `LightSensorDetector` | **Descartar**; seguir tema del sistema | — | — |
| `BackupFolder` + SAF | `java.nio` sobre la carpeta de datos del SO | copia a carpeta normal | ⭐⭐ |
| `DataSync` | Reutilizar política, cambiar I/O | `java.nio` | ⭐⭐ |
| `MusicService` | **Reescribir** | MPRIS/SMTC/Now Playing + bandeja | ⭐⭐⭐ |
| `MediaButtonReceiver` | **Reescribir** | integración nativa/atajos globales | ⭐⭐ |
| `UpdateChecker` | Adaptar a releases de escritorio | GitHub releases / paquete | ⭐ |
| `FloatingMusicControls` | Portar a Compose Desktop | igual | ⭐⭐ |

---

## 5. Reutilización concreta (números)

- **Lógica y dominio (100% portable):** ~30 archivos, ~5.000–6.000 líneas.
- **Red (≈95% portable):** `YouTubeManager` (231), `SimpleDownloader` (247), `SupabaseClient` (387), `AppModels`.
- **UI (≈55–70% portable):** ~42 archivos Compose; el grueso de estructura, tema y componentes se copia y se ajusta.
- **Tests (100% portable):** 455 tests JVM en 35 archivos → **la garantía de que el port no rompe los 60+ bugs ya resueltos**.
- **No portable:** NFC (4 archivos), sensor de luz, CameraX, `MusicService`/notificación, SAF (≈9 archivos) → ~1.500–2.000 líneas a reescribir o descartar.

Regla práctica: **~65% del código y el 100% de los tests viajan; ~35% se adapta o se reescribe**, y de ese 35% buena parte es capa fina (adaptadores) sobre APIs de escritorio bien conocidas.

---

## 6. Roadmap por hitos (ejecutable con opencode)

Los hitos `M0`–`M19` **están completados** (§6.1–§6.2): el andamiaje, la lógica compartida, el motor de audio, la UI, la persistencia, la sincronización, QR/Spotify, el feed, la descarga offline, la UI de almacenamiento, el carrusel de la Home, la búsqueda de listas de YouTube, la integración MPRIS y el empaquetado ya funcionan en escritorio. Lo que queda son los **huecos de paridad con Android** detectados después del cierre, recogidos como backlog en **§6.4**.

Cada hito se pasa a opencode tal cual con la plantilla de §6.0. La regla de oro es que **al terminar cada hito, `./gradlew build` y los tests deben pasar** tanto en Android como en desktop, y `./run.sh check` debe quedar en verde.

### 6.0 Cómo usar el roadmap con opencode

1. Trabaja **un hito a la vez**. No empieces `Mn+1` hasta cerrar `Mn`.
2. Para cada hito usa la **plantilla de prompt** de abajo, pegando el bloque `Prompt opencode` del hito.
3. Al terminar, ejecuta tú mismo los **comandos de verificación**. Si fallan, pide a opencode que los arregle antes de marcar el hito.
4. Marca el hito en la tabla de seguimiento (§6.2) solo si su **Definition of Done** se cumple.

**Plantilla de prompt (la de cada hito ya la rellena):**

```
CONTEXTO: repo plyr (Android, Kotlin). Port a escritorio con Kotlin Multiplatform
+ Compose Multiplatform. Módulos objetivo: :core (lógica compartida) y :desktop.
HITO: <id y título>
OBJETIVO: <objetivo>
TAREAS: <lista>
RESTRICCIONES: no romper el build de Android; no tocar código no relacionado; sin
comentarios innecesarios; respetar el estilo existente.
VERIFICACIÓN: <comandos>. Ejecútalos y no pares hasta que pasen.
ENTREGABLE: <qué debe quedar en el repo>.
```

### 6.1 Hitos completados (resumen)

- **M0 — Andamiaje de módulos:** `:core` (KMP; se usa `com.android.kotlin.multiplatform.library` en lugar de `androidTarget()`, que AGP 9 marca deprecado) y `:desktop` (Compose MP); `:app` y `:desktop` dependen de `:core`.
- **M1 — Lógica pura a `:core`:** 13 archivos a `commonMain` y 5 a `jvmSharedMain` (source set nuevo compartido JVM+Android), con `Logger` propio en vez de `android.util.Log`; los tests puros pasan a `:core:jvmTest`.
- **M2 — Red a `:core`:** `YouTubeManager`, `NewPipeHolder`, `SimpleDownloader`, `SupabaseClient`, `AppModels` y el catálogo de traducciones; `NetworkConfig` sustituye a `BuildConfig`; `MediaMetadataExtractor` se mueve a `jvmSharedMain`.
- **M3 — `AudioPlayer` + `QueueEngine`:** interfaz neutral de reproducción y lógica de cola pura; ExoPlayer queda aislado en `ExoAudioPlayer`.
- **M4 — VLCJ:** `VlcAudioPlayer` + `VlcPlaybackStateMachine` + detección de `libvlc`; prueba de sonido con YouTube real.
- **M5 — Esqueleto UI:** tema terminal, `Screen`/navegación, `Translations` y `FloatingMusicControls`.
- **M6 — SQLDelight:** esquema `.sq` + `PlaylistRepository`/`SearchHistoryRepository`; lógica de fusión (liked/tombstones/visibles) extraída a `:core`.
- **M7 — Reproducción en UI (MVP):** `PlayerController` y `SearchController` sobre `StateFlow`; búsqueda y cola reales.
- **M8 — Playlists UI:** alta/edición/borrado/reordenado, portada recortada con Coil 3 y «añadir a lista».
- **M9 — Config:** `DesktopConfig` persistente (multiplatform-settings), rutas por SO y `CoverCache`.
- **M10 — Import/Export + backup:** `plyr-sync.zip` con `java.nio`, tombstones persistentes y selector de carpeta nativo.
- **M11 — QR + Spotify:** `QrCodec`/`QrImages` (ZXing, sin cámara) y `SpotifyImport` desacoplado de Android/Room.
- **M12 — Feed (Supabase):** `FeedController`/`FeedScreen` con listado, publicación y apodo persistido.
- **M13 — Integración nativa:** MPRIS (Linux, D-Bus) y bandeja del sistema; SMTC/Now Playing quedan para más adelante.
- **M14 — Empaquetado:** `jpackage` vía plugin Compose (`.deb` verificado; `.rpm`/`.msi`/`.dmg` configurados).
- **M15 — Pulido:** atajos de teclado, `UpdateChecker` de escritorio, detekt con `detekt-baseline.xml`, checklist i18n y docs.
- **M16 — Descarga offline:** `DownloadPlan` (puro) a `:core`; `DownloadedAudioStore` (`java.nio`, `dataDir/audio/<videoId>.m4a`), `AudioDownloader` (OkHttp, con `AudioFetcher` inyectable) y `DownloadController` (`StateFlow`); `YouTubeTrackSourceResolver` prefiere el fichero local y deja el streaming de respaldo.
- **M17 — UI de almacenamiento offline:** `DownloadStatusBar` y `OfflineStorageDialog` en `:desktop`; botón descargar/limpiar en el detalle de lista, indicador `↓`/`↓ NN%` por pista y acción «borrar descarga»; sección de almacén en Ajustes (tamaño, nº de pistas y «borrar todo»).
- **M18 — Carrusel de playlists en Home:** `HomePlaylistCarousel`/`HomePlaylistItem`/`HomePlaylistCover` en `HomeScreen`; carga las listas visibles (mismo filtro que la pantalla de listas) con portada Coil 3 y abre el detalle al pulsarlas, manteniendo el menú de consola y el aviso de actualización.
- **M19 — Playlists de YouTube en búsqueda + detalle:** `YouTubeManager.searchAll`/`getPlaylistInfo`/`getPlaylistVideos` en `:core`; la búsqueda muestra vídeos y listas en dos secciones plegables (`v`/`>`) y `YouTubePlaylistDetailView` abre una lista, la reproduce entera y la guarda como lista local.

### 6.2 Tabla de seguimiento

| Hito | Descripción | Semanas | Estado |
|---|---|---|---|
| M0 | Andamiaje de módulos | 0.5 | ☑ |
| M1 | Extraer `core` + tests ⭐ | 1–2 | ☑ |
| M2 | Red a `core` | 0.5 | ☑ |
| M3 | `AudioPlayer` + `QueueEngine` ⭐ | 1 | ☑ |
| M4 | VLCJ funcionando ⭐ | 1 | ☑ |
| M5 | Esqueleto UI Compose Desktop | 1 | ☑ |
| M6 | SQLDelight + repositorios | 1–2 | ☑ |
| M7 | Reproducción integrada (**MVP**) ⭐ | 1–2 | ☑ |
| M8 | Playlists UI + drag & drop | 1–2 | ☑ |
| M9 | Config/Ajustes multiplataforma | 1 | ☑ |
| M10 | Import/Export + backup | 1 | ☑ |
| M11 | QR + Spotify import | 1 | ☑ |
| M12 | Feed Supabase | 0.5 | ☑ |
| M13 | Integración nativa (MPRIS/SMTC/Now Playing) | 1–2 | ☑ (solo MPRIS) |
| M14 | Empaquetado multiplataforma | 1 | ☑ |
| M15 | Pulido y paridad final | 1–2 | ☑ (paridad parcial, ver §6.4) |
| M16 | Descarga de audio offline + reproducción sin conexión ⭐ | 1–2 | ☑ |
| M17 | UI de almacenamiento offline | 1 | ☑ |
| M18 | Carrusel de playlists en Home | 0.5 | ☑ |
| M19 | Playlists de YouTube en búsqueda + detalle | 1–1.5 | ☑ |
| M20+ | Backlog de paridad con Android | — | ◐ pendiente (§6.4) |

### 6.3 Decisiones clave del port

- **Stack final:** Kotlin + Compose Multiplatform; `:core` KMP (`commonMain` puro + `jvmSharedMain` para `java.*` compartido JVM/Android + `jvmTest`); SQLDelight en vez de Room; VLCJ en vez de ExoPlayer; multiplatform-settings en vez de SharedPreferences; Coil 3; MPRIS tras `DesktopMediaIntegration`; `jpackage`.
- **Desviaciones conscientes (no son deuda):** sin NFC; sin CameraX (se pega URL / se abre imagen); sin sensor de luz (tema del SO); sin swipe (acciones contextuales con ratón); sin notificaciones nativas del SO (MPRIS); sin SAF (`java.nio`); listas creadas localmente (no de YouTube).
- **Código compartido en `:core`:** `MediaMetadataExtractor`, `ImportManifest`, `QrCodec`, `SpotifyImport`, `CoverCacheKey`, `formatTimestamp`, `PlaylistFilters`/`TrackIdentity`/`Tombstones`, `ShareUrlPolicy`/`PlaylistShare`.
- **Tests:** el conjunto `:core` + `:app` + `:desktop` cerró en **~723 tests** (los 455 originales + los nuevos de cada capa). `TranslationKeysUsageTest` escanea también `desktop`; detekt aplica `detekt-baseline.xml` por módulo para no fallar por los composables ya portados.
- **Historial detallado:** el registro de ejecución completo por hito (decisiones, desviaciones y recuentos de tests de M0–M15) vive en el historial de git del repositorio; aquí queda resumido a una línea por hito (§6.1).

### 6.4 Backlog de paridad pendiente (M20+)

Funciones de Android que **no** están en escritorio y **no** entraban en las desviaciones justificadas. Ordenadas por prioridad (impacto funcional). La prioridad «Alta» corresponde a F18 y F23 del inventario (§1.1).

| Hito | Descripción | Prioridad | Semanas est. |
|---|---|---|---|
| M20 | Reproducción aleatoria (`<rnd>`) en playlists | **Alta** | 0.5 |
| M21 | Paginación («cargar más») en búsqueda | Media | 0.5 |
| M22 | Crear/editar playlist con búsqueda + acciones por pista | Media | 1 |
| M23 | Feed: metadatos del vídeo y reproducir desde el feed | **Alta** | 0.5 |
| M24 | «Compartir app» por QR en Config | Baja | 0.5 |
| M25 | Recomendar al feed desde el diálogo de compartir | Media | 0.5 |

---

#### M16 — Descarga de audio offline + reproducción sin conexión ⭐ ☑ (completado)
**Objetivo:** que el escritorio descargue el audio (m4a) de una pista y lo reproduzca desde disco cuando exista, igual que Android; solo hace streaming si no está descargada.
**Tareas**
- Extraer a `:core` la parte pura de `DownloadPlan` (qué pistas faltan por descargar) validando que no depende de Room.
- `AudioDownloader` de escritorio sobre `SimpleDownloader`/OkHttp; `DownloadedAudioStore` con `java.nio` en `AppPaths.directories.dataDir/audio/<videoId>.m4a` («ya descargado» = existe el fichero, sin tabla nueva).
- `DownloadController` (`StateFlow`) al estilo de `DownloadViewModel`: encolar, progreso y cancelación.
- Envolver/ajustar `YouTubeTrackSourceResolver` para **preferir el fichero local**; el streaming queda como respaldo. Corregir su kdoc obsoleto («eso es M10»).
**Definition of Done:** una pista descargada se reproduce sin red; borrar `dataDir/audio` vuelve al streaming.
**Verificación:** tests con descarga inyectada + prueba manual sin conexión.
**Prompt opencode:**
> Porta el subsistema de descarga offline de Android a `:desktop`. Extrae a `:core` la lógica pura de `DownloadPlan`; implementa `AudioDownloader` (OkHttp) y `DownloadedAudioStore` (`java.nio`, `dataDir/audio/<videoId>.m4a`) y un `DownloadController` sobre `StateFlow`. Haz que `YouTubeTrackSourceResolver` prefiera el fichero local si existe y solo haga streaming si no. Verifica con tests y reproduciendo una pista descargada sin red.

---

#### M17 — UI de almacenamiento offline ☑ (completado)
**Objetivo:** paridad con la UI de descargas de `PlaylistScreen`.
**Tareas**
- Barra de descarga en el detalle de lista (descargar todas / limpiar) y en el lista de playlists.
- Indicador `↓` por pista ya descargada y acción por pista «borrar descarga».
- `OfflineStorageDialog`: tamaño total ocupado, número de pistas y «borrar todo».
**Definition of Done:** se puede descargar una lista entera, ver el estado y liberar espacio desde Ajustes.
**Verificación:** `:desktop:test` + prueba manual.
**Prompt opencode:**
> Añade a `:desktop` la UI de descargas offline: barra de descarga en el detalle de lista, indicador por pista, acción «borrar descarga» y un diálogo de almacenamiento con tamaño total y «borrar todo». Reutiliza `DownloadController` de M16. Verifica con tests de Compose y manualmente.

**Implementado:** `DownloadStatusBar` (`downloadStatusMessage`) y `OfflineStorageDialog` (`rememberOfflineSummary`/`offlineStorageMessage`) en `com.plyr.desktop.download`; en `PlaylistDetailView` el botón `playlist_download` alterna descargar/cancelar/limpiar y `PlaylistTrackItem` pinta `↓` (`track_downloaded_*`) / `↓ NN%` (`track_downloading_*`) con borrado por pista (`track_delete_download_*`); `ConfigScreen` gana la sección de almacén (`offline_storage_summary`/`offline_storage_button`) y `AudioListScreen` comparte un `DownloadController` entre Ajustes y listas. 7 claves `download_*` nuevas en el catálogo. Tests: `DownloadMessagesTest`, `OfflineStorageDialogTest`, `PlaylistDetailViewTest` y caso de almacén en `ConfigScreenTest`.

---

#### M18 — Carrusel de playlists en Home ☑ (completado)
**Objetivo:** que la Home muestre las listas visibles con portada, no solo el menú de consola.
**Tareas**
- Portar `HomePlaylistCarousel`/`HomePlaylistItem`/`HomePlaylistCover`; cargar listas visibles desde `PlaylistRepository`; clic para abrir el detalle.
- Mantener el menú de consola y el aviso de actualización bajo el carrusel.
**Definition of Done:** la Home lista las listas con portada y navega al pulsarlas.
**Verificación:** `:desktop:test` (Compose UI) + manual.
**Prompt opencode:**
> Añade a la Home de `:desktop` un carrusel horizontal de las playlists visibles con su portada (Coil 3), portando `HomePlaylistCarousel` de Android, manteniendo el menú de consola. Al pulsar una lista se abre su detalle. Verifica con tests de Compose.

**Implementado:** `HomeScreen` recibe `playlists` (ya filtradas por `PlaylistFilters` en `PlaylistsController`) y `onOpenPlaylist`; pinta `HomePlaylistCarousel` (`LazyRow` con `home_playlists`/`home_playlist_<remoteId>`) sobre el menú. `HomePlaylistItem`/`HomePlaylistCover` muestran la portada con Coil 3 (`UrlParser.normalizeYoutubeThumb`), el corazón en Favoritos y la inicial si no hay imagen. `AudioListScreen` cablea el clic a `selectPlaylist` + `Screen.PLAYLISTS`, de modo que se abre el detalle. Sin listas el carrusel no ocupa sitio. Tests: `HomeScreenTest` (3).

---

#### M19 — Playlists de YouTube en búsqueda + detalle ☑ (completado)
**Objetivo:** buscar también *playlists* de YouTube y poder abrir/reproducir/guardar una.
**Tareas**
- `:core`: `YouTubeManager.searchAll` (vídeos + listas), `getPlaylistInfo` y `getPlaylistVideos`, reutilizables desde Android.
- `SearchScreen`: mostrar resultados de lista junto a los vídeos (mismo patrón que Android): dos secciones **colapsables** («vídeos» y «playlists», con el mismo `v`/`>` de Android). El botón «cargar más» de la sección de vídeos (F20) se pospuso a **M21**.
- `YouTubePlaylistDetailView`: ver las pistas, reproducir todo y guardar como lista local (`PlaylistSource`).
**Definition of Done:** una búsqueda devuelve listas y se puede guardar/reproducir una.
**Verificación:** `:core:jvmTest` (con `assumeTrue` de red) + `:desktop:test` + manual.
**Prompt opencode:**
> Añade búsqueda de playlists de YouTube en `:core` (`searchAll`, `getPlaylistInfo`, `getPlaylistVideos`) y muéstralas en la búsqueda de `:desktop` junto a los vídeos. Implementa una vista de detalle que liste sus pistas, las reproduzca todas y permita guardarlas como lista local. Verifica con tests y manualmente.

**Implementado:** `:core` gana `YouTubePlaylistInfo`, `YouTubeSearchAllResult` y los métodos `searchAll`/`getPlaylistInfo`/`getPlaylistVideos` (más el helper `audioItemFrom`, compartido con `searchVideos`). La búsqueda de escritorio devuelve `SearchResults` (vídeos + listas) en una sola llamada de red; `SearchController.SearchState` añade `playlists`, `videosExpanded`/`playlistsExpanded` (con `toggleVideos`/`togglePlaylists`) y `hasResults`/`isEmptyResult` ampliados. `SearchScreen` pinta una `LazyColumn` con dos secciones plegables (`v`/`>`, cabeceras `search_videos_header`/`search_playlists_header`) y filas de lista con portada (`search_playlist_<id>`). Nuevos `YouTubePlaylistDetailController` (+`YouTubePlaylistDetailState`) y `YouTubePlaylistDetailView` en `com.plyr.desktop.search`: cargan las pistas, «play all» y guardar/quitar como lista local con `PlaylistRepository` (`remoteId = "youtube_<id>"`, `source = YOUTUBE`, `sourceId = <id>`, misma convención que Android). `AudioListScreen` muestra el detalle dentro de `Screen.SEARCH` cuando hay una lista abierta y salta a `Screen.QUEUE` al reproducir. 2 claves nuevas (`youtube_playlists`, `saved`). Tests: `YouTubePlaylistDetailControllerTest` (7), `YouTubePlaylistDetailViewTest` (6), `SearchScreenTest` (+4) y `SearchControllerTest` (+3), más 3 casos de red en `NetworkSmokeTest` (con `assumeTrue`).

---

#### M20 — Reproducción aleatoria (`<rnd>`) en playlists
**Objetivo:** botón `<rnd>` que baraja y reproduce una lista.
**Tareas**
- `PlayerController.startRandomized(tracks)` / stop; barajado puro y testeable.
- Cablear el botón en el detalle de lista.
**Definition of Done:** pulsar `<rnd>` reproduce las pistas en orden aleatorio.
**Verificación:** test puro del barajado + `:desktop:test`.
**Prompt opencode:**
> Añade a `:desktop` reproducción aleatoria en el detalle de playlist: un método `startRandomized` en `PlayerController` (barajado puro y testeable) y el botón `<rnd>` en la vista de lista. Verifica con un test del barajado.

---

#### M21 — Paginación («cargar más») en búsqueda
**Objetivo:** poder pedir más resultados, como el «cargar más» de Android (F20).
**Tareas**
- Soporte de paginación en `SearchController`/`SearchScreen` (`onLoadMore`), con el mismo umbral que Android (aparece «cargar más» cuando hay ≥10 resultados).
- *(Opcional, más allá de Android)* cablear `SearchHistoryRepository` para guardar cada búsqueda: en Android el historial se **persiste pero nunca se muestra** (`getAllSearches()` no se usa), así que no es un hueco de paridad.
**Definition of Done:** se pueden pedir más resultados desde la búsqueda.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Añade paginación («cargar más») a la búsqueda de `:desktop` (`SearchController`/`SearchScreen`), con el mismo umbral que Android. Verifica con tests. (El historial de búsqueda no hace falta para paridad: Android lo guarda pero no lo lista.)

---

#### M22 — Crear/editar playlist con búsqueda + acciones por pista
**Objetivo:** crear o editar una lista agregando pistas mientras se busca en YouTube, y acciones por pista completas.
**Tareas**
- `CreatePlaylistView` con buscador de YouTube y lista de seleccionadas.
- Modo edición del detalle de lista (F26): buscador de YouTube para **añadir** pistas (`+`) junto a las actuales, además de quitarlas (`x`) y reordenarlas.
- Menú por pista (detalle, cola y resultados): me gusta (favoritos), añadir a cola (`PlayerController.addToQueue`, hoy ausente) y añadir a playlist, además de compartir.
- **Nota:** el menú por pista de escritorio (`ui/components/SongListItem.kt`) usa hoy texto fijo en inglés («Play»/«Remove») en vez de `Translations`; hay que pasarlo por el catálogo al añadir las acciones nuevas.
**Definition of Done:** se crea/edita una lista eligiendo pistas desde la búsqueda y las acciones por pista funcionan.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Amplía `:desktop` para (1) buscar en YouTube y elegir pistas al crear una lista y (2) añadir pistas a una lista existente desde el modo edición (buscador + `+`, junto a `x`/reordenar). Añade a las pistas (detalle, cola y resultados) un menú con me gusta, añadir a cola (`addToQueue` en `PlayerController`) y añadir a playlist, además de compartir. Verifica con tests.

---

#### M23 — Feed: metadatos del vídeo y reproducir desde el feed
**Objetivo:** mostrar título/autor/miniatura en cada recomendación y reproducirla o abrirla al pulsar.
**Tareas**
- Usar `MediaMetadataExtractor` (ya en `:core`) en `FeedController`/`FeedScreen` para enriquecer cada entrada (título, autor y miniatura), con concurrencia acotada como en Android (`METADATA_CONCURRENCY`).
- Clic según el tipo de contenido (`MediaType`): vídeo de YouTube → reproducir directamente; *playlist* de YouTube → abrirla en la búsqueda (mismo `ScanResult` que usa el lector de QR); desconocido → intentar extraer un `videoId` y reproducir.
- Corregir el kdoc obsoleto de `FeedScreen.kt` («`MediaMetadataExtractor`… todavía no se ha portado»): sí está, en `:core`.
- **No perder de más:** el feed de escritorio ya tiene cosas que Android **no** tiene y deben conservarse: formulario de publicación (apodo + URL + comentario), selector de grupo destino y mostrar el `comment` de cada recomendación (Android no lo pinta). Android, en cambio, publica desde el diálogo de compartir (F25) y solo lista el grupo general.
**Definition of Done:** el feed muestra metadatos y al pulsar reproduce el vídeo o abre la lista.
**Verificación:** `:desktop:test` + manual con red.
**Prompt opencode:**
> Enriquece el feed de `:desktop` con `MediaMetadataExtractor` de `:core` (título, autor y miniatura por recomendación, con concurrencia acotada) y haz que al pulsar una recomendación se comporte como en Android: vídeo de YouTube → reproducir; playlist de YouTube → abrirla en la búsqueda (reutiliza el `ScanResult` del lector de QR); desconocido → extraer el `videoId` y reproducir. Corrige el kdoc que dice que `MediaMetadataExtractor` no está portado. Verifica con tests y manualmente.

---

#### M24 — «Compartir app» por QR en Config
**Objetivo:** paridad con `ShareAppSection` de Android.
**Tareas**
- Añadir a `ConfigScreen` una sección que comparta la app «plyr» (enlace/QR de descarga) reutilizando `QrShareDialog`/`QrCodec`.
**Definition of Done:** la sección aparece y genera el QR/enlace.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Añade a `ConfigScreen` de `:desktop` una sección «compartir app» que genere el QR y el enlace de descarga de plyr, reutilizando `QrShareDialog`/`QrCodec`. Verifica con tests.

---

#### M25 — Recomendar al feed desde el diálogo de compartir
**Objetivo:** paridad con `RecommendButton`/`requestRecommendation` de Android: publicar la URL compartida en el feed (grupo general) usando el apodo guardado, sin pasar por la pantalla de Feed.
**Tareas**
- Añadir el botón «recomendar» a `QrShareDialog` (solo si hay URL) que llame a `SupabaseClient.postRecommendation` con el `DesktopConfig.userNickname`.
- Mensaje de estado (`recommendation_sent`/`recommendation_error`, ya en `:core`).
**Definition of Done:** desde el QR de una pista/lista se puede enviar la recomendación y aparece en el feed.
**Verificación:** `:desktop:test` + manual con red.
**Prompt opencode:**
> Añade al `QrShareDialog` de `:desktop` un botón «recomendar» que publique la URL compartida en el feed (grupo general) con el apodo de `DesktopConfig.userNickname`, reutilizando `postRecommendation` de `FeedController`/`SupabaseClient` y las claves `recommendation_sent`/`recommendation_error`. Verifica con tests.

---

#### M26 — Flush de copia de seguridad al cerrar la app
**Objetivo:** paridad con Android `DataSync.flushOnStop`: no perder la copia pendiente al cerrar.
**Tareas**
- Evitar que `DataSync.shutdown()` cancele un flush pendiente sin intentar escribirlo. Añadir un método `flushOnStop()` (o ajustar shutdown) que intente `flush(force=false)` con un timeout corto (p.ej. 3–5s) sin bloquear indefinidamente.
- Llamarlo desde `AudioListScreen.onDispose` en vez de cancelar a ciegas.
- Mantener el comportamiento de `markDirty` y la comparación de huellas.
**Definition of Done:** si hay cambios pendientes al cerrar, se vuelca la copia de seguridad a la carpeta configurada (o se aguanta el timeout razonable) antes de salir.
**Verificación:** `:desktop:test` + manual (modificar lista, cerrar ventana antes de 30s; verificar `plyr-sync.zip`).
**Prompt opencode:**
> Modifica `DataSync.shutdown()` de `:desktop` para, cuando haya un `debounceJob` activo o se haya marcado sucio, intentar `flush(force=false)` con `withTimeout(5000)` antes de cancelar. En `AudioListScreen.onDispose`, llama a este shutdown modificado (o `dataSync.flushOnStop()`). Mantén la lógica de huellas y no rompas los tests existentes.

---

#### M27 — Acceso a la cola solo desde controles (escritorio)
**Objetivo:** cambiar navegación en escritorio para acceder a la cola únicamente desde el indicador `^` del `FloatingMusicControls` (igual que en Android, que no tiene botón de cola en la barra/menu principal). **Solo afecta a UI de escritorio, no tocar Android.**
**Tareas**
- Quitar el botón `plyr_queue` del menú/navegación de escritorio (`Screen`, `AudioListScreen`, `MenuBar`/botones según corresponda).
- Asegurar que la cola solo se abre al pulsar el botón `^` de `FloatingMusicControls` en escritorio (ya existente).
- No añadir ni quitar rutas en Android; no tocar `com.plyr.ui.Screen` ni la navegación de Android.
**Definition of Done:** en escritorio no aparece el botón para ir a la cola desde la barra de navegación; sigue siendo accesible desde `^` en los controles flotantes. En Android sin cambios.
**Verificación:** `:desktop:test`, manual (comprueba menús).
**Prompt opencode:**
> Solo en `desktop/...` (no toques `app/src/...`), elimina cualquier entrada de navegación/botón que lleve a la cola desde el menú principal. La cola debe seguir abriéndose únicamente al pulsar el botón `^` de `FloatingMusicControls`. Revisa `Screen.kt`, `AudioListScreen.kt`, menús y tests de escritorio afectados. No modifiques nada de Android.

---

#### M28 — Quitar QR scanning y mover búsqueda al inicio (escritorio)
**Objetivo:** simplificar la barra superior/navegación de escritorio: eliminar el escáner QR de la pantalla de búsqueda y meter la barra de búsqueda directamente en `HomeScreen`, de modo que se pueda eliminar el botón `plyr_search`. **Solo UI de escritorio.**
**Tareas**
- Quitar el botón/diálogo `QrScanDialog`/escaneo QR de `SearchScreen` de escritorio (mantener el lector vía QR share existente solo si aplica, pero eliminar la UI de escaneo en Search; no tocar Android).
- Añadir un campo de búsqueda (input) en `desktop/.../ui/screens/HomeScreen.kt` que dispare la navegación a `SearchScreen` con el término o directamente muestre resultados según el diseño actual (o reutiliza el `SearchController` para pasar query). 
- Quitar el botón `plyr_search` de la navegación de escritorio (`Screen`, menú, etc.).
- Eliminar referencias a QR scan de tests de escritorio que dejen de tener sentido; no tocar Android.
**Definition of Done:** en escritorio `HomeScreen` tiene la barra de búsqueda; el botón `plyr_search` desaparece del menú; `SearchScreen` ya no muestra la opción de escanear QR. Android intacto.
**Verificación:** `:desktop:test` + manual.
**Prompt opencode:**
> Solo en `desktop/...`, mueve la barra de búsqueda a `HomeScreen`. Elimina la UI de escaneo QR de `SearchScreen` (código y botones). Quita la entrada `SEARCH` del enum `Screen` de escritorio y cualquier botón de navegación asociado. Asegúrate de que al enviar búsqueda desde Home se navegue a Search con el texto (o se reutilice la pantalla existente). No toques código Android.

---

#### M29 — Carrusel horizontal de playlists en Home (escritorio)
**Objetivo:** reemplazar el acceso a `plyr_lists` por un carrusel horizontal de portadas a la izquierda, y al final un botón `>` que lleve a `PlaylistsScreen`. **Solo UI de escritorio.**
**Tareas**
- En `HomeScreen` de escritorio, mostrar un carrusel horizontal (LazyRow) con las portadas de las playlists visibles (reutilizar `PlaylistsController`/repositorio ya existente).
- Mostrar miniaturas (`PlaylistCover`/Coil) con título opcional; al pulsar una portada, abrir su detalle (`PlaylistDetailView`/navegación ya existente).
- Añadir un botón `>` al final del carrusel que navegue a `PlaylistsScreen` (mismo destino que el antiguo botón `plyr_lists`).
- Quitar el botón `plyr_lists` de la navegación de escritorio. Actualizar `Screen` de escritorio si procede (mantén las demás pantallas).
- No tocar Android.
**Definition of Done:** Home muestra el carrusel de playlists; el `>` lleva a la pantalla de listas; el botón `plyr_lists` ya no aparece en el menú de escritorio. Android sin cambios.
**Verificación:** `:desktop:test` + manual.
**Prompt opencode:**
> Solo en `desktop/...`, modifica `HomeScreen` para mostrar un carrusel horizontal con portadas de playlists (usa datos existentes de PlaylistsController/repositorio). Añade un botón `>` al final que navegue a la pantalla de playlists. Elimina la entrada/navegación a `PLAYLISTS` desde el menú principal de escritorio. No modifiques código de `app/src/...`.

---

**Diferencias menores / desviaciones conscientes:** el logo ASCII de la Home es fijo en escritorio (Android elige uno aleatorio de `ascii_1..50`); no hay layout apaisado/tablet «side-by-side» (la ventana es redimensionable); compartir usa el portapapeles en vez de la hoja del sistema (`ACTION_SEND`); sin háptica; el reordenado de la **cola** usa botones `^`/`v` (Android arrastra) — el detalle de lista sí admite arrastre; MPRIS publica `Volume`/`LoopStatus`/`Shuffle` como **solo lectura** fijos (el `Set` de `MprisPlayer` lanza `PropertyReadOnly`) y `OpenUri` es un no-op, mientras que `Seek`/`SetPosition` sí funcionan; Android usa `unknown_artist` para pistas sin artista y una etiqueta `loading_tracks` al cargar una lista, escritorio pinta el valor crudo (cosmético); `exit_message` de Android es **código muerto** (`showExitMessage` nunca pasa a `true`); la copia de seguridad automática en escritorio cancela el flush pendiente al cerrar (`DataSync.shutdown()`), mientras que Android espera a escribirlo en `onStop` (comportamiento a corregir en **M26**); **UI escritorio (M27–M29):** acceso a cola solo desde `^` en controles, búsqueda integrada en Home, carrusel de playlists en Home con `>` a listas.

**A favor del escritorio (no perder):** el feed tiene formulario de publicación + selector de grupo + muestra el `comment` (Android no); Config tiene filas extra de **repetición** y **rutas de datos/caché** (Android no); atajos de teclado, bandeja del sistema (`DesktopTray`) y prueba de sonido por CLI (`SoundCheck`).

**Nota:** `SearchHistoryRepository` de escritorio ya cubre la persistencia del historial de búsqueda con la misma política que el DAO de Android, pero **ninguna de las dos UIs lo muestra** (ver F19).

**No se porta (desviaciones conscientes):** NFC; escáner QR con cámara (se sustituye por pegar URL / abrir imagen); tema por sensor de luz (tema del SO); gestos swipe (acciones contextuales); notificaciones nativas del SO (MPRIS); SAF (`java.nio`); creación de playlists de YouTube como fuente (las locales las cubre M19).

---

## 7. Riesgos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| VLCJ no reproduce alguna URL de YouTube (formato/códec) | Media | Alto | Probar en **M4** con m4a y opus; fallback a GStreamer o a descargar+reproducir |
| `PlayerViewModel` difícil de desacoplar de ExoPlayer | Media | Alto | Hacerlo en **M3** con interfaz fina; los tests ya cubren la lógica |
| YouTube cambia y NewPipe se rompe | Alta (crónica) | Medio | Ya ocurre en Android; se comparte el mismo extractor y su mantenimiento |
| `libvlc` no disponible en el equipo del usuario | Media | Medio | Declararlo como dependencia del paquete; detectar y avisar |
| Compose Desktop + VLCJ (hilos/AWT) | Media | Medio | Audio sin vídeo simplifica; VLCJ gestiona su propio hilo |
| Deriva de las dos UIs (Android/desktop) | Media | Medio | Compartir `core` y los componentes comunes; divergir solo en lo Android‑específico |
| Sin integración nativa las teclas multimedia no funcionan | Media | Bajo | **M13**; opción de atajos globales |
| Distribución flatpak y `libvlc` | Baja | Medio | Empaquetar o declarar en el manifiesto |

---

## 8. Empaquetado y distribución

- **JRE incluido:** `jpackage` (o Conveyor) genera un runtime propio (no exige Java instalado) en los tres SO.
- **Linux:** `.deb` (Debian/Ubuntu), `.rpm` (Fedora), **AppImage** (universal), **Flatpak** (Flathub), **AUR** (Arch).
- **Windows:** `.msi` / `.exe` con jpackage (firmado Authenticode opcional).
- **macOS:** `.dmg` / `.pkg` con jpackage (notarización necesaria si se distribuye).
- **Rutas de datos por SO:** `%APPDATA%` (Windows), `~/.config` + `~/.local/share` + `~/.cache` (Linux), `~/Library/Application Support` (macOS).
- **Integración Linux:** `.desktop` + icono + AppStream metadata para aparecer en menús y tiendas.
- **Dependencia externa:** `libvlc`/VLC. Se puede declarar como dependencia del paquete o empaquetar. Documentarlo en el README.

---

## 9. Licencia

El proyecto es **GPL‑3.0** y usa **NewPipeExtractor (GPL‑3.0)**. El port debe seguir siendo **GPL‑3.0**. Notas:
- **GStreamer/LGPL** y **VLCJ** son compatibles con GPLv3 (VLCJ se distribuye bajo GPL).
- Al ser un fork/port del mismo proyecto, mantener `LICENSE`, aviso de copyright y el crédito a NewPipe.
- Al publicar en Flathub/AUR, respetar el empaquetado y las licencias de `libvlc`.

---

## Apéndice — Métricas usadas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 83 |
| Líneas Kotlin (main) | ~16.500 |
| Archivos sin import de Android | 30 |
| Archivos de test | 35 (~5.400 líneas, JVM puro) |
| Tests unitarios | 455 |
| Uso de `android.content.Context` | 22 archivos |
| Uso de Compose | 21 archivos |
| Uso de Room | 9 archivos |
| Uso de Media3/ExoPlayer | 5–6 archivos |
| Uso de NFC | 4 archivos |
| Uso de CameraX | 1 archivo |
