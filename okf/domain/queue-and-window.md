---
type: Domain Concept
title: "Cola y ventana de reproducción"
description: "QueueEngine y WindowState deciden navegación, repetición y qué tramo de la cola está cargado. La ventana es contigua y la generación invalida cargas en vuelo."
resource: repo://core/src/commonMain/kotlin/com/plyr/viewmodel/QueueEngine.kt
tags: [cola, ventana, repeat, reproduccion, paridad]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/commonTest/kotlin/com/plyr/viewmodel/QueueEngineTest.kt, core/src/commonTest/kotlin/com/plyr/viewmodel/QueueIndexTest.kt, core/src/commonTest/kotlin/com/plyr/viewmodel/WindowStateTest.kt, desktop/src/test/kotlin/com/plyr/desktop/player/PlayerControllerTest.kt]
---

# Piezas

| Pieza | Responsabilidad |
|---|---|
| `QueueEngine<T>` | Cola opaca, índice actual, repeat y ventana. Genérico: no conoce `TrackEntity`. |
| `QueueIndex` | Navegación pura: siguiente, anterior, fin de pista, reintento. |
| `WindowState` | Invariante de ventana: `startIndex` + `generation`. |
| `PendingSkips` | Skips pedidos antes de que la resolución termine. |
| `IdlePlayback` | Qué hacer con play cuando el player quedó en IDLE. |

# Invariantes

1. La ventana es un **tramo contiguo** de la cola que empieza en `windowStart`.
2. `generation` se incrementa al invalidar: cualquier resolución en vuelo de una
   generación anterior se descarta.
3. `windowAhead = 2` y `keepBehind = 1` por defecto. El `keepBehind` permite que
   `<<` sea inmediato (B49); sin él se cae al camino asíncrono.
4. `trimCount` nunca descarta la pista en reproducción ni las `keepBehind`.
5. `>>` con `repeat = all` da la vuelta; con `off`/`one`, no. `repeat = one` no
   afecta a un salto manual.
6. `previous` con `positionMs > 3000` reinicia la pista en vez de retroceder.

# Flujo

```text
UI (Android: PlayerViewModel / Desktop: PlayerController)
  → QueueEngine.setQueue / moveTo
  → QueueEngine.fillTarget(mediaItemCount)  → resolver pistas nuevas
  → AudioPlayer.setMediaItems(...)
  → QueueEngine.trimCount(...)              → recortar lo viejo
  → WindowState.shiftForward(...)
```

La lógica de cola **no** conoce coroutines, red, Android ni el motor de audio.
Esos viven en el ViewModel/Controlador.

# Errores históricos

B57, B58, B59, B62, B63: ventana desalineada, player en IDLE con items cargados,
y `>>` que no encontraba nada tras una extracción interrumpida. Los tests de
`QueueEngineTest`, `WindowStateTest` e `IdlePlaybackTest` son la red de seguridad.

# Al cambiar

- Toca `:core` primero y ejecuta `QueueEngineTest` + `WindowStateTest`.
- Replica el cambio en `PlayerViewModel` **y** `PlayerController`.
- Si cambias los valores de ventana, justifica el impacto en memoria y en saltos.
- No dupliques la lógica en el controlador.
