# plyr → Web · Plan de port a navegador

> Documento de trabajo para la migración de **plyr** a navegador.
> Objetivo del MVP: **paridad funcional con la app de escritorio**, servida desde
> `http://localhost:8080`, con un servidor propio que hace de extractor de YouTube.
>
> Este documento es **autosuficiente**: un agente (OpenCode) debe poder ejecutar
> un hito completo leyéndolo, sin más contexto que el propio repositorio.

---

## 0. Cómo usar este documento

El plan está troceado en **hitos (H0…H7)**. Cada hito es independiente, tiene un
alcance cerrado y sus propios **criterios de aceptación verificables**.

**Regla de trabajo: un hito = un PR.**

- No empieces un hito sin leer la sección completa de ese hito.
- No avances al hito siguiente hasta que los *criterios de aceptación* del actual
  estén verdes (tests + `detekt` + build).
- Si un hito revela que una premisa del plan es falsa, **para y documenta el
  hallazgo** en el PR en vez de improvisar una solución arquitectura alternativa.

Orden de ejecución: **H0 → H1 → H2 → H3 → H4 → H5 → H6**. H7 es futuro y no
bloquea a los anteriores.

---

## 1. Reglas invariables (NO romper)

Estas reglas son más importantes que cualquier otra cosa de este documento. El
proyecto tiene **103 ficheros de test** y una estructura deliberadamente limpia:
el valor de este port está en **reutilizar el código, no en reescribirlo**.

### 1.1 Estructura del proyecto

- **Kotlin Multiplatform + Compose Multiplatform.** El target nuevo es
  **`wasmJs`** (no `js`): el target `js` de Compose está deprecado (el plugin
  `compose.web.targets(...)` se eliminó en Compose 1.13).
- **Un módulo, una responsabilidad.** Nada de carpetas `common`/`jvm`/`web`
  dentro de un mismo paquete: en KMP eso se hace con *source sets*.
- **La lógica de negocio va a `commonMain`.** Si un fichero necesita `java.*`,
  `javax.*`, `androidx.compose.ui.window.Window`, AWT o Swing, **no va a
  `commonMain`**: se define una interfaz en `commonMain` y se implementa en
  `jvmMain` / `wasmJsMain`.
- **Cero duplicación de código entre `:desktop` y `:web`.** Todo lo que no sea
  específico de la plataforma vive en `:shared`.
- **`git mv`, nunca copia+borro.** Al mover un fichero entre módulos se conserva
  el historial. No se renombra a mano lo que no hace falta.

### 1.2 Estilo de código

- **snake_case** para variables y funciones, **PascalCase** para clases y
  objetos. Kotlin idiomático, `when`/`if` exhaustivos cuando el compilador puede
  comprobarlo.
- **Kotlin tipado** siempre que se pueda: `val`/`var` con tipo explícito en
  APIs públicas, tipos de retorno explícitos en funciones que devuelven valores
  no triviales.
- **Comentarios y KDoc en español**, como todo el repo. El KDoc explica **por
  qué**, no **qué**; si el código es evidente, no lleva KDoc.
- Formato: 4 espacios, línea máxima ~100–120 caracteres, sin líneas en blanco
  al final de fichero.
- Nada de `println` / `System.out` / `console.log` en código de producción: se
  usa el `Logger` existente (`com.plyr.core.util.Logger` en JVM; su equivalente
  en web se define en el hito H4).

### 1.3 Convenciones de tests (crítico)

Este repo testea **mucho** y con un patrón muy concreto. **Copia el patrón
existente, no inventes uno nuevo.**

- **JUnit 4** (`org.junit.Test`, `org.junit.Assert.*`), no JUnit 5. Coherente con
  `core/src/commonTest` y `desktop/src/test`.
- **Nombres de test en `metodo_escenarioEnPalabras`**:
  `play_resolvesFirstAndPrefetchesWindow`, `seekTo_positionMovesInsideTrack`.
- **Los dobles de test son clases reales** en el source set de test, no lambdas
  anónimas: mira `desktop/src/test/kotlin/com/plyr/desktop/player/FakeAudioPlayer.kt`.
  Cuando un doble pase a ser compartido por varios módulos, **sube a
  `commonTest`** (p. ej. `FakeAudioPlayer`).
- **Tests de corrutinas deterministas**: `Dispatchers.Unconfined` + resolutores
  falsos (sin red). Es el patrón de `PlayerControllerTest`. Nada de `delay()` real
  ni de `runBlocking` con esperas.
- **Un test = un comportamiento.** Sin `assertTrue(true)` ni "tests" que solo
  comprueban que algo compila.
- **Todo fichero nuevo de producción va acompañado de su test** en el mismo
  commit. Si no es testeable, es que la abstracción está mal: revísala.
- Los tests de red (NewPipe, Supabase) **no** se ejecutan en CI: se prueban con
  dobles o se marcan explícitamente como de integración.

### 1.4 `detekt`

- Configuración raíz: `detekt.yml`. Objetivo declarado: **detectar código
  muerto y mala estructura**, no estilo.
- Cada módulo nuevo necesita su `detekt-baseline.xml` (vacío al principio).
  `:core` y `:desktop` ya lo tienen.
- `detekt` se ejecuta con la task **`detektMain`** (necesita type resolution
  para las reglas `Unused*`).
- **El código nuevo no se mete en el baseline.** El baseline es la deuda
  heredada; si algo nuevo dispara una regla, se arregla, no se silencia.
- Reglas a respetar de facto: nada de `LongMethod` (>60 líneas) ni
  `CyclomaticComplexMethod` en código nuevo; `LongParameterList` se ignora solo
  para `@Composable`; `TooManyFunctions` ignora las privadas.

### 1.5 CI

- Workflows en `.github/workflows/`: `ci.yml` (analysis / tests / build) y
  `auto-release.yml` (manual).
- **El CI existente no se toca para que siga verde.** Los jobs nuevos (web) se
  **añaden** como jobs nuevos.
- `ci.yml` usa JDK 21 y `./gradlew` con caché de Gradle.

### 1.6 Lo que NO se hace en este port

- No se reescribe la UI. Se **mueve** y se adapta.
- No se toca `:app` (Android) más que para, como mucho, un `actual` compartido.
- No se cambia el formato de `plyr-sync.zip`: debe seguir siendo ** interoperable
  con la app de escritorio y con Android**.
- No se introduce una librería de autenticación, cuentas ni base de datos en la
  nube. **El MVP no tiene cuentas** (ver §4.1).

---

## 2. Estado actual del repositorio

### 2.1 Módulos y tamaño

| Módulo | Tipo | Targets actuales | LOC (main) |
|---|---|---|---|
| `:app` | Android | `android` | 16.482 (`app/src/main/java/com/plyr/`) |
| `:core` | KMP | `android`, `jvm` | 2.553 (`commonMain`) + 2.489 (`jvmSharedMain`) |
| `:desktop` | **JVM puro** (`kotlin.jvm`) | — | 12.347 (73 ficheros) |
| `:shared` | — | — | **no existe** |
| `:web` | — | — | **no existe** |
| `:server` | — | — | **no existe** |

### 2.2 Qué se reutiliza tal cual

- **`:core/commonMain` (32 ficheros, 2.553 LOC) es Kotlin puro**: cero imports
  externos (solo `kotlinx.coroutines.core`). Compila a `wasmJs` **sin tocar una
  línea**. Contiene justo las piezas que hacen falta:
  - `com.plyr.player.AudioPlayer` / `AudioPlayerListener` / `PlaybackState` /
    `AudioTrack` → **la costura del reproductor**.
  - `com.plyr.viewmodel.QueueEngine` / `QueueIndex` / `PendingSkips` /
    `IdlePlayback` / `LoadingState` → **la lógica de cola**.
  - `com.plyr.model.AudioItem`, `com.plyr.utils.UrlParser`,
    `com.plyr.utils.TrackIdentity`, `com.plyr.i18n.TranslationCatalog`,
    `com.plyr.service.CoverCropMath`, `com.plyr.ui.components.ShareUrlPolicy`,
    `com.plyr.ui.components.PlaylistShare`, `com.plyr.database.*`.
- **`:desktop` (73 ficheros)**: 17 portables tal cual, 48 con `expect/actual`,
  8 desktop-only. Ver §6 para el inventario fichero a fichero.

### 2.3 Lo que bloquea el navegador

| Bloqueo | Por qué | Dónde se resuelve |
|---|---|---|
| **Motor de audio** | VLCJ + `libvlc` nativo no existe en navegador | H3 (`WebAudioPlayer` sobre `<audio>`) |
| **Extracción de YouTube** | NewPipeExtractor es solo-JVM (`org.schabi.newpipe.*`) | H1/H2 (servidor Ktor) |
| **Persistencia** | `JdbcSqliteDriver` + `java.util.prefs` | H4 (IndexedDB + localStorage) |
| **Sistema de ficheros** | `java.nio.file` (descargas, backup, cachés) | H4/H5 (OPFS / Cache Storage) |
| **Imágenes** | `java.awt.image.BufferedImage` + `javax.imageio` | H5 (Canvas + ImageBitmap) |
| **ZIP** | `java.util.zip` | H5 (se hace **en el servidor**, no en wasm) |

---

## 3. Arquitectura objetivo

```
┌────────────── navegador (Kotlin/Wasm + Compose) ──────────────────┐
│  :web     → ComposeViewport, entry point, actuals de plataforma   │
│  :shared  → UI completa + lógica de dominio (común jvm/wasmJs)    │
│  IndexedDB → biblioteca + cachés │ localStorage → config          │
└──────────────┬──────────────────────────────────────────────────┘
               │ fetch al mismo origen (mismo host/puerto)
               │ → sin CORS, sin anti-bot de Google, sin IP-binding
┌──────────────▼───────── :server (Ktor, JVM) ─────────────────────┐
│  /api/search · /api/playlist/{id} · /api/resolve · /api/metadata │
│  /api/stream/{videoId}   → PROXY Range-aware del audio          │
│  /api/sync/export.zip · /api/sync/import.zip                    │
│  /api/spotify/import                                           │
│  GET /            → estáticos de :web (index.html, .wasm, .js)   │
│  Reutiliza :core → NewPipeExtractor ya implementado              │
└──────────────┬──────────────────────────────────────────────────┘
               │ https → youtubei.googleapis.com / *.googlevideo.com
```

### 3.1 Por qué el proxy de audio es obligatorio

Las URLs de `*.googlevideo.com` llevan la **IP de quien las resolvió** y el CDN
rechaza (403) la reproducción desde otra IP. Si el servidor resuelve y el
navegador reproduce, **falla**. El proxy lo neutraliza: el servidor resuelve con
su IP y descarga los bytes, que son los que ve el navegador.

El proxy **debe reenviar la cabecera `Range`** y devolver `206 Partial Content`
con `Content-Range`, o el `<audio>` no podrá hacer *seek*.

### 3.2 Módulos objetivo

```
settings.gradle.kts
include(":app")      // Android — sin cambios
include(":core")     // KMP — + target wasmJs
include(":shared")   // KMP NUEVO — jvm() + wasmJs(): UI y dominio
include(":desktop")  // JVM — app fina: Main.kt, VLC, bandeja, MPRIS, actuals JVM
include(":web")      // KMP NUEVO — solo wasmJs: entry point + actuals web
include(":server")   // JVM NUEVO — Ktor
```

Dependencias entre módulos:

```
:app ──> :core
:desktop ──> :shared ──> :core
:web ──> :shared ──> :core
:server ──> :core        (y :shared:jvmMain si reutiliza DataExporter/Importer)
```

> `:server` **no** depende de `:shared` salvo que se comparta el export/import del
> ZIP (H5). Esa dependencia se añade en H5, no antes.

---

## 4. Decisiones de producto

### 4.1 Sin cuentas (decidido)

El MVP **no tiene login**. La biblioteca vive en el navegador (IndexedDB) y se
transfiere con `plyr-sync.zip`:

| Mecanismo | Cómo | Dónde |
|---|---|---|
| Caché local | IndexedDB del navegador → carga instantánea al abrir la URL | siempre |
| Exportar / importar | `plyr-sync.zip` (mismo formato que escritorio y Android) | manual, cualquier navegador |
| Sync "a un link" | File System Access API (`showDirectoryPicker()`) → la app escribe el ZIP **in situ** en la carpeta de Drive/OneDrive/Dropbox montada | futuro (H7) |

El ZIP se **construye y parsea en el servidor** (`java.util.zip` ya funciona
ahí). El navegador solo sube/descarga un blob binario → **cero código ZIP en
wasm** y **interoperabilidad total** con escritorio/Android.

### 4.2 YouTube siempre a través del servidor

Nunca se reimplementa InnerTube en el navegador: es frágil (anti-bot de Google,
cipher `n`, PO tokens) y depende de la IP del usuario. El servidor reutiliza el
NewPipeExtractor que **ya está escrito y funcionando** en `:core/jvmSharedMain`.

---

## 5. Hitos

### H0 · Andamiaje: los tres módulos nuevos, sin mover código

**Objetivo**: que exista un `:web` wasmJs que pinta algo, un `:server` Ktor que
responde, y que la mecánica de build esté validada. **Sin tocar `:desktop`.**

**Preflight**
- `settings.gradle.kts` incluye hoy `:app`, `:core`, `:desktop`.
- Node 20 y npm 9 ya están instalados en la máquina (los builds wasm los usan).
- Verificar versión de Compose: `1.12.1` en `gradle/libs.versions.toml`.

**Pasos**

1. **`:core`: añadir target `wasmJs`.**
   En `core/build.gradle.kts`, dentro de `kotlin { }`:
   ```kotlin
   wasmJs {
       browser()
       binaries.executable()
   }
   ```
   Crear `core/src/wasmJsMain/kotlin/` y `core/src/wasmJsTest/kotlin/`.
   `commonMain` **no se toca**: ya es Kotlin puro.
   ⚠️ `jvmSharedMain` **no** se conecta a `wasmJsMain` (contiene NewPipe, OkHttp
   y ZXing, que son solo-JVM). Solo `commonMain` es compartido.

2. **`:shared` nuevo** (`shared/build.gradle.kts`):
   ```kotlin
   plugins {
       alias(libs.plugins.kotlin.multiplatform)
       alias(libs.plugins.compose.multiplatform)
       alias(libs.plugins.kotlin.compose)
       alias(libs.plugins.sqldelight)
       alias(libs.plugins.detekt)
   }
   kotlin {
       jvmToolchain(21)
       jvm()
       wasmJs { browser(); binaries.executable() }
       sourceSets {
           val commonMain by getting {
               dependencies {
                   implementation(project(":core"))
                   implementation(compose.runtime)
                   implementation(compose.foundation)
                   implementation(compose.material3)
                   implementation(libs.kotlinx.coroutines.core)
               }
           }
           val jvmMain by getting
           val wasmJsMain by getting
       }
   }
   ```
   + `shared/detekt-baseline.xml` vacío (cabecera `<SmellBaseline>`).
   + `detekt { buildUponDefaultConfig = true; config.setFrom(rootProject.file("detekt.yml")); baseline.set(layout.projectDirectory.file("detekt-baseline.xml")) }`
   + `tasks.withType<dev.detekt.gradle.Detekt>().configureEach { exclude { it.file.invariantSeparatorsPath.contains("/generated/") } }`

3. **`:web` nuevo** (`web/build.gradle.kts`): solo `wasmJs`.
   ```kotlin
   plugins {
       alias(libs.plugins.kotlin.multiplatform)
       alias(libs.plugins.compose.multiplatform)
       alias(libs.plugins.kotlin.compose)
   }
   kotlin {
       wasmJs {
           browser {
               commonWebpackConfig { outputFileName = "plyr.js" }
           }
           binaries.executable()
       }
       sourceSets {
           val wasmJsMain by getting {
               dependencies {
                   implementation(project(":shared"))
                   implementation(project(":core"))
                   implementation(compose.runtime)
                   implementation(compose.foundation)
                   implementation(compose.material3)
               }
           }
       }
   }
   ```
   `web/src/wasmJsMain/kotlin/com/plyr/web/Main.kt`:
   ```kotlin
   package com.plyr.web

   import androidx.compose.ui.ExperimentalComposeUiApi
   import androidx.compose.ui.window.ComposeViewport
   import kotlinx.browser.document

   @OptIn(ExperimentalComposeUiApi::class)
   fun main() {
       ComposeViewport(document.body!!) {
           Text("plyr — web")
       }
   }
   ```
   `web/src/wasmJsMain/resources/index.html`:
   ```html
   <!DOCTYPE html>
   <html lang="es"><head><meta charset="UTF-8">
   <meta name="viewport" content="width=device-width, initial-scale=1.0">
   <title>plyr</title></head>
   <body style="margin:0;background:#0b0b0b"></body></html>
   ```
   ⚠️ `Text` necesita `import androidx.compose.material3.Text`.

4. **`:server` nuevo** (`server/build.gradle.kts`): JVM + Ktor.
   ```kotlin
   plugins {
       alias(libs.plugins.kotlin.jvm)
       alias(libs.plugins.detekt)
   }
   kotlin { jvmToolchain(21) }
   dependencies {
       implementation(project(":core"))
       implementation(libs.ktor.server.core)
       implementation(libs.ktor.server.netty)
       implementation(libs.ktor.server.content.negotiation)
       implementation(libs.ktor.serialization.kotlinx.json)
       implementation(libs.kotlinx.serialization.json)
       testImplementation(libs.junit)
   }
   ```
   `server/src/main/kotlin/com/plyr/server/Main.kt`:
   ```kotlin
   fun main() {
       embeddedServer(Netty, port = 8080) {
           install(ContentNegotiation) { json() }
           routing {
               get("/api/health") { call.respondText("ok") }
               get("/") { call.respondTextFile("web/index.html") }
           }
       }.start(wait = true)
   }
   ```
   Añadir a `gradle/libs.versions.toml`:
   ```toml
   ktor = "3.6.0"
   kotlinxSerialization = "1.12.0"
   ```
   con las entradas `ktor-server-core`, `ktor-server-netty`,
   `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`,
   `kotlinx-serialization-json`.

5. **`settings.gradle.kts`**: `include(":shared")`, `include(":web")`,
   `include(":server")`.

**Criterios de aceptación**
- `./gradlew :core:compileKotlinWasmJs` compila.
- `./gradlew :web:wasmJsBrowserDistribution` produce `web/build/dist/wasmJs/productionExecutable/`.
- `./gradlew :server:compileKotlin` compila y `:server:run` responde `ok` en
  `http://localhost:8080/api/health`.
- `:desktop:build` sigue generando los mismos artefactos (`.deb`, `.rpm`,
  `.AppImage`) → **el módulo `:desktop` no se ha tocado**.
- `:app`, `:core:jvmTest` y `:desktop:test` siguen verdes.

**Fuera de alcance**: mover código real, tocar `:desktop`, endpoint de YouTube.

---

### H1 · Servidor de extracción + proxy de audio

**Objetivo**: el servidor resuelve URLs de YouTube y las sirve al navegador a
través de un proxy con soporte `Range`. Es el desbloqueo de todo lo demás.

**Pasos**

1. **Contrato de API** (este bloque es normativo):

   | Endpoint | Request | Response |
   |---|---|---|
   | `GET /api/health` | — | `text/plain` `ok` |
   | `POST /api/search` | `{"query":"...","page":1,"maxVideos":25,"maxPlaylists":10}` | `{"videos":[AudioItem],"playlists":[YouTubePlaylistInfo],"hasMore":bool}` |
   | `GET /api/playlist/{id}` | — | `{"info":YouTubePlaylistInfo,"videos":[AudioItem]}` |
   | `POST /api/resolve` | `{"videoId":"...","forceRefresh":false}` | `{"url":"...","durationMs":123456}` |
   | `GET /api/stream/{videoId}` | cabeceras `Range` opcionales | audio binario, `206` con `Content-Range` si hay `Range` |
   | `POST /api/metadata` | `{"url":"..."}` | `MediaMetadata` (JSON) |

   `AudioItem` = `{title,url,videoId,channel,duration}` (ya existe en
   `:core/commonMain` → serializable tal cual).
   `YouTubePlaylistInfo` = `{playlistId,title,uploader,videoCount,thumbnailUrl}`
   (existe en `:core/jvmSharedMain` → hay que moverlo o duplicarlo como DTO).

2. **Mover/duplicar los DTO** que el servidor necesita. `YouTubePlaylistInfo` y
   `YouTubeSearchAllResult` viven hoy en `core/src/jvmSharedMain/.../network/YouTubeManager.kt`
   (JVM). El servidor es JVM → **puede usarlos tal cual**. No hace falta moverlos
   a `commonMain` en este hito. (Se moverán en H5 si el cliente web los necesita.)

3. **Implementar los handlers** en `server/src/main/kotlin/com/plyr/server/`:
   - `SearchRoutes.kt` → `YouTubeManager.searchAll(query, page, maxVideos, maxPlaylists)`
     (ya cachea y deduplica; reutilizar tal cual).
   - `ResolveRoutes.kt` → `YouTubeManager.getAudioUrl(videoId, forceRefresh)`.
   - `StreamRoutes.kt` → **el proxy**:
     ```
     GET /api/stream/{videoId}
       1. url = YouTubeManager.getAudioUrl(videoId)          // se resuelve con la IP del server
       2. GET url con la cabecera Range del cliente
       3. responde el body + status (200/206) + Content-Type + Content-Length
          + Content-Range + Accept-Ranges: bytes
     ```
     Usar `java.net.http.HttpClient` de la JDK (no hace falta OkHttp aquí) o
     `ktor-client-cio`. **Debe** reenviar `Range` tal cual y devolver `206`.
   - `MetadataRoutes.kt` → `MediaMetadataExtractor.extractMetadata(url)`.

4. **CORS**: en desarrollo, `install(CORS) { anyHost(); allowHeader(HttpHeaders.ContentType) }`
   para que el `wasmJsDevRun` en otro puerto pueda hablar con el server. En
   producción (mismo origen) no hace falta.

5. **Tests del servidor** (`server/src/test/kotlin/com/plyr/server/`):
   - `StreamRoutesTest.kt`: con un cliente HTTP falso (interfaz propia
     `StreamFetcher`) que devuelva un body conocido; verificar que la respuesta
     incluye `Content-Range` y `206` cuando el cliente pide `Range: bytes=0-99`,
     y `200` cuando no lo pide.
   - `SearchRoutesTest.kt`: serialización de la respuesta (se usa un doble de
     `YouTubeManager`, no la red).
   - ⚠️ **Nunca** pegarle a YouTube desde un test de CI.

**Criterios de aceptación**
- `POST /api/search` con `"daft punk"` devuelve vídeos con `videoId` de 11
  caracteres.
- `POST /api/resolve` devuelve una URL de `googlevideo.com`.
- `curl -H "Range: bytes=0-1023" -D- http://localhost:8080/api/stream/<id>`
  devuelve `206` y exactamente 1024 bytes.
- `curl -I http://localhost:8080/api/stream/<id>` devuelve `Accept-Ranges: bytes`.
- `./gradlew :server:test` verde.

---

### H2 · `:shared`: la lógica y la UI portable

**Objetivo**: mover el grueso de `:desktop` a `:shared`, empezando por las
piezas de **mayor valor y menor riesgo** (lógica) antes que la UI (que es
voluminosa pero mecánica).

Este hito se divide internamente en pasos numerados; **se puede dividir en
varios PR**.

**Paso 2.1 — `PlayerController` a `commonMain`** (el cambio de mayor retorno)

`desktop/src/main/kotlin/com/plyr/desktop/player/PlayerController.kt` (891
líneas) → `shared/src/commonMain/kotlin/com/plyr/shared/player/PlayerController.kt`.

- Es **casi** todo agnóstico: solo usa `java.util.concurrent.ConcurrentHashMap`
  (línea ~129). Sustituir por un `mutableMapOf` (la clase ya vive en un único
  `scope` serializado) → **una línea**.
- Depende de `:core/commonMain`: `AudioPlayer`, `AudioPlayerListener`,
  `AudioTrack`, `PlaybackState`, `QueueEngine`, `QueueIndex`, `IdlePlayback`,
  `LoadingState`, `PendingSkips`, `RepeatMode`, `AudioItem`, `MediaCommand`.
- Depende de `:desktop`: `com.plyr.desktop.i18n.Translations` (cadenas de error)
  y `com.plyr.desktop.player.Shuffle`.
  → **Plan**: `Translations` se mueve a `:shared/commonMain`; `Shuffle` también.
- **Mover sus tests**: `desktop/src/test/kotlin/com/plyr/desktop/player/PlayerControllerTest.kt`
  → `shared/src/commonTest/.../PlayerControllerTest.kt`, junto con
  `FakeAudioPlayer.kt` y el `FakeResolver` que usa.
  El test ya usa `Dispatchers.Unconfined` + dobles → funciona en `commonTest`
  sin cambios (salvo los imports de paquete).

**Paso 2.2 — Los 17 ficheros portables tal cual**

Mover con `git mv` de `desktop/src/main/kotlin/com/plyr/desktop/` a
`shared/src/commonMain/kotlin/com/plyr/shared/`:

```
database/PlaylistRepository.kt
database/SearchHistoryRepository.kt
player/PlayerTrack.kt
player/Shuffle.kt
playlists/SongListItem.kt
playlists/TrackSearchController.kt
search/YouTubePlaylistDetailView.kt
ui/FloatingMusicControls.kt
ui/KeyboardShortcuts.kt
ui/Screen.kt
ui/components/ActionBttn.kt
ui/components/AddToPlaylistDialog.kt
ui/components/Reorder.kt
ui/components/Titulo.kt
ui/components/TrackMenu.kt
ui/screens/QueueScreen.kt
ui/theme/Theme.kt
```

Renombrar el paquete `com.plyr.desktop.*` → `com.plyr.shared.*` en todos los
ficheros movidos y en sus importadores. **Un solo `package` nuevo** para todo el
módulo.

**Paso 2.3 — Los 11 ficheros de cambio trivial**

| Fichero | Cambio | Nota |
|---|---|---|
| `ui/utils/TimeFormat.kt` | `java.util.Locale` + `String.format` | usar `padStart` y `kotlin.text` |
| `ui/screens/SearchScreen.kt` | `String.format` | idem |
| `download/DownloadStatusBar.kt` | `String.format` ×3 | idem |
| `download/OfflineStorageDialog.kt` | `String.format` | idem |
| `playlists/CreatePlaylistView.kt` | `String.format` | idem |
| `playlists/PlaylistsScreen.kt` | `String.format` (L202) | idem |
| `playlists/PlaylistDetailView.kt` | `String.format` + `BufferedImage` | la imagen va a interfaz (§6) |
| `player/VlcPlaybackStateMachine.kt` | `kotlin.jvm.Volatile` | → `kotlin.concurrent.Volatile` (verificado en stdlib 2.3.21) |
| `player/PlayerController.kt` | `ConcurrentHashMap` | → `mutableMapOf` |
| `media/DesktopMediaIntegration.kt` | `System.getProperty("os.name")` | → `expect fun currentOsName(): String` |
| `playlists/PlaylistsController.kt` | `java.util.UUID` | → `kotlin.uuid.Uuid` (verificado en stdlib 2.3.21) |

**Paso 2.4 — Las 37 interfaces de plataforma**

Definir en `:shared/commonMain` las interfaces siguientes y mover los
implementadores a `jvmMain` (o a `wasmJsMain` en H3+):

| Interfaz (propuesta) | Sustituye a | Implementación JVM | Implementación web (H+) |
|---|---|---|---|
| `SettingsFactory` / `expect fun createSettings(): Settings` | `DesktopSettings` (`java.util.prefs`) | `PreferencesSettings` | `StorageSettings` (localStorage) — H4 |
| `expect fun createDriver(location: String): SqlDriver` | `JdbcSqliteDriver` | JDBC + SQLite | SQLDelight web worker — H4 |
| `ExternalUrlOpener` | `Browser.browse` (AWT `Desktop`) | `java.awt.Desktop` | `window.open` — H3 |
| `FilePicker` | `FileDialogs` (AWT `FileDialog`/Swing) | AWT | `<input type=file>` / FileKit — H5 |
| `ImageCodec` | `CoverImages`/`CoverStore` (AWT+ImageIO) | AWT | Canvas — H5 |
| `CoverStore` | `utils/CoverStore.kt` | ficheros | OPFS/IndexedDB — H5 |
| `AudioStore` (descargas) | `download/DownloadedAudioStore.kt` | `java.nio.file` | OPFS — H5 |
| `QrRenderer` | `qr/QrImages.kt` | Skia + ImageIO | Skia + `canvas.toBlob` — H5 |
| `Clipboard` | `java.awt.Toolkit` | AWT | `navigator.clipboard` — H5 |
| `MediaIntegration` | `media/DesktopMediaIntegration.kt` (MPRIS/tray) | MPRIS + bandeja | no-op + Media Session — H3 |
| `AppPaths` | `platform/AppPaths.kt` | `java.nio.file` | sin equivalente (paths virtuales) — H4 |
| `PlatformLogger` | `com.plyr.core.util.Logger` | `java.util.logging` | `console` — H4 |

Cada interfaz va en su propio fichero, con KDoc explicando el contrato, en
`shared/src/commonMain/kotlin/com/plyr/shared/platform/`.

**Paso 2.5 — El contenedor de navegación**

`ui/AudioListScreen.kt` (586 líneas) es el **cerebro de la app**: crea la
`PlayerController`, la base de datos, los repositorios, los controladores de
búsqueda/feed/playlists/descargas y decide qué pantalla se ve.

Se mueve a `:shared/commonMain` **con todas sus dependencias inyectadas por
lambda** (ya lo está: `openPlayer`, `sourceResolver`, `openDatabase`, …). Lo
único que hay que parametrizar es la fábrica de Collaborators que hoy está
hardcodeada dentro:

```kotlin
// Antes (en :desktop)
PlayerController(scope, openPlayer = { VlcAudioPlayer() }, sourceResolver = YouTubeTrackSourceResolver(store), ...)

// Después (en :shared) — la app inyecta lo específico de su plataforma
@Composable
fun AudioListScreen(platform: PlatformAdapters, ...)
```

donde `PlatformAdapters` agrupa las fábricas dependientes de la plataforma
(player, resolver, base de datos, ajustes, portadas, integraciones de medios,
diálogos de fichero, QR, portadas).

**Paso 2.6 — `:desktop` como app JVM fina**

Se queda en `desktop/src/main/kotlin/com/plyr/desktop/`:
```
Main.kt                              (Window/application) — se adapta a ComposeViewport-free
SoundCheck.kt                        (CLI con VLCJ)
config/DesktopSettings.kt            (PreferencesSettings)
media/DesktopTray.kt                 (AWT SystemTray)
media/MprisMediaIntegration.kt       (dbus-java)
media/DesktopMediaIntegration.kt     → pasa a ser el actual JVM de MediaIntegration
platform/AppPaths.kt
platform/Browser.kt                  → actual JVM de ExternalUrlOpener
platform/FileDialogs.kt              → actual JVM de FilePicker
player/VlcAudioPlayer.kt
player/VlcEnvironment.kt
player/VlcPlaybackStateMachine.kt    (el estado, no el player)
download/AudioDownloader.kt
download/DownloadedAudioStore.kt     → actual JVM de AudioStore
qr/QrImages.kt                       → actual JVM de QrRenderer
sync/CoverImages.kt
utils/CoverCache.kt
utils/CoverStore.kt                  → actual JVM de CoverStore
utils/UpdateChecker.kt
ui/theme/AppIcon.kt                  → actual JVM de carga de recursos
ui/theme/AsciiArt.kt
```

`desktop/build.gradle.kts`: cambiar `implementation(compose.desktop.currentOs)` por
`implementation(project(":shared"))` + `implementation(compose.desktop.currentOs)`
(este último sigue haciendo falta para `Window`, `application`, `SwingPanel`).

**Criterios de aceptación de H2**
- `./gradlew :shared:jvmTest` verde (incluye los tests de `PlayerController`
  movidos).
- `./gradlew :desktop:test` verde y con **el mismo número de tests** que antes
  (49 ficheros en `desktop/src/test` → los que se movieron a `:shared` ya no
  cuentan aquí, pero **ninguno se pierde**).
- `./gradlew :desktop:build` produce `.deb`, `.rpm` y `.AppImage` **igual que
  antes** (comparar nombres y tamaños con el build previo).
- `./gradlew :desktop:run` abre la ventana y reproduce una canción de YouTube
  (igual que hoy).
- `:app:assembleDebug` sigue compilando (`:app` no se ha tocado, pero `:core` sí).
- `./gradlew :shared:detektMain` verde **sin ampliar el baseline**.

**Riesgo principal**: el paso 2.4 (37 interfaces) es el más laborioso. Si el
volumen aprieta, **se puede aplazar H2.4–H2.6 y hacer H3 (player web) contra un
`:shared` parcial** que contenga solo `PlayerController` + las 17 portables. El
hito H3 no depende del resto.

---

### H3 · Reproductor web (`WebAudioPlayer`)

**Objetivo**: que suene música en el navegador. Es el hito con más
incertidumbre técnica → **cuanto antes, mejor**.

**Contrato exacto a implementar** (`core/src/commonMain/kotlin/com/plyr/player/AudioPlayer.kt`):

```kotlin
interface AudioPlayer {
    val mediaItemCount: Int
    val currentMediaItemIndex: Int
    val currentPosition: Long          // ms
    val duration: Long                 // ms, 0 si se desconoce
    val isPlaying: Boolean
    val playbackState: PlaybackState   // IDLE | BUFFERING | READY | ENDED
    fun setListener(listener: AudioPlayerListener)
    fun setMediaItems(items: List<AudioTrack>, startIndex: Int)
    fun addMediaItems(items: List<AudioTrack>)
    fun removeMediaItems(fromIndex: Int, toExclusive: Int)
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun clearMediaItems()
    fun seekTo(index: Int, positionMs: Long)   // positionMs puede ser AudioPlayer.TIME_UNSET
    fun seekTo(positionMs: Long)
    fun seekForward()                          // +10 s
    fun seekBack()                              // -10 s
    fun setRepeatOne(enabled: Boolean)
    fun release()
}
```

**Pasos**

1. `shared/src/wasmJsMain/kotlin/com/plyr/shared/player/WebAudioPlayer.kt`
   implementa `AudioPlayer` sobre un `<audio>` de HTML.

   - El `<audio>` **no** se crea en el DOM de forma visible: se puede crear
     detached o con `style.display = "none"`. Se recomienda `document.createElement("audio")`.
   - `setMediaItems` → guarda la lista en memoria, fija `currentIndex`, y
     `prepare()` asigna `audio.src = items[index].uri` + `audio.load()`.
   - `play()` → `audio.play()`; `pause()` → `audio.pause()`.
   - `seekTo(index, positionMs)` → cambia el índice, recarga `src` y, cuando
     `loadedmetadata` dispare, aplica `audio.currentTime = positionMs / 1000`.
     Es el mismo patrón de "posición pendiente" que usa `VlcAudioPlayer`
     (`pendingPosition`).
   - `playbackState`: `waiting`/`loadstart` → `BUFFERING`; `canplay` →
     `READY`; `ended` → `ENDED`; sin `src` → `IDLE`.
   - `onMediaItemTransition(automatic = true)` al terminar: si `repeatOne`,
     reinicia la actual; si hay siguiente en la ventana, avanza; si no, `ENDED`.
     **Copiar la lógica de `VlcAudioPlayer.onFinished()`** (fichero
     `desktop/src/main/kotlin/com/plyr/desktop/player/VlcAudioPlayer.kt`,
     líneas 220-252).
   - `currentPosition` → `audio.currentTime * 1000` (leer del DOM en cada acceso;
     el consumidor ya hace polling cada 500 ms en `AudioListScreen`).
   - `seekForward/seekBack` → ±10 s, alineado con `SKIP_MS` de Android.

2. **Media Session API** (`web/src/wasmJsMain/kotlin/com/plyr/web/media/`):
   - `navigator.mediaSession.metadata` con título/artista/portada.
   - `setActionHandler` para `play`, `pause`, `previoustrack`, `nexttrack`,
     `seekbackward`, `seekforward`, `seekto`, `stop`.
   - Sustituye a MPRIS y a la bandeja del sistema en web.

3. **`WebTrackSourceResolver`** (`shared/src/wasmJsMain/.../player/`):
   - Implementa el mismo `TrackSourceResolver` que
     `desktop/.../player/YouTubeTrackSourceResolver.kt`.
   - `resolve()` → `POST /api/resolve` con `{videoId}` → devuelve la URL
     **`/api/stream/{videoId}` del propio servidor** (no la URL directa de
     googlevideo: la IP-binding lo rompe).
   - `forceRefresh` → se pasa al servidor, que ya tiene la caché de
     `YouTubeManager` con `invalidate()`.

4. **Test de `WebAudioPlayer`**: no hay DOM en `wasmJsTest` sin navegador.
   → Extraer la **máquina de estados** a `commonMain` reutilizando el patrón ya
   existente: `desktop/.../player/VlcPlaybackStateMachine.kt` es exactamente esa
   máquina y ya está testeada
   (`desktop/src/test/.../VlcPlaybackStateMachineTest.kt`).
   **Opción recomendada**: crear `shared/src/commonMain/.../player/PlaybackStateMachine.kt`
   genérica (o reutilizar la de VLCJ tal cual, si se independiza de VLCJ) y
   testearla en `commonTest`. El `WebAudioPlayer` queda como el adaptador fino
   sobre DOM.

**Criterios de aceptación**
- `./gradlew :web:wasmJsBrowserDevelopmentRun` (o servir el dist y abrirlo)
  arranca la app web.
- Buscar "daft punk" devuelve resultados **conCover y con vídeo reproducible**.
- Al pulsar reproducir: **suena audio** (verificable por `currentPosition`
  avanzando y por `duration > 0`).
- El botón de siguiente salta a la siguiente canción **sin recargar la página**.
- El *seek* (±10 s y barra de progreso) mueve `currentPosition`.
- La cola sigue funcionando con `PlayerController` (mismos tests que en
  escritorio, ahora en `commonTest`).
- `navigator.mediaSession` registrado: aparecen los controles en la pestaña del
  navegador y los botones multimedia del SO funcionan.

---

### H4 · Almacenamiento en el navegador

**Objetivo**: que la biblioteca, la configuración y las cachés sobrevivan a
cerrar el navegador, y que la app web sea usable de verdad.

**Pasos**

1. **Base de datos** — SQLDelight:
   - `web`/`shared` declaran `sqldelight { databases { create("PlyrDatabase") { packageName.set("com.plyr.shared.database") } } }`.
   - Los `.sq` (`desktop/src/main/sqldelight/com/plyr/desktop/database/*.sq`) son
     **SQL puro** → se mueven a `shared/src/commonMain/sqldelight/...` **sin
     cambios**.
   - Driver web: `app.cash.sqldelight:web-worker-driver-wasm-js:2.4.1`
     (verificado en Maven Central).
   - `PlaylistRepository` y `SearchHistoryRepository` **no cambian** (ya son
     portables): solo cambia el `SqlDriver` que se les inyecta.
   - ⚠️ **Plan B**: si el driver web de SQLDelight resulta inmaduro, escribir un
     `SqlDriver` propio sobre IndexedDB. Para eso, `PlaylistRepository` debe
     depender **solo** de la interfaz `SqlDriver` (ya lo hace) → el cambio sería
     de un solo fichero.

2. **Ajustes (config)** — `multiplatform-settings`:
   - `multiplatform-settings-wasm-js:1.3.0` (verificado).
   - **Mismas claves** que `DesktopConfig` (`theme`, `language`, `repeat_mode`,
     `search_engine`, `user_nickname`, `deleted_playlist_ids`,
     `removed_liked_track_keys`, `backup_folder`, `backup_hash`).
   - `DesktopConfig` → renombrar a `AppConfig` y moverlo a `:shared/commonMain`
     con `expect fun createSettings(): Settings`.

3. **Tombstones**: reutilizar `InMemoryTombstoneStore` (ya portable) + un
   `SettingsTombstoneStore` común que escriba en `Settings`.

4. **Caché de portadas**: el `CoverCache` de disco → **Cache Storage API** del
   navegador (o IndexedDB). Implementar `expect/actual` del `CoverCache`.

5. **Carga de portadas**: Coil 3 ya tiene artefactos wasm:
   - `io.coil-kt.coil3:coil-compose-wasm-js:3.6.3`
   - `io.coil-kt.coil3:coil-network-ktor3-wasm-js:3.6.3`
   `i.ytimg.com` manda `Access-Control-Allow-Origin: *` → carga directa.

6. **Logger**: `expect/actual` → `console` en web.

7. **Caché de la biblioteca en el navegador**: es exactamente lo que pide el
   producto ("que cada vez que entro directamente se use este"). Con IndexedDB
   (paso 1) ya está: al abrir la URL, la app lee de IndexedDB y pinta al
   instante.

**Criterios de aceptación**
- Crear una lista, recargar la página → la lista sigue ahí.
- Cerrar el navegador y volver a abrir → sigue ahí.
- Cambiar el tema y el idioma → persisten.
- El historial de búsqueda persiste (tabla `search_history`, tope 50).
- `./gradlew :shared:wasmJsTest` verde.

---

### H5 · Features que dependen de la plataforma

**Objetivo**: cerrar el paridad con el escritorio. **Cada punto es un PR
independiente**; se pueden priorizar o descartar.

| # | Feature | Enfoque | Complejidad |
|---|---|---|---|
| 5.1 | **Export/import del ZIP** | `POST /api/sync/export.zip` y `/api/sync/import.zip` en el servidor, reutilizando `DataExporter`/`DataImporter` (JVM). El navegador hace `fetch` con `Blob`. **Cero ZIP en wasm.** El `DataExporter`/`DataImporter` actuales están en `:desktop` → mover a `:shared/jvmMain` (usan `java.util.zip`, `java.nio`, `javax.imageio`) y que `:server` dependa de `:shared`. | Media |
| 5.2 | **Import de Spotify** | Exponer `SpotifyImport` (ya escrito, `OkHttp`+`org.json`) como endpoint del servidor. El navegador lo llama. | Baja |
| 5.3 | **QR: generar y compartir** | El **encoder** ZXing no existe en wasm → reimplementar el encoder QR en Kotlin común (algoritmo QR, sin decodificador: el escritorio solo genera; el decodificador ya se retiró en M28). Compartir con `navigator.share` + `navigator.clipboard`. Descargar PNG con `canvas.toBlob`. | Media |
| 5.4 | **Escanear QR con la cámara** | `BarcodeDetector` (Chrome/Edge/Safari 17+) + `getUserMedia`. Sustituye al NFC en web. Requiere HTTPS o `localhost`. | Media |
| 5.5 | **Descargas offline** | `AudioStore` web → **OPFS** (`navigator.storage.getDirectory()`). El proxy sirve con CORS → se puede `fetch` el audio y guardarlo como blob. | Media |
| 5.6 | **Portada de usuario + recorte** | `ImageCodec` web → `<canvas>` + `createImageBitmap` + `toBlob('image/jpeg')`. Sustituye a `BufferedImage`/`ImageIO`. | Media |
| 5.7 | **Recursos de classpath** | `ui/theme/AppIcon.kt` y `ui/theme/AsciiArt.kt` usan `useResource`/`loadImageBitmap` (recursos de classpath, **no existen en wasm**) → migrar a `composeResources` (`org.jetbrains.compose:components-resources:1.12.1`, verificado). | Baja |
| 5.8 | **Feed / Supabase** | `SupabaseClient` (JVM: `HttpURLConnection` + `org.json`) → versión `commonMain` con **Ktor client** (`ktor-client-js` para wasm) + `kotlinx.serialization`. PostgREST ya trae CORS y la anon key es pública → se puede llamar **directo desde el navegador** sin tocar el servidor. | Baja |
| 5.9 | **UpdateChecker** | `fetch` + kotlinx.serialization contra la API de GitHub. | Baja |
| 5.10 | **Diálogos de fichero** | FileKit (`io.github.vinceglb:filekit-dialogs-wasm-js:0.16.0`, verificado) o `<input type="file">`. | Baja |
| 5.11 | **Abrir URL externa** | `window.open` (sustituye a `java.awt.Desktop.browse`). | Trivial |

**Criterios de aceptación de H5**
- Exportar desde la web genera un `plyr-sync.zip` que **el escritorio importa
  sin errores** (y al revés).
- El QR generado en la web se lee con la app de escritorio.
- La cámara detecta un QR en el navegador.
- Una descarga offline sobrevive a cerrar el navegador y se reproduce desde OPFS.
- El feed carga las recomendaciones de Supabase desde el navegador.

---

### H6 · Pulido, responsive, PWA, CI

**Objetivo**: que la app web sea cómoda de usar a diario y publicable.

**Pasos**

1. **Responsive**: el escritorio es "rail lateral fijo + panel". En web:
   - ≥900 px → rail fijo (igual que escritorio).
   - <900 px → rail plegable (drawer) + barra inferior.
   - `core/commonMain/viewmodel/WindowState.kt` ya modela estados de ventana →
   reutilizarlo.

2. **PWA**:
   - `manifest.webmanifest` (nombre, icono, `display: standalone`, tema oscuro).
   - `service-worker.js` que precachee el shell (index.html + .wasm + .js) →
     la app abre offline con lo ya cacheado.
   - `beforeinstallprompt` → botón "Instalar".

3. **`run.sh`**: nuevos comandos, siguiendo el estilo existente:
   ```
   web-build      → ./gradlew :web:wasmJsBrowserDistribution
   web-run        → compila :web y arranca :server sirviendo los estáticos
   web-test       → ./gradlew :shared:wasmJsTest :server:test
   ```
   Actualizar el `usage()` y el `case` del dispatcher. Mantener el patrón
   `run_gradle` y el `extra` de flags que ya usa el script.

4. **CI** (`.github/workflows/ci.yml`): **añadir** un job `web` (no tocar los
   existentes):
   ```yaml
   web:
     runs-on: ubuntu-latest
     steps:
       - uses: actions/checkout@v4
       - uses: actions/setup-java@v4
         with: { distribution: temurin, java-version: '21', cache: gradle }
       - run: ./gradlew :shared:wasmJsTest :server:test :web:wasmJsBrowserDistribution
       - run: ./gradlew :shared:detektMain :server:detektMain :web:detektMain
   ```
   ⚠️ El build wasm en CI necesita Node: `actions/setup-node@v4` con Node 20.

5. **`README.md`**: sección "web" con cómo ejecutarlo y qué falta respecto al
   escritorio (NFC, bandeja/MPRIS).

**Criterios de aceptación**
- La app es usable a 360 px de ancho (móvil).
- "Instalar" funciona en Chrome → se abre como app independiente.
- Con el servidor apagado, la PWA abre y muestra la biblioteca cacheada.
- `./run.sh web-run` levanta todo en `http://localhost:8080`.
- CI en verde con el job `web` añadido.

---

### H7 · Sync automático y publicación (futuro, no bloquea H0-H6)

**Objetivo**: la experiencia "como Spotify" **sin cuentas**.

1. **File System Access API** (`showDirectoryPicker()`):
   - El usuario elige **una vez** la carpeta de Drive/OneDrive/Dropbox.
   - La app escribe/lee `plyr-sync.zip` **in situ** → sincronización real
     (Drive la sube sola).
   - ⚠️ Solo Chromium. ⚠️ Requiere HTTPS o `localhost`. ⚠️ El permiso hay que
     re-concederlo en cada sesión → guardar el handle en IndexedDB no basta,
     hay que pedirlo con un gesto del usuario.
   - Un **symlink** en Linux a la carpeta de Drive funciona igual.

2. **`plyr.link/<token>`** (opcional, requiere servidor):
   - `POST /api/sync/publish` → guarda el ZIP y devuelve un token corto con
     expiración.
   - `GET /plyr.link/<token>` → descarga el ZIP (o abre una web que lo importe).
   - Privado por token, sin cuentas. El fichero vive en nuestro servidor.

3. **Publicación**: `:server` sirve API + estáticos → **un solo artefacto**.
   - Docker: imagen con JRE 21 + el JAR.
   - Despliegue: Fly.io / Railway / un VPS propio. Detrás de un proxy con
     **HTTPS** (obligatorio para cámara, Media Session y File System Access).
   - Configuración por variables de entorno: `PORT`, `SUPABASE_URL`,
     `SUPABASE_ANON_KEY`, `PROXY_MODE` (`direct` | `proxy`).

---

## 6. Inventario de `:desktop` (referencia para H2)

Clasificación de los **73 ficheros** de `desktop/src/main/kotlin/com/plyr/desktop/`:

### 6.1 Desktop-only (8) → se quedan en `:desktop`

| Fichero | Motivo |
|---|---|
| `Main.kt` | `application {}` / `Window {}` (Compose Desktop) + bandeja AWT |
| `SoundCheck.kt` | CLI con VLCJ y `libvlc` |
| `config/DesktopSettings.kt` | `PreferencesSettings` (`java.util.prefs`) |
| `media/DesktopTray.kt` | `SystemTray` / `TrayIcon` (AWT) |
| `media/MprisMediaIntegration.kt` | `dbus-java` (`org.mpris.MediaPlayer2`) |
| `player/VlcAudioPlayer.kt` | VLCJ + `libvlc` nativo |
| `player/VlcEnvironment.kt` | localiza la biblioteca nativa |
| `download/AudioDownloader.kt` | OkHttp + `java.io` + NewPipe |

### 6.2 Portables tal cual (17) → `git mv` a `:shared/commonMain`

`database/PlaylistRepository.kt` · `database/SearchHistoryRepository.kt` ·
`player/PlayerTrack.kt` · `player/Shuffle.kt` · `playlists/SongListItem.kt` ·
`playlists/TrackSearchController.kt` · `search/YouTubePlaylistDetailView.kt` ·
`ui/FloatingMusicControls.kt` · `ui/KeyboardShortcuts.kt` · `ui/Screen.kt` ·
`ui/components/ActionBttn.kt` · `ui/components/AddToPlaylistDialog.kt` ·
`ui/components/Reorder.kt` · `ui/components/Titulo.kt` ·
`ui/components/TrackMenu.kt` · `ui/screens/QueueScreen.kt` · `ui/theme/Theme.kt`

### 6.3 Cambio trivial (11) → `:shared/commonMain` con ajuste de 1–5 líneas

`ui/utils/TimeFormat.kt` · `ui/screens/SearchScreen.kt` ·
`download/DownloadStatusBar.kt` · `download/OfflineStorageDialog.kt` ·
`playlists/CreatePlaylistView.kt` · `playlists/PlaylistsScreen.kt` ·
`playlists/PlaylistDetailView.kt` · `player/VlcPlaybackStateMachine.kt` ·
`player/PlayerController.kt` · `media/DesktopMediaIntegration.kt` ·
`playlists/PlaylistsController.kt`

### 6.4 Necesitan interfaz de plataforma (37)

| Fichero | Abstracción |
|---|---|
| `config/DesktopConfig.kt` | `SettingsFactory` (→ `AppConfig`) |
| `database/DesktopDatabase.kt` | `expect fun createDriver(...)` |
| `database/TombstoneStore.kt` | `SettingsTombstoneStore` común |
| `download/DownloadController.kt` | `AudioStore` + `AudioDownloader` (interfaz) |
| `feed/FeedController.kt` | `SupabaseClient` común (Ktor) |
| `feed/FeedScreen.kt` | modelo `Recommendation` a `commonMain` + Coil web |
| `i18n/Translations.kt` | lee de `AppConfig` (común) |
| `player/YouTubeTrackSourceResolver.kt` | `TrackSourceResolver` (ya `fun interface`) |
| `platform/AppPaths.kt` | `AppPaths` interfaz |
| `platform/Browser.kt` | `ExternalUrlOpener` |
| `platform/FileDialogs.kt` | `FilePicker` |
| `playlists/PlaylistCover.kt` | Coil (backend de red) |
| `playlists/CoverCropDialog.kt` | `ImageCodec` |
| `qr/QrImages.kt` | `QrRenderer` |
| `qr/QrShareDialog.kt` | `Clipboard` + `QrRenderer` + `FilePicker` |
| `qr/RecommendationSender.kt` | `SupabaseClient` común |
| `search/SearchController.kt` | `YouTubePlaylistInfo` a `commonMain` |
| `search/YouTubePlaylistDetailController.kt` | ídem |
| `spotify/SpotifyImportController.kt` | `SpotifyImport` a `commonMain` o endpoint |
| `spotify/SpotifyImportService.kt` | ídem |
| `sync/BackupConfig.kt` | `Settings` + `AppPaths` |
| `sync/BackupController.kt` | `FilePicker` + `BackupFolder` |
| `sync/BackupFolder.kt` | aritmética de `String` (no `Path`) |
| `sync/CoverImages.kt` | `ImageCodec` |
| `sync/DataExporter.kt` | `java.util.zip` → **al servidor** (H5.1) |
| `sync/DataImporter.kt` | ídem |
| `sync/DataSync.kt` | `FilePicker` + `Settings` |
| `ui/AudioListScreen.kt` | `PlatformAdapters` inyectado |
| `ui/components/PlaylistRail.kt` | `composeResources` + Coil |
| `ui/screens/ConfigScreen.kt` | `AppPaths` + `QrShareDialog` + `BackupController` |
| `ui/screens/HomeScreen.kt` | `ExternalUrlOpener` + `composeResources` + `UpdateChecker` |
| `ui/theme/AppIcon.kt` | `composeResources` |
| `ui/theme/AsciiArt.kt` | `composeResources` |
| `utils/CoverCache.kt` | `expect/actual` de caché |
| `utils/CoverStore.kt` | `CoverStore` interfaz |
| `utils/UpdateChecker.kt` | `fetch` + kotlinx.serialization |

---

## 7. Dependencias nuevas (versiones verificadas en Maven Central)

| Artefacto | Versión | Para qué | Target |
|---|---|---|---|
| `org.jetbrains.compose.ui:ui-wasm-js` | **1.12.1** | UI web (coincide con tu Compose) | wasm |
| `org.jetbrains.compose:components-resources` | **1.12.1** | `AsciiArt`, `AppIcon` | multi |
| `org.jetbrains.compose.material3:material3` | 1.12.0-alpha03 | Material 3 (lo que resuelve `compose.material3` con el plugin 1.12.1) | multi |
| `io.coil-kt.coil3:coil-compose-wasm-js` | **3.6.3** | portadas (coincide con tu coil3) | wasm |
| `io.coil-kt.coil3:coil-network-ktor3-wasm-js` | **3.6.3** | backend de red de Coil | wasm |
| `app.cash.sqldelight:web-worker-driver-wasm-js` | **2.4.1** | driver SQLite en el navegador | wasm |
| `com.russhwolf:multiplatform-settings-wasm-js` | **1.3.0** | config en localStorage | wasm |
| `io.ktor:ktor-server-core-jvm` | 3.6.0 | servidor HTTP | jvm |
| `io.ktor:ktor-server-netty-jvm` | 3.6.0 | motor del servidor | jvm |
| `io.ktor:ktor-server-content-negotiation-jvm` | 3.6.0 | JSON | jvm |
| `io.ktor:ktor-serialization-kotlinx-json-jvm` | 3.6.0 | JSON | jvm |
| `io.ktor:ktor-client-js` | 3.6.0 | HTTP desde el navegador | js/wasm |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | JSON | multi |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.8.0 | fechas | multi |
| `io.github.vinceglb:filekit-dialogs-wasm-js` | 0.16.0 | pickers de fichero | wasm |

**Verificado en el stdlib de Kotlin 2.3.21** (ya está, no hay que añadir nada):
`kotlin.concurrent.Volatile` · `kotlin.uuid.Uuid`.

**Verificado en `ui-wasm-js-1.12.1.klib`**: `androidx.compose.ui.window.Dialog`,
`DialogProperties`, `Popup` y `PopupProperties` **existen** en wasm → los
diálogos de la UI sobreviven sin reescribirse.

---

## 8. Riesgos

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| El driver web de SQLDelight es inmaduro | Media | Medio | `PlaylistRepository` depende solo de `SqlDriver` → cambiar de driver es 1 fichero |
| Algún fichero de UI no compila a wasm | Media | Medio | 17 ya son portables; el resto son cambios mecánicos; commits pequeños |
| El proxy consume mucho ancho de banda | Alta | Medio | modo `direct` cuando servidor y cliente comparten IP (localhost) |
| YouTube cambia la extracción | Media | Bajo | NewPipe ya lo resuelve; es el mismo código que usas hoy |
| 403 de googlevideo por IP | — | — | El proxy lo elimina por construcción |
| `useResource` no existe en wasm | Cierta | Bajo | `composeResources` |
| Regresión en el empaquetado de escritorio | Baja | Alto | H2.6 compara artefactos antes/después; `:desktop:test` debe seguir verde |
| La autoplay policy del navegador bloquea el primer play | Cierta | Bajo | Se reproduce tras un clic del usuario (requisito del navegador, no del diseño) |
| `formatTimestamp` / fechas con `SimpleDateFormat` | Media | Bajo | `kotlinx-datetime` |

---

## 9. Checklist global de aceptación

- [ ] `:app` (Android) compila y sus tests pasan, **sin cambios**.
- [ ] `:desktop` compila, empaqueta `.deb`/`.rpm`/`.AppImage` y sus tests pasan.
- [ ] `:core` compila en `jvm`, `android` y `wasmJs`.
- [ ] `:shared` compila en `jvm` y `wasmJs`; `commonTest` verde.
- [ ] `:web` produce un bundle ejecutable en el navegador.
- [ ] `:server` sirve la API **y** los estáticos de la web desde el mismo origen.
- [ ] Se puede buscar en YouTube, crear listas, reproducirlas y hacer seek.
- [ ] La biblioteca persiste entre recargas y reinicios del navegador.
- [ ] El `plyr-sync.zip` exportado en la web se importa en el escritorio y al revés.
- [ ] El feed de Supabase carga desde el navegador.
- [ ] `detektMain` verde en `:core`, `:shared`, `:desktop`, `:web`, `:server`
      **sin ampliar los baselines**.
- [ ] `./run.sh check` sigue verde.
- [ ] CI en verde con el job `web` añadido.
- [ ] `README.md` documenta el modo web y sus límites (NFC, bandeja/MPRIS).

---

## 10. Notas de referencia

- **Idioma**: el repo está comentado en español. Mantenerlo.
- **Refs a hitos del repo** (`M4`, `B57`, `M16`…) aparecen en los KDoc como
  trazabilidad de los hitos de desarrollo. Al escribir código nuevo, **añadir
  una referencia al hito de este documento** cuando aporte contexto.
- **El `detekt-baseline.xml` solo crece si el código es preexistente**: el código
  nuevo se arregla, no se silencia.
- **No tocar `.github/workflows/auto-release.yml`**: es el empaquetado de
  Android/escritorio y no tiene que ver con el port web.
- `run.sh` monta JDK 21 y el Android SDK en `/tmp/plyr-android` de forma
  autocontenida: cualquier comando nuevo debe usar su helper `run_gradle` para
  respetar ese entorno.
