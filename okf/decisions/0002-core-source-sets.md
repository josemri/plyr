---
type: Decision
title: "0002 · Source sets de :core"
description: "Jerarquía KMP manual con commonMain, jvmSharedMain, jvmMain, androidMain y tests enlazados a mano. Los flags de gradle.properties son necesarios, no deuda."
resource: repo://core/build.gradle.kts
tags: [kmp, source-sets, gradle, decisiones]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
sources:
  - id: core-build
    resource: repo://core/build.gradle.kts
    title: core/build.gradle.kts
  - id: props
    resource: repo://gradle.properties
    title: gradle.properties
---

# Decisión

Usar una jerarquía **manual**:

```text
jvmSharedMain  →  commonMain
jvmMain        →  jvmSharedMain
androidMain    →  jvmSharedMain
jvmTest        →  commonTest
androidHostTest→  commonTest
```

Con `kotlin.mpp.applyDefaultHierarchyTemplate=false` y las aristas declaradas a
mano en `core/build.gradle.kts`.[^core-build] [^props]

# Por qué

`jvmSharedMain` no encaja en la plantilla por defecto JVM+Android: necesitamos un
source set intermedio para `java.*`, NewPipe, OkHttp y ZXing que **no** puede
vivir en `commonMain` (multiplataforma estricto) pero sí compartirse entre el JVM
de escritorio y Android/ART.

# Flags deliberados

| Flag | Motivo |
|---|---|
| `kotlin.mpp.applyDefaultHierarchyTemplate=false` | Permite la jerarquía manual. |
| `android.builtInKotlin=false` | `:core` aplica Kotlin Multiplatform con versión explícita; si se activa, Gradle falla por plugin duplicado. |
| `android.newDsl=false` | `kotlin-android` no es compatible con el DSL nuevo. |

# Alternativas descartadas

- Meter `java.*` en `commonMain`: no compila para todos los targets.
- Duplicar código en `jvmMain` y `androidMain`: exactamente lo que `:core` evita.
- Usar la plantilla por defecto y renombrar targets: no cubre el source set
  intermedio.

# Consecuencias

- `./gradlew help` debe seguir pasando tras cualquier cambio de build.
- Migrar a built-in Kotlin es una tarea aparte, en rama propia, con los cuatro
  targets verdes.
- Un agente no debe "simplificar" estos flags ni las aristas.

[^core-build]: core/build.gradle.kts
[^props]: gradle.properties
