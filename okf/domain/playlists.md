---
type: Domain Concept
title: "Listas y pistas"
description: "Modelo compartido de listas y pistas, origen persistido, filtros de visibilidad y reglas de reordenación. Android usa Room; escritorio, SQLDelight."
resource: repo://core/src/commonMain/kotlin/com/plyr/database/PlaylistRow.kt
tags: [listas, playlists, room, sqldelight, filtros, paridad]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/commonTest/kotlin/com/plyr/database/PlaylistFiltersTest.kt, core/src/commonTest/kotlin/com/plyr/database/PlaylistSourceTest.kt, app/src/test/java/com/plyr/ui/utils/PlaylistMappingsTest.kt]
---

# Modelo

`PlaylistRow` y `TrackRow` son interfaces de `:core`; cada plataforma las
implementa con su entidad (`PlaylistEntity`/`TrackEntity` en Room,
`Playlist`/`Track` en SQLDelight). `PlaylistRecord` y `TrackRecord` son los
equivalentes neutros que usa el sync.

| Campo | Nota |
|---|---|
| `remoteId` | Clave primaria de la lista (id de YouTube/Spotify o uuid local). |
| `source` | `LOCAL`, `SPOTIFY`, `YOUTUBE`, `UNKNOWN`. **Persistido**, no deducido de la descripción. |
| `sourceId` | Id en el servicio de origen, si aplica. |
| `trackCount` | Lo mantienen las tres rutas que modifican pistas; no se recalcula en la UI. |
| `imageUrl` | Portada remota; la local va por `coverEntry` en el export. |
| `position` | Orden de la pista dentro de la lista. |

# Lista especial de favoritos

`LikedSongs.ID = "liked_songs"`. La fila se crea al arrancar y **no se borra**
aunque esté vacía: `toggleLikeTrack`, la fusión y el import la asumen.

# Filtros de visibilidad

`PlaylistFilters.visiblePlaylists`:

1. Excluye `album_*` (álbumes de Spotify que no son listas del usuario).
2. Excluye `liked_songs` si `trackCount == 0`.
3. Ordena favoritos primero y luego por nombre.

Es un **filtro, no un borrado**. Cambiar esto rompe tres rutas a la vez.

# Origen y share

El origen mandatorio evita deducir el tipo de lista por prefijos del id o por la
descripción (editable). Una lista `SPOTIFY` se comparte como URL de Spotify; una
`YOUTUBE`, como lista de YouTube. Ver [QR y share](qr-nfc.md).

# Reordenación

Android: `ui/components/Reorder.kt` + `PlaylistScreen`. Escritorio:
`ui/components/Reorder.kt` + `PlaylistDetailView`. La lista se reescribe con
posiciones contiguas; no se confía en el orden de inserción de la base.

# Al cambiar

- Actualiza `PlaylistRow`/`TrackRow` y **las dos** entidades.
- Mantén el orden `position` y `trackCount` coherentes.
- Si cambias `PlaylistSource`, actualiza `fromStorage`, el export y el import.
- Pasa `PlaylistFiltersTest` y `PlaylistMappingsTest`.
