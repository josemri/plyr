# Reporte de análisis de PLYR

**Fecha:** 2026-09-22 (auditoría original) · **Revisado:** 2026-09-28
**Alcance:** `/home/josep/plyr/app/src/main/java/com/plyr` (67 archivos, ~13.452 líneas Kotlin) + Gradle/Manifiesto.
**Método:** auditoría estática manual + verificación contra el código de cada hallazgo.

> **Estado de verificación (2026-09-28):** `./run.sh test` → **268/268 tests en
> verde** (18 clases, 0 fallos, 0 errores, 0 omitidos) y `./run.sh build` →
> **BUILD SUCCESSFUL**, APK generado. El recuento de tests de §2 y §8 está por
> tanto **verificado de verdad**, no estimado.
>
> La primera ejecución de `./run.sh test` **no llegó a compilar**: `createMediaItem`
> exige un parámetro `queueIndex` que la ruta de precarga no pasaba
> (`PlayerViewModel.kt:552`). Ojo con `print_test_summary` de `run.sh`: lee
> cualquier XML que encuentre en `app/build/test-results/`, así que con la
> compilación rota llegó a mostrar "237 tests ✔" usando resultados de una corrida
> anterior. **Un resumen en verde no demuestra que los tests se ejecutaran**;
> hay que mirar también el `exit code` y que la fecha de los XML sea de ahora.
>
> Lo que sigue sin poder verificarse sin dispositivo es el comportamiento en
> runtime: que el salto entre canciones funcione con red real, con y sin caché.

---

## 1. Resumen ejecutivo

PLYR es una app de música **YouTube-only + local** que compila y funciona. Buen estado general: migración Spotify → YouTube completada, asistente de voz y feature de descargas eliminados, `runBlocking` erradicado, dependencias limpias.

**El cambio grande de esta revisión es la reproducción.** El fallo que más se ha reportado ("una canción va bien y al saltar a la siguiente falla o no termina de cargar") no era un bug puntual sino un fallo de diseño, y tenía 6 hotfixes consecutivos en el historial sin resolverlo. La causa raíz era doble:

1. **`STATE_ENDED` no estaba gestionado en ninguna parte.** La app nunca decidía a dónde ir al terminar una canción; confiaba en que ExoPlayer saltara solo porque se le habían añadido items antes. Si el siguiente item no estaba, la reproducción **se detenía en silencio**. Ese handler existió en `MusicService` y se perdió al mover el player al `PlayerViewModel`.
2. **El flag `loadingJobsActive` se quedaba en `true` para siempre.** Se activaba *antes* de validar la cola, y los dos `return` posteriores no lo restauraban. Como `setCurrentPlaylist()` escribía con `postValue` (asíncrono) y el precargador leía `.value` (síncrono) en la línea siguiente, el caso normal era fallar la validación y envenenar el flag → **no se precargaba nada, la cola effective se quedaba en 1 canción y el salto a la siguiente no existía**.

A eso se sumaban: precarga secuencial de N canciones × hasta 30 s cada una, cero caché de URLs (cada reproducción re-resolvía por red), `onPlayerError` que paraba la cola entera, y el índice deducido comparando IDs (roto con pistas repetidas).

**Estado actual de la deuda:**

- **0 bugs críticos.** B1 estaba catalogado como crítico, pero `isValidAudioUrl` **no se llama desde ningún sitio del código de producción**: es código muerto (ver §3).
- **2 bugs medios** (B5, B10) + **6 bajos** activos (B1, B7, B12, B16, B17, B18); **5 ya resueltos** (B8, B9, B13, B14, B15).
- **268 unit tests** en 18 archivos (+31 respecto a la auditoría), 0 instrumentados útiles.
- **Sin R8/ofuscación** en release (S3).
- Monolitos de UI aún grandes (`PlaylistScreen` ~1363) y `PlayerViewModel` ha crecido a 713 líneas al centralizar la cola.

---

## 2. Métricas

| Métrica | Valor | Cambio |
|---|---|---|
| Archivos Kotlin (main) | 67 | +7 |
| Líneas totales | ~13.452 | +1.798 |
| Archivos más grandes | `PlaylistScreen.kt` (1363), `PlayerViewModel.kt` (713), `ConfigScreen.kt` (627), `Translations.kt` (541), `FloatingMusicControls.kt` (536), `SearchScreen.kt` (477), `SongListItem.kt` (459) | `PlayerViewModel` y `Translations` entran en la lista |
| versionCode / versionName | 6 / 1.1.0 | — |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 | — |
| DB Room | v7 (`MIGRATION_5_6`, `MIGRATION_6_7`) | — |
| Unit tests | **268** en 18 archivos | +31 |
| Tests instrumentados útiles | 0 (solo `ExampleInstrumentedTest`) | — |
| `runBlocking` en source | **0** | — |
| Claves de traducción sin uso | **15 de 82** definidas | nuevo hallazgo (ver §7) |
| Referencias "spotify" | Legítimas (`UrlParser`, `ScanResult`, `SpotifyImporter`, `ImportViewModel`, `PlaylistDatabase`, `ConfigScreen`) | — |

> La métrica "dependencias sin uso: 0" de la auditoría original **no se ha
> re-verificado** en esta revisión. La lista de `implementation` es corta y
> todas las bibliotecas (media3, okhttp, NewPipe, coroutines, Room, Coil) tienen
> uso visible, pero no se ha hecho un barrido formal de imports vs. graph.

---

## 3. BUGS ACTIVOS

### Críticos

| # | Ubicación | Descripción |
|---|---|---|
| — | — | **Ninguno.** |

> **Recalificación de B1 (era "crítico").** Ver tabla de bajos: `isValidAudioUrl`
> no se invoca desde producción, así que su comportamiento no afecta a la app.
> Clasificarlo de crítico era una sobrevaloración por no comprobar el uso real.

### Medios

| # | Ubicación | Descripción | Estado |
|---|---|---|---|
| B5 | `ui/QueueScreen.kt:103` | Usa la clave literal inexistente `"Player not available"`; la clave real `player_not_available` sí existe en los 4 idiomas (`Translations.kt:83,259,353,484`) pero no se usa → siempre cae al texto en inglés o a la clave sin traducir. | Activo |
| B10 | `ui/FeedScreen.kt:47,65` | `metadataCache` (Map en `mutableStateOf`) se reconstruye entero en cada insert (`metadataCache + (id to metadata)`), y cada insert dispara una recomposición → O(n²) en la carga del feed. No crece *sin* límite (una entrada por recomendación), pero sí es cuadrático. | Activo |
| ~~B8~~ | ~~`viewmodel/PlayerViewModel.kt:148-154,252`~~ | `loadingJob` solo se cancelaba en `clearPlayerState()`, no al cambiar de canción. | ✅ **RESUELTO** (2026-09-28) |
| ~~B9~~ | ~~`viewmodel/PlayerViewModel.kt:50-51,148-154`~~ | `loadingJobsActive`, `Boolean` no atómico escrito desde IO y leído desde main. | ✅ **RESUELTO** (2026-09-28) |

### Bajos

| # | Ubicación | Descripción | Estado |
|---|---|---|---|
| B1 | `utils/Utils.kt:44-50` | `isValidAudioUrl` no filtra nada: `return hasAudioPattern \|\| isValidUrlFormat(url)`, y `isValidUrlFormat(url)` ya se evaluó como `true` en el early-return, así que **toda URL http(s) pasa**. **Pero solo lo usan los tests** (`UtilsTest.kt`), ningún código de producción. Además `UtilsTest.isValidAudioUrl_acceptsAnyHttpUrl` **fija el comportamiento incorrecto**, así que un test impide arreglarlo. | Activo (reclasificado de crítico a bajo) |
| B7 | `network/SupabaseClient.kt:309-331` | `parseTimestamp`: el primer formato `"...ss.SSSSSS"` no matchea el ISO real de Supabase (3 dígitos de milisegundos), y el segundo `"...ss.SSS'Z'"` duplica la `'Z'` literal. Los fallos degeneran silenciosamente a `System.currentTimeMillis()`. | Activo |
| B12 | `service/YouTubeSearchManager.kt:168-175` | `getFormattedVideoCount`: el `else` final es inalcanzable (el `when` ya cubre todo); `Double.format(1)` depende del locale. | Activo |
| B16 | `service/YouTubeSearchManager.kt:376-378` | `getPlaylistThumbnailUrl()` devuelve hardcodeado `https://img.youtube.com/vi/undefined/hqdefault.jpg`, así que toda playlist sin thumbnail muestra un placeholder. | Activo |
| B13 | `utils/Utils.kt:24-32`, `ui/ConfigScreen.kt` | `getPackageInfo(name, 0)` deprecado en API 33+. | ✅ **RESUELTO** (2026-09-22) — `getPackageInfoCompat`. |
| B14 | `MainActivity.kt:80-82` | El servicio se iniciaba sin promover a foreground a tiempo. | ✅ **RESUELTO** (2026-09-22) — `bindService` + `startForeground` en `onStartCommand`. |
| B15 | `receivers/MediaButtonReceiver.kt` + `AndroidManifest.xml` | El receiver **no estaba registrado en el manifiesto**, y además tenía un parámetro de constructor (el framework no podía instanciarlo) y enviaba intents `ACTION_NEXT`/`ACTION_PLAY`… a `MusicService`, cuyo `onStartCommand` **ignoraba `intent.action`**. Los botones de auriculares no hacían nada. Además declaraba `ACTION_AUDIO_BECOMING_NOISY`, que ExoPlayer ya gestiona con `setHandleAudioBecomingNoisy(true)` (doble pausa). | ✅ **RESUELTO** (2026-09-28) — receiver registrado, sin constructor, ejecutando sobre `PlayerViewModel`; rama de "noisy" eliminada. Mapeo `keyCode → acción` extraído a `MediaButtonCommand` (lógica pura) con 9 tests. |
| B17 | `service/MusicService.kt:41-47` | `onStartCommand` ignora `intent?.action` por completo. Ya no es un problema activo (nadie le manda intents), pero sigue siendo una trampa latente para quien intente controlar la reproducción por servicio. | Activo (latente) |
| B18 | `AndroidManifest.xml:13` | `WAKE_LOCK` declarado y **sin usar**: no hay `PowerManager.WakeLock` ni `setWakeMode` en el source. La reproducción con la pantalla apagada la sostiene el subsistema de audio (`USAGE_MEDIA`), no el permiso. | Activo |

**Resumen:** 0 críticos, 2 medios (B5, B10), 6 bajos activos (B1, B7, B12, B16, B17, B18), 5 resueltos (B8, B9, B13, B14, B15).

---

## 4. SEGURIDAD

| # | Severidad | Ubicación | Descripción | Estado |
|---|---|---|---|---|
| S3 | Alta | `app/build.gradle.kts:45` | `isMinifyEnabled = false` en release: sin R8 ni ofuscación, APK trivial de de-compilar. | Activo |
| S6 | Media | `network/SimpleDownloader.kt:101,114-115` | Loguea **cookies y cabeceras completas** (pueden incluir `Authorization`/cookies de YouTube). | Activo |
| S7 | Media | `network/SupabaseClient.kt` (42 llamadas `Log.*`) | Loguea cuerpos completos de requests/responses (datos de grupos/usuarios). | Activo |
| S9 | Baja | `network/SupabaseClient.kt:19-20` | URL y anon key hardcodeadas (`sb_publishable_…`, publishable por diseño de DCL). No verificables las políticas RLS desde aquí. **Revisar RLS en `groups`, `group_members`, `recommendations`, `automatic`.** | Activo |

### Resueltos
- **S2** (OAuth client_secret), **S4** (tokens en prefs), **S5** (`plyr://spotify` BROWSABLE), **S8** (`READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`): eliminados con la migración Spotify.
- Sin secretos en el repo; sin WebView; `POST_NOTIFICATIONS`/`CAMERA` se piden en runtime.
- **Logs de `YouTubeManager` recortados** (2026-09-28): ya no se vuelca la URL completa de audio ni el detalle de cada stream en cada extracción (pasaba por `Log.d` en cada salto). Sigue logueando el resultado, no el contenido.

---

## 5. RENDIMIENTO Y CONCURRENCIA

| Severidad | Ubicación | Descripción | Recomendación | Estado |
|---|---|---|---|---|
| **Alta** | `ui/components/QrScannerDialog.kt:88,99-100` | Executor de CameraX creado en línea con `Executors.newSingleThreadExecutor()` en `setAnalyzer` y **nunca apagado**; sin `DisposableEffect`/`onDispose` que llame a `unbindAll()`. La cámara queda ligada al ciclo de vida de la Activity, no del diálogo. | Guardar ref + `shutdown()`; `unbindAll()` en `onDispose`. | Activo |
| Media | `ui/PlaylistScreen.kt:715,789,851,1006` | 4 `items(...)` de `LazyColumn` **sin `key`** (0 ocurrencias de `key =` en el archivo) → recomposición innecesaria y pérdida de estado/animación al reordenar. | `key` estable por id. | Activo |
| Media | `ui/FeedScreen.kt:74-107` | Recommendations con `Column` + `verticalScroll` + `forEach` → compone todos los items a la vez. | `LazyColumn`. | Activo |
| Media | `ui/FeedScreen.kt:47,65` | `metadataCache` O(n²) al reconstruirse entera por insert (es el mismo defecto que B10). | Acotar (LRU o resolver por id). | Activo |
| ~~Media~~ | ~~`service/YouTubeSearchManager.kt:149`~~ | `CoroutineScope(Dispatchers.IO)` efímera y no supervisada por búsqueda. | — | ✅ **RESUELTO** — todas las funciones de búsqueda son `suspend` con `withContext(Dispatchers.IO)`; no queda ningún scope creado a mano. |

### Resueltos
- `runBlocking` eliminado por completo de `PlaylistLocalRepository` y del resto del source (**0** ocurrencias).
- `SongList.kt` (isCurrentlyPlaying por item), `AudioDetection.kt` (OkHttp por llamada), paginación de `SpotifyRepository`: eliminados.
- `HomeScreen` ya no usa `Dispatchers.Default` (LazyGrid con key en línea 219).

### Incluye la reescritura de la reproducción (2026-09-28)
- **Caché de URLs de audio en memoria con TTL (2 h)** en `YouTubeManager`, con invalidación explícita. Antes **cada** reproducción re-resolvía la URL por red; ahora saltar a la siguiente canción no toca la red.
- **Precarga acotada y concurrente** (2 extracciones simultáneas) en vez de un bucle secuencial de N × 30 s.
- **`state` de carga derivado** (`resolviendo || bufferizando`) en vez de `postValue` manuales que podían quedar en `true` y deshabilitar los controles.
- **Guardas de escritura en el tick de la UI**: el poll de 500 ms escribía siempre en el estado de Compose, así que recomponía aunque nada cambiara; ahora solo escribe cuando el valor difiere.
- **`MediaItem` con URI de YouTube sin `SimpleCache`**: repetir una canción ya escuchada vuelve a descargarla. Es la siguiente mejora natural de tiempos de carga (pendiente, ver §9).

---

## 6. ARQUITECTURA

| Severidad | Ubicación | Descripción | Recomendación |
|---|---|---|---|
| Media | `viewmodel/PlayerViewModel.kt` (713) | **Nuevo en esta revisión.** Centralizar la cola (índice, ventana, `generation`, precarga, transiciones) ha resuelto los bugs de concurrencia pero ha dejado el archivo en 713 líneas con estado mutable repartido. | Extraer la lógica pura ya aislada en `QueueIndex` a un `PlayerQueue` con estado propio e inmutable. |
| Media | `ui/PlaylistScreen.kt` (1363) | Monolito que mezcla UI, red (Supabase), DB y lógica de negocio. | Extraer ViewModels y capas por dominio. |
| Media | `ui/SearchScreen.kt` (477), `ui/ConfigScreen.kt` (627), `ui/components/SongListItem.kt` (459) | Composable con estado/carga en `remember` + callbacks a repositorios. | ViewModels por pantalla + repositorios suspend. |
| Baja | `service/MusicService.kt` | Es solo notification + `MediaSession`; **no es dueño del reproductor**, que vive en el `PlayerViewModel` de proceso (`PlyrApp`). `onStartCommand` es un cascarón que solo hace `startForeground` de arranque. | Documentar que el servicio es una vista del player, o migrar a `MediaSessionService` de Media3 (habilitaría Android Auto, ver §9). |

**Nota positiva:** hay ViewModels (`PlayerViewModel`, `ImportViewModel` en `PlyrApp`); el patrón de **extraer lógica pura testeable** está ya consolidado y se ha aplicado a la cola (`QueueIndex`) y a los botones de media (`MediaButtonCommand`), ambos con tests JVM sin Android.

---

## 7. LIMPIEZA / CÓDIGO MUERTO

- Eliminados el 2026-09-22: `getLastfmApiKey`/`setLastfmApiKey` (`Config.kt`), `getAutomaticKeys()` (`SupabaseClient.kt`), `loadLikedSongs = { }` (`PlaylistScreen.kt`), `searchYouTubeIdsForPlaylist` + helper privado + `searchJob`/`cancelSearch`/`cleanup` (`YouTubeSearchManager.kt`), dominios obsoletos de `network_security_config.xml`.
- **2026-09-28:** `MediaButtonReceiver` era código muerto (B15) y ahora funciona. Eliminado de paso el callback `onKeyEvent` que nunca se usaba, el `abortBroadcast()` (ineficaz en un receiver del manifiesto) y la rama de `ACTION_AUDIO_BECOMING_NOISY` duplicada.
- **Pendiente de limpiar:**
  - `utils/Translations.kt` — **15 de las 82 claves definidas no se usan en ningún sitio**: `artist_image`, `enter_nickname`, `gestures_section`, `home_new_playlist`, `home_queue`, `home_settings`, `info`, `info_text`, `lastfm_api_key`, `next`, `nickname_description`, `no_results`, `not_configured`, `player_not_available`, `previous`. Son restos de features eliminadas (Spotify, Last.fm, gestos, shake) más strings que quedaron sin uso. `TranslationsTest` no detecta esta clase de problemas porque valida que la clave exista, no que se use.
  - `utils/Utils.kt:44-50` — `isValidAudioUrl` es código muerto en producción, y su test fija el comportamiento incorrecto (B1). O se arregla + se actualiza el test, o se borra la función y sus 13 tests.
  - `AndroidManifest.xml:13` — permiso `WAKE_LOCK` sin uso (B18).
  - `service/MusicService.kt:41-47` — rama de arranque de `onStartCommand` que ya no sirve para nada (B17).

> **Corolario de B5:** `player_not_available` está definida en los 4 idiomas y
> **nunca se usa**, precisamente porque `QueueScreen.kt:103` escribe el literal
> `"Player not available"`. Arreglar B5 rescata 4 traducciones que ya existen.

---

## 8. TESTS

- **268 unit tests, todos en verde** (verificado con `./run.sh test` el 2026-09-28: 0 fallos, 0 errores, 0 omitidos) en 18 archivos:

| Archivo | Tests |
|---|---|
| `UrlParserTest` | 33 |
| `UtilsTest` | 30 |
| `CoverCropMathTest` | 24 |
| `ImportManifestTest` | 24 |
| `SpotifyImporterTest` | 23 |
| `ExportManifestTest` | 22 |
| `QueueIndexTest` | 22 |
| `YouTubePlaylistCreatorTest` | 15 |
| `DatabaseMappingsTest` | 13 |
| `ImportArchiveTest` | 14 |
| `MediaButtonCommandTest` | 9 |
| `AppModelsTest` | 8 |
| `YouTubeFormattingTest` | 8 |
| `ModelDefaultsTest` | 7 |
| `SupabaseClientTest` | 7 |
| `TranslationsTest` | 5 |
| `NewPipeHolderTest` | 3 |
| `ExampleUnitTest` | 1 |

- **Nuevos en esta revisión:** `QueueIndexTest` (22) cubre el salto a la siguiente canción por `STATE_ENDED`, fin de lista, índices fuera de rango, los tres modos de repetición y el reinicio de "anterior" — justo el caso que había fallado 6 veces. `MediaButtonCommandTest` (9) cubre el mapeo de botones de media.
- **Gaps de cobertura relevantes:**
  - `PlayerViewModel` no tiene tests: su lógica *pura* sí está extraída en `QueueIndex`, pero el estado de la ventana (`windowStart`, `generation`, `trimWindow`) solo se ejercita en runtime.
  - `isValidAudioUrl` tiene 13 tests que **consolidan el bug** en vez de detectarlo.
  - **Solo `ExampleInstrumentedTest`** en `androidTest`: sin instrumentación real de flujos críticos (importación, permiso de cámara, escáner QR, NFC).

---

## 9. HOJA DE RUTA RECOMENDADA

### Fase 1 — Bugs (impacto rápido)
1. **B5**: `"Player not available"` → `"player_not_available"` en `QueueScreen.kt:103`. Una línea.
2. **B7**: simplificar `parseTimestamp` con `java.time.Instant.parse` y un único formato de fallback; hoy los fallos se disfrazan de fecha actual.
3. **B12**: limpiar `getFormattedVideoCount` (quitar el `else` inalcanzable, usar `Locale`).
4. **B16**: eliminar el placeholder `vi/undefined` (devolver la thumbnail real o `null`).
5. **B1**: decidir — arreglar `isValidAudioUrl` **y** corregir `UtilsTest.isValidAudioUrl_acceptsAnyHttpUrl`, o borrar la función y sus 13 tests. Mientras exista el test que fija el bug, no se puede cerrar.
6. **B18**: quitar el permiso `WAKE_LOCK` del manifiesto (no se usa).
7. **Claves muertas**: borrar las 15 claves sin uso de `Translations.kt` (§7). Conviene añadir a `TranslationsTest` un test que falle si una clave definida no aparece en el código, para que no vuelva a acumularse.

### Fase 2 — Estabilidad y rendimiento
8. ~~**B8/B9**~~: ✅ completado (2026-09-28) — `STATE_ENDED` gestionado, ventana deslizante, `generation` para descartar resoluciones obsoletas, `QueueIndex` + 22 tests.
9. **B10**: acotar `metadataCache` en `FeedScreen` (resolver por id en vez de reconstruir el mapa).
10. **QrScannerDialog**: `shutdown()` del executor + `unbindAll()` en `onDispose`. Es el único hallazgo **Alta** que queda.
11. `key` en las 4 `LazyColumn` de `PlaylistScreen`; `FeedScreen` a `LazyColumn`.
12. **`SimpleCache` de Media3** sobre el reproductor: es la mayor mejora pendiente de tiempos de carga, porque hoy repetir una canción ya escuchada vuelve a descargarla entera.

### Fase 3 — Calidad y limpieza
13. **S3**: `isMinifyEnabled = true` + `isShrinkResources` con reglas proguard (Room/NewPipe).
14. **S7**: recortar logs de cuerpos completos en `SupabaseClient` (42 llamadas `Log.*`).
15. **S6**: dejar de loguear cookies y cabeceras en `SimpleDownloader`.
16. **B17**: simplificar `MusicService` dejando claro que solo projectiona la notificación, o migrar a `MediaSessionService` de Media3.
17. Extraer el estado de la ventana de `PlayerViewModel` a una unidad propia testeable (§6).
18. Añadir tests instrumentados de flujos críticos (importación de playlist, cámara QR, NFC).
19. ~~**Ejecutar `./run.sh test`**~~: ✅ **hecho** (2026-09-28) — 268/268 en verde y `./run.sh build` genera el APK. El error de compilación que salió por el camino (`PlayerViewModel.kt:552` sin `queueIndex`) ya está corregido. Queda pendiente la verificación en dispositivo del salto entre canciones con red real.
