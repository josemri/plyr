---
type: Decision
title: "0001 · Límites de módulos"
description: "Tres módulos con responsabilidades disjuntas (app Android, core KMP, desktop JVM). No existen :shared, :web ni :server; crear módulos nuevos requiere decisión explícita."
resource: repo://settings.gradle.kts
tags: [arquitectura, modulos, decisiones, kmp]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: settings
    resource: repo://settings.gradle.kts
    title: settings.gradle.kts
---

# Decisión

```text
:app      Android: UI, Room, Media3, SAF, CameraX
:core     KMP Android+JVM: dominio, cola, red, sync, parsers, i18n
:desktop  JVM 21: UI Compose Desktop, VLC, SQLDelight, MPRIS
```

Dependencias permitidas: `:app → :core`, `:desktop → :core`. Nunca
`:app → :desktop`, `:desktop → :app` ni un cuarto módulo "compartido" sin
decisión.

# Contexto

Android y escritorio necesitan la misma lógica (cola, sync, identidad, URLs) pero
tienen UI, persistencia y motor de audio distintos. `:core` evita duplicar la
lógica; las plataformas conservan sus implementaciones.

# Alternativas descartadas

- **Un solo módulo Android**: impediría el escritorio.
- **`:shared` con toda la UI**: Compose Android y Compose Desktop no son
  intercambiables en este proyecto; forzarlo acopla versiones y rompe el
  empaquetado.
- **`:server`/`:web` ahora**: ver [0005](0005-web-port-deferred.md).

# Consecuencias

- Dos implementaciones de UI y persistencia que hay que mantener en paridad.
  Mitigación: tests compartidos en `:core` + playbook
  [feature-parity](../playbooks/feature-parity.md).
- `:core` debe mantenerse libre de Android y de UI.
- Los contratos entre plataformas (`AudioPlayer`, `ExportManifest`,
  `TrackIdentity`, `ShareUrlPolicy`) son API pública de facto.

# Cuándo revisar

Si el port web se aprueba, o si aparece una tercera plataforma. Entonces se
revisa primero el reparto de `:core`, no se crea un módulo por impulso.
