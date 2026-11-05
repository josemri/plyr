---
okf_version: "0.2"
---

# plyr · knowledge bundle

Conocimiento curado del proyecto **plyr**: reproductor de música Kotlin
Multiplatform (Android + escritorio). Este índice es el punto de entrada para
humanos y agentes. Abre solo los conceptos relevantes para tu tarea.

## Restricciones (leer siempre)

* [Contrato del agente](constraints/agent-contract.md) — qué puede y qué no puede hacer un agente en este repo.
* [Política de ramas y pushes](constraints/branch-policy.md) — `main` es producción; rama por tarea, commits locales y push solo del usuario.
* [Verificación](constraints/verification.md) — qué comando prueba qué y cuándo un cambio está terminado.

## Sistemas

* [App Android (:app)](systems/android-app.md) — Compose, Room, Media3, SAF, NFC, CameraX.
* [Núcleo compartido (:core)](systems/kmp-core.md) — source sets, jerarquía manual y qué va en cada uno.
* [App de escritorio (:desktop)](systems/desktop-app.md) — Compose Desktop, VLCJ, SQLDelight, MPRIS.
* [Entorno de build](systems/build-and-env.md) — `run.sh`, `/tmp/plyr-android`, JDK, SDK, VLC.
* [CI y releases](systems/ci-and-release.md) — workflows, matriz de tests, versionado y firma.

## Dominio

* [Cola y ventana de reproducción](domain/queue-and-window.md) — `QueueEngine`, `WindowState`, repeat, skips.
* [Listas y pistas](domain/playlists.md) — modelo, fuentes, filtros, reordenación y paridad.
* [Favoritos y tombstones](domain/library-and-likes.md) — identidad de pista, merge y borrados que no resucitan.
* [Formato de sincronización](domain/sync-format.md) — `plyr-sync.zip` v1, export/import y compatibilidad.
* [YouTube](domain/youtube.md) — NewPipe, extracción, caché de URLs y errores de reproducción.
* [QR, NFC y share](domain/qr-nfc.md) — `QrCodec`, `ShareUrlPolicy`, payloads y prioridad de URLs.
* [Importación de Spotify](domain/spotify-import.md) — parseo del embed y plan de búsqueda en YouTube.

## Decisiones

* [0001 · Límites de módulos](decisions/0001-module-boundaries.md)
* [0002 · Source sets de :core](decisions/0002-core-source-sets.md)
* [0003 · Formato de sync v1](decisions/0003-sync-format-v1.md)
* [0004 · Room en Android, SQLDelight en escritorio](decisions/0004-room-vs-sqldelight.md)
* [0005 · Port web aplazado](decisions/0005-web-port-deferred.md)

## Playbooks

* [Cambio con paridad Android/escritorio](playbooks/feature-parity.md)
* [Añadir o mover un test](playbooks/add-test.md)
* [Cambiar esquema o formato persistido](playbooks/schema-change.md)
* [Preparar una release](playbooks/release.md)

## Mapas y referencia

* [Mapa del repositorio](maps/repo-map.md) — generado desde el árbol real; no editar a mano.
* [Glosario](glossary.md) — términos, IDs `M*`/`B*` y nombres que se repiten.

## Propuestas (no implementadas)

* [Port a navegador](proposals/web-port.md) — plan futuro; **no** es arquitectura actual.
* [Auto-release al mergear en `main`](proposals/auto-release-on-merge.md) — disparo por etiqueta; hoy la release sigue siendo manual.
