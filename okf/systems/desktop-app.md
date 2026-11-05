---
type: System
title: "App de escritorio (:desktop)"
description: "Cliente JVM 21 con Compose Desktop, VLCJ sobre libvlc nativo, SQLDelight y MPRIS. Implementa su propia UI y persistencia, en paralelo a Android."
resource: repo://desktop/src/main/kotlin/com/plyr/desktop
tags: [desktop, compose, vlc, sqldelight, mpris, jvm]
module: desktop
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [desktop/src/test]
sources:
  - id: desktop-build
    resource: repo://desktop/build.gradle.kts
    title: desktop/build.gradle.kts
  - id: vlc-env
    resource: repo://desktop/src/main/kotlin/com/plyr/desktop/player/VlcEnvironment.kt
    title: VlcEnvironment.kt
---

# Responsabilidades

UI Compose Desktop, reproducción con VLCJ, persistencia SQLDelight, integración
con el escritorio (MPRIS/DBus, bandeja, atajos), descargas y sync por carpeta.

# Stack

- JVM 21 (`jvmToolchain(21)`), Compose Multiplatform 1.12.
- **VLCJ 4.12 + `libvlc` nativo**.[^vlc-env] VLCJ es solo JNA: sin la librería del
  sistema no hay audio. `PLYR_VLC_DIR` permite forzar el directorio.
- SQLDelight 2.4, esquema en `desktop/src/main/sqldelight/`.
- `java.util.prefs.Preferences` en el nodo `com/plyr/desktop`.
- `jpackage` para `.deb`, `.rpm`, `.AppImage`, `.msi`, `.dmg`.

# Mapa de paquetes

| Paquete | Contenido |
|---|---|
| `player/` | `PlayerController`, `VlcAudioPlayer`, `VlcPlaybackStateMachine`, resolución YouTube |
| `database/` | Repositorios SQLDelight, tombstones |
| `playlists/` | Controladores y vistas de listas |
| `search/` | Búsqueda y detalle de listas de YouTube |
| `sync/` | Export/import ZIP y carpeta de backup |
| `qr/` | QR, share y recomendaciones |
| `spotify/` | Import desde Spotify |
| `media/` | MPRIS, bandeja |
| `config/` | Ajustes y preferencias |
| `platform/` | Rutas, navegador, diálogos de fichero |
| `ui/` | Pantallas y componentes |

# Reproducción

`PlayerController` es el equivalente de `PlayerViewModel`: orquesta cola, ventana,
resolución y errores, pero **la lógica pura vive en `:core`**. No reimplementes
`QueueEngine` ni `QueueIndex` aquí.

# Persistencia

SQLDelight replica el esquema de Room v8, pero **sin migraciones**: el escritorio
parte de base nueva. Si cambias el esquema, cambia también `:app` y el formato de
sync. Ver [esquema](../playbooks/schema-change.md).

# Reglas

- No importar Android.
- No bloquear el hilo de UI: red, disco y VLC en coroutines/IO.
- Aislar `libvlc` en tests: usa `FakeAudioPlayer`; los smoke tests se omiten.
- No depender de `Preferences` reales en tests.
- Cualquier cambio de comportamiento debe reflejarse también en Android.

# Tests

`./run.sh test desktop` o `./run.sh verify unit`. Referencia:
`PlayerControllerTest` (cola, ventana, repeat, errores) con `FakeAudioPlayer` y
`FakeResolver`.

[^vlc-env]: VlcEnvironment.kt
