# Reporte de análisis de PLYR

**Fecha:** 2026-09-22
**Alcance:** `/home/josep/plyr/app/src/main/java/com/plyr` (60 archivos, ~11.654 líneas Kotlin) + Gradle/Manifiesto.
**Método:** auditoría estática manual + verificación contra el código de cada hallazgo.

---

## 1. Resumen ejecutivo

PLYR es una app de música **YouTube-only + local** que compila y funciona. Buen estado general: migración Spotify → YouTube completada, asistente de voz y feature de descargas eliminados, `runBlocking` erradicado del código, dependencias limpias. Acumula deuda técnica moderada:

- **1 bug crítico** (B1: `isValidAudioUrl` no filtra nada) + **2 bugs medios** (B8-B9, B10).
- **177 unit tests en verde**, 0 instrumentados útiles.
- **Sin R8/ofuscación** en release (S3).
- Monolitos de UI aún grandes (`PlaylistScreen` ~1370) y concurrencia/estado frágil en `PlayerViewModel` y `FeedScreen`.

**Últimas novedades desde 2026-08-20:** importación de playlists de Spotify por URL (`SpotifyImporter` + `ImportViewModel`), playlist **Liked Songs** local, DB v6 → v7 (MIGRATION_6_7).

---

## 2. Métricas

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 60 |
| Líneas totales | ~11.654 |
| Archivos más grandes | `PlaylistScreen.kt` (~1370), `SongListItem.kt` (~459), `ConfigScreen.kt` (~416), `SearchScreen.kt` (~477) |
| versionCode / versionName | 6 / 1.1.0 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| DB Room | v7 (`MIGRATION_5_6`, `MIGRATION_6_7`) |
| Tests unitarios | **177** en 13 archivos |
| Tests instrumentados útiles | 0 (solo `ExampleInstrumentedTest`) |
| `runBlocking` en source | **0** |
| Dependencias sin uso | 0 |
| Referencias "spotify" | Legítimas (UrlParser NFC/QR, ScanResult, SpotifyImporter, migración SQL) |

---

## 3. BUGS ACTIVOS

### Críticos

| # | Ubicación | Descripción |
|---|---|---|
| B1 | `utils/Utils.kt:25-30` | **`isValidAudioUrl` no filtra nada:** `return hasAudioPattern \|\| isValidUrlFormat(url)` — el segundo operando es siempre `true` tras el early-return, así que **cualquier URL http(s) pasa como válida** y `containsAudioPattern` es irrelevante. |

### Medios

| # | Ubicación | Descripción |
|---|---|---|
| B5 | `ui/QueueScreen.kt:103` | Usa la clave literal inexistente `"Player not available"`; la clave real `"player_not_available"` existe en los 4 idiomas pero no se usa → fallback en inglés. |
| B8 | `viewmodel/PlayerViewModel.kt:148-154,252` | `loadingJob` (precarga de playlist) solo se cancela en `clearPlayerState()`, **no al cambiar de canción** → resuelve URLs obsoletas en background. |
| B9 | `viewmodel/PlayerViewModel.kt:50-51,148-154` | `loadingJobsActive` (Boolean no atómico) escrito desde hilo IO y leído desde main → race condition. |
| B10 | `ui/FeedScreen.kt:47,65` | `metadataCache` (Map en `mutableStateOf`) crece sin límite y se reconstruye completo en cada update → O(n²). |

### Bajos

| # | Ubicación | Descripción |
|---|---|---|
| B7 | `network/SupabaseClient.kt:309-328` | `parseTimestamp`: primer formato `"SSSSSS"` no matchea el ISO real, segundo `"SSS'Z'"` duplica el `'Z'`; los fallos degeneran a `System.currentTimeMillis()`. |
| B12 | `service/YouTubeSearchManager.kt:242-249` | `getFormattedVideoCount`: `else` final inalcanzable (el `when` ya cubre todo); `Double.format` dependiente del locale. |
| B13 | `utils/UpdateChecker.kt:111`, `ui/ConfigScreen.kt:149` | `getPackageInfo(name, 0)` deprecado en API 33+; usar `PackageInfoFlags`. |
| B14 | `MainActivity.kt:81` | `startService()` en vez de `startForegroundService()` para MusicService (frágil en Doze/API 26+; `MediaButtonReceiver` sí lo hace bien). |
| B16 | `service/YouTubeSearchManager.kt:468-470` | `getPlaylistThumbnailUrl()` hardcodea `https://img.youtube.com/vi/undefined/hqdefault.jpg` (placeholder). |

**Resumen:** 1 crítico (B1), 3 medios (B5, B8-B9, B10), 5 bajos (B7, B12-B14, B16).

---

## 4. SEGURIDAD

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S3 | Alta | `app/build.gradle.kts:45` | `isMinifyEnabled = false` en release: sin R8 ni ofuscación, APK trivial de de-compilar. |
| S6 | Media | `network/SimpleDownloader.kt:101,114-115` | Loguea **cookies y cabeceras completas** (pueden incluir Authorization/cookies de YouTube). |
| S7 | Media | `network/SupabaseClient.kt:48,92,100,164,169,215,272,280,365` | Loguea cuerpos completos de requests/responses (datos de grupos/usuarios). |
| S9 | Baja | `network/SupabaseClient.kt:19-20` | Anon key hardcodeada (publishable por diseño, DCL) — no verificables las políticas RLS desde aquí. Revisar RLS en `groups`, `group_members`, `recommendations`, `automatic`. |
| S10 | Baja | `res/xml/network_security_config.xml:3-4` | Cleartext para `ws.audioscrobbler.com` **sin código que lo llame** (Last.fm eliminado) → config muerta; también quedan dominios `accounts/api.spotify.com` obsoletos. |

### Resueltos con refactors previos
- **S2** (OAuth client_secret), **S4** (tokens en prefs), **S5** (`plyr://spotify` BROWSABLE), **S8** (`READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`): **eliminados** con la migración Spotify.
- Sin secretos en el repo; sin WebView; `POST_NOTIFICATIONS`/`CAMERA` se piden en runtime.

---

## 5. RENDIMIENTO Y CONCURRENCIA

| Severidad | Ubicación | Descripción | Recomendación |
|---|---|---|---|
| **Alta** | `ui/components/QrScannerDialog.kt:88,99` | Executor de CameraX **nunca se apaga** (no hay `shutdown()`) y cámara con `bindToLifecycle` de la Activity (no del diálogo); sin `DisposableEffect`/`onDispose`. | Guardar ref + `shutdown()`; `unbindAll()` en `onDispose`. |
| Media | `ui/PlaylistScreen.kt:722,796,858,1013` | `items(size)` **sin `key`** → recomposición innecesaria y pérdida de estado/animación. | `key` estable por id. |
| Media | `ui/FeedScreen.kt:74,77,107` | Recommendations con `Column` + `verticalScroll` + `forEach` → compone todos los items a la vez. | `LazyColumn`. |
| Media | `service/YouTubeSearchManager.kt:149` | `CoroutineScope(Dispatchers.IO)` efímera (no supervisada) por búsqueda. | Reutilizar scope de ViewModel. |

### Resueltos
- `runBlocking` eliminado por completo de `PlaylistLocalRepository` y del resto del source.
- `SongList.kt` (isCurrentlyPlaying por item), `AudioDetection.kt` (OkHttp por llamada), paginación de `SpotifyRepository`: **eliminados**.
- `HomeScreen` ya no usa `Dispatchers.Default` (LazyGrid con key en línea 219).

---

## 6. ARQUITECTURA

| Severidad | Ubicación | Descripción | Recomendación |
|---|---|---|---|
| Media | `ui/PlaylistScreen.kt` (~1370) | Monolito que mezcla UI, red (Supabase), DB y lógica de negocio, con código muerto (`PlaylistScreen.kt:194` `loadLikedSongs = { }`). | Extraer ViewModels y capas por dominio. |
| Media | `ui/SearchScreen.kt` (~477), `ui/ConfigScreen.kt` (~416), `ui/components/SongListItem.kt` (~459) | Composable con estado/carga en `remember` + callbacks a repositorios. | ViewModels por pantalla + repositorios suspend. |

**Nota positiva:** ya hay ViewModels (`PlayerViewModel`, `ImportViewModel` en `PlyrApp`); `Config.kt` sin funciones Spotify; capas puras y testeables (`UrlParser`, `ScanResult`, `CoverCropMath`, `SpotifyImporter`).

---

## 7. LIMPIEZA / CÓDIGO MUERTO

| Ubicación | Detalle |
|---|---|
| `utils/Config.kt:261,270` | `getLastfmApiKey`/`setLastfmApiKey` sin callers (Last.fm eliminado). |
| `network/SupabaseClient.kt:345` | `getAutomaticKeys()` **sin callers** (tabla `automatic` ya no se consume). |
| `ui/PlaylistScreen.kt:194` | `loadLikedSongs: () -> Unit = { }` vacío (cargadores muertos de la ventana de listado eliminada en §16). |
| `service/YouTubeSearchManager.kt:145` | `searchYouTubeIdsForPlaylist` marcada `@Deprecated`. |
| `res/xml/network_security_config.xml` | Dominios obsoletos (`audioscrobbler`, `spotify`) sin código que los use. |

---

## 8. TESTS

- **177 unit tests** en 13 archivos: DatabaseMappings (13), NewPipeHolder (3), UrlParser (33), Translations (5), SpotifyImporter (23), Utils (30), ModelDefaults (7), AppModels (8), SupabaseClient (7), YouTubeFormatting (8), CoverCropMath (24), YouTubePlaylistCreator (15), Example (1).
- **Solo `ExampleInstrumentedTest`** en `androidTest`: sin instrumentación real de flujos críticos (importación, permiso cámara, escáner QR, NFC).

---

## 9. HOJA DE RUTA RECOMENDADA

### Fase 1 — Bugs (impacto rápido)
1. **B1**: arreglar `isValidAudioUrl` (`return containsAudioPattern(url)` si se quiere filtrar de verdad).
2. **B5**: `"Player not available"` → `"player_not_available"` en QueueScreen.
3. **B7**: simplificar `parseTimestamp` (`java.time.Instant.parse` con fallback a 1 formato); **B12** limpiar `getFormattedVideoCount` (quitar `else`, usar `Locale`).
4. **B16**: eliminar el placeholder `vi/undefined` (devolver thumbnail real o null).

### Fase 2 — Estabilidad y rendimiento
5. **B8/B9**: cancelar `loadingJob` al cambiar de track y usar `AtomicBoolean`/dispatcher coherente.
6. **B10**: acotar `metadataCache` en FeedScreen (LRU o por pantalla).
7. **QrScannerDialog**: `shutdown()` del executor + `unbindAll()` en `onDispose`.
8. Añadir `key` a las LazyLists de PlaylistScreen; pasar FeedScreen a `LazyColumn`.

### Fase 3 — Calidad y limpieza
9. **S3**: `isMinifyEnabled = true` + `isShrinkResources` con reglas proguard (Room/NewPipe).
10. **S7**: recortar logs de cuerpos completos en `SupabaseClient`.
11. Código muerto: eliminar `getLastfmApiKey`/`getAutomaticKeys`/`loadLikedSongs` y depurar `network_security_config.xml` + config cleartext (S10).
12. **B13**: `PackageInfoFlags` en UpdateChecker y ConfigScreen; **B14**: `startForegroundService` en MainActivity.
13. Añadir tests instrumentados de flujos críticos (importación de playlist, cámara QR, NFC).
14. Sincronizar con la hoja `todo` de la raíz: `eliminar warnings`, `export data`, `download lists`, `update report.md`.