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
> **B11**, **B15**, **B16**, **B23**, **B28**— y en una séptima los 7 que
> quedaban de reproducción, favoritos, swipe y manifiesto —**B1**, **B2**,
> **B4**, **B5**, **B6**, **B39**, **B48**— y en una octava el cierre —
> **B20** (animación de `NfcButton`), **B32** (los datos), **B41** (build release,
> que estaba roto), **§7.1** (traducciones)—; todos están documentados en
> **§10** (**48 resueltos y 0 activos** en esa fecha; desde el 2026-10-01 hay
> 7 activos nuevos en §3) y el total de tests pasó de 288 a
> **339**. En T8 se añadieron tres ficheros nuevos —
> `SimpleDownloaderLogRedactionTest` (18), `NfcPulseTest` (11) y
> `TranslationKeysUsageTest` (4)— y `TranslationsTest` dejó de exigir la clave
> `info` que se borró. En una novena tanda, sin bugs nuevos, se borró el código
> muerto que la propia auditoría listaba en §7: `SongMenuDialog.kt` (171),
> `CollapsibleSection.kt` (88), `PlyrDimensions`, tres constantes de
> `PlyrSymbols`, `loadPlaylists` y `QueueIndex.needsRefillAfterEnd` con sus 4
> tests, que certificaban una función que producción nunca llama:
> **−301 líneas y −4 tests**, y `./run.sh test`, `./run.sh build` y
> `./run.sh build release` quedan en verde. El histórico de tests por tanda: con B25 se retiró el test
> que consolidaba el "ahora falso" de `parseTimestamp` y se añadieron 4:
> `isoWithOffset`, `invalidReturnsZeroNotNow`, `blankReturnsZero`,
> `rejectsTrailingGarbage`; en T5 se añadieron `build_reportsDiscardedTracks`
> y `build_cancelledScopeStopsResolving` en `YouTubePlaylistCreatorTest` y
> `trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition` en
> `DatabaseMappingsTest`; en T6 se añadió el fichero nuevo
> `AudioUrlExtractionTest` (9) y 7 tests de identidad de favoritos en
> `DatabaseMappingsTest`, que pasa a 21.
>
> Lo único que queda abierto ya **no es un bug**: la lista de literales de
> interfaz que no pasan por `Translations` (§7.1), que es un refactor de
> amplio alcance y no un fallo.

> **Actualización (2026-10-01).** Nueva tanda de **reporte**, sin arreglos: se
> han vuelto a reportar tres fallos de reproducción/sincronización y tres
> comportamientos de compartir, y se ha añadido una petición de funcionalidad.
> Todos se han **verificado leyendo el código**, no ejecutando la app: **no se
> ha compilado ni se ha pasado ningún test**, tal como se pidió, así que las
> cifras de tests de abajo (339) son las de la última tanda y **no** han
> cambiado.
>
> - **8 bugs documentados, 4 activos (B49–B51, B53)**: los cuatro reportados. El
>   botón de anterior no vuelve a la canción anterior (**B49**), el botón de
>   siguiente desaparece de la notificación tras dos skips seguidos (**B50**), el
>   sync resucita los favoritos que se han borrado (**B51**) y el share de una
>   lista construye una URL inválida (**B53**).
>   **B54** (el diálogo de compartir en blanco), **B55** y **B56** (la notificación
>   que decía "Plyr / Reproduciendo") aparecieron al documentarlos y ya están
>   **resueltos**: B55 + B56 en la undécima tanda, B54 en la duodécima y B52 en
>   la treceava (→ §10). Detalle en §3, Arrangement en §9.
> - **3 peticiones de comportamiento/funcionalidad** en §11: que `liked` vacía
>   no aparezca como lista (**F1**), el criterio de qué URL debe compartirse
>   (**F2**, que es B52 + B53, y de los dos solo queda B53) y el "añadir a
>   lista" en el menú `*` (**F3**) — **F3 ya resuelta** en la décima tanda
>   (→ §10), F1 y F2 siguen abiertas.
> - El **B49** y el **B50** comparten el mismo defecto de fondo que §6 ya
>   señalaba: `MusicService` no registra ningún `MediaSession.Callback`, así
>   que los botones de la notificación y del lockscreen mueven ExoPlayer por
>   dentro de la ventana deslizante en vez de pasar por `QueueIndex`. Ya no es
>   una nota de arquitectura: tiene dos síntomas reproducibles.
> - La numeración **B49+** es nueva y no guarda relación con los B1–B48 de las
>   tandas anteriores, que siguen resueltos (§10).
> - **Al re-verificar B49–B53 para documentarlos han salido tres bugs más que no
>   estaban en el reporte: B54, B55 y B56.** No se contaban antes. **B55 y B56**
>   (la notificación fantasma al terminar la cola, y la que dice "Plyr /
>   Reproduciendo" al abrir la app sin música) se han **resuelto** en la undécima
>   tanda; queda **B54** activo.
> - **Décima tanda (2026-10-01): F3 implementada.** Se ha arreglado lo más fácil
>   de todo lo pendiente (el "añadir a lista" del menú `*`, que solo era cablear
>   UI), con `./run.sh test` (339, en verde) y `./run.sh build` (BUILD
>   SUCCESSFUL). **Undécima tanda (2026-10-01): B55 + B56 resueltos**, la
>   notificación que decía estar reproduciendo sin que hubiera nada sonando, con
>   `./run.sh test` (**347**, en verde) y `./run.sh build` (BUILD SUCCESSFUL).
>   **Duodécima tanda (2026-10-01): B54 resuelto**, el diálogo de compartir ya no
>   puede abrirse en blanco. Quedan **4 bugs abiertos** y 2 peticiones. Detalle en §10.

---

## 1. Resumen ejecutivo

PLYR es una app de música **YouTube-only + local** en estado funcional. La
migración Spotify → YouTube está hecha, no queda `runBlocking`, las
dependencias están limpias y la lógica pura de cola está extraída y testeada.

**Los dos fallos que has reportado están encontrados, y no son el mismo bug.**
El tercero que recordabas —añadir canciones a una lista— también existía, y era
peor: **ninguna de las cinco rutas para hacerlo funcionaba** (§1.3). **Las tres
cosas están ya arregladas**: los dos fallos reportados y las cinco rutas. En total
fueron **48 bugs** verificados contra el código; desde entonces se han resuelto
**los 48** (B1–B48 — ver §10), así que **no queda ninguno activo**. Los dos
últimos cierres fueron el **B32** (el mapa de cookies de `SimpleDownloader`, sin
sincronizar, y los logs que volcaban cookies, cabeceras y cuerpos de respuesta) y
el **B41** (el build de release estaba roto: R8 abortaba por `java.beans` de
Rhino y lint trataba como fatal un `exclude` de backup redundante; ahora
`./run.sh build release` termina bien). El hallazgo **B48** (el refresco de la
lista *Liked*) salió de revisar el flujo de favoritos que reportaste ("no puedo
quitar canciones de Liked"), y quedó arreglado en la séptima tanda junto con
**B1**: quitar un favorito de la lista *Liked* abierta es justamente el caso que
fallaba.
### 1.1 El slide a "liked" no funciona → **es un bug de datos, no de gesto** (~~B1~~, **resuelto** → §10)

`PlaylistScreen` construía el `Song` **sin `youtubeId`** en los tres sitios donde
se lista una playlist (`PlaylistScreen.kt:721`, `:794`, `:856`):

```kotlin
val song = Song(number = index + 1, title = track.name,
                artist = track.getArtistNames(),
                remoteId = track.id,                       // ← no había youtubeId
                shareUrl = "https://www.youtube.com/watch?v=${track.id}")
```

Los tres pasan ya `youtubeId` (el de la pista, o el id de YouTube del resultado
de búsqueda en la lista del modo edición), así que el `youtubeId` ya no llega
nulo a ninguna fila.

`SongListItem.executeSwipeAction` tampoco lo sustituía por cadena vacía
(`SongListItem.kt:421-432`):

```kotlin
Config.SWIPE_ACTION_ADD_TO_LIKED -> {
    val isNowLiked = repo.toggleLikeTrack(
        youtubeVideoId = song.youtubeId ?: "",           // ← siempre ""
        ...
```

y `toggleLikeTrack` buscaba por `youtubeVideoId` con **igualdad exacta**
(`PlaylistLocalRepository.kt:61-63`):

```kotlin
val tracks = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
val existing = tracks.find { it.youtubeVideoId == youtubeVideoId }
```

La identidad de la fila de *liked* vive ahora en una función pura,
`PlaylistLocalRepository.likedTrackOf`, usada por el toggle **y** por la
consulta de estado: primero busca por `youtubeVideoId` y, si la canción llega
sin id, cae a nombre+artista con `ImportManifest.fallbackDedupeKey` —el mismo
criterio que ya usaba `mergeLikedSongsTracks`—, de modo que las filas corruptas
que quedaron guardadas con `""` también se encuentran y se pueden quitar. El
`""` ya no se envía nunca, y el popup (`SongListItem.kt:287-292`) consulta
`isTrackLikedByKey`, así que la fila muestra el estado real y el corazón actúa
aunque la canción no tenga id.

Consecuencias, en orden (todas ~~desaparecidas~~ con el arreglo):

1. ~~El primer swipe guarda una pista con `youtubeVideoId = ""` en `liked_songs`.~~
2. ~~El swipe sobre **otra** canción sin `youtubeId` encuentra esa misma fila
   (`"" == ""`) y **la borra** en lugar de marcar la nueva. Por eso "a veces no
   funciona": depende del estado previo de la lista de favoritos.~~
3. ~~El popup de la fila nunca consulta el estado real: `LaunchedEffect` corre con
   `song.youtubeId` nulo y no llama a `isTrackLiked`, así que `isLiked` se queda
   en `false` y la fila muestra **"♡ like" aunque ya esté en favoritos**
   (`SongListItem.kt:287-292`). Y como el pulso también va en
   `song.youtubeId?.let { … }`, **el botón no hace nada** (no-op silencioso).
   (Solo las filas corruptas con `youtubeVideoId=""` salen `true`.)~~
4. La pista guardada sin id de YouTube luego hay que resolverla por
   nombre/artista en cada reproducción (`YouTubeManager.resolveVideoId`), lo que
   además hace que dos canciones parecidas colisionen en la misma fila. Esto ya
   no ocurre al guardar desde las listas de reproducción, que ahora llevan el id
   real; sigue siendo el caso para las pistas importadas que nunca resolverán
   (offline), por eso `PlayerViewModel` recuerda el video que resolvió cada pista
   (§1.2, **B4**).

Las pantallas de **búsqueda** sí pasaban el id real (`SearchScreen.kt:447`,
`YouTubeSearchResults.kt:226`, `YouTubePlaylistDetailView.kt:226`,
`QueueScreen.kt:65`), y ahí el like siempre funcionó. Por eso parecía
intermitente: en la lista de reproducción no funcionaba, en la búsqueda sí.

**Y por eso mismo "quitar" de la lista *Liked* no funcionaba** (el síntoma que
reportaste): abierta esa lista, el `Song` volvía a ir sin `youtubeId`, así que el
corazón del detalle era un no-op silencioso y el swipe "a liked" llamaba a
`toggleLikeTrack("")`, que **no encontraba** la fila real (su `youtubeVideoId`
está bien guardado, no es `""`) y entraba en la rama de **añadir**: creaba una
fila fantasma con id vacío en lugar de borrar la que se ve. La canción nunca
salía de la lista. Añadir era fácil porque se hace desde las pantallas de
búsqueda/crear (que sí llevan `youtubeId`); quitar se hace desde dentro de
*Liked* (que no lo llevaba). Con el `youtubeId` real en las filas y el fallback
por nombre+artista, **quitar funciona**; y con **B48** la lista abierta se
refresca sola tras el toggle (ver §3.3).

**Hay un segundo defecto en el mismo gesto, independiente del anterior:**
~~el `pointerInput` del swipe estaba fijado a `Unit` (`SongListItem.kt:148`), así
que captura `song`, `index` y `trackEntities` en la primera composición y nunca
se reinicia~~ (**B7**, ~~resuelto~~ → §10): se pasa
`pointerInput(song.youtubeId, index, trackEntities)`, así que el gesto ya se
reinicia al cambiar la pista, su índice o el set de pistas.

Y un tercero, en la propia mecánica del gesto (**B8**, ~~resuelto~~ → §10):
~~cada evento de drag lanzaba una corrutina que hace `offsetX.snapTo(...)`.
`Animatable` serializa con `MutatorMutex`, de modo que las corrutinas lanzadas se
cancelan entre sí y el desplazamiento acumulado se pierde. Además `onDragEnd`
evaluaba el umbral dentro de una corrutina, con el `snapTo` pendiente aún en
vuelo, así que la comparación se hacía sobre un valor que aún no es el final.~~ El
offset ahora se acumula de forma síncrona y `onDragEnd` lee el valor real.
Juntas, producían exactamente el "a veces sí, a veces no" del swipe.

### 1.2 La canción carga dos veces → tu hipótesis es correcta, y había dos causas (~~B4~~, ~~B5~~, **resueltos** → §10)

**Causa 1: la invalidación de la URL caducada casi nunca se ejecutaba.**
Cuando el reproductor falla, `onItemUnplayable` intenta tirar la URL de la caché:

```kotlin
val expiredUrl = isHttpStatusError(error)
if (expiredUrl) {
    currentVideoId?.let { YouTubeManager.invalidate(it) }
}
```

Pero `currentVideoId` se sobrescribía con `queue[i].youtubeVideoId` en
`syncIndexFromWindow` y en `setCurrentIndex`, y ese campo es **`null` para toda
pista que no venía con id de YouTube de origen** — o sea, casi todas, porque las
que se resuelven por búsqueda guardan el id solo en memoria. Solo `startAt` lo
reponía con el id realmente resuelto. En la práctica, cuando saltaba de canción
el valor volvía a `null`, la invalidación era un no-op, y el
`playIndex(reResolve = true)` posterior volvía a pedir la URL... que
`getAudioUrl` le devolvía **de la caché, la misma URL muerta**. El `MediaItem`
nuevo fallaba otra vez, la cola saltaba, y el ciclo se repetía hasta agotar
`MAX_CONSECUTIVE_FAILURES`.

Ahora hay un `ConcurrentHashMap<String, String>` en `PlayerViewModel` con el
video **realmente usado** por cada pista, indexado por `track.id`: se rellena
tanto en `startAt` como en `resolveItems`, y tanto `syncIndexFromWindow` como
`setCurrentIndex` leen de ahí primero. `invalidate()` recibe por fin el id
correcto. Además `getAudioUrl` acepta `forceRefresh`, que salta la caché, y
`resolveItems` lo propaga en el camino `reResolve = true`: el reintento ya no
puede recibir la URL que acaba de morir. De paso, `resolveItems` reutiliza el
video ya resuelto para esa pista en vez de volver a buscarlo en YouTube.

**Causa 2: no había deduplicación de extracciones en vuelo.** `getAudioUrl` no
tenía *single-flight*: dos llamadas concurrentes del mismo `videoId` lanzaban
dos extracciones de red y la segunda sobrescribía a la primera en la caché. Y eso
ocurría en cada transición, porque `growWindow()` se llamaba **dos veces por
salto**:

- `onMediaItemTransition(reason = AUTO)` → `growWindow()`
- `onTrackEnded()` → `playIndex()` → `growWindow()`

y en la carga inicial, `onMediaItemTransition` (por el `setMediaItem` de
`startAt`) más el `growWindow()` explícito de `startAt`. Cada llamada hacía
`prefetchJob?.cancel()` y relanzaba, y como la extracción es
`withContext(Dispatchers.IO)` sobre OkHttp/NewPipe, **no es interrumpible**: la
corrutina cancelada seguía extrayendo hasta el final pero ya no rellenaba la
caché a tiempo, así que la relanzada volvía a empezar de cero. Dos extracciones,
misma canción, misma transición.

`YouTubeManager` mantiene ahora un registro de extracciones en vuelo por
`videoId` con un `CompletableDeferred`: la primera corre y las demás esperan su
resultado, así que ocho peticiones simultáneas del mismo video hacen **una**
extracción. Y `growWindow()` ya no relanza un relleno idéntico que está en
marcha (`prefetchRange`): con la extracción indestructible, duplicarla era
además una forma de **añadir dos veces los mismos items a la ventana**, no solo
de repetir el trabajo de red.

### 1.3 Añadir canciones a una lista → sí falla, por **cinco** mecanismos distintos

Tu intuición era correcta, y el problema era peor de lo que recuerdas: **no había
ninguna ruta de "añadir canciones a una lista" que funcionase de principio a
fin**. Hay cinco, y las cinco estaban rotas o eran no-ops. **Las cinco están ya
resueltas** (B2, B3, B13, B14, B15/B16 → §10); lo que sigue es el análisis del
estado que se encontró. Detalle por mecanismo:

#### 1. El swipe "añadir a playlist" no hace nada (no-op puro) — **B2** (~~resuelto~~ → §10)

`ConfigScreen.kt` ofrece `swipe_action_playlist` como opción válida de swipe
izquierda **y** derecha, y `SongListItem.kt:57` le dibuja el icono `≡`, pero la
implementación era solo un log (`SongListItem.kt:449-451`):

```kotlin
Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> {
    Log.d("SongListItem", "Add to playlist (no-op): ${song.title}")
}
```

Ni diálogo, ni escritura, ni navegación. Encima, el parámetro
`onShowPlaylistDialog` que recibe la función **estaba cableado a `{}` en las dos
ramas del gesto** (`SongListItem.kt:171` y `:188`), así que la única vía por la
que el diálogo podía abrirse estaba muerta antes de empezar. Si en Ajustes
configuraste esa acción, llevabas meses viendo un icono que no hacía nada.

Ahora la rama llama a `onShowPlaylistDialog()`, las dos ramas del gesto la
cablean a un diálogo de selección de playlist, y el diálogo lista las playlists
locales (excluyendo *liked* y los álbumes) y añade la canción con
`addTrackToYouTubePlaylist`, que ya deduplica (B13). Si no hay ninguna playlist,
lo dice con `no_playlists`; el log de "no-op" ha desaparecido.

#### 2. El `+` del modo edición escribe en la base de datos pero no recarga la lista — **B3** (~~resuelto~~ → §10)

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

#### 3. `addTrackToYouTubePlaylist` no deduplica — **B13** (~~resuelto~~ → §10)

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

| Ruta | Síntoma que veía el usuario | Ahora |
|---|---|---|
| Swipe "añadir a playlist" (Ajustes) | Nada. Un icono `≡` inerte. | Diálogo de selección y añade (B2) |
| `<edit>` → buscar → `+` | La búsqueda se vacía y la canción no aparece. | La lista se recarga (B3) |
| `<edit>` → `x` para quitar | La fila no desaparece. | La fila desaparece (B3) |
| Crear playlist → `+` | Hay que repetir la búsqueda por cada canción. | La búsqueda no se borra (B14) |
| Crear playlist → `<create>` | Faltan canciones, sin ningún aviso. | Avisa cuántas y cuáles (B15/B16) |
| `<guardar>` en detalle de playlist de YouTube | **Este sí funcionaba**: cambia a `<saved>` (`:176-180`). Única ruta con feedback. | Sin cambios |

**La buena noticia:** la escritura en la base de datos era correcta en las cinco
rutas. Los datos sí se guardaban. Era la capa de UI la que mentía — no refrescaba,
no avisaba, y en dos casos directamente no ejecutaba nada. Por eso la mayoría se
arreglaron con cambios de estado y de refresco, sin tocar el esquema ni la lógica
de persistencia. Excepción: B13 necesitó una comprobación de duplicado por
identidad en `addTrackToYouTubePlaylist`, y B15/B16 que
`YouTubePlaylistCreator.build` pasase a ser `suspend` con cancelación y reporte
de descartes.

### 1.4 Estado de la deuda

- **4 bugs activos, B49–B51 y B53**, los cuatro reportados, todos de severidad **alta**.
  **B54** (el diálogo de compartir se abría en blanco), **B55** y **B56** (la
  notificación que decía "Plyr / Reproduciendo" al terminar la cola y al abrir
  la app sin música) aparecieron al documentarlos y ya están **resueltos**:
  B55 + B56 en la undécima tanda y B54 en la duodécima (→ §10). Los 48 de
  las tandas anteriores siguen resueltos. Detalle en §3.
- **2 peticiones abiertas** de funcionalidad/comportamiento (§11): F1 (`liked`
  vacía no debería aparecer) y F2 (qué URL se comparte, que es B52 + B53).
  **F3** ("añadir a lista" en el menú `*`) está **resuelta** en la décima tanda
  (→ §10): era la más fácil de las tres, solo una entrada de menú.
- **4 bugs activos** (B49–B51, B53). La décima tanda (2026-10-01) solo implementó F3;
  la undécima resolvió B55 + B56 y la duodécima B54.
- **347 tests unitarios** en 26 archivos (`./run.sh test`; todos en verde tras la
  undécima tanda, que añadió los 8 de `PlaybackNotificationStateTest`).
- **0 instrumentados** útiles (solo `ExampleInstrumentedTest`).
- **Código muerto: el que la auditoría listeó, borrado en T9** (`SongMenuDialog`,
  `CollapsibleSection`, `PlyrDimensions`, `loadPlaylists`,
  `QueueIndex.needsRefillAfterEnd` y 3 constantes de `PlyrSymbols`). Queda lo
  pequeño: previews de Android Studio, `ActionButtonData.enabled` y los campos
  muertos de `ResponsiveDimensions` (§7).
- **0 claves de traducción sin uso** y **0 claves referenciadas que no existen**
  (§7.1), con dos tests que lo garantizan. Queda la lista de literales sueltos
  fuera de `Translations`, que es refactor y no bug.
- R8 activo en release y **verificado**: `./run.sh build release` termina en
  `BUILD SUCCESSFUL` (APK de 4,9 MB sin firmar, porque no hay keystore local).

---

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 71 (~14.975 líneas) |
| Archivos de test | 26 (~4.003 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1403), `PlayerViewModel.kt` (786), `ConfigScreen.kt` (665), `SongListItem.kt` (580), `FloatingMusicControls.kt` (538), `SearchScreen.kt` (483), `PlaylistLocalRepository.kt` (445), `Translations.kt` (437) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7, migraciones `5→6` y `6→7` |
| Tests unitarios | **347** en 26 archivos (ejecutados y en verde) |
| Tests instrumentados útiles | 0 |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso | **0** (antes 22; `TranslationKeysUsageTest`) |
| Claves referenciadas que no existen | **0** (antes 1: `"Player not available"`) |
| Build release | **BUILD SUCCESSFUL** (R8 + lint), APK 4,9 MB sin firmar |

**Desglose de los 347 tests** (tras `./run.sh test`, todos en verde):

| Archivo | @Test | Archivo | @Test |
|---|---|---|---|
| `UrlParserTest` | 33 | `AudioUrlExtractionTest` | 9 |
| `ImportManifestTest` | 30 | `MediaButtonCommandTest` | 9 |
| `CoverCropMathTest` | 24 | `AppModelsTest` | 8 |
| `ExportManifestTest` | 24 | `ModelDefaultsTest` | 7 |
| `SpotifyImporterTest` | 23 | `CoverCacheTest` | 6 |
| `QueueIndexTest` | 18 | `BackupFolderTest` | 5 |
| `DatabaseMappingsTest` | 21 | `TranslationsTest` | 5 |
| `SimpleDownloaderLogRedactionTest` | 18 | `SupabaseClientTest` | 10 |
| `UtilsTest` | 17 | `ImportArchiveTest` | 14 |
| `YouTubePlaylistCreatorTest` | 17 | `NewPipeHolderTest` | 3 |
| `NfcPulseTest` | 11 | `YouTubeFormattingTest` | 11 |
| `ExportDigestTest` | 11 | `ExampleUnitTest` | 1 |
| `PlaybackNotificationStateTest` | 8 | `TranslationKeysUsageTest` | 4 |

---

## 3. BUGS ACTIVOS

**4** (B49–B51, B53). Los 52 de las tandas anteriores siguen resueltos (§10).

- Batch del **2026-10-01**, sin arreglar: **B49**–**B53**, los cinco reportados.
  Todos verificados **leyendo el código** (ver la nota de la cabecera).
- Severidad: los cinco **altos**.
- **Resueltos ya de este bloque:** ~~**B54**~~ (el diálogo de compartir se abría en
  blanco, duodécima tanda), ~~**B55**~~ y ~~**B56**~~ (la notificación que decía
  "Plyr / Reproduciendo" al terminar la cola y al abrir la app sin música,
  undécima tanda). Quedan tachados en §3.3 y §3.4, con lo hecho en §10.
- Los de reproducción (B49, B50) y el de datos (B51) tienen el mismo origen de
  fondo: la **ventana deslizante** de `PlayerViewModel` es la que posee el estado
  real del reproductor, y ni `MusicService` ni la copia de seguridad la tienen en
  cuenta (§6, nota de `MediaSession`).

### 3.1 Críticos

Ninguno. La reproducción tenía errores reales (§3.2) pero todos degradaban o se
recuperaban solos; ninguno corrompía datos de forma irreversible salvo B1 (que sí
tocaba la tabla de favoritos, y por eso estaba en altos). **B51 sí toca datos**,
pero es recuperable a mano (volver a quitar el favorito y volver a sincronizar
con el ZIP ya borrado), así que queda en altos y no en críticos.

### 3.2 Altos

**4 activos.**

#### B49 — El botón de anterior solo reinicia la canción y no vuelve a la anterior

**Reportado:** al darle, la canción vuelve al principio; a la anterior no llega.

**Ubicación:** `PlayerViewModel.kt:249-257` (`navigateToPrevious`) ·
`QueueIndex.kt:19,45-57` (`previousIndex`) · `PlayerViewModel.kt:421-503`
(`playIndex`) · `:566-573` (`trimWindow`) · `MusicService.kt:68-92` (sin
callback de sesión).

El síntoma tiene cuatro causas que se suman. Las tres primeras son de
`PlayerViewModel`; la cuarta es de la notificación.

1. **El umbral de 3 s es intencionado y funciona** (`QueueIndex.kt:19,52`):
   si `positionMs > 3_000`, `previousIndex` devuelve el índice actual y
   `navigateToPrevious` responde con `seekTo(0L)` (`:252-254`). Es el
   comportamiento de cualquier reproductor y está certificado por
   `QueueIndexTest.previous_siLaCancionHaEmpezado_reinicia`. **No es el bug**,
   pero condiciona todo lo demás: como desde cualquier posición de escucha la
   primera pulsación solo reinicia, y la segunda es cara (causa 2), el botón
   *parece* no ir nunca hacia atrás.
2. **Volver a la anterior solo es instantáneo si la anterior sigue en la ventana;
   cuando no lo está, hay que re-resolverla por red.** `trimWindow()`
   (`:566-573`) borra los items ya superados en **cada** transición, y en las
   automáticas eso deja `windowStart == currentIndex` exactamente: la canción
   anterior ya no está preparada. Entonces `playIndex` calcula
   `windowPosition = (currentIndex - 1) - windowStart < 0` (`:429-436`) y cae en
   la ruta asíncrona: `transitionInFlight = true` y `resolving = true` →
   `_isLoading` pasa a `true` → **los tres botones de reproducción quedan
   deshabilitados** (`FloatingMusicControls.kt:431,441,453`) y una segunda
   pulsación se ignora en silencio (`:422`). Entre la pulsación y el salto hay una
   búsqueda en YouTube **y** una extracción de URL por canción
   (`resolveItems`, `:584-625`): segundos de spinner con los controles muertos.
   - Y si esa canción no se resuelve, el bucle `while (resolved.isEmpty())` de
     `playIndex` (`:455-473`) **salta hacia delante** buscando la siguiente que sí
     (`candidate = QueueIndex.nextIndex(...)`), así que un "atrás" acaba sonando
     una canción distinta, sin avisar.
   - O sea: el `<<` funciona de verdad solo en el caso que ya no ocurre al
     escuchar una lista (transición automática), y en el resto depende de una
     resolución por red con los controles apagados.
3. **En la primera posición no hace nada.** Con la repetición apagada,
   `previousIndex(0, …)` devuelve `null` (`QueueIndex.kt:55`) y
   `navigateToPrevious` hace `return`: ni hacia atrás ni reinicio, incoherente
   con el caso de >3 s.
4. **El `<<` de la notificación no pasa por `QueueIndex`.** `MusicService` no
   registra ningún `MediaSession.Callback` (§6), así que
   `COMMAND_SEEK_TO_PREVIOUS` lo mueve ExoPlayer directamente. Como la ventana no
   tiene items anteriores (`trimWindow`), `hasPreviousMediaItem()` es `false`:
   el botón no hace nada, o el sistema directamente no lo muestra.

#### B50 — El botón de siguiente desaparece de la notificación tras dos skips seguidos

**Reportado:** al skipear dos canciones seguidas, el botón de siguiente de la
notificación de Android desaparece.

**Ubicación:** `MusicService.kt:85-89,109-112` (la notificación solo se
reconstruye en `onMediaItemTransition`) · `PlayerViewModel.kt:421-436`
(`playIndex` en ventana) · `:509-560` (`growWindow`, cancelación del relleno) ·
`:566-573` (`trimWindow`).

1. **La notificación solo se refresca en los cambios de pista.**
   `MusicService` añade un único `Player.Listener` y solo implementa
   `onMediaItemTransition` (`:85-89`), que es lo único que llama a
   `updateNotification` (`:109-112`). No hay reacción a `onTimelineChanged`, ni
   al play/pause, ni al final de la cola: lo que no pase por una transición se
   queda congelado en la notificación.
2. **El reproductor solo tiene 3 items preparados** (`WINDOW_AHEAD = 2`,
   `PlayerViewModel.kt:51`, más el actual) y `trimWindow()` (`:566-573`) quita
   uno por cada salto. En la ruta "el destino está en la ventana",
   `growWindow()` se llama **antes** de que el `seekTo` se aplique (`:432-434`):
   `player.currentMediaItemIndex` sigue siendo el índice anterior, `trimWindow`
   no recorta y `missing` sale 0, así que el **primer** skip solo lanza un
   relleno anticipado (`3-3`).
3. **El segundo skip sí recorta y mata el relleno en vuelo.** El índice ya está
   actualizado, `trimWindow` quita el item anterior (`windowStart` avanza) y el
   rango pedido pasa de `3-3` a `3-4`, que ya no coincide con `prefetchRange`:
   `prefetchJob?.cancel()` (`:536`) mata la extracción en marcha y lanza otra
   (`3-4`). **Entre el recorte y el `addMediaItems` del relleno al reproductor
   solo le queda el item actual**, así que `hasNextMediaItem()` es `false` y la
   notificación, que la transición ya ha reconstruido, **oculta el botón de
   siguiente**. Por eso hace falta **dos** skips seguidos: es el segundo el que
   produce el hueco.
4. **El botón no vuelve cuando llega el relleno.** `_exoPlayer?.addMediaItems(contiguous)`
   (`:555`) solo dispara `onTimelineChanged`, que nadie escucha para la
   notificación. El botón reaparece en la siguiente transición; con la app
   cerrada no hay ninguna, así que **desde la notificación no se puede avanzar**
   (el `>>` de la app sí funciona, porque va por `QueueIndex`, `navigateToNext`
   `:244-247`).
5. **Si el relleno falla, el hueco es permanente.** `contiguous.isEmpty()` →
   `return@launch` (`:553`) y la ventana no vuelve a crecer hasta la siguiente
   transición. Es el caso normal en las listas importadas de Spotify, donde
   muchas pistas se guardan con `youtubeVideoId = null`
   (`SpotifyImporter.kt:145-159`) y hay que resolverlas por búsqueda: si esa
   búsqueda falla, el botón de siguiente desaparece aunque la cola tenga
   canciones, y no hay `growWindow()` que lo intente otra vez hasta que cambie
   de pista.

#### B51 — El sync vuelve a aplicar los favoritos que se han borrado en la app

**Reportado:** al darle a sync se aplican los guardados del ZIP aunque se hayan
borrado de la app. **Solo lo ha notado en *liked songs*.**

**Ubicación:** `DataSync.kt:191,259-292` (la fusión va **antes** de escribir, en
cada sync) · `DataImporter.kt:93-100,180-185` · `ImportManifest.kt:155` ·
`PlaylistLocalRepository.kt:294-323` (`mergeLikedSongsTracks`) ·
`PlaylistLocalRepository.kt:271-275` + `Config.kt:383-389` (tomb de *listas*).

1. **Cada sync fusiona la copia de la carpeta antes de escribir**
   (`DataSync.flush` → `mergeArchiveFromFolder`, `DataSync.kt:191`), tanto desde
   el botón de Ajustes (`force = true`) como en el volcado automático. La fusión
   es una **unión**: no compara lo que había en el ZIP con lo que hay ahora en la
   app, así que no hay forma de que un borrado local gane.
2. **`liked_songs` nunca se sobrescribe y la fusión solo añade.**
   `ImportManifest.plan` marca `liked_songs` siempre como `MergeLikedSongs`
   (`ImportManifest.kt:155`) y `mergeLikedSongsTracks`
   (`PlaylistLocalRepository.kt:294-323`) inserta `fresh = tracks.filter { known.add(...) }`:
   lo que el usuario quitó ya no está en `known`, así que **se reinserta** con un
   id nuevo (`liked_songs_<remoteTrackId>_<position>`, `:309`).
3. **El borrado sí viaja para las listas, y por eso solo se nota en favoritos.**
   Para una lista borrada hay tomb (`Config.addDeletedPlaylistId`,
   `PlaylistLocalRepository.rememberDeletion` `:271-275`) que viaja en
   `deletedPlaylistIds` del manifiesto; y para las pistas *de una lista*, el
   borrado se respeta sin más porque las listas que ya existen se saltan
   (`PlaylistAction.Skip(ALREADY_EXISTS)`). **No existe nada equivalente para las
   pistas de `liked_songs`**: ni tomb, ni digest de favoritos, ni nada que
   distinga "favorito que el ZIP no conoce" de "favorito que este dispositivo
   borró a propósito".
4. **El resurreto se consolida en el propio sync.** `mergeLikedSongsTracks`
   llama a `markDirty()` (`:321`) y el `flush` que lo provocó escribe después el
   estado **ya fusionado** (`DataSync.kt:193-206`), así que la canción vuelve
   también al ZIP: no es un fallo de una sincronización, es un borrado que ya no
   se puede propagar nunca.

> **Aviso de diseño para el arreglo:** la fusión aditiva es justo lo que
> permite que una instalación nueva recupere la copia buena en vez de pisarla
> (`DataSync.kt:88-97`), así que la solución no puede ser "el archivo gana" sin
> más. Las dos vías limpias son un **tomb de favoritos por clave**
> (`youtubeVideoId`, o nombre+artista con `ImportManifest.fallbackDedupeKey`)
> que viaje en el manifiesto como campo aditivo, igual que `deletedPlaylistIds`;
> o un **digest de `liked_songs`** en `ExportDigest` que permita distinguir las
> dos situaciones. Ambas exigen tocar `ExportManifest` + `ImportManifest` +
> `ExportDigest` y sus tests (`ExportManifestTest` 24, `ImportManifestTest` 30,
> `ExportDigestTest` 11).

#### B52 — El share de una canción comparte un id que no es el de YouTube — **RESUELTO** (treceava tanda)

**Reportado:** el share está roto con canciones importadas de Spotify; debería
compartir la canción de YouTube, no el id de Spotify.

**Arreglo** (treceava tanda → §10): la decisión de qué URL se comparte se movió
a `ShareUrlPolicy`, un objeto puro, y ahora **gana el id real de YouTube**; una
URL ya montada solo se usa cuando no hay id. Además `PlaylistScreen.kt:799,862`
dejan de montar la URL con `track.id`. Los tres puntos del diagnóstico
siguiente se cumplen y el efecto colateral (NFC y feed de recomendaciones) queda
arreglado por ser la misma variable. Detalle y pruebas en §10.

**Ubicación:** `PlaylistScreen.kt:799,862` (construcción de la URL) ·
`SongListItem.kt:402-413` · `QRDialog.kt:115-128` (precedencia) ·
`DatabaseExtensions.kt:17-26` (`AppTrack.id` = `remoteTrackId`) ·
`SpotifyImporter.kt:137,151`.

1. **La URL se construye con el id equivocado.** `PlaylistScreen.kt:799` y
   `:862` hacen `shareUrl = "https://www.youtube.com/watch?v=${track.id}"`, pero
   `track` es un `AppTrack` y `AppTrack.id` es `TrackEntity.remoteTrackId`
   (`DatabaseExtensions.toAppTrack`, `:19`). En las listas importadas de Spotify
   ese campo es `spotify_<hashTitulo>_<hashArtistas>_<índice>`
   (`SpotifyImporter.kt:137,151`), así que lo que se comparte —QR, texto y NFC— es
   `https://www.youtube.com/watch?v=spotify_1234567_-987654_3`, un enlace que no
   existe. En favoritos y en las listas creadas a mano pasa igual: ahí el
   `remoteTrackId` es el título de la canción o un id sintético.
2. **El id correcto ya viaja y se descarta.** Las dos llamadas pasan también
   `youtubeId = track.youtubeVideoId` (`:798`, `:861`), pero `ShareDialog` da
   prioridad a `shareUrl` (`QRDialog.kt:115`, `item.shareUrl ?: when { … }`), así
   que el `youtubeId` **nunca** se usa cuando hay `shareUrl`. Basta con dejar
   `shareUrl = null` y construir la URL en el diálogo (o al revés) para que el
   valor bueno llegue.
3. **Solo funciona donde el id sí es de YouTube.** Los resultados de búsqueda
   (`SearchScreen.kt:453-454`, `YouTubeSearchResults.kt:226-227`,
   `YouTubePlaylistDetailView.kt:232-233`, `PlaylistScreen.kt:724-725`,
   `:1247-1248`, `:1298-1299`) construyen la URL con el `videoId` real, y ahí el
   share es correcto. Las rutas rotas son exactamente las dos que leen pistas de
   la base de datos.
4. **El efecto no se queda en el diálogo:** la misma URL rota se escribe en el
   tag NFC (`QRDialog.kt:145-155`) y se sube a Supabase como recomendación
   (`QRDialog.kt:318-323`), así que el feed público recibe enlaces que no abren.

#### B53 — El share de una lista construye una URL inválida

**Reportado:** con playlist importada de Spotify el share está roto; debería
compartir la URL de Spotify.

**Ubicación:** `PlaylistScreen.kt:951-964` (qué se pasa) · `:392-400` (el botón
se muestra siempre) · `QRDialog.kt:118-126` (la heurística de prefijos) ·
`SpotifyImporter.kt:76,168,171` (el `remoteId` y la marca de origen).

El diálogo recibe `youtubeId = selectedPlaylist.id.removePrefix("youtube_")` y
decide con prefijos: si empieza por `PL`/`UU`/`FL`/`RD` arma una
`youtube.com/playlist?list=…`, y si no, una `youtube.com/watch?v=…`
(`QRDialog.kt:118-126`). Con lo que hay hoy en la base de datos:

| Lista | `remoteId` | URL que se comparte | ¿Correcta? |
|---|---|---|---|
| Playlist real de YouTube | `youtube_PLxxxx` | `youtube.com/playlist?list=PLxxxx` | sí, y solo por el prefijo |
| **Importada de Spotify** | `youtube_<idSpotify22>` | `youtube.com/watch?v=<idSpotify22>` | **no** — debería ser `open.spotify.com/playlist/<id>` |
| **Favoritos** | `liked_songs` | `youtube.com/watch?v=liked_songs` | **no** — no hay nada que compartir |
| Creada en la app | `youtube_yt_<timestamp>` | `youtube.com/watch?v=yt_1759…` | **no** — id interno |

- El único rastro de que una lista viene de Spotify es
  `description = "Imported from Spotify"` (`SpotifyImporter.kt:171`), porque el
  id se guarda con el prefijo `youtube_` (`:76,168`): no hay ninguna columna que
  diga "origen". Y la URL que debería compartirse es justo la que la propia app
  sabe leer (`UrlParser.parseScanText` reconoce `spotify.com/playlist/<id>` →
  `ScanResult("spotify", "playlist", id)`, `UrlParser.kt:52-56`).
- El botón `<share>` se añade siempre que `!isEditing`, sin mirar qué lista es
  (`PlaylistScreen.kt:392-400`), así que la lista de favoritos también ofrece
  compartir y produce un QR a una página inexistente.

Referencia histórica: ~~**B1**~~, ~~**B2**~~, ~~**B4**~~, ~~**B5**~~ y ~~**B6**~~
se resolvieron en la séptima tanda (→ §10). Se conservan aquí las filas
originales como referencia:

| # | Ubicación | Descripción |
|---|---|---|
| **B1** | `PlaylistScreen.kt:721,794,856` → `SongListItem.kt:425` → `PlaylistLocalRepository.kt:61-63` | **El swipe a "liked" borra la canción que se había marcado antes, y desde la lista *Liked* no se puede quitar nada.** `PlaylistScreen` no pasaba `youtubeId`, así que `executeSwipeAction` mandaba `""` a `toggleLikeTrack`, que compara por igualdad exacta. La 1.ª llamada guarda una pista con `youtubeVideoId=""`; la 2.ª, sobre otra canción, la encuentra y la **borra**. Dentro de la lista *Liked*, el corazón del popup es un no-op silencioso (id nulo) y el swipe no encuentra la fila real (su id no es `""`), así que **creaba una fila fantasma en vez de quitar** la canción visible. `isTrackLiked("")` salía `true` para todo. Detalle completo en §1.1. |
| **B2** | `SongListItem.kt:449-451` + `:171,:188` + `ConfigScreen.kt` | **La acción de swipe "añadir a playlist" era un no-op.** Es una opción seleccionable en Ajustes para ambos lados y dibuja el icono `≡`, pero la implementación solo hacía `Log.d(...)`. El parámetro `onShowPlaylistDialog` estaba cableado a `{}` en las dos ramas del gesto, así que no existía ninguna vía de diálogo. Detalle en §1.3. |
| **B4** | `PlayerViewModel.kt` (`onItemUnplayable`, `syncIndexFromWindow`, `setCurrentIndex`) | **La invalidación de URL caducada era un no-op.** `currentVideoId` se rellenaba con `queue[i].youtubeVideoId` (null para toda pista resuelta por búsqueda), así que `YouTubeManager.invalidate()` casi nunca se llamaba y el re-intento recibía de la caché **la misma URL muerta**. Detalle en §1.2. |
| **B5** | `YouTubeManager.kt` (`getAudioUrl`) + `PlayerViewModel.kt` (`growWindow`) | **Doble extracción por transición.** `getAudioUrl` no deduplicaba en vuelo y `growWindow()` se invocaba dos veces por salto, con `prefetchJob?.cancel()` + relanzamiento. La extracción cancelada (OkHttp sobre `Dispatchers.IO`, no interrumpible) no llegaba a escribir caché y la relanzada repetía el trabajo. Es la "carga doble" que se oye. |
| **B6** | `PlayerViewModel.kt` (`onItemUnplayable`, `playIndex`) | **`_error` nunca se limpiaba en la ruta de error.** `_error.publish(null)` solo existía en `startAt` y `clearPlayerState`. Tras un `onPlayerError` recuperable, el estado de error se quedaba pegado para siempre: los controles quedaban deshabilitados y el texto de error no desaparecía aunque la canción volviera a sonar. |

### 3.3 Medios

**Ninguno activo.** ~~**B54**~~ (el diálogo de compartir se abría en blanco) se
resolvió en la duodécima tanda (→ §10).

#### ~~B54~~ — El diálogo de compartir se abre vacío cuando no hay URL — **RESUELTO (duodécima tanda)**

**Encontrado** al verificar B52/B53. **Resuelto en la duodécima tanda** (→ §10).

**Ubicación (antes del arreglo):** `QRDialog.kt:115-128,225,258,302` ·
`QueueScreen.kt:60-67` · `PlayerViewModel.kt:114` (dónde está el id bueno) ·
`SpotifyImporter.kt:145-159`.

`ShareDialog` solo dibuja el QR, el `<share>`, el NFC y el `<recomendar>` cuando
`shareUrl != null` (`QRDialog.kt:225,258,302`). Si es `null`, lo que queda es un
`Card` con padding: **un diálogo en blanco**, sin QR, sin botón de compartir y sin
ningún mensaje que explique por qué. No hay estado de error para ese caso.

- **Se llega desde la cola**, que es el único sitio donde `shareUrl` **y**
  `youtubeId` acaban a la vez en `null`: allí se pasa `shareUrl = null` a
  propósito (`QueueScreen.kt:66`, con el comentario *"TrackEntity no tiene
  shareUrl"*) y `youtubeId = track.youtubeVideoId`, que es `null` en
  cualquier pista guardada sin coincidencia en YouTube — o sea, buena parte de
  las listas importadas de Spotify (`SpotifyImporter.kt:145-159` guarda
  `youtubeVideoId = null` cuando la búsqueda falla) y de los favoritos hechos
  desde ellas.
  Los otros tres `shareUrl = null` del repo no llegan aquí: `ConfigScreen.kt:634`
  es `ShareType.APP` (cae en el enlace de GitHub), y
  `YouTubePlaylistDetailView.kt:255` y `PlaylistScreen.kt:956` pasan siempre
  `youtubeId` no nulo.
- **El id que sí sirve no está a mano.** La pantalla de cola construye su `Song`
  desde el `TrackEntity` de la base de datos, pero el vídeo que está sonando se
  resolvió en memoria: vive en `PlayerViewModel.resolvedVideoId`
  (`PlayerViewModel.kt:114`), que `QueueScreen` no consulta. O sea, el dato
  correcto existe, pero no llega al diálogo.

**Arreglo (duodécima tanda):** `ShareDialog` tiene ya un estado explícito para
"no hay nada que compartir": cuando `shareUrl == null` pinta un mensaje
(`no_share_url`, nuevo en los 4 idiomas) y un botón de cerrar, en vez de dejar el
`Card` con padding y nada dentro (`QRDialog.kt:225-248`). **El diálogo ya nunca
puede abrirse en blanco.** El resto de la caja (QR, `<share>`, NFC,
`<recomendar>`) sigue igual, porque todo eso depende de que haya URL.

Lo que **no** se arregla aquí, a propósito: el `id` bueno sigue sin llegar desde
la cola. Usar `PlayerViewModel.resolvedVideoId` para entonces es un arreglo de
otro tipo (pasarle el id resuelto a la pantalla de cola), y pertenece al bloque de
compartir de B52/B53, donde además hay que decidir **qué** URL se comparte. Aquí
solo se hace que, cuando no hay URL, la app **lo diga** en vez de fingir que sí.

~~**B32**~~ (`SimpleDownloader.kt:32`, `:49-59`, `:61`) —el mapa de cookies era
un `mutableMapOf` plano leído desde todos los hilos de red, y se logueaban
cookies (`:101`) y cabeceras completas (`:113-117`), que pueden incluir
`Authorization`— se resolvió en la octava tanda: `ConcurrentHashMap`, cookies
redactadas por nombre, valores sensibles de cabecera ocultos y ningún volcado del
cuerpo de respuesta (→ §10).

~~**B48**~~ (`PlaylistScreen.kt` + `SongListItem.kt`) estaba aquí: las filas de
*Liked* salían de un snapshot que no se recargaba con el toggle. Resuelto en la
séptima tanda junto con B1 (→ §10).

### 3.4 Bajos

**Ninguno activo.** ~~**B56**~~ y ~~**B55**~~ (la notificación que miente: al
terminar la cola y al abrir la app sin música) se resolvieron juntos en la
undécima tanda (→ §10).

#### ~~B56~~ — La notificación "Plyr / Reproduciendo" aparece aunque no suene nada — **RESUELTO (undécima tanda)**

**Reportado** (2026-10-01): "salta una notificación que dice algo de Plyr is
playing music". **Encontrado** al explicar B55. **Resuelto en la undécima
tanda** (→ §10).

**Ubicación:** `MainActivity.kt:83-86` · `MusicService.kt:45-65`
(`createStartupNotification`) · `MusicService.kt:95-107` ·
`PlayerViewModel.kt:87,184,396,492`.

Es el mismo síntoma que B55 pero por un motivo distinto, y **no es un fallo del
servicio: es el arranque**.

1. `MainActivity.onCreate` arranca el servicio **siempre**, sin mirar si hay
   música: `startForegroundService(it)` (`MainActivity.kt:83-86`). O sea, el
   servicio se promueve a foreground en cuanto se abre la app.
2. `startForegroundService` **exige** una notificación en los primeros 5 s
   (`ForegroundServiceStartNotAllowedException` si no), así que `onStartCommand`
   publica una notificación provisional (`MusicService.kt:52-54`,
   `createStartupNotification` `:58-65`) que dice literalmente **"Plyr" /
   "Reproduciendo"** — sin canción y sin `MediaStyle`.
3. Esa provisional **solo se sustituye** cuando el `PlayerViewModel` invoca
   `onMediaSessionUpdate` (`PlayerViewModel.kt:184,396,492`), y eso ocurre
   **al empezar a sonar una pista** (o al saltar). Con la app abierta y nada
   sonando, la provisional se queda ahí **con `.setOngoing(true)`** (`:63`).
4. La `MediaSession` real solo se crea en `setupMediaSession`
   (`MusicService.kt:68-92`), que MainActivity engancha en `onServiceConnected`
   (`MainActivity.kt:58-60`): o sea, tampoco ayuda hasta que hay una pista.

**Consecuencia para el usuario:** abrir la app sin poner música ya deja una
notificación persistente que dice "Plyr / Reproduciendo". Si luego no se reproduce
nada, ahí se queda.

**Arreglo (undécima tanda):** la provisional es **obligatoria** (es lo que
mantiene el servicio en foreground y evita el crash), así que **no se quita**: lo
que cambia es que ya no afirma que hay música sonando. Se pinta con el estado
"idle" (`PlaybackNotificationState.idle`), o sea título = nombre de la app, **sin
`setOngoing` y sin `MediaStyle`**: no dice "Reproduciendo" y el usuario puede
descartarla. Cuando arranca una pista, `setupMediaSession` la sustituye por la
real. Detalle en §10. **Se arregla junto con B55**, que es el mismo
`createNotification` cayendo en los valores por defecto.

#### ~~B55~~ — Al terminar la cola la notificación "Plyr" se queda puesta — **RESUELTO (undécima tanda)**

**Encontrado** al verificar B50. **Resuelto en la undécima tanda** (→ §10).

**Ubicación (antes del arreglo):** `PlayerViewModel.kt:687-697`
(`stopAtQueueEnd`) · `MusicService.kt:85-89,97-98,109-112` ·
`MainActivity.kt:190-196`.

Cuando la cola se acaba, `stopAtQueueEnd()` hace `player.stop()` +
`clearMediaItems()` (`:689-691`). Eso dispara `onMediaItemTransition(null)`, y el
listener de `MusicService` reconstruye la notificación **con
`currentMediaItem == null`**, así que cae en los valores por defecto: título
`"Plyr"` y texto `"Reproduciendo"` (`MusicService.kt:97-98`) sobre un
`MediaStyle` sin items. Y como la notificación se construye con
`.setOngoing(true)` (`:104`), **no se va sola**: queda una notificación
permanente, sin canción y sin controles, que solo desaparece al cerrar la app
desde recientes.

**Arreglo (undécima tanda):** `updateNotification` ya no reconstruye la
notificación cuando no hay item en curso: hace `stopForeground(STOP_FOREGROUND_REMOVE)`
y no vuelve a pintar nada (`MusicService.kt:129-138`). La `MediaSession` **no** se
libera, así que el siguiente item repinta la notificación con normalidad. La
decisión de qué pintar se extrajo a `PlaybackNotificationState`
(`service/PlaybackNotificationState.kt`), que devuelve el estado "idle" cuando no
hay item: sin `setOngoing`, sin `MediaStyle` y sin decir "Reproducendo". Cubierta
por `PlaybackNotificationStateTest` (8 tests). Se arregla **sin tocar
`PlayerViewModel`**: el aviso llega solo, porque `clearMediaItems()` ya dispara
`onMediaItemTransition(null)` y el listener del servicio lo ve (§6: el servicio
no debe ser dueño del reproductor, y aquí sigue sin serlo).

> Nota: `ACTION_STOP` (`MusicService.kt:27,46-49`, añadida en **B42**) sigue sin
> que nadie la mande, pero ya **no hace falta** para esto: `updateNotification`
> retira la notificación por su cuenta cuando no hay item. La otra vía de
> retirada es `MainActivity.onDestroy` con `isFinishing` (`:190-196`), que llama
> a `stopService(...)`, al cerrar la app.

~~**B39**~~ (`AndroidManifest.xml:54-60`) —`MediaButtonReceiver`
exportada con intent filter y sin permiso, de modo que cualquier app podía
inyectar `ACTION_MEDIA_BUTTON`— se resolvió en la séptima tanda poniéndola a
`exported="false"`: el sistema y la propia app siguen llegándole con los botones
de medios sin exponerla a otras apps (→ §10). |

---

## 4. SEGURIDAD

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S3 | Alta | `app/build.gradle.kts:45` | ~~Sin R8 ni ofuscación en release (ver B41).~~ **B41 resuelto y verificado**: R8 activo en `assembleRelease`, los `Log.*` con datos sensibles se eliminan con `-assumenosideeffects` y `./run.sh build release` termina en `BUILD SUCCESSFUL` (APK de 22,6 MB → 4,9 MB, sin firmar por falta de keystore). |
| S6 | Media | `SimpleDownloader.kt` | ~~Loguea **cookies** (incluida la de reCAPTCHA) y **cabeceras completas**.~~ **Resuelto** en la octava tanda: los logs dan solo los *nombres* de las cookies (`describeCookieNames`), ocultan `Authorization`, `Cookie`, `Set-Cookie`, `X-Goog-Visitor-Id` y compañía (`describeHeaders`) y ya no vuelcan el cuerpo de respuesta ni el `playabilityStatus` entero (`playabilityStatusOf`). Verificado con `SimpleDownloaderLogRedactionTest` (18). |
| S7 | Media | `SupabaseClient.kt` (≈42 `Log.*`) | Vuelca cuerpos completos de requests/responses: nicknames, códigos de invitación, nombres de grupo, URLs y comentarios de recomendaciones. PII en logcat. |
| S9 | Baja | `SupabaseClient.kt:19-20` | URL y anon key en el código. La key es `sb_publishable_…` (publishable por diseño de DCL), así que no es un secreto, pero **las políticas RLS de `groups`, `group_members`, `recommendations` y `automatic` no son verificables desde aquí** y son la única defensa de esos datos. |
| S10 | Baja | `SimpleDownloader.kt:19` | Cookie de YouTube hardcodeada (`PREF=f2=8000000`): caduca en servidor sin aviso y el "bypass" es en consecuencia poco fiable. |
| S11 | Baja | `AndroidManifest.xml:54-60` | ~~Receiver exportado sin permiso (ver B39).~~ **Resuelto** en la séptima tanda: `exported="false"` (ver §10). |
| S12 | Baja | `AndroidManifest.xml:22-24` + `data_extraction_rules.xml` | La copia de seguridad se lleva el Uri del árbol SAF a la nube (ver B40). |

**Nada que resolver aquí.** Las piezas que quedaban de Spotify
(`plyr://spotify` BROWSABLE, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, token en
prefs, `client_secret` de OAuth) ya no están en el manifiesto ni en el código.

---

## 5. RENDIMIENTO Y CONCURRENCIA

| Severidad | Ubicación | Descripción |
|---|---|---|
| Alta | `QrScannerDialog.kt` | ~~Executor sin `shutdown()` y cámara sin `unbindAll()` en `onDispose` (B11).~~ **Resuelto** en la sexta tanda: `onDispose` con `unbindAll()` + `shutdown()`, guarda de reentrada con `AtomicBoolean` y callbacks al hilo principal (→ §10). |
| Alta | `QRDialog.kt:223` | ~~QR de 512×512 regenerado sin `remember` en el hilo principal.~~ **Resuelto** en la quinta tanda con un bitmap cacheado por contenido (→ §10). La otra mitad de B20, la animación de `NfcButton`, se cerró en la octava tanda: el dibujo es ahora una función pura del frame (`NfcPulse`), el frame lo marca `withFrameNanos` (vsync) en vez de un `delay(200L)` a 5 fps, y el estado de animación pasa de una `SnapshotStateList` mutada por frame a un único `Int` (11 tests). |
| Media | `ConfigScreen.kt` | ~~Exportación/importación/copia en scope de composición, cancelable (B23).~~ **Resuelto** en la sexta tanda con `backgroundScope` (→ §10). |
| Media | `FeedScreen.kt:47`, `:65` | `metadataCache = metadataCache + (...)` reconstruye el mapa entero en cada insert (**O(n²)**) y es un read-modify-write no atómico ejecutado desde N corrutinas concurrentes (`:63`). Además esos `scope.launch` cuelgan de `rememberCoroutineScope()`, no del `LaunchedEffect` que los lanza: sobreviven a la cancelación del efecto y escriben estado cuando ya no aplica. |
| Media | `FeedScreen.kt:74-107` | `Column` + `verticalScroll` + `forEach`: se componen todas las recomendaciones a la vez, y sin límite de concurrencia (una extracción de red por fila, `:107`). |
| Media | `YouTubeSearchResults.kt:217-218` | `Column` + `forEachIndexed` en vez de `LazyColumn`: todo el set de resultados se compone de golpe, y cada `SongListItem` lanza una consulta a la DB en su `LaunchedEffect` (`SongListItem.kt:285-290`). |
| Media | `ConfigScreen.kt:184-188` | `packageManager.getPackageInfoCompat(...)` se ejecuta **dentro de la composición**: `PackageManager` + reflexión en cada recomposición. |
| Media | `Theme.kt:136-140` | `unifiedTypography()` es `@Composable` y hace `return Typography(...)` **sin `remember`**: se reconstruyen la `Typography` y 14 `TextStyle` en cada recomposición, invalidando todos los nodos de texto de la app. |
| Baja | `PlaylistScreen.kt` y `YouTubePlaylistDetailView.kt:220` | **5 `items(...)` de listas perezosas sin `key`**, 4 de ellas en el monolito de `PlaylistScreen` (0 ocurrencias de `key =` en el archivo): al reordenar o añadir, cada item se recrea y pierde su estado y su animación. Las listas de `HomeScreen` y `YouTubeSearchResults` sí van keyadas; la de `QueueScreen` va keyada pero con una clave inestable (siguiente fila). |
| Baja | `QueueScreen.kt:53-54`, `:71` | `key = { index -> "${currentPlaylist!![index].id}_$index" }` **no es estable** (incorpora la posición: cualquier reordenación cambia todas las claves y destruye el estado de los items) y `currentPlaylist!!` se re-deriva tres veces, dos de ellas dentro de la lambda de composición, con la posibilidad de desborde si la lista cambia entremedias. |
| Baja | `SearchScreen.kt:425-437` | `trackEntities` se reconstruye **en cada recomposición** (no está en `remember`) y con `lastSyncTime = System.currentTimeMillis()`, así que las identidades de objeto son siempre nuevas y los `SongListItem` de debajo recomponen sin parar. |
| Baja | `CoverCropDialog.kt:235-236` | `crop()` + `resizeToSquare()` se ejecutan **de forma síncrona en el hilo principal** dentro del manejador del clic: decodifica, escala y recorta un bitmap a tamaño completo en la UI. Además `:113-115` escribe estado de snapshot **durante la composición**, y `:140` usa esas medidas como claves del `pointerInput`, con lo que el gesto se reinicia en cada recomposición. |
| Baja | `SongListItem.kt:105`, `:224` | `.height(32.dp)` fijo en la `Box` y en la `Row` que contienen un `Column` de **dos** líneas (título + artista): el artista se recorta en todas las filas de todas las listas. |
| Baja | `SongListItem.kt:82-83` | `Config.getSwipeRightAction(context)` / `getSwipeLeftAction(context)` leen `SharedPreferences` en cada recomposición de cada item, no solo cuando cambian. |

---

## 6. ARQUITECTURA

| Severidad | Ubicación | Descripción |
|---|---|---|
| Media | `viewmodel/PlayerViewModel.kt` (785) | Monolito con estado mutable repartido entre el hilo principal y las corrutinas. `generation` + `windowStart` + `transitionInFlight` son 3 banderas que hay que mantener coherentes a mano; B4 y B6 eran consecuencia directa de que la invalidación de caché y el estado de error se gestionasen en un sitio y no en otro. La lógica pura ya está aislada en `QueueIndex` (y ahora también en `PlaylistLocalRepository.likedTrackOf` y en el núcleo de `YouTubeManager.getAudioUrl`), pero el estado de la ventana no. |
| Media | `ui/PlaylistScreen.kt` (1403) | Mezcla UI, red (Supabase/YouTube), DB y lógica de negocio; además construía el modelo de UI (`Song`) sin el campo que la propia UI necesitaba (B1), lo que es exactamente el tipo de error que un ViewModel por pantalla habría hecho imposible. |
| Media | `service/MusicService.kt` | No es dueño del reproductor: solo proyecta la notificación sobre el `ExoPlayer` que vive en el `PlayerViewModel` de `PlyrApp`. **No registra ningún `MediaSession.Callback`**, así que `seekToNext`/`seekToPrevious` desde la notificación o el lockscreen los mueve ExoPlayer directamente, no `QueueIndex`; el índice se reconcilia después por la aritmética de `syncIndexFromWindow`. Funciona por casualidad, no por diseño. |
| Media | `ui/ConfigScreen.kt` (665), `ui/SearchScreen.kt` (483) | Composables con carga, red y estado en `remember`/`rememberCoroutineScope`. |
| Baja | `ui/components/SongListItem.kt` (545) | ~~`pointerInput(Unit)` sin claves y `Animatable` mutado desde corrutinas lanzadas a mano~~ (B7, B8, ya resueltos → §10): el gesto ahora se reinicia por clave y el offset se acumula de forma síncrona. |

**Nota positiva:** el patrón de **extraer lógica pura testeable** está
consolidado y bien aplicado en `QueueIndex`, `MediaButtonCommand`,
`CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`,
`ExportDigest` y `UrlParser`. Son 8 módulos con 159 tests y cero dependencias de
Android. Es el asset de calidad más valioso del repo y el modelo a seguir.

---

## 7. LIMPIEZA / CÓDIGO MUERTO — lo grande, borrado en la novena tanda

**Archivos enteros sin ninguna llamada: 0 (antes 2).** Los dos que quedaban se
borraron en T9 (→ §10), junto con sus imports muertos:

| Archivo | Líneas | Qué era |
|---|---|---|
| ~~`ui/components/SongMenuDialog.kt`~~ | −171 | Solo quedaban dos `import` sin usar en `FloatingMusicControls.kt:28-29`; el diálogo en sí no se llamaba desde ningún sitio (duplicaba el popup que vive dentro de `SongListItem`). |
| ~~`ui/components/CollapsibleSection.kt`~~ | −88 | Cero referencias. |

**Símbolos y miembros sin uso en producción:** quedan 4, y todos son menores.

| Símbolo | Ubicación | Nota |
|---|---|---|
| `TerminalColorsPreview`, `PreviewTerminalThemeDark/Light` | `Theme.kt` | Previews de Android Studio, inalcanzables en runtime. **Se dejan**: son herramientas de desarrollo. |
| `ResponsiveDimensions`: `titleSize`, `iconSize*`, `buttonHeight`, `buttonMinWidth` | `ResponsiveUtils.kt:31-51` | Calculados en cada llamada y nunca leídos. |
| `ActionButtonData.enabled` | `ActionBttn.kt:25` | Ningún `ActionButtonData(...)` del source pone `enabled = false`; el render de deshabilitado (`:54`, `:65`) es inalcanzable. |
| `MediaCommand` sin `STOP` | `MediaButtonCommand.kt` | `KEYCODE_MEDIA_STOP` se mapea a `PAUSE` y el `NONE` de `execute` (`:77`) es rama inalcanzable. |

**Borrados ya (resueltos):**

| Símbolo | Qué se hizo |
|---|---|
| ~~`PlyrDimensions` (objeto completo)~~ | 12 constantes sin una sola referencia → borrado en T9 (→ §10). |
| ~~`QueueIndex.needsRefillAfterEnd`~~ | Probado con 4 tests pero **nunca llamado** por `PlayerViewModel` (`growWindow()` se encarga por su cuenta) → función **y sus 4 tests** borrados en T9 (→ §10). |
| ~~`val loadPlaylists = { }`~~ | No-op asignado y nunca invocado → borrado en T9. |
| ~~`PlyrSymbols.COMMAND/SEPARATOR/BACK`~~ | Sin referencias → borrados en T9. (`BULLET` y `ARROW` sí se usan en `HomeScreen.kt:235-236` y se conservan.) |
| `YouTubeManager.clearCache` | **Se conserva**: producción no la llama (la caché solo se invalida por `videoId` con `invalidate`), pero `AudioUrlExtractionTest` la usa en el `setUp` para partir de una caché limpia. Documentado en su KDoc. |
| `isValidAudioUrl` + `containsAudioPattern` | Ya no existen: borrados en **B37** con sus 13 tests (ver §10). |
| `MediaMetadataExtractor.extractMetadata(context = …)` | Parámetro muerto eliminado en **B29** (ver §10). |
| `SongListItem.onShowPlaylistDialog` | ~~Ambos callers pasan `{}`.~~ **Conectado en B2** (ver §10): el swipe "añadir a playlist" abre el diálogo de selección. |
| `CollapsibleSection.statusColor` | Parámetro con default que llamaba a `MaterialTheme.colorScheme` en el argumento por defecto; desapareció con el fichero en T9. |

### 7.1 Traducciones — **cerrado en la octava tanda**

**Claves sin uso: 0 (antes 22).** El recuento original de este informe (16 de
104) se quedó corto porque buscaba la cadena en cualquier sitio del código, no
como argumento de `Translations.get`: así `search_engine` o `user_nickname`
contaban como "usadas" por ser claves de `SharedPreferences` en `Config.kt`, y
`invite_code`/`nickname`/`comment`/`recommendations` por ser campos JSON de
`SupabaseClient`. Barrido real de los argumentos de `Translations.get`:

- **15 eliminadas** (×4 idiomas = 60 líneas): `artist_image`,
  `colored by used engine`, `enter_nickname`, `gestures_section`,
  `home_new_playlist`, `home_queue`, `home_settings`, `info`, `info_text`,
  `lastfm_api_key`, `next`, `no_results`, `not_configured`, `previous`
  (+ `backup_folder_none`, que el informe listaba pero ya no existía).
- **7 eliminadas** (×4 = 28 líneas): `add_to_liked_songs` (la cadena sigue
  viva, pero como valor de `Config.SWIPE_ACTION_ADD_TO_LIKED`, no como clave de
  traducción), `comment`, `invite_code`, `nickname`, `recommendations`,
  `search_engine`, `user_nickname`.
- **`player_not_available` deja de estar sin uso**: `QueueScreen.kt:103` pasaba
  el texto inglés `"Player not available"` como clave, así que `Translations.get`
  devolvía la propia clave y el texto salía **sin traducir en los 4 idiomas**.
  Ahora usa la clave real, y su valor en español era en sí una clave
  (`"reproductor_no_disponible"`), corregido a `"reproductor no disponible"`.

**Claves que son textos: 2 renombradas.** `"No tracks loaded"` (usada en
`QueueScreen.kt:87`) → `no_tracks_loaded`, y `"Loading tracks..."`
(`PlaylistScreen.kt:264`) → `loading_tracks`. Ambas funcionaban por casualidad,
porque el inglés era también la clave. `noKeyIsEnglishTextUsedAsAKey` impide que
vuelvan a colarse.

**Y ahora hay tests que lo mantienen.** `TranslationKeysUsageTest` (nuevo, 4
tests) recorre `src/main/java` y falla si una clave usada no existe en los 4
idiomas, si queda alguna sin usar (con una lista explícita de las que se resuelven
en runtime, hoy solo `sync_working` vía `ConfigScreen.DataActionRow`), si
`player_not_available`/`no_tracks_loaded` no traducen, o si una clave vuelve a ser
un texto. `TranslationsTest` (`coreKeysExistInAllLanguages`) ya no exige `info`,
que se borró.

**Literales de interfaz fuera de `Translations`** — esto **sigue pendiente** y no
es un bug, es un refactor de amplio alcance (muestra; hay más):
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

- **347 tests unitarios en 26 archivos** (`./run.sh test`, todos en verde;
  +7 respecto al recuento estático de esta revisión por la cobertura de
  tombstones/`deletedPlaylistIds`, luego +4 de formato de vídeo en B27, −13 al
  retirar los de `isValidAudioUrl` en B37, −1/+4 con `parseTimestamp` en B25
  (el test que certificaba el "ahora falso" se retiró y se añadieron
  `isoWithOffset`, `invalidReturnsZeroNotNow`, `blankReturnsZero` y
  `rejectsTrailingGarbage`), +3 en T5
  (`build_reportsDiscardedTracks` y `build_cancelledScopeStopsResolving`
  (B15/B16) en `YouTubePlaylistCreatorTest` y
  `trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition` (B28) en
  `DatabaseMappingsTest`) y **+16 en T6**: `AudioUrlExtractionTest` (9, nuevo
  fichero: *single-flight*, salto de caché y no-cacheo de fallos en
  `YouTubeManager.getAudioUrl`) y 7 de identidad de favoritos en
  `DatabaseMappingsTest`, que pasa de 14 a 21, **+33 en T8**
  (`SimpleDownloaderLogRedactionTest` 18, `NfcPulseTest` 11 y
  `TranslationKeysUsageTest` 4, los tres ficheros nuevos) y **−4 en T9**: los
  tests de `QueueIndex.needsRefillAfterEnd`, que solo verificaban código que no
  llama nadie y que se borró con la función, y **+8 en T11**:
  `PlaybackNotificationStateTest` (nuevo, 8), que cubre los dos estados de la
  notificación de reproducción (con item y "idle") y con ello el B55 y el B56.
- 0 tests instrumentados útiles: solo `ExampleInstrumentedTest`.

**Gaps relevantes, en orden de daño que hacen:**

1. **`PlayerViewModel` no tiene ningún test.** Sigue siendo el mayor gap: B4, B5,
   B6 y B36 eran los 4 bugs de reproducción de la lista y ninguno se puede
   seguir ejercitando sin Android. `QueueIndex` sí está cubierta (18 tests) y es
   correcta; lo que no está cubierta es la *orquestación*: `generation`,
   `windowStart`, `transitionInFlight`, `currentVideoId` y la derivación de
   `isLoading`/`error`. La lógica que decide si una URL caducada se invalida,
   cuándo se limpia `_error` y si hay que recargar la ventana es exactamente la
   que no se puede ejercitar sin Android. Lo que sí se ha hecho en T6 es
   **aislar la política de caché**: `getAudioUrl` delega en un núcleo con la
   extracción inyectada, que ya tiene sus 9 tests; lo que queda sin cubrir es
   la coreografía de la ventana.
2. **`SongListItem` no tiene ningún test**, y su lógica de swipe (umbral,
   dirección, acción) está embebida en lambdas de `pointerInput`. B1, B2, B7 y B8
   son inaccesibles a un test JVM tal como está el código. Extraer la decisión
   "offset → acción" a una función pura (como se hizo con `QueueIndex` y
   `MediaButtonCommand`) es el primer paso para poder probarlo.
3. ~~**13 tests de `UtilsTest` consolidaban el bug de `isValidAudioUrl`** en vez de
   detectarlo (B37).~~ **Hecho** (ver §10): la función muerta y sus 13 tests se
   borraron.
4. **Los módulos más valiosos del repo están bien cubiertos** (`CoverCropMath` 24,
   `ImportManifest` 30, `ExportManifest` 24, `ImportArchive` 14, `ExportDigest` 11,
   `QueueIndex` 22, `MediaButtonCommand` 9). El patrón funciona; el problema es
   que no se ha extendido a la capa de orquestación. T6 añade el noveno módulo,
   `AudioUrlExtractionTest` (9), con el mismo enfoque, y T8 los tres siguientes
   (`SimpleDownloaderLogRedactionTest` 18, `NfcPulseTest` 11,
   `TranslationKeysUsageTest` 4), extrayendo antes la lógica a funciones puras
   (`buildCookieHeader`, `describeHeaders`, `NfcPulse`).
5. ~~`QueueIndexTest` cubre 4 tests de `needsRefillAfterEnd`, una función que
   producción no llama (ver §7): 4 tests que certifican código muerto.~~
   **Hecho en T9**: función y tests borrados (ver §10).
6. ~~`TranslationsTest` valida consistencia entre idiomas pero **no** detecta ni
   claves sin uso (16) ni claves referenciadas inexistentes (1).~~ **Hecho en
   T8**: `TranslationKeysUsageTest` recorre `src/main/java` y cubre las dos
   invariantes (más que las claves sin uso, que eran 22 y no 16).

---

## 9. HOJA DE RUTA

### Fase 0 — Re-reportado el 2026-10-01 (lo primero, por impacto directo)

Ordenado por lo que más molesta al uso diario. Los arrangements de B49 y B50
comparten pieza (un `MediaSession.Callback` que pase los comandos de transporte a
`QueueIndex`), así que conviene hacerlos juntos.

1. **B53 — el share de una lista.** Queda del bloque de compartir, que era
   B52 + B53 + B54; **B52 y B54 ya están resueltos** (treceava y duodécima
   tanda, → §10). Lo que queda:
   - Dejar de construir la URL en la pantalla y decidirla **una sola vez** en
     `ShareDialog` (`QRDialog.kt:115-128`), que hoy da prioridad a `shareUrl` y
     por eso descarta el `youtubeVideoId` correcto que ya viaja. Con
     `shareUrl = null` en `PlaylistScreen.kt:799,862` el caso de la canción se
     resuelve solo (usa `youtubeVideoId`).
   - Para la lista, dejar de deducir el tipo de URL por prefijos
     (`QRDialog.kt:118-126`): decidir con el origen real de la lista. Como hoy
     el único dato es `description == "Imported from Spotify"`
     (`SpotifyImporter.kt:171`), lo serio es **persistir el origen**
     (columna `source`/`sourceId` en `PlaylistEntity`, con su migración de Room)
     y compartir `open.spotify.com/playlist/<id>` para lo importado, la URL de
     YouTube para lo guardado de YouTube, y **no ofrecer compartir** en
     `liked_songs` ni en las listas creadas localmente sin origen.
   - Con eso, el mensaje de "no hay nada que compartir" que se añadió en B54
     pasa a ser la excepción en vez del caso normal, y `QueueScreen` podrá tomar
     el id de `PlayerViewModel.resolvedVideoId` (`:114`) para las pistas
     resueltas por búsqueda.
   - Tests: `ShareUrlPolicy` como objeto puro (tipo de URL a partir de
     origen + id) con cobertura de las cuatro filas de la tabla de B53.
2. **B51 — los favoritos borrados vuelven al sincronizar.** Requiere decidir la
   política **antes** de escribir código (ver el aviso de diseño en §3.2): tomb de
   favoritos por clave (`youtubeVideoId` o `fallbackDedupeKey`) o digest de
   `liked_songs` en `ExportDigest`. Campo aditivo en el manifiesto, formato v1
   intacto, como se hizo con `deletedPlaylistIds`. Toca `ExportManifest`,
   `ImportManifest`, `ExportDigest` y sus tres ficheros de test.
3. **B49 + B50 — anterior y siguiente.** Pieza común: un
   `MediaSession.Callback` en `MusicService` que redirija
   `COMMAND_SEEK_TO_NEXT` / `COMMAND_SEEK_TO_PREVIOUS` a
   `navigateToNext()` / `navigateToPrevious()` (que es lo que §6 ya señalaba),
   en vez de dejar que ExoPlayer se mueva dentro de la ventana. Y para B50, que
   la notificación se reconstruya también cuando cambia la línea de tiempo
   (`onTimelineChanged`), no solo en `onMediaItemTransition`.
   - B49 en concreto: distinguir "reiniciar" de "volver" sin el coste actual
     (hoy cualquier salto atrás sale de la ventana y re-resuelve por red con los
     controles deshabilitados, `PlayerViewModel.kt:429-436`), y no dejar que
     `playIndex` salte **hacia delante** cuando lo que falló resolver fue la
     canción anterior (`:455-473`).
   - B50 además: que un relleno fallido no deje la ventana sin item siguiente
     (`:553`), p. ej. reintentando o reservando el sitio.
4. ~~**F3 — "añadir a lista" en el menú `*`**: **hecho en la décima tanda**
   (2026-10-01, → §10). Era el más fácil de toda la lista: el selector ya existía
   y funcionaba, solo faltaba la entrada en el popup. +15 líneas, 1 entrada.
5. ~~**B55 + B56 — la notificación fantasma.** **Resueltos en la undécima
   tanda** (→ §10): la decisión de qué pintar se movió a la función pura
   `PlaybackNotificationState`, y sin item en curso `MusicService` hace
   `stopForeground(STOP_FOREGROUND_REMOVE)` en vez de repintar. No hace falta
   tocar `PlayerViewModel`: `clearMediaItems()` ya dispara
   `onMediaItemTransition(null)`.
6. **F1 — `liked` vacía no aparece como lista** (§11): filtrarla por
   `trackCount > 0` en `HomeScreen.kt:276-278` y `PlaylistScreen.kt:121-124` sin
   borrar la fila de la base, porque de su existencia dependen `toggleLikeTrack`,
   `mergeLikedSongsTracks` e `ImportManifest.plan` (§3.2, F1).

### Fase 1 — Lo que has reportado (impacto directo)

1. ~~**B1** — pasar `youtubeId` en los tres `Song(...)` de `PlaylistScreen.kt:721,794,856`
   (`youtubeVideoId = track.youtubeVideoId`) **y** hacer que `executeSwipeAction`
   no llame a `toggleLikeTrack` con `""` (si no hay id, buscar por
   nombre+artista con `ImportManifest.fallbackDedupeKey`, que ya existe y se usa
   en `mergeLikedSongsTracks`). Sin la segunda mitad, volver a abrir la lista de
   favoritos sigue encontrando las filas corruptas que ya están guardadas.
   Al tocar el like hay que refrescar también la lista abierta de *Liked*
   (**B48**): `playlistTracks` es un snapshot que no se recarga con el toggle.~~
   **Hecho** (ver §10): los tres `Song` llevan `youtubeId`, la identidad de la
   fila de *Liked* es la función pura `PlaylistLocalRepository.likedTrackOf`
   (id → nombre+artista) y `PlaylistScreen` pasa `onLikedStatusChanged =
   { tracksRevision++ }`, que resuelve **B48** en el mismo cambio.
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
4. ~~**B4** — que `invalidate()` reciba el id **realmente usado** (el que resolvió
   `startAt`, no `track.youtubeVideoId`), y persistirlo en la `MediaItem` para
   poder recuperarlo en el handler de error. Además, `getAudioUrl` debería
   aceptar un flag "forzar re-extracción" para que `reResolve = true` no pueda
   recibir la URL caducada de la caché.~~ **Hecho** (ver §10): mapa
   `resolvedVideoId` por `track.id` que `startAt`, `resolveItems`,
   `syncIndexFromWindow` y `setCurrentIndex` leen y escriben (no hace falta
   meterlo en el `MediaItem`: el id se recupera del estado), más
   `getAudioUrl(videoId, forceRefresh)` propagado desde `resolveItems`.
5. ~~**B5** — *single-flight* en `YouTubeManager.getAudioUrl` (un
   `ConcurrentHashMap<String, Deferred<String?>>` o `Mutex` por `videoId`), y
   dejar de llamar `growWindow()` dos veces por transición.~~ **Hecho** (ver §10):
   registro de extracciones en vuelo con `CompletableDeferred` y marca
   `prefetchRange` para que el segundo `growWindow()` de la transición no
   relance un relleno idéntico. Cubierto por `AudioUrlExtractionTest`.
6. ~~**B2** — implementar `SWIPE_ACTION_ADD_TO_PLAYLIST`: quitar el `{}` de
   `onShowPlaylistDialog` en `SongListItem.kt:165,182`, cablear un diálogo de
   selección de playlist, y borrar el `Log.d` de `:446-448`. Mientras tanto,
   **ocultar la opción en Ajustes** si no se va a implementar, porque hoy es la
   opción que más engaña.~~ **Hecho** (ver §10): implementada, así que no hace
   falta ocultarla en Ajustes.
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

11. ~~**B6** — limpiar `_error` al recuperar en `playIndex` (y al empezar cualquier
    carga), no solo en `startAt`.~~ **Hecho** (ver §10): `onMediaItemTransition`
    publica `null` cuando entra un item, que es el punto por el que pasa toda
    recuperación.
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
    (ver §10): el QR ya no se regenera en cada recomposición. La segunda mitad
    (optimización) se cerró en T8: la animación de `NfcButton` es la función pura
    `NfcPulse.textAt(frame)` en su propio fichero, el frame lo avanza
    `withFrameNanos` (vsync en vez de `delay(200L)` a 5 fps) y el estado pasó de
    una `SnapshotStateList` mutada por frame a un único `Int`. Se mantiene el
    dibujo ASCII en `Text` en lugar de pasarlo a `Canvas`, porque el aspecto es el
    que el usuario ve.
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
26. ~~**§7.1** — borrar las claves sin uso y arreglar `QueueScreen.kt:87,103`.~~
    **Hecho en T8** (ver §7.1): 22 claves muertas eliminadas (no 16: el barrido
    original contaba como "usadas" las claves de `Config.kt` y los campos JSON de
    `SupabaseClient`), `player_not_available` en uso con su valor español corregido,
    `"No tracks loaded"` → `no_tracks_loaded` y `"Loading tracks..."` →
    `loading_tracks`, y `TranslationKeysUsageTest` (4) falla si una clave usada no
    existe, si queda alguna sin usar, si esas dos no traducen o si una clave vuelve
    a ser un texto.
27. ~~**B39**, **B41**~~ — poner el receiver en `exported="false"` y activar R8 con
    reglas para Room/NewPipe (**ambos Hechos**, ver §10: B39 con
    `android:exported="false"`; B41 con `isMinifyEnabled = true` más `-keep` para
    NewPipe y `-assumenosideeffects` para `Log.*`). **B41 verificado en T8**: el
    build de release estaba roto por dos motivos —R8 abortaba con
    `Missing class java.beans.*` (Rhino, `org.mozilla.javascript`, referencia
    `java.desktop`) y lint trataba como fatal un `exclude` de una ruta no incluida
    en las reglas de backup—, y ambos están arreglados:
    `./run.sh build release` da `BUILD SUCCESSFUL`. (Excluir
    `plyr_config.xml` del cloud-backup —B40— ya está hecho, ver §10; quitar
    `WAKE_LOCK` —B38— ya está hecho, ver §10.)

### Fase 4 — Estabilidad operativa y limpieza

28. ~~**B23** — mover exportación, importación y copia de seguridad a un scope de
    aplicación (o a un `ViewModel`) para que un swipe en el pager no las corte.~~
    **Hecho** (ver §10): `backgroundScope` expuesto por `PlyrApp` y usado por
    `SyncSection` en config.
29. ~~**§7** — borrar `SongMenuDialog.kt` (171), `CollapsibleSection.kt` (88),
    `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd` + sus 4
    tests y `YouTubeManager.clearCache`.~~ **Hecho en T9**, con una salvedad:
    `YouTubeManager.clearCache` **se conserva** porque `AudioUrlExtractionTest` la
    usa (ver §7). Lo que queda en §7 son 3 entradas menores, que no se tocan:
    previews de Android Studio, campos muertos de `ResponsiveDimensions` y
    `ActionButtonData.enabled` + la rama inalcanzable de `MediaCommand.NONE`.
    (`isValidAudioUrl` —B37— ya se borró con sus 13 tests, ver §10.)
30. ~~**B32** — dejar de loguear cookies, cabeceras y cuerpos completos (y
    sincronizar el mapa de cookies).~~ **Hecho** en T8 (ver §10).
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
| **B20** (1ª mitad) | `QRDialog.kt:223` | `generateQrBitmap(shareUrl)` movido a `val qrBitmap = remember(shareUrl) { generateQrBitmap(shareUrl) }`: el QR 512×512 ya no se decodifica y pinta en el hilo principal en cada recomposición (p. ej. las 5 frames por segundo del `NfcButton` en `WAITING`). |
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

---

Séptima tanda de arreglos (2026-09-30), los 7 bugs que quedaban: los 5 altos
(B1, B2, B4, B5, B6), el medio B48 y el bajo B39.
Todos verificados con `./run.sh test` (**310 tests, en verde**) y
`./run.sh build` (BUILD SUCCESSFUL).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B1** | `PlaylistScreen.kt` (los 3 `Song`) + `SongListItem.kt` + `PlaylistLocalRepository.kt` | Los tres `Song(...)` de la pantalla pasan ahora `youtubeId` (el de la pista, o el id de YouTube del resultado de búsqueda en la lista de edición), así que el gesto ya no manda `""` a `toggleLikeTrack`. La identidad de la fila de *Liked* se extrajo a la función pura `PlaylistLocalRepository.likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId)`: primero `youtubeVideoId` y, si no hay, nombre+artista con `ImportManifest.fallbackDedupeKey`, con lo que **las filas corruptas ya guardadas también se pueden quitar**. El popup consulta `isTrackLikedByKey` (misma identidad) y hace el toggle aunque no haya id, así que la fila ya no miente con "♡ like". 7 tests nuevos en `DatabaseMappingsTest`. |
| **B2** | `SongListItem.kt` | `SWIPE_ACTION_ADD_TO_PLAYLIST` llama a `onShowPlaylistDialog()` (ya no a un `Log.d`) y las dos ramas del gesto cablean ese parámetro a un diálogo de selección de playlist, que lista las playlists locales (excluye *liked* y álbumes), añade con `addTrackToYouTubePlaylist` —que ya deduplica, B13— y avisa con `no_playlists` si no hay ninguna. |
| **B4** | `PlayerViewModel.kt` + `YouTubeManager.kt` | Nuevo `resolvedVideoId: ConcurrentHashMap<String, String>` con el video **realmente usado** por pista (indexado por `track.id`), que se rellena en `startAt` y en `resolveItems` y se lee en `syncIndexFromWindow`/`setCurrentIndex`: `invalidate()` recibe por fin el id correcto en vez de `null`. `getAudioUrl(videoId, forceRefresh)` salta la caché y `resolveItems` propaga el flag en la ruta `reResolve = true`, así que el reintento tras un 403/410 ya no puede recibir la URL muerta. `resolveItems` reutiliza además el video ya resuelto para esa pista en vez de volver a buscarlo en YouTube. |
| **B5** | `YouTubeManager.kt` + `PlayerViewModel.growWindow` | *Single-flight* en `getAudioUrl`: registro `inFlight` de `CompletableDeferred` por `videoId`; la primera extracción corre y las demás esperan su resultado, y un fallo no queda en caché. En `growWindow`, la marca `prefetchRange` evita relanzar un relleno idéntico que ya está en marcha (con la extracción indestructible, duplicarlo además añadía los mismos items dos veces a la ventana). 9 tests nuevos en `AudioUrlExtractionTest`, que ejercita el núcleo con la extracción inyectada. |
| **B6** | `PlayerViewModel.kt` | `onMediaItemTransition` publica `_error = null` cuando entra un item, que es el punto por el que pasa toda recuperación: el mensaje de error ya no se queda pegado y los controles no quedan deshabilitados para siempre tras un `onPlayerError` recuperable. |
| **B48** | `PlaylistScreen.kt` | La lista abierta de *Liked* pasa `onLikedStatusChanged = { tracksRevision++ }` al `SongListItem` de la vista normal, de modo que el popup y el swipe recarga `playlistTracks`/`trackEntities` y la fila quitada desaparece sin salir y reentrar. |
| **B39** | `AndroidManifest.xml:53-61` | `MediaButtonReceiver` a `android:exported="false"`: el sistema y la propia app siguen entregándole `ACTION_MEDIA_BUTTON`, pero ninguna otra app puede inyectarlo. |

---

Octava tanda de arreglos (2026-09-30), el cierre: **B32**, el resto de **B20**,
**B41** verificado de verdad y **§7.1**. Con ella **no quedaba ningún bug
activo**: 48 de 48 (cifra que se mantuvo hasta el 2026-10-01; ver §3, donde
aparecen B49–B56).
Verificado con `./run.sh test` (**343 tests, en verde**), `./run.sh build`
(BUILD SUCCESSFUL) y, por primera vez, **`./run.sh build release`**
(BUILD SUCCESSFUL, APK de 22,6 MB → 4,9 MB, sin firmar por falta de keystore).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B32** | `SimpleDownloader.kt` | El mapa de cookies pasa de `mutableMapOf` a `ConcurrentHashMap`: `setCookie` escribe desde el hilo que inicializa el extractor y `getCookies` leía desde todos los hilos de red. Los logs dejaron de volcar datos: `describeCookieNames` da solo los nombres (`PREF; SID`), `describeHeaders` oculta `Authorization`, `Cookie`, `Set-Cookie`, `X-Goog-Visitor-Id` y las demás sensibles (sin distinguir mayúsculas, indicando solo el tamaño del valor) y el cuerpo de respuesta ya no se registra —ni en éxito (longitud), ni en error (que antes imprimía 500 caracteres del cuerpo 4xx), ni el `playabilityStatus` completo, que se sustituye por `playabilityStatusOf(body)`, que devuelve solo `OK`/`LOGIN_REQUIRED`/… . La lógica sensible quedó extraída a funciones puras (`buildCookieHeader`, `describeCookieNames`, `describeHeaders`, `playabilityStatusOf`) y cubierta por `SimpleDownloaderLogRedactionTest` (18 tests). |
| **B20** (resto) | `QRDialog.kt` + `NfcPulse.kt` (nuevo) | El pulso de escritura NFC salía de mutar una `SnapshotStateList` de radios desde un `LaunchedEffect` con `delay(200L)` (5 fps fijos, una recomposición por mutación y hasta 3 mutaciones por frame). Ahora el estado es un único `Int` de frame que avanza con `withFrameNanos` (vsync), y el dibujo es `NfcPulse.textAt(frame)`, una función pura sin Compose en fichero propio: nace un anillo cada 3 frames, el radio máximo sale por los extremos y el ciclo completo son 36 frames. 11 tests en `NfcPulseTest`. |
| **B41** (verificación) | `proguard-rules.pro` + `backup_rules.xml` + `data_extraction_rules.xml` | El build de release **nunca se había compilado** y estaba roto por dos motivos. 1) R8 abortaba: `Missing class java.beans.*` referenciado desde `org.mozilla.javascript.JavaToJSONConverters` (Rhino, dependencia de NewPipeExtractor) — `java.beans` es de `java.desktop` y no existe en Android, y la ruta no se ejecuta nunca; se añade `-dontwarn java.beans.**` y `-dontwarn javax.script.**` (que además silencia el aviso de `META-INF/services/javax.script.ScriptEngineFactory`). 2) Lint es fatal en release y marcaba `export-covers/ is not in an included path`: el `<exclude>` era redundante, porque con reglas solo de `include` (`covers/`) esa caché ya quedaba fuera. Se quitan los dos `<exclude>`; el comportamiento de copia es idéntico y `device-transfer` sigue incluyendo la caché a propósito. Resultado: `./run.sh build release` → `BUILD SUCCESSFUL`, y los `Log.*` con datos sensibles desaparecen del APK (comprobado: las cadenas de log están en el dex de debug y no en el de release). |
| **§7.1** | `Translations.kt`, `QueueScreen.kt`, `PlaylistScreen.kt`, `TranslationsTest.kt` | 22 claves muertas eliminadas en los 4 idiomas (88 líneas), 7 más de las que listaba este informe, porque el barrido original contaba como "usadas" las claves de `SharedPreferences` de `Config.kt` (`search_engine`, `user_nickname`) y los campos JSON de `SupabaseClient` (`invite_code`, `nickname`, `comment`, `recommendations`). `QueueScreen.kt:103` pasa a usar `player_not_available` (antes pasaba el texto inglés como clave, así que salía sin traducir) y su valor español era en sí una clave, `"reproductor_no_disponible"`, corregido. Las dos claves que eran texto se renombran: `"No tracks loaded"` → `no_tracks_loaded` y `"Loading tracks..."` → `loading_tracks`. `TranslationKeysUsageTest` (nuevo, 4 tests) recorre `src/main/java` y falla si una clave usada falta en algún idioma, si queda alguna sin usar, si esas dos no traducen o si una clave vuelve a ser un texto. `TranslationsTest` deja de exigir `info`, que ya no existe. |

---

Novena tanda (2026-09-30), **sin bugs nuevos**: el código muerto que esta misma
auditoría dejó listado en §7, borrado de raíz. Sin tocar una sola ruta de
ejecución — todo lo eliminado estaba sin referencias, verificado con barrido de
`app/src` antes de borrar, y los imports muertos que quedaban también.
Verificado con `./run.sh test` (**339 tests, en verde**), `./run.sh build`
(BUILD SUCCESSFUL) y `./run.sh build release` (BUILD SUCCESSFUL).
**−301 líneas de código, −2 ficheros, −4 tests.**

| Qué se borró | Por qué se podía borrar |
|---|---|
| `ui/components/SongMenuDialog.kt` (−171) | El diálogo no se llamaba desde ningún sitio: en `FloatingMusicControls.kt` solo quedaban los dos `import` (`SongMenuDialog` y `SongMenuData`) sin usar. El diálogo de canción que sí vive dentro de `SongListItem` no se toca. También se corrigió el comentario de `formatDuration` en `Utils.kt`, que lo citaba. |
| `ui/components/CollapsibleSection.kt` (−88) | Cero referencias en todo el repo. |
| `PlyrDimensions` (`Theme.kt`) (−21) | Objeto completo con 12 constantes (`floatingControlsHeight`, `buttonHeight`, `listItemHeight`, `elevationLarge`, …) sin una sola referencia, más su cabecera de sección. |
| `PlyrSymbols.COMMAND/SEPARATOR/BACK` (−3) | Sin referencias. **Cuidado**: `BULLET` y `ARROW` sí se usan en `HomeScreen.kt:235-236` y se conservan. |
| `val loadPlaylists = { }` (`PlaylistScreen.kt`) (−2) | No-op asignado y nunca invocado. |
| `QueueIndex.needsRefillAfterEnd` (−16) + sus **4 tests** (−25) | `PlayerViewModel` nunca la llama: `growWindow()` ya se encarga por su cuenta. Los 4 tests certificaban exactamente eso — código muerto — así que se van con la función, no con el `PlayerViewModel`. `QueueIndexTest` pasa de 22 a 18 y el resto de la cobertura de `QueueIndex` no se toca. |
| KDoc de `YouTubeManager.clearCache` | **No se borra la función**: `AudioUrlExtractionTest` la usa en el `setUp` para partir de una caché limpia. Se documenta que producción no la llama y por qué sigue ahí, en vez de inventar una vía nueva para los tests. |

Pendientes **a fecha de esa tanda**: ningún bug. Queda solo la lista de literales
de interfaz fuera de `Translations` (§7.1), que es un refactor y no un fallo; las 3
entradas menores de §7 (previews, `ResponsiveDimensions`, `ActionButtonData.enabled`),
que se dejan a propósito: no merece la pena tocar código vivo a cambio de un aviso
del linter; y los gaps de cobertura de §8 (`PlayerViewModel` y `SongListItem` sin
tests). Nada de esto se ha tocado desde entonces.

---

Décima tanda (2026-10-01), **un feature y ningún bug**: se implementa **F3**, la
única petición de §11 que era solo cablear UI, porque el selector de playlists ya
existía y funcionaba y solo faltaba la entrada en el menú `*`. Con esto F3 sale de
§11, pero **B49–B55 seguían todos activos al cerrarla**: esta tanda no toca
ninguno.

Verificado con `./run.sh test` (**339 tests, en verde**, los mismos que la
novena: el cambio no altera lógica, así que no hay test nuevo ni que actualizar) y
`./run.sh build` (**BUILD SUCCESSFUL**, APK debug). **No se compiló release**, por
falta de keystore en local.

| # | Ubicación | Qué se hizo |
|---|---|---|
| **F3** | `ui/components/SongListItem.kt:382-396` | El popup del `*` tenía tres acciones (like, `add_to_queue`, `share`) y ahora tiene una cuarta, `add_to_playlist`, que hace `showPopup = false; showPlaylistPicker = true`. Reutiliza el mismo `showPlaylistPicker` que ya abrían las dos ramas de swipe (`:178,195`), así que el selector, su filtro (excluye *liked* y álbumes), el `addTrackToYouTubePlaylist` y el aviso `no_playlists` son exactamente los de antes. Se cierra el popup antes de abrir el selector para que no se solapen dos diálogos. La etiqueta ya estaba en los cuatro idiomas: 0 traducciones nuevas. **+15 líneas, 1 entrada de menú.** |

Undécima tanda (2026-10-01), **B55 + B56 resueltos juntos**: los dos eran la misma
línea de código —`MusicService` construyendo la notificación sin item en curso y
cayendo en sus valores por defecto— así que no tenían sentido separarlos. Es el
arreglo más pequeño de los que quedaban y **no toca `PlayerViewModel`**: el aviso
de "no hay nada sonando" llega solo, porque `clearMediaItems()` ya dispara
`onMediaItemTransition(null)`.

Verificado con `./run.sh test` (**347 tests, en verde**: los 339 anteriores más 8
nuevos) y `./run.sh build` (**BUILD SUCCESSFUL**, APK debug). **No se compiló
release**, por falta de keystore en local.

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B55** + **B56** | `service/PlaybackNotificationState.kt` (nuevo) · `service/MusicService.kt` · `test/.../PlaybackNotificationStateTest.kt` (nuevo) | La decisión de qué pintar sale del servicio a una función pura `PlaybackNotificationState.of(appName, título, artista)`: con item devuelve título/artista con `ongoing = true` y `MediaStyle`; **sin item** devuelve el estado "idle" (título = nombre de la app, `ongoing = false`, `showMediaStyle = false`), o sea **no dice "Reproduciendo", no es imborrable y no monta controles que no puede resolver**. En `MusicService`, `createNotification` y la provisional `createStartupNotification` pasan a usar ese estado a través de un único `buildNotification`, y `updateNotification` hace `stopForeground(STOP_FOREGROUND_REMOVE)` cuando no hay item en vez de repintar la notificación fantasma. La `MediaSession` no se libera, así que el siguiente item vuelve a pintar la notificación con normalidad. 8 tests nuevos cubren los dos estados, incluidos el título vacío y el artista ausente (que antes caían en "Reproduciendo"). **+85 líneas, −2** |

Con esto quedan **4 bugs abiertos** (B49, B50, B51, B53) y **2 peticiones** (F1,
F2). **B52** y **B54** están resueltos, así que del bloque de compartir solo
queda **B53** (la URL de la lista). **B49** + **B50** siguen siendo el mismo
bloque (`MediaSession.Callback`) y **B51** va solo.

---

Duodécima tanda (2026-10-01), **B54 resuelto**: el diálogo de compartir ya no
puede abrirse en blanco. Es un arreglo de estado de UI, sin tocar cómo se calcula
la URL — eso era B52/B53 y se resolvió en la treceava tanda (abajo): B52 arreglado,
B53 pendiente.

Verificado con `./run.sh test` (**347 tests, en verde**, sin cambio de número: no
hay test nuevo porque el arreglo es de composición) y **`./run.sh build`**
(`BUILD SUCCESSFUL`, APK debug; comprobado que la clave nueva y el diálogo
modificado están dentro del APK). **No se compiló release**, por falta de
keystore en local.

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B54** | `ui/components/QRDialog.kt:225-248` · `utils/Translations.kt` (4 idiomas) | `ShareDialog` ocultaba el QR, el `<share>`, el NFC y el `<recomendar>` cuando `shareUrl == null` (`:258,302,337`), y lo que quedaba era un `Card` con padding de 24 dp y **nada dentro**: un diálogo en blanco, sin QR, sin botones y sin un solo mensaje que explicara por qué. Se llega desde la cola, que es el único sitio donde `shareUrl` **y** `youtubeId` acaban a la vez en `null` (`QueueScreen.kt:66` + `youtubeVideoId` de pistas sin coincidencia en YouTube, típicas de lo importado de Spotify). Ahora, cuando no hay URL, el diálogo pinta su propio estado: un mensaje (`no_share_url`, clave nueva en español, inglés, catalán y japonés) y un botón de cerrar. El resto de la caja no se toca, porque todo lo demás depende de que haya URL. **Lo que no se arregla aquí, a propósito:** que el `id` bueno (`PlayerViewModel.resolvedVideoId`) siga sin llegar desde la cola — eso es cambiar el contrato entre la pantalla de cola y el diálogo, y pertenecía al bloque de B52/B53, donde había que decidir *qué* URL se comparte — B52 quedó resuelto en la treceava tanda (§10). Aquí solo se consigue que, cuando no hay nada que compartir, la app **lo diga**. **+29 líneas, 1 clave × 4 idiomas** |

Con esto quedan **4 bugs abiertos** y **2 peticiones**: **B53** (qué URL se
comparte de una lista) y el bloque **B49 + B50** (`MediaSession.Callback`), más
**B51** (los favoritos borrados que el sync resucita), que es el más delicado
porque toca datos. Del bloque de compartir solo queda B53: **B52 y B54 ya están
resueltos**.

---

## 11. PETICIONES Y FUNCIONALIDAD FALTANTE

No son fallos: comportamiento que se quiere y que hoy no existe (o existe a
medias). Verificadas leyendo el código. **F3 está ya resuelta** (fue lo más fácil
de todo lo pendiente y se arregló en la décima tanda, 2026-10-01); F1 y F2
siguen abiertas. **F2 va de la mano de B52 + B53**, que son su parte de bug.

### F1 — La lista de favoritos vacía no debería aparecer como lista

**Petición:** que `liked` solo exista cuando tenga alguna canción.

Hoy la fila se crea siempre al arrancar la app (`PlyrApp.kt:26-28` →
`ensureLikedSongsPlaylist`, `PlaylistLocalRepository.kt:70-85`) con
`trackCount = 0`, y **ninguno de los dos listados la filtra**:

| Listado | Filtro actual | Con `liked_songs` vacía |
|---|---|---|
| Carrusel del Home | `HomeScreen.kt:276-278` — solo quita `album_*` | aparece un corazón rojo, el primero |
| Rejilla de listas | `PlaylistScreen.kt:121-124` — solo quita `album_*` | aparece una tile con el corazón |

- **Lo que hay que hacer es filtrar, no borrar.** La fila de `liked_songs` no es
  decorativa: `toggleLikeTrack` la lee para actualizar `trackCount`
  (`PlaylistLocalRepository.kt:112,132`), `mergeLikedSongsTracks` la actualiza
  (`:316-318`) e `ImportManifest.plan` da por hecho que existe
  (`ImportManifest.kt:139-141`, comentario explícito). Borrarla "si está vacía"
  rompería las tres.
- Detalle: `trackCount` sí se mantiene al quitar la última canción
  (`toggleLikeTrack` lo reescribe con `remaining.size`), así que el filtro puede
  ir por `trackCount > 0`; si se quiere ser exacto, por "tiene pistas" con una
  consulta a `TrackDao`.

### F2 — Qué URL hay que compartir (canciones y listas importadas de Spotify)

**Petición:** al compartir una canción, la de YouTube; al compartir una lista
importada de Spotify, la URL de Spotify.

Es exactamente **B52** + **B53** (§3.2), pero se recoge aquí como petición de
comportamiento. Lo que se deja por escrito es el criterio de fondo: **compartir
siempre el origen real de lo que se comparte**.

| Qué se comparte | Qué debería salir |
|---|---|
| Canción con `youtubeVideoId` | `https://www.youtube.com/watch?v=<id>` |
| Canción sin `youtubeVideoId` (resuelta por búsqueda) | la del vídeo que está sonando (`PlayerViewModel.resolvedVideoId`) |
| Lista importada de Spotify | `https://open.spotify.com/playlist/<id>` |
| Lista guardada de YouTube | `https://www.youtube.com/playlist?list=<id>` |
| Lista creada en la app / favoritos | nada que compartir → oculto o con aviso |

Dato de apoyo: `open.spotify.com/playlist/<id>` es justo lo que la app ya sabe
leer al escanear (`UrlParser.parseScanText`, `UrlParser.kt:52-56`), así que el
enlace que se comparta es uno que la propia app puede volver a abrir.

### F3 — "Añadir a lista" en el menú `*` — ~~pendiente~~ **RESUELTO (2026-10-01)**

**Petición:** que el menú que se abre con `*` tenga la opción de añadir la
canción a una lista.

**Lo que faltaba:** el popup de `SongListItem` tenía tres acciones — like,
`add_to_queue` y `share` — y el selector de listas ya existía y funcionaba, pero
solo se abría con la **acción de swipe** `add_to_playlist` (`executeSwipeAction`,
`:514`, rama en `:553-555`, cableado en `:178,195`), no desde el `*`.

**Arreglo:** una entrada más en el popup (`SongListItem.kt:382-396`) que hace
exactamente lo que hace el swipe — `showPopup = false` y
`showPlaylistPicker = true` — reutilizando el mismo `showPlaylistPicker` que ya
tenían las ramas de swipe (`:178,195`). Se cierra el popup antes de abrir el
selector para que no se solapen dos diálogos. Sin lógica nueva: el selector
(`:431-526`), el filtro que excluye *liked* y álbumes (`:436`), el
`addTrackToYouTubePlaylist` y el aviso `no_playlists` son los que ya había.
`add_to_playlist` ya existía en los cuatro idiomas, así que no hizo falta
traducción.

Verificado con `./run.sh test` (**339 tests, en verde**) y `./run.sh build`
(**BUILD SUCCESSFUL**, APK debug). No hay test unitario nuevo porque el cambio es
solo de composición: la lógica que decide a qué playlist se añade y qué se
escribe en la base de datos es la de `PlaylistLocalRepository.addTrackToYouTubePlaylist`,
sin ruta de ejecución nueva. El riesgo real es de UI (que el `*` abra el
selector), y eso es instrumentado.

---

### Treceava tanda de arreglos (2026-10-01): B52 resuelto

**B52 — compartir una canción ya no comparte el id de Spotify.** El share de
una pista leída de la base de datos mandaba
`youtube.com/watch?v=spotify_1234567_-987654_3`, porque `PlaylistScreen.kt:799,862`
montaban la URL con `track.id`, que en un `AppTrack` es
`TrackEntity.remoteTrackId` (`DatabaseExtensions.kt:19`) —y en lo importado de
Spotify es `spotify_<hashTítulo>_<hashArtistas>_<índice>`. El enlace no existía,
y como la misma variable va al tag NFC (`:145-155`) y a la recomendación de
Supabase (`:318-323`), el efecto llegaba hasta el feed público.

El `youtubeVideoId` correcto ya viajaba en `youtubeId` en esas mismas dos
llamadas, pero `ShareDialog` lo descartaba por precedencia (`item.shareUrl ?:
when { … }`, `QRDialog.kt:115`). El arreglo tiene dos mitades, y hacen falta
las dos:

1. **`ui/components/ShareUrlPolicy.kt`, nuevo.** Un `object` puro que decide la
   URL a partir de `(type, shareUrl, youtubeId)`. La regla que arregla el bug es
   que **gana el id real de YouTube** y una URL ya montada solo entra si no hay
   id. `TRACK` → `watch?v=<id>`; `APP` → siempre el enlace de descarga (compartir
   la app no es compartir una canción, así que un id colado no la convierte en un
   enlace de YouTube); `PLAYLIST` → si el id lleva prefijo de lista
   (`PL`/`UU`/`FL`/`RD`) arma `playlist?list=`, si no devuelve lo que vino.
2. **`QRDialog.kt:115-117`.** Las siete líneas de decisión en línea se sustituyen
   por una llamada a `ShareUrlPolicy.resolve(item.type, item.shareUrl, item.youtubeId)`.
   Ya no hay dos sitios que decidan la URL.
3. **`PlaylistScreen.kt:799,862`.** `shareUrl = null` en las dos rutas que leen
   pistas de la BD, para no volver a montar una URL con el id equivocado. Se deja
   que la política la construya con el `youtubeVideoId`.

Un detalle que salió de los tests: una `shareUrl` de solo espacios devolvía una
URL basura (`watch?v=  `) en vez de "no hay nada que compartir". Se normaliza
con `trim()` + `takeIf { isNotEmpty() }` sobre el fallback.

**Lo que NO arregla: B53.** El share de una *lista* sigue igual, porque la app no
guarda de qué servicio viene la lista y el tipo de URL se deduce por prefijos
del id (`youtube_` vs Spotify). Arreglarlo exige persistir el origen
(`source`/`sourceId` en `PlaylistEntity` con su migración de Room) o excluir el
compartir de las listas sin origen. Y las tres rutas de *resultados de búsqueda*
(`PlaylistScreen.kt:725,1248,1299`, `SearchScreen`, `YouTubeSearchResults`) se
dejan como estaban: ahí `track.id` **sí** es el `videoId` real de YouTube, y con
la nueva política también pasarían por `youtubeId`.

Verificado con `./run.sh test` (**356 tests, en verde**: 347 + 9 nuevos en
`ShareUrlPolicyTest`, que cubren el caso del bug —`shareUrl` con hash de Spotify
frente a `youtubeId` real—, la URL de la app, los cuatro prefijos de lista y los
ids en blanco) y `./run.sh build` (**BUILD SUCCESSFUL**, APK debug; comprobado
que `ShareUrlPolicy` está dentro del APK).

| # | Ubicación | Qué se hizo |
|---|---|---|
| **B52** | `ui/components/ShareUrlPolicy.kt` (nuevo) · `ui/components/QRDialog.kt:115-117` · `ui/PlaylistScreen.kt:799,862` · `test/.../ShareUrlPolicyTest.kt` (nuevo) | `PlaylistScreen` montaba la URL con `track.id`, que en un `AppTrack` es `TrackEntity.remoteTrackId` (`spotify_<hashTítulo>_<hashArtistas>_<índice>`), así que se compartía `youtube.com/watch?v=spotify_1234567_-987654_3`; y como la misma variable va al tag NFC y a la recomendación de Supabase, el enlace roto llegaba al feed público. El `youtubeVideoId` correcto ya viajaba en `youtubeId`, pero `ShareDialog` lo descartaba por precedencia (`item.shareUrl ?: when { … }`). **Arreglo en dos mitades, hacen falta las dos:** (1) la decisión de la URL sale del diálogo a `ShareUrlPolicy`, un `object` puro donde **gana el id real de YouTube** y una URL ya montada solo entra si no hay id — `TRACK` → `watch?v=<id>`, `APP` → siempre el enlace de descarga (un id colado no convierte compartir la app en compartir una canción), `PLAYLIST` → `playlist?list=` si el id lleva prefijo `PL`/`UU`/`FL`/`RD`, si no lo que vino; (2) `PlaylistScreen:799,862` dejan de montar la URL (`shareUrl = null`) y dejan que la política la construya. **B53 no se arregla**: el share de una lista sigue deduciendo el tipo de URL por prefijos porque no se guarda el origen de la lista. Salió de los tests: una `shareUrl` de solo espacios devolvía una URL basura (`watch?v=  `) en vez de "no hay nada que compartir" → `trim()` + `takeIf`. 9 tests nuevos. **+140 líneas, −17** |
