---
type: Constraint
title: "Política de ramas y pushes"
description: "main es producción y solo la toca el usuario. Cada implementación o fix vive en una rama propia; el agente puede commitear ahí, pero nunca hace push."
resource: repo://AGENTS.md
tags: [ramas, git, main, push, agentes, flujo]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [scripts/ai/guards.sh]
---

# Regla

- `main` es **producción** y la única rama de integración. No hay `develop`,
  `staging` ni ramas de entorno: se trabaja con ramas por tarea que se mergean
  directamente en `main`.
- **Toda implementación nueva** empieza en una rama nueva creada desde `main`.
- **Todo fix** también va en una rama propia. Nunca se corrige directamente en
  `main`.
- En la rama de trabajo, el agente **puede hacer commits**.
- Con **permiso explícito del usuario**, el agente también puede hacer **push**,
  crear **PRs**, **mergear** ramas en `main` y **cerrar PRs**. Sin ese permiso,
  el push/PR/merge siguen siendo del usuario.
- En `main`, el agente **nunca** ejecuta `git stash`, `git commit`, `git merge`
  ni `git push` **salvo** cuando el usuario le ha dado permiso explícito para
  mergear una rama; aun así, evita `git stash` en `main`.
- Cambiar a `main` para leer, comparar o comprobar el estado sí está permitido,
  siempre que el árbol esté limpio y no se modifique nada.

# Nombres de rama

| Prefijo | Uso |
|---|---|
| `feat/<slug>` | funcionalidad nueva |
| `fix/<slug>` | corrección de un bug |
| `docs/<slug>` | documentación, OKF o plantillas |
| `ci/<slug>` | workflows y verificación |
| `chore/<slug>` | mantenimiento sin cambio de comportamiento |
| `refactor/<slug>` | reorganización sin cambio funcional |

Un PR = una rama = un cambio coherente. No mezcles tareas no relacionadas.

# Flujo del agente

1. Comprueba que `main` está limpio: `git status -sb`.
2. Crea la rama: `git switch -c fix/<slug>`.
3. Trabaja y commitea en esa rama.
4. Ejecuta la verificación que corresponda.
5. **Antes de crear un PR, pregunta al usuario qué tipo de auto-release quiere**
   (`skip`, `patch`, `minor` o `major`) y aplica la etiqueta `release:<tipo>`
   correspondiente.
6. Con permiso explícito del usuario, haz push, crea el PR, espera a CI y
   auto-release, y mergea/cierra cuando todo esté verde. Sin permiso, deja la
   rama lista y entrega el resumen para que el usuario haga el push/PR/merge.
7. Entrega un resumen con rama, commits, PR y estado de CI/auto-release.
8. Tras un merge exitoso: **borra la rama** (local y remota) una vez que CI y,
   si hubo bump de versión, el auto-release terminen con éxito. Si el
   auto-release falla, conserva la rama para investigar.

Si al empezar hay cambios sin commitear que no ha creado el agente, **para y
pregunta**. No uses `git stash` para esconder trabajo ajeno.

# Por qué

- `main` es lo que se publica: el workflow de release hace commit del bump ahí.
- Un push del agente puede disparar CI o release: por eso exige permiso explícito
  y pregunta el tipo de auto-release antes del PR.
- `git stash` en `main` puede mezclar trabajo de varias tareas y perder contexto.
- Una rama por cambio hace que el PR, el changelog y el bump de versión sean
  trazables.

# Auto-release

Al mergear un PR en `main` se dispara el release automáticamente. El salto se
elige con la etiqueta `release:patch`, `release:minor`, `release:major` o
`release:skip`; sin etiqueta se usa `patch`. Detalle en
[auto-release al mergear](../systems/release-on-merge.md).
