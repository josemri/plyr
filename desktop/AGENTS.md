# :desktop — app de escritorio

Lee antes `../AGENTS.md` y `../okf/systems/desktop-app.md`.

## Stack

- JVM 21 (`jvmToolchain(21)`), Compose Multiplatform Desktop 1.12.
- **VLCJ 4.12 + `libvlc` nativo**. VLCJ es solo el binding JNA; sin `libvlc`
  instalado no hay audio. `PLYR_VLC_DIR` fuerza el directorio en tests.
- SQLDelight 2.4 (no Room). Esquema en `desktop/src/main/sqldelight/`.
- Preferencias con `java.util.prefs.Preferences` (`DesktopSettings`), nodo
  `com/plyr/desktop`. Los tests deben aislar ese nodo.
- MPRIS/DBus (`MprisMediaIntegration`), bandeja (`DesktopTray`), atajos de teclado.
- Empaquetado con `jpackage` (`desktop-package`, `desktop-dist`).

## Estructura

```text
desktop/src/main/kotlin/com/plyr/desktop/
  Main.kt
  player/       PlayerController (orquesta), VlcAudioPlayer, resolución YouTube
  database/     SQLDelight: repositorios, tombstones
  playlists/    controladores y vistas de listas
  search/       búsqueda YouTube
  sync/         export/import ZIP, backup en carpeta
  qr/           QR, share, recomendaciones
  spotify/      import desde Spotify
  media/        MPRIS, bandeja
  config/       ajustes y preferencias
  platform/     rutas, navegador, diálogos de fichero
  ui/           pantallas y componentes Compose
```

## Reglas específicas

- **`PlayerController` es el equivalente de `PlayerViewModel`.** La lógica pura
  de cola/ventana está en `:core`; no la reimplementes aquí.
- **No importes Android.** Si necesitas una API de plataforma, va en
  `platform/` con una interfaz y su implementación JVM.
- **SQLDelight no tiene migraciones**: el escritorio parte de base nueva. Si
  cambias el esquema, cambia también `:app` (Room) y el formato de sync.
- **Aisla VLC en tests**: usa `FakeAudioPlayer`. Los tests que abren `libvlc`
  llevan `assumeTrue` y se omiten si no está instalado.
- **No bloquees el hilo de UI**: red, VLC y disco van en coroutines/IO.
- **`PlayerControllerTest` es el test de referencia** para cambios de cola.

## Tests

```bash
./run.sh test desktop
./run.sh verify unit        # incluye :desktop:test
```

- JUnit 4, dobles propios (`FakeAudioPlayer`, `FakeResolver`). Sin red salvo
  `*NetworkIntegrationTest` / `*SmokeTest`, que se omiten sin conexión.
- No dependas de `Preferences` reales: usa un nodo temporal o inyecta settings.
- `VlcAudioPlayerSmokeTest` requiere `libvlc`; en CI puede omitirse.

## Errores que ya han pasado

- Compartir una lista de Spotify como si fuera de YouTube (paridad de
  `ShareUrlPolicy`).
- Duplicar lógica de cola en el controlador en vez de usar `QueueEngine`.
- Cambiar el esquema SQLDelight sin tocar Room/sync.
