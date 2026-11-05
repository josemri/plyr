---
description: Revisa los cambios pendientes contra el contrato del repo
agent: plan
---

Revisa los cambios pendientes de plyr sin editar ficheros.

1. Lee `AGENTS.md` y el concepto OKF relevante (`okf/index.md`).
2. Obtén el diff con `git diff` y `git status --short`.
3. Busca, por orden de gravedad:
   - imports prohibidos (`commonMain`, `jvmSharedMain`, fronteras de módulos);
   - cambios de comportamiento solo en Android o solo en escritorio;
   - contratos persistidos tocados sin plan (`FORMAT_VERSION`, Room, SQLDelight,
     `TrackIdentity`, tombstones);
   - tests ausentes o que se omiten sin justificar;
   - secretos, baselines ampliados o ficheros generados.
4. Reporta hallazgos con fichero y línea, y separa "bloqueante" de "sugerencia".
5. Termina con los comandos de verificación que debería ejecutar el autor.
