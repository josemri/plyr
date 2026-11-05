---
type: Reference
title: "Glosario"
description: "Términos del dominio, nombres que se repiten y qué significan los IDs M* y B* de los comentarios."
resource: repo://okf/index.md
tags: [glosario, referencia, historia]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Términos

| Término | Significado |
|---|---|
| **pista / track** | Una canción dentro de una lista. |
| **lista / playlist** | Colección ordenada de pistas. |
| **favoritos / liked** | Lista especial `liked_songs`. |
| **ventana** | Tramo contiguo de la cola cargado en el reproductor. |
| **tombstone / tomb** | Marca de borrado que impide resucitar datos al sincronizar. |
| **sync** | Export/import del ZIP `plyr-sync.zip`. |
| **paridad** | Que Android y escritorio hagan lo mismo. |
| **resolución** | Convertir una pista en un id/URL reproducible. |
| **extracción** | Obtener la URL de audio de YouTube con NewPipe. |
| **share** | Compartir pista/lista/app por QR, NFC o enlace. |

# IDs históricos `M*` y `B*`

Los comentarios del código usan IDs como `M22`, `M34`, `B57`. Son referencias
**históricas** a hitos de desarrollo y bugs, no documentación viva:

- `M*`: hito o milestone del plan interno.
- `B*`: bug conocido/corregido.

**El comentario adyacente es la fuente de verdad**, no el número. No inventes una
tabla de equivalencias ni renombres IDs: si necesitas contexto, lee el KDoc y el
test que lo cubre.

# Nombres que no debes confundir

| Nombre | Dónde |
|---|---|
| `PlayerViewModel` | Android (`:app`) |
| `PlayerController` | Escritorio (`:desktop`) |
| `AudioPlayer` | Interfaz común (`:core`) |
| `ExoAudioPlayer` | Implementación Android |
| `VlcAudioPlayer` | Implementación escritorio |
| `TrackEntity` / `Track` | Room / SQLDelight |
| `TrackRow` / `PlaylistRow` | Interfaces comunes |
| `PlaylistSource` | Origen persistido (no deducido) |
| `ExportManifest` / `ImportManifest` | Escritura / lectura del ZIP |
