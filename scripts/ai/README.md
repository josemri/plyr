# scripts/ai

Herramientas de verificación y contexto para trabajar con agentes en `plyr`.
Todas se pueden ejecutar directamente o a través de `./run.sh`.

| Script | Qué hace |
|---|---|
| `doctor.sh` | Diagnóstico de JDK, SDK, adb, libvlc, Python, git y red. No instala nada. |
| `verify.sh` | `fast`, `unit`, `analysis`, `full`, `changed`. Réplica de la matriz de CI. |
| `guards.sh` | Imports prohibidos, prints, baselines, módulos fantasma, versión, tests ausentes. |
| `okf_check.py` | Valida el bundle OKF: frontmatter, `type`, enlaces, `resource:`, tests, vigencia, `verified`. |
| `okf_map.py` | Genera `okf/maps/repo-map.md`; `--check` falla si está desfasado e imprime el diff. Ignora `versionCode`/`versionName`, que gestiona la release. |
| `context.py` | Imprime concepto + ficheros + tests + enlaces + backlinks. |

## Ejemplos

```bash
./run.sh doctor
./run.sh okf-check
./run.sh map
./run.sh guards
./run.sh verify fast
./run.sh verify full
./run.sh context domain/sync-format
```

## Reglas

- `guards.sh` y `okf_check.py` son baratos: se pueden ejecutar antes de cada
  commit.
- `verify.sh` usa el entorno de `run.sh` si se invoca desde él; si se ejecuta
  suelto, usa `JAVA_HOME`/`ANDROID_HOME` del shell.
- No dupliques la lógica de estos scripts en workflows: CI los llama.
- Si añades una regla, añade también su test o su comprobación de que detecta
  una violación real.
