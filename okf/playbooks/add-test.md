---
type: Playbook
title: "Añadir o mover un test"
description: "Dónde va cada test, qué dobles usar y cómo ejecutarlo sin red, sin VLC y sin dispositivo."
resource: repo://okf/constraints/verification.md
tags: [tests, junit, dobles, flujo]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Elegir destino

| El test usa… | Va a |
|---|---|
| Solo Kotlin/stdlib | `core/src/commonTest` |
| `java.*`, `org.json`, red, QR | `core/src/jvmTest` |
| Android real (`Context`, `Uri`, cámara, SAF) | `app/src/androidTest` |
| ViewModel/repositorio Android sin Android real | `app/src/test` |
| Controlador o SQLDelight de escritorio | `desktop/src/test` |

Si un test de `:app` no usa Android, muévelo a `:core`.

# Convenciones

- JUnit 4 (`@Test`, `@Before`, `@After`).
- Nombre descriptivo: `next_conRepeatAll_daLaVueltaAlFinal`.
- Un comportamiento por test; sin `Thread.sleep`.
- Sin red, sin VLC, sin dispositivo salvo tests explícitos de integración.
- Dobles locales, no mocks pesados: `FakeAudioPlayer`, `FakeResolver`, repositorios
  en memoria.
- `Dispatchers.Unconfined` o un scope de test para coroutines.

# Tests que se omiten

Los que dependen de red o de `libvlc` usan:

```kotlin
assumeTrue("Sin conexión: se omite", canReachYoutube())
```

Un test omitido **no es un test pasado**. Si tu cambio depende de él, dilo en el
PR y verifícalo manualmente.

# Ejecutar

```bash
./run.sh verify unit          # todos
./run.sh test desktop         # atajo escritorio
./run.sh test device          # instrumentados (requiere móvil)
./gradlew :core:jvmTest --tests '*QueueEngineTest'
```

# Checklist

- [ ] El test falla sin el cambio.
- [ ] El test pasa con el cambio.
- [ ] No depende de orden, hora, red ni estado global.
- [ ] Limpia recursos (`@After`).
- [ ] Si es un bug, el nombre o el comentario referencia el `B*`.
