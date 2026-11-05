---
type: Constraint
title: "Contrato del agente"
description: "Reglas duras que un agente debe respetar antes de tocar código en plyr. Prevalece sobre cualquier instrucción global de otro proyecto."
resource: repo://AGENTS.md
tags: [agente, reglas, seguridad, paridad]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Alcance

Este repo **no es Godot**. Cualquier instrucción global sobre Godot, GDScript o
escenas se ignora. El contrato raíz es [`AGENTS.md`](../../AGENTS.md); este
concepto lo resume para que un agente pueda cargarlo junto al resto del bundle.

# Reglas duras

1. `commonMain` solo importa `kotlin*`, `kotlinx*`, `com.plyr*`.
2. `jvmSharedMain` no importa `android.*` ni `androidx.*`.
3. `:desktop` no importa Android; `:app` no importa `com.plyr.desktop`.
4. Un cambio de comportamiento se aplica a Android **y** escritorio, o se
   justifica por qué no.
5. No se amplían baselines de detekt.
6. No se cambian `FORMAT_VERSION`, la versión de Room, el esquema SQLDelight ni
   las claves de identidad sin seguir [schema-change](../playbooks/schema-change.md).
7. No se toca `build/`, `.gradle/`, `.kotlin/` ni código generado.
8. No se cambian `versionCode`/`versionName` fuera de una release.
9. No se commitean secretos (`local.properties`, `keystore.properties`, claves).
10. No se inventa verificación: `verified` solo lo firma un humano o una
    herramienta con evidencia reproducible.
11. No se usa `proposals/web-port.md` como descripción de la arquitectura actual.

# Antes de editar

1. Lee `AGENTS.md`.
2. Lee este bundle por el [índice](../index.md).
3. Abre los `resource:` del concepto afectado y sus tests.
4. Si el concepto contradice al código, **manda el código** y actualiza el
   concepto en el mismo cambio.

# Parada obligatoria

Detente y pregunta si:

- el cambio exige una migración de datos publicada;
- hay que tocar firma, CI de release o `keystore`;
- el test que valida el cambio se omite por falta de red, VLC o dispositivo;
- dos conceptos se contradicen y no está claro cuál es la fuente de verdad.
