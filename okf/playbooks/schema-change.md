---
type: Playbook
title: "Cambiar esquema o formato persistido"
description: "Checklist para tocar Room, SQLDelight, tombstones o plyr-sync sin romper datos ni compatibilidad entre plataformas."
resource: repo://okf/domain/sync-format.md
tags: [room, sqldelight, migracion, sync, compatibilidad]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Antes de empezar

Pregúntate:

1. ¿Es un campo nuevo, un renombrado o un cambio de tipo?
2. ¿Afecta al ZIP de sync o solo a una base local?
3. ¿Hay datos publicados que deban sobrevivir?

Si hay datos publicados y no hay migración clara, **para y pregunta**.

# Room (Android)

1. Añade el campo a `PlaylistEntity` / `TrackEntity`.
2. Crea `MIGRATION_N_N+1` con SQL explícito.
3. Sube `version` en `PlaylistDatabase`.
4. Registra la migración en `addMigrations(...)`.
5. No uses `fallbackToDestructiveMigration(true)`.
6. Añade test de mapeo en `app/src/test`.

# SQLDelight (escritorio)

1. Cambia el `.sq` en `desktop/src/main/sqldelight/`.
2. Actualiza consultas y repositorios.
3. Recuerda: el escritorio **no migra**; si hay datos de usuarios, hay que
   decidir explícitamente qué hacer.
4. Añade o actualiza el test del repositorio.

# Formato de sync

1. ¿El campo debe viajar? Si sí, es aditivo y opcional.
2. ¿Cambia la semántica? Sube `ExportManifest.FORMAT_VERSION`.
3. Actualiza `ExportManifest`, `ImportManifest` y `ExportDigest`.
4. Añade fixture en `core/src/jvmTest/resources/fixtures/`.
5. Prueba ida y vuelta: exportar → importar → comparar.
6. Un archivo antiguo debe seguir leyéndose (o fallar con mensaje claro).

# Identidad y tombstones

- No cambies `TrackIdentity.likedTrackKey` sin migrar tombstones: los
  invalidarías.
- Si añades una entidad borrable, añade su tombstone y su campo en el JSON.

# Verificación

```bash
./run.sh verify unit
./run.sh verify full
```

Checklist final:

- [ ] Room y SQLDelight coherentes.
- [ ] JSON de sync coherente (si aplica).
- [ ] Fixture antigua sigue leyéndose.
- [ ] Tests de migración/mapeo verdes.
- [ ] Concepto actualizado + `okf/log.md`.
- [ ] `FORMAT_VERSION` y `version` revisados a propósito.
