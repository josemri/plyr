# Reporte de análisis de PLYR

**Fecha:** 2026-09-29
**Alcance:** `app/src/main/java/com/plyr` (71 archivos, ~14.757 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual (lectura + verificación de cada hallazgo contra el código). **No se ha compilado ni ejecutado ningún test**, tal como se pidió: nada de lo que sigue está verificado en runtime, solo leído.

> **Aviso sobre este informe.** Es una reescritura, no una actualización. La
> numeración de bugs es **nueva** y **no guarda relación** con la del informe
> anterior. Todo lo que estaba resuelto y ya no era cierto se ha borrado (§10).
> Las cifras de tests son un **recuento estático** de anotaciones `@Test`, no el
> resultado de una ejecución: `./run.sh test` no se ha lanzado.

---

## 1. Resumen ejecutivo

PLYR es una app de música **YouTube-only + local** en estado funcional. La
migración Spotify → YouTube está hecha, no queda `runBlocking`, las
dependencias están limpias y la lógica pura de cola está extraída y testeada.

**Los dos fallos que has reportado están encontrados, y no son el mismo bug.**
El tercero que recordabas —añadir canciones a una lista— también existe, y es
peor: **ninguna de las cinco rutas para hacerlo funciona** (§1.3). En total,
**47 bugs activos** verificados contra el código.
### 1.1 El slide a "liked" no funciona → **es un bug de datos, no de gesto**

`PlaylistScreen` construye el `Song` **sin `youtubeId`** en los tres sitios donde
se lista una playlist (`PlaylistScreen.kt:719`, `:793`, `:853`):

```kotlin
val song = Song(number = index + 1, title = track.name,
                artist = track.getArtistNames(),
                remoteId = track.id,                       // ← no hay youtubeId
                shareUrl = "https://www.youtube.com/watch?v=${track.id}")
```

`SongListItem.executeSwipeAction` no lo comprueba y lo sustituye por cadena
vacía (`SongListItem.kt:421-432`):

```kotlin
Config.SWIPE_ACTION_ADD_TO_LIKED -> {
    val isNowLiked = repo.toggleLikeTrack(
        youtubeVideoId = song.youtubeId ?: "",           // ← siempre ""
        ...
```

Y `toggleLikeTrack` busca por `youtubeVideoId` con **igualdad exacta**
(`PlaylistLocalRepository.kt:61-63`):

```kotlin
val tracks = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
val existing = tracks.find { it.youtubeVideoId == youtubeVideoId }
```

Consecuencias, en orden:

1. El primer swipe guarda una pista con `youtubeVideoId = ""` en `liked_songs`.
2. El swipe sobre **otra** canción sin `youtubeId` encuentra esa misma fila
   (`"" == ""`) y **la borra** en lugar de marcar la nueva. Por eso "a veces no
   funciona": depende del estado previo de la lista de favoritos.
3. `isTrackLiked("")` devuelve `true` para cualquier canción, así que el popup
   de la fila muestra siempre `♥ liked` (`SongListItem.kt:285-290`).
4. La pista guardada sin id de YouTube luego hay que resolverla por
   nombre/artista en cada reproducción (`YouTubeManager.resolveVideoId`), lo que
   además hace que dos canciones parecidas colisionen en la misma fila.

Las pantallas de **búsqueda** sí pasan el id real (`SearchScreen.kt:447`,
`YouTubeSearchResults.kt:226`, `YouTubePlaylistDetailView.kt:226`,
`QueueScreen.kt:65`), y ahí el like sí funciona. Por eso parece intermitente: en
la lista de reproducción no funciona, en la búsqueda sí.

**Hay un segundo defecto en el mismo gesto, independiente del anterior:** el
`pointerInput` del swipe está fijado a `Unit` (`SongListItem.kt:148`), así que
captura `song`, `index` y `trackEntities` en la primera composición y **nunca se
reinicia**. En las cuatro pantallas que lo usan, cualquier reordenación de la
lista deja el swipe actuando sobre la pista que había en ese índice en el primer
frame, no sobre la que se ve.

Y un tercero, en la propia mecánica del gesto (`SongListItem.kt:194-199`): cada
evento de drag lanza una corrutina que hace `offsetX.snapTo(...)`. `Animatable`
serializa con `MutatorMutex`, de modo que las corrutinas lanzadas se cancelan
entre sí y el desplazamiento acumulado se pierde. Además `onDragEnd` evalúa el
umbral **dentro** de una corrutina (`SongListItem.kt:150-153`), con el `snapTo`
pendiente aún en vuelo, así que la comparación se hace sobre un valor que aún no
es el final. Juntas, estas dos cosas producen exactamente el "a veces sí, a veces
no" del swipe.

### 1.2 La canción carga dos veces → tu hipótesis es correcta, y hay dos causas

**Causa 1: la invalidación de la URL caducada casi nunca se ejecuta.**
Cuando el reproductor falla, `onItemUnplayable` intenta tirar la URL de la caché
(`PlayerViewModel.kt:589-592`):

```kotlin
val expiredUrl = isHttpStatusError(error)
if (expiredUrl) {
    currentVideoId?.let { YouTubeManager.invalidate(it) }
}
```

Pero `currentVideoId` se sobrescribe con `queue[i].youtubeVideoId` en
`syncIndexFromWindow` (`:660`) y en `setCurrentIndex` (`:668`), y ese campo es
**`null` para toda pista que no venía con id de YouTube de origen** — o sea,
casi todas, porque las que se resuelven por búsqueda guardan el id solo en
memoria. Solo `startAt` lo repone con el id realmente resuelto (`:358`). En la
práctica, cuando salta de canción el valor vuelve a `null`, la invalidación es un
no-op, y el `playIndex(reResolve = true)` posterior (`:390`, `:608`) vuelve a
pedir la URL... que `getAudioUrl` le devuelve **de la caché, la misma URL
muerta** (`YouTubeManager.kt:68-80`). El `MediaItem` nuevo falla otra vez, la
cola salta, y el ciclo se repite hasta agotar `MAX_CONSECUTIVE_FAILURES`.

**Causa 2: no hay deduplicación de extracciones en vuelo.** `getAudioUrl` no
tiene *single-flight*: dos llamadas concurrentes del mismo `videoId` lanzan dos
extracciones de red y la segunda sobrescribe a la primera en la caché. Y eso
ocurre en cada transición, porque `growWindow()` se llama **dos veces por
salto**:

- `onMediaItemTransition(reason = AUTO)` → `growWindow()` (`PlayerViewModel.kt:163`)
- `onTrackEnded()` → `playIndex()` → `growWindow()` (`:403`)

y en la carga inicial, `onMediaItemTransition` (por el `setMediaItem` de
`startAt`) más el `growWindow()` explícito de `startAt` (`:366`). Cada llamada
hace `prefetchJob?.cancel()` y relanza (`:479-481`), y como la extracción es
`withContext(Dispatchers.IO)` sobre OkHttp/NewPipe, **no es interrumpible**: la
corrutina cancelada sigue extrayendo hasta el final pero ya no rellena la caché a
tiempo, así que la relanzada vuelve a empezar de cero. Dos extracciones, misma
canción, misma transición.

### 1.3 Añadir canciones a una lista → sí falla, por **cinco** mecanismos distintos

Tu intuición era correcta, y el problema es peor de lo que recuerdas: **no hay
ninguna ruta de "añadir canciones a una lista" que funcione de principio a
fin**. Hay cinco, y las cinco están rotas o son no-ops. Detalle por mecanismo:

#### 1. El swipe "añadir a playlist" no hace nada (no-op puro) — **B2**

`ConfigScreen.kt:266,274,296,304` ofrece `swipe_action_playlist` como opción
válida de swipe izquierda **y** derecha. `SongListItem.kt:56` le dibuja el icono
`≡`, y `SongListItem.kt:446-448` la implementa así:

```kotlin
Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> {
    Log.d("SongListItem", "Add to playlist (no-op): ${song.title}")
}
```

Solo un log. Ni diálogo, ni escritura, ni navegación. Encima, el parámetro
`onShowPlaylistDialog` que recibe la función **está cableado a `{}` en las dos
ramas del gesto** (`SongListItem.kt:165` y `:182`), así que la única vía por la
que el diálogo podría abrirse está muerta antes de empezar. Si en Ajustes
configuraste esa acción, llevas meses viendo un icono que no hace nada.

#### 2. El `+` del modo edición escribe en la base de datos pero no recarga la lista — **B3**

Este es el que más fácilmente se percibe como "no funciona". El flujo es
`PlaylistScreen.kt:736` → `addTrackToYouTubePlaylist` (escritura correcta en
`PlaylistLocalRepository.kt:319-347`), y al volver la pantalla hace:

```kotlin
searchResults = emptyList()
searchQuery = ""
```

(`PlaylistScreen.kt:750-751`). Desaparece el resultado de la búsqueda… y la
lista de la playlist **sigue igual**, porque el único sitio que repuebla
`trackEntities` y `playlistTracks` es el `LaunchedEffect` de
`PlaylistScreen.kt:146-158`, cuya clave es `selectedPlaylistEntity?.remoteId`
— un valor que **no cambia** al añadir una canción. No hay reconsulta de la DAO,
ni `trackEntities +=` optimista, ni nada. La canción se guardó, la pantalla no
lo sabe. Lo mismo ocurre al quitar con la `x` (`PlaylistScreen.kt:806-819`): el
borrado va a la base de datos y la fila permanece en pantalla.

Hay una ironía útil: el `LiveData` de `playlists` **sí** se refresca, así que
el contador de la rejilla de la izquierda sube. Verás "+1 canciones" con la
lista de dentro sin cambios. Eso es lo que hace el bug tan confuso.

#### 3. `addTrackToYouTubePlaylist` no deduplica — **B13**

`PlaylistLocalRepository.kt:331-346` construye el id como
`"${localPlaylistId}_${track.remoteTrackId}_$nextPosition"`, con
`nextPosition` calculado a incremented cada vez. **No hay comprobación de
duplicado** (al revés que `mergeLikedSongsTracks`, que sí busca el
`remoteTrackId` en `findByPlaylistId`). Añadir dos veces la misma canción
crea dos filas con distinto id y el mismo `youtubeVideoId`; con tres, tres
filas. Y como `toggleLikeTrack` (B1/B12) solo encuentra la primera coincidencia
con `findByPlaylistId(...).firstOrNull()`, al dar like solo cambia una de
ellas y las otras quedan como fantasmas que nunca se sincronizan.

#### 4. Al crear una playlist solo puedes añadir **una** canción por búsqueda — **B14**

En `CreatePlaylistScreen`, el botón custom de cada resultado hace
(`PlaylistScreen.kt:1250-1256`):

```kotlin
selectedTracks = selectedTracks + track
searchResults = emptyList()
searchQuery = ""
```

El contador sube, pero **la búsqueda se borra entera en cada pulsación**. Para
montar una playlist de 10 canciones tienes que hacer 10 búsquedas
independientes, escribiendo el nombre completo cada vez. No hay un "añadir
todo" ni accumulation: es un flujo diseñado para un solo elemento y colocado
donde se espera un selector múltiple. La UI dice "N seleccionadas" y el
usuario asume que puede seguir añadiendo.

#### 5. Al crear la playlist se pierden canciones **sin avisar** — **B15** y **B16**

`YouTubePlaylistCreator.build` descarta en silencio toda pista que no resuelva
un vídeo (`YouTubePlaylistCreator.kt:67`):

```kotlin
if (videoId.isNullOrBlank()) return@forEach
```

Lo grave es el feedback. `PlaylistScreen.kt:1344-1350` **calcula** el recuento
de lo perdido y lo **tira**:

```kotlin
ok to "${created.tracks.size} tracks (${selectedTracks.size - created.tracks.size} sin vídeo)"
```

y luego `if (saved) { onPlaylistCreated() }` — `message` solo se usa en el
`else`. Es decir: si de 12 canciones se resuelven 7, la app crea la playlist,
**navega hacia atrás sin decir nada**, y el usuario descubre 5 canciones
perdidas cuando vuelve a abrirla.

Y el caso peor: `build` llama a `resolveVideoId` (`:66`), que es una búsqueda
bloqueante de YouTube vía NewPipe, **una vez por pista y en serie**, sin
timeout y sin cancelación. `PlaylistScreen.kt:1323` la ejecuta en
`Dispatchers.IO`, pero el botón queda en `enabled = !isLoading` y no hay
"cancelar": si varias pistas no traen id de 11 caracteres (importadas de
Spotify, por ejemplo), crear la playlist se queda bloqueada durante minutos
sin salida. El usuario ve el spinner y asume que la app se ha colgado.

#### Lo que esto significa en conjunto

Las cinco rutas fallan, así que el síntoma depende de cuál uses:

| Ruta | Síntoma que ve el usuario |
|---|---|
| Swipe "añadir a playlist" (Ajustes) | Nada. Un icono `≡` inerte. |
| `<edit>` → buscar → `+` | La búsqueda se vacía y la canción no aparece. |
| `<edit>` → `x` para quitar | La fila no desaparece. |
| Crear playlist → `+` | Hay que repetir la búsqueda por cada canción. |
| Crear playlist → `<create>` | Faltan canciones, sin ningún aviso. |
| `<guardar>` en detalle de playlist de YouTube | **Este sí funciona**: cambia a `<saved>` (`:176-180`). Es la única ruta con feedback. |

**La buena noticia:** la escritura en la base de datos es correcta en las cinco
rutas. Los datos sí se guardan. Es la capa de UI la que miente — no refresca,
no avisa, y en dos casos directamente no ejecuta nada. Eso significa que la
mayoría de estas Playlist se arreglan con cambios de estado y de refresco, sin
tocar el esquema ni la lógica de persistencia. Excepción: B13 necesita un
índice único o una comprobación de duplicado, y B15/B16 necesitan que
`YouTubePlaylistCreator.build` pase a ser `suspend` con cancelación y reporte
de descartes.

### 1.4 Estado de la deuda

- **0 bugs críticos**, **11 altos**, **25 medios**, **11 bajos** (§3).
- **290 tests unitarios** en 21 archivos (recuento estático de `@Test`).
- **0 instrumentados** útiles (solo `ExampleInstrumentedTest`).
- **~700 líneas muertas** entre `SongMenuDialog`, `CollapsibleSection`,
  `PlyrDimensions` y funciones sin uso (§7).
- **16 claves de traducción sin uso** y **1 clave referenciada que no existe**.
- Sin R8/ofuscación en release.

---

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 71 (~14.757 líneas) |
| Archivos de test | 21 (~3.177 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1363), `ConfigScreen.kt` (919), `PlayerViewModel.kt` (713), `Translations.kt` (617), `FloatingMusicControls.kt` (536), `SearchScreen.kt` (477), `SongListItem.kt` (459), `QRDialog.kt` (449) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7, migraciones `5→6` y `6→7` |
| Tests unitarios (recuento estático `@Test`) | **290** en 21 archivos |
| Tests instrumentados útiles | 0 |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso | **16 de 101** (verificadas por barrido) |
| Claves referenciadas que no existen | **1** (`"Player not available"`) |

**Desglose de los 290 tests** (recuento estático, sin ejecutar):

| Archivo | @Test | Archivo | @Test |
|---|---|---|---|
| `UrlParserTest` | 33 | `ExportDigestTest` | 11 |
| `UtilsTest` | 30 | `MediaButtonCommandTest` | 9 |
| `ImportManifestTest` | 24 | `AppModelsTest` | 8 |
| `CoverCropMathTest` | 24 | `YouTubeFormattingTest` | 8 |
| `SpotifyImporterTest` | 23 | `ModelDefaultsTest` | 7 |
| `ExportManifestTest` | 22 | `SupabaseClientTest` | 7 |
| `QueueIndexTest` | 22 | `CoverCacheTest` | 6 |
| `YouTubePlaylistCreatorTest` | 15 | `BackupFolderTest` | 5 |
| `ImportArchiveTest` | 14 | `TranslationsTest` | 5 |
| `DatabaseMappingsTest` | 13 | `NewPipeHolderTest` | 3 |
| | | `ExampleUnitTest` | 1 |

---

## 3. BUGS ACTIVOS

### 3.1 Críticos

Ninguno. La reproducción tiene errores reales (§3.2) pero todos degradan o se
recuperan solos; ninguno corrompe datos de forma irreversible salvo B1 (que
sí toca la tabla de favoritos, y por eso está en altos).

### 3.2 Altos

| # | Ubicación | Descripción |
|---|---|---|
| **B1** | `PlaylistScreen.kt:719,793,853` → `SongListItem.kt:425` → `PlaylistLocalRepository.kt:61-63` | **El swipe a "liked" borra la canción que se había marcado antes.** `PlaylistScreen` no pasa `youtubeId`, así que `executeSwipeAction` manda `""` a `toggleLikeTrack`, que compara por igualdad exacta. La 1.ª llamada guarda una pista con `youtubeVideoId=""`; la 2.ª, sobre otra canción, la encuentra y la **borra**. `isTrackLiked("")` sale `true` para todo. Detalle completo en §1.1. |
| **B2** | `SongListItem.kt:446-448` + `:165,:182` + `ConfigScreen.kt:266,274,296,304` | **La acción de swipe "añadir a playlist" es un no-op.** Es una opción seleccionable en Ajustes para ambos lados y dibuja el icono `≡` (`SongListItem.kt:56`), pero la implementación solo hace `Log.d(...)`. El parámetro `onShowPlaylistDialog` está cableado a `{}` en las dos ramas del gesto, así que no existe ninguna vía de diálogo. Detalle en §1.3. |
| **B3** | `PlaylistScreen.kt:736-752` y `:806-819` → `LaunchedEffect:146-158` | **Añadir y quitar en modo edición no refrescan la lista.** `trackEntities` y `playlistTracks` solo se repueblan en un `LaunchedEffect` cuya clave es `selectedPlaylistEntity?.remoteId`, que no cambia al añadir o quitar. La escritura va a la base de datos (correcta) pero la UI limpia la búsqueda y no muestra el cambio; el `LiveData` de `playlists` sí refresca, así que el contador de la rejilla se mueve y la lista de dentro no. Detalle en §1.3. |
| **B4** | `PlayerViewModel.kt:589-592`, `:660`, `:668` | **La invalidación de URL caducada es un no-op.** `currentVideoId` se rellena con `queue[i].youtubeVideoId` (null para toda pista resuelta por búsqueda), así que `YouTubeManager.invalidate()` casi nunca se llama y el re-intento recibe de la caché **la misma URL muerta**. Detalle en §1.2. |
| **B5** | `YouTubeManager.kt:68-80` + `PlayerViewModel.kt:163,366,403,479-481` | **Doble extracción por transición.** `getAudioUrl` no deduplica en vuelo y `growWindow()` se invoca dos veces por salto, con `prefetchJob?.cancel()` + relanzamiento. La extracción cancelada (OkHttp sobre `Dispatchers.IO`, no interrumpible) no llega a escribir caché y la relanzada repite el trabajo. Es la "carga doble" que se oye. |
| **B6** | `PlayerViewModel.kt:611`, `:390` | **`_error` nunca se limpia en la ruta de error.** `_error.publish(null)` solo existe en `startAt:337` y `clearPlayerState:290`. Tras un `onPlayerError` recuperable, el estado de error se queda pegado para siempre: los controles quedan deshabilitados y el texto de error no desaparece aunque la canción vuelva a sonar. |
| **B7** | `SongListItem.kt:148` | **`pointerInput(Unit)` con estado capturado.** El bloque de drag fija `song`, `index` y `trackEntities` en la primera composición y no se reinicia nunca. Afecta a `QueueScreen:69`, `YouTubePlaylistDetailView:231`, `YouTubeSearchResults:221` y `SearchScreen:451`: tras reordenar o añadir, el swipe y el clic actúan sobre la pista del primer frame. |
| **B8** | `SongListItem.kt:194-199` y `:150-153` | **`Animatable` + `launch` por evento de drag.** Cada `onHorizontalDrag` lanza una corrutina con `snapTo`; `MutatorMutex` hace que se cancelen entre sí y el offset acumulado se pierde. `onDragEnd` lee `offsetX.value` dentro de otra corrutina, con el `snapTo` pendiente en vuelo, así que el umbral se evalúa sobre un valor provisional. Resultado: el gesto se "come" desplazamientos y a veces no supera el umbral. |
| **B9** | `YouTubePlaylistDetailView.kt:138-142` | **`<rnd>` desincroniza la UI de la cola.** Se llama `setCurrentPlaylist(shuffled, 0)` pero `trackEntities` (`:44`) no se sustituye, así que `isPlaying` (`:229-230`) y **todas** las acciones por índice que pasan `trackEntities` + `index` a `SongListItem` apuntan a la posición de la lista sin barajar, no a la que suena. |
| **B10** | `PlayerViewModel.kt:713` | `private const val MAX_CAUSE_DEPTH = 5` a nivel de fichero, **duplicado** del companion (`:62`) y sin usar. Sobrescrito silenciosamente por el del companion; código muerto que además confunde cualquier lectura futura. |
| **B11** | `QrScannerDialog.kt:88`, `:99` | **Fuga de cámara y de hilo por cada apertura del escáner.** `Executors.newSingleThreadExecutor()` se crea en línea y nunca se hace `shutdown()`; el `unbindAll()` de la línea 99 es limpieza previa a `bindToLifecycle`, **no teardown**: no hay `DisposableEffect` ni `onDispose` en el archivo, así que la cámara sigue ligada a la Activity y el analizador sigue corriendo con el diálogo cerrado. Encima, `onQrScanned()` y `onDismiss()` se invocan desde el **hilo del analizador** (`:92-93`), lo que hace que `SearchScreen` escriba estado de Compose desde él, y no hay guarda de reentrada: cada frame posterior vuelve a disparar el callback. |

### 3.3 Medios

| # | Ubicación | Descripción |
|---|---|---|
| **B12** | `PlaylistLocalRepository.kt:56-99` | `toggleLikeTrack` no es atómico: lee la lista entera, calcula `maxOf { position } + 1`, inserta y reescribe el `trackCount` de la playlist en pasos separados sin transacción. Dos swipes seguidos (o un swipe simultáneo con una importación) pueden dejar `position` duplicada y `trackCount` desincronizado. Además el id `"${LIKED_SONGS_ID}_${remoteTrackId}_$nextPosition"` colisiona con `OnConflictStrategy.REPLACE` (`TrackDao.kt:57`) cuando `remoteTrackId` es `""`, que es justo el caso de B1. |
| **B13** | `PlaylistLocalRepository.kt:319-347` | **`addTrackToYouTubePlaylist` no deduplica.** El id se construye con `$nextPosition` incrementado, así que añadir la misma canción N veces crea N filas con distinto id y el mismo `youtubeVideoId`. No hay comprobación previa, al contrario que `mergeLikedSongsTracks:230-240`, que sí busca por `remoteTrackId`. Consecuencia: `toggleLikeTrack` (B1/B12) solo afecta a la primera coincidencia y las demás quedan como filas fantasma que nunca se sincronizan. Detalle en §1.3. |
| **B14** | `PlaylistScreen.kt:1250-1256` | **Al crear una playlist solo se puede añadir una canción por búsqueda.** Cada pulsación del botón custom hace `selectedTracks + track` y a continuación `searchResults = emptyList(); searchQuery = ""`, borrando la búsqueda entera. El contador de seleccionadas sube, pero para montar una lista de 10 canciones hay que hacer 10 búsquedas independientes. No existe "añadir todo" ni acumulación de resultados. Detalle en §1.3. |
| **B15** | `YouTubePlaylistCreator.kt:67` + `PlaylistScreen.kt:1344-1350` | **La creación de playlist descarta canciones sin avisar.** `build` hace `if (videoId.isNullOrBlank()) return@forEach`, y el recuento de lo perdido se **calcula y se tira**: `"${created.tracks.size} tracks (… sin vídeo)"` solo se muestra en la rama `else` de fallo. Si 7 de 12 canciones resuelven vídeo, la app crea la playlist, navega atrás sin decir nada, y el usuario descubre las 5 perdidas al reabrirla. Detalle en §1.3. |
| **B16** | `YouTubePlaylistCreator.kt:66` + `PlaylistScreen.kt:1323-1344` | **La creación de playlist puede colgarse durante minutos sin salida.** Por cada pista sin `youtubeVideoId` y con id de longitud distinta de 11 (p. ej. importadas de Spotify) se lanza una búsqueda bloqueante de YouTube vía NewPipe, **en serie**, sin timeout y sin cancelación. El botón queda en `enabled = !isLoading` y no hay "cancelar", así que el usuario ve el spinner y asume cuelgue. Arreglarlo exige que `build` pase a `suspend` con `ensureActive()`. |
| **B17** | `ConfigScreen.kt:74-77` y `:119` | `onThemeChanged` se llama **dos veces** por cambio de tema: una en el `LaunchedEffect(selectedTheme)` y otra en el `onSelected` del `MultiToggle`. En `MainActivity:152-160` eso duplica `lightSensorDetector.start()/stop()`. |
| **B18** | `SearchScreen.kt:152-157` y `:222-227` | `ScanResult.type` no se inspecciona nunca: una **playlist** escaneada se construye como URL de video (`watch?v=<id-de-playlist>`). `FeedScreen.kt:150` emite `ScanResult("youtube", "playlist", playlistId)` justo para este camino. El `else` de la línea 160 convierte el error en "unsupported source" en lugar de abrir la playlist. |
| **B19** | `QRDialog.kt:164-166` | `DisposableEffect(lifecycleOwner, nfcState)` lee `nfcAdapter`, que se asigna en `LaunchedEffect(Unit)` (`:159-161`). En la primera composición vale `null`, y al asignarse después el efecto **no se reinicia** porque no es clave → `enableForegroundDispatch` nunca se llama y la **escritura de tags NFC no arranca nunca**. El `onDispose` (`:180-189`) sí llega a llamar `disableForegroundDispatch` sin haberlo activado nunca. |
| **B20** | `QRDialog.kt:223` y `:363-373` | `generateQrBitmap(shareUrl)` se llama **sin `remember`**, en el cuerpo de composición: decodifica y pinta el QR entero (512×512) en el hilo principal en cada recomposición. Y `NfcButton` muta `rings`/`frameCounter` cada 200 ms mientras está en `WAITING`, lo que fuerza esa recomposición 5 veces por segundo mientras se escribe un tag. |
| **B21** | `Translations.kt:408-409` | El mapa **`català` tiene valores japoneses** en `plyr_queue` (`"plyr_キュー"`) y en `No tracks loaded` (`"曲が読み込まれていません"`): copiado del bloque `日本語` de las líneas 451+. Un usuario en catalán ve japonés en la pantalla de cola. |
| **B22** | `Translations.kt:35/47, 215/227, 360/372, 510/522, 304/377, 454/527, 320/440` | Claves duplicadas dentro de un mismo `mapOf`: `loading` en los **cuatro** idiomas, `user_nickname` en `català` y `日本語`, `share_me` en `català`. Gana la última ocurrencia; el resto es basura que sugiere dos valores distintos para lo mismo. |
| **B23** | `ConfigScreen.kt:414/423, 462/471, 525/562, 655, 693` | Exportar, importar y copiar/restaurar se lanzan en `rememberCoroutineScope()`. La pantalla de config vive en un pager (un swipe basta para salir), y `onCleared` **cancela** esas corrutinas a mitad de la escritura → ZIP truncado o base de datos restaurada a medias sin aviso. Necesitan un scope de aplicación. |
| **B24** | `CoverCropDialog.kt:79` | `text = if (loadError != null) "$ load_error" else "$ loading..."`. En Kotlin `"$ "` no abre plantilla, así que el usuario ve literalmente la cadena `$ load_error` cuando falla la carga de la imagen. |
| **B25** | `SupabaseClient.kt:308-334` | `parseTimestamp`: 4 objetos `SimpleDateFormat` por fila; el 2º patrón tiene `'Z'` duplicado (`"…ss.SSS'Z'"`); el 1º (`SSSSSS`) acepta en realidad 1..N dígitos y `format.parse(String)` ignora el texto sobrante, así que un `+HH:MM` real se descarta en silencio; y se fuerza `timeZone = UTC` (`:322`). **Todo fallo devuelve `System.currentTimeMillis()`** (`:311`, `:330`, `:333`): una fecha ilegible se disfraza de "ahora". El `catch` externo (`:331`) es inalcanzable porque el interno (`:325`) se traga todo. Como se usa para `createdAt`/`joinedAt` de grupos y recomendaciones, las sombras de "nuevo". |
| **B26** | `YouTubeSearchManager.kt:376-378` (y llamada en `:248`) | `getPlaylistThumbnailUrl()` devuelve hardcodeado `https://img.youtube.com/vi/undefined/hqdefault.jpg`, y se invoca **sin argumentos** teniendo el `playlistId` real a mano en esa misma línea. Toda playlist sin portada muestra el mismo placeholder. |
| **B27** | `YouTubeSearchManager.kt:168-175`, `:184` | `getFormattedVideoCount`: el `else` final es inalcanzable (`== 1`, `< 1000` y `>= 1000` ya particionan todos los `Int`); `"%.${digits}f".format(this)` sin `Locale` → `1,5K` en `es-ES`/`ca-ES`; plurales ingleses fijos y sin sufijo `M`/`B`, así que 1 000 000 de vídeos sale como `1000K videos`. |
| **B28** | `DatabaseExtensions.kt:17-24` (`AppModels.kt:7,26`) | `toAppTrack()` no puede transportar `youtubeVideoId`, `position` ni `durationMs` porque `AppTrack` no tiene esos campos. Toda ida y vuelta `AppTrack → TrackEntity` (usada en `PlaylistScreen.kt:152` y `:178`) **pierde el id de YouTube** de la pista. Es el mecanismo que hace que B1 sea irrecuperable una vez guardado. |
| **B29** | `MediaMetadataExtractor.kt:26`, `:34`, `:42-57` | El parámetro `context: Context?` no se usa; `isYouTubeUrl` es sensible a mayúsculas (`HTTPS://WWW.YOUTUBE.COM/…` → `UNKNOWN` con `title = url`); y las heurísticas `v=PL` / `v=UU` / `v=FL` / `v=RD` clasifican como playlist un vídeo normal cuyo id empiece por esos prefijos, con lo que el vídeo no llega a sonar. |
| **B30** | `NfcReader.kt:82` | `enableForegroundDispatch` es la **única** llamada NFC sin `try/catch` (lanza `IllegalStateException` si la Activity no está resumed) y se invoca desde `QRDialog.kt:188` en un `onDispose`, que puede correr con la Activity parando. El resto de llamadas del archivo sí van protegidas. |
| **B31** | `LightSensorDetector.kt:75`, `:46`, `:59` | `event.values[0]` sin comprobar el tamaño del array → `ArrayIndexOutOfBoundsException` en el hilo principal; `getSystemService(...) as SensorManager` sin guardia; y `isListening = true` se pone aunque `registerListener` haya devuelto `false`, dejando `start()` como no-op permanente hasta que se llame a `stop()`. |
| **B32** | `SimpleDownloader.kt:31`, `:49-59`, `:61` | El mapa de cookies es un `mutableMapOf` plano: `setCookie` muta desde el hilo que lo inicializa y `getCookies` lo lee desde **todos** los hilos de red, sin sincronizar. Además se loguean cookies (`:101`) y cabeceras completas (`:113-117`), que pueden incluir `Authorization`. |
| **B33** | `SupabaseClient.kt:59,109,179,228,291` | `JSONObject.optString(key, null)` devuelve la **cadena** `"null"` cuando el valor JSON es `null`, no `null`. Un `comment` o `invite_code` nulo se guarda y se muestra como la palabra "null". Además `connection.inputStream` lanza `FileNotFoundException` en cualquier 4xx (`:136`, `:168`, `:279`) y el cuerpo de error nunca se lee → el usuario ve `"error: null"`. Y `invite_code` se interpola sin URL-encoding en el filtro PostgREST (`:130`): un código con `&`, `#` o `,` cambia la semántica de la consulta. |
| **B34** | `FloatingMusicControls.kt:330-348` | El `pointerInput(Unit)` de la barra de progreso captura `duration` del primer frame en que se compone la rama `else`. Al cambiar de canción el `seekTo((duration * dragProgress).toLong())` usa la **duración de la canción anterior**. |
| **B35** | `FloatingMusicControls.kt:145` | `LaunchedEffect(playerViewModel.exoPlayer)` usa como clave un `var` normal, no observable. Hoy el bucle de polling arranca porque otras `LiveData` fuerzan recomposición por el camino; si algún día el título deja de cambiar (p. ej. misma canción repetida) el poll no arranca nunca. Frágil por construcción. |
| **B36** | `PlayerViewModel.kt:390-456` vs `:324-380` | En la ruta de `playIndex` no se pone `resolving = true` (solo en `startAt:335`), así que `isLoading` no refleja la re-resolución de URLs: el spinner no aparece y los controles se reactivan mientras se está volviendo a resolver. Asimetría entre las dos rutas de carga. |

### 3.4 Bajos

| # | Ubicación | Descripción |
|---|---|---|
| **B37** | `Utils.kt:44-50` | `isValidAudioUrl` es código muerto: `return hasAudioPattern \|\| isValidUrlFormat(url)`, y `isValidUrlFormat(url)` ya salió `true` del early-return, así que **toda URL http(s) pasa**. Verificado: **cero** llamadas desde producción, solo desde `UtilsTest` — y `UtilsTest.kt:60-62` fija precisamente el comportamiento incorrecto, de modo que un test impide arreglarlo. Decidir: arreglar + corregir el test, o borrar la función y sus 13 tests. |
| **B38** | `AndroidManifest.xml:15` | `WAKE_LOCK` declarado y **sin uso**: no hay `PowerManager.WakeLock` ni `setWakeMode` en todo el source (verificado). La reproducción con la pantalla apagada la sostiene el `foregroundServiceType="mediaPlayback"` + `USAGE_MEDIA`, no el permiso. |
| **B39** | `AndroidManifest.xml:54-60` | `MediaButtonReceiver` es `exported="true"` con intent filter y **sin permiso**: cualquier app del dispositivo puede inyectar `ACTION_MEDIA_BUTTON` y manejar la reproducción. El impacto se limita al transporte (la clase valida acción y keycode), pero no necesita estar exportada. |
| **B40** | `AndroidManifest.xml:22-24` + `res/xml/data_extraction_rules.xml` | `allowBackup="true"` y las reglas incluyen `sharedpref/plyr_config.xml` en **cloud-backup**. Ese fichero guarda el nickname del usuario en el feed, el Uri del árbol SAF y el hash de la copia (`Config.kt:35-41`). El Uri del árbol es un *capability handle* ligado al dispositivo: no debería subirse a la nube. |
| **B41** | `app/build.gradle.kts:45` | `isMinifyEnabled = false` en release → los `proguardFiles` de `:49-52` son inertes y **todos** los `Log.d/e` de B32 y B33 llegan tal cual al APK publicado. |
| **B42** | `MusicService.kt:41-47` | `onStartCommand` ignora `intent?.action` por completo. Hoy nadie le manda intents (los botones de media van por `MediaButtonReceiver` → `PlayerViewModel`), pero sigue siendo una trampa para quien intente controlar la reproducción por servicio. |
| **B43** | `MusicService.kt:94` | `MediaStyleNotificationHelper.MediaStyle(mediaSession!!)` — aserción no nula sin guardia en un callback del servicio. |
| **B44** | `SearchScreen.kt:58-66`, `:56` | Bucle infinito de `delay(100)` leyendo `SharedPreferences` mientras la pantalla está viva: 10 lecturas de disco por segundo, para siempre. Y `currentLanguage` es *write-only*: se asigna en la línea 63 y no se lee en ningún sitio. |
| **B45** | `NewPipeHolder.kt:21` | `Localization("es", "ES")` hardcodeado: todos los mensajes de error que produce el extractor de NewPipe salen en español, pase lo que pase el idioma configurado en la app. |
| **B46** | `MainActivity.kt:186-192` | `unbindService(serviceConnection)` en `onDestroy` pone `onMediaSessionUpdate = null` (`MainActivity:65`) mientras el `MediaSession` del servicio sigue vivo. En una rotación hay una ventana en la que la notificación muestra la pista anterior hasta que `onServiceConnected` vuelve a engancharse. |
| **B47** | `QrScannerDialog.kt:61-63` | Si se deniega el permiso de cámara, el diálogo se renderiza como un `Box` **vacío**: sin mensaje y sin botón. `LaunchedEffect(Unit)` solo corre una vez, así que el usuario se queda con un modal mudo del que solo sale con "atrás". |

---

## 4. SEGURIDAD

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S3 | Alta | `app/build.gradle.kts:45` | Sin R8 ni ofuscación en release (ver B41). |
| S6 | Media | `SimpleDownloader.kt:101`, `:113-117` | Loguea **cookies** (incluida la de reCAPTCHA) y **cabeceras completas** de request/response; pueden incluir `Authorization`. |
| S7 | Media | `SupabaseClient.kt` (≈42 `Log.*`) | Vuelca cuerpos completos de requests/responses: nicknames, códigos de invitación, nombres de grupo, URLs y comentarios de recomendaciones. PII en logcat. |
| S9 | Baja | `SupabaseClient.kt:19-20` | URL y anon key en el código. La key es `sb_publishable_…` (publishable por diseño de DCL), así que no es un secreto, pero **las políticas RLS de `groups`, `group_members`, `recommendations` y `automatic` no son verificables desde aquí** y son la única defensa de esos datos. |
| S10 | Baja | `SimpleDownloader.kt:19` | Cookie de YouTube hardcodeada (`PREF=f2=8000000`): caduca en servidor sin aviso y el "bypass" es en consecuencia poco fiable. |
| S11 | Baja | `AndroidManifest.xml:54-60` | Receiver exportado sin permiso (ver B39). |
| S12 | Baja | `AndroidManifest.xml:22-24` + `data_extraction_rules.xml` | La copia de seguridad se lleva el Uri del árbol SAF a la nube (ver B40). |

**Nada que resolver aquí.** Las piezas que quedaban de Spotify
(`plyr://spotify` BROWSABLE, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, token en
prefs, `client_secret` de OAuth) ya no están en el manifiesto ni en el código.

---

## 5. RENDIMIENTO Y CONCURRENCIA

| Severidad | Ubicación | Descripción |
|---|---|---|
| Alta | `QrScannerDialog.kt:88,99` | Executor sin `shutdown()` y cámara sin `unbindAll()` en `onDispose` (ver B11). |
| Alta | `QRDialog.kt:223`, `:363-373` | QR de 512×512 regenerado sin `remember` en el hilo principal, con recomposición forzada 5×/s (ver B20). |
| Media | `ConfigScreen.kt:414-693` | Exportación/importación/copia en scope de composición, cancelable (ver B23). |
| Media | `FeedScreen.kt:47`, `:65` | `metadataCache = metadataCache + (...)` reconstruye el mapa entero en cada insert (**O(n²)**) y es un read-modify-write no atómico ejecutado desde N corrutinas concurrentes (`:63`). Además esos `scope.launch` cuelgan de `rememberCoroutineScope()`, no del `LaunchedEffect` que los lanza: sobreviven a la cancelación del efecto y escriben estado cuando ya no aplica. |
| Media | `FeedScreen.kt:74-107` | `Column` + `verticalScroll` + `forEach`: se componen todas las recomendaciones a la vez, y sin límite de concurrencia (una extracción de red por fila, `:107`). |
| Media | `YouTubeSearchResults.kt:217-218` | `Column` + `forEachIndexed` en vez de `LazyColumn`: todo el set de resultados se compone de golpe, y cada `SongListItem` lanza una consulta a la DB en su `LaunchedEffect` (`SongListItem.kt:285-290`). |
| Media | `ConfigScreen.kt:184-188` | `packageManager.getPackageInfoCompat(...)` se ejecuta **dentro de la composición**: `PackageManager` + reflexión en cada recomposición. |
| Media | `Theme.kt:136-140` | `unifiedTypography()` es `@Composable` y hace `return Typography(...)` **sin `remember`**: se reconstruyen la `Typography` y 14 `TextStyle` en cada recomposición, invalidando todos los nodos de texto de la app. |
| Baja | `PlaylistScreen.kt:715,789,851,1006` y `YouTubePlaylistDetailView.kt:220` | **5 `items(...)` de listas perezosas sin `key`**, 4 de ellas en el monolito de `PlaylistScreen` (0 ocurrencias de `key =` en el archivo): al reordenar o añadir, cada item se recrea y pierde su estado y su animación. Las listas de `HomeScreen` y `YouTubeSearchResults` sí van keyadas; la de `QueueScreen` va keyada pero con una clave inestable (siguiente fila). |
| Baja | `QueueScreen.kt:53-54`, `:71` | `key = { index -> "${currentPlaylist!![index].id}_$index" }` **no es estable** (incorpora la posición: cualquier reordenación cambia todas las claves y destruye el estado de los items) y `currentPlaylist!!` se re-deriva tres veces, dos de ellas dentro de la lambda de composición, con la posibilidad de desborde si la lista cambia entremedias. |
| Baja | `SearchScreen.kt:425-437` | `trackEntities` se reconstruye **en cada recomposición** (no está en `remember`) y con `lastSyncTime = System.currentTimeMillis()`, así que las identidades de objeto son siempre nuevas y los `SongListItem` de debajo recomponen sin parar. |
| Baja | `CoverCropDialog.kt:235-236` | `crop()` + `resizeToSquare()` se ejecutan **de forma síncrona en el hilo principal** dentro del manejador del clic: decodifica, escala y recorta un bitmap a tamaño completo en la UI. Además `:113-115` escribe estado de snapshot **durante la composición**, y `:140` usa esas medidas como claves del `pointerInput`, con lo que el gesto se reinicia en cada recomposición. |
| Baja | `SongListItem.kt:105`, `:224` | `.height(32.dp)` fijo en la `Box` y en la `Row` que contienen un `Column` de **dos** líneas (título + artista): el artista se recorta en todas las filas de todas las listas. |
| Baja | `SongListItem.kt:82-83` | `Config.getSwipeRightAction(context)` / `getSwipeLeftAction(context)` leen `SharedPreferences` en cada recomposición de cada item, no solo cuando cambian. |

---

## 6. ARQUITECTURA

| Severidad | Ubicación | Descripción |
|---|---|---|
| Media | `viewmodel/PlayerViewModel.kt` (713) | Monolito con estado mutable repartido entre el hilo principal y las corrutinas. `generation` + `windowStart` + `transitionInFlight` son 3 banderas que hay que mantener coherentes a mano; B4 y B6 son consecuencia directa de que la invalidación de caché y el estado de error se gestionen en un sitio y no en otro. La lógica pura ya está aislada en `QueueIndex`, pero el estado de la ventana no. |
| Media | `ui/PlaylistScreen.kt` (1363) | Mezcla UI, red (Supabase/YouTube), DB y lógica de negocio; además construye el modelo de UI (`Song`) sin el campo que la propia UI necesita (B1), lo que es exactamente el tipo de error que un ViewModel por pantalla habría hecho imposible. |
| Media | `service/MusicService.kt` | No es dueño del reproductor: solo proyecta la notificación sobre el `ExoPlayer` que vive en el `PlayerViewModel` de `PlyrApp`. **No registra ningún `MediaSession.Callback`**, así que `seekToNext`/`seekToPrevious` desde la notificación o el lockscreen los mueve ExoPlayer directamente, no `QueueIndex`; el índice se reconcilia después por la aritmética de `syncIndexFromWindow`. Funciona por casualidad, no por diseño. |
| Media | `ui/ConfigScreen.kt` (919), `ui/SearchScreen.kt` (477) | Composables con carga, red y estado en `remember`/`rememberCoroutineScope`. |
| Baja | `ui/components/SongListItem.kt` (459) | `pointerInput(Unit)` sin claves y `Animatable` mutado desde corrutinas lanzadas a mano: el componente no se puede reutilizar en ninguna lista que cambie sin romper el gesto (B7, B8). |

**Nota positiva:** el patrón de **extraer lógica pura testeable** está
consolidado y bien aplicado en `QueueIndex`, `MediaButtonCommand`,
`CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`,
`ExportDigest` y `UrlParser`. Son 8 módulos con 159 tests y cero dependencias de
Android. Es el asset de calidad más valioso del repo y el modelo a seguir.

---

## 7. LIMPIEZA / CÓDIGO MUERTO

**Archivos enteros sin ninguna llamada** (verificado con barrido de `app/src`):

| Archivo | Líneas | Nota |
|---|---|---|
| `ui/components/SongMenuDialog.kt` | 171 | Solo aparece un `import` sin usar en `FloatingMusicControls.kt:29`. Duplica el popup de like que sí vive dentro de `SongListItem`. |
| `ui/components/CollapsibleSection.kt` | 88 | Cero referencias. |

**Símbolos y miembros sin uso en producción:**

| Símbolo | Ubicación | Nota |
|---|---|---|
| `PlyrDimensions` (objeto completo) | `Theme.kt:53-71` | 19 constantes (`floatingControlsHeight`, `buttonHeight`, `listItemHeight`, …) sin una sola referencia. |
| `QueueIndex.needsRefillAfterEnd` | `QueueIndex.kt:83-91` | Probado (4 tests) pero **nunca llamado** por `PlayerViewModel`: `growWindow()` se encarga por su cuenta. Test que verifica código muerto. |
| `YouTubeManager.clearCache` | `YouTubeManager.kt:102-109` | Cero llamadas. La caché solo se invalida por `videoId`. |
| `isValidAudioUrl` + `containsAudioPattern` | `Utils.kt:44-50` | Código muerto en producción (B37), con 13 tests que consolidation el bug. |
| `val loadPlaylists = { }` | `PlaylistScreen.kt:160` | No-op asignado y nunca invocado. |
| `MAX_CAUSE_DEPTH` a nivel de fichero | `PlayerViewModel.kt:713` | Duplica el del companion (B10). |
| `TerminalColorsPreview`, `PreviewTerminalThemeDark/Light` | `Theme.kt:266,314` | Previews de Android Studio, inalcanzables en runtime. |
| `ResponsiveDimensions`: `titleSize`, `iconSize*`, `buttonHeight`, `buttonMinWidth` | `ResponsiveUtils.kt:31-51` | Calculados en cada llamada y nunca leídos. |
| `PlyrSymbols.COMMAND/SEPARATOR/BULLET/ARROW/BACK` | `Theme.kt:74-82` | Sin referencias. |
| `MediaMetadataExtractor.extractMetadata(context = …)` | `MediaMetadataExtractor.kt:26` | Parámetro muerto en los dos call sites. |
| `SongListItem.onShowPlaylistDialog` | `SongListItem.kt:417` | Ambos callers pasan `{}`. |
| `MediaCommand` sin `STOP` | `MediaButtonCommand.kt` | `KEYCODE_MEDIA_STOP` se mapea a `PAUSE` y el `NONE` de `execute` (`:77`) es rama inalcanzable. |
| `CollapsibleSection.statusColor` | `CollapsibleSection.kt:32` | Parámetro con default que llama a `MaterialTheme.colorScheme` en el argumento por defecto. |
| `ActionButtonData.enabled` | `ActionBttn.kt:25` | Ningún `ActionButtonData(...)` del source pone `enabled = false`; el render de deshabilitado (`:54`, `:65`) es inalcanzable. |

### 7.1 Traducciones

**16 de las 101 claves del mapa `español` no se referencian en ningún sitio**
(verificado excluyendo el propio `Translations.kt` de la búsqueda):

`artist_image`, `backup_folder_none`, `colored by used engine`, `enter_nickname`,
`gestures_section`, `home_new_playlist`, `home_queue`, `home_settings`, `info`,
`info_text`, `lastfm_api_key`, `next`, `no_results`, `not_configured`,
`player_not_available`, `previous`.

Restos de features eliminadas (Last.fm, gestos, "home" anterior) más strings que
quedaron sin uso. **`TranslationsTest` no puede detectar esta clase de problema**
porque valida que la clave exista y sea coherente entre idiomas, no que se use.

**1 clave referenciada que no existe en ningún idioma:**
`Translations.get(context, "Player not available")` en `QueueScreen.kt:103` — el
texto en inglés se pasa como clave, así que `Translations.get` devuelve la propia
clave y la cadena sale **sin traducir en los 4 idiomas**. La clave correcta,
`player_not_available`, existe en los 4 mapas y no se usa nunca. El mismo patrón
se repite en `QueueScreen.kt:87` con `"No tracks loaded"`, que sí existe pero
como clave (funciona por casualidad y es inmantenible).

**Literales de interfaz fuera de `Translations`** (muestra; hay más):
`QueueScreen.kt:63` `"Unknown Artist"` · `SongListItem.kt:328` `"♥ liked"/"♡ like"` ·
`PlyrComponents.kt:45` `text = "loading"` · `YouTubeSearchResults.kt:78,97,137,247` ·
`YouTubePlaylistDetailView.kt:85,180,210,212` · `ConfigScreen.kt:197,199,387` ·
`PlaylistScreen.kt:479,489,516,532,884,892,919,941` ·
`FloatingMusicControls.kt:241,252` · `CoverCropDialog.kt:79,103,219,224` ·
`QrScannerDialog.kt:107` (español) · `QRDialog.kt:243,270` (español) ·
`SpotifyImporter.kt:71,90,112,166,176` · `YouTubeSearchManager.kt:170-172,231,246,300`
(español) · `SearchScreen.kt:302`.

Dos casos concretos que se ven sin traducir hoy:
- `QRDialog.kt:257` usa `Translations.get(context, "btn_share")` correctamente, y
  **tres líneas después** (`:270`) pone `Intent.createChooser(sendIntent, "Compartir via")`
  en español fijo.
- `CoverCropDialog.kt:79` renderiza el literal `$ load_error` (§B24).

---

## 8. TESTS

- **290 tests unitarios en 21 archivos** (recuento estático de anotaciones
  `@Test`). **No se han ejecutado** en esta revisión, así que no hay ninguna
  afirmación sobre si pasan.
- 0 tests instrumentados útiles: solo `ExampleInstrumentedTest`.

**Gaps relevantes, en orden de daño que hacen:**

1. **`PlayerViewModel` no tiene ningún test.** Es donde están B4, B5, B6 y B36,
   es decir los 4 bugs de reproducción de la lista. `QueueIndex` sí está
   cubierta (22 tests) y es correcta; lo que no está cubierta es la
   *orquestación*: `generation`, `windowStart`, `transitionInFlight`,
   `currentVideoId` y la derivación de `isLoading`/`error`. La lógica que
    decide si una URL caducada se invalida, cuándo se limpia `_error` y si hay que
   recargar la ventana es exactamente la que no se puede ejercitar sin Android.
2. **`SongListItem` no tiene ningún test**, y su lógica de swipe (umbral,
   dirección, acción) está embebida en lambdas de `pointerInput`. B1, B7 y B8
   son inaccesibles a un test JVM tal como está el código. Extraer la decisión
   "offset → acción" a una función pura (como se hizo con `QueueIndex` y
   `MediaButtonCommand`) es el primer paso para poder probarlo.
3. **13 tests de `UtilsTest` consolidan el bug de `isValidAudioUrl`** en vez de
   detectarlo (B37): `isValidAudioUrl_acceptsAnyHttpUrl` afirma que
   `https://example.com/plain-video` es válida, que es justo lo que no debería ser.
4. **Los módulos más valiosos del repo están bien cubiertos** (`CoverCropMath` 24,
   `ImportManifest` 24, `ExportManifest` 22, `ImportArchive` 14, `ExportDigest` 11,
   `QueueIndex` 22, `MediaButtonCommand` 9). El patrón funciona; el problema es
   que no se ha extendido a la capa de orquestación.
5. `QueueIndexTest` cubre 4 tests de `needsRefillAfterEnd`, una función que
   producción no llama (ver §7): 4 tests que certifican código muerto.
6. `TranslationsTest` valida consistencia entre idiomas pero **no** detecta ni
   claves sin uso (16) ni claves referenciadas inexistentes (1).

---

## 9. HOJA DE RUTA

### Fase 1 — Lo que has reportado (impacto directo)

1. **B1** — pasar `youtubeId` en los tres `Song(...)` de `PlaylistScreen.kt:719,793,853`
   (`youtubeVideoId = track.youtubeVideoId`) **y** hacer que `executeSwipeAction`
   no llame a `toggleLikeTrack` con `""` (si no hay id, buscar por
   nombre+artista con `ImportManifest.fallbackDedupeKey`, que ya existe y se usa
   en `mergeLikedSongsTracks`). Sin la segunda mitad, volver a abrir la lista de
   favoritos sigue encontrando las filas corruptas que ya están guardadas.
2. **B7** — `pointerInput(song.youtubeId, index, trackEntities)` en
   `SongListItem.kt:148`, o mejor: sacar la decisión del gesto a una función pura.
3. **B8** — sustituir el `launch { snapTo }` por un acumulador sin suspender
   (guardar el offset en un `mutableFloatStateOf` y hacer un único `snapTo` en
   `onDragEnd`), o usar `Modifier.swipeable` / `anchoredDraggable`.
4. **B4** — que `invalidate()` reciba el id **realmente usado** (el que resolvió
   `startAt`, no `track.youtubeVideoId`), y persistirlo en la `MediaItem` para
   poder recuperarlo en el handler de error. Además, `getAudioUrl` debería
   aceptar un flag "forzar re-extracción" para que `reResolve = true` no pueda
   recibir la URL caducada de la caché.
5. **B5** — *single-flight* en `YouTubeManager.getAudioUrl` (un
   `ConcurrentHashMap<String, Deferred<String?>>` o `Mutex` por `videoId`), y
   dejar de llamar `growWindow()` dos veces por transición.
6. **B2** — implementar `SWIPE_ACTION_ADD_TO_PLAYLIST`: quitar el `{}` de
   `onShowPlaylistDialog` en `SongListItem.kt:165,182`, cablear un diálogo de
   selección de playlist, y borrar el `Log.d` de `:446-448`. Mientras tanto,
   **ocultar la opción en Ajustes** si no se va a implementar, porque hoy es la
   opción que más engaña.
7. **B3** — extraer la carga de `trackEntities`/`playlistTracks` a un
   `suspend fun refreshTracks()` (o a un `ViewModel`) y llamarla **después** de
   `addTrackToYouTubePlaylist` y de `removeTrackFromYouTubePlaylist`, además de
   no limpiar la búsqueda en el camino de éxito. Es el arreglo más rentable de
   todo el informe: cuatro líneas y tres síntomas visibles desaparecen.
8. **B14** — no borrar `searchResults`/`searchQuery` al añadir, y ofrecer
   "añadir los N resultados" además de la selección individual.
9. **B15** — propagar el recuento de descartes a la UI **también en el camino de
   éxito** (`PlaylistScreen.kt:1347-1350`), Idealmente bloqueando `<create>` si
   se ha perdido alguna canción, o al menos mostrando el aviso antes de
   navegar atrás.
10. **B13** — comprobación de duplicado por `remoteTrackId` en
    `addTrackToYouTubePlaylist` antes de insertar, reutilizando la lógica de
    `mergeLikedSongsTracks:230-240`. A medio plazo, un índice único
    `(playlistId, remoteTrackId)`.

### Fase 2 — Estabilidad

11. **B6** — limpiar `_error` al recuperar en `playIndex` (y al empezar cualquier
    carga), no solo en `startAt`.
12. **B36** — poner `resolving = true` en `playIndex` para que `isLoading` refleje
    la re-resolución y los controles no se reactiven a mitad.
13. **B9** — guardar la lista barajada en `trackEntities` (o en un estado que
    `SongListItem` reciba) para que `<rnd>` no desincronice la UI.
14. **B10** — borrar la constante duplicada de `PlayerViewModel.kt:713`.
15. **B11** — `DisposableEffect` en `QrScannerDialog` con `shutdown()` del executor
    y `unbindAll()`, y devolver el resultado del analizador al hilo principal.
16. **B19** — `DisposableEffect(lifecycleOwner, nfcState, nfcAdapter)` para que la
    escritura NFC arranque de verdad, y `NfcReader.enableForegroundDispatch`
    protegido (B30).
17. **B18** — ramificar por `ScanResult.type` en `SearchScreen` para abrir
    playlists.
18. **B20** — `remember(shareUrl) { generateQrBitmap(shareUrl) }`, y sacar la
    animación de `NfcButton` de la composición (Canvas con `withFrameNanos` en un
    `LaunchedEffect`).
19. **B16** — hacer `YouTubePlaylistCreator.build` `suspend`, meter un
    `ensureActive()` por pista y un timeout por resolución, y añadir un botón
    "cancelar" mientras `isLoading`.

### Fase 3 — Datos e i18n

20. **B12** — meter `toggleLikeTrack` en una transacción Room.
21. **B21** y **B22** — corregir los valores japoneses del mapa `català` y
    eliminar las claves duplicadas; añadir un test que compare el **conjunto** de
    claves de los 4 idiomas (no una por una), que es lo que habría pillado ambas
    cosas.
22. **B25** — `parseTimestamp` con `java.time.Instant.parse` y un único fallback
    explícito; devolver `null` en vez de "ahora" para que el llamante distinga.
23. **B26**, **B27** — pasar el `playlistId` real a `getPlaylistThumbnailUrl`, y
    limpiar `getFormattedVideoCount` con `Locale`.
24. **B28** — añadir `youtubeVideoId` a `AppTrack` o dejar de hacer la ida y vuelta
    por `AppTrack` en `PlaylistScreen`.
25. **B24** — `"$ load_error"` → clave de traducción real.
26. **§7.1** — borrar las 16 claves sin uso y arreglar `QueueScreen.kt:87,103`.
    Añadir a `TranslationsTest` un test que falle si una clave definida no
    aparece en el código, y que detecte claves referenciadas que no existen (el
    que habría pillado B7 al instante).
27. **B38**, **B39**, **B40**, **B41** — quitar `WAKE_LOCK`, poner el receiver en
    `exported="false"`, excluir `plyr_config.xml` del cloud-backup, y activar R8
    con reglas para Room/NewPipe.

### Fase 4 — Estabilidad operativa y limpieza

28. **B23** — mover exportación, importación y copia de seguridad a un scope de
    aplicación (o a un `ViewModel`) para que un swipe en el pager no las corte.
29. **§7** — borrar `SongMenuDialog.kt` (171), `CollapsibleSection.kt` (88),
    `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd` + sus 4
    tests, `YouTubeManager.clearCache`, y decidir el destino de `isValidAudioUrl`.
30. **B32**, **B33** — dejar de loguear cookies, cabeceras y cuerpos completos;
    corregir `optString(key, null)`, leer el cuerpo de error en los 4xx, y
    URL-encodear el `invite_code`.
31. Extraer el estado de ventana de `PlayerViewModel` a una unidad propia
    testeable, como ya se hizo con `QueueIndex` y `MediaButtonCommand` — es lo que
    permitiría cubrir B4, B5, B6 y B36 con tests JVM.
32. Añadir tests instrumentados de los flujos que no se pueden cubrir en JVM:
    importación de playlist, escáner QR, escritura NFC.
