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
- En la rama de trabajo, el agente **nunca hace push**, ni crea el PR, ni hace
  merge. El push y el PR son del usuario.
- En `main`, el agente **nunca** ejecuta `git stash`, `git commit`, `git merge`
  ni `git push`. De `main` se ocupa el usuario.
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
5. Deja la rama limpia y **no hagas push**.
6. Entrega un resumen con rama, commits y comandos ejecutados. El usuario hace
   el push y abre/mergea el PR.

Si al empezar hay cambios sin commitear que no ha creado el agente, **para y
pregunta**. No uses `git stash` para esconder trabajo ajeno.

# Por qué

- `main` es lo que se publica: el workflow de release hace commit del bump ahí.
- Un push del agente podría disparar CI o release sin revisión del usuario.
- `git stash` en `main` puede mezclar trabajo de varias tareas y perder contexto.
- Una rama por cambio hace que el PR, el changelog y el bump de versión sean
  trazables.

# Auto-release

Al mergear un PR en `main` se dispara el release automáticamente. El salto se
elige con la etiqueta `release:patch`, `release:minor`, `release:major` o
`release:skip`; sin etiqueta se usa `patch`. Detalle en
[auto-release al mergear](../systems/release-on-merge.md).
