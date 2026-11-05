---
type: Decision
title: "0005 · Port web aplazado"
description: "El port a navegador es una propuesta, no arquitectura actual. No existen :shared, :web ni :server. No crear esos módulos sin aprobación explícita."
resource: repo://okf/proposals/web-port.md
tags: [web, propuesta, decisiones, alcance]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-02-05
sources:
  - id: proposal
    resource: repo://okf/proposals/web-port.md
    title: Propuesta de port web
---

# Decisión

El plan de port a navegador se conserva como **propuesta** en
[`proposals/web-port.md`](../proposals/web-port.md). No se implementa y no
describe el estado actual.

# Qué NO es cierto hoy

- No existe `:shared`.
- No existe `:web`.
- No existe `:server`.
- No hay servidor propio de extracción ni frontend servido en `localhost:8080`.

Si un agente encuentra esas menciones, debe tratarlas como plan futuro.

# Por qué se conserva

El documento contiene análisis útil (paridad funcional, hitos, extracción desde
servidor). Borrarlo perdería contexto; implementarlo sin decisión de producto
sería peor.

# Regla

- Para tocar código actual, la fuente de verdad es `okf/systems/*` y el código.
- Para retomar el port: decisión explícita, revisar versiones y hitos (el plan
  puede estar desactualizado), y actualizar este concepto.
- No crear módulos `:shared`/`:web`/`:server` como parte de una tarea normal.
