# Reporte de análisis de PLYR

**Fecha:** 2026-09-29
**Alcance:** `app/src/main/java/com/plyr` (71 archivos, ~14.757 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual (lectura + verificación de cada hallazgo contra el código). **No se ha compilado ni ejecutado ningún test**, tal como se pidió: nada de lo que sigue está verificado en runtime, solo leído. (Exception: los cambios de sync documentados en la **Actualización** de abajo sí se verificaron después con `./run.sh test` —298 tests, en verde— y `./run.sh build`.)

> **Aviso sobre este informe.** Es una reescritura, no una actualización. La
> numeración de bugs es **nueva** y **no guarda relación** con la del informe
> anterior. Todo lo que estaba resuelto y ya no era cierto se ha borrado (§10).
> Las cifras de tests del cuerpo del informe eran, en su momento, un
> **recuento estático** de anotaciones `@Test`, no el resultado de una
> ejecución; las cifras actualizadas están en la **Actualización** de abajo y
> en §1.4/§2/§8.

> **Actualización (2026-09-30).** Los tests se han ejecutado desde entonces
> (`./run.sh test` y `./run.sh build`): **298 tests, todos en verde**, con 7
> tests nuevos en `ImportManifestTest` (30) y `ExportManifestTest` (24) que
> cubren la nueva política de borrados. La sección de copia de seguridad
> cambió de comportamiento en estas dos direcciones:
>
> - **Sync bidireccional**: antes de escribir, la copia anterior de la carpeta
>   se fusiona en la app (`DataSync.flush` → `mergeArchiveFromFolder`), así que
>   instalar de cero y pulsar sync restaura el archivo en vez de pisarlo con
>   una copia vacía; si el archivo existe pero no se puede leer, no se
>   escribe nada (`SyncResult.ArchiveUnreadable`).
> - **Propagación de borrados (tombstones)**: borrar una lista queda registrado
>   en `Config` (`deleted_playlist_ids`) y viaja en `deletedPlaylistIds` del
>   manifiesto (campo aditivo, formato v1 intacto). Al importar se unen los
>   tombs locales con los del archivo, se aplican a las listas locales y se
>   fusionan en la huella `ExportDigest` y en la UI del botón sync. Guardar de
>   nuevo una lista limpia su tomb, así que es reversible.
>
> En una segunda tanda se resolvieron los 5 bugs más triviales —**B10**, **B24**,
> **B30**, **B38**, **B43**—, en una tercera los 9 del siguiente grupo de
> dificultad —**B17**, **B21**, **B22**, **B31**, **B34**, **B40**, **B42**,
> **B46**, **B47**—, en una cuarta los 8 del grupo T3 —**B14**, **B26**, **B27**,
> **B29**, **B36**, **B37**, **B44**, **B45**— y en una quinta los 11 del grupo
> T4 —**B3**, **B9**, **B12**, **B13**, **B18**, **B19**, **B20**, **B25**,
> **B33**, **B35**, **B41**— y en una sexta los 7 del grupo T5 —**B7**, **B8**,
> **B11**, **B15**, **B16**, **B23**, **B28**—; todos están documentados en
> **§10** (40 resueltos en total) y quedan **7 activos** (5 altos, 1 medio,
> 1 bajo). El total de tests pasó de 288 a **294** (con B25 se retiró el test
> que consolidaba el "ahora falso" de `parseTimestamp` y se añadieron 4:
> `isoWithOffset`, `invalidReturnsZeroNotNow`, `blankReturnsZero`,
> `rejectsTrailingGarbage`; en T5 se añadieron `build_reportsDiscardedTracks`
> y `build_cancelledScopeStopsResolving` en `YouTubePlaylistCreatorTest` y
> `trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition` en
> `DatabaseMappingsTest`). El resto de hallazgos no se ha tocado en estas seis
> rondas; el swipe "añadir a playlist" (**B2**), la reproducción y la cola
> (**B1**, **B4**, **B5**, **B6**), los datos (**B32**), **B39** (manifiesto),
> la animación de `NfcButton` (resto de B20) y las traducciones de **§7.1**
> siguen pendientes de lo descrito abajo.

---

## 1. Resumen ejecutivo

PLYR es una app de música **YouTube-only + local** en estado funcional. La
migración Spotify → YouTube está hecha, no queda `runBlocking`, las
dependencias están limpias y la lógica pura de cola está extraída y testeada.

**Los dos fallos que has reportado están encontrados, y no son el mismo bug.**
El tercero que recordabas —añadir canciones a una lista— también existe, y es
peor: **ninguna de las cinco rutas para hacerlo funciona** (§1.3). En total
fueron **47 bugs** verificados contra el código; desde entonces se han resuelto
**40** (B3, B7, B8, B9, B10, B11, B12, B13, B14, B15, B16, B17, B18, B19, B20,
B21, B22, B23, B24, B25, B26, B27, B28, B29, B30, B31, B33, B34, B35, B36, B37,
B38, B40, B41, B42, B43, B44, B45, B46, B47 — ver §10), así que quedan **7
activos** (5 altos, 1 medio, 1 bajo).
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

#### 4. Al crear una playlist solo puedes añadir **una** canción por búsqueda — **B14** (~~resuelto~~ → §10)

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

#### 5. Al crear la playlist se pierden canciones **sin avisar** — **B15** y **B16** (~~resueltos~~ → §10)

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

- **0 bugs críticos**, **5 altos**, **1 medio**, **1 bajo** (§3).
- **294 tests unitarios** en 21 archivos (`./run.sh test`; todos en verde).
  Respecto a la tanda anterior: +4 de `parseTimestamp` (B25: `isoWithOffset`,
  `invalidReturnsZeroNotNow`, `blankReturnsZero`, `rejectsTrailingGarbage`), −1
  que consolidaba el "ahora falso" (`parseTimestamp_invalidFallsBackToNow`) y +3
  en T5 (`build_reportsDiscardedTracks`, `build_cancelledScopeStopsResolving` y
  `trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition`).
- **0 instrumentados** útiles (solo `ExampleInstrumentedTest`).
- **~700 líneas muertas** entre `SongMenuDialog`, `CollapsibleSection`,
  `PlyrDimensions` y funciones sin uso (§7).
- **16 claves de traducción sin uso** y **1 clave referenciada que no existe**.
- R8 activo en release (**B41**); no verificado en build release (sin keystore local).

---

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 71 (~14.757 líneas) |
| Archivos de test | 21 (~3.177 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1363), `ConfigScreen.kt` (919), `PlayerViewModel.kt` (712), `Translations.kt` (617), `FloatingMusicControls.kt` (536), `SearchScreen.kt` (477), `SongListItem.kt` (459), `QRDialog.kt` (449) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7, migraciones `5→6` y `6→7` |
| Tests unitarios | **294** en 21 archivos (ejecutados y en verde) |
| Tests instrumentados útiles | 0 |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso | **16 de 104** (verificadas por barrido) |
| Claves referenciadas que no existen | **1** (`"Player not available"`) |

**Desglose de los 294 tests** (tras `./run.sh test`, todos en verde):

| Archivo | @Test | Archivo | @Test |
|---|---|---|---|
| `UrlParserTest` | 33 | `ExportDigestTest` | 11 |
| `UtilsTest` | 17 | `MediaButtonCommandTest` | 9 |
| `ImportManifestTest` | 30 | `AppModelsTest` | 8 |
| `CoverCropMathTest` | 24 | `YouTubeFormattingTest` | 11 |
| `SpotifyImporterTest` | 23 | `ModelDefaultsTest` | 7 |
| `ExportManifestTest` | 24 | `SupabaseClientTest` | 10 |
| `QueueIndexTest` | 22 | `CoverCacheTest` | 6 |
| `YouTubePlaylistCreatorTest` | 17 | `BackupFolderTest` | 5 |
| `ImportArchiveTest` | 14 | `TranslationsTest` | 5 |
| `DatabaseMappingsTest` | 14 | `NewPipeHolderTest` | 3 |
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
| **B4** | `PlayerViewModel.kt:589-592`, `:660`, `:668` | **La invalidación de URL caducada es un no-op.** `currentVideoId` se rellena con `queue[i].youtubeVideoId` (null para toda pista resuelta por búsqueda), así que `YouTubeManager.invalidate()` casi nunca se llama y el re-intento recibe de la caché **la misma URL muerta**. Detalle en §1.2. |
| **B5** | `YouTubeManager.kt:68-80` + `PlayerViewModel.kt:163,366,403,479-481` | **Doble extracción por transición.** `getAudioUrl` no deduplica en vuelo y `growWindow()` se invoca dos veces por salto, con `prefetchJob?.cancel()` + relanzamiento. La extracción cancelada (OkHttp sobre `Dispatchers.IO`, no interrumpible) no llega a escribir caché y la relanzada repite el trabajo. Es la "carga doble" que se oye. |
| **B6** | `PlayerViewModel.kt:611`, `:390` | **`_error` nunca se limpia en la ruta de error.** `_error.publish(null)` solo existe en `startAt:337` y `clearPlayerState:290`. Tras un `onPlayerError` recuperable, el estado de error se queda pegado para siempre: los controles quedan deshabilitados y el texto de error no desaparece aunque la canción vuelva a sonar. |

### 3.3 Medios

| # | Ubicación | Descripción |
|---|---|---|
| **B32** | `SimpleDownloader.kt:31`, `:49-59`, `:61` | El mapa de cookies es un `mutableMapOf` plano: `setCookie` muta desde el hilo que lo inicializa y `getCookies` lo lee desde **todos** los hilos de red, sin sincronizar. Además se loguean cookies (`:101`) y cabeceras completas (`:113-117`), que pueden incluir `Authorization`. |

### 3.4 Bajos

| # | Ubicación | Descripción |
|---|---|---|
| **B39** | `AndroidManifest.xml:54-60` | `MediaButtonReceiver` es `exported="true"` con intent filter y **sin permiso**: cualquier app del dispositivo puede inyectar `ACTION_MEDIA_BUTTON` y manejar la reproducción. El impacto se limita al transporte (la clase valida acción y keycode), pero no necesita estar exportada. |

---

## 4. SEGURIDAD

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S3 | Alta | `app/build.gradle.kts:45` | ~~Sin R8 ni ofuscación en release (ver B41).~~ **B41 resuelto**: R8 activo en `assembleRelease` (ver §10) y los `Log.*` con datos sensibles se eliminan con `-assumenosideeffects`. Pendiente de verificar con `./run.sh build release` (no hay keystore local). |
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
| Media | `viewmodel/PlayerViewModel.kt` (712) | Monolito con estado mutable repartido entre el hilo principal y las corrutinas. `generation` + `windowStart` + `transitionInFlight` son 3 banderas que hay que mantener coherentes a mano; B4 y B6 son consecuencia directa de que la invalidación de caché y el estado de error se gestionen en un sitio y no en otro. La lógica pura ya está aislada en `QueueIndex`, pero el estado de la ventana no. |
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

> `isValidAudioUrl` + `containsAudioPattern` (`Utils.kt:44-50`) ya **no existen**:
> eran código muerto y se borraron en **B37** junto con sus 13 tests (ver §10).
| `val loadPlaylists = { }` | `PlaylistScreen.kt:160` | No-op asignado y nunca invocado. |
| `TerminalColorsPreview`, `PreviewTerminalThemeDark/Light` | `Theme.kt:266,314` | Previews de Android Studio, inalcanzables en runtime. |
| `ResponsiveDimensions`: `titleSize`, `iconSize*`, `buttonHeight`, `buttonMinWidth` | `ResponsiveUtils.kt:31-51` | Calculados en cada llamada y nunca leídos. |
| `PlyrSymbols.COMMAND/SEPARATOR/BULLET/ARROW/BACK` | `Theme.kt:74-82` | Sin referencias. |
| `MediaMetadataExtractor.extractMetadata(context = …)` | `MediaMetadataExtractor.kt:26` | ~~Parámetro muerto en los dos call sites.~~ **Eliminado en B29** (ver §10). |
| `SongListItem.onShowPlaylistDialog` | `SongListItem.kt:417` | Ambos callers pasan `{}`. |
| `MediaCommand` sin `STOP` | `MediaButtonCommand.kt` | `KEYCODE_MEDIA_STOP` se mapea a `PAUSE` y el `NONE` de `execute` (`:77`) es rama inalcanzable. |
| `CollapsibleSection.statusColor` | `CollapsibleSection.kt:32` | Parámetro con default que llama a `MaterialTheme.colorScheme` en el argumento por defecto. |
| `ActionButtonData.enabled` | `ActionBttn.kt:25` | Ningún `ActionButtonData(...)` del source pone `enabled = false`; el render de deshabilitado (`:54`, `:65`) es inalcanzable. |

### 7.1 Traducciones

**16 de las 104 claves del mapa `español` no se referencian en ningún sitio**
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

---

## 8. TESTS

- **294 tests unitarios en 21 archivos** (`./run.sh test`, todos en verde;
  +7 respecto al recuento estático de esta revisión por la cobertura de
  tombstones/`deletedPlaylistIds`, luego +4 de formato de vídeo en B27, −13 al
  retirar los de `isValidAudioUrl` en B37, −1/+4 con `parseTimestamp` en B25
  (el test que certificaba el "ahora falso" se retiró y se añadieron
  `isoWithOffset`, `invalidReturnsZeroNotNow`, `blankReturnsZero` y
  `rejectsTrailingGarbage`) y +3 en T5:
  `build_reportsDiscardedTracks` y `build_cancelledScopeStopsResolving`
  (B15/B16) en `YouTubePlaylistCreatorTest` y
  `trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition` (B28) en
  `DatabaseMappingsTest`).
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
3. ~~**13 tests de `UtilsTest` consolidaban el bug de `isValidAudioUrl`** en vez de
   detectarlo (B37).~~ **Hecho** (ver §10): la función muerta y sus 13 tests se
   borraron.
4. **Los módulos más valiosos del repo están bien cubiertos** (`CoverCropMath` 24,
   `ImportManifest` 30, `ExportManifest` 24, `ImportArchive` 14, `ExportDigest` 11,
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
2. ~~**B7** — `pointerInput(song.youtubeId, index, trackEntities)` en
   `SongListItem.kt:148`, o mejor: sacar la decisión del gesto a una función pura.~~
   **Hecho** (ver §10). (Extraer la decisión del gesto a una función pura queda
   como mejora futura, resuena con §8 gap 3.)
3. ~~**B8** — sustituir el `launch { snapTo }` por un acumulador sin suspender
   (guardar el offset en un `mutableFloatStateOf` y hacer un único `snapTo` en
   `onDragEnd`), o usar `Modifier.swipeable` / `anchoredDraggable`.~~ **Hecho**
   (ver §10): acumulador `mutableFloatStateOf` con la animación de retorno en un
   único `Animatable`; la opción `swipeable`/`anchoredDraggable` queda como
   refactor futuro.
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
7. ~~**B3** — extraer la carga de `trackEntities`/`playlistTracks` a un
   `suspend fun refreshTracks()` (o a un `ViewModel`) y llamarla **después** de
   `addTrackToYouTubePlaylist` y de `removeTrackFromYouTubePlaylist`, además de
   no limpiar la búsqueda en el camino de éxito.~~ **Hecho** (ver §10): se añadió
   `tracksRevision` como clave del `LaunchedEffect` (no hace falta un ViewModel).
8. ~~**B14** — no borrar `searchResults`/`searchQuery` al añadir.~~ **Hecho**
   (ver §10). Pendiente solo la mejora de "añadir los N resultados" de golpe.
9. ~~**B15** — propagar el recuento de descartes a la UI **también en el camino de
   éxito** (`PlaylistScreen.kt:1347-1350`), Idealmente bloqueando `<create>` si
   se ha perdido alguna canción, o al menos mostrando el aviso antes de
   navegar atrás.~~ **Hecho** (ver §10): aviso "%N de %M canciones se añadieron
   (%D sin vídeo)" + botón "Continuar" antes de volver.
10. ~~**B13** — comprobación de duplicado por `remoteTrackId` en
    `addTrackToYouTubePlaylist` antes de insertar, reutilizando la lógica de
    `mergeLikedSongsTracks:230-240`.~~ **Hecho** (ver §10). Pendiente a medio
    plazo: un índice único `(playlistId, remoteTrackId)`.

### Fase 2 — Estabilidad

11. **B6** — limpiar `_error` al recuperar en `playIndex` (y al empezar cualquier
    carga), no solo en `startAt`.
12. ~~**B36** — poner `resolving = true` en `playIndex` para que `isLoading` refleje
    la re-resolución y los controles no se reactiven a mitad.~~ **Hecho**
    (ver §10).
13. ~~**B9** — guardar la lista barajada en `trackEntities` (o en un estado que
    `SongListItem` reciba) para que `<rnd>` no desincronice la UI.~~ **Hecho**
    (ver §10).
14. ~~**B10** — borrar la constante duplicada de `PlayerViewModel.kt:713`.~~ **Hecho** (ver §10).
15. ~~**B11** — `DisposableEffect` en `QrScannerDialog` con `shutdown()` del executor
    y `unbindAll()`, y devolver el resultado del analizador al hilo principal.~~
    **Hecho** (ver §10): `onDispose` con `unbindAll()` + `shutdown()`, guarda de
    reentrada con `AtomicBoolean` y callbacks al hilo principal.
16. ~~**B19** — `DisposableEffect(lifecycleOwner, nfcState, nfcAdapter)` para que la
    escritura NFC arranque de verdad.~~ **Hecho** (ver §10). (La protección de
    `NfcReader.enableForegroundDispatch` —B30— ya está hecha, ver §10.)
17. ~~**B18** — ramificar por `ScanResult.type` en `SearchScreen` para abrir
    playlists.~~ **Hecho** (ver §10).
18. ~~**B20** — `remember(shareUrl) { generateQrBitmap(shareUrl) }`~~ **Hecho**
    (ver §10): el QR ya no se regenera en cada recomposición. Queda pendiente la
    segunda mitad (optimización): sacar la animación de `NfcButton` de la
    composición (Canvas con `withFrameNanos` en un `LaunchedEffect`).
19. ~~**B16** — hacer `YouTubePlaylistCreator.build` `suspend`, meter un
    `ensureActive()` por pista y un timeout por resolución, y añadir un botón
    "cancelar" mientras `isLoading`.~~ **Hecho** (ver §10): `build` es `suspend`
    con `ensureActive()` y timeout `withTimeoutOrNull(15s)` por resolución; el
    botón "Cancelar" cancela el `Job` del create.

### Fase 3 — Datos e i18n

20. ~~**B12** — meter `toggleLikeTrack` en una transacción Room.~~ **Hecho**
    (ver §10).
21. ~~**B21** y **B22** — corregir los valores japoneses del mapa `català` y
    eliminar las claves duplicadas.~~ **Hecho** (ver §10). Nota: el "test que
    compare el conjunto de claves de los 4 idiomas" no se pudo añadir tal cual:
    `mapOf` colapsa los duplicados al construir el mapa, así que ningún test en
    runtime puede ver la duplicación de la fuente, y el conjunto real ya difiere
    por claves sobrantes (`nickname_description` solo en `català`/`日本語`), que es
    terreno de §7.1 (item 26).
22. ~~**B25** — `parseTimestamp` con `java.time.Instant.parse` y un único fallback
    explícito; devolver `null` en vez de "ahora" para que el llamante distinga.~~
    **Hecho** (ver §10): parseo estricto de dos formatos (`XX.SSS` y `XX` con
    offset explícito) y `0L` en fallo; `formatTimestamp` muestra "unknown".
23. ~~**B26**, **B27** — pasar la miniatura real a `getPlaylistThumbnailUrl` y
    limpiar `getFormattedVideoCount`.~~ **Hecho** (ver §10).
24. ~~**B28** — añadir `youtubeVideoId` a `AppTrack` o dejar de hacer la ida y vuelta
    por `AppTrack` en `PlaylistScreen`.~~ **Hecho** (ver §10): `AppTrack` ya
    transporta `youtubeVideoId` y `position`, y `toAppTrack()` los propaga.
25. ~~**B24** — `"$ load_error"` → clave de traducción real.~~ **Hecho** (ver §10).
26. **§7.1** — borrar las 16 claves sin uso y arreglar `QueueScreen.kt:87,103`.
    Añadir a `TranslationsTest` un test que falle si una clave definida no
    aparece en el código, y que detecte claves referenciadas que no existen (el
    que habría pillado B7 al instante).
27. **B39**, ~~**B41**~~ — poner el receiver en `exported="false"` y ~~activar R8 con
    reglas para Room/NewPipe~~ (B41 **Hecho**, ver §10: `isMinifyEnabled = true`
    con `-keep` para NewPipe y `-assumenosideeffects` para `Log.*`; pendiente de
    verificar en `./run.sh build release` por falta de keystore local). (Excluir
    `plyr_config.xml` del cloud-backup —B40— ya está hecho, ver §10; quitar
    `WAKE_LOCK` —B38— ya está hecho, ver §10.)

### Fase 4 — Estabilidad operativa y limpieza

28. ~~**B23** — mover exportación, importación y copia de seguridad a un scope de
    aplicación (o a un `ViewModel`) para que un swipe en el pager no las corte.~~
    **Hecho** (ver §10): `backgroundScope` expuesto por `PlyrApp` y usado por
    `SyncSection` en config.
29. **§7** — borrar `SongMenuDialog.kt` (171), `CollapsibleSection.kt` (88),
    `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd` + sus 4
    tests y `YouTubeManager.clearCache`. (`isValidAudioUrl` —B37— ya se borró con
    sus 13 tests, ver §10.)
30. **B32** — dejar de loguear cookies, cabeceras y cuerpos completos.
    ~~**B33** — corregir `optString(key, null)`, leer el cuerpo de error en los
    4xx, y URL-encodear el `invite_code`.~~ **Hecho** (ver §10).
31. Extraer el estado de ventana de `PlayerViewModel` a una unidad propia
    testeable, como ya se hizo con `QueueIndex` y `MediaButtonCommand` — es lo que
    permitiría cubrir B4, B5, B6 y B36 con tests JVM.
32. Añadir tests instrumentados de los flujos que no se pueden cubrir en JVM:
    importación de playlist, escáner QR, escritura NFC.

---

## 10. BUGS RESUELTOS

Primera tanda de arreglos (2026-09-30), los 5 más triviales del informe.
Todos verificados con `./run.sh test` (**298 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B10** | `PlayerViewModel.kt:713` | Eliminado el `private const val MAX_CAUSE_DEPTH = 5` a nivel de fichero, duplicado y sombreado por el del companion (`:62`); la referencia de `:631` sigue resolviendo al companion, comportamiento idéntico. |
| **B24** | `CoverCropDialog.kt:79` | El `"$ load_error"`/`"$ loading..."` (que en Kotlin no era plantilla y se veía literal) ahora muestra el mensaje de error real (`loadError`, con fallback `"error"`) o la clave **`Translations.get(context, "loading")`** en los 4 idiomas. |
| **B30** | `NfcReader.kt:82`, `:93` | `enableForegroundDispatch` envuelto en `try/catch` (devuelve `false` y loguea si la Activity no está `resumed`, p. ej. desde el `onDispose` de `QRDialog`); `disableForegroundDispatch` en `stopReading` también protegido. |
| **B38** | `AndroidManifest.xml:15` | Retirado el permiso `WAKE_LOCK`, sin uso (verificado: cero `WakeLock`/`setWakeMode`). La reproducción con pantalla apagada la sostiene `foregroundServiceType="mediaPlayback"` + `USAGE_MEDIA`. |
| **B43** | `MusicService.kt:94` | Eliminada la aserción `mediaSession!!`: `createNotification` recibe el `MediaSession` por parámetro y `updateNotification` usa guarda `mediaSession ?: return`, ambas tras asignarlo en `setupMediaSession`. |

---

Segunda y tercera tanda de arreglos (2026-09-30), los 9 bugs del grupo T2.
Todos verificados con `./run.sh test` (**298 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B17** | `ConfigScreen.kt:100` | Quitada la llamada duplicada a `onThemeChanged` del `onSelected` del selector de tema; el `LaunchedEffect(selectedTheme)` ya persiste y notifica, así que cada cambio solo dispara una vez `Config.setTheme` + `onThemeChanged` en lugar de dos. |
| **B21** | `Translations.kt` (català) | `plyr_queue` → `"plyr_cua"` y `No tracks loaded` → `"Cap cançó carregada"`: el mapa `català` ya no tiene valores japoneses copiados del bloque `日本語`. |
| **B22** | `Translations.kt` | Eliminadas las claves duplicadas dentro de cada `mapOf`: `loading` en los 4 idiomas, `user_nickname` en `català`/`日本語` y `share_me` en `català` (se conservó la primera ocurrencia, con el valor coherente con el resto de idiomas). |
| **B31** | `LightSensorDetector.kt:46,:58-64,:79` | `event.values.getOrNull(0)` (sin AIOOBE), `getSystemService(SensorManager::class.java)` con guarda de null, e `isListening = true` solo si `registerListener` devolvió `true` (si falla, `start()` ya no queda como no-op permanente). |
| **B34** | `FloatingMusicControls.kt:330` | `pointerInput(duration)` en la barra de progreso: el gesto se reinicia al cambiar de canción y el `seekTo` ya no usa la duración de la pista anterior. |
| **B40** | `res/xml/backup_rules.xml` y `data_extraction_rules.xml` | `sharedpref/plyr_config.xml` (nickname, Uri SAF, hash de la copia) excluido del **cloud-backup**; se mantiene en `device-transfer`, que no sube nada a la nube. Comentarios actualizados. |
| **B42** | `MusicService.kt:41-51` | Añadida acción `ACTION_STOP` (`com.plyr.action.STOP`): `onStartCommand` la detecta y hace `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`, en vez de ignorar `intent?.action`. |
| **B46** | `MainActivity.kt:63-66,:185-195` | `onServiceDisconnected` ya no anula `onMediaSessionUpdate`; se anula solo en `onDestroy` cuando `isFinishing` (con `pausePlayer()` + `stopService` antes del `unbindService`), de modo que en rotación el `MediaSession` del servicio no deja una ventana con la pista anterior. |
| **B47** | `QrScannerDialog.kt:61-79` | Si el permiso de cámara se deniega, el diálogo muestra mensaje `permission_denied`, botón `retry` (re-lanza el `RequestPermission`) y botón `close` en lugar de un `Box` vacío. Nuevas claves `permission_denied`/`retry`/`close` en los 4 idiomas. |

---

Cuarta tanda de arreglos (2026-09-30), los 8 bugs del grupo T3.
Todos verificados con `./run.sh test` (**288 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B14** | `PlaylistScreen.kt:1250` | El botón custom "+" ya no vacía `searchResults`/`searchQuery` al añadir una canción: se pueden montar listas añadiendo varias canciones de la misma búsqueda. (Pendiente solo el botón "añadir los N resultados".) |
| **B26** | `YouTubeSearchManager.kt:248,:376` | `getPlaylistThumbnailUrl()` ahora recibe la miniatura real del `PlaylistInfoItem` de NewPipe (`item.thumbnails`) y devuelve su URL en vez del placeholder hardcodeado `vi/undefined`. |
| **B27** | `YouTubeSearchManager.kt:168-184` | `getFormattedVideoCount` usa `Locale.ROOT` (adiós "1,5K" en `es-ES`/`ca-ES`), elimina el `else` inalcanzable y añade sufijos `M`/`B` (1 500 000 → "1.5M"). Se añadieron 3 tests (`videoCount_millions`, `videoCount_millionsWhole`, `videoCount_billions`). |
| **B29** | `MediaMetadataExtractor.kt` | Eliminado el parámetro `context` sin usar; `isYouTubeUrl` ahora es insensible a mayúsculas; la heurística de playlist vía `v=PL/UU/FL/RD` solo se activa si el valor NO tiene 11 caracteres (un vídeo normal cuyo id empiece por esos prefijos ya no se clasifica como playlist). |
| **B36** | `PlayerViewModel.kt:410-486` | En la ruta de `playIndex` con re-resolución se pone `resolving = true` + `updateLoadingState()` + limpieza de `_error` (como `startAt`), y se cierra en un `finally` solo si la resolución sigue siendo la vigente (`gen == generation`). El spinner y el bloqueo de controles ya reflejan la re-resolución. |
| **B37** | `Utils.kt` + `UtilsTest.kt` | Borrada `isValidAudioUrl` (código muerto: cero llamadas; su lógica ya devolvía `true` para toda URL http(s)) junto con sus privadas `isValidUrlFormat`/`containsAudioPattern` y sus **13 tests** que consolidaban el comportamiento incorrecto (`UtilsTest` pasa de 30 a 17). |
| **B44** | `SearchScreen.kt` | Eliminado el `LaunchedEffect` de `while(true) { delay(100); leer SharedPreferences }` y la variable `currentLanguage` write-only: se acabaron las 10 lecturas de disco por segundo en pantalla de búsqueda. |
| **B45** | `NewPipeHolder.kt:20-24` | `Localization("es", "ES")` hardcodeado reemplazado por el locale del sistema (`Locale.getDefault()`), así que los mensajes de error del extractor ya no salen siempre en español. |

---

Quinta tanda de arreglos (2026-09-30), los 11 bugs del grupo T4.
Todos verificados con `./run.sh test` (**291 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL). B41 (R8) no se pudo verificar en
`assembleRelease` por falta de keystore local.

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B3** | `PlaylistScreen.kt:146-158` | Añadir y quitar en modo edición ya refrescan la lista: un contador `tracksRevision` se suma a la clave del `LaunchedEffect` y se incrementa tras cada `addTrackToYouTubePlaylist`/`removeTrackFromYouTubePlaylist`. Además el add de edición ya no limpia `searchResults`/`searchQuery` (refuerza B14). |
| **B9** | `YouTubePlaylistDetailView.kt:138-142` | `<rnd>` ahora sustituye `trackEntities` por la lista barajada y reordena `videos` para que `isPlaying` (y todas las acciones por índice) apunten a la pista que realmente suena. |
| **B12** | `PlaylistLocalRepository.kt:56-99` | `toggleLikeTrack` envuelto en `database.withTransaction { }`: `position` y `trackCount` se leen/reescriben de forma atómica, sin ventana entre lecturas. |
| **B13** | `PlaylistLocalRepository.kt:319-347` | `addTrackToYouTubePlaylist` deduplica por `(youtubeVideoId ?: remoteTrackId)`: re-añadir la misma canción es un no-op (devuelve `true`) en vez de crear filas fantasma con id incremental. |
| **B18** | `YouTubeSearchManager.kt` + `SearchScreen.kt:152-157,:222-227` | Nuevo `getYouTubePlaylistInfo(playlistId)` (nombre, autor y nº de canciones vía `PlaylistExtractor`). Escanear un código de **playlist** (NFC o QR) ramifica por `result.type == "playlist"` y abre la playlist en vez de construir un `watch?v=<id-de-playlist>` inválido. El `else` de error ("unsupported source") solo aplica a no-YouTube. |
| **B19** | `QRDialog.kt:164-166` | `DisposableEffect(lifecycleOwner, nfcState, nfcAdapter)`: `nfcAdapter` es ahora clave del efecto, así que al asignarse (después de null en la primera composición) `enableForegroundDispatch` arranca de verdad y la escritura de tags NFC funciona desde el principio. |
| **B20** | `QRDialog.kt:223` | `generateQrBitmap(shareUrl)` movido a `val qrBitmap = remember(shareUrl) { generateQrBitmap(shareUrl) }`: el QR 512×512 ya no se decodifica y pinta en el hilo principal en cada recomposición (p. ej. las 5 frames por segundo del `NfcButton` en `WAITING`). |
| **B25** | `SupabaseClient.kt:308-334` + `Utils.kt` | `parseTimestamp` reescrito: dos formatos estrictos (`yyyy-MM-dd'T'HH:mm:ssXXX` y `….SSSXXX`), `isLenient=false`, `ParsePosition(0)` exigiendo consumo completo del input y formatos en `ThreadLocal` (sin el `'Z'` duplicado ni el `SSSSSS` tramposo). El offset `+HH:MM` ya se aplica (antes se descartaba). Un fallo devuelve **`0L`**, nunca `System.currentTimeMillis()`: una fecha ilegible ya no se disfraza de "ahora". `formatTimestamp` muestra `"unknown"` para `timestamp <= 0`. Tests: se retiró `parseTimestamp_invalidFallsBackToNow` y se añadieron `isoWithOffset`, `invalidReturnsZeroNotNow`, `blankReturnsZero` y `rejectsTrailingGarbage`. |
| **B33** | `SupabaseClient.kt:59,109,179,228,291` | `optString(key, null)` → `JSONObject.optNullableString` (una cadena JSON `null` ya no se guarda/muestra como la palabra "null"); helper `readBody` que lee `errorStream` en los no-2xx (adiós al `"error: null"` de los 4xx y a `FileNotFoundException`); checks 2xx en las llamadas de escritura (createGroup, alta de miembro, createRecommendation); e `invite_code` **URL-encodificado** en el filtro PostgREST (`eq.`), de modo que `&`, `#` o `,` ya no cambian la semántica de la consulta. |
| **B35** | `FloatingMusicControls.kt:145` | `LaunchedEffect(playerViewModel.exoPlayer)` sustituido por un `LaunchedEffect(Unit)` autosostenido que relee `exoPlayer` en cada vuelta (`?: run { delay(500); continue }`): el polling ya no depende de que otras `LiveData` fuerzen recomposición. |
| **B41** | `app/build.gradle.kts` + `app/proguard-rules.pro` | `isMinifyEnabled = true` en release + `-keep class org.schabi.newpipe.** { *; }` (no se puede ofuscar el extractor) y `-assumenosideeffects` para `android.util.Log` (v/d/i/w/e), de modo que los `Log.*` con cuerpos/cookies de B32 vuelven de la mano del shrinking. **Sin verificar en build release** (no hay keystore local): probar con `./run.sh build release`. |

---

Sexta tanda de arreglos (2026-09-30), los 7 bugs del grupo T5.
Todos verificados con `./run.sh test` (**294 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B7** | `SongListItem.kt:148` | `.pointerInput(Unit)` → `.pointerInput(song.youtubeId, index, trackEntities)`: el gesto ya se reinicia cuando cambia la pista, su índice o el set de pistas (tras reordenar o añadir), en las cuatro listas que usan `SongListItem`. |
| **B8** | `SongListItem.kt:150-153,:194-199` | El drag ya no lanza una corrutina con `snapTo` por evento: el offset se acumula de forma síncrona en un `mutableFloatStateOf` (`dragOffset`), `onDragStart` cancela la animación de retorno y `onDragEnd` evalúa el umbral con el valor real acumulado; la vuelta al reposo se anima una sola vez con `Animatable` (`tween(300)`). El gesto ya no se "come" desplazamientos. |
| **B11** | `QrScannerDialog.kt` | `DisposableEffect(lifecycleOwner, context)` cuyo `onDispose` hace `cameraProviderRef.unbindAll()` y `scannerExecutor.shutdown()` (executor remembered a nivel de diálogo, no en línea); `scanHandled` (`AtomicBoolean`) evita que cada frame posterior del analizador vuelva a disparar `onQrScanned`/`onDismiss`; ambos callbacks se devuelven al hilo principal con `ContextCompat.getMainExecutor`. |
| **B15** | `YouTubePlaylistCreator.kt` + `PlaylistScreen.kt` | El recuento de descartes viaja en la firma (`CreatedYouTubePlaylist.discardedTracks`). Con canciones sin vídeo, `CreatePlaylistScreen` muestra "%N de %M canciones se añadieron (%D sin vídeo)" con un botón "Continuar" explícito antes de volver atrás; el mensaje de fallo también lo usa. |
| **B16** | `YouTubePlaylistCreator.kt` + `PlaylistScreen.kt` | `build` pasa a `suspend` con `coroutineContext.ensureActive()` por pista; cada resolución tiene timeout `withTimeoutOrNull(15 s)`; `resolveVideoId` es un `suspend` inyectable (testeable sin red). En la UI, el botón "Cancelar" (visible mientras `isLoading`) cancela el `Job` del create. |
| **B23** | `PlyrApp.kt` + `ConfigScreen.kt` | `PlyrApp` expone `backgroundScope` (`SupervisorJob() + Dispatchers.Main.immediate`); `SyncSection` lanza exportación/importación/copia en ese scope (`applicationContext as PlyrApp`) en vez de `rememberCoroutineScope()`: un swipe en el pager ya no corta el trabajo a mitad. |
| **B28** | `AppModels.kt` + `DatabaseExtensions.kt` | `AppTrack` gana `youtubeVideoId` y `position`; `toAppTrack()` los propaga desde `TrackEntity` y `buildSourceTracks` los conserva, así que la ida y vuelta `AppTrack → TrackEntity` ya no pierde el id de YouTube. |

Aún pendientes, más difíciles: **B2** (swipe "añadir a playlist"), la reproducción
y la cola (**B1**, **B4**, **B5**, **B6**), **B32** (logs con datos sensibles),
**B39** (manifiesto), la optimización de la animación de `NfcButton` (resto de
B20) y las traducciones de **§7.1**.
