---
type: Playbook
title: "Preparar una release"
description: "Cómo se publica plyr. La versión la incrementa el workflow; un agente no la toca a mano."
resource: repo://.github/workflows/auto-release.yml
tags: [release, versionado, firma, ci]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Antes

1. `main` verde en CI (`config`, `analysis`, `tests`, `build`, `guardrails`).
2. `./run.sh verify full` verde en local (o incidencias documentadas).
3. `./run.sh okf-check` verde (valida el bundle y que el mapa esté al día).
4. Changelog revisado: los mensajes de commit se usan para generarlo.

# Publicar

El workflow es **manual** (`workflow_dispatch`) con `bump: patch|minor|major`.
Hace:

1. Calcula versión y changelog.
2. Hace bump de `versionCode`/`versionName` en `app/build.gradle.kts`.
3. Compila APK firmado con R8 y guarda el informe de uso.
4. Crea release **draft**.
5. Construye instaladores de escritorio (Linux/Windows/macOS), firma Windows y
   notariza macOS.
6. Publica la release versionada, crea el tag y refresca `latest`.
7. Si algo falla, borra draft y tag.

# Reglas para agentes

- **No cambies `versionCode`/`versionName`** en una tarea normal.
- No edites `auto-release.yml` sin permiso explícito.
- No imprimas secretos ni contenidos de `keystore.properties`.
- No publiques una release desde local: usa el workflow.
- Si un instalable falla, revisa `jpackage`/`jlink` y el icono antes de tocar la
  versión.

# Después

- Verifica que `latest` apunta a los assets nuevos.
- Comprueba `UpdateChecker` contra `latest`.
- Si cambió el formato de sync o el esquema, actualiza los conceptos y
  `okf/log.md`.
