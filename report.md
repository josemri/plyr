# plyr — reporte estático (sin compilación)

Fecha: 2026-11-24. Análisis estático del estado actual de `main` (v1.6.3).

## Métricas

- Archivos `.kt` principales más grandes:
  - `app/.../ui/PlaylistScreen.kt` — 62 KB (sigue siendo el más grande pese al split en `PlaylistViewModel`).
  - `app/.../viewmodel/PlayerViewModel.kt` — 41 KB.
  - `app/.../ui/components/SongListItem.kt` — 32 KB.
  - `desktop/.../ui/AudioListScreen.kt` — 32 KB.
  - `desktop/.../player/PlayerController.kt` — 30 KB.
- Sin `GlobalScope`; `Thread.sleep` solo en tests.
- 46 usos de `!!`, concentrados en tests.
- 77 llamadas a `Log.*` en main, sin URLs/cookies/tokens aparentes (CoverCache registra URLs de portadas, no sensibles).

## Hallazgos

1. **`PlaylistScreen.kt` sigue siendo monolítico** (62 KB) aunque el roadmap marca el split como hecho. El ViewModel existe, pero la pantalla arrastra mucha lógica de UI/estado.
2. **`PlyrMediaLibraryService` usa `runBlocking` en el hilo del binder** (`onGetChildren`, `onSetMediaItems`). Puede bloquear Android Auto si la BD o la red (YouTube) tardan.
3. **Android Auto no validado en dispositivo/coche.** El código compila y pasa CI, pero no se ha probado en un head unit real.
4. **Dos versiones de Coil** (`coil-compose` 2.7.0 y `coil3` 3.6.3) conviven; candidato a consolidar.
5. **`json` 20260814** y otras dependencias convendría revisar periódicamente.
6. **Gradle emite deprecaciones de plugins** (Kover `setVisible`, Compose Hot Reload `HotReloadUsageType`): vienen de los plugins, no del código del repo; se resolverán al actualizar esos plugins.
7. **`targetSdk` sigue en 36** (pendiente subir a 37 con pruebas en dispositivo).
8. **Tests instrumentados** (`QrCode`, `DataImporter`) no se ejecutan en CI (requieren dispositivo).
9. **`Thread.sleep` en tests** (6 ocurrencias) introduce fragilidad por timing; preferir `advanceUntilIdle`/await en coroutines.
10. **Cobertura**: no hay regla de mínimo; Kover está aplicado solo en `:app`.

## Recomendaciones priorizadas

1. Refactorizar `PlaylistScreen.kt` en subcomponentes/composables más pequeños.
2. Sustituir `runBlocking` en `PlyrMediaLibraryService` por coroutines con scope y `Futures`.
3. Validar Android Auto en dispositivo/emulador automotriz.
4. Consolidar Coil a una sola versión.
5. Ejecutar `./run.sh test device` cuando haya móvil disponible.
6. Revisar dependencias en `libs.versions.toml` periódicamente.

## Lo que está bien

- `verify full` verde; detekt/lint sin findings propios.
- Sin `GlobalScope`; `Thread.sleep` solo en tests.
- `!!` concentrado en tests.
- Baselines de detekt no ampliados.
- Paridad Android/escritorio mantenida en los cambios recientes.
EOF
echo written