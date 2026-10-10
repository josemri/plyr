# Reporte de análisis de PLYR

**Fecha:** 2026-10-10 (tanda de implementación del §5)
**Alcance:** `app/src/main/java/com/plyr` (86 archivos, ~16.500 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual con cada hallazgo verificado leyendo el código; lo cerrado se confirmó con `./run.sh test`, `./run.sh check` y `./run.sh build` (todo verde al cierre de esta tanda; release verificado aparte).

---

## 1. Estado

- **0 bugs abiertos.** Los 63 (B1–B63) están cerrados (§4).
- **`./run.sh test` ✓** — **484 tests unitarios, 0 fallos** en 40 archivos (incluye `WindowStateTest`, `IdlePlaybackTest`, `StableKeysTest`, `SongSwipeTest`, `PlaylistMappingsTest` y `PlaylistSourceTest`).
- **`./run.sh check` ✓** — detekt **0 findings** (6 nuevos corregidos en esta tanda), lint **0 errores** (2 `NonObservableLocale` corregidos con `Locale.ROOT`), cobertura ~15 % líneas.
- **`./run.sh build` ✓** — APK debug compilado (`app/build/outputs/apk/debug/plyr-debug.apk`).
- **9 tests instrumentados** nuevos (NFC, QR, importación) en 3 archivos, **pendientes de ejecutar en dispositivo** (`./run.sh test device`).
- **0 claves de traducción sin uso** y **0 inexistentes**, garantizado por dos tests.
- Código muerto grande borrado (−301 líneas): `SongMenuDialog`, `CollapsibleSection`, `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd`; más los campos muertos de `ResponsiveDimensions` y la rama inalcanzable de `MediaCommand.NONE`.
- Sync bidireccional con tombstones verificado. R8 activo en release (APK 4,9 MB sin firmar).

### Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 86 (~16.500 líneas) |
| Archivos de test | 40 (~5.700 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (~1434), `PlayerViewModel.kt` (~1085), `ConfigScreen.kt` (665), `SongListItem.kt` (~580), `FloatingMusicControls.kt` (538) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v8, migraciones `5→6`, `6→7`, `7→8` |
| Tests unitarios | **484** en 40 archivos |
| Tests instrumentados | **9** en 3 archivos (pendientes de ejecutar en device) |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso / inexistentes | **0** / **0** |

---

## 2. Pendiente

Nada de esto es un fallo de datos ni bloquea el uso: son mejoras de arquitectura, deuda de estructura y verificación.

### 2.1 Arquitectura

- **`PlayerViewModel.kt` (~1085) — orquestación sin cubrir.** La lógica pura ya está extraída y testeada (`QueueIndex`, `WindowState`, `LoadingState`, `PendingSkips`, `IdlePlayback`), pero la *orquestación* sigue viviendo en el ViewModel (`AndroidViewModel` + `ExoPlayer`): invalidación de URL caducada, limpieza de `_error`, cuándo recargar la ventana, quién invalida la carga en vuelo, qué pasa cuando `resolveItems` devuelve nulos y cuándo se drenan los saltos apuntados. Cubrirla exige **Robolectric** (o terminar de extraer el orquestador a una clase pura con dependencias inyectadas). Hoy está sin test unitario. Fue la fuente de B57, B58, B59, B60, B62 y B63.
- **`PlaylistScreen.kt` (~1434) — split completo pendiente.** Se ha extraído la lógica pura a `ui/utils/PlaylistMappings.kt` (interpretación de ids de YouTube, autor desde la descripción y mapeo de resultados de búsqueda, con tests). Sigue mezclando UI, red, DB y negocio en un único fichero; el split a `PlaylistViewModel` + estado Compose no se ha hecho (alto riesgo sin compilación incremental).
- **`ConfigScreen.kt` (665) y `SearchScreen.kt` (~490)** — Composables con carga, red y estado en `remember`; mismo tipo de deuda.
- **`MusicService.kt`** — no es dueño del reproductor; solo proyecta la notificación sobre el `ExoPlayer` de `PlayerViewModel`. El reparto es frágil (ver B57/B59 en §4).

### 2.2 Seguridad

- **S9 (residual) — RLS no verificable.** La anon key de Supabase (`sb_publishable_…`) ya no está hardcodeada en el código: se inyecta vía `buildConfigField` (`SUPABASE_URL`/`SUPABASE_ANON_KEY`) desde `local.properties`/variables de entorno (`app/build.gradle.kts`). **No es un secreto por diseño**, pero las políticas RLS de `groups`, `group_members`, `recommendations` y `automatic` no son verificables desde el repo y son la única defensa de esos datos: revisarlas en el panel de Supabase.
- **Riesgo de build:** si `SUPABASE_URL`/`SUPABASE_ANON_KEY` no están en `local.properties`, el build usa un valor por defecto funcional pero apuntando a un proyecto vacío. Documentar en `local.properties.example` si se añade uno.

### 2.3 Tests instrumentados (escritos, sin ejecutar)

- `app/src/androidTest/java/com/plyr/utils/NfcReaderInstrumentedTest.kt` — 5 tests: lectura de registro NDEF URI/TXT, descarte de registros no reproducibles y de intents no NFC.
- `app/src/androidTest/java/com/plyr/ui/components/QrCodeInstrumentedTest.kt` — 2 tests: round-trip `generateQrBitmap` (se decodifica con zxing) y contenido vacío → null.
- `app/src/androidTest/java/com/plyr/utils/DataImporterInstrumentedTest.kt` — 2 tests: importación end-to-end de un ZIP vía `Uri` + Room, y archivo inválido que falla limpiamente.
- **Pendiente:** ejecutarlos (`./run.sh test device`) y, si aplica, añadir `androidTestImplementation` faltantes. `app/build.gradle.kts` ya declara `androidTestImplementation(libs.core)` (zxing) para el round-trip del QR.

### 2.4 Deuda de estructura (detekt)

- **66 hallazgos `complexity`** congelados en `app/detekt-baseline.xml`, concentrados en los ficheros del §2.1 más `FloatingMusicControls.kt`, `SongListItem.kt`, `QRDialog.kt`, `YouTubePlaylistDetailView.kt`, `PlaylistLocalRepository`, `TrackDao`, `Config` e `ImportManifest`.
- Refactorizar esos 8–10 ficheros y regenerar el baseline (`./gradlew :app:detektBaselineMain`) cubriría ~55 de los 66 avisos.
- Lint: 58 falsos positivos de `UnusedResources` (`drawable-nodpi/ascii_*.png`, referenciados dinámicamente en `HomeScreen.kt`); no se baselan. El resto son advertencias de versiones (`GradleDependency`, `AndroidGradlePluginVersion`, `OldTargetApi`, …) que no rompen el check.

---

## 3. Hecho en esta tanda (§5)

### Seguridad
- **S7** — `SupabaseClient.kt`: logs con PII redactados (helpers `redact`/`describeBody`).
- **S9** — anon key y URL de Supabase vía `BuildConfig` (ver §2.2 residual).
- **S10** — cookie de YouTube (`PREF=f2=8000000`) vía `BuildConfig.YOUTUBE_RESTRICTED_MODE_COOKIE` (`SimpleDownloader.kt`), inyectable por `local.properties` sin tocar código.
- **S12** — verificado que el Uri del árbol SAF **ya** está excluido de `cloud-backup` (toda la config vive en `plyr_config.xml`) e incluido en `device-transfer` a propósito (documentado en los XML). **Resuelto por diseño**, sin cambio de código.

### Rendimiento (§5.3)
- `FeedScreen.kt` — caché de metadatos vía `metadataCache` + `LazyColumn` + `Semaphore` (acota la concurrencia de consultas).
- `Theme.kt` — `unifiedTypography()` envuelto en `remember(isDark)`.
- `CoverCropDialog.kt` — decodificación/escalado/recorte en `Dispatchers.Default`.
- `SearchScreen.kt` — refactor a `LazyColumn` (ver abajo); `trackEntities` en `remember`.
- `SongListItem.kt` — `heightIn(min = 40.dp)` en vez de `.height(32.dp)` fijo (el artista ya no se recorta).
- Claves estables (`stableKeys`) en `QueueScreen`, `PlaylistScreen` y `YouTubePlaylistDetailView`.

### Refactor de `SearchScreen.kt`
- `SearchMainView` pasa a `LazyColumn` (hoistea `youtubeManager`, `coverCache`, `coverSemaphore`, estados de expansión, `currentTrack` y las entidades de vídeo).
- Nueva `private fun LazyListScope.collapsibleYouTubeSearchResultsSection(...)` sustituye a `CollapsibleYouTubeSearchResultsView`.
- Eliminado el callback muerto `onVideoSelectedFromSearch` de `SearchMainView`, `SearchScreen`, `AudioListScreen` y su argumento en `MainActivity.kt` (ya no hace falta: `SongListItem` reproduce directamente).

### i18n (§5.5)
- 16 claves nuevas en `Translations.kt` en los 4 idiomas: `unknown_artist`, `liked`, `like`, `continue`, `cancel`, `share_via`, `delete_playlist_title`, `delete_playlist_message`, `delete`, `unsaved_changes_title`, `unsaved_changes_message`, `exit`, `create_playlist_discarded`, `youtube_search_failed`, `error_adding_track`, `error_removing_track`.
- Aplicadas en `QueueScreen.kt`, `SongListItem.kt` (`♥`/`♡`), `QRDialog.kt` (`share_via`), `PlaylistScreen.kt` (diálogos de borrado/salida, mensaje de descartados con `String.format`, errores de edición) y `YouTubeSearchManager.kt` ("Desconocido").
- **`SpotifyImporter.kt:222` (`"Unknown Artist"`) se deja a propósito**: es un *fallback de datos* que se persiste en Room; localizarlo guardaría texto dependiente del idioma en la base de datos.

### Código muerto (§5.5)
- `ResponsiveUtils.kt` — eliminados los 9 campos sin uso de `ResponsiveDimensions` (`titleSize`, `iconSizeSmall/Medium/Large`, `floatingControlsHeight`, `buttonHeight`, `buttonMinWidth`, `isCompact`, `isLandscape`) y sus asignaciones.
- `MediaButtonReceiver.kt` — `MediaCommand.NONE -> return false` → `else -> return false` (rama inalcanzable).
- `ActionButtonData.enabled` — verificado que **sí** se usa (`ActionBttn.kt`, `PlaylistScreen.kt`): sin cambios.

### Tests / extracción de lógica pura (§5.6)
- **`StableKeys.kt`** + `StableKeysTest` — claves únicas y estables para listas perezosas con ids duplicados.
- **`SongSwipe.kt`** + `SongSwipeTest` — `swipeActionFor(offset, threshold): SwipeAction`; `SongListItem.onDragEnd` usa un `when` sobre la decisión pura.
- **`PlaylistMappings.kt`** + `PlaylistMappingsTest` (11 tests) — ver §2.1.
- **`WindowState.kt`** (estado de la ventana) ya estaba extraído y cubierto por `WindowStateTest`.

### Análisis estático en esta tanda (detekt/lint)
- Corregidos los **6 findings nuevos de detekt** que introdujo el refactor de búsqueda: `PlaylistScreen.kt:492/1377` (`String.format` sin `Locale`) y 4 de complexity en `SearchScreen.kt`/`YouTubeSearchResults.kt`:
  - `YouTubeSearchResults.kt`: nuevos `YouTubeSearchSectionDeps` + `YouTubeSearchResultsState` (un parámetro agrupado elimina el `LongParameterList` de 13), la sección se descompone en `youtubeVideosItems`/`youtubePlaylistItems`, y `PlaylistCoverRow` extrae `rememberPlaylistCoverUrl` y `PlaylistCoverPlaceholder` (adios `LongMethod` ×2).
  - `SearchScreen.kt`: `LegacyResultsSectionState` agrupa los 10 parámetros de `collapsibleYouTubeSearchResultsSection`.
- Corregidos los **2 errores de lint `NonObservableLocale`** que dichos `String.format` reintrodujeron (leer `Locale.getDefault()` en composable no es observable): se usa `Locale.ROOT`, como ya hacía el resto del repo.

### Share: origen persistido (roadmap)
El commit `e58056d` ya había persistido `source`/`sourceId` en `playlists` (v8 + backfill) y `PlaylistShare.classify` ya los prefiere sobre la heurística de `remoteId`/`description`. Esta tanda cierra la única brecha que quedaba: el origen **no sobrevivía al backup**.
- `ExportManifest` serializa `source`/`sourceId` (aditivo: un archivo antiguo no los lleva); `ExportDigest` los incluye en la huella para que un cambio de origen reescriba la copia.
- `ImportManifest` los parsea vía `PlaylistSource.fromStorage` (case-insensitive; ausente/desconocido → `null` → heurística) y `DataImporter` los restaura en `PlaylistEntity`.
- `saveCreatedYouTubePlaylist` recibe el origen explícito (`CreatedPlaylist` + `source`), en vez de deducir Spotify de la `description`; `SpotifyImporter` pasa `SPOTIFY`. Ya no queda ningún punto donde el origen dependa del texto editable de la descripción.
- Tests: `PlaylistSourceTest` (3), +2 en `ExportManifestTest` (serialización y null), +1 en `ImportManifestTest` (parseo) y el round-trip ampliado. **484/484**.

---

## 4. Bugs resueltos (B1–B63)

**63 bugs, todos cerrados.** Agrupados por área:

> Las citas `L###` de `PlayerViewModel.kt` apuntan al archivo **en el momento en que se documentó cada bug**; el fichero ha crecido desde entonces. Las citas a otros ficheros siguen vigentes salvo indicación.

| Área | Bugs | Qué eran |
|---|---|---|
| **Reproducción** | B4, B5, B6, B35, B36, B42, B43, B46, B49, B50, B55, B56, B57, B58, B59, B60, B61, B62, B63 | La canción se cargaba dos veces; la URL caducada nunca se invalidaba; `_error` no se limpiaba al recuperar; los botones `<<`/`>>` de la notificación se quedaban fuera de la cola; la notificación fantasma; falta de acción `STOP`; `onServiceDisconnected` anulaba la sesión; el `>>` de la notificación desaparecía tras dos saltos; el `resolving` clavado en `true`; los saltos de notificación/auriculares descartados en silencio durante una transición; la canción pedida se cancelaba con la pantalla; la notificación sin botones en Android ≤12; la ventana con huecos; el fin de cola en IDLE sin `prepare()` |
| **Listas y favoritos** | B1, B2, B3, B9, B12, B13, B14, B15, B16, B18, B28, B48 | El swipe a *liked* borraba la canción; "añadir a lista" era un no-op; la lista no se refrescaba; duplicados al añadir; `<rnd>` desincronizaba la UI; `toggleLikeTrack` sin transacción; quitar un favorito no se reflejaba |
| **Compartir** | B19, B20, B30, B52, B53, B54 | El `share` mandaba el `remoteTrackId` de Spotify; URL de lista inválida; diálogo en blanco; el NFC no arrancaba; el QR se regeneraba en cada recomposición |
| **Sincronización** | B25, B40, B51 | Fechas ilegibles de Supabase; el Uri del SAF en cloud-backup; el sync resucitaba favoritos borrados (tombstones por clave) |
| **Compose / UI / rendimiento** | B7, B8, B11, B17, B23, B26, B31, B34, B44, B47 | Swipe sin claves; cámara sin liberar; polling de `SharedPreferences` cada 100 ms; `onThemeChanged` duplicado; AIOOBE en el sensor de luz; permiso de cámara sin salida; el sync se cancelaba al cambiar de pestaña |
| **Red / seguridad / build** | B32, B33, B38, B39, B41 | Mapa de cookies no sincronizado y logs volcando cookies/cabeceras/cuerpos; `optString(key, null)`; receiver exportado sin permiso; R8 roto en release; `WAKE_LOCK` sin uso |
| **i18n** | B21, B22, B24, B27, B45 | Valores japoneses en el mapa `català`; claves duplicadas; `"$ load_error"` literal; "1,5K" en `es-ES`; locale hardcodeado |
| **Limpieza** | B10, B29, B37 | Constante duplicada; parámetro muerto; `isValidAudioUrl` (código muerto) con 13 tests que la certificaban |

### Notas de implementación de los últimos bugs de reproducción

- **B57** — `QueueNextCommand` (lógica pura) decide el `>>` sobre la cola entera; `QueueAwarePlayer` (un `ForwardingPlayer`) añade `COMMAND_SEEK_TO_NEXT*` en `getAvailableCommands()` e `isCommandAvailable()`. El relleno de ventana pasa a *single-flight* (`fillWindow()` + `fillTarget()`, con reintentos) y `inFlight` guarda un `Extraction` (`Finished`/`Interrupted`) en vez de `String?`, eliminando el envenenamiento por cancelación. Tests: `QueueNextCommandTest`, `AudioUrlExtractionTest`.
- **B58** — `LoadingState` (tokens de identidad, `begin()`/`end(token)`/`supersede()`); todo `generation++` pasa por `invalidateLoads()`. Test: `LoadingStateTest` (9).
- **B59** — `PendingSkips` (cola FIFO con tope `MAX_PENDING = 5`); las transiciones usan `LoadingState`; `invalidateLoads()` vacía los saltos apuntados; drenaje en orden `endTransition` → `drainPendingSkips` → `endLoading`. Test: `PendingSkipsTest` (8).
- **B60** — `PlayerViewModel.playTrack(track, onFinished)` es la puerta única (scope del ViewModel); `cancelPendingPlayback()`; `clearPlayerState()` cancela la resolución en vuelo.
- **B62** — `QueueIndex.contiguousPrefixLength` recorta `resolved` en el primer hueco antes de `setMediaItems`. Tests: 5 casos `contiguous_*` en `QueueIndexTest`.
- **B63** — `IdlePlayback` + `PlayerViewModel.playPlayer()` resuelve IDLE sin items (reinicia desde el ancla), IDLE con items (`prepare`+`play`), y `stopAtQueueEnd` limpia el título. Test: `IdlePlaybackTest`.

---

## 5. Análisis de calidad automatizado

```
./run.sh check
```

encadena detekt (`:app:detektMain`, con type resolution; `app/detekt.yml` recortado a reglas `Unused*`, `complexity` y `potential-bugs`), Android Lint (`:app:lint`) y Kover (`:app:koverXmlReport`).

- **Código muerto:** los 19 hallazgos iniciales corregidos (6 imports, 4 parámetros, 5 variables, 1 propiedad privada, 3 `var`→`val`, 1 bloque inalcanzable) más las limpiezas de esta tanda.
- **Bugs reales de lint corregidos:** `NewApi` (`startForegroundService` → `ContextCompat`), `ImplicitDefaultLocale` ×8, `NonObservableLocale` ×3 (los 2 de esta tanda, con `Locale.ROOT`), `UnreachableCode`, `UnnecessarySafeCall` ×2 y `UnsafeCallOnNullableType` ×22 (`!!` reescritos capturando `val` local).
- **Comprobaciones de R8 (release):** `MenuOption`, `SupabaseClient.createGroup`/`joinGroup` (feature "crear/unirse a grupo" muerta) y `DataExporter.exportTo`/`ExportDigest` eliminados por inalcanzables. `PlyrSymbols` solo sufre inlining de constantes.
- **Estructura:** 66 `complexity` congelados en el baseline (ver §2.4) y **6 nuevos corregidos en esta tanda** (ver §3): `PlaylistScreen.kt` ×2 (`String.format` sin locale) y `SearchScreen.kt`/`YouTubeSearchResults.kt` ×4 (`LongParameterList`/`LongMethod` resueltos con parámetros-agrupados y extracción de sub-composables). detekt vuelve a **0 findings** fuera del baseline.
- **Cobertura:** global ~15,0 % de líneas / 14,3 % ramas. `ui`/Compose ~0 % (los tests son JVM), `utils` ~40 %, `viewmodel` ~8 % (el gap del §2.1), `network` ~24 %, `service` ~39 %. Sube al cubrir `viewmodel` y lógica pura nueva; no es objetivo por sí mismo.

---

## 6. Patrón consolidado

**Extraer lógica pura testeable (sin dependencias de Android) es el asset de calidad más valioso del repo.** Instancias: `QueueIndex`, `WindowState`, `MediaButtonCommand`, `CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`, `ExportDigest`, `UrlParser`, `ShareUrlPolicy`, `PlaylistShare`, `NfcPulse`, `SessionSkipCommand`, `PlaybackNotificationState`, `LoadingState`, `PendingSkips`, `IdlePlayback`, `StableKeys`, `SongSwipe`, `PlaylistMappings`. Es el modelo a seguir para cerrar los pendientes del §2.1.
