---
type: Constraint
title: "Verificación"
description: "Comandos canónicos, qué cubre cada uno y qué significa \"verde\". Un test omitido no es un test pasado."
resource: repo://scripts/ai/verify.sh
tags: [tests, verificacion, ci, detekt]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [scripts/ai/verify.sh, scripts/ai/guards.sh]
---

# Comandos

```bash
./run.sh verify fast       # configuración + compilación de módulos afectados
./run.sh verify unit       # :app:testDebugUnitTest :core:jvmTest
                           # :core:testAndroidHostTest :desktop:test
./run.sh verify analysis   # detektMain (app/desktop/core) + :app:lint
./run.sh verify full       # unit + analysis + assembleDebug + createDistributable
./run.sh verify changed     # solo módulos tocados según git
./run.sh guards            # imports, prints, baselines, higiene
./run.sh okf-check         # bundle + mapa generado
```

`./run.sh test` y `./run.sh check` siguen existiendo por compatibilidad, pero
**no cubren todo**: `test` no ejecuta `:core:jvmTest` ni `:desktop:test`, y
`check` no ejecuta tests de escritorio. Para replicar CI usa `verify full`.

# Qué significa verde

- Código de salida 0.
- Cero tests fallidos. Los **omitidos** (`assumeTrue`, sin red, sin `libvlc`, sin
  dispositivo) no cuentan como pasados: se listan en el PR.
- detekt sin findings nuevos. Si hay un finding, se arregla; no se amplía el
  baseline.
- `guards` y `okf-check` sin errores.

# Matriz de tests

| Dónde | Qué prueba | Requisitos |
|---|---|---|
| `core/src/commonTest` | lógica pura multiplataforma | ninguno |
| `core/src/jvmTest` | JVM, red, JSON, QR | JDK; red opcional |
| `core/src/androidHostTest` | `commonMain` compila para Android | JDK + SDK |
| `app/src/test` | ViewModels, DB mapping, utils | JDK |
| `app/src/androidTest` | cámara, SAF reales | dispositivo |
| `desktop/src/test` | controladores, VLC fake, SQLDelight | JDK; `libvlc` opcional |

# Reglas

- No inventes tareas de Gradle: usa las de este concepto o `./gradlew tasks`.
- No declares verde un comando que no ejecutaste.
- Si un test falla por entorno (sin red, sin VLC), dilo y distingue “omitido” de
  “fallido”.
- Un bug corregido deja un test que falla antes del arreglo.
