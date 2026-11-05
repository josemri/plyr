# plyr — instrucciones para agentes

> **Este repositorio NO es un proyecto Godot.** Si tu configuración global
> menciona Godot, GDScript, escenas o `godot --headless`, **no aplica aquí**.
> plyr es Kotlin Multiplatform + Android + Compose Desktop. Ignora cualquier
> instrucción de Godot que llegue desde `~/.config/opencode/AGENTS.md`.

Este fichero es el **contrato**. El conocimiento detallado vive en el bundle
OKF [`okf/index.md`](okf/index.md). No dupliques aquí lo que ya está allí.

## Nombre corto: «el kit»

Cuando el usuario diga **«el kit»** o **«plyr-kit»**, se refiere al gobierno de
agentes de este repo: `AGENTS.md`, el bundle `okf/`, `scripts/ai/` y
`.opencode/`. No incluye el código de producto.

## Política de ramas y pushes

`main` es **producción** y la única rama de integración. No hay `develop`,
`staging` ni ramas de entorno: cada tarea va en su rama y se mergea directamente
en `main`.

- **Toda implementación nueva** sale de `main` en una rama nueva.
- **Todo fix** va en una rama propia; nunca se corrige directamente en `main`.
- En la rama de trabajo el agente **puede hacer commits**.
- En la rama de trabajo el agente **nunca hace push**, ni crea el PR, ni hace
  merge. El push y el PR son del usuario.
- En `main` el agente **nunca** hace `git stash`, `git commit`, `git merge` ni
  `git push`. De `main` se ocupa el usuario.
- Cambiar a `main` para leer o comprobar estado sí está permitido, con el árbol
  limpio y sin modificar nada.

Nombres: `feat/<slug>`, `fix/<slug>`, `docs/<slug>`, `ci/<slug>`,
`chore/<slug>`, `refactor/<slug>`. Un PR = una rama = un cambio coherente.
Detalle completo en [`okf/constraints/branch-policy.md`](okf/constraints/branch-policy.md).

## Qué es el proyecto

Reproductor de música minimalista: streaming de YouTube (NewPipe Extractor),
listas locales, descargas offline, QR y sincronización por ZIP entre Android
y escritorio. GPL-3.0. Sin telemetría.

## Módulos reales

| Módulo | Tipo | Código | Tests |
|---|---|---|---|
| `:app` | Android (Compose, Room, Media3, SAF, CameraX) | ~16.500 LOC en `app/src/main/java` | 13 unitarios + 4 instrumentados |
| `:core` | KMP Android + JVM | ~5.000 LOC en `core/src/commonMain` y `jvmSharedMain` | 24 `commonTest` + 14 `jvmTest` |
| `:desktop` | JVM 21 puro (Compose Desktop, VLCJ, SQLDelight, MPRIS) | ~12.400 LOC en `desktop/src/main/kotlin` | 51 clases de test |

**No existen** `:shared`, `:web` ni `:server`. Si los ves mencionados en
`okf/proposals/web-port.md`, es una **propuesta futura**, no arquitectura actual.

## Comandos canónicos

```bash
./run.sh doctor          # comprueba JDK, SDK, adb, libvlc, python, git
./run.sh okf-check       # valida el bundle okf/ y el mapa generado
./run.sh context <id>    # concepto + ficheros + tests + enlaces
./run.sh guards          # reglas estáticas del repo
./run.sh verify fast     # configuración + compilación de módulos afectados
./run.sh verify unit     # todos los tests JVM/host
./run.sh verify analysis # detekt + lint
./run.sh verify full     # unit + analysis + APK debug + distributable
./run.sh test            # tests Android host (atajo existente)
./run.sh test desktop    # tests escritorio (atajo existente)
./run.sh check           # detekt + lint + cobertura (atajo existente)
```

El entorno (JDK 21, SDK, caches) se monta en `/tmp/plyr-android`. No lo crees
dentro del repo. `./run.sh clean` lo borra.

## Reglas de oro

1. **`commonMain` es multiplataforma estricto.** Solo `kotlin*`, `kotlinx*` y
   `com.plyr*`. Nunca `java.*`, `javax.*`, `android.*`, `androidx.*`, `org.json`,
   `okhttp3` ni `org.schabi.*`.
2. **`jvmSharedMain` no puede importar Android.** Es código JVM compartido por
   escritorio y Android/ART.
3. **`desktop` no importa Android; `app` no importa `com.plyr.desktop`.**
4. **Si cambia el comportamiento, revisa las dos plataformas.** Android y
   escritorio tienen implementaciones paralelas de UI, persistencia y share. Un
   cambio solo en una es un bug de paridad.
5. **No amplíes `detekt-baseline.xml`.** Si detekt falla, arregla el código; si
   de verdad hace falta, pide permiso y justifícalo en el PR.
6. **No cambies contratos persistidos sin plan**: `ExportManifest.FORMAT_VERSION`
   (hoy `1`), Room `version` (hoy `8`), esquema SQLDelight, claves de tombstones
   y `TrackIdentity`.
7. **No toques `build/`, `.gradle/`, `.kotlin/` ni código generado.**
8. **No cambies `versionCode`/`versionName`** salvo tarea de release explícita;
   la única fuente de verdad es `app/build.gradle.kts`.
9. **`android.newDsl=false` y `android.builtInKotlin=false` son deliberados.**
   No los "limpies" sin una rama aparte y `./gradlew help` verde.
10. **Nada de secretos en el repo.** `local.properties` y `keystore.properties`
    están ignorados; no los imprimas ni los commitees.
11. **No inventes verificación.** En `okf/`, `generated.by` dice quién escribió
    y `verified` solo lo firma un humano o una herramienta con evidencia real.

## Quiero cambiar X → empiezo por

| Cambio | Android | Escritorio | Compartido |
|---|---|---|---|
| Cola, ventana, repeat | `viewmodel/PlayerViewModel.kt` | `player/PlayerController.kt` | `core/.../viewmodel/` |
| Reproductor de audio | `player/ExoAudioPlayer.kt`, `service/MusicService.kt` | `player/VlcAudioPlayer.kt` | `core/.../player/AudioPlayer.kt` |
| Listas / tracks | `database/*`, `ui/PlaylistScreen.kt` | `database/*`, `playlists/*` | `core/.../database/` |
| Import/export ZIP | `utils/DataExporter.kt`, `DataImporter.kt` | `sync/DataExporter.kt`, `DataImporter.kt` | `core/.../utils/ExportManifest.kt`, `ImportManifest.kt` |
| YouTube | `service/YouTubeSearchManager.kt` | `search/*`, `player/YouTubeTrackSourceResolver.kt` | `core/.../network/YouTubeManager.kt` |
| QR / share | `ui/components/QRDialog.kt` | `qr/*` | `core/.../utils/QrCodec.kt`, `ui/components/ShareUrlPolicy.kt` |
| Ajustes / config | `utils/Config*.kt`, `ui/ConfigScreen.kt` | `config/*`, `ui/screens/ConfigScreen.kt` | — |
| Sincronización | `utils/DataSync.kt` | `sync/DataSync.kt` | `core/.../utils/ExportDigest.kt` |
| i18n | `utils/Translations.kt` | `i18n/Translations.kt` | `core/.../i18n/TranslationCatalog.kt` |

## Tests

- JUnit 4. Nombres descriptivos en `snake_case` con backticks cuando aporta.
- Lógica pura → `core/src/commonTest` (sin JVM) o `jvmTest` (si usa `java.*`).
- Lógica de plataforma → `app/src/test` / `desktop/src/test` con dobles, sin red,
  sin dispositivo y sin VLC real.
- Tests que dependen de red usan `assumeTrue` y **se omiten**, no fallan. Un test
  omitido **no cuenta como pasado**: dilo en el PR.
- Tests de dispositivo (`app/src/androidTest`) solo para cámara y SAF reales.
- Cada bug corregido debería dejar un test que falle antes del arreglo.

## Definition of Done

- [ ] El trabajo está en una rama propia (nunca en `main`), con commits locales
      y **sin push**.
- [ ] El cambio está en **todas** las plataformas afectadas.
- [ ] `./run.sh verify fast` verde (o `full` si tocaste contratos).
- [ ] Tests nuevos o actualizados; los omitidos, justificados.
- [ ] `./run.sh guards` y `./run.sh okf-check` verdes.
- [ ] Si cambió un contrato de dominio: concepto OKF + `okf/log.md` actualizados.
- [ ] El PR lleva una etiqueta `release:*` (o `release:skip`) para el auto-release.
- [ ] Sin baselines ampliados, sin ficheros generados, sin secretos.
- [ ] `web-port.md` no se usa como fuente de arquitectura actual.

## Protocolo de lectura para una tarea

1. Este fichero.
2. `okf/index.md`.
3. Los 2–4 conceptos relevantes (`okf/systems/*`, `okf/domain/*`).
4. Los `resource:` reales y los tests declarados en el concepto.
5. `./run.sh context <id>` si necesitas el paquete completo de una vez.

Si el concepto y el código se contradicen, **manda el código** y actualiza el
concepto en el mismo cambio.
