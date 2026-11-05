---
type: System
title: "Auto-release al mergear en main"
description: "release-on-merge.yml dispara auto-release.yml al mergear un PR. El salto se elige con release:patch/minor/major; sin etiqueta, patch."
resource: repo://.github/workflows/release-on-merge.yml
tags: [release, ci, versionado, etiquetas]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: workflow
    resource: repo://.github/workflows/release-on-merge.yml
    title: release-on-merge.yml
  - id: resolver
    resource: repo://scripts/ai/release_bump.py
    title: release_bump.py
  - id: auto-release
    resource: repo://.github/workflows/auto-release.yml
    title: auto-release.yml
tests: [scripts/ai/test_release_bump.py]
---

# Qué hace

Al mergear un PR en `main`, `.github/workflows/release-on-merge.yml` dispara
`auto-release.yml` con el salto que indican las etiquetas del PR:

| Etiqueta | Versión | Cuándo |
|---|---|---|
| `release:patch` | `1.2.X` | fixes, docs, tests, refactors internos, sin cambio de contrato |
| `release:minor` | `1.X.0` | funcionalidad nueva compatible, opción/UI nueva, API pública nueva |
| `release:major` | `X.0.0` | breaking change, contrato persistido (`FORMAT_VERSION`, Room, SQLDelight), eliminación/renombrado, subida de mínimo |
| `release:skip` | — | no publicar (docs/chore) |

- Sin etiqueta de release se usa `patch`.
- Si hay varias `release:*`, gana la mayor (`major` > `minor` > `patch`).
- `release:skip` gana sobre todas.

# Flujo

```
rama fix/... --commit--> PR a main --CI verde--> merge
                                              |
                                              v
                              release-on-merge (lee etiquetas)
                                              |
                                              v
                              auto-release.yml -f bump=patch|minor|major
                                              |
                                              v
                              APK + escritorio + tag + release + bump en main
```

1. `release-on-merge.yml` se dispara con `pull_request_target: closed`.
2. Solo continúa si `merged == true` y la base es `main`.
3. `scripts/ai/release_bump.py` lee las etiquetas del payload y devuelve
   `patch`, `minor`, `major` o `skip`.
4. Si no es `skip`, ejecuta:
   `gh workflow run auto-release.yml --ref main -f bump=<salto>`.

# Detalles de implementación

- Se usa `pull_request_target` en vez de `pull_request` porque el token de un PR
  desde un fork es de solo lectura. El workflow **no ejecuta código del PR**:
  solo lee su payload y hace checkout de `main`.
- Permisos: `actions: write` (para `workflow_dispatch`) y `contents: read`.
- `GITHUB_TOKEN` sí puede disparar `workflow_dispatch`; no hace falta un PAT.
- `auto-release.yml` ya tiene `concurrency: auto-release` y
  `cancel-in-progress: false`: los releases se encolan, no se pisan.
- El commit del bump lleva `[skip ci]`, así que no vuelve a disparar CI ni el
  propio release.
- Cada merge lanza 5 runners (APK + 4 escritorio). Para docs/chore se puede usar
  `release:skip`.
- La lógica de etiquetas está en `scripts/ai/release_bump.py` y se prueba en
  `scripts/ai/test_release_bump.py`, que corre en el job `guardrails`.

# Por qué etiquetas y no tamaño del diff

Un umbral de LOC/files no mide impacto: un refactor de 2.000 líneas puede ser
`patch` y un cambio de 3 líneas en `FORMAT_VERSION` debe ser `major`. La
etiqueta es explícita y revisable en el PR.

Si se quisiera cero intervención, una heurística posible sería:

| Señal | Bump |
|---|---|
| `BREAKING CHANGE` o `!` en el título, o rutas de contrato (`ExportManifest`, `PlaylistDatabase`, `*.sq`, `libs.versions.toml`, `compileSdk`/`minSdk`) | `major` |
| rama `feat/` o umbral medio de líneas/ficheros | `minor` |
| resto | `patch` |

No se usa como fuente única: un refactor grande subiría `major` sin ser
breaking, y un cambio pequeño en el formato de sync debería ser `major`.

# Comportamiento esperado

- Un PR con `release:patch` lanza un release `X.Y.Z+1`.
- Un PR con `release:minor` lanza un release `X.Y+1.0`.
- Un PR con `release:major` lanza un release `X+1.0.0`.
- Sin etiqueta se usa `patch`; con `release:skip` no se lanza nada.
- Un PR cerrado sin merge, o con base distinta de `main`, no dispara nada.
- El release sigue siendo atómico (draft + cleanup) y el tag apunta al commit
  con la versión incrementada.
