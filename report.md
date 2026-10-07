# Reporte de análisis de PLYR

**Fecha:** 2026-10-06 (última tanda)
**Alcance:** `app/src/main/java/com/plyr` (78 archivos, ~16.000 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual, con cada hallazgo verificado leyendo el código. Lo que se da por cerrado se comprobó con `./run.sh test` (**413 tests, en verde**), `./run.sh build` y `./run.sh build release`. Esta tanda añade **17 tests más** (430 en total: 8 de B57 y 9 de B58) que **aún no se han ejecutado**.

---

## 1. Estado

- **5 bugs abiertos (B59–B63), documentados en §3.** Los **58** anteriores (B1–B58) siguen resueltos (§4); **B57 (el `>>` perdido) y B58 (el spinner clavado) se han corregido en esta tanda** — §4.
- **B59, B60 y B63 son los "la canción nunca llega a reproducirse y se queda sin hacer nada"** (cada uno por un motivo distinto: salto descartado en silencio, corrutina cancelada con la pantalla, y player que queda en IDLE). B58 era el cuarto de la lista y era el mismo síntoma con el flag de carga clavado: ya está corregido (§4). Van encadenados: el agujero de B57 (una cola que no llega a rellenarse) era lo que abría la puerta a B58/B59.
- **1 petición abierta** (§5.1): F2. **F1 está resuelta** (`PlaylistLocalRepository.visiblePlaylists`, filtro por `trackCount > 0` que se aplica en los dos listados).
- **430 tests unitarios** en 33 archivos — los **17 nuevos** (6 de `QueueNextCommandTest` + 2 de `AudioUrlExtractionTest`, que cubren B57; y 9 de `LoadingStateTest`, que cubren B58) **faltan por ejecutar**. 0 instrumentados útiles.
- Código muerto grande **borrado** (−301 líneas): `SongMenuDialog`, `CollapsibleSection`, `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd`.
- **0 claves de traducción sin uso** y **0 claves referenciadas que no existen**, con dos tests que lo garantizan.
- Sync bidireccional con propagación de borrados (tombstones) verificado.
- R8 activo en release: APK de 4,9 MB sin firmar (no hay keystore local).

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 78 (~16.000 líneas) |
| Archivos de test | 33 (~5.270 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1422), `PlayerViewModel.kt` (935), `ConfigScreen.kt` (665), `SongListItem.kt` (580), `FloatingMusicControls.kt` (538) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7, migraciones `5→6` y `6→7` |
| Tests unitarios | **430** en 33 archivos (413 ejecutados y en verde; **17 de B57 y B58 sin ejecutar**) |
| Tests instrumentados | 0 útiles (solo `ExampleInstrumentedTest`) |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso / inexistentes | **0** / **0** |

## 3. Bugs abiertos

**5 bugs (B59–B63).** Solo informe: no se ha tocado código en ellos. B59 es el último de los tres reportados desde fuera que sigue abierto ("la canción nunca llega a reproducirse y se queda sin hacer nada"); los demás han salido de la misma zona de código. **B57 y B58, los otros dos reportados, están corregidos y documentados en §4.**

| Bug | Síntoma | Raíz |
|---|---|---|
| **B59** | `<<`/`>>` de la notificación o de los auriculares no hacen nada durante varios segundos | `playIndex` descarta en silencio el salto con `transitionInFlight`, y el sistema ya lo ha dado por atendido |
| **B60** | Tocar una canción de una lista puede no iniciar nada y sin error | La resolución corre en el `rememberCoroutineScope()` de la pantalla y `catch (_: Exception)` se traga la cancelación |
| **B61** | En Android 12 y anteriores la notificación no tiene **ningún** botón | 0 llamadas a `addAction` en todo el proyecto |
| **B62** | Suena una canción distinta de la que muestra la UI, o se cuela una duplicada en la ventana | `resolveItems` elimina los nulos y `playIndex` pasa el resultado con huecos a `setMediaItems` |
| **B63** | Al terminar la cola, el `>` no hace nada y el título se queda en pantalla | `stopAtQueueEnd` deja `windowStart = 0` con la cola y `currentIndex` intactos; el player queda en IDLE sin `prepare()` |

---

### B59 — Los saltos se descartan en silencio mientras hay una transición en vuelo

**Síntoma.** Se pulsa `>>` en la notificación (o en los auriculares, `MediaButtonReceiver.kt:72-73`) y no pasa nada: ni salto, ni error, ni feedback. Dura mientras dure una resolución de red.

**Causa.** Dos piezas que se cruzan:

1. `playIndex` empieza con dos guardas que devuelven sin hacer nada ni decir nada (`PlayerViewModel.kt`):

   ```kotlin
   if (transitionInFlight) return      // L446
   val player = _exoPlayer ?: return   // L447
   ```

2. El sistema **cree que el salto ya se atendió**. La cadena es: `MediaSessionLegacyStub` → `MusicService.kt:104-112` (`onPlayerCommandRequest`) → `SessionSkipCommand.handle` → `SessionSkipCommand.kt:65-68`, que invoca el callback y devuelve **siempre** `SessionResult.RESULT_INFO_SKIPPED`. En media3, `dispatchSessionTaskWithPlayerCommand` comprueba (`msls.java:881-884`):

   ```java
   int resultCode = sessionImpl.onPlayerCommandRequestOnHandler(controller, command);
   if (resultCode != RESULT_SUCCESS) {
       // Don't run rejected command.
       return;
   }
   ```

   Como `RESULT_INFO_SKIPPED = 1 ≠ 0`, media3 **no ejecuta el comando en el reproductor**. Ese "que no lo ejecute el reproductor" es intencional (si no, el salto ocurriría dos veces), pero significa que **si la app después se guarda el salto, no lo hace nadie**: ni la app ni el player. Y para el controlador el resultado es un código de *info*, no un error.

`transitionInFlight` está en `true` durante toda la resolución de `playIndex` (L462 → L501), que encadena hasta `MAX_RESOLUTION_SKIPS = 5` intentos de `resolveItems`, cada uno con timeout de 30 s. Todo ese rato, los saltos de notificación y de auriculares no hacen nada.

**Caso pariente: el final de canción también se descarta.** `onTrackEnded` (L675) empieza con `if (transitionInFlight) return`. Si la canción termina mientras hay una resolución en vuelo, el `STATE_ENDED` se ignora. Si además esa resolución se descarta por cambio de generación (el descarte sigue sin avisar, aunque el estado de carga ya no se queda clavado en `true` — B58, §4), el reproductor queda en `STATE_ENDED` sin nada que reproducir, sin que se haya publicado ningún error y sin que llegue otro evento: **parada permanente**. Es el otro lado de "no llega a reproducirse".

---

### B60 — La canción tocada en una lista se cancela con la pantalla y el error se traga

**Síntoma.** Se toca una canción (en una lista, en una playlist de búsqueda, en el feed) y no empieza a sonar; no hay error, y el spinner puede ni siquiera llegar a aparecer. Basta con que la pantalla salga de composición durante la resolución.

**Causa.** `SongListItem.kt:221-226`:

```kotlin
coroutineScope.launch {
    try {
        viewModel.loadAudioFromTrack(selectedTrackEntity)
    } catch (_: Exception) {
    }
}
```

- `coroutineScope` es el `rememberCoroutineScope()` de la pantalla (`PlaylistScreen.kt:93`, `PlaylistScreen.kt:1148`, etc.). Se cancela al salir de composición (navegación, *back*, cambio de pestaña) mientras `startAt` está en mitad de `withContext(Dispatchers.IO) { … }` resolviendo por red.
- `startAt` propaga la `CancellationException` (L416-417) tras cerrar su `finally`, y `catch (_: Exception)` **la traga igual**: en Kotlin `CancellationException` es un `Exception`. El coroutine acaba en silencio.
- Consecuencia: la canción pedida no se reproduce, `clearPlayerState()` ya había vaciado el reproductor (L327-333), la cola y `currentIndex` quedan apuntando a algo que no está cargado, y no hay mensaje de error. La app "no hace nada".

**Casos iguales:** `FeedScreen.kt:184`, `PlaylistScreen.kt:330/352`, `YouTubePlaylistDetailView.kt:127/147`, `MainActivity.kt:155` — todos lanzan `loadAudioFromTrack` sin observar el resultado (`Boolean`) ni manejar excepciones. `SongListItem` incluso descarta el `Boolean`.

---

### B61 — En Android 12 y anteriores la notificación no tiene ningún botón

**Síntoma.** Con `minSdk = 24`, en dispositivos por debajo de Android 13 la notificación de reproducción saldría sin `<<`, play, `>>` ni nada: `buildNotification` no hace **ninguna** llamada a `NotificationCompat.Builder.addAction` en todo el proyecto (verificado con grep: 0 ocurrencias de `addAction`, `setShowActionsInCompactView` o `NotificationCompat.Action` en `app/src/main`).

**Causa.** A partir de Android 13 los controles derivan del `PlaybackState` (ver B57), pero en Android 12 y anteriores el sistema sigue pintando las acciones de la notificación — que aquí no existen. `MediaStyleNotificationHelper.MediaStyle(session)` (`MusicService.kt:168`) solo aporta el token de sesión; no genera botones.

**Impacto:** la app anuncia `minSdk = 24`, así que toda la franja 24–32 queda sin transporte en la notificación. Es el mismo agujero de fondo que explica por qué B50 apuntaba al sitio equivocado (B57): la notificación de esta app nunca ha tenido botones propios.

*(Inferencia de la documentación y del código, no probada en dispositivo — marcar al verificar.)*

---

### B62 — `resolved` con huecos desalinea la ventana con la cola

**Síntoma.** Al saltar a una canción que está fuera de la ventana, suena una canción distinta de la que enseña la UI, o la ventana acaba con un item repetido, y a partir de ahí `currentIndex` se desvía.

**Causa.** `resolveItems` devuelve `mapIndexedNotNull`: descarta los nulos (canciones que no se pudieron resolver) pero conserva los índices originales. `playIndex` hace (L512-517):

```kotlin
val start = resolved.first().index
windowStart = start
setCurrentIndex(start)
player.setMediaItems(resolved.map { it.mediaItem }, 0, C.TIME_UNSET)
```

Si la ventana pedida era `[5, 6, 7]` y el 6 falló, `resolved = [5, 7]` → `windowStart = 5` y los items del reproductor quedan `[5, 7]`. A partir de ahí `syncIndexFromWindow` / `growWindow` asumen contigüidad: `lastCovered = windowStart + mediaItemCount - 1 = 6`, cuando en realidad el `1` es la cola 7 → el siguiente `growWindow` vuelve a pedir el 7 (duplicado) y `currentIndex` se calcula sobre índices equivocados.

`growWindow` a sí mismo sí protege su tramo con el check de `contiguous` (L572-579); **el que no lo hace es el `setMediaItems` de `playIndex`**, que mete la lista directamente.

---

### B63 — Tras `stopAtQueueEnd` la UI apunta a una canción que ya no está cargada

**Síntoma.** Termina la cola (o un `giveUp` tras varios fallos) y a partir de ahí el `>` de los controles no hace nada. El título de la canción sigue en pantalla. Nada indica que la reproducción haya terminado.

**Causa.** `stopAtQueueEnd` (L727-736):

- `player.stop()` + `player.clearMediaItems()` → el player queda en **IDLE** sin items.
- `windowStart = 0` (L732) **pero no** `currentIndex` ni `queue`: siguen apuntando a la última canción, y `_currentTitle` no se limpia.
- `transitionInFlight = false` y `updateLoadingState()` → `_isLoading = false`, así que los controles quedan **habilitados** y muestran `>` (porque `player.isPlaying` es falso).

Entonces:

- Pulsar `>` → `playPlayer()` = `_exoPlayer?.play()` (L251) sobre un player en IDLE **sin items y sin `prepare()`** → no pasa nada, sin error.
- Cualquier intento de rellenar después (`addToQueue` L305 o `updateRepeatMode` L284 → `growWindow`) añade items a un player que nadie ha preparado: ExoPlayer no sale de IDLE automáticamente (masking timeline: *"never move out of IDLE automatically"*), así que **los items se cargan y no suenan**. Y lo hacen con `windowStart = 0` contra un `currentIndex` que puede ser cualquier cosa → la UI enseña una canción y el reproductor tiene otra (o la primera de la cola).

Es el escenario más plano de "no llega a reproducirse y se queda sin hacer nada": no hay excepción, no hay error, no hay spinner; la app parece lista y los botones no hacen nada.

---

## 4. Bugs resueltos

**58 bugs, todos cerrados.** Agrupados por área para no perderlos:

| Área | Bugs | Qué eran |
|---|---|---|
| **Reproducción** | B4, B5, B6, B35, B36, B42, B43, B46, B49, B50, B55, B56, B57, B58 | La canción se cargaba dos veces; la URL caducada nunca se invalidaba; `_error` no se limpiaba al recuperar; los botones `<<` y `>>` de la notificación se quedaban fuera de la cola; la notificación fantasma decía "Plyr / Reproduciendo"; falta de acción `STOP`; `onServiceDisconnected` anulaba la sesión; el `>>` de la notificación desaparecía tras dos saltos seguidos y no volvía; el `resolving` quedaba clavado en `true` y dejaba el spinner y los cinco controles muertos para siempre (los dos últimos, documentados abajo) |
| **Listas y favoritos** | B1, B2, B3, B9, B12, B13, B14, B15, B16, B18, B28, B48 | El swipe a *liked* **borraba** la canción; "añadir a lista" era un no-op; la lista no se refrescaba al añadir/quitar; duplicados al añadir; `<rnd>` desincronizaba la UI; `toggleLikeTrack` sin transacción; quitar un favorito de *Liked* no se reflejaba |
| **Compartir** | B19, B20, B30, B52, B53, B54 | El `share` de una canción mandaba el `remoteTrackId` de Spotify en vez del vídeo de YouTube (y llegaba al feed público); la URL de una lista era inválida; el diálogo se abría en blanco; el NFC no arrancaba; el QR se regeneraba en cada recomposición |
| **Sincronización** | B40, B51 | El Uri del SAF acababa en el cloud-backup; el sync resucitaba los favoritos que se habían borrado (arreglado con tombstones por clave) |
| **Compose / UI / rendimiento** | B7, B8, B11, B17, B23, B26, B31, B34, B44, B47 | El gesto de swipe sin claves se rompía al cambiar de canción; la cámara no liberaba executor ni `unbind`; el polling de `SharedPreferences` cada 100 ms; `onThemeChanged` duplicado; AIOOBE en el sensor de luz; permiso de cámara denegado sin salida; el sync se cancelaba al cambiar de pestaña |
| **Red / seguridad / build** | B32, B33, B38, B39, B41 | El mapa de cookies no estaba sincronizado y los logs volcaban cookies, cabeceras y cuerpos; `optString(key, null)`; receiver exportado sin permiso; R8 roto en release; `WAKE_LOCK` sin uso |
| **i18n** | B21, B22, B24, B27, B45 | Valores japoneses dentro del mapa `català`, claves duplicadas, `"$ load_error"` literal, "1,5K" en `es-ES`, locale hardcodeado a `es-ES` |
| **Limpieza** | B10, B29, B37 | Constante duplicada, parámetro muerto, y `isValidAudioUrl` (código muerto) con 13 tests que la **certificaban** en vez de detectarla |

---

### B57 — El `>>` de la notificación desaparecía tras dos saltos seguidos y no volvía (corregido)

**Síntoma exacto.** Reproduciendo, se pulsa `>>` dos veces seguidas. En el segundo salto el botón siguiente de los controles de reproducción (Android 13+) desaparece y no reaparece mientras dure la canción. El `<<` **se mantiene**: la asimetría es parte del diagnóstico.

#### Cómo decide Android si ese botón existe

Cadena verificada leyendo media3 1.10.0 (la versión del proyecto, `gradle/libs.versions.toml`):

1. La app va con `targetSdk = 36` → en Android 13+ el sistema pinta los controles de reproducción a partir del `PlaybackState` de la `MediaSession`, **no** a partir de las acciones de la notificación (docs Android: *"action buttons on media controls are derived from the Player state"*; el slot *Next* exige `COMMAND_SEEK_TO_NEXT` o `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`).
2. `MediaSessionLegacyStub.createPlaybackStateCompat` construye ese `PlaybackState` con `convertCommandToPlaybackStateActions` (`msls.java:1974`): `COMMAND_SEEK_TO_NEXT` **y** `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` → `ACTION_SKIP_TO_NEXT` (`msls.java:1991-1993`).
3. De ahí sale el `>>`: `Util.getAvailableCommands` (`util3.java:3878-3906`) incluye `COMMAND_SEEK_TO_NEXT` solo si `hasNextMediaItem()` (o si el item es live/dynamic), y `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` solo si `hasNextMediaItem()`. Es decir: **el `>>` existe si y solo si la ventana de ExoPlayer tiene un item después del actual.**
4. En cambio `COMMAND_SEEK_TO_PREVIOUS` se añade con `!isTimelineEmpty && (hasPreviousMediaItem || !isCurrentMediaItemLive || isCurrentMediaItemSeekable)` (`util3.java:3892-3895`): para una canción normal `!isCurrentMediaItemLive` ya es cierto, así que **`<<` está siempre disponible aunque no haya canción anterior**. Esa es la razón de la asimetría del síntoma: solo desaparece el `>>`.
5. `ExoPlayerImpl.updateAvailableCommands()` (`epoi.java:2564`) detecta el cambio y emite `EVENT_AVAILABLE_COMMANDS_CHANGED` (`epoi.java:2569`); `MediaSessionLegacyStub.onAvailableCommandsChangedFromPlayer` (`msls.java:1371`) es el único sitio que llama a `updateLegacySessionPlaybackState`. O sea: **el estado se actualiza bien, y solo cuando cambian los comandos del reproductor.**

#### Por qué el segundo salto es el que lo mata

En `PlayerViewModel.kt`:

- `WINDOW_AHEAD = 2` (L51), `KEEP_BEHIND = 1` (L69). Tras `startAt(i)` la ventana de ExoPlayer es `[i, i+1, i+2]`: `growWindow` rellena hasta `min(i + 2, size - 1)` (L542, L549).
- **Salto 1** → la canción actual pasa a ser `i+1`; detrás sigue `i+2` → `hasNextMediaItem()` cierto → el botón sigue ahí.
- **Salto 2** → la actual es `i+2`, **que es el último item de la ventana** → `hasNextMediaItem()` falso → en ese instante `updateAvailableCommands` emite el evento, media3 retira `ACTION_SKIP_TO_NEXT` del `PlaybackState` y el sistema borra el `>>`.

Para que vuelva hace falta que `growWindow()` añada `i+3`/`i+4` con `addMediaItems`: eso vuelve a cambiar `hasNextMediaItem()`, vuelve a disparar `EVENT_AVAILABLE_COMMANDS_CHANGED` y el sistema repinta el slot. **Nada más lo podía devolver** (ver el punto siguiente).

#### Por qué no volvía: dos fallos encadenados en el relleno

_Los tres puntos siguientes describen el código **antes** de la corrección; los números de línea son los de entonces._

**1. El trabajo del salto 1 se cancela en el salto 2 y se pierde lo ya extraído.** `growWindow` cambia `prefetchRange` de `"g:i+3-i+3"` (salto 1) a `"g:i+3-i+4"` (salto 2) y como ya no coincide, hace `prefetchJob?.cancel()` (L560-562) y relanza. El job cancelado estaba justo extrayendo el vídeo de `i+3`: `YouTubeManager.getAudioUrl` (`YouTubeManager.kt:90-114`) hace `withTimeoutOrNull { withContext(Dispatchers.IO) { extract() } }`. Al cancelarse, `withContext` lanza `CancellationException` **antes de que `url` reciba valor**, de modo que el `finally` (L106-112) no cachea nada y hace `mine.complete(null)`.

**2. El job nuevo hereda ese `null`.** El job del salto 2 pide **el mismo** vídeo `i+3`, hace `inFlight.putIfAbsent(videoId, mine)` (L98) y, si el job cancelado aún no ha ejecutado su `finally`, recibe `pending` y hace `pending.await()` (L99) → le llega `null` → `resolveItems` descarta esa canción. El envenenamiento es probable precisamente porque `resolveItems` trabaja con `chunked(RESOLVE_CONCURRENCY)`: el rango del salto 1 es de un solo track, así que el job cancelado está en `i+3` cuando llega el segundo salto.

**3. Y `growWindow` no reintenta.** L572-579: construye `contiguous` recorriendo `resolved` y **exige que el primer índice sea exactamente `from`**. Si `i+3` vino `null`, el primer índice es `i+4` → `contiguous` queda vacío → `if (contiguous.isEmpty()) return@launch` (L579) → no añade nada, no marca error, no reprograma. `prefetchRange` ya se limpió (L560) y `growWindow` solo se vuelve a llamar en la siguiente transición de item (`MusicService.kt:117-120`), en `updateRepeatMode` (L284) o en `addToQueue` (L305).

**Resultado:** la ventana se queda en `[i+1, i+2]` sin item siguiente, `hasNextMediaItem()` falso el resto de la canción y el `>>` apagado hasta que algo dispare otra transición. Además, como el trabajo se reinicia de cero (estaba cancelado), incluso cuando el relleno acaba funcionando el botón tarda varios segundos en volver — ventana en la que salta de nuevo un usuario que está saltando canciones.

#### Por qué el arreglo de B50 no puede ser el que lo recupere

- La app **nunca llama a `addAction`** (0 ocurrencias de `addAction` / `setShowActionsInCompactView` en `app/src/main`). `buildNotification` (`MusicService.kt:152-171`) solo pone título, texto y `MediaStyleNotificationHelper.MediaStyle(session)`. La notificación de la app **no lleva botones**; los que ve el usuario los pinta el sistema desde el `PlaybackState`.
- El comentario de `MusicService.kt:163-164` ("el sistema no pintaría botones") es un falso premisa: `MediaStyle` aporta el token de la sesión, no botones.
- `MediaSessionLegacyStub.onTimelineChanged` (`msls.java:1588-1595`) solo hace `updateQueue` + `updateMetadataIfChanged`: **no refresca el `PlaybackState`**. Por lo tanto el `REBUILD` de `NotificationRefreshPolicy.decide` en `TIMELINE_CHANGED` (`NotificationRefreshPolicy.kt:64`) repinta una notificación que no contiene los botones, y no puede devolver un `>>` que nunca estuvo en ella.
- El propio comentario de `NotificationRefreshPolicy.kt:22-24` y `MusicService.kt:122-127` asumen que repintar la notificación es lo que devuelve los botones. No lo es: quien los decide es el `PlaybackState`, y este sí se refresca — pero solo en `onAvailableCommandsChangedFromPlayer`, es decir, **solo si la ventana acaba ganando un item siguiente**, que es justo lo que falla (puntos 1-3).

En resumen: B50 arregló un repaint que no estaba en la cadena de decisión del botón. El botón se decidía en la ventana de ExoPlayer, y la ventana era lo que se rompía — que es exactamente lo que se ha corregido.

#### Casos relacionados con el mismo síntoma

- **Repetir todo en la última canción** *(también corregido)*. `wanted = minOf(currentIndex + WINDOW_AHEAD, queue.size - 1)` no envolvía: en la última, `wanted = size-1 = currentIndex` → no faltaba nada → no se rellenaba → la ventana terminaba en la actual y el `>>` se apagaba, mientras que `QueueIndex.nextIndex` sí envuelve con `REPEAT_MODE_ALL`. Como el botón ya no se decide con la ventana sino con la cola, la discrepancia ha dejado de importar.
- **La canción contigua no se puede resolver** *(corregido)*. El `contiguous` vacío ya no corta: `fillWindow()` espera al siguiente ciclo y reintenta hasta `MAX_FILL_ATTEMPTS`.
- **`stopAtQueueEnd` (giveUp, fin de cola).** *Sigue abierto* (es B63): `clearMediaItems` → `showMediaStyle` falso → `NotificationAction.REMOVE` → desaparece la notificación entera (`MusicService.kt:181-186`).

**Por qué es fácil de volver a reproducir:** basta con una cola de más de 5 canciones y dos saltos rápidos (< ~2 s de diferencia, el tiempo de extracción de `i+3`).

**Cobertura de tests:** `NotificationRefreshPolicyTest` solo verifica que se repinta la notificación y `SessionSkipCommandTest` que la decisión de salto es correcta. **Sigue sin haber ningún test de `PlayerViewModel`** (§5.6), donde vive la ventana.

#### Corrección aplicada

Tres piezas que atacan las dos raíces a la vez: la decisión del botón y la fiabilidad del relleno.

**1. El `>>` deja de depender de la ventana.** `QueueNextCommand` (lógica pura, al estilo de `SessionSkipCommand`) responde con la cola entera: `hasNextInQueue()` delega en `QueueIndex.nextIndex(...) != null`. `QueueAwarePlayer` — un `ForwardingPlayer` que envuelve al `ExoPlayer` en `MusicService.setupMediaSession` — añade esos dos comandos en `getAvailableCommands()` **e** `isCommandAvailable()` (`ForwardingPlayer.isCommandAvailable` delega en el de abajo sin pasar por `getAvailableCommands`, así que hay que sobrescribir los dos).

Dos detalles que lo hacen inocuo:

- `MediaSession` monta el `PlaybackState` con un `intersect` entre sus comandos y los del reproductor, así que **solo se puede añadir, nunca quitar**: con la cola sin siguiente el botón se apaga igual que antes. `MediaSessionLegacyStub.onAvailableCommandsChangedFromPlayer` dispara `updateLegacySessionPlaybackState` y el sistema repinta el slot en cuanto el estado cambia.
- El salto **no cambia de manos**: `MediaSessionLegacyStub.onSkipToNext` comprueba `isCommandAvailable(COMMAND_SEEK_TO_NEXT)` y, con el comando disponible, lo despacha; `SessionSkipCommand` lo intercepta y devuelve `RESULT_INFO_SKIPPED`, con lo que ExoPlayer nunca ejecuta `seekToNext()`. Sigue decidiendo la cola (con `REPEAT_MODE` y `stopAtQueueEnd` intactos), no la ventana.

Envolver el player es seguro: en el paquete `media3.session` no hay ningún `instanceof ExoPlayer`, y `createPlaybackStateCompat` trabaja contra la interfaz `Player`.

**2. El relleno vuelve a intentarlo.** `growWindow()` ya no calcula un `prefetchRange` para comparar ni cancela jobs de otro rango: es *single-flight* (`prefetchJob?.isActive == true` → no lanza nada más) y delega en `fillTarget()` + `fillWindow()`. Esta última comprueba el invariante `windowStart + player.mediaItemCount == target.from` antes de `addMediaItems` y, si el primer bloque no empieza en `from`, espera (`delay(FILL_RETRY_DELAY_MS)`) y reintenta hasta `MAX_FILL_ATTEMPTS`, en vez de devolver un `contiguous` vacío en silencio. Desaparece `prefetchRange`, con lo que desaparece la cancelación en cadena del punto 1.

**3. El envenenamiento de `getAudioUrl` desaparece.** `inFlight` ya no guarda `String?`, sino un `Extraction`: `Finished(url)` es un resultado real (`null` = fallo de verdad) e `Interrupted` significa "nadie llegó a extraer nada". El que espera hace `continue` en el segundo caso — vuelve a mirar la caché y se convierte él mismo en el productor — en vez de recibir `null` y descartar la canción. Si la extracción cancelada sí extrajo una URL, se cachea y se reparte igual.

**Cobertura nueva:** `QueueNextCommandTest` (6 tests sobre la decisión) y dos casos en `AudioUrlExtractionTest` (el productor cancelado y el esperador cancelado). La ventana en sí sigue sin estar cubierta: es el punto 1 de §5.6.

---

### B58 — El `resolving` quedaba clavado en `true`: spinner para siempre y todos los controles muertos (corregido)

**Síntoma.** La app mostraba `"$ loading"` en lugar del título (`FloatingMusicControls.kt:241-243`) y los cinco botones de los controles quedaban deshabilitados: `isEnabled = !isLoading` en `<<` (L431), play (L441), `>>` (L454) y repetir (L466), y `.clickable(enabled = !isLoading …)` en el de cola (L417). La canción pedida no sonaba y **no había ningún error**. Es literalmente "se queda sin hacer nada": ni se podía pausar ni avanzar ni repetir.

#### Por qué pasaba

_Los números de línea siguientes son los de antes de la corrección._

`playIndex` ponía `resolving = true` y su `finally` solo lo cerraba si la resolución seguía siendo la vigente:

```kotlin
} finally {
    if (gen == generation) {
        resolving = false
        updateLoadingState()
    }
}
```

Pero había tres caminos que hacían `generation++` **sin tocar `resolving`**:

- `addToQueue` → `generation++` (el botón "añadir a la cola" de `SongListItem.kt:374` y `SongListItem.kt:560`).
- `setCurrentPlaylist` → `generation++` (solo cambiaba índices y publicaba la canción; no limpiaba `resolving`).
- `startAt` → `generation++` (este sí lo compensaba, porque su `finally` cerraba sin condición).

`clearPlayerState` sí lo limpiaba, pero era el único. Si `addToQueue` se ejecutaba mientras `playIndex` estaba resolviendo, el `finally` de ese `playIndex` veía `gen != generation`, se saltaba el cierre y **nadie volvía a poner `resolving = false`**: `updateLoadingState()` publicaba `_isLoading = resolving || buffering = true` para siempre.

**El resultado descartado tampoco avisaba.** Antes del cierre, `playIndex` hacía:

```kotlin
transitionInFlight = false
if (gen != generation || _exoPlayer !== player) return@launch
```

La canción pedida se descartaba sin publicar error ni intentar reproducirse. Ídem en `startAt`: `if (gen != generation) return false`, silencioso.

**Cómo se recuperaba:** solo con `clearPlayerState()` (que es lo que hace `SongListItem.kt:217` al tocar una canción de una lista). Mientras tanto el usuario no podía usar los controles, porque estaban desactivados por el propio `isLoading`.

**Amplificado por B57:** con la ventana sin item siguiente, cada `>>` de la app (el que sí funciona, que pasa por `navigateToNext`) caía en la ruta **asíncrona** con resolución por red (hasta `EXTRACTION_TIMEOUT_MS = 30_000` por vídeo en `YouTubeManager.kt:17`), lo que alargaba muchísimo la ventana de tiempo en la que un `generation++` inocente orfanizaba el flag.

**Cobertura de tests:** ninguna al documentarlo; era la clase de invariante que exige tener `PlayerViewModel` testeado (§5.6). Ahora la parte extraíble sí está cubierta (§5.6 punto 1).

#### Corrección aplicada

La raíz era un `Boolean` compartido con un dueño y una caducidad: solo se podía apagar desde el `finally` que lo había puesto, y solo mientras su `generation` siguiera vigente. Se ha sustituido por un registro con **tokens de identidad** y se ha obligado a que toda invalidación pase por el mismo sitio.

**1. `LoadingState` (nueva clase pura, `viewmodel/LoadingState.kt`).** Ya no hay un flag, sino un conjunto de tokens: quien empieza a resolver coge un token con `begin()` y se lo lleva a `end(token)` cuando termina, **sea cual sea la `generation` con la que empezó**; mientras quede algún token, hay carga en vuelo. `supersede()` retira todos los de golpe y es lo que llama quien invalida el trabajo en curso.

Por qué con tokens y no con un contador simple: la retirada tardía de un token huérfano no encuentra nada que retirar, así que **no puede descuadrar al recuento de una operación más moderna**. Con `Boolean` el cierre condicional hacía exactamente lo contrario, y con un contador entero la operación invalidada acabaría apagando el spinner de la que la sustituyó.

**2. Todo `generation++` pasa por `invalidateLoads()`.** El método hace `generation++` + `loading.supersede()` + `updateLoadingState()`, y se usa en `setCurrentPlaylist`, `clearPlayerState` y `startAt`. Lo que faltaba en el bug era precisamente ese acoplamiento: había incrementos que no tocaban el estado de carga, y quien lo había puesto no lo cerraba porque su generación ya no era la vigente.

**3. `addToQueue` deja de hacer `generation++`.** Encolar al final **no desplaza ningún índice**, así que no invalida nada: una resolución en vuelo sigue siendo válida y el salto que el usuario estaba pidiendo se aplica al terminar. Ese incremento era el que más a menudo se lo tragaba en silencio, y de paso dejaba de anular saltos inocentes. También se le añade el `prefetchJob = null` que le faltaba junto a la cancelación.

**4. Los `finally` dejan de ser condicionales.** `playIndex` y `startAt` hacen `endLoading(token)` incondicionalmente (antes era `if (gen == generation)`), y publican `_error = null` *dentro* del `try`, de modo que una excepción al montar la operación no deja el token huérfano. `updateLoadingState()` pasa a derivar `_isLoading` de `loading.isLoading || buffering`.

Detalles que evitan regresiones:

- `startAt` ya no cierra el estado de forma incondicional *ajena* a su propia carga: era lo que solucionaba B36 (el spinner se apagaba a mitad de la re-resolución de `playIndex`), y con tokens se consigue sin tocarse la operación de nadie.
- `stopAtQueueEnd` sigue publicando su `false` final; el `finally` lo reafirma después, y entre medias no hay punto en el que el estado pueda quedarse en `true`.
- Los tres publicadores de `_isLoading` son ahora `updateLoadingState()` y `clearPlayerState()` (que lo fuerza a `false`); no queda ningún sitio que escriba el flag a mano.

**Cobertura nueva:** `LoadingStateTest` (9 tests): estado básico, dos cargas concurrentes que no se pisan, `supersede()`, y —la invariante del bug— que `end()` de una operación invalidada ni reenciende la suya ni apaga la de la operación más moderna. `PlayerViewModel` en sí sigue sin estar cubierto (§5.6), pero el estado de carga ya no depende de él.

---

## 5. Pendiente

Nada de esto es un fallo de datos ni bloquea el uso: son mejoras.

### 5.1 Peticiones de comportamiento (lo prioritario)

- **F1 — `liked` vacía no debería aparecer como lista — RESUELTA.** La fila se creaba siempre al arrancar (`ensureLikedSongsPlaylist`) con `trackCount = 0` y ninguno de los dos listados la filtraba. Ahora ambos pasan por `PlaylistLocalRepository.visiblePlaylists`, que descarta `album_*` y `liked` con `trackCount == 0`, y mantiene el orden *liked* primero.
  Es un **filtro, no un borrado**: `toggleLikeTrack`, `mergeLikedSongsTracks` e `ImportManifest.plan` dan por hecho que la fila existe, y borrarla "si está vacía" habría roto las tres. `trackCount` lo mantienen las tres rutas que la modifican y `getAllPlaylists()` es un `Flow` sobre la tabla, así que el corazón aparece y desaparece solo. Cubierto con 4 tests en `DatabaseMappingsTest`.
- **F2 — Persistir el origen de la lista.** Hoy `PlaylistShare.classify` deduce si una lista es de Spotify o de YouTube a partir del `remoteId` y de la description (`description == "Imported from Spotify"`), así que si el usuario edita la description la lista deja de ser compartible. Lo correcto es una columna `source`/`sourceId` en `PlaylistEntity` (+ migración) y que `ShareUrlPolicy`/`PlaylistShare` lean el origen guardado.
  Criterio de fondo de F2: **compartir siempre el origen real de lo que se comparte** (canción → su vídeo de YouTube; lista de Spotify → `open.spotify.com/playlist/<id>`, que la propia app ya sabe volver a abrir; lista creada en la app → nada que compartir).
- **Recorte de la ventana después del relleno.** `trimWindow()` recorta antes de que llegue el `addMediaItems`, así que durante uno o dos segundos el reproductor no tiene item siguiente y el botón `>>` parpadea; y si el relleno falla, la ventana se queda sin él. Mover el recorte *después* del `addMediaItems` cierra el hueco y, de rebote, mejora el `<<`. Toca el invariante de `windowStart`: va con tests. **Era un paliativo parcial de lo que era B57 (§4): cerraba el parpadeo, pero no el caso de que el relleno falle y no se reintente. Ese caso ya lo cubre `fillWindow`; queda el recorte en sí y el invariante de `windowStart`, que va con tests.**

### 5.2 Seguridad

- **S7 — PII en logcat.** `SupabaseClient.kt` (~42 `Log.*`) vuelca cuerpos completos de requests/responses: nicknames, códigos de invitación, nombres de grupo, URLs y comentarios de recomendaciones.
- **S9 —** La anon key (`sb_publishable_…`) está en el código; no es un secreto por diseño, pero **las políticas RLS de `groups`, `group_members`, `recommendations` y `automatic` no son verificables desde el repo** y son la única defensa de esos datos.
- **S10 —** Cookie de YouTube hardcodeada (`PREF=f2=8000000`): caduca en servidor sin aviso.
- **S12 —** La copia de seguridad todavía incluye el Uri del árbol SAF (B40 solo excluyó `plyr_config.xml`).

### 5.3 Rendimiento

- `FeedScreen.kt:47,65` — `metadataCache = metadataCache + (...)` reconstruye el mapa entero en cada insert (**O(n²)**) y es un read-modify-write no atómico desde N corrutinas; además esos `scope.launch` cuelgan de `rememberCoroutineScope()` y sobreviven a la cancelación del efecto.
- `FeedScreen.kt:74-107` y `YouTubeSearchResults.kt:217-218` — `Column` + `forEach` en vez de `LazyColumn`: se compone todo de golpe, y cada fila lanza su propia consulta (sin límite de concurrencia).
- `Theme.kt:136-140` — `unifiedTypography()` es `@Composable` y hace `return Typography(...)` **sin `remember`**: se reconstruyen 14 `TextStyle` en cada recomposición.
- `ConfigScreen.kt:184-188` — `PackageManager` + reflexión **dentro de la composición**.
- `CoverCropDialog.kt:235-236` — decodifica, escala y recorta un bitmap a tamaño completo en el hilo principal, dentro del manejador del clic.
- `SearchScreen.kt:425-437` — `trackEntities` se reconstruye en cada recomposición (no está en `remember`), así que los `SongListItem` recomponen sin parar.
- Claves de listas perezosas: 5 `items(...)` sin `key` (4 en el monolito `PlaylistScreen`) y `QueueScreen.kt:53-54` usa una clave inestable que incorpora la posición.
- `SongListItem.kt:105,224` — `.height(32.dp)` fijo en una `Box`/`Row` con dos líneas: **el artista se recorta en todas las listas**.

### 5.4 Arquitectura

- `PlayerViewModel.kt` (935) — monolito con **tres** banderas que hay que mantener coherentes a mano (`generation`, `windowStart`, `transitionInFlight`); el cuarto, `resolving`, se ha sacado de ahí como `LoadingState` (B58). La lógica pura ya está extraída (`QueueIndex`, `PlaylistLocalRepository.likedTrackOf`, `LoadingState`, el núcleo de `YouTubeManager.getAudioUrl`); **el estado de la ventana no**. Es exactamente lo que ha producido B57 y B58 (los dos ya corregidos, §4) y B59, B62 y B63 (§3), y es lo que habría que cubrir con tests JVM: el patrón de "banderas a mano que se resetean condicionalmente" ya es la fuente de los bugs más caros del reproductor.
- `PlaylistScreen.kt` (1422) — mezcla UI, red, DB y lógica de negocio.
- `MusicService.kt` — no es dueño del reproductor, solo proyecta la notificación sobre el `ExoPlayer` que vive en `PlayerViewModel`. El reparto es frágil: `SessionSkipCommand` (B49) decidió que la app atienda los saltos devolviendo `RESULT_INFO_SKIPPED`, y eso, sumado a los `return` mudos de `playIndex`, es B59 (§3). La notificación, además, no llega a tener botones propios (B57, B61).
- `ConfigScreen.kt` (665) y `SearchScreen.kt` (483) — Composables con carga, red y estado en `remember`/`rememberCoroutineScope`.

**Nota positiva:** el patrón de **extraer lógica pura testeable** está consolidado en `QueueIndex`, `MediaButtonCommand`, `CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`, `ExportDigest`, `UrlParser`, `AudioUrlExtractionTest`, `ShareUrlPolicy`, `PlaylistShare`, `NfcPulse`, `SessionSkipCommand`, `PlaybackNotificationState` y `LoadingState`. Cero dependencias de Android y es el asset de calidad más valioso del repo: **el modelo a seguir**.

### 5.5 Limpieza e i18n

- **Literales de interfaz fuera de `Translations`** — refactor de amplio alcance, no un bug. Ejemplos: `QRDialog.kt:270` pone `"Compartir via"` en español fijo **tres líneas después** de usar `Translations.get` correctamente; `QueueScreen.kt:63` `"Unknown Artist"`, `SongListItem.kt:328` `"♥ liked"/"♡ like"`, más `SpotifyImporter.kt`, `YouTubeSearchManager.kt` y `PlaylistScreen.kt` con literals sueltos.
- **Literales sueltos menores:** 4 previews de Android Studio (se dejan, son herramienta de desarrollo), campos muertos de `ResponsiveDimensions`, `ActionButtonData.enabled` y la rama inalcanzable de `MediaCommand.NONE`.

### 5.6 Tests

1. **`PlayerViewModel` no tiene ningún test** — el mayor gap, y ya no es teórico: **B57, B58, B59, B62 y B63 son bugs de esta clase que ningún test detectó** (de los cinco, B57 y B58 ya están corregidos — §4 —, con tests sobre sus piezas nuevas: `QueueNextCommandTest` y los casos de `AudioUrlExtractionTest`, y `LoadingStateTest`; los otros tres siguen abiertos en §3). `QueueIndex` sí está cubierta y es correcta; lo que no está cubierta es la *orquestación* (invalidación de URL caducada, limpieza de `_error`, cuándo recargar la ventana, quién invalida la carga en vuelo, qué pasa cuando `resolveItems` devuelve nulos). El estado de carga ya vive en `LoadingState`, que es puro Kotlin y está testeado; **el de la ventana sigue dentro**, y sacarlo es la misma extracción que ya se hizo con `QueueIndex`.
2. **`SongListItem` no tiene ningún test** y su lógica de swipe (umbral, dirección, acción) está embebida en lambdas de `pointerInput`. El primer paso es extraer la decisión "offset → acción" a una función pura, como se hizo con `QueueIndex`.
3. **Tests instrumentados**: importación de playlist, escáner QR y escritura NFC no se pueden cubrir en JVM.
