---
type: Decision
title: "0003 · Formato de sync v1"
description: "El ZIP de sync usa JSON con formatVersion 1, campos aditivos y tombstones. Es un contrato entre plataformas, no un detalle interno."
resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ExportManifest.kt
tags: [sync, formato, compatibilidad, decisiones]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: export
    resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/ExportManifest.kt
    title: ExportManifest.kt
---

# Decisión

`plyr-sync.zip` con `playlists.json` + `covers/`, `FORMAT_VERSION = 1`.

- `app: "_plyr"` identifica el formato.
- `formatVersion` incompatible ⇒ error explícito, sin migración automática.
- Los campos nuevos son **aditivos y opcionales**.
- Los borrados viajan como `deletedPlaylistIds` y `removedLikedTrackKeys`.
- El ZIP se escribe de forma atómica y se compara por digest antes de reescribir.

# Por qué

Android y escritorio comparten datos sin servidor. Un formato legible, versionado
y tolerante permite que una versión antigua ignore campos nuevos sin corromper
nada, y que una nueva no resucite datos borrados.

# Alternativas descartadas

- **Base de datos compartida**: exige servidor y acopla esquemas Room/SQLDelight.
- **JSON sin versión**: imposible saber si un archivo es compatible.
- **Migración automática de versiones**: frágil y sin fixture real; mejor fallar
  claro.
- **Incluir `exportedAt` en el digest**: cambiaría en cada ejecución y forzaría
  reescrituras innecesarias.

# Consecuencias

- Cambiar `FORMAT_VERSION` es una decisión de producto, no un refactor.
- Hay que mantener fixtures de lectura/escritura.
- Ver [formato de sincronización](../domain/sync-format.md) y
  [schema-change](../playbooks/schema-change.md).
