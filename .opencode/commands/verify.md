---
description: Ejecuta la verificación canónica de plyr
---

Ejecuta la verificación del repositorio plyr con la herramienta shell.

- Si `$ARGUMENTS` está vacío, usa `./run.sh verify fast`.
- Si contiene un modo (`fast`, `unit`, `analysis`, `full`, `changed`), ejecuta
  `./run.sh verify $ARGUMENTS`.
- No inventes tareas de Gradle ni declares verde un comando que no ejecutaste.
- Al terminar, informa de: comando exacto, código de salida, tests fallidos y
  tests **omitidos** (red, `libvlc`, dispositivo). Un omitido no cuenta como
  pasado.
- Si algo falla, propone la causa concreta antes de tocar código.
