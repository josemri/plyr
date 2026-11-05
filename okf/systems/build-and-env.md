---
type: System
title: "Entorno de build"
description: "run.sh monta JDK 21, Android SDK y caches en /tmp/plyr-android. Comandos, rutas y requisitos para compilar y probar sin ensuciar el repo."
resource: repo://run.sh
tags: [build, entorno, gradle, sdk, jdk, vlc]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [scripts/ai/doctor.sh]
sources:
  - id: run-sh
    resource: repo://run.sh
    title: run.sh
  - id: build-config
    resource: repo://app/build.gradle.kts
    title: app/build.gradle.kts
---

# Principio

`run.sh` es autosuficiente: no instala nada en `$HOME` y no deja artefactos de
entorno dentro del repo.[^run-sh] Todo vive en `/tmp/plyr-android` (override con
`PLYR_TMP_ROOT`):

| Ruta | Contenido |
|---|---|
| `android-sdk/` | platform 37.0, build-tools 37.0.0, platform-tools, cmdline-tools |
| `jdk/` | JDK 21 (Temurin) |
| `gradle-home/` | caches y daemons de Gradle |
| `gradle-project-cache/` | cache del proyecto (sustituye `<repo>/.gradle`) |
| `android-user/` | claves adb y `debug.keystore` |
| `xdg/` | estado del daemon de Kotlin |

`./run.sh clean` borra todo y deja el repo como recién clonado.

# Comandos

```bash
./run.sh setup                  # monta el entorno
./run.sh env                    # imprime exports para tu shell
./run.sh build [release|desktop]
./run.sh run [desktop]
./run.sh test [device|desktop]
./run.sh check                  # detekt + lint + cobertura
./run.sh desktop-package [deb|rpm|appimage|msi|dmg|all]
./run.sh desktop-dist
./run.sh doctor                 # diagnóstico (no instala)
./run.sh verify <modo>
./run.sh guards
./run.sh okf-check
./run.sh context <id>
```

# Versiones

- Gradle 9.8.2 (wrapper), AGP 9.5.0, Kotlin 2.4.21, Compose Multiplatform 1.12.2.
- JDK 21 obligatorio (toolchain de escritorio y AGP).
- Android: `compileSdk 37`, `targetSdk 36`, `minSdk 24`.[^build-config]

# Configuración opcional

`local.properties` **no es necesario**: `app/build.gradle.kts` tiene valores por
defecto funcionales (`configValue(...)`). Si existe, permite sobreescribir
`SUPABASE_URL`, `SUPABASE_ANON_KEY` y `YOUTUBE_RESTRICTED_MODE_COOKIE`. Nunca se
commitea. Plantilla en `local.properties.example`.

# Escritorio y VLC

Compilar escritorio no requiere `libvlc`, pero **reproducir audio sí**. Si falta:

```bash
sudo apt-get install libvlc5 vlc-plugin-base
# o
export PLYR_VLC_DIR=/ruta/a/libvlc
```

Sin `libvlc`, `VlcAudioPlayerSmokeTest` y los tests de reproducción real se
omiten con `assumeTrue`. `./run.sh doctor` lo detecta y avisa.

# Troubleshooting

- **“plugin is already on the classpath”**: no quites
  `android.builtInKotlin=false` ni `android.newDsl=false`.
- **SDK no encontrado**: `./run.sh setup` y usa `./run.sh env`.
- **Daemon de Kotlin colgado**: `./run.sh clean` y vuelve a montar.
- **Tests de Compose Desktop sin pantalla**: `DISPLAY= ./run.sh test desktop`.

[^run-sh]: run.sh
[^build-config]: app/build.gradle.kts
