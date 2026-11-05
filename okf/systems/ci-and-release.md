---
type: System
title: "CI y releases"
description: "Workflows de GitHub Actions. CI valida configuración, análisis, tests y build; auto-release firma APK, empaqueta escritorio y publica. Una sola fuente de versión."
resource: repo://.github/workflows
tags: [ci, release, github-actions, firma, versionado]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: ci
    resource: repo://.github/workflows/ci.yml
    title: ci.yml
  - id: release
    resource: repo://.github/workflows/auto-release.yml
    title: auto-release.yml
---

# CI (`ci.yml`)

Se ejecuta en push a `main`, pull requests y manualmente. Permisos de solo
lectura y concurrencia cancelable.

| Job | Qué hace |
|---|---|
| `config` | `./gradlew help --no-daemon` — resuelve plugins sin SDK |
| `analysis` | `:app:detektMain :desktop:detektMain :core:detektMainJvm` + `:app:lint` |
| `tests` | `:app:testDebugUnitTest`, `:core:jvmTest`, `:desktop:test` (+ `:core:testAndroidHostTest`) |
| `build` | `:app:assembleDebug` + `:desktop:createDistributable` |
| `guardrails` | `scripts/ai/guards.sh`, `scripts/ai/okf_check.py`, `scripts/ai/okf_map.py --check` |

`DISPLAY=''` en tests para que Compose Desktop no intente abrir ventana.

# Release (`auto-release.yml`)

Se dispara al mergear un PR en `main` mediante
[release-on-merge](release-on-merge.md), que elige `patch`/`minor`/`major` con
las etiquetas `release:*`. También se puede lanzar a mano
(`workflow_dispatch`) con `bump: patch|minor|major`. Jobs:

1. **release**: calcula versión y changelog, hace bump en
   `app/build.gradle.kts`, decodifica el keystore, compila APK firmado, extrae
   el informe R8 y crea una release **draft**.
2. **desktop**: matriz Linux/Windows/macOS, construye instaladores, firma
   Windows y notariza macOS.
3. **publish**: commitea el bump, crea el tag, publica la release versionada y
   refresca `latest`.
4. **cleanup**: si algo falla, borra draft y tag.

# Versionado

- `versionCode` y `versionName` viven **solo** en `app/build.gradle.kts`.
- `:desktop` lee `versionName` de ese fichero y genera `BuildInfo`.
- `UpdateChecker` compara contra la release `latest`.
- No cambies la versión en una tarea normal: la incrementa el workflow.

# Reglas para agentes

- La política de ramas y pushes está en
  [branch-policy](../constraints/branch-policy.md): `main` solo lo toca el
  usuario; el agente commitea en su rama y no hace push.
- No edites workflows de release sin pedir permiso explícito.
- No añadas secretos ni los imprimas.
- Un job nuevo debe ser barato y no duplicar lo que ya hace `verify`.
- Si cambias la matriz de tests, actualiza `scripts/ai/verify.sh` y este
  concepto en el mismo cambio.
- `okf_map.py --check` falla si `okf/maps/repo-map.md` está desactualizado e
  imprime el diff; regenéralo con `./run.sh map`. Las filas `versionCode` y
  `versionName` se ignoran en la comparación porque las incrementa el workflow
  de release en `main`; el resto de la tabla de versiones sí se comprueba.
