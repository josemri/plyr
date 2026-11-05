---
type: System
title: "App Android (:app)"
description: "App Android de plyr. Compose, Room v8, Media3/ExoPlayer, SAF, CameraX y un servicio en primer plano. No importar nada de :desktop."
resource: repo://app/src/main/java/com/plyr
tags: [android, compose, room, media3, saf]
module: app
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [app/src/test, app/src/androidTest]
sources:
  - id: app-config
    resource: repo://app/build.gradle.kts
    title: app/build.gradle.kts
  - id: db
    resource: repo://app/src/main/java/com/plyr/database/PlaylistDatabase.kt
    title: PlaylistDatabase.kt (Room v8)
---

# Responsabilidades

Todo lo específico de Android: UI Compose, persistencia Room, reproducción con
Media3, servicio en primer plano, notificaciones, SAF y cámara para QR.

# Configuración

- `namespace = "com.plyr"`, `applicationId = "io.github.josemri.plyr"`.
- `compileSdk 37`, `targetSdk 36`, `minSdk 24`.[^app-config]
- `versionCode`/`versionName` viven **solo** aquí (hoy `13` / `1.4.2`).
- KSP para Room (kapt ya no se usa). R8 activo en release.

# Mapa de paquetes

| Paquete | Contenido |
|---|---|
| `database/` | Entidades Room, DAOs, repositorios, migraciones, tombstones |
| `player/` | `ExoAudioPlayer` (implementa `AudioPlayer` de `:core`) |
| `service/` | `MusicService`, `QueueAwarePlayer`, notificación, portada |
| `receivers/` | Botones multimedia |
| `ui/` | Pantallas Compose y componentes (Playlist, Search, Queue, Config, Home) |
| `viewmodel/` | `PlayerViewModel`, `PlaylistViewModel`, `SearchViewModel`… |
| `utils/` | SAF, backup, sync, config, i18n, descargas, update checker |

# Persistencia

Room `version = 8`, `exportSchema = false`,
`fallbackToDestructiveMigration(false)`.[^db] Migraciones publicadas:
`5→6`, `6→7`, `7→8`. No se borran ni se reordenan.

La fila especial `liked_songs` se crea al arrancar y **no se borra** aunque esté
vacía: `toggleLikeTrack`, la fusión de favoritos y el import la dan por hecha.

# Reproducción

La UI no crea reproductores. `PlaybackRequester` / `MusicService` mandan, y
`PlayerViewModel` aplica la lógica de `:core` (`QueueEngine`, `QueueIndex`,
`WindowState`, `IdlePlayback`). Ver [cola y ventana](../domain/queue-and-window.md).

# Reglas

- No importar `com.plyr.desktop.*`.
- No meter `android.*` en `:core`.
- No leer preferencias desde la UI: usar `Config*`.
- Los `Log.*` con URLs firmadas, cookies o rutas de usuario son un fallo.
- Los tests de cámara/SAF van a `androidTest`, no a `test`.

# Tests

`./run.sh verify unit` incluye `:app:testDebugUnitTest`. Los instrumentados se
lanzan con `./run.sh test device` y requieren móvil.

[^app-config]: app/build.gradle.kts
[^db]: PlaylistDatabase.kt
