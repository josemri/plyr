---
type: Proposal
title: "MusicService posee el reproductor"
description: "Plan futuro para mover la propiedad del reproductor al servicio Android, habilitando Android Auto. NO implementado."
resource: repo://okf/proposals/music-service-owns-player.md
tags: [android, servicio, reproductor, propuesta, futuro, no-implementado]
module: repo
generated: { by: human:josep, at: 2026-11-24T00:00:00Z }
status: draft
stale_after: 2027-05-05
---

> ⚠️ **Propuesta, no arquitectura actual.** Hoy el reproductor vive en `PlayerViewModel`.
> Este documento describe un plan futuro.

# Propuesta: `MusicService` posee el reproductor

## Motivación

Hoy `PlayerViewModel` (Android) instancia `ExoAudioPlayer` y `MusicService` solo envuelve su `MediaSession`. Eso impide que Android Auto (y otras superficies sin Activity) reproduzcan, porque no hay reproductor vivo en el servicio. El roadmap lo lista como diferido por ser un cambio de ciclo de vida verificable solo en dispositivo.

## Objetivo

Mover la propiedad del `ExoPlayer` y de la cola de reproducción al `MusicService`, de forma que:

- `MusicService` cree y libere el reproductor.
- `PlayerViewModel` (y la UI) se comuniquen con el servicio en lugar de poseer el reproductor.
- `MediaSession` y las notificaciones se construyan sobre el mismo reproductor del servicio.
- Android Auto (`PlyrMediaLibraryService`) pueda delegar la reproducción en el servicio.

## Pasos propuestos

1. **Servicio expone un `AudioPlayer` propio.** `MusicService` crea `ExoAudioPlayer` en `onCreate` y lo expone vía `MusicBinder` (`audioPlayer`). La Activity ya no llama a `buildPlayer()`; usa el del servicio.
2. **`PlayerViewModel` depende del servicio, no del reproductor.** Sustituir `_player` por una referencia al `MusicBinder` (o a una interfaz `PlaybackController`). La cola, la ventana y los skips siguen en `PlayerViewModel`, pero operan sobre `service.audioPlayer`.
3. **Ciclo de vida.** `MainActivity.startAndBindMusicService()` ya arranca el servicio en foreground; asegurar que el reproductor se crea antes de enlazar la UI (o exponer un `StateFlow<AudioPlayer?>`).
4. **Notificaciones.** `setupMediaSession` pasa a ser interno del servicio: se crea la `MediaSession` en `onCreate` y se actualiza cuando cambia la pista.
5. **Android Auto.** `PlyrMediaLibraryService` delega `play`/`pause`/`skip` al `MusicService` compartido en lugar de tener su propio `ExoPlayer`.
6. **Limpieza.** `PlayerViewModel.pausePlayer()` y `release` dejan de tocar el reproductor directamente; el servicio gestiona su ciclo.

## Riesgos

- Condición de carrera entre el arranque del servicio (foreground) y el bind de la Activity.
- Pérdida de notificaciones si el servicio muere mientras la Activity sigue viva.
- Tests de `PlayerViewModel` asumen un reproductor inyectable: habrá que introducir un doble (`FakeAudioPlayer`) o mover la lógica pura a `:core`.

## Verificación

- `./run.sh verify full` verde.
- Reproducción manual en dispositivo: play/pause/skip/notificación/Android Auto.
- No ampliar `detekt-baseline.xml`.

## Alcance de este PR

Solo documentación del plan. La implementación se hará en PRs posteriores, empezando por el paso 1.
EOF
echo written