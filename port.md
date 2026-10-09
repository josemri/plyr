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

Cada hito (`M0`…`M15`) es **autocontenido y verificable**: se le puede pasar a opencode tal cual. La regla de oro es que **al terminar cada hito, `./gradlew build` y los tests deben pasar**, tanto en Android como en desktop.

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
| M0 | Andamiaje de módulos | 0.5 | ☐ |
| M1 | Extraer `core` + tests ⭐ | 1–2 | ☐ |
| M2 | Red a `core` | 0.5 | ☐ |
| M3 | `AudioPlayer` + `QueueEngine` ⭐ | 1 | ☐ |
| M4 | VLCJ funcionando ⭐ | 1 | ☐ |
| M5 | Esqueleto UI Compose Desktop | 1 | ☐ |
| M6 | SQLDelight + repositorios | 1–2 | ☐ |
| M7 | Reproducción integrada (**MVP**) ⭐ | 1–2 | ☐ |
| M8 | Playlists UI + drag & drop | 1–2 | ☐ |
| M9 | Config/Ajustes multiplataforma | 1 | ☐ |
| M10 | Import/Export + backup | 1 | ☐ |
| M11 | QR + Spotify import | 1 | ☐ |
| M12 | Feed Supabase | 0.5 | ☐ |
| M13 | Integración nativa (MPRIS/SMTC/Now Playing) | 1–2 | ☐ |
| M14 | Empaquetado multiplataforma | 1 | ☐ |
| M15 | Pulido y paridad final | 1–2 | ☐ |

**Ruta crítica:** `M0 → M1 → M3 → M4 → M7` da un MVP funcional. `M2`, `M5` y `M6` pueden ir en paralelo si hay más de un dev.

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
