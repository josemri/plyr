## Qué cambia

<!-- Resume el cambio en 1–3 frases. -->

## Por qué

<!-- Bug, decisión de producto o deuda. Si corrige un bug, referencia el ID B*. -->

## Plataformas afectadas

- [ ] Android (`:app`)
- [ ] Escritorio (`:desktop`)
- [ ] Compartido (`:core`)
- [ ] Solo documentación / tooling

Si el cambio de comportamiento solo toca una plataforma, explica por qué no
hace falta la otra.

## Contratos persistidos

- [ ] No se tocan.
- [ ] Room (migración `N→N+1`, versión subida).
- [ ] SQLDelight.
- [ ] `ExportManifest.FORMAT_VERSION` / `ImportManifest`.
- [ ] `TrackIdentity` / tombstones.
- [ ] Concepto OKF y `okf/log.md` actualizados.

## Release

Añade **una** etiqueta al PR. Si no hay ninguna, el release automático usará
`patch`. Si hay varias, gana la mayor (`major` > `minor` > `patch`).

- [ ] `release:patch` — fix, docs, tests o refactor interno; sin cambio de contrato.
- [ ] `release:minor` — funcionalidad nueva compatible.
- [ ] `release:major` — cambio rompedor o de contrato persistido.
- [ ] `release:skip` — no publicar (solo docs/chore).

## Verificación

Comandos ejecutados (marca los que pasan de verdad):

- [ ] `./run.sh guards`
- [ ] `./run.sh okf-check`
- [ ] `./run.sh verify fast`
- [ ] `./run.sh verify unit`
- [ ] `./run.sh verify analysis`
- [ ] `./run.sh verify full`
- [ ] `./run.sh test device` (requiere móvil)

Tests **omitidos** y por qué (red, `libvlc`, dispositivo). Un omitido no cuenta
como pasado:

<!-- ... -->

## Checklist

- [ ] Sin secretos (`local.properties`, `keystore.properties`, claves).
- [ ] Sin ampliar `detekt-baseline.xml`.
- [ ] Sin tocar `build/`, `.gradle/`, `.kotlin/` ni código generado.
- [ ] `versionCode`/`versionName` sin cambios (salvo release explícita).
- [ ] `okf/proposals/web-port.md` no se usa como arquitectura actual.
