---
type: Map
title: Mapa del repositorio
description: Estadísticas generadas desde el árbol real (módulos, source sets, LOC, tests, versiones). No editar a mano.
resource: repo://.
tags: [mapa, generado, estadisticas]
module: repo
generated: { by: tool:okf-map/1.0, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-04
---

# Cómo se genera

```bash
./run.sh map           # regenera este fichero
./run.sh okf-check     # falla si está desfasado
```

Fuente: `scripts/ai/okf_map.py`. Si el mapa y el código discrepan, el
código manda y el mapa se regenera.

# Módulos y código

| Área | Ficheros `.kt` | Líneas |
|---|---|---|
| `app/src/main` | 76 | 16521 |
| `app/src/test` | 13 | 1571 |
| `core/src/commonMain` | 32 | 2553 |
| `core/src/jvmSharedMain` | 16 | 2497 |
| `core/src/commonTest` | 24 | 2704 |
| `core/src/jvmTest` | 14 | 2602 |
| `desktop/src/main` | 73 | 12406 |
| `desktop/src/test` | 51 | 7750 |

# Tests

| Área | Ruta | Ficheros | `@Test` |
|---|---|---|---|
| Android host | `app/src/test` | 13 | 121 |
| Android instrumentado | `app/src/androidTest` | 4 | 10 |
| core commonTest | `core/src/commonTest` | 24 | 256 |
| core jvmTest | `core/src/jvmTest` | 14 | 178 |
| escritorio | `desktop/src/test` | 51 | 337 |

# Ficheros más grandes (solo `main`)

| Fichero | Líneas |
|---|---|
| `app/src/main/java/com/plyr/ui/PlaylistScreen.kt` | 1813 |
| `app/src/main/java/com/plyr/viewmodel/PlayerViewModel.kt` | 1018 |
| `app/src/main/java/com/plyr/ui/components/SongListItem.kt` | 986 |
| `desktop/src/main/kotlin/com/plyr/desktop/player/PlayerController.kt` | 891 |
| `app/src/main/java/com/plyr/ui/FloatingMusicControls.kt` | 660 |
| `desktop/src/main/kotlin/com/plyr/desktop/ui/AudioListScreen.kt` | 641 |
| `app/src/main/java/com/plyr/ui/SearchScreen.kt` | 595 |
| `app/src/main/java/com/plyr/ui/ConfigScreen.kt` | 575 |
| `desktop/src/main/kotlin/com/plyr/desktop/ui/screens/SearchScreen.kt` | 540 |
| `app/src/main/java/com/plyr/ui/components/QRDialog.kt` | 525 |
| `app/src/main/java/com/plyr/service/YouTubeSearchManager.kt` | 422 |
| `desktop/src/main/kotlin/com/plyr/desktop/playlists/PlaylistDetailView.kt` | 421 |
| `desktop/src/main/kotlin/com/plyr/desktop/ui/FloatingMusicControls.kt` | 413 |
| `app/src/main/java/com/plyr/utils/DataSync.kt` | 399 |
| `desktop/src/main/kotlin/com/plyr/desktop/database/PlaylistRepository.kt` | 391 |

# Versiones y contratos

| Clave | Valor |
|---|---|
| Gradle | `9.8.2` |
| AGP | `9.5.0` |
| Kotlin | `2.4.21` |
| Compose Multiplatform | `1.12.2` |
| Java toolchain | `21` |
| compileSdk | `37` |
| targetSdk | `36` |
| minSdk | `24` |
| versionCode | `13` |
| versionName | `1.4.2` |
| Room version | `8` |
| FORMAT_VERSION | `1` |

# Conceptos del bundle por tipo

| Tipo | Conceptos |
|---|---|
| Constraint | 2 |
| Data Format | 1 |
| Decision | 5 |
| Domain Concept | 6 |
| Map | 1 |
| Playbook | 4 |
| Proposal | 1 |
| Reference | 1 |
| System | 5 |

# Comandos de verificación

| Necesitas | Comando |
|---|---|
| Todo lo barato | `./run.sh verify fast` |
| Tests JVM/host | `./run.sh verify unit` |
| detekt + lint | `./run.sh verify analysis` |
| Replicar CI | `./run.sh verify full` |
| Reglas del repo | `./run.sh guards` |
| Bundle OKF | `./run.sh okf-check` |
