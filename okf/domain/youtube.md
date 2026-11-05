---
type: Domain Concept
title: "YouTube"
description: "Búsqueda, resolución de ids y extracción de URLs de audio con NewPipe Extractor. La URL de googlevideo caduca y se cachea; una extracción interrumpida no es un fallo real."
resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/network/YouTubeManager.kt
tags: [youtube, newpipe, streaming, cache, red]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/jvmTest/kotlin/com/plyr/network/AudioUrlExtractionTest.kt, core/src/jvmTest/kotlin/com/plyr/network/NetworkSmokeTest.kt, core/src/jvmTest/kotlin/com/plyr/utils/NewPipeHolderTest.kt, core/src/jvmTest/kotlin/com/plyr/network/SimpleDownloaderLogRedactionTest.kt]
sources:
  - id: manager
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/network/YouTubeManager.kt
    title: YouTubeManager.kt
  - id: downloader
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/network/SimpleDownloader.kt
    title: SimpleDownloader.kt
---

# Responsabilidad

`YouTubeManager` es el único punto que habla con NewPipe: búsqueda de vídeos y
listas, resolución de id por título/artista, extracción de URL de audio y caché.

# API relevante

| Función | Uso |
|---|---|
| `searchVideoId(query)` | Búsqueda rápida de un id. |
| `resolveVideoId(name, artists, id)` | Resuelve una pista que llegó sin id. |
| `searchVideos` / `searchAll` | Resultados paginados de vídeos y listas. |
| `getPlaylistInfo` / `getPlaylistVideos` | Detalle de una lista de YouTube. |
| `getAudioUrl(videoId, forceRefresh)` | URL reproducible (cacheada). |
| `cachedUrl` / `invalidate` / `clearCache` | Gestión explícita de la caché. |

# Caché y timeouts

- `URL_CACHE_TTL_MS = 2 h`: suficiente para una sesión sin re-resolver en cada
  salto, sin arriesgar URLs caducadas.
- `EXTRACTION_TIMEOUT_MS = 30 s`.
- Las extracciones simultáneas del mismo vídeo se comparten con
  `CompletableDeferred`.

# Extracción interrumpida ≠ fallo

`Extraction.Finished(url)` y `Extraction.Interrupted` son distintos a propósito:
si quien extraía se canceló, **no se ha intentado de verdad** y el que espera
debe reintentar; devolver `null` hacía que `>>` descartara la canción (B57).

# Red y privacidad

`SimpleDownloader` envuelve OkHttp y aplica cookie de modo restringido y
cabeceras. Los logs **ocultan** valores sensibles (B32): nunca imprimas URLs
firmadas, cookies ni parámetros completos. `SimpleDownloaderLogRedactionTest` lo
vigila.

# Plataformas

- Android: `service/YouTubeSearchManager.kt` adapta a la UI y a `TrackEntity`.
- Escritorio: `search/SearchController.kt` y
  `player/YouTubeTrackSourceResolver.kt`; **el fichero descargado manda** sobre
  la URL de streaming (M16).
- Descargas: `utils/AudioDownloader.kt` (app) y `download/AudioDownloader.kt`
  (desktop).

# Al cambiar

- No cambies el formato de URL cacheada sin invalidar la caché.
- Mantén `UrlParser` como parser único de URLs.
- Los tests de red usan `assumeTrue`; sin conexión se omiten, no fallan.
- Si NewPipe cambia, actualiza la versión en `gradle/libs.versions.toml` y
  ejecuta el smoke test con red.
