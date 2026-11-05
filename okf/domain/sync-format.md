---
type: Data Format
title: "Formato de sincronización plyr-sync"
description: "Contrato del ZIP de exportación/importación entre Android y escritorio. FORMAT_VERSION = 1. Cambiarlo o renombrar campos rompe la compatibilidad."
resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ExportManifest.kt
tags: [sync, import, export, compatibilidad, zip, formato]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/jvmTest/kotlin/com/plyr/utils/ExportManifestTest.kt, core/src/jvmTest/kotlin/com/plyr/utils/ImportManifestTest.kt, core/src/jvmTest/kotlin/com/plyr/utils/ImportArchiveTest.kt, core/src/jvmTest/kotlin/com/plyr/utils/ExportDigestTest.kt]
sources:
  - id: export
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ExportManifest.kt
    title: ExportManifest.kt
  - id: import
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ImportManifest.kt
    title: ImportManifest.kt
  - id: archive
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ImportArchive.kt
    title: ImportArchive.kt
---

# Contenedor

`plyr-sync.zip` contiene:

```text
playlists.json
covers/<playlist-id>.jpg
```

Nada más se lee. `ImportArchive` ignora entradas fuera del formato, rutas con
`..`, nombres demasiado largos y directorios.[^archive]

# `playlists.json`

```json
{
  "app": "_plyr",
  "formatVersion": 1,
  "appVersion": "1.4.2",
  "exportedAt": 1792514701745,
  "playlistCount": 9,
  "trackCount": 170,
  "deletedPlaylistIds": ["..."],
  "removedLikedTrackKeys": ["..."],
  "playlists": [
    {
      "id": "...",
      "name": "...",
      "description": "...",
      "coverEntry": "covers/....jpg",
      "source": "YOUTUBE",
      "sourceId": "...",
      "tracks": [
        {
          "position": 0,
          "name": "...",
          "artists": ["..."],
          "remoteTrackId": "...",
          "youtubeVideoId": "..."
        }
      ]
    }
  ]
}
```

`FORMAT_VERSION = 1`.[^export] `source`, `sourceId`, `deletedPlaylistIds` y
`removedLikedTrackKeys` son aditivos: un archivo antiguo no los trae y se cae a
la heurística o a conjunto vacío.

# Invariantes

- `formatVersion` distinto ⇒ `ManifestFormatException`; **no** se intenta migrar
  a ciegas.[^import]
- Los campos nuevos son opcionales y con valor por defecto.
- Las pistas se ordenan por `position`.
- `artists` se serializa como array y se une con `", "` al leer.
- El ZIP se escribe de forma atómica y se intercambia al final; nunca se deja un
  archivo a medias.
- `ExportDigest` (SHA-256) **excluye** `exportedAt`: si el contenido no cambió,
  no se reescribe la copia.

# Import

`ImportManifest.plan` decide por lista: `Create`, `MergeLikedSongs` o `Skip`
(con `SkipReason`). Aplica tombstones locales + entrantes y no resucita lo
borrado.

# Al cambiar el formato

1. Sube `FORMAT_VERSION`.
2. Actualiza `ExportManifestTest` e `ImportManifestTest`.
3. Añade fixture en `core/src/jvmTest/resources/fixtures/`.
4. Actualiza este concepto y `okf/log.md`.
5. Comprueba lectura/escritura cruzada Android ↔ escritorio.

[^export]: ExportManifest.kt
[^import]: ImportManifest.kt
[^archive]: ImportArchive.kt
