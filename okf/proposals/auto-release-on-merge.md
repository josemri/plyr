---
type: Proposal
title: "Auto-release al mergear en main"
description: "Propuesta para disparar auto-release.yml al mergear cada PR y elegir patch/minor/major con etiquetas. NO implementada: hoy la release es manual."
resource: repo://.github/workflows/auto-release.yml
tags: [release, ci, versionado, propuesta, no-implementado]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: draft
stale_after: 2027-05-05
sources:
  - id: auto-release
    resource: repo://.github/workflows/auto-release.yml
    title: auto-release.yml
---

> ⚠️ **Propuesta, no implementada.** `auto-release.yml` sigue siendo manual
> (`workflow_dispatch`). No edites workflows de release sin permiso explícito
> (ver [CI y releases](../systems/ci-and-release.md)).

# Objetivo

Que cada PR mergeado en `main` dispare automáticamente el release y que el
salto de versión sea el adecuado:

| Cambio | Versión | Etiqueta |
|---|---|---|
| pequeño / fix | `1.2.X` | `release:patch` |
| funcionalidad compatible | `1.X.0` | `release:minor` |
| cambio rompedor | `X.0.0` | `release:major` |

# Diseño recomendado: etiqueta en el PR + dispatcher

La etiqueta es la única fuente de verdad del salto. Es explícita, revisable en
el PR y no depende de heurísticas frágiles.

## 1. Etiquetas

| Etiqueta | Cuándo |
|---|---|
| `release:patch` | fixes, docs, tests, refactors internos, sin cambio de contrato |
| `release:minor` | funcionalidad nueva compatible, opción/UI nueva, API pública nueva |
| `release:major` | breaking change, contrato persistido (`FORMAT_VERSION`, Room, SQLDelight), eliminación/renombrado, subida de mínimo |
| `release:skip` | opcional: no publicar (solo docs/chore) |

Reglas:

- Si hay varias `release:*`, gana la mayor (`major` > `minor` > `patch`).
- Si no hay ninguna, se usa `patch`.
- `release:skip` gana sobre las demás.

## 2. Workflow disparador

Nuevo `.github/workflows/release-on-merge.yml`:

```yaml
name: Release on merge

on:
  pull_request:
    types: [closed]

permissions:
  actions: write
  contents: read
  pull-requests: read

jobs:
  dispatch:
    if: >-
      github.event.pull_request.merged == true &&
      github.event.pull_request.base.ref == 'main'
    runs-on: ubuntu-latest
    steps:
      - name: Elegir bump según las etiquetas
        id: bump
        env:
          GH_TOKEN: ${{ github.token }}
          PR: ${{ github.event.pull_request.number }}
        run: |
          set -euo pipefail
          LABELS=$(gh pr view "$PR" --repo "$GITHUB_REPOSITORY" \
            --json labels --jq -r '.labels[].name')
          BUMP=patch
          if grep -qx 'release:skip' <<< "$LABELS"; then
            echo "skip=true" >> "$GITHUB_OUTPUT"
            exit 0
          fi
          if grep -qx 'release:major' <<< "$LABELS"; then BUMP=major
          elif grep -qx 'release:minor' <<< "$LABELS"; then BUMP=minor
          fi
          echo "skip=false" >> "$GITHUB_OUTPUT"
          echo "bump=$BUMP" >> "$GITHUB_OUTPUT"

      - name: Lanzar Auto Release
        if: steps.bump.outputs.skip == 'false'
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh workflow run auto-release.yml \
            --repo "$GITHUB_REPOSITORY" \
            --ref main \
            -f bump="${{ steps.bump.outputs.bump }}"
```

`workflow_dispatch` es una de las excepciones que `GITHUB_TOKEN` sí puede
disparar (junto con `repository_dispatch`). Si el repo lo bloqueara, se usa un
PAT fine-grained con `actions: write` guardado como secreto.

## 3. Por qué no calcularlo solo por líneas cambiadas

Un umbral de LOC/files es determinista pero no mide impacto: un refactor de
2.000 líneas puede ser `patch` y un cambio de 5 líneas en `FORMAT_VERSION` es
`major`. La etiqueta permite que la persona decida con contexto. Si se quiere
automatizar la sugerencia, se puede añadir un job que comente la etiqueta
recomendada según las rutas tocadas, pero **no** que la aplique sin revisión.

## 4. Si quieres cero intervención: heurística por tamaño

Si prefieres que nadie elija etiqueta, el dispatcher puede calcular el bump con
el diff del PR (`gh pr diff --name-only` + `--patch`). Una matriz posible:

| Señal | Bump |
|---|---|
| `BREAKING CHANGE` o `!` en el título, o rutas de contrato (`ExportManifest`, `PlaylistDatabase`, `*.sq`, `libs.versions.toml`, `compileSdk`/`minSdk`) | `major` |
| rama `feat/` o umbral medio de líneas/ficheros | `minor` |
| resto | `patch` |

Ejemplo de umbrales: `patch` con menos de 150 líneas y menos de 8 ficheros;
`minor` entre 150 y 800 líneas; `major` por encima de 800. Los números son
arbitrarios y hay que calibrarlos con el historial del repo.

Problema: un refactor grande sube `major` sin ser breaking, y un cambio de tres
líneas en el formato de sync debería ser `major`. Por eso la recomendación es
**etiqueta con sugerencia automática** (o `actions/labeler` por rutas), no una
heurística pura de tamaño. Si aun así quieres cero intervención, empieza con la
matriz de arriba y revisa los primeros releases.

## 5. Flujo completo

```
rama fix/... --commit--> PR a main --CI verde--> merge
                                              |
                                              v
                              release-on-merge (lee etiqueta)
                                              |
                                              v
                              auto-release.yml -f bump=patch|minor|major
                                              |
                                              v
                              APK + escritorio + tag + release + bump en main
```

- `auto-release.yml` ya tiene `concurrency: auto-release` y
  `cancel-in-progress: false`: los releases se encolan, no se pisan.
- El commit del bump lleva `[skip ci]`, así que no vuelve a disparar CI ni el
  propio release.
- Cada merge lanza 5 runners (APK + 4 escritorio). Si el coste importa, usar
  `release:skip` para docs/chore.

# Alternativas descartadas

- **Añadir `push: main` a `auto-release.yml`**: no hay etiqueta en el evento
  `push`; habría que consultar el PR asociado por API y el workflow se
  dispararía también con el commit del propio bump (salvo `[skip ci]`).
- **Conventional Commits**: `fix:` → patch, `feat:` → minor, `feat!:` → major es
  válido, pero exige disciplina en el título y no distingue bien un fix enorme
  de un breaking change pequeño.
- **Tamaño del diff**: ver arriba; no recomendado como única fuente.

# Criterios de aceptación (cuando se implemente)

- [ ] Un PR mergeado con `release:patch` lanza un release `X.Y.Z+1`.
- [ ] Un PR mergeado con `release:minor` lanza un release `X.Y+1.0`.
- [ ] Un PR mergeado con `release:major` lanza un release `X+1.0.0`.
- [ ] Sin etiqueta se usa `patch`; con `release:skip` no se lanza nada.
- [ ] El workflow no se dispara para PRs cerrados sin merge ni para bases
      distintas de `main`.
- [ ] El release sigue siendo atómico (draft + cleanup) y el tag apunta al
      commit con la versión incrementada.
