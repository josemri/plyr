---
type: Decision
title: "0004 · Room en Android, SQLDelight en escritorio"
description: "Cada plataforma conserva su ORM. La paridad se garantiza con contratos compartidos y tests, no con un ORM común."
resource: repo://desktop/src/main/sqldelight
tags: [room, sqldelight, persistencia, decisiones, paridad]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: room
    resource: repo://app/src/main/java/com/plyr/database/PlaylistDatabase.kt
    title: Room (Android)
  - id: sqldelight
    resource: repo://desktop/src/main/sqldelight/com/plyr/desktop/database/Playlist.sq
    title: SQLDelight (escritorio)
---

# Decisión

- **Android**: Room `version = 8`, `exportSchema = false`, migraciones
  explícitas `5→6→7→8`.
- **Escritorio**: SQLDelight 2.4, esquema equivalente, **sin migraciones** (parte
  de base nueva).
- Los modelos y reglas viven en `:core` (`PlaylistRow`, `TrackRow`,
  `PlaylistFilters`, `TrackIdentity`).

# Por qué

Room es la opción idiomática en Android (Flow, migraciones, KSP) y SQLDelight es
JVM puro (sin kapt/KSP, sin Android). Un ORM común obligaría a renunciar a
migraciones de Android o a añadir dependencias Android en escritorio.

# Coste aceptado

- Dos esquemas que mantener sincronizados.
- Riesgo de divergencia. Mitigación:
  - tests de mapeo en `:app`;
  - esquema SQLDelight comentado contra `PlaylistEntity`/`TrackEntity`;
  - el formato de sync como contrato de datos;
  - [schema-change](../playbooks/schema-change.md) como checklist.

# Alternativas descartadas

- SQLDelight en Android: posible, pero pierde migraciones y herramientas Room ya
  en uso.
- Room en escritorio: no es JVM puro y arrastra Android.
- Un módulo `:database` común: no resuelve el motor distinto, solo añade capas.

# Regla práctica

Un cambio de columna se hace **tres veces**: Room, SQLDelight y el JSON de sync
(si aplica). Si solo se hace una, es un bug esperando.
