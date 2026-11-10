# Registro de cambios del bundle

Formato: fecha ISO descendente, una entrada por cambio relevante. No borres
entradas antiguas: el historial sirve para saber si un concepto sigue vigente.

## 2026-11-09

* **Migrado**: build a AGP built-in Kotlin y DSL nuevo (`android.builtInKotlin=true`,
  `android.newDsl=true`); eliminado el plugin `org.jetbrains.kotlin.android` de `:app`
  y del catálogo. Validado con `./gradlew help` y compilación.

## 2026-11-09

* **Migrado**: build a AGP built-in Kotlin y DSL nuevo (`android.builtInKotlin=true`,
  `android.newDsl=true`); eliminado el plugin `org.jetbrains.kotlin.android` de `:app`
  y del catálogo. Validado con `./gradlew help` y compilación.

## 2026-11-05

* **Creación**: bundle OKF v0.2 inicial con restricciones, sistemas, dominio,
  decisiones, playbooks y glosario.
* **Creación**: conceptos de paridad Android/escritorio y de contratos
  persistidos (`sync-format`, `library-and-likes`, `queue-and-window`).
* **Movido**: `web-port.md` a `proposals/web-port.md`, marcado como `draft`.
* **Añadido**: configuración de OpenCode (`opencode.jsonc`), comandos `/context`,
  `/verify` y `/review`, y subagentes de solo lectura `verifier` y `reviewer`.
* **CI**: job `guardrails` (guards + OKF + mapa) y `:core:testAndroidHostTest`
  en el job de tests; plantillas de pull request e issue.
* **Corregido**: el validador OKF acepta las fechas que PyYAML resuelve a
  `date`/`datetime`, y las notas al pie de los source sets de `:core`.
* **Añadido**: restricción de ramas y pushes. `main` es producción y solo la
  toca el usuario; cada implementación o fix va en una rama propia, con commits
  locales y sin push del agente. No hay rama `develop`.
* **Implementado**: `.github/workflows/release-on-merge.yml` dispara
  `auto-release.yml` al mergear un PR en `main`. El salto
  `patch`/`minor`/`major` sale de las etiquetas `release:*` y se resuelve en
  `scripts/ai/release_bump.py`, con tests en `scripts/ai/test_release_bump.py`.
  Sin etiqueta se usa `patch`; `release:skip` no publica.

## 2026-11-05

* **Eliminado**: todo el código NFC (NfcReader, NfcTagEvent, NfcPulse, NfcScanEvent
  y el flujo de escritura NFC en QRDialog). Se conserva la navegación de listas
  mediante `ScanEvent` (movido a `app/src/main/java/com/plyr/utils/ScanEvent.kt`).
* **Actualizado**: `okf/domain/qr-nfc.md` y `okf/systems/android-app.md` para
  reflejar la eliminación de NFC.
