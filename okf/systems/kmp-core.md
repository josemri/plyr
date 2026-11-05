---
type: System
title: "Núcleo compartido (:core)"
description: "Módulo Kotlin Multiplatform con la lógica que comparten Android y escritorio. Jerarquía manual de source sets; commonMain es estricto."
resource: repo://core/src
tags: [kmp, core, source-sets, logica-pura]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/commonTest, core/src/jvmTest]
sources:
  - id: core-build
    resource: repo://core/build.gradle.kts
    title: core/build.gradle.kts (source sets)
  - id: gradle-props
    resource: repo://gradle.properties
    title: gradle.properties (jerarquía manual)
---

# Para qué existe

Concentrar la lógica que Android y escritorio deben comportar igual: cola,
ventana, modelos, parsers, identidad de pistas, sync, QR, i18n y red.

# Source sets

```text
commonMain        kotlin*, kotlinx*, com.plyr*      (estricto)
  ├── jvmSharedMain   java.* + NewPipe + OkHttp + ZXing
  │     ├── jvmMain   org.json (escritorio)
  │     └── androidMain
  └── commonTest      JUnit 4, sin JVM
        ├── jvmTest
        └── androidHostTest
```

`gradle.properties` fija `kotlin.mpp.applyDefaultHierarchyTemplate=false` y las
aristas se declaran a mano.[^core-build] [^gradle-props] **No simplificar** sin
comprobar los cuatro targets y `./gradlew help`.

# Qué hay dentro

| Área | Ficheros clave |
|---|---|
| Cola | `viewmodel/QueueEngine.kt`, `QueueIndex.kt`, `WindowState.kt`, `PendingSkips.kt`, `IdlePlayback.kt` |
| Reproductor | `player/AudioPlayer.kt`, `AudioTrack.kt` |
| Modelo | `model/AudioItem.kt`, `network/AppModels.kt`, `database/PlaylistRow.kt`, `PlaylistSource.kt` |
| Favoritos | `utils/TrackIdentity.kt`, `LikedSongsMerge.kt`, `Tombstones.kt`, `database/PlaylistFilters.kt` |
| Sync | `utils/ExportManifest.kt`, `ImportManifest.kt`, `ImportArchive.kt`, `ExportDigest.kt` |
| YouTube | `network/YouTubeManager.kt`, `SimpleDownloader.kt`, `utils/NewPipeHolder.kt` |
| QR/share | `utils/QrCodec.kt`, `ui/components/ShareUrlPolicy.kt`, `PlaylistShare.kt` |
| Texto | `i18n/TranslationCatalog.kt`, `utils/UrlParser.kt`, `RelativeTime.kt` |

# Reglas

- Lógica pura primero: si no necesita JVM, va a `commonMain`.
- Sin `Context`, `Uri`, `Bitmap`, `Compose` ni `android.*` en `:core`.
- Los contratos de `ExportManifest`, `ImportManifest`, `TrackIdentity` y
  `QrCodec` son API entre plataformas: cambios con test de ida y vuelta.
- Si un test de `:app` no usa Android, muévelo a `:core`.

# Tests

- `commonTest`: preferido, sin dependencias de JVM.
- `jvmTest`: `java.*`, red (`assumeTrue`), `org.json`.
- `androidHostTest`: garantiza que `commonMain` compila para Android.

[^core-build]: core/build.gradle.kts
[^gradle-props]: gradle.properties
