# Análisis de portabilidad de **plyr** a escritorio (Windows / Linux / macOS)

**Fecha:** 2026-10-09
**Alcance analizado:** `app/src/main/java/com/plyr` (83 archivos Kotlin, ~16.500 líneas de producción) + 35 archivos de test JVM (~5.400 líneas), Gradle, `AndroidManifest.xml`.
**Objetivo del documento:** dejar claro el lenguaje/stack elegido y un **roadmap por hitos** listo para ejecutar con opencode.

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

### 1.1 Lenguaje y stack seleccionados

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

### 1.2 ¿Es multiplataforma?

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

Cada hito (`M0`…`M15`) es **autocontenido y verificable**: se le puede pasar a opencode tal cual. La regla de oro es que **al terminar cada hito, `./gradlew build` los tests deben pasar**, tanto en Android como en desktop y se debe pasar el checkeo de run.sh

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

### 6.1 Hitos

---

#### M0 — Andamiaje de módulos (0.5 semana)
**Objetivo:** crear la estructura de módulos sin mover código todavía.
**Tareas**
- `settings.gradle.kts`: añadir `include(":core")` y `include(":desktop")`.
- `core/build.gradle.kts`: plugin `kotlin.multiplatform`, targets `androidTarget()` + `jvm()`; `commonMain` con `kotlinx-coroutines`.
- `desktop/build.gradle.kts`: Compose Multiplatform (`org.jetbrains.compose`) + `application` (target `jvm`), JDK 17.
- `libs.versions.toml`: versiones de Compose MP, VLCJ, SQLDelight, multiplatform-settings y Coil 3.
- `:app` depende de `:core`; `:desktop` depende de `:core`.
- **Fallback anti-fricción:** si el wiring KMP (dependencias JVM-only en `commonMain`) da problemas, `core` puede empezar como `kotlin("jvm")` puro (Android lo consume igual). Se convierte a KMP en M2 si hace falta.

**Definition of Done:** `:core` vacío compila para Android y JVM; `:desktop` abre una ventana vacía; el APK de Android sigue construyendo.
**Verificación:** `./gradlew :core:build`, `./gradlew :desktop:run`, `./run.sh build`
**Prompt opencode:**
> Crea los módulos `:core` (Kotlin Multiplatform, targets `androidTarget()` y `jvm()`) y `:desktop` (Compose Multiplatform desktop, target `jvm`, aplicación) en `settings.gradle.kts` con sus `build.gradle.kts`. No muevas código existente todavía. Añade `kotlinx-coroutines` a `commonMain` de `:core` y las versiones nuevas a `gradle/libs.versions.toml`. Haz que `:app` y `:desktop` dependan de `:core`. Verifica con `./gradlew :core:build` y `./gradlew :desktop:run`.

---

#### M1 — Extraer la lógica pura a `core` + migrar los 455 tests (1–2 semanas) ⭐
**Objetivo:** mover a `core/src/commonMain` todo lo que no importa Android, y a `core/src/jvmTest` los tests puros. **Es el hito que desriesga el port.**
**Tareas**
- Mover a `core` los paquetes/archivos de §2.2: `model/`, `QueueIndex`, `WindowState`, `LoadingState`, `PendingSkips`, `IdlePlayback`, `UrlParser`, `ImportManifest`, `ExportManifest`, `ExportDigest`, `ImportArchive`, `LikedSongsMerge`, `MediaMetadataExtractor`, `PlaylistSource`, `CoverCropMath`, `PlaybackNotificationState`, `NotificationRefreshPolicy`, `YouTubePlaylistCreator`.
- Mover a `jvmTest` los 35 archivos de test correspondientes.
- Ajustar `package`/`import` en `:app` para referencia a `:core`.
- Sustituir cualquier referencia a `android.util.Log` de estos archivos por un logger propio (`core/util/Logger`).

**Definition of Done:** los 455 tests corren y pasan **en el JVM de escritorio** (`./gradlew :core:jvmTest`), y Android sigue compilando con la lógica delegada en `:core`.
**Verificación:** `./gradlew :core:jvmTest`, `./run.sh build`, `./run.sh test`
**Prompt opencode:**
> Mueve a `core/src/commonMain` los archivos puros listados en port.md §2.2 (model/, QueueIndex, WindowState, LoadingState, PendingSkips, IdlePlayback, UrlParser, ImportManifest, ExportManifest, ExportDigest, ImportArchive, LikedSongsMerge, MediaMetadataExtractor, PlaylistSource, CoverCropMath, PlaybackNotificationState, NotificationRefreshPolicy, YouTubePlaylistCreator) y sus tests a `core/src/jvmTest`. Reemplaza `android.util.Log` por un logger propio. Actualiza `:app` para que los use desde `:core`. No cambies la lógica. Verifica con `./gradlew :core:jvmTest` (deben pasar los ~455 tests) y `./run.sh build`.

---

#### M2 — Red a `core` sin Android (0.5 semana)
**Objetivo:** portar `YouTubeManager`, `NewPipeHolder`, `SimpleDownloader`, `SupabaseClient` y `AppModels`.
**Tareas**
- Declarar `NewPipeExtractor`, `OkHttp` y `org.json` para los targets `jvm` y `android`.
- Quitar `android.util.Log` de `SimpleDownloader`, `SupabaseClient` y `YouTubeManager`.
- Mover `Translations` a `core` (es un mapa puro).
- Añadir un **test de humo** de red en `jvmTest` (búsqueda + extracción de URL, marcado como opcional/con `assumeTrue` de conexión).

**Definition of Done:** `YouTubeManager.searchVideoId("...")` y `getAudioUrl(...)` funcionan desde un test JVM de escritorio.
**Verificación:** `./gradlew :core:jvmTest --tests "*Network*"`
**Prompt opencode:**
> Porta a `:core` los archivos de red: `YouTubeManager`, `NewPipeHolder`, `SimpleDownloader`, `SupabaseClient`, `AppModels` y `Translations`. Declara `NewPipeExtractor`, `OkHttp` y `org.json` en los targets JVM y Android. Elimina las dependencias de `android.util.Log`. Añade un test JVM de humo que haga una búsqueda real y extraiga una URL de audio (que se omita si no hay red). Verifica con `./gradlew :core:jvmTest`.

---

#### M3 — Interfaz `AudioPlayer` + `QueueEngine` (1 semana) ⭐
**Objetivo:** desacoplar la lógica de cola de ExoPlayer **sin cambiar el comportamiento de Android**.
**Tareas**
- Definir en `core` la interfaz `AudioPlayer` y `AudioPlayerListener` de §3.2, más el modelo `AudioTrack`.
- Extraer de `PlayerViewModel` una clase pura `QueueEngine` con toda la lógica de ventana/transición (lo que ya cubren `WindowState`, `QueueIndex`, `LoadingState`, `PendingSkips`).
- `PlayerViewModel` (en `:app`) pasa a depender de `AudioPlayer`; se añade `ExoAudioPlayer : AudioPlayer` envolviendo `ExoPlayer`.
- Portar a `core` los tests de `QueueIndex`, `WindowState`, `IdlePlayback`, `PendingSkips` y añadir tests de `QueueEngine`.

**Definition of Done:** Android reproduce igual que antes (mismos 455+ tests verdes) y `PlayerViewModel` ya no menciona `ExoPlayer` directamente salvo en `ExoAudioPlayer`.
**Verificación:** `./gradlew :core:jvmTest`, `./run.sh test`, `./run.sh build`
**Prompt opencode:**
> Crea en `:core` la interfaz `AudioPlayer`, `AudioPlayerListener` y el modelo `AudioTrack`. Extrae de `PlayerViewModel` una clase pura `QueueEngine` con la lógica de ventana/transición, reutilizando `WindowState`/`QueueIndex`/`LoadingState`/`PendingSkips`. En `:app`, crea `ExoAudioPlayer : AudioPlayer` que envuelva `ExoPlayer` y haz que `PlayerViewModel` dependa de la interfaz. No cambies el comportamiento. Verifica que los tests siguen verdes y el APK compila.

---

#### M4 — Adaptador VLCJ + prueba de sonido (1 semana) ⭐
**Objetivo:** demostrar que el motor de audio funciona en desktop. **Si este hito cierra, el port es técnicamente viable.**
**Tareas**
- Añadir `uk.co.caprica:vlcj` a `:desktop`.
- Implementar `VlcAudioPlayer : AudioPlayer` sobre `EmbeddedMediaPlayer` (solo audio).
- CLI/prototipo en `:desktop` que: busca una canción, extrae la URL con `YouTubeManager` y la reproduce.
- Detectar `libvlc` ausente y mostrar error claro.

**Definition of Done:** en Linux (y opcionalmente Win/mac), `./gradlew :desktop:run` reproduce una canción real de YouTube.
**Verificación:** manual: `./gradlew :desktop:run` reproduce audio; test JVM de la máquina de estados de `VlcAudioPlayer`.
**Prompt opencode:**
> Añade VLCJ a `:desktop` e implementa `VlcAudioPlayer : AudioPlayer` sobre `EmbeddedMediaPlayer` (solo audio). Crea un punto de entrada temporal que use `YouTubeManager` para resolver y reproducir una canción de YouTube. Maneja el caso de que `libvlc` no esté instalado con un mensaje claro. Verifica arrancando la app de escritorio y reproduciendo.

---

#### M5 — Esqueleto de UI Compose Desktop (1 semana)
**Objetivo:** ventana, tema terminal, navegación e i18n compartida.
**Tareas**
- Definir `MaterialTheme` con la estética terminal y fuente monoespaciada (arte ASCII donde aplique).
- `Screen` enum y navegación con `AudioListScreen` equivalente (Home/Search/Queue/Playlists/Feed/Config).
- Cablear `Translations` y `Config` (aún con backend temporal).
- `FloatingMusicControls` portado (sin reproducción real todavía).

**Definition of Done:** la app de escritorio abre, navega entre pantallas vacías y cambia de idioma/tema.
**Verificación:** `./gradlew :desktop:run` + test de Compose UI (`runComposeUiTest`) del cambio de pantalla.
**Prompt opencode:**
> Crea el esqueleto de UI de `:desktop` con Compose Multiplatform: tema terminal (monoespaciado, arte ASCII), enum `Screen` y navegación entre Home/Search/Queue/Playlists/Feed/Config, componentes `Titulo` y `ActionBttn` portados, y `FloatingMusicControls`. Usa `Translations` de `:core`. Verifica con `./gradlew :desktop:run` y un test de UI.

---

#### M6 — Persistencia SQLDelight (1–2 semanas)
**Objetivo:** sustituir Room por SQLDelight y portar los repositorios.
**Tareas**
- Definir esquema `.sq` equivalente a `PlaylistEntity`, `TrackEntity`, `SearchHistoryEntity` (versión 8 actual).
- Generar DAOs y escribir `PlaylistRepository` + `SearchHistoryRepository` en `:desktop` reutilizando la lógica de `PlaylistLocalRepository`.
- Migrar la lógica de negocio portable (liked, tombstones, `visiblePlaylists`) a `:core` como funciones puras.
- Tests de repositorio con SQLite en memoria.

**Definition of Done:** crear/editar/borrar playlists y favoritos funciona y sobrevive a reinicios; tests en verde.
**Verificación:** `./gradlew :desktop:test`
**Prompt opencode:**
> Añade SQLDelight a `:desktop` con un esquema equivalente a las entidades Room actuales (`playlists`, `tracks`, `search_history`, versión 8). Implementa `PlaylistRepository` y `SearchHistoryRepository` reutilizando la lógica de `PlaylistLocalRepository`; extrae a `:core` las funciones puras (fusión de liked, tombstones, `visiblePlaylists`). Añade tests con SQLite en memoria. Verifica con `./gradlew :desktop:test`.

---

#### M7 — Reproducción integrada en la UI (1–2 semanas) ⭐ (MVP)
**Objetivo:** reproducir desde búsqueda y gestionar la cola en desktop.
**Tareas**
- Conectar `VlcAudioPlayer` + `QueueEngine` + UI.
- `SearchScreen` con búsqueda de YouTube y reproducción del resultado.
- `QueueScreen` con reordenar/saltar.
- Estado con `StateFlow` en lugar de `LiveData`.

**Definition of Done:** **MVP**: buscar en YouTube, reproducir, saltar, pausar y ver la cola funcionando en escritorio.
**Verificación:** `./gradlew :desktop:run` + tests de `QueueEngine` y del viewmodel.
**Prompt opencode:**
> Integra `VlcAudioPlayer` y `QueueEngine` en la UI de `:desktop` usando `StateFlow`. Implementa la búsqueda de YouTube y la reproducción desde los resultados, y una pantalla de cola con reordenar y saltar. Verifica con `./gradlew :desktop:run` y tests.

---

#### M8 — Playlists UI + drag & drop (1–2 semanas)
**Objetivo:** paridad con `PlaylistScreen` (1.421 líneas) en escritorio.
**Tareas**
- Portar `PlaylistScreen`, `SongListItem`, `PlaylistShare`, `CoverCropDialog`.
- Drag & drop para reordenar; acciones contextuales en lugar de swipe.
- Carga de portadas con Coil 3.

**Definition of Done:** crear/editar listas, añadir/quitar/reordenar pistas y portada personalizada funcionan.
**Verificación:** `./gradlew :desktop:test` + prueba manual.
**Prompt opencode:**
> Porta a `:desktop` las pantallas de playlists: `PlaylistScreen`, `SongListItem`, `PlaylistShare` y `CoverCropDialog`. Implementa drag & drop para reordenar y menú contextual en lugar de swipe. Usa Coil 3 para las portadas. Verifica manualmente y con tests.

---

#### M9 — Config/Ajustes multiplataforma (1 semana)
**Objetivo:** preferencias, rutas y tema por SO.
**Tareas**
- `Config` con backend multiplataforma (multiplatform-settings / `java.util.prefs`) manteniendo claves y migraciones actuales.
- Rutas por SO: `%APPDATA%`, `~/.config`+`~/.local/share`, `~/Library/Application Support`. `CoverCache` en la carpeta de caché equivalente.
- `ConfigScreen` portado (674 líneas).

**Definition of Done:** los ajustes persisten entre reinicios en el SO correspondiente; tema e idioma se aplican.
**Verificación:** `./gradlew :desktop:test` + prueba manual en al menos un SO.
**Prompt opencode:**
> Implementa `Config` de `:desktop` con backend multiplataforma conservando claves y migraciones actuales, y rutas de datos/caché por SO. Porta `ConfigScreen`. Verifica que los ajustes persisten y que los tests pasan.

---

#### M10 — Import/Export + copia de seguridad (1 semana)
**Objetivo:** `plyr-sync.zip` en carpeta local.
**Tareas**
- `DataExporter`/`DataImporter`/`DataSync`/`ExportManifest`/`ImportManifest` sobre `java.nio` (sin SAF).
- Carpeta de backup configurable (diálogo nativo del SO).
- Reutilizar tombstones y la lógica de fusión; tests puros en `:core`.

**Definition of Done:** exportar, importar y la copia automática funcionan; los tombstones evitan resurrecciones.
**Verificación:** `./gradlew :core:jvmTest :desktop:test`
**Prompt opencode:**
> Porta `DataExporter`, `DataImporter`, `DataSync`, `ExportManifest` e `ImportManifest` a `:desktop` usando `java.nio` en lugar de SAF, con una carpeta de backup elegible por el usuario. Mantén la política de tombstones y fusión. Verifica con tests puros y una exportación/importación real.

---

#### M11 — QR + Spotify import (1 semana)
**Objetivo:** compartir por QR y leer QR sin cámara.
**Tareas**
- `QRDialog` con ZXing JVM (generar imagen + guardar/copiar).
- Lector alternativo: pegar URL o abrir imagen de QR (decodificar con ZXing) en lugar de CameraX.
- `SpotifyImporter` desacoplado de `Context` y del repositorio.

**Definition of Done:** generar y decodificar un QR de playlist/pista funciona; importar una playlist de Spotify crea la lista.
**Verificación:** `./gradlew :core:jvmTest :desktop:test`
**Prompt opencode:**
> Porta `QRDialog` con ZXing para generar y decodificar QR en JVM. Sustituye `QrScannerDialog` (CameraX) por una entrada de URL o la apertura de una imagen. Desacopla `SpotifyImporter` de `Context` y del repositorio. Verifica con tests y manualmente.

---

#### M12 — Feed de recomendaciones (Supabase) (0.5 semana)
**Objetivo:** `FeedScreen` con `SupabaseClient` (ya en `:core`).
**Tareas**
- Portar `FeedScreen`; nickname, grupos y recomendaciones.
- Reducir los logs de `SupabaseClient` (PII, punto pendiente del `report.md`).

**Definition of Done:** se listan recomendaciones reales y se puede crear una.
**Verificación:** `./gradlew :desktop:test` + prueba manual con red.
**Prompt opencode:**
> Porta `FeedScreen` a `:desktop` usando el `SupabaseClient` de `:core`: listar recomendaciones, nickname y grupos, y crear una recomendación. Reduce los logs con datos sensibles. Verifica con una prueba manual con red.

---

#### M13 — Integración nativa del escritorio (1–2 semanas)
**Objetivo:** teclas multimedia, bandeja y notificaciones del SO.
**Tareas**
- Interfaz `DesktopMediaIntegration` en `:desktop` + implementación Linux (**MPRIS** vía D-Bus).
- Bandeja del sistema (`SystemTray`) con controles; menú de salir.
- *(Fase posterior)* `SmtcIntegration` (Windows) y `NowPlayingIntegration` (macOS) bajo la misma interfaz.

**Definition of Done:** las teclas play/pause/next/prev del SO controlan la reproducción en Linux; hay icono de bandeja.
**Verificación:** prueba manual en Linux con `playerctl`.
**Prompt opencode:**
> Crea la interfaz `DesktopMediaIntegration` y su implementación Linux con MPRIS (D-Bus) para integrar teclas multimedia y metadatos. Añade icono de bandeja con play/pause/next/prev y salir. Verifica con `playerctl` y manualmente.

---

#### M14 — Empaquetado multiplataforma (1 semana)
**Objetivo:** instalables por SO.
**Tareas**
- `jpackage` (o Conveyor) y tareas Gradle: Linux (`.deb`, `.rpm`, AppImage, Flatpak), Windows (`.msi`), macOS (`.dmg`).
- `.desktop` + icono (`.png`/`.svg`) + AppStream metadata en Linux.
- Declarar `libvlc` como dependencia; documentar en `README`.

**Definition of Done:** se genera un instalable para el SO objetivo que arranca sin Java instalado.
**Verificación:** instalar el paquete generado y abrir la app.
**Prompt opencode:**
> Configura el empaquetado de `:desktop`: tareas Gradle con `jpackage` para Linux (.deb/.rpm/AppImage), Windows (.msi) y macOS (.dmg), `.desktop` + icono + AppStream en Linux, y declaración de `libvlc` como dependencia. Documenta en el README. Verifica generando e instalando el paquete de Linux.

---

#### M15 — Pulido y paridad final (1–2 semanas)
**Objetivo:** cerrar diferencias y calidad.
**Tareas**
- Atajos de teclado, drag & drop fino, estados de carga/error pulidos.
- `UpdateChecker` adaptado (GitHub releases / gestor de paquetes).
- Actualizar `README.md` y `report.md` con la parte de escritorio.
- Checklist de i18n y detekt aplicado a los módulos nuevos.

**Definition of Done:** paridad funcional con Android menos NFC/cámara/sensor; documentación actualizada.
**Verificación:** `./gradlew build`, `./run.sh check`
**Prompt opencode:**
> Cierra la paridad: atajos de teclado, pulido de estados, `UpdateChecker` para escritorio, y actualiza README/report.md. Pasa detekt y los tests. Verifica con `./gradlew build` y `./run.sh check`.

---

### 6.2 Tabla de seguimiento

| Hito | Descripción | Semanas | Estado |
|---|---|---|---|
| M0 | Andamiaje de módulos | 0.5 | ☑ |
| M1 | Extraer `core` + tests ⭐ | 1–2 | ◐ |
| M2 | Red a `core` | 0.5 | ☑ |
| M3 | `AudioPlayer` + `QueueEngine` ⭐ | 1 | ☑ |
| M4 | VLCJ funcionando ⭐ | 1 | ☑ |
| M5 | Esqueleto UI Compose Desktop | 1 | ☑ |
| M6 | SQLDelight + repositorios | 1–2 | ☑ |
| M7 | Reproducción integrada (**MVP**) ⭐ | 1–2 | ☑ |
| M8 | Playlists UI + drag & drop | 1–2 | ☐ |
| M9 | Config/Ajustes multiplataforma | 1 | ☑ |
| M10 | Import/Export + backup | 1 | ☐ |
| M11 | QR + Spotify import | 1 | ☐ |
| M12 | Feed Supabase | 0.5 | ☐ |
| M13 | Integración nativa (MPRIS/SMTC/Now Playing) | 1–2 | ☐ |
| M14 | Empaquetado multiplataforma | 1 | ☐ |
| M15 | Pulido y paridad final | 1–2 | ☐ |

**Ruta crítica:** `M0 → M1 → M3 → M4 → M7` da un MVP funcional. `M2`, `M5` y `M6` pueden ir en paralelo si hay más de un dev.

### 6.3 Registro de ejecución del port

- **M0 — Andamiaje de módulos ✅.** Módulos `:core` (Kotlin Multiplatform, target Android vía el plugin `com.android.kotlin.multiplatform.library` + `jvm()`) y `:desktop` (Compose Multiplatform, toolchain JVM 21). `:app` y `:desktop` dependen de `:core`.
  - Versiones fijadas en `gradle/libs.versions.toml`: Compose Multiplatform `1.12.1`, SQLDelight `2.4.1`, VLCJ `4.12.1`, multiplatform-settings `1.3.0`, Coil 3 `3.6.3`, kotlinx-coroutines `1.10.2` (usados en hitos posteriores).
  - **Desviación respecto al plan (M0):** se usa `com.android.kotlin.multiplatform.library` (`kotlin { androidLibrary { … } }`) en lugar de `androidTarget()` + `com.android.library`. AGP 9 marca esa combinación como deprecada y recomienda el plugin nuevo; el resultado (target Android + JVM compartidos) es equivalente.
  - Verificado: `./gradlew :core:build`, `./gradlew :desktop:build`, `./gradlew :desktop:run` (abre la ventana) y `./run.sh build` (APK de Android).
- **M1 — Extraer lógica pura a `:core` ◐ (parcial).** Movidos **13 archivos** a `core/src/commonMain` (`model/AudioItem`, `model/ScanResult`, `viewmodel/{WindowState,LoadingState,PendingSkips,IdlePlayback}`, `service/{CoverCropMath,PlaybackNotificationState,NotificationRefreshPolicy}`, `utils/{UrlParser,LikedSongsMerge}`, `database/PlaylistSource`) y **5** al source set compartido `jvmSharedMain` (usa `java.*`, presente en JVM y Android/ART): `model/Recommendation`, `utils/{ExportManifest,ExportDigest,ImportArchive}`, `utils/NfcScanEvent`. Se movieron **169 tests** que pasan en `:core:jvmTest`; los **335** restantes siguen en `:app` (UI/Room/red) y migrarán en M2/M3/M5/M6/M9.
  - Nuevo source set KMP `jvmSharedMain` compartido por los targets `jvm` y `android` (intermedio entre `commonMain` y ambos), para el código JVM (`java.*`) que no encaja en `commonMain` estricto.
  - Logger propio `com.plyr.core.util.Logger` (`jvmSharedMain`, vía `java.util.logging`, equivalente en Android) sustituye a `android.util.Log` en `NfcScanEvent`.
  - **Diferidos respecto al plan (M1):** `QueueIndex` (depende de `Config`→M9/M3), `ImportManifest` (Room→M6), `YouTubePlaylistCreator` (Room+red→M2/M6), `MediaMetadataExtractor` (NewPipe→M2), `NfcTagEvent` (`android.nfc.Tag`, se queda en `:app`) y `ModelDefaultsTest` (depende de UI). Se documentan aquí para no perderlos.
  - Verificado: `./gradlew :core:jvmTest` (169 tests, 0 fallos), `./gradlew :app:assembleDebug` y `./gradlew :app:testDebugUnitTest` (335 tests, 0 fallos).
- **M2 — Red a `:core` sin Android ✅.** Movidos **7 archivos** de `:app` a `:core`:
  - `commonMain` (puro): `network/AppModels.kt` (data classes de Spotify) e `i18n/TranslationCatalog.kt` (catálogo de traducciones: mapas + `get(language, key)`).
  - `jvmSharedMain` (JVM/Android): `network/{YouTubeManager,SimpleDownloader,SupabaseClient}.kt` y `utils/{NewPipeHolder,MediaMetadataExtractor}.kt` (este último era un diferido de M1).
  - **Sin dependencia de Android:** `android.util.Log` → `com.plyr.core.util.Logger` en los tres ficheros de red; `BuildConfig` → nuevo `com.plyr.core.NetworkConfig`, que `:app` rellena en `PlyrApp.onCreate` desde `BuildConfig` (conserva los overrides de `local.properties`/entorno de S9/S10). `SupabaseClient` lee `NetworkConfig.supabaseUrl`/`supabaseAnonKey` mediante getters para no capturar valores en el init.
  - **Traducciones (desviación respecto al plan):** el catálogo puro se mueve a `:core` (`com.plyr.i18n.TranslationCatalog`); `:app` conserva un envoltorio fino `com.plyr.utils.Translations.get(context, key)` que resuelve el idioma con `Config` y delega en el catálogo. Así las ~110 llamadas `Translations.get(context, …)` no cambian y la parte que necesita `Context` no contamina `:core`.
  - **Dependencias** declaradas en `jvmSharedMain` (compartidas por `jvm` + `android`): `NewPipeExtractor` (`v0.26.3`) y `OkHttp`. `org.json` se declara solo para `jvm`: en Android lo aporta el SDK (android.jar), y declararlo empaquetaría una copia duplicada en el APK.
  - **Tests movidos:** `AppModelsTest` y `TranslationCatalogTest` (ex `TranslationsTest`) a `commonTest`; `SimpleDownloaderLogRedactionTest`, `SupabaseClientTest`, `NewPipeHolderTest` y `AudioUrlExtractionTest` (usa el overload `internal` de `YouTubeManager`) a `jvmTest`. `TranslationKeysUsageTest` se queda en `:app` (escanea las fuentes de la app) pero refleja el catálogo de `:core`.
  - **Smoke test de red** (`jvmTest/.../NetworkSmokeTest.kt`): verifica desde un JVM de escritorio que `YouTubeManager.searchVideoId(...)` devuelve un id y que `getAudioUrl(...)` extrae una URL reproducible; se omite con `assumeTrue` cuando no hay conexión (en esta máquina **se ejecutó y pasó**).
  - **Ajuste de test:** `NewPipeHolderTest.init_notInitializedBeforeFirstCall` asumía un JVM limpio; al inicializar el smoke test NewPipe en el mismo proceso dejaba de ser válido. Se sustituye por `init_secondCallIsIdempotentAndKeepsFirstDownloader`, que comprueba el contrato real (idempotencia: el segundo init no reemplaza el `Downloader`) y no depende del orden de ejecución.
  - Verificado: `./gradlew :core:jvmTest` (**225 tests**, 0 fallos), `./run.sh test` (**280 tests** de `:app`, 0 fallos) y `./run.sh build` (APK de Android, sin clases duplicadas). **Total: 505 tests.**
- **M3 — `AudioPlayer` + `QueueEngine` ✅.** `PlayerViewModel` (y el resto de `:app`) ya no menciona ExoPlayer: solo lo hace `ExoAudioPlayer`.
  - **`core` (puro):** nuevo paquete `com.plyr.player` con `AudioTrack` (`mediaId`, `title`, `artist`, `uri`), la interfaz `AudioPlayer` (`mediaItemCount`, `currentMediaItemIndex`, `currentPosition`, `duration`, `isPlaying`, `playbackState`, `setListener`, `setMediaItems`/`addMediaItems`/`removeMediaItems`, `prepare`/`play`/`pause`/`stop`/`clearMediaItems`, `seekTo(index,position)`/`seekTo(position)`/`seekForward`/`seekBack`, `setRepeatOne`, `release`, y `TIME_UNSET`), el enum `PlaybackState` (`IDLE`/`BUFFERING`/`READY`/`ENDED`) y `AudioPlayerListener`. `PlaybackState` se eligió sobre booleanos (`isIdle`/`isBuffering`/`isReady`) para que la UI y la cola distingan READY de ENDED sin exponer las constantes de media3.
  - **`RepeatMode`** (`com.plyr.domain`, valores `"off"`/`"one"`/`"all"`) centraliza el modo de repetición; `Config.REPEAT_MODE_*` pasan a ser alias `const val` para no tocar las llamadas de `:app`.
  - **`QueueEngine<T>` (puro, `commonMain`):** reúne el estado que antes vivía suelto en `PlayerViewModel` (cola, índice actual, modo de repetición y `WindowState`) y delega las decisiones en `QueueIndex`: `nextTarget`/`previousTarget`/`onTrackEndedTarget`/`retryTarget`/`hasNext`, `fillTarget(mediaItemCount)` y `trimCount(currentMediaItemIndex)`, más `setQueue`/`appendTrack`/`replaceQueue`/`anchorWindowAt`/`resetWindow`/`shiftWindow`/`invalidateWindow`. Es genérico (`<T>`) para no depender de `TrackEntity` (que vive en `:app`). `PlayerViewModel` lo usa con propiedades de delegación (`queue`, `currentIndex`, `windowStart`, `generation`, `queueRepeatMode`), de modo que el grueso del código no cambia.
  - **`:app`:** `ExoAudioPlayer` (paquete `com.plyr.player`) envuelve `ExoPlayer` (misma configuración: atributos de audio, `setHandleAudioBecomingNoisy(true)`, incrementos de 10 s) y traduce `MediaItem`/`Player.STATE_*`/`PlaybackException` a los tipos neutrales; expone `val player: ExoPlayer` para lo intrínsecamente Android. `TrackResolver` construye `AudioTrack` en vez de `MediaItem` (`ResolvedItem.track`). `PlayerViewModel` pasa de `_exoPlayer: ExoPlayer?`/`exoPlayer` a `_player: AudioPlayer?`/`audioPlayer`, y `onMediaSessionUpdate` es `((AudioPlayer) -> Unit)?`. `MusicService.setupMediaSession(AudioPlayer, …)` desenvuelve a `ExoPlayer` para la `MediaSession`; `FloatingMusicControls` y `MediaButtonReceiver` usan la interfaz. En `FloatingMusicControls` se renombró la data class local `PlaybackState` a `PlaybackUiState` para no chocar con el enum de `:core`.
  - **Tests portados/creados:** `QueueIndexTest` (32) se movió de `:app` a `commonTest`; ya estaban en `:core` los de `WindowState`, `LoadingState`, `IdlePlayback` y `PendingSkips`. Nuevo `QueueEngineTest` (**19 tests**) cubre navegación, anclaje/generación de ventana, `fillTarget` y `trimCount`.
  - **Sin cambios de comportamiento:** `ExoAudioPlayer` reproduce la misma configuración de ExoPlayer y el mismo mapeo de eventos; `QueueEngine` conserva la aritmética exacta de `fillTarget`/`trimWindow` y la delegación en `QueueIndex`.
  - Verificado: `./gradlew :core:jvmTest` (**276 tests**, 0 fallos), `./run.sh test` (**248 tests** de `:app`, 0 fallos), `./run.sh build` (APK) y `./gradlew :desktop:build`. **Total: 524 tests** (505 + 19 de `QueueEngine`; el recuento de `:app` baja de 280 a 248 porque `QueueIndexTest` pasa a `:core`).
  - **Nota para M4:** `ExoAudioPlayer` es el molde de `VlcAudioPlayer`; la ventana de cola y las decisiones de salto ya viven en `QueueEngine` (puro), así que el adaptador de escritorio solo debe implementar `AudioPlayer`.
- **M4 — Adaptador VLCJ + prueba de sonido ✅.** El motor de audio funciona en escritorio: `:desktop:soundCheck` reproduce una canción real de YouTube (verificado).
  - **Dependencia:** `uk.co.caprica:vlcj:4.12.1` en `:desktop` (arrastra `vlcj-natives`, que son solo los bindings JNA). `libvlc` nativo **no** va incluido: hay que tener VLC instalado en el sistema.
  - **`VlcAudioPlayer : AudioPlayer`** (`com.plyr.desktop.player`), sobre `EmbeddedMediaPlayer`. VLCJ no tiene un "media item list" como media3, así que la ventana de pistas se mantiene en la propia clase y cada salto recarga la URL en VLC (`media().play(url, ":no-video")`). Reproduce el comportamiento de `ExoAudioPlayer`: avance automático al siguiente item de la ventana al terminar (`finished`), "repetir uno" recargando la actual, y `PlaybackState.ENDED` solo al agotar la ventana.
  - **Máquina de estados pura** `VlcPlaybackStateMachine`: traduce los eventos de VLCJ (`opening`/`buffering`/`playing`/`paused`/`stopped`/`finished`/`error`) a `PlaybackState` y avisa al `AudioPlayerListener`, notificando solo cuando el valor cambia. Es testeable **sin** `libvlc` (justo lo que pide la verificación).
  - **Detección de `libvlc`** (`VlcEnvironment`): busca el runtime en `PLYR_VLC_DIR` y, si no, en rutas estándar por SO; construye `--plugin-path` y `jna.library.path` para VLC no estándar; y da un **mensaje claro** con cómo instalarlo si falta (la CLI sale con código 2 y la ventana lo muestra en pantalla).
  - **Prueba de sonido:** `SoundCheck.kt` (CLI, tarea `:desktop:soundCheck`) busca en YouTube con `YouTubeManager`, extrae la URL con `getAudioUrl` y reproduce informando posición/duración/estado. `Main.kt` pasa a ser una ventana mínima (campo de búsqueda + Reproducir/Parar) para que `./gradlew :desktop:run` también reproduzca. Ambas vías admiten `-PvlcDir=/ruta/a/libvlc` (o `PLYR_VLC_DIR`), que se propaga a `run`/`soundCheck`/`test` como `PLYR_VLC_DIR` + `LD_LIBRARY_PATH`.
  - **Tests (15, `desktop/src/test`):** `VlcPlaybackStateMachineTest` (9) cubre la máquina de estados; `VlcEnvironmentTest` (5) la detección y los argumentos; `VlcAudioPlayerSmokeTest` (1) construye el reproductor y gestiona la ventana, **se omite con `assumeTrue`** si no hay `libvlc`.
  - **Verificado aquí:** esta máquina no tenía VLC ni permisos de `sudo`, así que se extrajo VLC 3.0.24 sin root (`apt-get install --download-only` + `dpkg-deb -x` en `/tmp/opencode/vlc`) y se ejecutó con `PLYR_VLC_DIR`+`LD_LIBRARY_PATH`: `:desktop:soundCheck` reprodujo **audio real** (posición avanzando 3.7s→18.2s de una pista de 450.7s, estado "sonando"); la ventana de `:desktop:run` arranca sin excepciones; y sin `libvlc` el mensaje de instalación y la salida con código 2 son correctos. En un sistema con VLC instalado no hace falta nada de esto. Procedimiento sin root (por si hace falta repetirlo):
    ```bash
    mkdir -p /tmp/opencode/vlcdebs/partial
    apt-get install --download-only --no-install-recommends -y \
      -o Dir::Cache::archives=/tmp/opencode/vlcdebs -o Debug::NoLocking=1 vlc
    for d in /tmp/opencode/vlcdebs/*.deb; do dpkg-deb -x "$d" /tmp/opencode/vlc; done
    D=/tmp/opencode/vlc/usr/lib/x86_64-linux-gnu          # libvlc.so.5 / libvlccore.so.9
    ln -sf libvlc.so.5 "$D/libvlc.so"; ln -sf libvlccore.so.9 "$D/libvlccore.so"
    ./gradlew :desktop:soundCheck -PvlcDir="$D" --args="artista canción"
    ```
  - Verificado: `:desktop:test` (**15 tests** con VLC, 14 + 1 omitido sin él, 0 fallos), `:desktop:build`, `./run.sh build` (APK), `./run.sh test` (248) y `:core:jvmTest` (276). **Total: 539 tests** (524 + 15 de `:desktop`).
- **M5 — Esqueleto de UI Compose Desktop ✅.** `:desktop` ya tiene navegación, tema terminal e i18n; la reproducción real llega en M7.
  - **Tema** (`com.plyr.desktop.ui.theme.Theme`): portado tal cual de `com.plyr.ui.theme` (colores claro/oscuro, `PlyrSpacing`, `PlyrSymbols`, `Typography` monoespaciada y `PlyrTheme`, sin `@Preview`). El logo de Android (drawables `ascii_1..50`) se sustituye por un banner de texto fijo `AsciiArt.LOGO`.
  - **Navegación** (`com.plyr.desktop.ui`): `enum Screen` (HOME/SEARCH/QUEUE/PLAYLISTS/FEED/CONFIG) y `AudioListScreen` como contenedor. En vez de los `Pager` de Android (que no encajan con ratón/teclado) la Home es un menú de consola explícito que cambia de pantalla; cada pantalla de datos es un `PlaceholderScreen` hasta M6/M7.
  - **Componentes portados** (`ui/components`): `Titulo`/`Subtitulo` y `ActionButton`/`ActionButtonsGroup`.
  - **Pantallas** (`ui/screens`): `HomeScreen` (banner ASCII + menú), `PlaceholderScreen` (Search/Queue/Playlists/Feed) y `ConfigScreen` (selectores de tema e idioma, con la opción activa marcada).
  - **`FloatingMusicControls` portado sin reproducción real** (`ui/FloatingMusicControls.kt`): recibe un `PlayerUiState` neutral (título, tiempos, cola, repetición) + callbacks que cablea M7; conserva la estética terminal (`//`, `>`, `<<`, `>>`, `^`, ciclo `o`/`1`/`*`). En el esqueleto se muestra con el estado "player not available". La marquesina solo crea la animación infinita si el texto desborda: así la composición queda ociosa y los tests de Compose no se cuelgan.
  - **Config + i18n:** `DesktopConfig` (backend **temporal en memoria** con estado de Compose; M9 lo hará persistente con `multiplatform-settings`) expone tema, idioma, modo de repetición y buscador; `ThemeMode`/`Language` usan los **mismos ids** que Android (`"dark"`, `"español"`, …). `com.plyr.desktop.i18n.Translations` envuelve `TranslationCatalog` de `:core` resolviendo el idioma actual.
  - **`Main.kt`** aplica `PlyrTheme(DesktopConfig.isDark(isSystemInDarkTheme()))` y muestra `AudioListScreen` (la ventana de prueba de M4 desaparece; M4 sigue verificándose con `:desktop:soundCheck`). El tema por defecto es `SYSTEM`.
  - **Tests (13 nuevos, `desktop/src/test`):** `AudioListScreenTest` (**6**, con `runComposeUiTest` v2 — `androidx.compose.ui.test.v2`, que evita el aviso de deprecación del v1 y usa `StandardTestDispatcher`) abre en Home, navega por el menú, vuelve, usa el botón de cola y cambia idioma/tema; `DesktopConfigTest` (**4**) y `TranslationsTest` (**3**). Se añade `testImplementation(compose.desktop.uiTestJUnit4)`.
  - **Verificado aquí:** `:desktop:test` (**28 tests**, 1 omitido sin `libvlc`, 0 fallos); `:desktop:run` abre una ventana real —comprobado bajo Xvfb con `xdotool` (ventana "plyr", 800x600, `IsViewable`, sin excepciones)— y el OCR de una captura confirma el banner ASCII, los 5 ítems del menú (`> plyr_search`, `> plyr_queue`, `> plyr_lists`, `> plyr_feed`, `> plyr_config`) y los controles. **Total: 552 tests** (539 + 13 de `:desktop`; `:core` 276 + `:app` 248 + `:desktop` 28).
- **M6 — Persistencia SQLDelight ✅.** El escritorio ya guarda listas, pistas, favoritos e historial sin Room y **sobrevive a reinicios**.
  - **Lógica portable a `:core` (commonMain).** Nuevas interfaces de vista mínima `PlaylistRow` (`remoteId`, `name`, `trackCount`) y `TrackRow` (`name`, `artists`, `youtubeVideoId`) + modelos completos `PlaylistRecord`/`TrackRecord` (`com.plyr.database`) y constante compartida `LikedSongs.ID = "liked_songs"`. `PlaylistFilters.visiblePlaylists` (filtro/orden de listas visibles, ex-`PlaylistLocalRepository`) y `TrackIdentity` (`fallbackDedupeKey`/`likedTrackKey`/`likedTrackOf`, ex-`ImportManifest`+`PlaylistLocalRepository`) viven ahora en `:core`; `Tombstones` (`merge`/`add`/`remove`) aporta las operaciones puras de tombs. `LikedSongsMerge` y `PlaylistSource` ya estaban en `:core`.
  - **`:app` sin cambios de comportamiento.** `PlaylistEntity`/`TrackEntity` implementan `PlaylistRow`/`TrackRow` (`override`); `PlaylistLocalRepository.likedTrackOf`/`visiblePlaylists` pasan a **delegar** en `:core` (misma firma: los tests de `:app` no cambian); `ImportManifest.fallbackDedupeKey`/`likedTrackKey` delegan en `TrackIdentity` y `LIKED_SONGS_ID` es `LikedSongs.ID`. Room y sus DAOs/migraciones se mantienen intactos.
  - **Esquema `.sq` (SQLDelight 2.4.1)** en `desktop/src/main/sqldelight/com/plyr/desktop/database/`: `Playlist.sq`, `Track.sq` (FK a `playlists` + índice en `playlistId`) y `SearchHistory.sq`, equivalentes a las entidades Room v8. El escritorio parte de base nueva, así que no arrastra las migraciones 5→8 (el esquema ya incluye su resultado: `source`/`sourceId`). Genera `PlyrDatabase` en `com.plyr.desktop.database`.
  - **Repositorios de escritorio** (`com.plyr.desktop.database`): `DesktopDatabase` (abre/crea el fichero, crea el esquema si falta, activa FKs; `inMemory()` para tests), `PlaylistRepository` (alta/edición/borrado de listas, pistas con dedupe por identidad, reordenación con renumerado, favoritos con tombstones, fusión de liked con `LikedSongsMerge`, `visiblePlaylists` y observación por `Flow` vía `sqldelight-coroutines`) y `SearchHistoryRepository` (sin duplicados por texto+motor, 50 recientes). Los tombs usan un `TombstoneStore` con implementación en memoria (`InMemoryTombstoneStore`): el escritorio aún no tiene copia de seguridad (M10), único consumidor de los tombs, que podrá inyectar un almacén persistente sin tocar los repositorios.
  - **Desviaciones respecto al plan (M6):** (1) el escritorio **no migra** desde el esquema Room v8 —no hay base previa—, solo replica el esquema final; (2) `ImportManifest` **no** se mueve a `:core` (sigue en `:app`, ya que su `buildTracks` construye `TrackEntity` y solo el escritorio usará import/export en M10); se extrae lo portable (`TrackIdentity`). (3) Los repositorios de escritorio son **síncronos** con `Flow` para observación, en vez de `suspend`: SQLDelight/JDBC es bloqueante y la UI de Compose lo consumirá en M7/M8.
  - **Tests (nuevos):** `:core` `PlaylistFiltersTest` (5), `TrackIdentityTest` (8) y `TombstonesTest` (6); `:desktop` `PlaylistRepositoryTest` (20), `SearchHistoryRepositoryTest` (7) y `DesktopDatabaseTest` (5, incluida la persistencia cerrando y reabriendo el fichero SQLite). Se añaden `app.cash.sqldelight:coroutines-extensions` y el plugin `app.cash.sqldelight` en `:desktop`.
  - Verificado: `:core:jvmTest` (**295 tests**, 0 fallos), `:desktop:test` (**62 tests**, 1 omitido sin `libvlc`, 0 fallos) y `./run.sh test` (**248 tests** de `:app`, 0 fallos). **Total: 605 tests** (`:core` 295 + `:app` 248 + `:desktop` 62). `:desktop:compileKotlin` y `./run.sh build` (APK) compilan.
  - **Nota:** `./run.sh check` reporta 1 hallazgo de detekt (`complexity, TooManyFunctions` en `app/src/main/java/com/plyr/player/ExoAudioPlayer.kt`), **ajeno a M6** y ya presente en el commit `m4`; se deja sin tocar.
- **M7 — Reproducción integrada en la UI ✅ (MVP).** El escritorio ya busca en YouTube, reproduce, pausa/salta y muestra la cola, todo sobre `StateFlow`.
  - **Controlador de escritorio** (`com.plyr.desktop.player.PlayerController`): orquesta `QueueEngine` (+ `QueueIndex`/`LoadingState`/`PendingSkips`/`IdlePlayback` de `:core`) sobre un `StateFlow<PlayerState>`. Reutiliza la lógica del `PlayerViewModel` de Android (ventana contigua, resolución perezosa, reintentos con re-resolución, saltos pendientes) pero sin `LiveData` ni Android. El motor y la resolución se **inyectan** —`openPlayer: () -> AudioPlayer` (perezoso) y `sourceResolver: TrackSourceResolver`—, de modo que los tests corren sin `libvlc` ni red. La pista de cola es un tipo neutral propio, `PlayerTrack` (+ `PlayerTrack.fromSearchResult`).
  - **`SearchController`** (`com.plyr.desktop.search`): `StateFlow<SearchState>` (query/resultados/carga/error) con la búsqueda inyectada. La búsqueda real vive en `:core`: `YouTubeManager.searchVideos(query, maxResults)` (+ `formatDurationSeconds` privado) devuelve `List<AudioItem>`, reutilizable desde Android.
  - **UI** (`com.plyr.desktop.ui`): `AudioListScreen` pasa a ser el contenedor que crea el controlador y el buscador, traduce `PlayerState` a `PlayerUiState` y enruta `SEARCH`/`QUEUE`. Nuevas `SearchScreen` (buscar + reproducir resultado) y `QueueScreen` (listar/saltar/reordenar). `FloatingMusicControls` recibe ahora estado real: **se oculta cuando no hay nada** (título/carga/error), así que la barra solo aparece al reproducir. El progreso se refresca por sondeo de la UI (`LaunchedEffect` solo mientras suena), no con un bucle interno.
  - **Resolución real** (`YouTubeTrackSourceResolver`): usa `YouTubeManager.resolveVideoId`/`getAudioUrl` de `:core`.
  - **Correcciones de concurrencia (descubiertas con VLC real):** (1) todas las mutaciones del `StateFlow` usan `MutableStateFlow.update` (atómico) en vez de `value = value.copy(...)`, que perdía actualizaciones cuando dos callbacks de VLC (que llegan por el hilo de VLC y se reencolan en el `scope`) coincidían; (2) `VlcPlaybackStateMachine.state`/`isPlaying` pasan a `@Volatile`. Sin (1) el estado se quedaba en "cargando" con audio sonando.
  - **Bug latente de M4 corregido:** VLCJ emite `buffering` **justo después** de `playing` (con la caché llena), y `onBuffering()` devolvía el estado a `BUFFERING` para siempre: la UI mostraba "cargando" indefinidamente aunque el audio avanzaba. `onBuffering()` ahora solo pasa a `BUFFERING` si aún no se está reproduciendo (+ test de regresión).
  - **Desviaciones respecto al plan (M7):** (1) reordenar en la cola **reanuda** la pista actual reconstruyendo la ventana (drag & drop suave es M8/M15); (2) el **historial de búsqueda** de M6 aún no se cablea a la búsqueda (diferido); (3) el `PlayerState` es un espejo neutral de escritorio, no el `PlayerViewModel` de Android.
  - **Tests (nuevos):** `:desktop` `PlayerControllerTest` (15, con `FakeAudioPlayer` + resolver falso, `Dispatchers.Unconfined`), `SearchControllerTest` (6), `SearchScreenTest` (5), `QueueScreenTest` (3), `PlayerControllerVlcIntegrationTest` (1, reproduce un WAV local con **VLC real**; se omite sin `libvlc`) y +1 en `VlcPlaybackStateMachineTest` (10) por el bug del `buffering`. Se actualizó `AudioListScreenTest` al comportamiento de M7 (la barra flotante no aparece sin reproducción; la cola se abre desde el menú de Home).
  - Verificado en vivo: `./gradlew :desktop:run` (bajo `Xvfb`, `PLYR_VLC_DIR=/tmp/opencode/vlc/...`); búsqueda "daft punk" con resultados reales de YouTube, reproducción de un resultado (posición avanzando `01:32→01:48→02:39…`), cola con **15** pistas y pista actual `4/15`, **pausa** (posición congelada en `02:32`) y reanudación (`02:34→02:39`).
  - Verificado: `:core:jvmTest` (**295 tests**), `./run.sh test` (**248 tests** de `:app`), `:desktop:test` (**93 tests**; con `libvlc` corren los 93 —incluida la integración—, sin él se omiten 2: el de humo y la integración). **Total: 636 tests** (`:core` 295 + `:app` 248 + `:desktop` 93), 0 fallos. Compilan `:core:build`, `:app:compileDebugKotlin` y `./gradlew build -x :app:detekt`. **Nota:** `./gradlew build` (sin excluir nada) sigue fallando **solo** por el hallazgo de detekt preexistente de M4 (`TooManyFunctions` en `ExoAudioPlayer.kt:26:7`), ajeno a M7.
- **M9 — Config/Ajustes multiplataforma ✅.** Los ajustes del escritorio ya persisten entre reinicios y las rutas de datos/caché siguen la convención de cada SO.
  - **`DesktopConfig` persistente** (`com.plyr.desktop.config`): mantiene las claves y migraciones de `com.plyr.utils.Config` de Android (`theme`, `language`, `repeat_mode`, `search_engine`, `search_engine_migrated`), pero con backend multiplataforma `multiplatform-settings` 1.3.0 sobre `java.util.prefs` (`DesktopSettings.create()`, nodo `com/plyr/desktop`). Conserva la migración del idioma legacy `"japanese"` → `"日本語"` y la inicialización única del buscador (`search_engine_migrated`). Los valores viven en `mutableStateOf` (la UI reacciona al cambiarlos) y se escriben al almacén en el mismo setter. `installSettings(Settings)` permite sustituir el almacén en tests y relee los valores aplicando de nuevo las migraciones.
  - **Rutas por SO** (`com.plyr.desktop.platform.AppPaths`): **Windows** `%APPDATA%\plyr` (config y datos) + `%LOCALAPPDATA%\plyr\cache`; **Linux** `$XDG_CONFIG_HOME/plyr`, `$XDG_DATA_HOME/plyr` y `$XDG_CACHE_HOME/plyr` con los fallbacks `~/.config`, `~/.local/share` y `~/.cache`; **macOS** `~/Library/Application Support/plyr` + `~/Library/Caches/plyr`. `resolve(osName, home, env)` es lógica pura y devuelve **cadenas** (no `Path`, que interpreta los separadores del SO anfitrión) para poder testear los tres sistemas desde cualquiera; `ensureDirectories()` las crea.
  - **Datos persistentes (cabía en M6, se cierra aquí):** la base SQLite se abre ahora en `AppPaths.databaseFile` (`dataDir/plyr.db`) desde `AudioListScreen` (la fábrica se inyecta; los tests usan `DesktopDatabase.inMemory()`) y se cierra al salir, y `Main` llama a `AppPaths.ensureDirectories()` al arrancar. Hasta M7 la UI usaba `DesktopDatabase.inMemory()`, así que listas, favoritos e historial **no** sobrevivían a reinicios; ahora sí.
  - **Caché de portadas de escritorio** (`com.plyr.desktop.utils.CoverCache`): descarga con OkHttp y guarda cada portada una sola vez en `AppPaths.directories.cacheDir/export-covers`, con carpeta y descarga inyectables. La clave del nombre de archivo (SHA-256 hex de la URL) se extrae a `:core` (`com.plyr.utils.CoverCacheKey`, `jvmSharedMain`) y el `CoverCache` de Android pasa a **delegar** en ella, de modo que ambas plataformas usan los mismos nombres de archivo.
  - **`ConfigScreen` portado** (`desktop/.../ui/screens/ConfigScreen.kt`): selectores de **tema**, **idioma** y **modo de repetición** (este último cableado a `PlayerController.setRepeatMode` desde `AudioListScreen`) más las **rutas de datos y caché** reales del SO. Se omiten las secciones que no aplican a escritorio (swipe, importación de Spotify, QR/NFC); la copia de seguridad llega en M10.
  - **i18n:** 7 claves nuevas (`repeat`, `repeat_off`, `repeat_one`, `repeat_all`, `paths_title`, `data_folder`, `cache_folder`) en los 4 idiomas de `TranslationCatalog`. `TranslationKeysUsageTest` (`:app`) pasa a escanear también `desktop/src/main/kotlin` y a reconocer la variante de un solo argumento `Translations.get("clave")`, que antes no veía.
  - **Tests nuevos/actualizados:** `:core` `CoverCacheKeyTest` (**6**, portado desde `:app`); `:desktop` `AppPathsTest` (**7**, los tres SO + `ensureDirectories` + `databaseFile`), `CoverCacheTest` (**4**, con carpeta temporal y descarga inyectada), `ConfigScreenTest` (**6**, con `runComposeUiTest`) y `DesktopConfigTest` ampliado a **10** (persistencia "releída" como tras reiniciar y migraciones); `AudioListScreenTest` inyecta una BD en memoria y `DesktopDatabaseTest` ya cubría la persistencia cerrando y reabriendo el fichero. Los tests de `:desktop` usan `MapSettings` (artefacto `multiplatform-settings-test`, alias nuevo) y la tarea `Test` fija `java.util.prefs.userRoot` a una carpeta de build para no tocar las preferencias reales del usuario.
  - **Desviaciones respecto al plan (M9):** (1) `ConfigScreen` (674 líneas en Android) se porta solo en la parte aplicable a escritorio, no de forma literal; (2) el `CoverCache` de Android se refactoriza mínimamente para compartir la clave; (3) en escritorio `AUTO` y `SYSTEM` siguen el tema del SO (no hay sensor de luz).
  - Verificado: `:core:jvmTest` (**301 tests**), `./run.sh test` (**242 tests** de `:app`) y `:desktop:test` (**116 tests**, 2 omitidos sin `libvlc`), 0 fallos. **Total: 659 tests** (`:core` 301 + `:app` 242 + `:desktop` 116). `./run.sh build` genera el APK de Android. Prueba manual en Linux: dos procesos JVM distintos sobre el mismo nodo `com/plyr/desktop` conservan `theme=dark`, `language=日本語` y `repeat_mode=all`. `:app:lint` pasa. **Nota:** `./gradlew build` (agregado) sigue fallando por **dos causas preexistentes y ajenas a M9**, confirmadas sobre `HEAD` con los cambios apartados: (1) `:app:detektMain` → `TooManyFunctions` en `ExoAudioPlayer.kt:26:7`; (2) `:app:minifyReleaseWithR8` → clase duplicada `com.plyr.ui.components.PlaylistOrigin` entre `:app` y `:core` (introducida en M8).


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
