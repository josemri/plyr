# Análisis de portabilidad de **plyr** a escritorio (Windows / Linux / macOS)

**Fecha:** 2026-10-09 · **Actualizado:** 2026-10-15
**Alcance analizado:** `app/src/main/java/com/plyr` (83 archivos Kotlin, ~16.500 líneas de producción) + 35 archivos de test JVM (~5.400 líneas), Gradle, `AndroidManifest.xml`.
**Objetivo del documento:** dejar claro el lenguaje/stack elegido, el **roadmap por hitos** (los `M0`–`M33` ya están completados, §6) y el **backlog de paridad pendiente** con Android (§6.4).

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
| F18 | Reproducción aleatoria (`<rnd>`) en playlists | `PlaylistScreen` + `PlaylistViewModel.startRandomizing` | **Presente (M20)**: `Shuffle` puro + `PlayerController.startRandomized`/`stopRandomized`; botón `<rnd>`/`<stop>` en el detalle de lista y `<rnd>` en el detalle de lista de YouTube. MPRIS `Shuffle` sigue siendo de solo lectura | **Alta** |
| F19 | Historial de búsqueda (no es hueco real) | `SearchViewModel` lo persiste, pero **`getAllSearches()` nunca se llama**: Android tampoco lo muestra | `SearchHistoryRepository` persiste igual; la UI no lo usa en ninguno de los dos | — |
| F20 | Paginación / «cargar más» en búsqueda | `SearchViewModel.onLoadMore` | **Presente (M21)**: `YouTubeManager.searchAll(page)` + `SearchController.loadMore` van añadiendo páginas; «cargar más» al pie de los vídeos con el umbral de Android (≥10). Android solo re-lanzaba la misma consulta | Media |
| F21 | Crear playlist buscando en YouTube | `CreatePlaylistScreen` | **Presente (M22)**: `CreatePlaylistView` busca en YouTube y acumula seleccionadas; al guardar nacen ya dentro de la lista | Media |
| F22 | Acciones por pista (like / añadir a cola / añadir a playlist) | `SongListItem` (menú contextual) | **Presente (M22)**: menú `⋯` por pista en detalle, cola y resultados (me gusta, añadir a cola, añadir a lista y compartir). El `ui/components/SongListItem.kt` de escritorio (muerto, con textos fijos en inglés) se eliminó | Baja |
| F23 | Metadatos del vídeo en el feed + reproducir desde el feed | `FeedScreen` + `MediaMetadataExtractor` | **Presente (M23)**: `FeedController`/`FeedScreen` enriquecen cada recomendación (título, autor y miniatura) con concurrencia acotada y, al pulsar, reproducen el vídeo o abren la lista | **Alta** |
| F24 | «Compartir app» por QR en Config | `ShareAppSection` + `ShareType.APP` | **Presente (M24)**: sección «compartir app» en `ConfigScreen` que abre `QrShareDialog` con `ShareType.APP` (enlace de descarga) | Baja |
| F25 | Recomendar al feed desde el diálogo de compartir | `QRDialog.RecommendButton` + `requestRecommendation` | **Presente (M25)**: botón «recomendar» en `QrShareDialog` que publica la URL compartida en el grupo general del feed con el apodo guardado | Media |
| F26 | Editar una playlist añadiendo pistas buscando en YouTube | `PlaylistScreen` (modo edición: buscador + `+`/`x`) | **Presente (M22)**: el modo edición añade un buscador de YouTube (`+`/`x`) junto a las pistas actuales | Media |

> **Nota:** las claves de traducción de F14/F22/F24 (`storage_*`, `delete_download`, `like`, `add_to_queue`, `share_me`, `add_to_playlist`) ya están en el catálogo compartido `:core` (`TranslationCatalog`). El escritorio ya consume `add_to_playlist`, `like`/`liked`, `add_to_queue` y `share` (F22, M22), desde M17 `storage_*` (almacén offline) y desde M24 `share_me` (compartir app); `delete_download` sigue listo para F14. M17 añadió además las claves `download`, `download_clean`, `download_downloading`, `download_done`, `download_partial`, `download_already` y `download_cancelled`; M19 añadió `youtube_playlists` y `saved`; M22, `search_tracks_label`/`tracks_count` y `save`/`cancel`; y M25 pinta `add_recommendation`/`recommendation_sent`/`recommendation_error` (ya presentes por Android y el feed).

> **Nota:** `MediaMetadataExtractor`, `ImportManifest`, `QrCodec`, `SpotifyImport` y `CoverCacheKey` ya viven en `:core` (`jvmSharedMain`), así que F23 fue más barato de lo que sugería su kdoc (ya corregido en M23).

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
| `QrScannerDialog` (leer) | **Descartar** en escritorio (sin cámara; se retiró en M28) | — (se elimina) | — |
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

Los hitos `M0`–`M33` **están completados** (§6.1–§6.2): el andamiaje, la lógica compartida, el motor de audio, la UI, la persistencia, la sincronización, QR/Spotify, el feed, la descarga offline, la UI de almacenamiento, el panel de listas de la Home, la búsqueda de listas de YouTube, la reproducción aleatoria, la paginación de la búsqueda, la creación/edición de listas con acciones por pista, los metadatos y la reproducción desde el feed, el compartir-app por QR en Ajustes, la recomendación al feed desde el diálogo de compartir, el volcado de la copia al cerrar, la cola accesible solo desde los controles, la búsqueda integrada en la Home (sin escáner QR), el `>` a listas y el engranaje de Ajustes en la Home, la rejilla adaptativa de la pantalla de listas con su botón de volver, los Ajustes limpios con el audio descargado junto al `plyr-sync.zip`, los logos ASCII aleatorios de la Home (los mismos PNG que Android) con la versión bajo el logo, el detalle de lista con la botonera de Android (sin «play all», `< back` a la izquierda del `rnd` y etiquetas `<shr>`/`<dwn>`/`<edt>`) y la descripción bajo el título pegada a la derecha con `~`, la integración MPRIS y el empaquetado ya funcionan en escritorio. No queda backlog de paridad abierto (§6.4).

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
- **M11 — QR + Spotify:** `QrCodec`/`QrImages` (ZXing, sin cámara) y `SpotifyImport` desacoplado de Android/Room. El lector sin cámara se retiró en M28; queda la generación/compartición de QR.
- **M12 — Feed (Supabase):** `FeedController`/`FeedScreen` con listado, publicación y apodo persistido.
- **M13 — Integración nativa:** MPRIS (Linux, D-Bus) y bandeja del sistema; SMTC/Now Playing quedan para más adelante.
- **M14 — Empaquetado:** `jpackage` vía plugin Compose (`.deb` verificado; `.rpm`/`.msi`/`.dmg` configurados).
- **M15 — Pulido:** atajos de teclado, `UpdateChecker` de escritorio, detekt con `detekt-baseline.xml`, checklist i18n y docs.
- **M16 — Descarga offline:** `DownloadPlan` (puro) a `:core`; `DownloadedAudioStore` (`java.nio`, `dataDir/audio/<videoId>.m4a`), `AudioDownloader` (OkHttp, con `AudioFetcher` inyectable) y `DownloadController` (`StateFlow`); `YouTubeTrackSourceResolver` prefiere el fichero local y deja el streaming de respaldo.
- **M17 — UI de almacenamiento offline:** `DownloadStatusBar` y `OfflineStorageDialog` en `:desktop`; botón descargar/limpiar en el detalle de lista, indicador `↓`/`↓ NN%` por pista y acción «borrar descarga»; sección de almacén en Ajustes (tamaño, nº de pistas y «borrar todo»).
- **M18 — Carrusel de playlists en Home:** `HomePlaylistCarousel`/`HomePlaylistItem`/`HomePlaylistCover` en `HomeScreen`; carga las listas visibles (mismo filtro que la pantalla de listas) con portada Coil 3 y abre el detalle al pulsarlas, manteniendo el menú de consola y el aviso de actualización.
- **M19 — Playlists de YouTube en búsqueda + detalle:** `YouTubeManager.searchAll`/`getPlaylistInfo`/`getPlaylistVideos` en `:core`; la búsqueda muestra vídeos y listas en dos secciones plegables (`v`/`>`) y `YouTubePlaylistDetailView` abre una lista, la reproduce entera y la guarda como lista local.
- **M20 — Reproducción aleatoria (`<rnd>`) en playlists:** `Shuffle` puro (semilla inyectable) y `PlayerController.startRandomized`/`stopRandomized` con `isRandomizing` en el estado; botón `<rnd>`/`<stop>` en el detalle de lista local y `<rnd>` en el detalle de lista de YouTube, sin salir de la pantalla (la barra flotante muestra lo que suena).
- **M21 — Paginación («cargar más») en búsqueda:** `YouTubeManager.searchAll(page)` avanza con `getPage(nextPage)` y expone `hasMore`; `SearchController.loadMore` **añade** la página siguiente (con `isLoadingMore` para no vaciar la lista) y `SearchScreen` muestra «> cargar más» al pie de los vídeos, con el umbral de Android (≥10).
- **M22 — Crear/editar lista buscando en YouTube + acciones por pista:** alta de lista (F21) y modo edición del detalle (F26) con un buscador de YouTube reutilizable (`TrackSearchController`, `YouTubeManager.searchAll … maxPlaylists = 0`) que acumula seleccionadas (`+`/`x`); al guardar/añadir nacen dentro de la lista. Menú `⋯` por pista común (`ui/components/TrackMenu.kt`, `TrackMenuActions`) en detalle, cola y resultados: me gusta reactivo (`PlaylistsController.likedKeys` observando `liked_songs`), añadir a cola (`PlayerController.addToQueue`) y a lista (`AddToPlaylistDialog`), y compartir (`QrShareDialog`).
- **M23 — Feed: metadatos del vídeo y reproducir desde el feed:** `FeedRemote` agrupa la red del feed (Supabase + `MediaMetadataExtractor`, ya en `:core`) y `FeedController` enriquece cada recomendación con título, autor y miniatura, con concurrencia acotada (`METADATA_CONCURRENCY = 4`) y caché por id. `FeedScreen` pinta la miniatura (Coil) y, al pulsar, reproduce el vídeo (`PlayerController.play`) o abre la lista; el click de playlist reutiliza el mismo camino que un `ScanResult` de QR (`AudioListScreen.openYouTubePlaylist` → `YouTubeManager.getPlaylistInfo` + `YouTubePlaylistDetailController`). Se conserva el formulario de publicación, el selector de grupo y el comentario (propios del escritorio).
- **M24 — «Compartir app» por QR en Config (F24):** `ConfigScreen` añade `ShareAppSection`, un botón `share_me` (testTag `share_app_button`) que abre `QrShareDialog` con un `ShareableItem` de tipo `ShareType.APP`; la URL la resuelve `ShareUrlPolicy.APP_DOWNLOAD_URL` (enlace de descarga de plyr) y el QR lo pinta `QrCodec`/`QrImages`, con «copiar enlace» y «guardar PNG» ya existentes de M11/M22. Sin claves nuevas: reutiliza `share_me` (ya usada por Android). Se extrajo el botón «volver» de Ajustes a `ConfigBackButton` para no superar el límite de longitud de detekt.

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
| M20 | Reproducción aleatoria (`<rnd>`) en playlists | 0.5 | ☑ |
| M21 | Paginación («cargar más») en búsqueda | 0.5 | ☑ |
| M22 | Crear/editar playlist con búsqueda + acciones por pista | 1 | ☑ |
| M23 | Feed: metadatos del vídeo y reproducir desde el feed | 0.5 | ☑ |
| M24 | «Compartir app» por QR en Config | 0.5 | ☑ |
| M25 | Recomendar al feed desde el diálogo de compartir | 0.5 | ☑ |
| M26 | Flush de copia de seguridad al cerrar la app | 0.5 | ☑ |
| M27 | Acceso a la cola solo desde controles (escritorio) | 0.5 | ☑ |
| M28 | Quitar QR scanning y mover búsqueda al inicio (escritorio) | 0.5 | ☑ |
| M29 | Home: `>` a listas, engranaje de Ajustes y sin franja inferior (escritorio) | 0.25 | ☑ |
| M30 | Pantalla de listas: rejilla adaptativa y botón de volver (escritorio) | 0.25 | ☑ |
| M31 | Ajustes de escritorio: limpieza y audio junto al sync | 0.25 | ☑ |
| M32 | Home: logos ASCII de Android aleatorios y versión bajo el logo | 0.25 | ☑ |
| M33 | Detalle de lista: botonera de Android y descripción bajo el título | 0.25 | ☑ |
| M29+ | Backlog de paridad con Android | — | ✔ sin pendientes |

### 6.3 Decisiones clave del port

- **Stack final:** Kotlin + Compose Multiplatform; `:core` KMP (`commonMain` puro + `jvmSharedMain` para `java.*` compartido JVM/Android + `jvmTest`); SQLDelight en vez de Room; VLCJ en vez de ExoPlayer; multiplatform-settings en vez de SharedPreferences; Coil 3; MPRIS tras `DesktopMediaIntegration`; `jpackage`.
- **Desviaciones conscientes (no son deuda):** sin NFC; sin CameraX (se pega URL / se abre imagen); sin sensor de luz (tema del SO); sin swipe (acciones contextuales con ratón); sin notificaciones nativas del SO (MPRIS); sin SAF (`java.nio`); listas creadas localmente (no de YouTube).
- **Código compartido en `:core`:** `MediaMetadataExtractor`, `ImportManifest`, `QrCodec`, `SpotifyImport`, `CoverCacheKey`, `formatTimestamp`, `PlaylistFilters`/`TrackIdentity`/`Tombstones`, `ShareUrlPolicy`/`PlaylistShare`.
- **Tests:** el conjunto `:core` + `:app` + `:desktop` cerró en **~723 tests** (los 455 originales + los nuevos de cada capa). `TranslationKeysUsageTest` escanea también `desktop`; detekt aplica `detekt-baseline.xml` por módulo para no fallar por los composables ya portados.
- **Historial detallado:** el registro de ejecución completo por hito (decisiones, desviaciones y recuentos de tests de M0–M15) vive en el historial de git del repositorio; aquí queda resumido a una línea por hito (§6.1).

### 6.4 Backlog de paridad pendiente (M29+)

Funciones de Android que **no** están en escritorio y **no** entraban en las desviaciones justificadas. Ordenadas por prioridad (impacto funcional). La prioridad «Alta» correspondía a F23 del inventario (§1.1), ya cubierta por M23. Tras cerrar **M29** (Home), **M30** (rejilla de listas), **M31** (Ajustes/audio), **M32** (logos ASCII de la Home) y **M33** (detalle de lista) no queda backlog abierto.

| Hito | Descripción | Prioridad | Semanas est. |
|---|---|---|---|
| —  | Sin pendientes (M29/M30/M31/M32/M33 cerrados) | — | — |

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
- `SearchScreen`: mostrar resultados de lista junto a los vídeos (mismo patrón que Android): dos secciones **colapsables** («vídeos» y «playlists», con el mismo `v`/`>` de Android). El botón «cargar más» de la sección de vídeos (F20) se pospuso a **M21** (ya hecho: ver más abajo).
- `YouTubePlaylistDetailView`: ver las pistas, reproducir todo y guardar como lista local (`PlaylistSource`).
**Definition of Done:** una búsqueda devuelve listas y se puede guardar/reproducir una.
**Verificación:** `:core:jvmTest` (con `assumeTrue` de red) + `:desktop:test` + manual.
**Prompt opencode:**
> Añade búsqueda de playlists de YouTube en `:core` (`searchAll`, `getPlaylistInfo`, `getPlaylistVideos`) y muéstralas en la búsqueda de `:desktop` junto a los vídeos. Implementa una vista de detalle que liste sus pistas, las reproduzca todas y permita guardarlas como lista local. Verifica con tests y manualmente.

**Implementado:** `:core` gana `YouTubePlaylistInfo`, `YouTubeSearchAllResult` y los métodos `searchAll`/`getPlaylistInfo`/`getPlaylistVideos` (más el helper `audioItemFrom`, compartido con `searchVideos`). La búsqueda de escritorio devuelve `SearchResults` (vídeos + listas) en una sola llamada de red; `SearchController.SearchState` añade `playlists`, `videosExpanded`/`playlistsExpanded` (con `toggleVideos`/`togglePlaylists`) y `hasResults`/`isEmptyResult` ampliados. `SearchScreen` pinta una `LazyColumn` con dos secciones plegables (`v`/`>`, cabeceras `search_videos_header`/`search_playlists_header`) y filas de lista con portada (`search_playlist_<id>`). Nuevos `YouTubePlaylistDetailController` (+`YouTubePlaylistDetailState`) y `YouTubePlaylistDetailView` en `com.plyr.desktop.search`: cargan las pistas, «play all» y guardar/quitar como lista local con `PlaylistRepository` (`remoteId = "youtube_<id>"`, `source = YOUTUBE`, `sourceId = <id>`, misma convención que Android). `AudioListScreen` muestra el detalle dentro de `Screen.SEARCH` cuando hay una lista abierta y salta a `Screen.QUEUE` al reproducir. 2 claves nuevas (`youtube_playlists`, `saved`). Tests: `YouTubePlaylistDetailControllerTest` (7), `YouTubePlaylistDetailViewTest` (6), `SearchScreenTest` (+4) y `SearchControllerTest` (+3), más 3 casos de red en `NetworkSmokeTest` (con `assumeTrue`).

---

#### M20 — Reproducción aleatoria (`<rnd>`) en playlists ☑ (completado)
**Objetivo:** botón `<rnd>` que baraja y reproduce una lista.
**Tareas**
- `PlayerController.startRandomized(tracks)` / stop; barajado puro y testeable.
- Cablear el botón en el detalle de lista.
**Definition of Done:** pulsar `<rnd>` reproduce las pistas en orden aleatorio.
**Verificación:** test puro del barajado + `:desktop:test`.
**Prompt opencode:**
> Añade a `:desktop` reproducción aleatoria en el detalle de playlist: un método `startRandomized` en `PlayerController` (barajado puro y testeable) y el botón `<rnd>` en la vista de lista. Verifica con un test del barajado.

**Implementado:** `com.plyr.desktop.player.Shuffle.randomized(items, random)` es una permutación pura con `Random` inyectable (semilla reproducible en tests). `PlayerController` gana `startRandomized(tracks, random)` (baraja y arranca) y `stopRandomized()` (para y vacía la cola); el body de `play` se extrajo a `startPlayback(tracks, startIndex, randomizing)` para compartirlo. `PlayerState` expone `isRandomizing`, que se resetea al parar, al vaciar la cola y al terminar la cola (`stopAtQueueEnd`). En la UI, `PlaylistDetailView` muestra `<rnd>`/`<stop>` (testTag `playlist_random`, mismo `playbackRandomButton` que Android, solo fuera del modo edición) y `YouTubePlaylistDetailView` un `<rnd>` puntual (testTag `youtube_playlist_random`, como el `onShuffle` de Android). Ninguno navega a la cola: la barra flotante muestra lo que suena, de modo que `<stop>` sigue accesible. Cableado en `PlaylistsScreen` y `AudioListScreen` (helper `List<TrackRecord>.toPlayerTracks()`). Tests: `ShuffleTest` (4), `PlayerControllerTest` (+4) y un caso de `<rnd>`/`<stop>` en `PlaylistDetailViewTest` (+2) y `YouTubePlaylistDetailViewTest` (+1).

---

#### M21 — Paginación («cargar más») en búsqueda ☑ (completado)
**Objetivo:** poder pedir más resultados, como el «cargar más» de Android (F20).
**Tareas**
- Soporte de paginación en `SearchController`/`SearchScreen` (`onLoadMore`), con el mismo umbral que Android (aparece «cargar más» cuando hay ≥10 resultados).
- *(Opcional, más allá de Android)* cablear `SearchHistoryRepository` para guardar cada búsqueda: en Android el historial se **persiste pero nunca se muestra** (`getAllSearches()` no se usa), así que no es un hueco de paridad.
**Definition of Done:** se pueden pedir más resultados desde la búsqueda.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Añade paginación («cargar más») a la búsqueda de `:desktop` (`SearchController`/`SearchScreen`), con el mismo umbral que Android. Verifica con tests. (El historial de búsqueda no hace falta para paridad: Android lo guarda pero no lo lista.)

**Implementado:** `:core` `YouTubeManager.searchAll(query, page, …)` avanza con `InfoItemsPage.getPage(nextPage)` hasta la página pedida y devuelve `YouTubeSearchAllResult.hasMore` (la UI de escritorio pasó a pedir página). `SearchController` cambia su función inyectada a `(query, page)`, añade `loadMore()` —que **acumula** la página siguiente sin vaciar la lista, con `isLoadingMore`— y `SearchState.canLoadMore` (≥10 vídeos como Android **y** `hasMore`, para no ofrecer un botón muerto). `SearchScreen` pinta «> cargar más» (`search_load_more`) al pie de los vídeos y «cargando» mientras llega la página. Nota: la paginación de Android era en realidad un no-op (re-lanzaba la misma consulta); aquí sí trae resultados nuevos. Tests: `SearchControllerTest` (+6) y `SearchScreenTest` (+4); un smoke de red (`NetworkSmokeTest`) cubre la página 2. **El historial de búsqueda se dejó fuera** (Android lo guarda pero no lo lista: no es paridad).

---

#### M22 — Crear/editar playlist con búsqueda + acciones por pista ☑ (completado)
**Objetivo:** crear o editar una lista agregando pistas mientras se busca en YouTube, y acciones por pista completas.
**Tareas**
- `CreatePlaylistView` con buscador de YouTube y lista de seleccionadas.
- Modo edición del detalle de lista (F26): buscador de YouTube para **añadir** pistas (`+`) junto a las actuales, además de quitarlas (`x`) y reordenarlas.
- Menú por pista (detalle, cola y resultados): me gusta (favoritos), añadir a cola (`PlayerController.addToQueue`, hoy ausente) y añadir a playlist, además de compartir.
- **Nota:** el `ui/components/SongListItem.kt` de escritorio estaba muerto (sin referencias) y usaba texto fijo en inglés («Play»/«Remove»); se eliminó al crear el menú nuevo `ui/components/TrackMenu.kt`, que sí usa `Translations`.
**Definition of Done:** se crea/edita una lista eligiendo pistas desde la búsqueda y las acciones por pista funcionan.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Amplía `:desktop` para (1) buscar en YouTube y elegir pistas al crear una lista y (2) añadir pistas a una lista existente desde el modo edición (buscador + `+`, junto a `x`/reordenar). Añade a las pistas (detalle, cola y resultados) un menú con me gusta, añadir a cola (`addToQueue` en `PlayerController`) y añadir a playlist, además de compartir. Verifica con tests.

**Implementado:** buscador de pistas reutilizable en `TrackSearchController` (`playlists/TrackSearchController.kt`) sobre `YouTubeManager.searchAll(…, maxPlaylists = 0)`; lo cablea `AudioListScreen` con una lambda estable. Alta (F21) y edición (F26) comparten el patrón «buscar → marcar → confirmar»: `CreatePlaylistView` guarda con `PlaylistsController.createPlaylist(title, description, tracks)` (normaliza `id`/`playlistId`/`position`) y `PlaylistDetailView` añade con el mismo `PlaylistRepository`; `PlaylistsController` ahora recibe `createPlaylist(…, tracks)` y `addTrackToPlaylist`. Menú común `ui/components/TrackMenu.kt` (`TrackMenuActions` + `itemsFor`) usado en cola (`QueueScreen`), resultados (`SearchScreen`) y detalle (`PlaylistTrackItem`), combinado con `AddToPlaylistDialog` (extraído de `SearchScreen`). Me gusta reactivo: `PlaylistsController.likedKeys` observa `LikedSongs.ID` y usa `TrackIdentity.likedTrackKey` (por `youtubeVideoId`; si falta, `name|artists`). `PlayerController.addToQueue` reemplaza la cola del motor al final (sin tocar el índice), cancela el prefetch y vuelve a `growWindow()`. Compartir por `QrShareDialog` en la raíz de `AudioListScreen`. Verificación: `:desktop:test` 295 (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings (detekt 0), `./run.sh test` 116/116 y `:core:jvmTest`.

---

#### M23 — Feed: metadatos del vídeo y reproducir desde el feed ☑ (completado)
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

**Implementado:** `FeedRemote` (Supabase + `MediaMetadataExtractor`, agrupados para inyectarlos) y `FeedController.enrichMetadata` extrae los metadatos de las recomendaciones nuevas con un `Semaphore(METADATA_CONCURRENCY = 4)` y los cachea por id en `FeedState.metadata`; un fallo de red deja la entrada sin enriquecer. `FeedScreen` pinta miniatura (Coil `AsyncImage`), título y autor (o la URL si no hay metadatos), conservando el comentario, el formulario de publicación y el selector de grupo, y hace la fila pulsable: `handleRecommendationClick` reproduce los vídeos (también los «desconocidos» con `videoId`) y las listas se abren con `AudioListScreen.openYouTubePlaylist` (el mismo camino que un `ScanResult("youtube", "playlist", …)` de QR: `YouTubeManager.getPlaylistInfo` + `YouTubePlaylistDetailController`, con la búsqueda como reserva). Se corrigió el kdoc obsoleto de `FeedScreen.kt`. Verificación: `:desktop:test` 302 (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest`.

---

#### M24 — «Compartir app» por QR en Config ☑ (completado)
**Objetivo:** paridad con `ShareAppSection` de Android.
**Tareas**
- Añadir a `ConfigScreen` una sección que comparta la app «plyr» (enlace/QR de descarga) reutilizando `QrShareDialog`/`QrCodec`.
**Definition of Done:** la sección aparece y genera el QR/enlace.
**Verificación:** `:desktop:test`.
**Prompt opencode:**
> Añade a `ConfigScreen` de `:desktop` una sección «compartir app» que genere el QR y el enlace de descarga de plyr, reutilizando `QrShareDialog`/`QrCodec`. Verifica con tests.

**Implementado:** `ConfigScreen` gana `ShareAppSection` (M24): un `ActionButton` con el texto `share_me` (testTag `share_app_button`) que abre el `QrShareDialog` ya existente con un `ShareableItem` de tipo `ShareType.APP`. Como `ShareUrlPolicy` resuelve `ShareType.APP` a `APP_DOWNLOAD_URL`, el diálogo pinta el QR del enlace de descarga de plyr (`QrCodec`/`QrImages`) y ofrece «copiar enlace» y «guardar PNG» (M11/M22). Sin claves nuevas (`share_me` ya estaba en el catálogo por Android); se extrajo el botón «volver» a `ConfigBackButton` para que `ConfigScreen` no superara el `LongMethod` de detekt. Tests: `ConfigScreenTest.comparteLaAppConSuQrDeDescarga` (pulsa el botón y comprueba el enlace de descarga en `qr_url`). Verificación: `:desktop:test` **303** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.

---

#### M25 — Recomendar al feed desde el diálogo de compartir ☑ (completado)
**Objetivo:** paridad con `RecommendButton`/`requestRecommendation` de Android: publicar la URL compartida en el feed (grupo general) usando el apodo guardado, sin pasar por la pantalla de Feed.
**Tareas**
- Añadir el botón «recomendar» a `QrShareDialog` (solo si hay URL) que llame a `SupabaseClient.postRecommendation` con el `DesktopConfig.userNickname`.
- Mensaje de estado (`recommendation_sent`/`recommendation_error`, ya en `:core`).
**Definition of Done:** desde el QR de una pista/lista se puede enviar la recomendación y aparece en el feed.
**Verificación:** `:desktop:test` + manual con red.
**Prompt opencode:**
> Añade al `QrShareDialog` de `:desktop` un botón «recomendar» que publique la URL compartida en el feed (grupo general) con el apodo de `DesktopConfig.userNickname`, reutilizando `postRecommendation` de `FeedController`/`SupabaseClient` y las claves `recommendation_sent`/`recommendation_error`. Verifica con tests.

**Implementado:** `QrShareDialog` recibe un `RecommendationSender` inyectable (`RecommendationSender.kt`, con `SupabaseRecommendationSender` por defecto) y añade `RecommendSection`, que solo aparece si hay URL: al pulsarlo publica la URL compartida en el grupo general del feed con `DesktopConfig.userNickname` (vía `SupabaseClient.getGroups()` + `createRecommendation`, el mismo camino que el `requestRecommendation` de Android) y muestra `recommendation_sent`/`recommendation_error`; sin apodo o sin grupo general falla sin tocar la red. Sin claves nuevas (reutiliza `add_recommendation`, `recommendation_sent` y `recommendation_error`, ya en `:core`). El kdoc del diálogo ya no dice que omita las recomendaciones. Tests: `QrShareDialogTest` (3: publica la URL y avisa de éxito, avisa del error, y sin URL no aparece el botón). Verificación: `:desktop:test` **306** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.

---

#### M26 — Flush de copia de seguridad al cerrar la app ☑ (completado)
**Objetivo:** paridad con Android `DataSync.flushOnStop`: no perder la copia pendiente al cerrar.
**Tareas**
- Evitar que `DataSync.shutdown()` cancele un flush pendiente sin intentar escribirlo. Añadir un método `flushOnStop()` (o ajustar shutdown) que intente `flush(force=false)` con un timeout corto (p.ej. 3–5s) sin bloquear indefinidamente.
- Llamarlo desde `AudioListScreen.onDispose` en vez de cancelar a ciegas.
- Mantener el comportamiento de `markDirty` y la comparación de huellas.
**Definition of Done:** si hay cambios pendientes al cerrar, se vuelca la copia de seguridad a la carpeta configurada (o se aguanta el timeout razonable) antes de salir.
**Verificación:** `:desktop:test` + manual (modificar lista, cerrar ventana antes de 30s; verificar `plyr-sync.zip`).
**Prompt opencode:**
> Modifica `DataSync.shutdown()` de `:desktop` para, cuando haya un `debounceJob` activo o se haya marcado sucio, intentar `flush(force=false)` con `withTimeout(5000)` antes de cancelar. En `AudioListScreen.onDispose`, llama a este shutdown modificado (o `dataSync.flushOnStop()`). Mantén la lógica de huellas y no rompas los tests existentes.

**Implementado:** `DataSync.flushOnStop(timeoutMs = STOP_TIMEOUT_MS)` (M26, `STOP_TIMEOUT_MS = 5_000`): si hay carpeta configurada y un `debounceJob` activo, lanza `flush(force = false)` en el scope interno y bloquea el hilo que cierra con `withTimeout` como máximo 5 s (escritorio no puede suspender en `onDispose`); si no hay nada pendiente no escribe, y en ambos casos acaba llamando a `shutdown()`. Se conservan `markDirty` y la comparación de huellas (`config.hash`). `AudioListScreen.onDispose` llama a `flushOnStop()` en lugar de `shutdown()`, así que un cambio hecho en los últimos 30 s ya no se pierde al cerrar. Tests: `DataSyncTest.cerrarVuelcaLaCopiaPendiente` (con `debounceMs = 60_000`: el ZIP aparece al cerrar) y `cerrarSinCambiosPendientesNoEscribe`. Verificación: `:desktop:test` **308** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.

---

#### M27 — Acceso a la cola solo desde controles (escritorio) ☑ (completado)
**Objetivo:** cambiar navegación en escritorio para acceder a la cola únicamente desde el indicador `^` del `FloatingMusicControls` (igual que en Android, que no tiene botón de cola en la barra/menu principal). **Solo afecta a UI de escritorio, no tocar Android.**
**Tareas**
- Quitar el botón `plyr_queue` del menú/navegación de escritorio (`Screen`, `AudioListScreen`, `MenuBar`/botones según corresponda).
- Asegurar que la cola solo se abre al pulsar el botón `^` de `FloatingMusicControls` en escritorio (ya existente).
- No añadir ni quitar rutas en Android; no tocar `com.plyr.ui.Screen` ni la navegación de Android.
**Definition of Done:** en escritorio no aparece el botón para ir a la cola desde la barra de navegación; sigue siendo accesible desde `^` en los controles flotantes. En Android sin cambios.
**Verificación:** `:desktop:test`, manual (comprueba menús).
**Prompt opencode:**
> Solo en `desktop/...` (no toques `app/src/...`), elimina cualquier entrada de navegación/botón que lleve a la cola desde el menú principal. La cola debe seguir abriéndose únicamente al pulsar el botón `^` de `FloatingMusicControls`. Revisa `Screen.kt`, `AudioListScreen.kt`, menús y tests de escritorio afectados. No modifiques nada de Android.

**Implementado:** `HomeScreen` ya no lista `Screen.QUEUE` en el menú de consola, así que la cola no tiene acceso desde la navegación principal; el `^` de `FloatingMusicControls` sigue siendo el único camino (`control_queue` → `onShowQueue` → `Screen.QUEUE`, ya existente). Como Android no navega a la cola al reproducir, se quitó también el `currentScreen = Screen.QUEUE` de `playTracks` en `AudioListScreen`: al pulsar «play all»/una pista, la barra flotante muestra lo que suena y la cola se abre con `^`. `Screen.QUEUE` se mantiene (identifica la pantalla interna y su título `plyr_queue`), solo desaparece del menú. Sin claves nuevas y Android intacto. Tests: `AudioListScreenTest.laColaNoEstaEnElMenu` (sustituye al antiguo «navega a cola desde el menú») y `FloatingMusicControlsTest` (2: el `^` avisa para mostrar la cola y sin reproducción no aparece). Verificación: `:desktop:test` **310** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.

---

#### M28 — Quitar QR scanning y mover búsqueda al inicio (escritorio) ☑ (completado)
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

**Implementado:** `SearchScreen` perdió el lector de QR (botón `[ scan_title ]` + `QrScanDialog` + el callback `onScanResult`); con él se borraron `qr/QrScanDialog.kt` y `qr/QrScan.kt` (código muerto), el `decodeImage` de `QrImages` (lado de lectura) y las 6 claves `scan_*` de los cuatro idiomas (ES/EN/CA/JA), además de la entrada del baseline de detekt. Se mantiene el QR de **compartir app** (M24) y la recomendación (M25). En `HomeScreen` la búsqueda ya no es una entrada del menú (`plyr_search`): es una barra `HomeSearchBar` (testTag `home_search_input`) **arriba y centrada** que se envía con Intro —sin botón— y muestra el placeholder `search_placeholder` («Buscar música…»); avisa con `onSearch` y `AudioListScreen` la conecta a `searchController.performSearch(query)` + `Screen.SEARCH`, de modo que los resultados siguen viviendo en `SearchScreen`. Además, las listas visibles pasan de un carrusel horizontal a un **panel vertical pegado a la izquierda** (`HomePlaylistRail`, testTag `home_playlists`), siempre visible y con **solo la portada** (sin nombres, para no cargar la Home), al estilo de la biblioteca de Spotify en escritorio: adelanta el grueso de M29 (queda el `>` a la pantalla de listas y quitar `plyr_lists` del menú). **Desviación consciente:** el prompt pedía quitar `SEARCH` del enum `Screen`, pero el objetivo/DoD exigen navegar a los resultados; se mantiene `Screen.SEARCH` (identifica `screen_SEARCH` y el destino) y solo desaparece como botón `plyr_search` del menú. Se limpiaron el formulario/estado de escaneo, `QrScan`/`QrScanDialog`, `QrImages.decodeImage`, sus tests y las 6 claves; nada de Android. Tests: `HomeScreenTest.escribirYBuscarAvisaConLaConsulta` (y `menu_SEARCH` ya no existe), `AudioListScreenTest.laBusquedaDeLaHomeAbreLosResultados` (sustituye a «el menú navega a buscar»), `QrImagesTest` sin las pruebas del lector (2: PNG con el tamaño de la matriz y `ImageBitmap`) y `SearchScreenTest` sin las 2 pruebas del lector. Verificación: `:desktop:test` **305** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.

---

#### M29 — Home de escritorio: `>` a listas, engranaje de Ajustes y sin franja inferior ☑ (completado)
**Objetivo:** reemplazar el acceso a `plyr_lists` por un panel vertical de portadas a la izquierda con un `>` que lleve a `PlaylistsScreen`, mover Ajustes a un engranaje junto al buscador y no reservar hueco inferior cuando no hay reproducción. **Solo UI de escritorio.**
**Tareas**
- En `HomeScreen` de escritorio, mostrar las portadas de las listas visibles en el panel vertical ya existente (M28).
- Mostrar miniaturas (Coil) sin nombre; al pulsar una portada, abrir su detalle (`PlaylistDetailView`).
- Añadir un botón `>` al final del panel que navegue a `PlaylistsScreen` (mismo destino que el antiguo botón `plyr_lists`).
- Quitar `plyr_lists` (y `config`) del menú de escritorio; los Ajustes pasan a un engranaje `⚙` a la derecha del buscador.
- Quitar el divisor entre versión y menú y reservar el hueco inferior solo con la barra flotante visible.
- No tocar Android.
**Definition of Done:** Home muestra el panel de listas y el `>` lleva a la pantalla de listas; `plyr_lists` y `config` ya no aparecen en el menú; sin reproducción no queda franja inferior. Android sin cambios.
**Implementado:** el panel `HomePlaylistRail` (M28) recibe `onNavigate` y añade al final un `ActionButton` `>` (testTag `home_more_lists`) que abre `Screen.PLAYLISTS`; el menú queda reducido a `feed`. Ajustes se abre con `HomeConfigButton` (símbolo `PlyrSymbols.GEAR = "⚙"`, testTag `home_config`) alineado a la derecha del buscador en la cabecera. Se elimina el `AsciiArt.DIVIDER` entre la versión (que sigue bajo el logo) y el menú. En `AudioListScreen`, el `padding(bottom = CONTROLS_RESERVED_HEIGHT)` del contenido pasa a aplicarse solo cuando `controlsVisible` (título/carga/error), de modo que sin reproducción no queda franja fija y el panel de listas llega hasta el fondo. Tests: `HomeScreenTest.laFlechaDelPanelAvisaParaAbrirListas`/`elEngranajeAvisaParaAbrirAjustes` y `AudioListScreenTest.laFlechaDelPanelAbreListas`; `menu_CONFIG` → `home_config` y el caso sin listas comprueba el panel visible pero vacío.
**Verificación:** `:desktop:test` (308) + manual.
**Prompt opencode:**
> Solo en `desktop/...`, modifica `HomeScreen` para mostrar un carrusel horizontal con portadas de playlists (usa datos existentes de PlaylistsController/repositorio). Añade un botón `>` al final que navegue a la pantalla de playlists. Elimina la entrada/navegación a `PLAYLISTS` desde el menú principal de escritorio. No modifiques código de `app/src/...`.

---

#### M30 — Pantalla de listas: rejilla adaptativa y botón de volver (escritorio) ☑ (completado)
**Objetivo:** que `PlaylistsScreen` de escritorio se parezca al grid de Android —rejilla adaptativa de portadas en vez de listado— y que siempre haya un botón de volver visible. **Solo UI de escritorio.**
**Tareas**
- Sustituir el `LazyColumn` de listas por un `LazyVerticalGrid(GridCells.Adaptive(minSize = 150.dp))` con portada + nombre + nº de pistas, como `PlaylistGridView` de Android.
- Mover el `+` (crear lista) al `trailing` del `Titulo`, como Android, y añadir el estado vacío `no_playlists`.
- Añadir un botón `< back` visible (el escritorio no tiene back del sistema) que vuelva a la Home.
- No tocar Android.
**Definition of Done:** `PlaylistsScreen` muestra las listas en rejilla adaptativa con las portadas y el botón `< back` siempre visible; sin listas muestra `no_playlists`. Android sin cambios.
**Implementado:** `PlaylistGridView` pasa a ser extensión de `ColumnScope` y pinta su propia cabecera (`Titulo` con `+`, testTag `playlists_create`), la `DownloadStatusBar` y una `LazyVerticalGrid` adaptativa (`GridCells.Adaptive(minSize = 150.dp)`, separación de `PlyrSpacing.large`) con `PlaylistGridItem` (portada de 150 dp con esquinas redondeadas, corazón para favoritos, nombre a dos líneas y `tracks_count`); al final, un `ActionButton` `< back` (testTag `back_from_PLAYLISTS`) que antes quedaba empujado fuera de pantalla por el listado. La cabecera duplicada del `PlaylistsScreen` exterior se retira: cada rama (rejilla, detalle, alta) pinta la suya. El botón de volver del detalle también pasa a `< back` (testTag `back_from_playlist_detail`, antes `← close`). Se añade la clave `back` a los cuatro idiomas (ES/EN/CA/JA) y se quita el `create_playlist` del listado (sigue usándose en el alta). Tests: nuevo `PlaylistsScreenTest` (4): rejilla + botón de volver visibles, el volver avisa, el `+` abre el alta y sin listas aparece `no_playlists`.
**Verificación:** `:desktop:test` **312** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434.
**Prompt opencode:**
> Ahora vamos a pasar a solucionar la `playlist_screen` porque ahora mismo no se parece en nada a la de Android: quiero que sea un grid, no un listado, igual que el de Android, que sea adaptativo, y añade un botón de back que sea algo como `< back`.

---

#### M31 — Ajustes: limpieza de secciones y audio en la carpeta de sync (escritorio) ☑ (completado)
**Objetivo:** dejar los Ajustes de escritorio más limpios (sin los títulos de sección ni la sección de audio offline) y hacer que el audio descargado viva en la misma carpeta que el `plyr-sync.zip`, como Android. **Solo escritorio.**
**Tareas**
- Quitar los títulos de sección (`paths_title`, `sync`, `spotify_import_title`) y la sección completa de audio offline (título, resumen «no downloaded audio» y botón «limpiar»).
- Cambiar el botón de volver de `← close` a `< back`.
- Descargar el audio a `<carpeta de sync>/audio` (o `dataDir/audio` si no hay carpeta de copia), como Android.
- Quitar el botón de importar de Spotify (se lanza con Intro), la fila de la carpeta de copia (ya está en la etiqueta `synced w/ …`) y la sección de **repetición**; pintar tema e idioma como tiras `opción / opción / opción`, igual que Android.
- No tocar Android.
**Definition of Done:** Ajustes sin los títulos ni la sección offline, sin botón de importar (Entrar lanza la importación), sin fila de carpeta de copia y sin repetición; tema/idioma como tiras `opción / opción / opción`; botón `< back`; el audio descargado aparece junto al `plyr-sync.zip`. Android sin cambios.
**Implementado:** en `ConfigScreen` se eliminan los `Subtitulo` de rutas, sync y Spotify (se mantienen botones y campos), la `OfflineStorageSection` completa y su parámetro `downloadController` (ajustado el cableado en `AudioListScreen`), la sección de repetición entera (con su `onRepeatModeSelected`) y la fila `path_backup` (la carpeta ya se anuncia en el botón `synced w/ …`); el botón de volver pasa a `< back`. La importación de Spotify pierde el botón: con `KeyboardOptions(imeAction = ImeAction.Go)`/`KeyboardActions(onGo = …)` se lanza al pulsar Intro, como Android. El antiguo `OptionSection` (botones con `●`) se sustituye por `ToggleRow`: una tira controlada `título` + `opción / opción / opción` con la activa en color primario, igual que el `MultiToggle` de Android. El campo de importar de Spotify pasa a `placeholder` (con el texto de Android «spotify playlist url or id»), la sección de copia queda debajo del campo y los botones de sync y «share me» se centran. Se borran las claves `paths_title`, `spotify_import_title`, `repeat`, `repeat_off`, `repeat_one`, `repeat_all`, `backup_folder` y `spotify_import_go` de ES/EN/CA/JA (eran sus únicas referencias; `sync`, `storage_title` y `download_clean` siguen usándose en Android y en el detalle de lista). El audio descargado pasa a `<carpeta de sync>/audio` (respaldo `dataDir/audio`): `DownloadedAudioStore` resuelve la carpeta en cada uso leyendo `BackupConfig`, con constructor `internal` que inyecta el `BackupConfig` para los tests; `AudioListScreen` mantiene `DownloadedAudioStore()` por defecto. Tests: `DownloadedAudioStoreTest.porDefectoUsaLaSubcarpetaAudioDeLaCarpetaDeSync`/`sinCarpetaDeSyncCaeALaCarpetaDeDatos`; `ConfigScreenTest` pierde los casos de repetición y el de la sección offline, y el de Spotify pasa a `performImeAction`.
**Verificación:** `:desktop:test` **311** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434 (1 omitido de red, `NetworkSmokeTest`).
**Prompt opencode:**
> En Ajustes elimina el título de storage, el de sync y el de import from spotify, la sección completa de offline audio, el botón de clean y el texto «no downloaded audio»; cambia el botón de close por `< back`. En Android el audio descargado vive en la misma carpeta que el `sync.zip`; haz que en escritorio también. No toques Android.
>
> Además, elimina el botón de importar de settings (debe importar al pulsar Intro), quita la fila de backup folder (basta con que salga en el botón `synced w/ …`), cambia tema e idioma para que sean como Android (`opción / opción / opción`) y elimina el repeat.

---

#### M32 — Logos ASCII de la Home: los mismos que Android, aleatorios (escritorio) ☑ (completado)
**Objetivo:** sustituir el banner ASCII fijo de la Home de escritorio (un placeholder de texto) por **los mismos PNG que usa Android** (`ascii_1..50`, en `drawable-nodpi`), eligiendo uno al azar cada vez que se compone la Home, y colocar el número de versión **debajo y a la derecha** del logo. **Solo escritorio.**
**Tareas**
- Copiar los `ascii_1..50.png` de `app/src/main/res/drawable-nodpi` a `desktop/src/main/resources/ascii/` (los recursos de imagen que Compose Desktop carga del classpath).
- Cargar el logo como `ImageBitmap`, teñido con el color primario del tema (igual que el `ColorFilter.tint` de Android) y ajustado a caja con `ContentScale.Fit`/`aspectRatio` como el `HomeLogoWithVersion` de Android.
- Elegir el índice al azar una vez por composición (`remember`), con un parámetro `logoIndex` opcional para que los tests sean deterministas.
- Poner la versión en una columna alineada al final (derecha), justo debajo del logo.
- No tocar Android.
**Definition of Done:** la Home muestra uno de los 50 PNG de Android al azar por carga, teñido con el color primario, y la versión queda debajo a su derecha; Android sin cambios.
**Implementado:** `AsciiArt` (antes un `LOGO`/`DIVIDER` de texto) pasa a exponer `COUNT = 50`, `DIR = "ascii"`, `randomIndex()`, `resourcePath(index)` y `load(index)`, que lee el PNG del classpath con `useResource`/`loadImageBitmap` y devuelve `null` si faltara (sin tumbar la Home). Los 50 PNG se copian de `app/src/main/res/drawable-nodpi` a `desktop/src/main/resources/ascii/` (copia consciente: `:desktop` no debe tirar del `res` de `:app`). `HomeScreen` gana el parámetro `logoIndex: Int? = null` y elige `logoIndex ?: AsciiArt.randomIndex()` dentro de un `remember` (una vez por composición, como el `remember { ids.random() }` de Android), que pasa a `HomeMainContent`/`HomeLogo`. El nuevo `HomeLogo` (testTag `home_logo`) es una `Column(horizontalAlignment = End)` con la `Image` teñida con `primary` y, debajo, el `HomeVersion` ya existente (testTag `home_version`), de modo que la versión queda **a la derecha del logo, debajo de él**, igual que el `HomeLogoWithVersion` de Android; el tamaño máximo del logo es 280×220 dp. Se eliminan `AsciiArt.LOGO`/`AsciiArt.DIVIDER` (el `DIVIDER` ya no se usaba desde M29). Tests: `AsciiArtTest` (los 50 recursos cargan, el índice aleatorio cae en `1..50`) y `HomeScreenTest.laVersionVaDebajoDelLogo…` (el logo se muestra y la versión queda bajo su borde inferior y alineada a su derecha); el resto de tests de la Home pasan `logoIndex` fijo o ignoran el logo.
**Verificación:** `:desktop:test` **315** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434 (1 omitido de red, `NetworkSmokeTest`).
**Prompt opencode:**
> En la Home de escritorio quiero usar las mismas imágenes ASCII que usa Android en vez del placeholder de texto, y que salgan aleatorizadas cada vez que se carga la app igual que en Android. Además, pon el número de la versión debajo a la derecha de la imagen del ASCII que se cargue en ese momento. No toques Android.

---

#### M33 — Detalle de lista: botonera de Android y descripción bajo el título (escritorio) ☑ (completado)
**Objetivo:** acercar el detalle de lista de escritorio al de Android: quitar por completo el «play all», dejar la botonera con las mismas etiquetas (`<shr>`, `<dwn>`, `<edt>`…), poner el botón de volver a la izquierda del `rnd` y mostrar la descripción de la lista justo debajo del título, pegada a la derecha y con `~` delante (p. ej. `~Imported from Spotify`). **Solo escritorio.**
**Tareas**
- Quitar por completo el botón «play all» (`playlist_play_all`) del detalle de lista local.
- Mover el botón de volver (`back_from_playlist_detail`) del pie al principio de la botonera, a la izquierda del `rnd`.
- Cambiar las etiquetas de compartir/descargar/editar por las de Android: `<shr>`, `<dwn>` (con `<stop>` mientras descarga y `<clr>` cuando ya está todo) y `<edt>`.
- Pintar la descripción (solo en modo normal) en una línea propia bajo el título, pegada a la derecha (`fillMaxWidth` + `TextAlign.End`), con `~` y recortada a 2 líneas.
- Borrar las claves que quedan sin uso (`download`, `download_clean`, `edit`) de ES/EN/CA/JA.
- No tocar Android.
**Definition of Done:** el detalle de lista muestra `[< back] [<rnd>] [<shr>] [<dwn>] [<edt>]` (sin «play all»), la descripción sale bajo el título pegada a la derecha con `~` y `TranslationKeysUsageTest` sigue verde. Android sin cambios.
**Implementado:** en `PlaylistDetailView` se elimina el `ActionButton` de `play_all` y su `testTag("playlist_play_all")`; el botón de volver se traslada al inicio de la fila de acciones (mismo `testTag` y misma lógica de descartar cambios sin guardar) y ya no se pinta al pie. Las etiquetas pasan a los literales de Android: `<shr>` para compartir, `<dwn>`/`<stop>`/`<clr>` para descargar/limpiar y `<edt>` para editar. La nueva `PlaylistDescription` (testTag `playlist_description`) pinta `~<descripción>` en `bodySmall` monoespaciado con `TextAlign.End`, `fillMaxWidth`, `maxLines = 2` y elipsis, justo debajo del título y antes de la botonera; sustituye al bloque de descripción que había al final del modo normal. Como `download`, `download_clean` y `edit` dejan de usarse (solo los usaba este archivo), se borran de las cuatro lenguas del catálogo. Tests: `PlaylistDetailViewTest.laBotoneraUsaLasEtiquetasDeAndroidYSinPlayAll` (atrás y `rnd` presentes, sin `playlist_play_all`, etiquetas `<shr>`/`<dwn>`/`<edt>`) y `laDescripcionSaleBajoElTituloConTilde` (`~Imported from Spotify`), con el helper `seedShareablePlaylist`.
**Verificación:** `:desktop:test` **317** (4 omitidos: 2 de bandeja AWT sin entorno gráfico y 2 de VLC), `:desktop:detektMain` sin findings, `./run.sh check` sin findings, `./run.sh test` 116/116 y `:core:jvmTest` 434 (1 omitido de red, `NetworkSmokeTest`).
**Prompt opencode:**
> En el detalle de lista del escritorio quita por completo el play all y deja solo el rnd; añade un botón `< back` a la izquierda del rnd; cambia el share por `<shr>`, el download por `<dwn>` y el edit por `<edt>`, igual que en Android. Además, pon la descripción de la lista de la misma forma que en Android: justo debajo del título, pegada a la derecha del todo y con `~` delante (p. ej. `~Imported from Spotify`). No toques Android.

---

**Diferencias menores / desviaciones conscientes:** no hay layout apaisado/tablet «side-by-side» (la ventana es redimensionable); compartir usa el portapapeles en vez de la hoja del sistema (`ACTION_SEND`); sin háptica; el reordenado de la **cola** usa botones `^`/`v` (Android arrastra) — el detalle de lista sí admite arrastre; MPRIS publica `Volume`/`LoopStatus`/`Shuffle` como **solo lectura** fijos (el `Set` de `MprisPlayer` lanza `PropertyReadOnly`) y `OpenUri` es un no-op, mientras que `Seek`/`SetPosition` sí funcionan; Android usa `unknown_artist` para pistas sin artista y una etiqueta `loading_tracks` al cargar una lista, escritorio pinta el valor crudo (cosmético); `exit_message` de Android es **código muerto** (`showExitMessage` nunca pasa a `true`); la copia de seguridad automática en escritorio ahora vuelca el flush pendiente al cerrar (`DataSync.flushOnStop()`, M26), igual que Android en `onStop` (ya no cancela a ciegas); **UI escritorio (M28–M29):** sin escáner QR de escritorio (M28 retiró el lector sin cámara `QrScanDialog`/`QrScan` y sus claves `scan_*`; sigue el botón «compartir app» con QR de M24 y la recomendación de M25), búsqueda en la Home arriba y centrada sin botón (se envía con Intro, con placeholder «buscar música…»), biblioteca de playlists en vertical a la izquierda estilo Spotify, siempre visible y solo con portadas, con un `>` al final que abre la pantalla de listas, y Ajustes como engranaje a la derecha del buscador (menú reducido a `feed`); sin reproducción no se reserva hueco inferior (M28–M29; la cola ya solo se abre desde `^` en controles, M27); en Ajustes ya no hay títulos de sección ni el panel de audio offline, tema e idioma son tiras `opción / opción / opción` (sin sección de repetición) y el audio descargado se guarda en `<carpeta de sync>/audio` (o `dataDir/audio` como respaldo), como Android (M31); el logo ASCII de la Home son los mismos `ascii_1..50` de Android, elegidos al azar por carga y teñidos con el color primario, con la versión justo debajo a su derecha (M32); el detalle de lista usa la botonera de Android (sin «play all», con `< back` a la izquierda del `rnd` y etiquetas `<shr>`/`<dwn>`/`<edt>`), muestra la descripción bajo el título pegada a la derecha con `~` y guarda el volver y las acciones en la misma fila (M33).

**A favor del escritorio (no perder):** el feed tiene formulario de publicación + selector de grupo + muestra el `comment` (Android no); Config tiene filas extra de **rutas de datos/caché** (Android no); atajos de teclado, bandeja del sistema (`DesktopTray`) y prueba de sonido por CLI (`SoundCheck`).

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
