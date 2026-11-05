# :app — app Android

Lee antes `../AGENTS.md` y `../okf/systems/android-app.md`.

## Stack

- `namespace = "com.plyr"`, `applicationId = "io.github.josemri.plyr"`.
- `compileSdk 37`, `targetSdk 36`, `minSdk 24`. **`targetSdk` no sube a 37 sin
  probar audio en segundo plano y NFC en dispositivo.**
- Kotlin 2.4 + Compose (BOM Android) + Room 2.8 + KSP (ya **no** kapt) +
  Media3/ExoPlayer 1.11 + CameraX + ZXing + NewPipe (vía `:core`).
- R8 activo en release. Los `Log.*` con datos sensibles son un fallo, no ruido.

## Estructura

```text
app/src/main/java/com/plyr/
  MainActivity.kt, PlyrApp.kt
  database/     Room: entidades, DAOs, repositorios, migraciones
  player/       ExoAudioPlayer (implementa AudioPlayer de :core)
  service/      MusicService (foreground), QueueAwarePlayer, notificación
  receivers/    MediaButtonReceiver
  ui/           pantallas Compose + componentes
  viewmodel/    PlayerViewModel, PlaylistViewModel, SearchViewModel...
  utils/        SAF, NFC, backup, sync, config, i18n, descargas
```

## Reglas específicas

- **Room `version = 8`**, `exportSchema = false`, `fallbackToDestructiveMigration(false)`.
  Toda migración nueva: `MIGRATION_N_N+1` + subir versión + test de mapeo.
  Nunca borres una migración publicada.
- **Una sola fuente de verdad por dato.** No leas preferencias directamente en
  la UI; pasa por `Config*` / repositorio.
- **El servicio manda en reproducción.** La UI llama a `PlaybackRequester` /
  `MusicService`; no instancies `ExoPlayer` en una pantalla.
- **`PlayerViewModel` no debe crecer.** La lógica pura va a `:core/viewmodel`
  (`QueueEngine`, `QueueIndex`, `WindowState`, `IdlePlayback`, `PendingSkips`).
- **SAF**: no guardes `Uri` persistentes sin `takePersistableUriPermission`; los
  tests de SAF van a `androidTest`.
- **NFC y cámara** solo se prueban en dispositivo. No metas esas dependencias en
  `:core`.
- **Traducciones**: las claves viven en `core/.../TranslationCatalog.kt` y en
  `app/.../Translations.kt`. `TranslationKeysUsageTest` debe seguir verde.

## Tests

```bash
./run.sh verify unit          # incluye :app:testDebugUnitTest
./run.sh test device          # instrumentados (requiere móvil)
```

- Host: `app/src/test` (JUnit 4, sin Android real).
- Dispositivo: `app/src/androidTest` solo para QR con cámara, NFC y SAF reales.
- No añadas Robolectric: el repo no lo usa.

## Errores que ya han pasado y no deben repetirse

- Importar `java.*` desde código que luego se comparte (`:core`).
- Cambiar solo Android y olvidar escritorio (paridad de share, sync, i18n).
- Añadir un `Log.d` con URL firmada, cookie o ruta de usuario.
- Ampliar el baseline de detekt en vez de borrar código muerto.
