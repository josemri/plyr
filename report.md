# Reporte de análisis de PLYR

**Fecha:** 2026-08-02 (actualizado tras refactor de duplicación — todos los focos D1-D7 resueltos)
**Alcance:** `/home/josep/plyr/app/src/main/java/com/plyr` (69 archivos, ~19.600 líneas Kotlin) + configuración de Gradle/Manifiesto.
**Método:** auditoría estática manual + 3 análisis paralelos (bugs/duplicación, rendimiento/arquitectura, seguridad/prácticas). Todos los hallazgos críticos fueron verificados leyendo el código. Esta revisión actualiza la primera versión teniendo en cuenta los cambios de refactor ya aplicados (ver §11).

---

## 1. Resumen ejecutivo

PLYR es una app de música funcional (Spotify + YouTube + local) que compila, se instala y funciona en el dispositivo. Tiene **buenos cimientos** (Compose moderno, Media3, Room, NewPipe), pero acumula **deuda técnica importante**:

- **4 bugs críticos** (1 de lógica que no hace nada, 1 de traducción que muestra japonés en català, 2 de riesgo ANR/crash).
- **3 focos de seguridad reales** (secretos de Spotify distribuidos vía Supabase anon, HTTP en claro con API key en la URL, tokens en SharedPreferences plano con backup a la nube).
- **Duplicación resuelta al 100%**: los 7 focos (D1-D7) han sido eliminados; quedan solo patrones menores aislados (joinToString de artistas inline, `images?.firstOrNull()?.url?.let`).
- **Monolitos de UI** (PlaylistScreen con 2.189 líneas, SearchScreen con 1.675).
- **6 dependencias declaradas sin usar** y 2 archivos vacíos.
- **148 unit tests en verde** (0 fallos), pero **0 tests instrumentados útiles**.

**Prioridades claras:** (1) seguridad de credenciales, (2) los 4 bugs críticos (B1-B4), (3) arquitectura por pantallas, (4) bugs medios/bajos restantes.

---

## 2. Métricas del proyecto

| Métrica | Valor |
|---|---|
| Archivos Kotlin (main) | 69 |
| Líneas totales | ~19.600 |
| Archivo más grande | `ui/PlaylistScreen.kt` (2.189) |
| versionCode / versionName | 5 / 1.0.6 |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| Tests unitarios | **148** (0 fallos; antes 76) |
| Tests instrumentados útiles | 0 |
| Dependencias declaradas sin uso | 6 |
| Focos de duplicación | 7 → **0 pendientes** (todos resueltos) |
| Bugs detectados | ~14 (4 críticos) |
| Ficheros nuevos de refactor | `utils/UrlParser.kt`, `model/ScanResult.kt`, `utils/NewPipeHolder.kt`, `ui/components/TrackMetadataSection.kt`, `service/YouTubePlaylistCreator.kt` |

---

## 3. BUGS

### Críticos

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| B1 | **Alta** | `utils/Utils.kt:39` | **`isValidAudioUrl` nunca filtra nada.** En la L29 hay un early-return: si `isValidUrlFormat(url)` es false → return false. En la L39 `return hasAudioPattern || isValidUrlFormat(url)`: el segundo operando es **siempre `true`**, así que `containsAudioPattern` (ytimg.com, .mp3, etc.) es irrelevante y **cualquier URL http(s) pasa como "válida para audio"**. |
| B2 | **Alta** | `utils/Translations.kt:970-1158` | **El bloque de català está contaminado con japonés.** A partir de "Local Screen" (~L970) hasta el final, las claves están traducidas a japonés (p.ej. `"plyr_local" to "plyr_ローカル"`, `"No tracks loaded" to "曲が読み込まれていません"`), copiadas del mapa "日本語". Los usuarios de català ven japonés en Local/Queue/Playlists/NFC/Share. |
| B3 | **Alta** | `utils/Config.kt:210` (callers: `ui/SearchScreen.kt:208,275,...,1024`, `ui/PlaylistScreen.kt:346`, `ui/ConfigScreen.kt:54`) | **Red bloqueante en el hilo principal.** `getSpotifyAccessToken()` usa `runBlocking { withContext(IO) { suspendCoroutine {...} } }` para renovar el token por red. La UI la invoca directamente desde composables/LaunchedEffect en main → **riesgo de ANR**. |
| B4 | **Alta** | ~~`assistant/AssistantManager.kt:717-725`~~ | **ExoPlayer tocado desde hilo de fondo.** El sleep-timer usa `java.util.Timer`/`TimerTask` y ejecuta `playerViewModel.pausePlayer()` en L723. ExoPlayer **no es thread-safe**; los métodos deben ir al main thread (riesgo de crash `IllegalStateException`). **Resuelto por eliminación:** el asistente de voz (y su sleep-timer) se eliminó en §14. |

### Medios

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| B5 | Media | `ui/QueueScreen.kt:103` | Usa `Translations.get(context, "Player not available")` con la **clave literal inexistente** (la clave real es `"player_not_available"`). El fallback devuelve la propia clave → **inglés en todos los idiomas**. |
| B6 | Media | `ui/ConfigScreen.kt:262` | Loguea `Config.getSpotifyClientSecret(context)?.take(5)` → **fuga parcial del client secret a logcat**. |
| B7 | Media | `network/SupabaseClient.kt:315` | Primer patrón de `parseTimestamp` (`SSSSSS`, 6 dígitos) no matchea el ISO real; hoy funciona por el fallback del 2º patrón. Si llega fracción de 6 dígitos, SimpleDateFormat lenient lo interpreta como milisegundos (~+2 min). |
| B8 | Media | `viewmodel/PlayerViewModel.kt:163` | `loadingJob` (precarga de playlist) nunca se cancela al cambiar de canción/salir → resuelve URLs de canciones obsoletas en background. |
| B9 | Media | `viewmodel/PlayerViewModel.kt:50,177` | `loadingJobsActive` (boolean no atómico) escrito desde hilo IO (:177) y leído desde main (:157) → race condition. |
| B10 | Media | `ui/FeedScreen.kt:47,65` | `metadataCache` (Map en `mutableStateOf`) crece sin límite y se reconstruye completo en cada actualización (`metadataCache + ...` → O(n²)). |
| B11 | Media | `network/Recommendations.kt:25-30` | La llamada a Last.fm usa **HTTP en claro** con la **API key en la URL** (`&api_key=`): cualquier intermediario la captura. |

### Bajos

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| B12 | Baja | `service/YouTubeSearchManager.kt:253-263` | `getFormattedVideoCount`: el `else` final es inalcanzable (el `when` ya cubre 1 / <1000 / >=1000) y `Double.format` depende del locale (`1.6K` → `1,6K`). |
| B13 | Baja | `utils/UpdateChecker.kt:111` | `getPackageInfo(name, 0)` deprecado (API 33+); usar `PackageInfoFlags`. |
| B14 | Baja | `MainActivity.kt:94` | `startService()` en vez de `startForegroundService()` para MusicService (frágil en Doze/API 26+). |
| B15 | Baja | ~~`ui/HomeScreen.kt:162-168`~~ | La activación del asistente por shake no pide `RECORD_AUDIO` en runtime si falta → falla en silencio (las otras rutas sí lo piden). **Resuelto por eliminación:** el asistente de voz se eliminó en §14. |
| B16 | Baja | `service/YouTubeSearchManager.kt:479-482` | **Resuelto en parte:** `getThumbnailUrl` ya no emite el literal `undefined` (delega en `UrlParser.youtubeThumbnailUrl(videoId) ?: ""`). Pero **`getPlaylistThumbnailUrl()` aún hardcodea `https://img.youtube.com/vi/undefined/hqdefault.jpg`** como placeholder (usada en :333). |

---

## 4. DUPLICACIÓN DE CÓDIGO

| # | Ubicación | Descripción | Estado |
|---|---|---|---|
| D1 | (antes 4 sitios) `service/YouTubeSearchManager.kt:322,337,391,513`, `network/YouTubeManager.kt:37`, `ui/FeedScreen.kt` (3 fns privadas), `ui/components/QrScannerDialog.kt` (parseQrContent), `utils/NfcReader.kt` (parseNfcUrl), `utils/MediaMetadataExtractor.kt:241` (extractSpotifyId) | **Extracción de IDs de URLs en 6 sitios** con diferencias (YouTubeManager no soportaba `/shorts/`; QrScanner/NfcReader tenían parseo propio con `toUri()`). | ✅ **RESUELTO.** Nuevo `utils/UrlParser.kt` (`extractYoutubeVideoId`, `extractYoutubePlaylistId`, `extractSpotifyId`, `parseScanText`, `getUrlType`, `isPlayableUrl`) usado en los 6 puntos; funciones privadas eliminadas. |
| D2 | (antes 5 sitios) `utils/Utils.kt`, ~~`assistant/AssistantManager.kt:742`~~, `service/YouTubeSearchManager.kt:233-245`, `ui/components/SongListItem.kt:479`, `ui/components/SongMenuDialog.kt:215` | **5 implementaciones de formato de duración** con formatos distintos: `MM:SS` con pad vs `M:SS` sin pad, y solo YouTubeSearchManager soportaba horas. | ✅ **RESUELTO.** `Utils.formatDurationMs(ms)` ("M:SS") y `Utils.formatDurationSeconds(s)` ("MM:SS"/"HH:MM:SS"/"En vivo"); los 4 consumidores ahora las usan. |
| D3 | (antes 3 sitios) `network/YouTubeManager.kt:13-19`, `utils/MediaMetadataExtractor.kt:31-37`, `service/YouTubeSearchManager.kt:32-56` (+ `network/YoutubeAudioExtractor.kt:12-27`) | Guard `isInitialized` + `NewPipe.init(...)` en 3-4 objetos (YouTubeManager relanzaba errores, MediaMetadataExtractor los silenciaba; cada uno con su propio downloader/locale). | ✅ **RESUELTO.** `utils/NewPipeHolder.kt` (singleton `@Synchronized`, init único idempotente, locale "es,ES"). `YoutubeAudioExtractor.kt` (código muerto sin callers, duplicaba `YouTubeManager.getAudioUrl`) **eliminado**. |
| D4 | (antes 3 sitios) `service/YouTubeSearchManager.kt:473`, `ui/components/search/YouTubePlaylistDetailView.kt:164`, `ui/PlaylistScreen.kt:68-72` | Construcción de thumbnail `https://img.youtube.com/vi/$id/mqdefault.jpg` + regex 16:9 propia en PlaylistScreen. | ✅ **RESUELTO.** `UrlParser.youtubeThumbnailUrl(videoId)` (null sin id) y `UrlParser.normalizeYoutubeThumb` (regex 16:9) en los 3 sitios. |
| D5 | (antes 5 cuerpos) `network/SpotifyRepository.kt:1432,1455,1520,1524,1536` | `getImageUrl()` (`images?.firstOrNull()?.url ?: ""`) y `getArtistNames()` (`artists.joinToString(", ")`) con cuerpos idénticos en 3-2 clases. | ✅ **RESUELTO.** Extensiones compartidas `List<SpotifyImage>?.firstImageUrl()` y `List<SpotifyArtist>.artistNames()`; los 5 métodos delegan. 2 tests nuevos. |
| D6 | `ui/components/QrScannerDialog.kt` (`QrScanResult`) vs `utils/NfcReader.kt` (`NfcScanResult`) | Estructura idéntica `source/type/id`; NfcReader decía "compatible con QrScanResult". | ✅ **RESUELTO.** Nuevo `model/ScanResult.kt` usado por NfcReader, NfcScanEvent, QrScannerDialog, FeedScreen y MainActivity. |
| D7 | (antes 2 sitios) `ui/components/SongListItem.kt:457-485`, `ui/components/SongMenuDialog.kt:193-221` | Strings de UI duplicadas (`"Album:"`, `"Release:"`, `"Duration:"`) y fila de metadatos duplicada. | ✅ **RESUELTO.** Componente compartido `LazyListScope.trackMetadataSection(albumName, releaseDate, durationMs)` (extensión que preserva items/espaciado). **Nota:** los keys `album_colon`/`release_colon`/`duration_colon` ya existen en Translations.kt pero el bloque català tiene valores en japonés (B2) → no se usan hasta arreglar B2. |

---

## 5. RENDIMIENTO Y CONCURRENCIA

| Severidad | Ubicación | Descripción | Recomendación |
|---|---|---|---|
| **Alta** | `ui/components/QrScannerDialog.kt:93` | `Executors.newSingleThreadExecutor()` para el analyzer de CameraX **nunca se apaga** (no hay `shutdown()`); cada apertura del diálogo deja un thread huérfano. | Guardar referencia + `shutdown()` en `onDispose`. |
| **Alta** | `ui/components/QrScannerDialog.kt:104-110` | Cámara ligada al lifecycle de la Activity (`bindToLifecycle`), no al diálogo: al cerrarlo, cámara y analyzer siguen activos. | `DisposableEffect { onDispose { cameraProvider.unbindAll() } }`. |
| Media | `service/AudioDetection.kt:53` | `OkHttpClient()` nuevo por llamada (pierde pool de conexiones). | Cliente singleton (como en SpotifyRepository:17). |
| Media | `network/SpotifyRepository.kt:179,247` | Paginación con `Handler.postDelayed(200ms)` y callbacks encadenados (frágil ante cancelaciones). | Reescritura suspend con `while` + `withContext(IO)`. |
| Media | `database/PlaylistLocalRepository.kt:163,262,415,465,507` | `runBlocking` dentro de callbacks OkHttp/Room para puentear con coroutines. | Migrar a APIs suspend. |
| Media | `ui/components/SongList.kt:48`, `LocalScreen.kt:284,407,795`, `SearchScreen.kt:836,1016,1438,1502,1553`, `PlaylistScreen.kt:456,1034,1105,1224,1267,1357,1637,1703,1779`, `SongMenuDialog.kt:485`, `SongListItem.kt:823`, `YouTubePlaylistDetailView.kt:219`, `SpotifyArtistDetailView.kt:266`, `YouTubeSearchResults.kt:257` | **LazyLists sin `key`** → recomposición innecesaria y pérdida de estado/animación al cambiar las listas. | `key` estable por id. |
| Media | `ui/components/SongList.kt:59` | `isCurrentlyPlaying` recalculado por item comparando con LiveData en cada recomposición. | `derivedStateOf` + pasar id activo como parámetro. |
| Baja | `ui/FeedScreen.kt:105` | `recommendations.forEach` dentro de `Column` + `verticalScroll` compone todos los items a la vez. | `LazyColumn`. |
| Baja | ~~`ui/HomeScreen.kt:191,193`~~ | Red (YouTubeManager) en `Dispatchers.Default` (pool CPU compartido con ONNX). **Eliminado en §14** (el único uso de `Dispatchers.Default`/ONNX era el asistente de voz). |
| Baja | `service/YouTubeSearchManager.kt:159` | `CoroutineScope(Dispatchers.IO)` efímera por búsqueda (no hay leak por `searchJob`, pero es scope no supervisada). | Reutilizar scope del composable/ViewModel. |

---

## 6. ARQUITECTURA

| Severidad | Ubicación | Descripción | Recomendación |
|---|---|---|---|
| **Alta** | `ui/PlaylistScreen.kt` (2.189), `ui/SearchScreen.kt` (1.675), `network/SpotifyRepository.kt` (1.631), `ui/ConfigScreen.kt` (1.200), `ui/LocalScreen.kt` (1.114), `ui/components/SongListItem.kt` (1.029), ~~`assistant/AssistantManager.kt` (986)~~ | **God classes / monolitos** que mezclan UI, red, DB y lógica de negocio. | Extraer ViewModels, composables de item y capas de datos por dominio. |
| **Alta** | `utils/Config.kt` (729, líneas 24-44, 73-79, 195-227) | **God object** que mezcla SharedPreferences, API keys hardcodeadas y renovación de token con red síncrona. | Separar en `SpotifyAuthStore`, `AppSettings`, `ApiKeys`; mover renovación a `SpotifyTokenManager` (ya suspend). |
| Media | `ui/components/SongListItem.kt:989-991` | Composable que consulta `PlaylistDatabase...downloadedTrackDao()` en composición. | Llevar al ViewModel/repositorio. |
| Media | `ui/SearchScreen.kt:104-164`, `PlaylistScreen.kt:530-615`, `LocalScreen.kt:1076`, `FeedScreen.kt:43-68` | Estado y carga de datos en `remember { mutableStateOf }` + `rememberCoroutineScope`, llamando a repositorios con callbacks. | ViewModels por pantalla + repositorios suspend. |
| Baja | ~~`network/YoutubeAudioExtractor.kt:35`~~ | **Eliminado** — `YoutubeAudioExtractor.getAudioUrl()` no tenía callers y duplicaba `YouTubeManager.getAudioUrl`. |

**Nota positiva del refactor:** `utils/UrlParser.kt` y `model/ScanResult.kt` son clases puras (sin dependencias Android), testeables en JVM — patrón a replicar al extraer lógica de los monolitos.

---

## 7. SEGURIDAD

### Crítico / Alto

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S1 | **Alta** | `network/SupabaseClient.kt:345-386` + `ui/ConfigScreen.kt:174-204` | **Distribución de secretos vía Supabase con anon key.** `getAutomaticKeys()` baja de la tabla `automatic` el `client_secret` de Spotify, la key de AcoustID y la de Last.fm usando la anon key pública. Si RLS permite `anon SELECT` (probable, el propio app lo lee así), **cualquiera puede hacer curl y obtener los secretos**. |
| S2 | **Alta** | `network/SpotifyRepository.kt:55,97,793-797` | **OAuth con client_secret en el dispositivo** (Authorization: Basic). Spotify exige el flujo **PKCE** (sin secret) para apps nativas; el secret es extraíble del APK/memoria. |
| S3 | **Alta** | `app/build.gradle.kts:24` | **`isMinifyEnabled = false` en release**: sin R8 ni ofuscación; el APK es trivial de de-compilar. |
| S4 | **Alta** | `utils/Config.kt:140` + `AndroidManifest.xml:25` + `res/xml/backup_rules.xml` y `data_extraction_rules.xml` | **Tokens de Spotify en SharedPreferences plano** (`MODE_PRIVATE`, sin `EncryptedSharedPreferences`) **con `allowBackup="true"` y reglas de backup vacías** → los refresh tokens se copian a la nube de Google / transferencia entre dispositivos. |

### Medios

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S5 | Media | `AndroidManifest.xml:49-54` | Esquema custom `plyr://spotify` con `BROWSABLE`: otra app puede interceptar el deep-link con el auth `code`. Validar origen y considerar HTTPS `autoVerify` / PKCE. |
| S6 | Media | `network/SimpleDownloader.kt:101,114-117` | Loguea **cookies y cabeceras completas** (pueden incluir Authorization/cookies de YouTube). |
| S7 | Media | `network/SpotifyRepository.kt:154,227,295,363,998,1153,1228` y `SupabaseClient.kt:48,100,215,280,365` | Loguea **cuerpos completos** de respuestas (datos personales). |

### Bajos / Aceptables

| # | Severidad | Ubicación | Descripción |
|---|---|---|---|
| S8 | Baja | `AndroidManifest.xml:20-21` | `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` **declarados sin uso** (la lectura local usa SAF `OpenDocument`); pedir en runtime si se añade escaneo MediaStore o quitar. |
| S9 | Baja | `network/SupabaseClient.kt:20` | Anon key de Supabase hardcodeada — aceptable por diseño (es publishable), pero **no se pueden verificar las políticas RLS** desde aquí. Revisar RLS en `groups`, `group_members`, `recommendations` y sobre todo `automatic`. |
| S10 | Baja | `res/xml/network_security_config.xml:3-5` | Permiso cleartext para `ws.audioscrobbler.com` (el dominio que se llama por HTTP). Al pasar a HTTPS (B11) puede eliminarse. |

### Positivos de seguridad
- `local.properties` y keystore en `.gitignore`; sin secretos en el repositorio.
- Sin WebView (ni `setJavaScriptEnabled`/`addJavascriptInterface`).
- `POST_NOTIFICATIONS` y `CAMERA` se piden en runtime correctamente.
- `MusicService` usa Media3 MediaSession (no API deprecada) con `foregroundServiceType="mediaPlayback"`.

---

## 8. BUENAS PRÁCTICAS / LIMPIEZA / DEPENDENCIAS

### Dependencias sin uso (verificado con grep)

| Dependencia | Línea | Uso real |
|---|---|---|
| `androidx.compose.material:material-icons-extended` | `app/build.gradle.kts:70` | Solo se usan iconos del core (`Icons.Filled.Close/Mic/Settings`, HomeScreen.kt:39-42). **~30MB que no se usan.** |
| `com.android.volley` | `app/build.gradle.kts:82` | 0 imports. |
| `androidx.media3:media3-ui` | `app/build.gradle.kts:95` | 0 imports. |
| `androidx.media:media` | `app/build.gradle.kts:109` | 0 imports (solo `androidx.media3`). |
| `androidx.core:core-splashscreen` | `app/build.gradle.kts:79` | 0 imports. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `app/build.gradle.kts:92` | 0 imports (`viewModel()` no se usa). |

Además: `navigation-compose` solo en catálogo (sin usar) y alias duplicados `androidx-foundation`/`androidx-compose-foundation` (libs.versions.toml:64-65).

### Estilo / limpieza

| Ubicación | Detalle |
|---|---|
| `ui/components/MediaListItem.kt`, `ui/components/MediaGridItem.kt` | Archivos de 1 línea (vacíos). |
| `ui/theme/Color.kt:5-11` | Colores template (`Purple80`, `Pink40`, etc.) sin usar; el tema real usa paleta terminal privada. |
| `utils/Utils.kt:36` | `println()` en producción (dentro de `isValidAudioUrl`). |
| `ui/SearchScreen.kt` (~14x) | Comentarios obsoletos `// antes Color(0xFF...)`. |
| ~~`assistant/AssistantManager.kt:905`~~ | `// TODO: Implementar creación de playlist` — **eliminado en §14** (el asistente de voz ya no existe; la creación de playlists se hace desde la UI de playlists, §12). |
| `utils/UpdateChecker.kt:15,30-33` | Caché de actualizaciones desactivado ("For debugging"); constante `CHECK_INTERVAL_MS` sin efecto. |
| `utils/NewPipeHolder.kt` | Locale fijo `Localization("es","ES")` para NewPipe, no sigue el dispositivo (init único centralizado en D3). |
| `res/values/strings.xml` | Solo `app_name`; **toda la UI está hardcodeada en Kotlin** (`Translations.kt`), no se aprovecha el sistema de recursos (sin lint de traducción ni resource shrinking). |
| ~~`assistant/AssistantTTSHelper.kt:62`~~ | Override del `onError(utteranceId)` deprecado — **eliminado en §14**. |

### Tests
- **148 unit tests en verde** (`./run.sh test`, 0 fallos): Translations (5), SpotifyModels (12), DatabaseMappings (3), YouTubeFormatting (8), Utils (30), SupabaseClient (7), ModelDefaults (7), UrlParser (33), NewPipeHolder (3), YouTubePlaylistCreator (15), CoverCropMath (24), ExampleUnitTest (1).
- **Cambios aplicados en esta ronda:** eliminado `UrlExtractorTest` (usaba reflexión sobre funciones privadas de FeedScreen ya borradas) → sustituido por `UrlParserTest` (33 casos directos). `UtilsTest` creció de 24 a 30 (formatTimestamp, formatDurationMs, formatDurationSeconds). Añadidos `NewPipeHolderTest` (3) y tests directos de las extensiones `firstImageUrl`/`artistNames` en `SpotifyModelsTest` (12). Añadido `SpotifyToYouTubeConverterTest` (10) para la nueva capa de conversión `SpotifyToYouTubeConverter` (ver §12).
- **Solo 1 test instrumentado de plantilla** (`app/src/androidTest/.../ExampleInstrumentedTest.kt`, verifica el package). No hay instrumentación real de los flujos críticos (login/callback, persistencia de tokens, permisos runtime, escáner QR).

### Estilo / limpieza (actualización)
| Ubicación | Detalle |
|---|---|
| `network/YoutubeAudioExtractor.kt` | **Eliminado** (código muerto: 0 callers, duplicaba `YouTubeManager.getAudioUrl` y `NewPipe.init`). |

---

## 9. HOJA DE RUTA RECOMENDADA

### Fase 1 — Crítico (seguridad + crashes)
1. **S1/S2**: Migrar Spotify OAuth a **flujo PKCE** (sin client_secret en el dispositivo). Eliminar la distribución de secretos vía tabla `automatic` o bloquear `anon SELECT` en RLS.
2. **S4**: Mover tokens a **`EncryptedSharedPreferences`** (Jetpack Security) y añadir `<exclude>` en `backup_rules.xml`/`data_extraction_rules.xml` (o `allowBackup="false"`).
3. **B11/S10**: Cambiar Last.fm a `https://` y eliminar el `domain-config` cleartext.
4. **B1**: Decidir la semántica de `isValidAudioUrl` y arreglarla (`return containsAudioPattern(url)` si se quiere filtrar).
5. **B2**: Reescribir el bloque català contaminado con japonés (es y ja ya pasan sus tests; revisar `TranslationsTest` tras el cambio).
6. **B3/B4**: Hacer `getSpotifyAccessToken` suspend y mover el sleep-timer a coroutine main (`delay()`), eliminando el acceso a ExoPlayer desde hilo de fondo.

### Fase 2 — Estabilidad (semana 2)
7. **B5**: Corregir clave `"Player not available"` → `"player_not_available"` en QueueScreen.
8. **B6/B10**: Quitar el log del client secret y acotar/optimizar `metadataCache` en FeedScreen.
9. **B8/B9**: Cancelar `loadingJob` al cambiar de track y usar `AtomicBoolean`/dispatcher coherente para `loadingJobsActive`.
10. ✅ **D1-D7 — COMPLETADO** (UrlParser, formatDuration, thumbnails, ScanResult, NewPipeHolder, extensiones de Spotify, trackMetadataSection; 76 → 109 tests; `YoutubeAudioExtractor.kt` muerto eliminado).
11. **B7**: Simplificar `parseTimestamp` (2 formatos + `java.time.Instant.parse`) y **B12**: limpiar `getFormattedVideoCount`. **B16**: sustituir el placeholder `vi/undefined` de `getPlaylistThumbnailUrl`. **D7b**: cuando se arregle B2 (català), cablear `album_colon`/`release_colon`/`duration_colon` en `trackMetadataSection`.
12. ✅ **Crear playlists de YouTube** — **HECHO** (no vía asistente — eliminado en §14 — sino en la **pantalla de crear playlist**): selector Spotify/YouTube + `YouTubePlaylistCreator` (§12).

### Fase 3 — Arquitectura (semana 3)
12. Introducir **ViewModels por pantalla** (Search, Playlist, Local, Feed) moviendo estado, red y DB fuera de los composables.
13. Refactorizar `Config` en `SpotifyAuthStore`/`AppSettings`/`ApiKeys`; eliminar `runBlocking` en `PlaylistLocalRepository`.
14. Limpieza de dependencias (6 sin uso + `navigation-compose` del catálogo + alias duplicado) y de código muerto (MediaListItem/MediaGridItem, Color.kt).

### Fase 4 — Calidad (continuo)
15. **QrScannerDialog**: `shutdown()` del executor + `unbindAll()` en `onDispose`.
16. Añadir `key` a todas las LazyLists y `LazyColumn` en FeedScreen.
17. `isMinifyEnabled=true` + `isShrinkResources=true` con reglas proguard (Room/NewPipe).
18. Migrar strings a `strings.xml` (o al menos cablear `album_colon`/`release_colon`/`duration_colon` tras arreglar B2 y eliminar claves muertas como `not_configurat`).
19. Añadir **tests instrumentados** de flujos críticos (login/callback, tokens, permisos).
20. Versionar bien: `UpdateChecker` ya lee `versionName` del PackageInfo — consistente con build.gradle.

---

## 10. HEALTH REPORT — Integrity Score

> Sección de "salud" del código. Cada categoría puntúa de 0 a 10 (10 = excelente). Los pesos son orientativos. Puntuaciones actualizadas tras el refactor de duplicación.

### Puntuaciones por categoría

| Categoría | Nota | Justificación |
|---|---|---|
| **Correctitud / Funcionalidad** | 6.5 / 10 | Funciona y compila, pero hay 1 filtro que no filtra (B1), 1 pantalla con japonés en català (B2) y 2 riesgos de ANR/crash (B3, B4). |
| **Rendimiento** | 5.0 / 10 | Leak de threads + cámara encendida tras cerrar el escáner (Alta), cache O(n²) en Feed, OkHttpClient recreado, LazyLists sin key, Feed no lazy. |
| **Arquitectura / Mantenibilidad** | 5.0 / 10 | Siguen los monolitos y el God object `Config`, pero la **duplicación está resuelta al 100%** (D1-D7 con `UrlParser`, `formatDuration*`, `ScanResult`, `NewPipeHolder`, extensiones de Spotify y `trackMetadataSection`; código muerto eliminado). |
| **Seguridad** | 3.5 / 10 | Sin cambios. Secretos distribuibles vía anon key (S1), OAuth con client secret (S2), tokens planos con backup (S4), sin minify (S3). Lo único sólido: sin WebView, .gitignore correcto, permisos runtime. |
| **Cobertura de tests** | 6.0 / 10 | 148 unit tests en verde (+72). URL parsing, duración, inicialización NewPipe, extensiones de modelos y la creación de playlists de YouTube (con buscador inyectado) testeados de forma directa y sin reflexión; pero sigue habiendo 0 instrumentados y nada de flujos críticos. |
| **Limpieza / Estilo** | 6.0 / 10 | Código legible y comentado en general; lastrado por 2 archivos vacíos, 14 comentarios obsoletos, `println`, strings hardcodeadas y 6 deps sin usar. |

### Nota global

**5.4 / 10 — "Funciona, pero con deuda técnica acumulada"** (antes 5.0; +0.4 por duplicación resuelta y mejor cobertura)

| Estado | Interpretación |
|---|---|
| ✅ **Estable y funcional** | Compila, instala, reproduce música, los 148 unit tests pasan. Apto para uso personal diario. |
| ⚠️ **Riesgo de seguridad real** | Credenciales de Spotify son recuperables por cualquiera (Supabase anon + APK sin ofuscar) y los tokens viajan en prefs planas con backup. **Esto es lo más urgente.** |
| ⚠️ **Riesgo de crash puntual** | ExoPlayer desde Timer y red en main thread (B3, B4). |
| 🟡 **Deuda de mantenimiento** | Monolitos y los 3 focos de duplicación restantes (D3/D5/D7) hacen cada cambio lento y propenso a regresiones. |

### Top 5 acciones que más mejorarían la nota

1. **PKCE + eliminar secretos vía Supabase** (seguridad: 3.5 → ~7) — *días de trabajo, impacto máximo.*
2. **EncryptedSharedPreferences + excluir prefs del backup** (seguridad: → ~8).
3. **Arreglar los 4 bugs críticos (B1-B4)** (correctitud: 6.5 → ~8.5) — *horas de trabajo.*
4. **Hacer `getSpotifyAccessToken` suspend + terminar bugs B5-B16** (correctitud y mantenibilidad).
5. **Activar R8 + limpiar dependencias sin uso** (seguridad y APK: -30MB, → ~7).

---

## 11. CAMBIOS APLICADOS (refactor de duplicación)

Estado del repo tras la revisión 2026-08-06 (compila y 148 tests en verde):

### D1 — Extracción de URLs centralizada
- **Nuevo `utils/UrlParser.kt`** (110 líneas, sin dependencias Android): `extractYoutubeVideoId` (watch?v=, youtu.be/, /watch/, /shorts/, fallback), `extractYoutubePlaylistId` (list=, /playlist/), `extractSpotifyId`, `parseScanText` (incluye formato legacy `plyr_source:type:id`), `getUrlType`, `isPlayableUrl`, `youtubeThumbnailUrl`, `normalizeYoutubeThumb`.
- Sustituidos: `FeedScreen.kt` (3 funciones privadas + `formatTimestamp` privado), `YouTubeSearchManager.kt` (`extractVideoIdFromUrl`/`extractPlaylistIdFromUrl`), `YouTubeManager.kt` (ahora con soporte `/shorts/`), `QrScannerDialog.kt` (`parseQrContent`), `NfcReader.kt` (`parseNfcUrl`/`isValidUrl`/`getUrlType`/enum `UrlType`), `MediaMetadataExtractor.kt` (`extractSpotifyId` privado, 4 usos → `UrlParser.extractSpotifyId(url).orEmpty()`), `MainActivity.kt` (`UrlParser.getUrlType`).

### D6 — Modelo de escaneo unificado
- **Nuevo `model/ScanResult.kt`** (`data class ScanResult(source, type, id)`) sustituye a `QrScanResult` y `NfcScanResult`; actualizados `NfcReader`, `NfcScanEvent`, `QrScannerDialog`, `FeedScreen` y `MainActivity`.

### D4 — Thumbnails centralizados
- `YouTubeSearchManager.getThumbnailUrl` → `UrlParser.youtubeThumbnailUrl(videoId) ?: ""`; `PlaylistScreen.youtubeThumbTo16to9` → `UrlParser.normalizeYoutubeThumb`; `YouTubePlaylistDetailView` thumb inline → `UrlParser.youtubeThumbnailUrl`.

### D2 — Formatos de duración centralizados
- **Nuevas en `utils/Utils.kt`:** `formatDurationMs(ms: Number)` ("M:SS") y `formatDurationSeconds(s)` ("MM:SS"/"HH:MM:SS"/"En vivo").
- Sustituidos: `AssistantManager.formatDuration` (privada), expresión inline en `SongListItem.kt` y `SongMenuDialog.kt`, `YouTubeSearchManager.getFormattedDuration` → `formatDurationSeconds`.

### D3 — Inicialización NewPipe unificada
- **Nuevo `utils/NewPipeHolder.kt`** (singleton `@Synchronized`, init único e idempotente; `NewPipe.init` lanza si se llama dos veces). Sustituidos los guards de `YouTubeManager`, `MediaMetadataExtractor` y `YouTubeSearchManager`.
- **Eliminado `network/YoutubeAudioExtractor.kt`** (código muerto sin callers; duplicaba `YouTubeManager.getAudioUrl` y `NewPipe.init`).

### D5 — Extensiones de modelos Spotify
- En `SpotifyRepository.kt`: `fun List<SpotifyImage>?.firstImageUrl()` y `fun List<SpotifyArtist>.artistNames()`; los 5 métodos `getImageUrl()`/`getArtistNames()` ahora delegan.

### D7 — Fila de metadatos común
- **Nuevo `ui/components/TrackMetadataSection.kt`**: `LazyListScope.trackMetadataSection(albumName, releaseDate, durationMs)` (extensión que emite items separados, preservando el espaciado `spacedBy(8.dp)`). Usado en `SongListItem` y `SongMenuDialog`.
- **Nota:** los keys `album_colon`/`release_colon`/`duration_colon` ya existen en Translations.kt pero el català los tiene en japonés (B2) → no se cablean hasta arreglar B2.

### Tests
- Eliminado `UrlExtractorTest.kt` (reflexión); nuevos `UrlParserTest.kt` (33), `NewPipeHolderTest.kt` (3); `UtilsTest.kt` 24 → 30; `SpotifyModelsTest` 10 → 12. Total: **76 → 109**, 0 fallos.

### Pendiente de esta línea de trabajo
- **B16** (placeholder `vi/undefined` en `getPlaylistThumbnailUrl`) y **D7b** (cablear traducciones `album_colon`/`release_colon`/`duration_colon` tras arreglar B2).

---

## 12. CREACIÓN DE PLAYLISTS DE YOUTUBE (capa testeable, con la integración existente)

Ante el objetivo de *"crear playlists exactamente iguales que las de Spotify pero de YouTube"*:
- **No se usa la YouTube Data API** ni API key: la resolución de vídeos usa la integración de YouTube **ya existente** (`YouTubeManager.searchVideoId`, vía NewPipe).
- **No hay conversión de un lado a otro**: la playlist creada es **la misma** — mismo título, misma descripción y los mismos tracks en orden, solo que cada track queda resuelto a su vídeo de YouTube (playlist local con prefijo `youtube_`).
- La creación de playlists se hace desde la **UI de playlists** (el asistente de voz, que tenía el `// TODO: Implementar creación de playlist`, se eliminó en §14).

### Nuevo `service/YouTubePlaylistCreator.kt`
- `data class CreatedYouTubePlaylist(title, description, tracks)` — resultado listo para guardar.
- `class YouTubePlaylistCreator(resolveVideoId = YouTubeManager.searchVideoId)`:
  - `buildSourceTracks(selectedTracks)` — convierte los tracks de Spotify seleccionados en tracks fuente (mismo nombre, artistas e `spotifyTrackId`).
  - `build(title, description, sourceTracks, targetPlaylistId, resolvedVideoIds = emptyMap())` — resuelve cada track con la integración existente (`"<nombre> <artistas>"`), reusa `youtubeVideoId` si ya existe, luego `resolvedVideoIds[spotifyTrackId]` (para no re-buscar lo ya encontrado), omite los que no tengan vídeo y reindexa (0..n-1) escribiendo `playlistId`/`id`/`youtubeVideoId`.
  - El buscador es **inyectable** para poder testear sin red.

### Cambios en `PlaylistLocalRepository`
- Refactor: `saveYouTubePlaylist` y la nueva `saveCreatedYouTubePlaylist` delegan en el helper privado `saveYouTubePlaylistWithTracks` (sin duplicación).
- `saveCreatedYouTubePlaylist` **preserva la descripción original** (la misma playlist); `saveYouTubePlaylist` mantiene el comportamiento previo (`"YouTube Playlist by $uploader"`).
- En `PlaylistScreen.kt`: `CreateSpotifyPlaylistScreen` renombrada a **`CreatePlaylistScreen`** con selector **Spotify/YouTube**; la rama YouTube crea la playlist con `YouTubePlaylistCreator` + `saveCreatedYouTubePlaylist`. El botón `<new>` ya no depende de la conexión a Spotify, y en modo "youtube" el buscador usa `YouTubeSearchManager.searchYouTubeAll` (sin Spotify). `getYouTubeChannelName` ahora solo muestra el canal si el prefijo existe.

### Edición de playlists `youtube_` (misma UI que Spotify, sin depender de Spotify)
- `canEdit` ya no excluye las playlists de YouTube: son locales y por tanto editables igual que las propias de Spotify (sigue excluyendo `liked_songs`).
- **Renombrar / descripción**: el `<save>` de una playlist `youtube_` guarda con el nuevo `updatePlaylistDetails(localPlaylistId, newTitle, newDesc)` del repositorio (null = mantener), sin token de Spotify.
- **Añadir tracks**: el buscador del modo edición cambia a `YouTubeSearchManager.searchYouTubeAll` (videos, sin playlists) cuando la playlist es `youtube_`; los resultados se mapean con `id = videoId`, y al pulsar `+` se insertan en local vía `addTrackToYouTubePlaylist` (siguiente posición, ID único reescrito, `youtubeVideoId` = el `videoId` ya resuelto, sin re-buscar). La lista "current tracks" se refresca sola vía LiveData.
- **Quitar tracks**: `removeTrackFromYouTubePlaylist` borra por `spotifyTrackId`, reindexa posiciones y actualiza el contador.
- **Borrar la playlist**: `<delete>` usa `deleteYouTubePlaylist` (local, sin token de Spotify).

### Tests — `YouTubePlaylistCreatorTest` (15)
A los 9 originales se añaden: `buildSourceTracks_mapsNameArtistsAndSpotifyId`, `buildSourceTracks_reindexesPositions`, `buildSourceTracks_emptyListProducesEmptyTracks`, `buildFromSpotifyTracks_resolvesSamePlaylistEndToEnd`, `build_usesResolvedVideoIdsWithoutSearching`, `build_resolvedVideoIdsOverridesSearchOnlyForKnownIds`.

**Total de tests: 148, 0 fallos.**

### Nota de uso
El botón `<new>` de Playlists **siempre es visible** (ya no depende de la conexión a Spotify; `<sync>` sigue siendo solo Spotify). En la pantalla de crear, con el selector en **youtube**, el buscador usa la integración existente de YouTube (`YouTubeSearchManager.searchYouTubeAll`), así que **no necesitas Spotify** para crear playlists de YouTube ni añadirles contenido; los tracks añadidos vía esa búsqueda conservan su `videoId` exacto (no se re-buscan).

Las playlists `youtube_` también se **editan sin Spotify**: `<edit>` permite renombrar/descripción, añadir vídeos buscando en YouTube, quitarlos con `x` y borrarlas con `<delete>` (todo local, prefijo `youtube_`, nunca se toca al sincronizar con Spotify).

### Siguiente paso
Probar en el dispositivo (`./run.sh run debug`): crear playlist con tipo "youtube", verificar que aparece en la lista, que `<edit>` permite renombrar/añadir/quitar vídeos y que reproduce con audio de YouTube.

---

## 13. PORTADAS DE PLAYLISTS (recorte cuadrado + subida local, estilo Spotify)

Para **playlists locales `youtube_`** (decisión de alcance: solo locales; las de Spotify necesitarían el scope `ugc-image-upload` y re-autenticación, fuera de alcance):

- **`service/CoverCropMath.kt`** — matemática pura del recorte (testeada en JVM, 24 tests):
  - `coverScale` (la imagen cubre por completo el viewport de recorte, sin espacios vacíos), `minZoom` (zoom-out mínimo para que la imagen siga cubriendo el marco cuadrado), `focalZoom` (zoom manteniendo fijo el punto bajo los dedos, con arrastre), `clampState` (zoom entre `minZoom` y 8x; la traslación se limita para que la imagen cubra **el marco de recorte**, no el viewport completo, de modo que la imagen nunca queda "pegada"/bloqueada a un borde por grande o pequeña que sea) y `sourceRect` (el marco cuadrado, centrado en el viewport, expresado en píxeles de la imagen original; siempre cuadrado y dentro de la imagen).
- **`service/CoverImageManager.kt`** — decode de un Uri con downsampling (máx. 2048), `crop` del rectángulo calculado, `resizeToSquare` (máx. 1024) y `save` a `filesDir/covers/playlist_<rawId>.jpg` devolviendo una URI `file://` que Coil entiende (se guarda en `PlaylistEntity.imageUrl`).
- **`ui/components/CoverCropDialog.kt`** — editor simple a sangre: la imagen llena por completo el área de recorte (centrada, sin franjas grises), con un marco cuadrado blanco centrado encima y un oscurecido suave (45%) fuera de él. El zoom/pinch se aplica como **transformación gráfica real** (`graphicsLayer`: escala + traslación de la capa), de modo que la imagen crece de verdad y el punto tocado queda fijo bajo los dedos (gestos `detectTransformGestures` siempre clampados). **Admite zoom out**: el zoom puede bajar de 1 hasta `minZoom`, el punto en que la imagen dejaría de cubrir el marco de recorte; si queda más pequeña que el área, la imagen se centra (letterbox). Botones `<cancel>`/`<save>`. Recorta y entrega el Bitmap final.
- **`PlaylistLocalRepository.updatePlaylistImage(localPlaylistId, imageUrl)`** — guarda la nueva portada en local; al ser LiveData, el grid se actualiza solo.
- **`PlaylistScreen`**: en modo edición de una `youtube_` la portada (preview 120dp a la izquierda, clickable) abre el **Photo Picker** (`PickVisualMedia`, con fallback a ACTION_OPEN_DOCUMENT en dispositivos viejos); al confirmar el recorte se guarda y se refresca la entidad. `normalizeYoutubeThumb` deja pasar las rutas locales (`file://`) sin tocar.

**Total de tests: 148, 0 fallos.**

### Nota de uso
Dentro de `<edit>` de una playlist `youtube_`: tocar la portada (preview 120dp) → elegir imagen de la galería → encuadrar (la imagen llena la pantalla; pinch-zoom + arrastre) → `<save>`. La portada se guarda en el almacenamiento interno de la app (no depende de red ni de Spotify) y aparece en el grid al momento.

### Siguiente paso
Probar en el dispositivo (`./run.sh run debug`): editar una playlist `youtube_`, cambiar su portada con `<pick>`, recortarla y verificar que aparece tanto en el grid como en la preview del modo edición.

---

## 14. ELIMINACIÓN DEL ASISTENTE DE VOZ

Se eliminó **por completo** el asistente de voz (micrófono + NLU on-device + TTS + activación por gestos), preservando el resto de interacciones (shake/orientación de transporte, NFC, gestos, etc.).

### Ficheros eliminados
- `assistant/AssistantManager.kt` (NLU con ONNX + ejecución de comandos + sleep-timer).
- `assistant/AssistantVoiceHelper.kt` (SpeechRecognizer).
- `assistant/AssistantTTSHelper.kt` (TextToSpeech).
- `utils/AssistantActivationEvent.kt` (Flow global de activación por shake).
- `res/xml/actions.xml` (deep links de Google Assistant).

### Código/UI retirado
- `ui/HomeScreen.kt`: overlay de micrófono con animación CAVA, respuesta con efecto typewriter, gesto pull-to-activate, petición de `RECORD_AUDIO`, launcher de permisos y listener de voz. La pantalla vuelve a ser un layout simple (Row/Column) sin `pointerInput`/drag. Se quitaron los imports muertos (`Manifest`, `LocalDensity`, `ContextCompat`, `fadeIn/fadeOut`, `delay/launch/withContext/Dispatchers`, `Icons.Filled.Mic/Close`, `clickable`, `TextOverflow`).
- `ui/ConfigScreen.kt`: `AssistantConfigSection` (checkboxs enable/same-language/TTS + selector de idioma), el ítem "assistant" del `MultiToggle` de shake y el composable `CheckboxOption` (quedó sin usos).
- `MainActivity.kt`: rama `ShakeDetector.ACTION_ASSISTANT` que disparaba `AssistantActivationEvent`.
- `utils/ShakeDetector.kt`: constante `ACTION_ASSISTANT` (y su doc).
- `utils/Config.kt`: claves `KEY_ASSISTANT_*`, defaults `DEFAULT_ASSISTANT_*`, `SHAKE_ACTION_ASSISTANT` y los 8 métodos `is/setAssistant*`. `getShakeAction` **migra** el valor legacy `"assistant"` guardado en prefs a `"off"`.
- `utils/Translations.kt`: **~390 líneas** de claves `assistant_*`, `enable_assistant`, `enable_tts`, `shake_assistant` y sus comentarios de sección en los 4 idiomas. Se conservan `auto_suggestions`/`contextual_help` (sin uso) y `enabled`/`disabled`.
- `TranslationsTest.kt`: `assistant_cmd_add_queue` sustituida por `exit_message` en `coreKeys`.
- `AndroidManifest.xml`: permiso `RECORD_AUDIO`. `app/build.gradle.kts`: dependencia `onnxruntime-android:1.26.0`. `README.md`: línea de permisos `RECORD_AUDIO`.

### Lo que NO se tocó
- Acciones de transporte por shake (`off/next/previous/play_pause`) y por orientación (`volume/skip`), NFC, QR, feed, gestos, idiomas, etc.
- La reproducción, búsqueda, playlists ~~y descargas~~ y el resto de pantallas (el feature de descargas se eliminó posteriormente en la §15).

### Tests
- El asistente no tenía tests propios, así que el total se mantiene: **148 unit tests, 0 fallos** (`./run.sh test`). Compilación `:app:compileDebugKotlin` BUILD SUCCESSFUL.

---

## 15. ELIMINACIÓN DEL FEATURE LOCAL / DESCARGAS

Se eliminó **por completo** el feature local/descargas (pantalla Local, descarga de audio desde YouTube, importación de archivos y detección de audio con AcoustID/fpcalc), preservando las playlists de **Spotify/YouTube** (`PlaylistLocalRepository`), el historial de búsqueda y el resto de pantallas.

### Ficheros eliminados
- `ui/LocalScreen.kt` (~1.114 líneas: ventana Local con descargas, playlists locales e importación).
- `utils/DownloadManager.kt` (descarga de YouTube, importación de audio y borrado de playlists locales).
- `service/AudioDetection.kt` (detección de audio con fpcalc + AcoustID).
- `database/DownloadedTrackEntity.kt`, `database/DownloadedTrackDao.kt`.
- `database/LocalPlaylistEntity.kt`, `database/LocalPlaylistTrackEntity.kt`, `database/LocalPlaylistDao.kt`.

### Código/UI retirado
- `ui/AudioListScreen.kt`: `Screen.LOCAL` del enum y la rama `Screen.LOCAL -> LocalScreen(...)`.
- `ui/HomeScreen.kt`: botón `< local >` (y su traducción `home_local` en los 4 idiomas).
- `ui/components/SongListItem.kt`: botón de descarga del menú, acción de swipe `SWIPE_ACTION_DOWNLOAD` (icono + handler) e imports de `DownloadManager`/`PlaylistDatabase`/`withContext`/`Dispatchers`.
- `ui/components/SongMenuDialog.kt`: opción de descargar, imports de `DownloadManager`/`PlaylistDatabase` y el parámetro `coroutineScope` (ya sin uso; se quitó también del caller en `FloatingMusicControls.kt`).
- `utils/Config.kt`: `KEY_ACOUSTID_API_KEY`, `SWIPE_ACTION_DOWNLOAD` y los 3 métodos de AcoustID. `getSwipeLeftAction`/`getSwipeRightAction` **migran** el valor legacy `"download"` guardado en prefs al default. `clearAllApiKeys` ya no borra AcoustID.
- `ui/ConfigScreen.kt`: sección `AcoustidApiConfigSection` + su llamada, el mapeo `keys["acust_id"]` de `SupabaseClient.getAutomaticKeys()` y la opción "download" (índice 4) de los dos `MultiToggle` de swipe.
- `utils/Translations.kt`: **~40 claves** `acoustid_*`, `home_local`, `plyr_local`, `download` y `swipe_action_download` (+ comentarios de sección `// AcoustID Configuration`) en los 4 idiomas. Se conservan `note_local_storage` (credenciales) y `lastfm_*`.
- `AndroidManifest.xml`: permisos `READ_EXTERNAL_STORAGE` y `READ_MEDIA_AUDIO` (solo los usaba la importación de la ventana Local).
- `app/build.gradle.kts` + `gradle/libs.versions.toml`: dependencia `fpcalc-android` (QuickLyric).
- `viewmodel/PlayerViewModel.kt`: rama de reproducción de archivos `file://` (muerta, solo la generaba el feature local).

### Base de datos
- `database/PlaylistDatabase.kt`: se quitan las 3 entidades y 2 DAOs del `@Database` (versión 5 → **6**) y se añade **`MIGRATION_5_6`** que hace `DROP TABLE IF EXISTS` de `downloaded_tracks`, `local_playlists` y `local_playlist_tracks`. Así los usuarios existentes conservan playlists, tracks e historial al abrir la app.

### Lo que NO se tocó
- `PlaylistLocalRepository` y la creación/edición de playlists de **Spotify y YouTube**, `PlaylistDao`/`TrackDao`/`SearchHistoryDao`, `SearchHistoryEntity`.
- `SimpleDownloader`/`NewPipeHolder` (descarga de JSON de YouTube, sin relación con el feature eliminado).
- Acciones de swipe restantes (`queue/liked/playlist/share`) y de shake/orientación, NFC, QR, feed, etc.

### Tests
- Ningún test referenciaba el feature (los 148 unit tests existentes se mantienen **148, 0 fallos** con `./run.sh test`). Compilación `:app:compileDebugKotlin` BUILD SUCCESSFUL.

## 16. REDISEÑO DE PLAYLISTS EN EL HOME (carrusel horizontal)

Se eliminó la ventana de listado de playlists (`PlaylistsScreen` como listado con grid de playlists, Liked Songs, álbumes y artistas seguidos) y el botón `< playlists >` del Home. En su lugar, el Home muestra un **carrusel horizontal de portadas** (estilo Spotify) con las playlists reales de la base de datos local, más un tile final **"+"** para crear playlist. Abrir una portada lleva directamente a la vista de tracks de esa playlist; el botón "+" abre el creador de playlists. **No se incluye nada de Spotify**: el carrusel muestra únicamente playlists (excluye `liked_songs` y `album_*`), y el botón de sincronización `<sync>` desaparece.

### Navegación (`ui/AudioListScreen.kt`)
- Nuevos estados `playlistToOpenId` y `openPlaylistCreate` (rememberSaveable).
- `HomeScreen` recibe `onOpenPlaylist(id)` y `onCreatePlaylist()`; ambos navegan a `Screen.PLAYLISTS`.
- `PlaylistsScreen` recibe `initialPlaylistId`, `openCreate` y `onInitialConsumed` y, al abrirse, carga directamente la playlist indicada (o muestra `CreatePlaylistScreen`) y resetea los flags.

### Home (`ui/HomeScreen.kt`)
- Eliminado el botón `ActionButtonData` de playlists (y la clave `home_playlists` de `Translations.kt` en los 4 idiomas, sustituida por `home_new_playlist` para el tile "+").
- El carrusel se muestra **entre el botón de search y el de queue** (tanto en el layout vertical como en el horizontal). Carrusel `LazyRow` con portada 120 dp (AsyncImage vía `UrlParser.normalizeYoutubeThumb`, con fallback de letra inicial si no hay imagen), nombre debajo, y tile "+" final que abre el creador. Datos reactivos desde `PlaylistLocalRepository.getAllPlaylistsLiveData()`. Extraído a `HomePlaylistCarousel`: usa `remember { LazyListState() }` (no restaurable, para no heredar el scroll de sesiones anteriores) y `LaunchedEffect(playlists) { scrollToItem(0) }` para que **al entrar en Home empiece siempre por la primera playlist** (izquierda), no por el "+" del final. Se eliminó el `contentPadding` del `LazyRow` para que no haya márgenes extra a izquierda/derecha.

### Playlists (`ui/PlaylistScreen.kt`, ~2.400 → ~2.000 líneas)
- Retirada la ventana de listado: botones `<sync>`/`<new>`, la cuadrícula de playlists de YouTube sin conectar y la cuadrícula conectada (con Liked Songs, Saved Albums y Followed Artists).
- El `when` de la pantalla pasa a `if (selectedPlaylist != null) { ... }`: solo existe la **vista de tracks de playlist** (reproducción, edición, borrado, share). El título ahora es el nombre de la playlist abierta (se eliminó el fallback `plyr_lists`).
- `BackHandler` simplificado: desde una playlist (o con cambios de edición sin guardar) siempre vuelve al **Home**, nunca al listado.
- Tras crear/guardar/borrar, la navegación vuelve al Home (`onBack()`), donde el carrusel refleja los cambios vía LiveData.
- El creador de playlists (`CreatePlaylistScreen`, en el mismo archivo) se conserva tal cual, ahora invocable solo desde el carrusel.

### Traducciones (`utils/Translations.kt`)
- Eliminadas `home_playlists`, `plyr_lists`, `<sync>`, `<syncing...>` y `<new>`; añadida `home_new_playlist` (es/en/ca/ja). Se conservan `Spotify not connected` y `Loading tracks...` (usados por `CreatePlaylistScreen`).

### Lo que NO se tocó
- La vista de tracks de una playlist (detalil), `CreatePlaylistScreen`, la edición de portadas y `PlaylistLocalRepository`.
- Nota: queda código muerto (cargadores de Liked Songs/álbumes/artistas y el sub-flujo de artista) que quedó inalcanzable al quitar la ventana de listado; pendiente de limpieza en una futura iteración.

### Tests
- Compilación `:app:compileDebugKotlin` BUILD SUCCESSFUL y **148 tests, 0 fallos** con `./run.sh test`.

*Generado a partir de auditoría estática. Todos los hallazgos críticos están verificados contra el código. Los números de línea corresponden al estado actual del repo (commit working tree de 2026-08-02).*
