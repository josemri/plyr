# Reporte de análisis de PLYR

**Fecha:** 2026-10-01 (última tanda)
**Alcance:** `app/src/main/java/com/plyr` (76 archivos, ~15.650 líneas Kotlin) + Gradle + manifiesto + recursos.
**Método:** auditoría estática manual, con cada hallazgo verificado leyendo el código. Lo que se da por cerrado se comprobó con `./run.sh test` (**409 tests, en verde**), `./run.sh build` y `./run.sh build release`.

---

## 1. Estado

- **0 bugs abiertos.** Los **56** documentados (B1–B56) están resueltos (§3).
- **2 peticiones** de comportamiento abiertas (§4.1) + un puñado de mejoras (§4.2–§4.6).
- **409 tests unitarios** en 31 archivos, todos en verde. 0 instrumentados útiles.
- Código muerto grande **borrado** (−301 líneas): `SongMenuDialog`, `CollapsibleSection`, `PlyrDimensions`, `loadPlaylists`, `QueueIndex.needsRefillAfterEnd`.
- **0 claves de traducción sin uso** y **0 claves referenciadas que no existen**, con dos tests que lo garantizan.
- Sync bidireccional con propagación de borrados (tombstones) verificado.
- R8 activo en release: APK de 4,9 MB sin firmar (no hay keystore local).

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 76 (~15.650 líneas) |
| Archivos de test | 31 (~4.890 líneas) |
| Archivos más grandes | `PlaylistScreen.kt` (1403), `PlayerViewModel.kt` (786), `ConfigScreen.kt` (665), `SongListItem.kt` (580), `FloatingMusicControls.kt` (538) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7, migraciones `5→6` y `6→7` |
| Tests unitarios | **409** en 31 archivos (ejecutados, en verde) |
| Tests instrumentados | 0 útiles (solo `ExampleInstrumentedTest`) |
| `runBlocking` en source | 0 |
| Claves de traducción sin uso / inexistentes | **0** / **0** |

## 3. Bugs resueltos

**56 bugs, todos cerrados.** Agrupados por área para no perderlos:

| Área | Bugs | Qué eran |
|---|---|---|
| **Reproducción** | B4, B5, B6, B35, B36, B42, B43, B46, B49, B50, B55, B56 | La canción se cargaba dos veces; la URL caducada nunca se invalidaba; `_error` no se limpiaba al recuperar; los botones `<<` y `>>` de la notificación se quedaban fuera de la cola; la notificación fantasma decía "Plyr / Reproduciendo"; falta de acción `STOP`; `onServiceDisconnected` anulaba la sesión |
| **Listas y favoritos** | B1, B2, B3, B9, B12, B13, B14, B15, B16, B18, B28, B48 | El swipe a *liked* **borraba** la canción; "añadir a lista" era un no-op; la lista no se refrescaba al añadir/quitar; duplicados al añadir; `<rnd>` desincronizaba la UI; `toggleLikeTrack` sin transacción; quitar un favorito de *Liked* no se reflejaba |
| **Compartir** | B19, B20, B30, B52, B53, B54 | El `share` de una canción mandaba el `remoteTrackId` de Spotify en vez del vídeo de YouTube (y llegaba al feed público); la URL de una lista era inválida; el diálogo se abría en blanco; el NFC no arrancaba; el QR se regeneraba en cada recomposición |
| **Sincronización** | B40, B51 | El Uri del SAF acababa en el cloud-backup; el sync resucitaba los favoritos que se habían borrado (arreglado con tombstones por clave) |
| **Compose / UI / rendimiento** | B7, B8, B11, B17, B23, B26, B31, B34, B44, B47 | El gesto de swipe sin claves se rompía al cambiar de canción; la cámara no liberaba executor ni `unbind`; el polling de `SharedPreferences` cada 100 ms; `onThemeChanged` duplicado; AIOOBE en el sensor de luz; permiso de cámara denegado sin salida; el sync se cancelaba al cambiar de pestaña |
| **Red / seguridad / build** | B32, B33, B38, B39, B41 | El mapa de cookies no estaba sincronizado y los logs volcaban cookies, cabeceras y cuerpos; `optString(key, null)`; receiver exportado sin permiso; R8 roto en release; `WAKE_LOCK` sin uso |
| **i18n** | B21, B22, B24, B27, B45 | Valores japoneses dentro del mapa `català`, claves duplicadas, `"$ load_error"` literal, "1,5K" en `es-ES`, locale hardcodeado a `es-ES` |
| **Limpieza** | B10, B29, B37 | Constante duplicada, parámetro muerto, y `isValidAudioUrl` (código muerto) con 13 tests que la **certificaban** en vez de detectarla |

## 4. Pendiente

Nada de esto es un fallo de datos ni bloquea el uso: son mejoras.

### 4.1 Peticiones de comportamiento (lo prioritario)

- **F1 — `liked` vacía no debería aparecer como lista.** La fila se crea siempre al arrancar (`ensureLikedSongsPlaylist`) con `trackCount = 0` y ninguno de los dos listados la filtra (`HomeScreen.kt:276-278`, `PlaylistScreen.kt:121-124`).
  **Filtrar, no borrar**: `toggleLikeTrack`, `mergeLikedSongsTracks` e `ImportManifest.plan` dan por hecho que la fila existe. Basta con filtrar por `trackCount > 0`.
- **F2 — Persistir el origen de la lista.** Hoy `PlaylistShare.classify` deduce si una lista es de Spotify o de YouTube a partir del `remoteId` y de la description (`description == "Imported from Spotify"`), así que si el usuario edita la description la lista deja de ser compartible. Lo correcto es una columna `source`/`sourceId` en `PlaylistEntity` (+ migración) y que `ShareUrlPolicy`/`PlaylistShare` lean el origen guardado.
  Criterio de fondo de F2: **compartir siempre el origen real de lo que se comparte** (canción → su vídeo de YouTube; lista de Spotify → `open.spotify.com/playlist/<id>`, que la propia app ya sabe volver a abrir; lista creada en la app → nada que compartir).
- **Recorte de la ventana después del relleno.** `trimWindow()` recorta antes de que llegue el `addMediaItems`, así que durante uno o dos segundos el reproductor no tiene item siguiente y el botón `>>` parpadea; y si el relleno falla, la ventana se queda sin él. Mover el recorte *después* del `addMediaItems` cierra el hueco y, de rebote, mejora el `<<`. Toca el invariante de `windowStart`: va con tests.

### 4.2 Seguridad

- **S7 — PII en logcat.** `SupabaseClient.kt` (~42 `Log.*`) vuelca cuerpos completos de requests/responses: nicknames, códigos de invitación, nombres de grupo, URLs y comentarios de recomendaciones.
- **S9 —** La anon key (`sb_publishable_…`) está en el código; no es un secreto por diseño, pero **las políticas RLS de `groups`, `group_members`, `recommendations` y `automatic` no son verificables desde el repo** y son la única defensa de esos datos.
- **S10 —** Cookie de YouTube hardcodeada (`PREF=f2=8000000`): caduca en servidor sin aviso.
- **S12 —** La copia de seguridad todavía incluye el Uri del árbol SAF (B40 solo excluyó `plyr_config.xml`).

### 4.3 Rendimiento

- `FeedScreen.kt:47,65` — `metadataCache = metadataCache + (...)` reconstruye el mapa entero en cada insert (**O(n²)**) y es un read-modify-write no atómico desde N corrutinas; además esos `scope.launch` cuelgan de `rememberCoroutineScope()` y sobreviven a la cancelación del efecto.
- `FeedScreen.kt:74-107` y `YouTubeSearchResults.kt:217-218` — `Column` + `forEach` en vez de `LazyColumn`: se compone todo de golpe, y cada fila lanza su propia consulta (sin límite de concurrencia).
- `Theme.kt:136-140` — `unifiedTypography()` es `@Composable` y hace `return Typography(...)` **sin `remember`**: se reconstruyen 14 `TextStyle` en cada recomposición.
- `ConfigScreen.kt:184-188` — `PackageManager` + reflexión **dentro de la composición**.
- `CoverCropDialog.kt:235-236` — decodifica, escala y recorta un bitmap a tamaño completo en el hilo principal, dentro del manejador del clic.
- `SearchScreen.kt:425-437` — `trackEntities` se reconstruye en cada recomposición (no está en `remember`), así que los `SongListItem` recomponen sin parar.
- Claves de listas perezosas: 5 `items(...)` sin `key` (4 en el monolito `PlaylistScreen`) y `QueueScreen.kt:53-54` usa una clave inestable que incorpora la posición.
- `SongListItem.kt:105,224` — `.height(32.dp)` fijo en una `Box`/`Row` con dos líneas: **el artista se recorta en todas las listas**.

### 4.4 Arquitectura

- `PlayerViewModel.kt` (785) — monolito con tres banderas que hay que mantener coherentes a mano (`generation`, `windowStart`, `transitionInFlight`). La lógica pura ya está extraída (`QueueIndex`, `PlaylistLocalRepository.likedTrackOf`, el núcleo de `YouTubeManager.getAudioUrl`); **el estado de la ventana no**. Es lo que permitiría cubrir B4/B5/B6/B36 con tests JVM.
- `PlaylistScreen.kt` (1403) — mezcla UI, red, DB y lógica de negocio.
- `MusicService.kt` — no es dueño del reproductor, solo proyecta la notificación sobre el `ExoPlayer` que vive en `PlayerViewModel`. Funciona por diseño ya arreglado (`SessionSkipCommand`), pero el reparto sigue siendo frágil.
- `ConfigScreen.kt` (665) y `SearchScreen.kt` (483) — Composables con carga, red y estado en `remember`/`rememberCoroutineScope`.

**Nota positiva:** el patrón de **extraer lógica pura testeable** está consolidado en `QueueIndex`, `MediaButtonCommand`, `CoverCropMath`, `ImportManifest`, `ExportManifest`, `ImportArchive`, `ExportDigest`, `UrlParser`, `AudioUrlExtractionTest`, `ShareUrlPolicy`, `PlaylistShare`, `NfcPulse`, `SessionSkipCommand` y `PlaybackNotificationState`. Cero dependencias de Android y es el asset de calidad más valioso del repo: **el modelo a seguir**.

### 4.5 Limpieza e i18n

- **Literales de interfaz fuera de `Translations`** — refactor de amplio alcance, no un bug. Ejemplos: `QRDialog.kt:270` pone `"Compartir via"` en español fijo **tres líneas después** de usar `Translations.get` correctamente; `QueueScreen.kt:63` `"Unknown Artist"`, `SongListItem.kt:328` `"♥ liked"/"♡ like"`, más `SpotifyImporter.kt`, `YouTubeSearchManager.kt` y `PlaylistScreen.kt` con literals sueltos.
- **Literales sueltos menores:** 4 previews de Android Studio (se dejan, son herramienta de desarrollo), campos muertos de `ResponsiveDimensions`, `ActionButtonData.enabled` y la rama inalcanzable de `MediaCommand.NONE`.

### 4.6 Tests

1. **`PlayerViewModel` no tiene ningún test** — el mayor gap. `QueueIndex` sí está cubierta y es correcta; lo que no está cubierta es la *orquestación* (invalidación de URL caducada, limpieza de `_error`, cuándo recargar la ventana).
2. **`SongListItem` no tiene ningún test** y su lógica de swipe (umbral, dirección, acción) está embebida en lambdas de `pointerInput`. El primer paso es extraer la decisión "offset → acción" a una función pura, como se hizo con `QueueIndex`.
3. **Tests instrumentados**: importación de playlist, escáner QR y escritura NFC no se pueden cubrir en JVM.
