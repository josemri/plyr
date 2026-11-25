---
type: Proposal
title: "Orquestador puro de PlayerViewModel"
description: "Plan futuro para extraer la orquestación de la cola a un componente puro en :core, testeable en JVM. NO implementado."
resource: repo://okf/proposals/player-viewmodel-orchestrator.md
tags: [android, viewmodel, cola, propuesta, futuro, no-implementado]
module: repo
generated: { by: human:josep, at: 2026-11-25T00:00:00Z }
status: draft
stale_after: 2027-05-05
---

> ⚠️ **Propuesta, no arquitectura actual.** Hoy la orquestación vive en `PlayerViewModel`.

# Propuesta: orquestador puro de `PlayerViewModel`

## Motivación

`PlayerViewModel` (Android) mezcla orquestación de cola (ventana, skips, repeat, carga de URLs) con estado de UI y acceso a plataforma. El roadmap lo lista como diferido por requerir Robolectric o extraer un orquestador puro con dependencias inyectadas. Cubrirlo con tests permitiría validar cambios de cola sin dispositivo.

## Objetivo

Extraer a `:core` un `PlayerOrchestrator` (o similar) que:

- Reciba dependencias inyectadas: `AudioPlayer`, `TrackResolver`, `QueueEngine`, `Config`.
- Gestione la ventana deslizante, skips, repeat, carga de URLs y transiciones.
- Exponga estado observable (título actual, índice, loading, error) sin depender de Android.
- Sea testeable en JVM con dobles.

## Pasos propuestos

1. **Identificar la lógica pura.** `startAt`, `growWindow`, `trimWindow`, `navigateToNext/Previous`, `onTrackEnded`, `onItemUnplayable` y la gestión de `PendingSkips` no usan APIs de Android directamente salvo el reproductor; ya dependen de `QueueEngine`/`QueueIndex` en `:core`.
2. **Definir interfaz `PlaybackController`** en `:core` con las operaciones que la UI necesita (`play`, `pause`, `next`, `previous`, `setQueue`, etc.) y que `PlayerViewModel` implemente delegando.
3. **Mover el estado a un `StateFlow`/`MutableState`** expuesto por el orquestador; `PlayerViewModel` lo mapea a `LiveData`/`State` para Compose.
4. **Inyectar dependencias** en el constructor del orquestador; en Android se construye con `ExoAudioPlayer`, en tests con `FakeAudioPlayer`.
5. **Migrar gradualmente**: primero los skips y la ventana, luego la carga de URLs (`TrackResolver`), manteniendo `PlayerViewModel` como fachada.
6. **Tests**: añadir `commonTest` para el orquestador (cola, skips, repeat, errores de carga).

## Riesgos

- `PlayerViewModel` tiene ~1000 líneas y muchas interdependencias; migrar todo de golpe es arriesgado.
- El reproductor es mutable y compartido; hay que cuidar la sincronización del índice.
- `TrackResolver` usa `YouTubeManager` (red); en tests se inyecta un doble.

## Verificación

- `./run.sh verify full` verde.
- Nuevos tests en `:core/commonTest` para el orquestador.
- No ampliar `detekt-baseline.xml`.

## Alcance de este PR

Solo documentación del plan. La implementación se hará en PRs posteriores, empezando por el paso 1 (skips y ventana).
EOF
echo written