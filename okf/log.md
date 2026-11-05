# Registro de cambios del bundle

Formato: fecha ISO descendente, una entrada por cambio relevante. No borres
entradas antiguas: el historial sirve para saber si un concepto sigue vigente.

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
