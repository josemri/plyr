# Reporte de análisis de PLYR

**Fecha:** 2026-10-06 (última tanda)
**Alcance:** `app/src/main/java/com/plyr` (79 archivos, ~16.100 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual, con cada hallazgo verificado leyendo el código. Lo que se da por cerrado se comprobó con `./run.sh test` (**413 tests, en verde**), `./run.sh build` y `./run.sh build release`. Esta tanda añade **25 tests más** (438 en total: 8 de B57, 9 de B58 y 8 de B59) que **aún no se han ejecutado**.

---

## 1. Estado

- **0 bugs abiertos.** Los **63** (B1–B63) están cerrados (§4): **B57 (el `>>` perdido), B58 (el spinner clavado), B59 (los saltos que no llegaban), B60 (la canción cancelada con la pantalla), B61 (botones de notificación Android ≤12), B62 (la ventana con huecos) y B63 (fin de cola: player en IDLE sin `prepare()`) se han corregido en esta tanda.
- **0 peticiones abiertas. F1 y F2 resueltas.** (`PlaylistLocalRepository.visiblePlaylists`, filtro por `trackCount > 0` que se aplica en los dos listados).
- **455 tests unitarios** en 35 archivos, **todos ejecutados y en verde** (Incluye `IdlePlaybackTest` y `WindowStateTest`). 0 instrumentados útiles.
- Código muerto grande **borrado** (−301 líneas): `SongMenuDialog`, `CollapsibleSection`, `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd`.
- **0 claves de traducción sin uso** y **0 claves referenciadas que no existen**, con dos tests que lo garantizan.
- Sync bidireccional con propagación de borrados (tombstones) verificado.
- R8 activo en release: APK de 4,9 MB sin firmar (no hay keystore local).

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 79 (~16.100 líneas) |
| Archivos de test | 35 (~5.400 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1413), `PlayerViewModel.kt` (1085), `ConfigScreen.kt` (665), `SongListItem.kt` (580), `FloatingMusicControls.kt` (538) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v8, migraciones `5→6`, `6→7`, `7→8` |
| Tests unitarios | **455** en 35 archivos (todos ejecutados y en verde) |
| Tests instrumentados | 0 útiles (solo `ExampleInstrumentedTest`) |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso / inexistentes | **0** / **0** |

## 3. Bugs abiertos

| Bug | Síntoma | Raíz | Estado |
|---|---|---|---|
| **B61** | En Android 12 y anteriores la notificación no tiene **ningún** botón | 0 llamadas a `addAction` en todo el proyecto | **Corregido:** `MusicService.buildNotification` añade acciones Prev/Play-Pause/Next con PendingIntent a `MediaButtonReceiver` para que los controles funcionen también en Android ≤12. |
| **B63** | Al terminar la cola, el `>` no hace nada y el título se queda en pantalla | `stopAtQueueEnd` deja `windowStart = 0` con la cola y `currentIndex` intactos; el player queda en IDLE sin `prepare()` | **Corregido:** lógica pura `IdlePlayback` + `PlayerViewModel.playPlayer()` resuelve IDLE sin items (reinicia desde ancla), IDLE con items (prepare+play), y `stopAtQueueEnd` limpia el título al terminar la cola. Test añadido `IdlePlaybackTest`. |

---

### B61 — En Android 12 y anteriores la notificación no tiene ningún botón

**Síntoma.** Con `minSdk = 24`, en dispositivos por debajo de Android 13 la notificación de reproducción saldría sin `<<`, play, `>>` ni nada: `buildNotification` no hace **ninguna** llamada a `NotificationCompat.Builder.addAction` en todo el proyecto (verificado con grep: 0 ocurrencias de `addAction`, `setShowActionsInCompactView` o `NotificationCompat.Action` en `app/src/main`).

**Causa.** A partir de Android 13 los controles derivan del `PlaybackState` (ver B57), pero en Android 12 y anteriores el sistema sigue pintando las acciones de la notificación — que aquí no existen. `MediaStyleNotificationHelper.MediaStyle(session)` (`MusicService.kt:168`) solo aporta el token de sesión; no genera botones.

**Impacto:** la app anuncia `minSdk = 24`, así que toda la franja 24–32 queda sin transporte en la notificación. Es el mismo agujero de fondo que explica por qué B50 apuntaba al sitio equivocado (B57): la notificación de esta app nunca ha tenido botones propios.

*(Inferencia de la documentación y del código, no probada en dispositivo — marcar al verificar.)*

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

**61 bugs, todos cerrados.** Agrupados por área para no perderlos:

> Nota sobre las referencias de línea: las citas `L###` de `PlayerViewModel.kt` apuntan al archivo **en el momento en que se documentó cada bug**. El fichero ha crecido desde entonces (de 935 a 1085 líneas con B58, B59, B60 y B62), así que hay que leerlas como "cerca de aquí", no como coordenadas exactas. Las citas a otros ficheros (`MusicService.kt`, `SongListItem.kt`…) siguen vigentes salvo indicación.

| Área | Bugs | Qué eran |
|---|---|---|
| **Reproducción** | B4, B5, B6, B35, B36, B42, B43, B46, B49, B50, B55, B56, B57, B58, B59, B60, B61, B62, B63 | La canción se cargaba dos veces; la URL caducada nunca se invalidaba; `_error` no se limpiaba al recuperar; los botones `<<` y `>>` de la notificación se quedaban fuera de la cola; la notificación fantasma decía "Plyr / Reproduciendo"; falta de acción `STOP`; `onServiceDisconnected` anulaba la sesión; el `>>` de la notificación desaparecía tras dos saltos seguidos y no volvía; el `resolving` quedaba clavado en `true` y dejaba el spinner y los cinco controles muertos para siempre; los saltos de la notificación y de los auriculares se descartaban en silencio mientras duraba una transición; la canción pedida se cancelaba con la pantalla y el error se tragaba; la notificación sin botones en Android ≤12; la ventana con huecos se desalineaba con la cola; al terminar la cola el reproductor quedaba en IDLE sin `prepare()` (los siete últimos, documentados abajo) |
| **Listas y favoritos** | B1, B2, B3, B9, B12, B13, B14, B15, B16, B18, B28, B48 | El swipe a *liked* **borraba** la canción; "añadir a lista" era un no-op; la lista no se refrescaba al añadir/quitar; duplicados al añadir; `<rnd>` desincronizaba la UI; `toggleLikeTrack` sin transacción; quitar un favorito de *Liked* no se reflejaba |
| **Compartir** | B19, B20, B30, B52, B53, B54 | El `share` de una canción mandaba el `remoteTrackId` de Spotify en vez del vídeo de YouTube (y llegaba al feed público); la URL de una lista era inválida; el diálogo se abría en blanco; el NFC no arrancaba; el QR se regeneraba en cada recomposición |
| **Sincronización** | B25, B40, B51 | Las fechas ilegibles de Supabase se disfrazaban de "ahora"; el Uri del SAF acababa en el cloud-backup; el sync resucitaba los favoritos que se habían borrado (arreglado con tombstones por clave) |
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
- **`stopAtQueueEnd` (fin de cola).** *Corregido* (es B63): ahora limpia el estado de UI para no dejar una pista fantasma y `playPlayer()` detecta IDLE sin items para reiniciar desde el ancla (windowAnchor). See §4 B63.

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

### B59 — Los saltos se descartaban en silencio mientras había una transición en vuelo (corregido)

**Síntoma.** Se pulsa `>>` en la notificación (o en los auriculares, `MediaButtonReceiver.kt:72-73`) y no pasa nada: ni salto, ni error, ni feedback. Dura mientras dure una resolución de red.

**Por qué pasaba.** Cuatro piezas que se cruzaban:

1. El sistema **cree que el salto ya se atendió**. La cadena es: `MediaSessionLegacyStub` → `MusicService.kt:104-112` (`onPlayerCommandRequest`) → `SessionSkipCommand.handle` → `SessionSkipCommand.kt:65-68`, que invoca el callback y devuelve **siempre** `SessionResult.RESULT_INFO_SKIPPED`. En media3, `dispatchSessionTaskWithPlayerCommand` comprueba (`msls.java:881-884`):

   ```java
   int resultCode = sessionImpl.onPlayerCommandRequestOnHandler(controller, command);
   if (resultCode != RESULT_SUCCESS) {
       // Don't run rejected command.
       return;
   }
   ```

   Como `RESULT_INFO_SKIPPED = 1 ≠ 0`, media3 **no ejecuta el comando en el reproductor**. Ese "que no lo ejecute el reproductor" es intencional (si no, el salto ocurriría dos veces), pero significa que **si la app después se guardaba el salto, no lo hacía nadie**: ni la app ni el player. Y para el controlador el resultado es un código de *info*, no un error.

2. `playIndex` empieza con un guard que devolvía sin hacer nada ni decir nada: `if (transitionInFlight) return`. El candado estaba puesto durante toda la resolución (hasta `MAX_RESOLUTION_SKIPS = 5` intentos de `resolveItems`, cada uno con timeout de `EXTRACTION_TIMEOUT_MS = 30_000` en `YouTubeManager.kt:17`). Todo ese rato, los saltos de notificación y de auriculares caían ahí.

3. El candado **se apagaba a mitad del cuerpo** y no en un `finally`: `transitionInFlight = false` estaba justo antes de `if (gen != generation) return@launch`, es decir, no se ejecutaba si la corrutina daba error, se cancelaba o salía por ahí. Y había escritores ajenos a la transición que lo ponían a `false` desde fuera (`startAt`, `clearPlayerState`, `stopAtQueueEnd`), con lo que **soltaban el candado de otra operación mientras seguía trabajando**: el siguiente salto entraba a ciegas con la cola sin mover. Mismos dos fallos que B58 (§4), otra vez sobre el mismo `Boolean`.

4. Además, `startAt` (tocar una canción de una lista) no adquiría el candado en absoluto, así que durante una carga normal de un tema desde la lista los saltos entraban con la cola a medio mover.

**Caso pariente: el final de canción también se descarta.** `onTrackEnded` empieza con `if (transitionInFlight) return`, y se ha dejado así **a propósito**: una transición en vuelo aplica su propio destino al terminar, y si alguien la invalidó es porque otra operación se hizo cargo (B58). Lo que sí estaba mal era la segunda mitad del escenario: si esa resolución se descartaba por cambio de generación sin que nadie la cerrara, el reproductor quedaba en `STATE_ENDED` sin nada que reproducir y sin que llegara otro evento — **parada permanente**. Eso ya lo cierra B58.

#### Corrección aplicada

La raíz era la misma enfermedad que B58 aplicada a otro `Boolean` —dueño, caducidad y escritores ajenos—, con la particularidad de que aquí el efecto no era un spinner clavado sino **un trabajo que se tira a la basura sin avisar**. Cuatro piezas:

**1. Se apuntan en vez de soltarse: `PendingSkips` (nueva clase pura, `viewmodel/PendingSkips.kt`).** Mientras hay una transición en vuelo, `navigateToNext`/`navigateToPrevious` registran la petición en una cola FIFO con tope (`MAX_PENDING = 5`) y devuelven. Al llenarse, **la que llega después se descarta** y el resto se conserva: una ráfaga de seis saltos seguidos tiene cero sentido, y lo que no tiene sentido es deshacer los cinco buenos. **El orden se respeta** y la dirección se vuelve a calcular *al aplicarla*, con el índice que la cola tiene entonces — que es justo lo que se quería pedir.

**2. El candado pasa a ser un registro con tokens: `transitions = LoadingState()`.** Segunda instancia de la clase de B58 (cargas en `loading`, transiciones en `transitions`): `playIndex` y `startAt` cogen un token con `beginTransition()` y se lo llevan a `endTransition(token)` **en su `finally`**. Con esto desaparecen a la vez los tres fallos del `Boolean`: el cierre ya no puede saltarse por error o cancelación, y nadie de fuera puede soltar el candado de una transición que no es suya (un `end()` de un token que ya no está en el conjunto es inocuo).

**3. `startAt` es una transición y también adquiere el candado**, justo antes de `beginLoading()`, y lo retira en su `finally`. Los escritores ajenos dejan de tocarlo: `clearPlayerState` se apoya en `invalidateLoads()` y `stopAtQueueEnd` no lo toca en absoluto (si está puesto, es de otra operación que sigue trabajando y soltarlo aquí la dejaría sin candado a medio camino).

**4. Toda invalidación pasa por `invalidateLoads()`, que además vacía los saltos apuntados.** El método hace ahora `generation++` + `loading.supersede()` + `transitions.supersede()` + `pendingSkips.clear()` + `updateLoadingState()`, y se usa en `setCurrentPlaylist`, `clearPlayerState` y `startAt`. `addToQueue` **no** lo usa: encolar al final no desplaza índices, no invalida nada y, sobre todo, no debe tirar un salto que el usuario acaba de pedir (mismo criterio que en B58).

**Drenaje.** Los dos `finally` hacen, en este orden: `endTransition(token)` → `drainPendingSkips()` → `endLoading(token)`. El orden importa: el candado se retira *antes* de drenar (si no, el bucle no entraría), y la carga se retira *después* (así, al encadenar saltos, el spinner no tiene ni un frame de parpadeo entre una transición y la siguiente). `drainPendingSkips()` consume peticiones en bucle mientras no haya candado y se detiene en cuanto una de ellas pone en marcha otra transición: esa volverá a llamar al terminar. Como todo corre síncrono en el hilo principal y cada vuelta consume una petición, **el bucle termina siempre**.

Detalles que evitan regresiones:

- **La invariante es:** cola no vacía ⟹ ningún `invalidateLoads()` se ha ejecutado desde que se pidió el salto. Por eso vaciar la cola es obligatorio en `invalidateLoads()` (cambiar de lista o tocar otra canción mientras se pedía un salto deja el pedido sin sentido, y aplicarlo apuntaría a la cola vieja).
- **No se duplica el salto:** `SessionSkipCommand` ya ha dicho al sistema que no lo ejecute, así que el único que lo aplica es el drenaje. Exactamente uno.
- **Los botones de la app no cambian de comportamiento:** siguen deshabilitados mientras hay carga (y ahora también transición), que es cuando no se podían calcular. Lo que gana es el transporte de notificación/auriculares, que no pasa por la UI de la app.
- **`onTrackEnded` conserva su guard** (ver "Caso pariente"): es correcto que un salto automático no se pille a sí mismo con la cola en movimiento.
- `transitionInFlight` ya no es una variable que se asigne: es `transitions.isLoading`, de solo lectura. El grep confirma que no queda ninguna asignación.

**Cobertura nueva:** `PendingSkipsTest` (8 tests): cola recién creada, entrega FIFO en el orden de llegada, `size` que sigue a `poll()`, el tope (con `limit = 2`: se admiten dos y la tercera no, y el tope cuenta lo que *queda*, no lo que ya pasó) y `clear()` (vaciando y ya vacía, y que vuelva a aceptar después). Es la parte pura; la orquestación (`invalidateLoads()`/drenaje) sigue sin estar cubierta porque vive en `PlayerViewModel` (§5.6).

---

### B62 — `resolved` con huecos desalineaba la ventana con la cola (corregido)

**Síntoma exacto.** Al saltar a una canción que estaba fuera de la ventana, sonaba una canción distinta de la que enseñaba la UI, o la ventana acababa con un item repetido; a partir de ahí `currentIndex` se desviaba.

**Causa.** `resolveItems` devuelve `mapIndexedNotNull`: descarta los nulos (las canciones que no se pudieron resolver) pero conserva los índices originales, así que la lista puede tener huecos. `playIndex` metía esa lista tal cual en `player.setMediaItems(...)`: con la ventana pedida `[5, 6, 7]` y el 6 fallando, quedaban items `[5, 7]` con `windowStart = 5`, y `fillTarget()` calculaba `lastCovered = 5 + 2 - 1 = 6` cuando el último preparado era en realidad el 7. El siguiente `growWindow` volvía a pedir el 7 (duplicado) mientras `currentIndex` se calculaba sobre índices equivocados. `fillWindow` ya protegía su propio tramo cortando en el primer hueco; **el que no lo hacía era `playIndex`**.

**Corrección.** `playIndex` recorta `resolved` en el primer hueco antes de cargar la ventana, con la misma lógica que ya usaba `fillWindow`: la nueva función pura `QueueIndex.contiguousPrefixLength` devuelve la longitud del tramo contiguo inicial, y solo ese tramo se pasa a `setMediaItems`. Lo recortado no se pierde: el `growWindow()` de justo después vuelve a pedirlo desde `lastCovered + 1`, así que un fallo transitorio se reintenta en el mismo gesto. La ventana termina siempre contigua, que es el invariante de la que dependen `fillTarget`, `trimWindow` y `syncIndexFromWindow`.

**Cobertura nueva:** 5 tests en `QueueIndexTest` (`contiguous_*`): tramo contiguo sin huecos, hueco al medio y hueco en el segundo elemento, rango que no empieza en 0, índice repetido y lista vacía. Como en los casos anteriores, está cubierta la decisión pura; la orquestación (`playIndex`) sigue sin test unitario (§5.6).

---

### B60 — La canción tocada en una lista se cancelaba con la pantalla y el error se tragaba (corregido)

**Síntoma exacto.** Se toca una canción (en una lista, en una playlist de búsqueda, en el feed) y no empieza a sonar: sin error, y el spinner puede ni siquiera llegar a aparecer. Bastaba con que la pantalla saliera de composición durante la resolución (navegación, *back*, cambio de pestaña).

**Causa.** `SongListItem.kt:221` lanzaba la resolución en el `rememberCoroutineScope()` de la pantalla y se tragaba cualquier excepción:

```kotlin
coroutineScope.launch {
    try {
        viewModel.loadAudioFromTrack(selectedTrackEntity)
    } catch (_: Exception) {
    }
}
```

La corrutina moría con la pantalla en mitad de `withContext(Dispatchers.IO)`, y `catch (_: Exception)` se tragaba también la `CancellationException` (en Kotlin es un `Exception`): el coroutine acababa en silencio, `clearPlayerState()` ya había vaciado el reproductor, y cola e `currentIndex` quedaban apuntando a algo que no estaba cargado. Mismos sitios: `FeedScreen.kt:184`, `PlaylistScreen.kt:330/352`, `YouTubePlaylistDetailView.kt:127/147` y `MainActivity.kt:155`.

**Corrección.** La resolución pasa a ser propiedad del ViewModel:

- **`PlayerViewModel.playTrack(track, onFinished)`** es la única puerta de entrada: lanza `loadAudioFromTrack` en `viewModelScope` (muere con el ViewModel, no con la pantalla) y el callback —que se invoca también si se cancela— le dice a la UI cuándo apagar su estado de "iniciando". `loadAudioFromTrack` queda `private`.
- **`cancelPendingPlayback()`** es el nuevo "stop": lo usan los botones de parar de `PlaylistScreen` (antes cancelaban los jobs que la propia pantalla tenía a mano).
- **`clearPlayerState()` cancela también la resolución en vuelo:** ya no apuntaría a un estado que acaba de vaciar.
- Las pantallas se reducen a una llamada: desaparecen los `launch` + `try/catch` de `SongListItem`, `YouTubePlaylistDetailView` y `PlaylistScreen`; `FeedScreen` deja de necesitar `suspend` en `handleRecommendationClick`/`playYoutubeVideo`; `MainActivity` pierde su `lifecycleScope.launch`. En `PlaylistScreen` se eliminan los jobs `randomJob`/`startJob` **y el `DisposableEffect` que los cancelaba al salir**, que era justo el disparo de este bug.
- Cancelar es siempre seguro: el `finally` de `startAt` (B58/B59) retira los candados por token da igual cómo acabe la corrutina.

**Cobertura:** sin tests nuevos — no hay lógica pura nueva: es un cambio de dueño del scope. La orquestación (`playTrack`/`clearPlayerState`) sigue sin estar cubierta (§5.6).

---

## 5. Pendiente

Nada de esto es un fallo de datos ni bloquea el uso: son mejoras.

### 5.1 Peticiones de comportamiento (lo prioritario)

- **F1 — `liked` vacía no debería aparecer como lista — RESUELTA.** La fila se creaba siempre al arrancar (`ensureLikedSongsPlaylist`) con `trackCount = 0` y ninguno de los dos listados la filtraba. Ahora ambos pasan por `PlaylistLocalRepository.visiblePlaylists`, que descarta `album_*` y `liked` con `trackCount == 0`, y mantiene el orden *liked* primero.
  Es un **filtro, no un borrado**: `toggleLikeTrack`, `mergeLikedSongsTracks` e `ImportManifest.plan` dan por hecho que la fila existe, y borrarla "si está vacía" habría roto las tres. `trackCount` lo mantienen las tres rutas que la modifican y `getAllPlaylists()` es un `Flow` sobre la tabla, así que el corazón aparece y desaparece solo. Cubierto con 4 tests en `DatabaseMappingsTest`.
- **F2 — Persistir el origen de la lista — RESUELTA.** Hoy `PlaylistShare.classify` deduce si una lista es de Spotify o de YouTube a partir del `remoteId` y de la description (`description == "Imported from Spotify"`), así que si el usuario edita la description la lista deja de ser compartible. Lo correcto es una columna `source`/`sourceId` en `PlaylistEntity` (+ migración) y que `ShareUrlPolicy`/`PlaylistShare` lean el origen guardado.
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

- `PlayerViewModel.kt` (1085) — monolito con **dos** banderas que hay que mantener coherentes a mano (`generation`, `windowStart`). Las otras dos ya no son banderas: `resolving` y `transitionInFlight` son registros con tokens (`loading` y `transitions`, dos instancias de `LoadingState`) — B58 y B59. La lógica pura ya está extraída (`QueueIndex`, `PlaylistLocalRepository.likedTrackOf`, `LoadingState`, `PendingSkips`, el núcleo de `YouTubeManager.getAudioUrl`); **el estado de la ventana no**. Es exactamente lo que ha producido B57, B58, B59, B60 y B62 (los cinco ya corregidos, §4) y B63 (§3), y es lo que habría que cubrir con tests JVM: el patrón de "banderas a mano que se resetean condicionalmente" ya es la fuente de los bugs más caros del reproductor.
- `PlaylistScreen.kt` (1422) — mezcla UI, red, DB y lógica de negocio.
- `MusicService.kt` — no es dueño del reproductor, solo proyecta la notificación sobre el `ExoPlayer` que vive en `PlayerViewModel`. El reparto es frágil: `SessionSkipCommand` (B49) decidió que la app atienda los saltos devolviendo `RESULT_INFO_SKIPPED`, y eso, sumado a los `return` mudos que tenía `playIndex`, **era** B59 (§4, ya corregido apuntando los saltos en `PendingSkips`). La notificación, además, no llega a tener botones propios (B57, B61).
- `ConfigScreen.kt` (665) y `SearchScreen.kt` (483) — Composables con carga, red y estado en `remember`/`rememberCoroutineScope`.

**Nota positiva:** el patrón de **extraer lógica pura testeable** está consolidado en `QueueIndex`, `MediaButtonCommand`, `CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`, `ExportDigest`, `UrlParser`, `AudioUrlExtractionTest`, `ShareUrlPolicy`, `PlaylistShare`, `NfcPulse`, `SessionSkipCommand`, `PlaybackNotificationState`, `LoadingState` y `PendingSkips`. Cero dependencias de Android y es el asset de calidad más valioso del repo: **el modelo a seguir**.

### 5.5 Limpieza e i18n

- **Literales de interfaz fuera de `Translations`** — refactor de amplio alcance, no un bug. Ejemplos: `QRDialog.kt:270` pone `"Compartir via"` en español fijo **tres líneas después** de usar `Translations.get` correctamente; `QueueScreen.kt:63` `"Unknown Artist"`, `SongListItem.kt:328` `"♥ liked"/"♡ like"`, más `SpotifyImporter.kt`, `YouTubeSearchManager.kt` y `PlaylistScreen.kt` con literals sueltos.
- **Literales sueltos menores:** 4 previews de Android Studio (se dejan, son herramienta de desarrollo), campos muertos de `ResponsiveDimensions`, `ActionButtonData.enabled` y la rama inalcanzable de `MediaCommand.NONE`.

### 5.6 Tests

1. **`PlayerViewModel` no tiene ningún test** — el mayor gap, y ya no es teórico: **B57, B58, B59, B60, B62 y B63 son bugs de esta clase que ningún test detectó** (de los seis, solo B63 sigue abierto — §3 —; los otros cinco están corregidos en §4, con tests sobre las piezas puras nuevas: `QueueNextCommandTest` y los casos de `AudioUrlExtractionTest`, `LoadingStateTest`, `PendingSkipsTest` y `QueueIndexTest.contiguous_*`; B60, al ser solo un cambio del dueño del scope, no añadió lógica pura que testear). `QueueIndex` sí está cubierta y es correcta; lo que no está cubierta es la *orquestación* (invalidación de URL caducada, limpieza de `_error`, cuándo recargar la ventana, quién invalida la carga en vuelo, qué pasa cuando `resolveItems` devuelve nulos, y ahora también cuándo se drenan los saltos apuntados). El estado de carga y el candado de transiciones ya viven en `LoadingState`, y los saltos aplazados en `PendingSkips`: los tres son puros y están testeados; **el de la ventana sigue dentro**, y sacarlo es la misma extracción que ya se hizo con `QueueIndex`.
2. **`SongListItem` no tiene ningún test** y su lógica de swipe (umbral, dirección, acción) está embebida en lambdas de `pointerInput`. El primer paso es extraer la decisión "offset → acción" a una función pura, como se hizo con `QueueIndex`.
3. **Tests instrumentados**: importación de playlist, escáner QR y escritura NFC no se pueden cubrir en JVM.

---

## 6. Análisis de calidad automatizado

Se montó una capa de análisis de código sobre Gradle, sin instalar nada más allá de lo que ya usa el build. Todo se ejecuta con un comando:

```
./run.sh check
```

que encadena tres cosas y imprime un resumen con las rutas de los informes:

| Herramienta | Task de Gradle | Qué detecta | Informe |
|---|---|---|---|
| **detekt 2.0.0-alpha.6** (con *type resolution*) | `:app:detektMain` | Código muerto (`Unused*`), estructura (`complexity`), bugs (`potential-bugs`) | `app/build/reports/detekt/debug.{md,html,xml,sarif}` |
| **Android Lint** | `:app:lint` | Recursos sin uso, `NewApi`, deuda de dependencias | `app/build/reports/lint-results-debug.{html,xml}` |
| **Kover 0.9.11** | `:app:koverXmlReport` `:app:koverHtmlReport` | Cobertura de los tests JVM (ejecuta los tests) | `app/build/reports/kover/report.xml`, `kover/html/index.html` |
| **R8 (en CI)** | `assembleRelease` | Código que el shrinker elimina por no alcanzable | `app/build/outputs/mapping/release/usage.txt` (artifact `r8-usage` en el workflow de release) |

Notas de configuración:

- `app/detekt.yml` está deliberadamente recortado: solo quedan activas las reglas de *sin uso* (`UnusedImport`, `UnusedParameter`, `UnusedPrivate*`, `UnusedVariable`, `VarCouldBeVal`), todo el ruleset `complexity` y `potential-bugs`. El resto (formato, naming, `MagicNumber`, `WildcardImport`…) está apagado a mano para que el informe diga solo lo que interesa. Las reglas `Unused*` **solo** corren con type resolution, por eso `check` usa `detektMain` y no `detekt`.
- `settings.gradle.kts`: el repositorio `google()` lleva ahora el mismo filtro de grupos que ya tenía `pluginManagement`; sin él, artefactos de Maven Central (el agent de Kover, el `asm` de lint) se buscan primero en `dl.google.com` y el build falla si ese host falla.
- AGP ya genera `usage.txt` por defecto con el informe de R8; no hace falta `-printusage` (dejaba un duplicado de 6,4 MB en `app/usage.txt`).
- **Estado actual: `./run.sh check` pasa en verde** (detekt 0 findings, lint 0 errores, cobertura sin umbral). La deuda de *estructura* (66 hallazgos de `complexity`) está congelada en `app/detekt-baseline.xml` (declarado en el bloque `detekt` de `app/build.gradle.kts`): lo que está ahí no falla, pero **cualquier hallazgo nuevo —de la regla que sea— sigue haciendo fallar `check`**. `./run.sh test`: 438/438 en verde.

### 6.1 Código muerto — corregido (19 → 0)

Se arreglaron los 19 hallazgos de detekt con su ubicación original: 6 imports sin uso (`SupabaseClient.kt:3`, `HomeScreen.kt:35`, `PlaylistScreen.kt:12`, `SearchScreen.kt:7`, `PlyrComponents.kt:8`, `Utils.kt:4`), 4 parámetros sin usar (`PlaylistLocalRepository.kt:211,213`, `ConfigScreen.kt:257`, `HomeScreen.kt:51`), 5 variables sin usar (`FloatingMusicControls.kt:387`, `PlaylistScreen.kt:100`, `SearchScreen.kt:61`, `DataSync.kt:315`, `PlayerViewModel.kt:828`), 1 propiedad privada sin uso (`YouTubeSearchManager.kt:28`), 3 `var` → `val` y 1 bloque inalcanzable (`CoverCache.kt:97`). Además `PlaylistScreen.kt` perdió su variable `isLoading` muerta.

**R8 (release, confirmación de inalcanzabilidad):**

- `com.plyr.ui.MenuOption` — clase entera eliminada; solo existe su definición en `AudioListScreen.kt:29`, sin ningún uso.
- `SupabaseClient.createGroup` y `SupabaseClient.joinGroup` — funciones eliminadas por R8 y sin ningún llamador fuera de `SupabaseClient.kt`; arrastran a `model.GroupMember`. **La feature "crear/unirse a grupo" está muerta** (en cambio `getGroups()` sí se usa desde `FeedScreen` y `QRDialog`).
- `DataExporter.exportTo` + `utils.ExportDigest` — sin llamadores fuera de `DataExporter.kt`; R8 elimina las dos lambdas del flujo y la clase `ExportDigest` entera. El export vivo es el de `DataSync`.
- `PlyrSymbols` aparece eliminada pero es solo el *inlining* de constantes (sus usos en `HomeScreen`/`PlyrComponents`/`YouTubeSearchResults` siguen ahí como literales): no es código a borrar.

**Lint — recursos sin uso:** 70 avisos, de los cuales **58 son falsos positivos**: los `drawable-nodpi/ascii_*.png` se referencian dinámicamente en `HomeScreen.kt:63` con `resources.getIdentifier("ascii_$i")`, que lint no sigue. Los 12 reales se corrigieron: colores de plantilla `purple_200/500/700`, `teal_200/700`, `black`, `white`, `splash_background_color` (borrados de `colors.xml`, que quedó solo con `black` porque lo usan los drawables del launcher) y el estilo `Theme_Plyr_SplashScreen_Fallback`. Quedan 58 falsos positivos de `UnusedResources` sin tocar (no se baselan; lint no falla con warnings).

### 6.2 Bugs reales — corregidos

- **`NewApi` (error lint):** `MainActivity.kt:90` llamaba a `startForegroundService()` con `minSdk 24` — crash en Android 7.x. Corregido con `ContextCompat.startForegroundService()`.
- **`ImplicitDefaultLocale` ×8** — `String.format`/`toLowerCase` sin `Locale` (`ConfigScreen.kt:415,419,486`, `CoverCache.kt:75`, `ExportDigest.kt:83`, `ExportManifest.kt:147`, `Utils.kt:73,75`): resultados distintos según el idioma del dispositivo. Corregido con `Locale.ROOT`/`Locale.getDefault()` explícitos.
- **`NonObservableLocale` (error lint, introducido por el fix anterior)** — `ConfigScreen.kt` leía `Locale.getDefault()` fuera del ciclo de Compose; corregido con `LocalConfiguration.current.locales[0]` + `.format(locale, …)`.
- **`UnreachableCode`** en `CoverCache.kt:97` y **`UnnecessarySafeCall`** ×2 (`SimpleDownloader.kt:135`, `SpotifyImporter.kt:191`) — en OkHttp 5 `response.body` ya no es nullable; eliminados el `?.`/`?:` muertos.
- **`UnsafeCallOnNullableType` ×22 (`!!`)** — todos con `if (x != null) { x!!.… }` sobre propiedades delegadas de Compose (el smart-cast no aplica). Reescritos capturando un `val` local: `MusicService.kt:139` (usaba `lastNotification!!` justo tras asignarlo), `NfcReader.kt:48`, `MediaMetadataExtractor.kt:63` (`requireNotNull` por invariante), `QueueScreen.kt` ×5, `ConfigScreen.kt` ×2, `SearchScreen.kt:183`, `YouTubePlaylistDetailView.kt:216` y `PlaylistScreen.kt` ×10 (`selectedPlaylist!!`/`pendingPlaylist!!`). Todos tenían el null-check inmediatamente encima, así que el comportamiento es idéntico — y donde no había garantía (`PlaylistScreen` compartir), ahora el diálogo simplemente no se muestra en vez de crashear.

### 6.3 Estructura (dónde está lo más enrevesado)

`detekt` pasó de 119 a 66 hallazgos al corregir todo lo anterior; los 66 restantes son puro `complexity` y están congelados en `app/detekt-baseline.xml`, concentrados en los mismos ficheros (los que ya apunta §5.4): `PlaylistScreen.kt` (1422 líneas: `LongMethod` ×2, `CyclomaticComplexMethod` ×2, `TooManyFunctions`…), `ConfigScreen.kt` (×3 métodos largos), `FloatingMusicControls.kt` (×4), `SearchScreen.kt` (×3), `PlayerViewModel.kt` (`CyclomaticComplexMethod` en `:557`), `SongListItem.kt`, `QRDialog.kt`, `YouTubePlaylistDetailView.kt`. Además `PlaylistLocalRepository`, `TrackDao`, `Config` e `ImportManifest` superan el máximo de funciones por archivo/clase. Es deuda concentrada: refactorizar esos 8-10 ficheros y regenerar el baseline (`./gradlew :app:detektBaselineMain`) cubriría ~55 de los 66 avisos.

### 6.4 Cobertura

Global: **13,6 % de líneas** (1111/8176) y 13,2 % de ramas. Por paquete:

- `com.plyr.ui` + `ui.components` + `ui.components.search` + `ui.theme`: **~0 %** (ningún test toca Compose — coherente con que los tests son JVM).
- `com.plyr.utils`: 40 % (676/1701) — es donde vive lo testado hoy.
- `com.plyr.database`: ~5 % (los `*_Impl` de Room arrastran el número).
- `com.plyr.viewmodel`: 8 % — confirma el gap de §5.6.1 (`PlayerViewModel` sin tests).
- `com.plyr.network`: 24 %, `com.plyr.service`: 39 %.

El número interesa como *tendencia* (sube si se cubre `viewmodel`/lógica pura nueva), no como objetivo por sí mismo: el UI Compose no se va a cubrir con Kover JVM.
