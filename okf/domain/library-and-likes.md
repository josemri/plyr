---
type: Domain Concept
title: "Favoritos y tombstones"
description: "Identidad de pista compartida, fusión de favoritos y borrados que no resucitan. La clave de marcado, fusión y tombstone debe ser literalmente la misma."
resource: repo://core/src/commonMain/kotlin/com/plyr/utils/TrackIdentity.kt
tags: [favoritos, liked, tombstones, sync, identidad]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/commonTest/kotlin/com/plyr/utils/TrackIdentityTest.kt, core/src/commonTest/kotlin/com/plyr/utils/LikedSongsMergeTest.kt, core/src/commonTest/kotlin/com/plyr/utils/TombstonesTest.kt]
---

# Identidad de una pista

`TrackIdentity` es la única fuente de verdad:

- Si `youtubeVideoId` no está vacío, la identidad es ese id.
- Si falta, la clave es `"$name|$artists"` (`fallbackDedupeKey`).
- `likedTrackKey(track)` y `likedTrackKey(videoId, name, artists)` **deben**
  producir lo mismo.

Si el marcado usara un criterio y la fusión otro, un `youtubeVideoId` vacío
crearía una clave que la fusión nunca consulta: el borrado no se propaga y la
pista resucita (B1, B13, B51).

# Fusión

`LikedSongsMerge` combina listas locales y entrantes sin duplicar y respetando los
tombstones. `ImportManifest.plan` la usa al importar `plyr-sync.zip`.

# Tombstones

`Tombstones` es lógica pura; la persistencia es de cada plataforma:

- Android: `TombstoneConfig` sobre `SharedPreferences`.
- Escritorio: `TombstoneStore` sobre SQLDelight/Preferences.

Operaciones: `merge`, `add`, `remove` (idempotentes). Un tomb creado al importar
viaja al exportar; el borrado se propaga en ambos sentidos.

Se tombstonan dos cosas:

| Clave | Dónde viaja |
|---|---|
| id de lista borrada | `deletedPlaylistIds` |
| clave de favorito quitado | `removedLikedTrackKeys` |

# Al cambiar

- No cambies `likedTrackKey` sin migración: invalida todos los tombstones.
- Actualiza el export/import y los tests de ida y vuelta.
- Comprueba `TrackIdentityTest`, `LikedSongsMergeTest`, `TombstonesTest` y
  `ImportManifestTest`.
- La UI de favoritos debe usar el repositorio, nunca comparar nombres a mano.
