---
type: Domain Concept
title: "Importación de Spotify"
description: "Parsea el embed público de Spotify y construye un plan de búsqueda en YouTube. No guarda nada: la persistencia y la búsqueda se inyectan."
resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/SpotifyImport.kt
tags: [spotify, import, playlists, red]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/jvmTest/kotlin/com/plyr/utils/SpotifyImportTest.kt, app/src/test/java/com/plyr/utils/UtilsTest.kt]
---

# Por qué vive en :core

En Android estaba atado a `Context` y Room. En `:core` queda lo reutilizable:
parseo del HTML embebido, descarga y construcción del plan. Ni almacenamiento ni
motor de búsqueda concretos entran: `searchTrack` se inyecta.

# Flujo

```text
URL/ID de Spotify
  → fetchPlaylist (embed público)
  → parseo de __NEXT_DATA__
  → SpotifyPlaylist(id, name, imageUrl, tracks[])
  → ImportPlan: por cada pista, buscar en YouTube y numerar
  → la plataforma guarda (Room / SQLDelight)
```

- `https://open.spotify.com/embed/playlist/<id>`.
- Acepta `open.spotify.com/playlist/...`, `spotify:playlist:...` y variantes.
- El id debe ser alfanumérico de 22 caracteres.
- `YOUTUBE_SEARCH_DELAY_MS = 1500` entre búsquedas para no disparar rate limits.

# Plataformas

- Android: `utils/SpotifyImporter.kt` envuelve `:core` y guarda en Room.
- Escritorio: `spotify/SpotifyImportService.kt` y
  `spotify/SpotifyImportController.kt`.

# Reglas

- No metas `Context`/Room en `:core`.
- No elimines el delay sin medir el rate limit de YouTube.
- Si Spotify cambia el HTML, el test con HTML de muestra debe fallar primero.
- Una lista importada es `PlaylistSource.SPOTIFY` con `sourceId`; se comparte
  como URL de Spotify, no como YouTube.
