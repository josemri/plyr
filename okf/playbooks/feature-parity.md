---
type: Playbook
title: "Cambio con paridad Android/escritorio"
description: "Pasos para cambiar comportamiento que existe en las dos apps: localizar la lógica compartida, aplicarla en ambas y probarla."
resource: repo://okf/domain/queue-and-window.md
tags: [paridad, android, desktop, flujo]
module: repo
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
---

# Cuándo usarlo

El cambio afecta a algo que el usuario ve en las dos plataformas: reproducción,
listas, sync, share, i18n, descargas o ajustes.

# Pasos

1. **Localiza la lógica pura.** Si está duplicada, muévela a `:core` primero y
   haz que ambas plataformas la usen. No arregles dos copias a mano.
2. **Escribe el test en `:core`.** Debe fallar antes del cambio.
3. **Aplica Android.** UI/estado en `PlayerViewModel`, `PlaylistViewModel` o el
   repositorio correspondiente.
4. **Aplica escritorio.** `PlayerController`, controladores o repositorios SQLDelight.
5. **Revisa los contratos persistidos.** Si tocas datos, sigue
   [schema-change](schema-change.md).
6. **Ejecuta:**

   ```bash
   ./run.sh verify fast
   ./run.sh verify unit
   ./run.sh guards
   ./run.sh okf-check
   ```

7. **Actualiza el concepto** si cambió el contrato o el flujo. Añade entrada en
   `okf/log.md` si es relevante.

# Checklist de paridad

- [ ] Misma lógica en `:core` (o justificación de por qué no).
- [ ] Android y escritorio llegan al mismo resultado.
- [ ] Errores y estados vacíos también coinciden.
- [ ] El share no comparte una URL distinta en cada plataforma.
- [ ] El sync lee lo que la otra plataforma escribe.
- [ ] Tests en `:core` + al menos un test de controlador por plataforma.

# Señales de alarma

- Cambiar solo `PlayerViewModel` y dar la tarea por hecha.
- Duplicar un parser en Android y escritorio.
- Cambiar un texto en una sola traducción.
- Tocar `ShareUrlPolicy` sin probar listas de Spotify y de YouTube.
