#!/usr/bin/env bash
#
# Genera los iconos del escritorio a partir del de Android (M36).
#
# Única fuente de verdad: `app/src/main/ic_launcher-playstore.png`, el mismo
# `ic_launcher` que se ve en Android (512x512, fondo #1A1A1A). De ahí salen:
#
#   packaging/icon.png                     PNG 512 — jpackage en Linux (.deb/.rpm/AppImage)
#   packaging/icon.ico                     multi-tamaño — jpackage en Windows (.msi)
#   packaging/icon.icns                    multi-tamaño — jpackage en macOS (.dmg)
#   src/main/resources/icon/plyr.png       el que dibuja la app (ventana + botón-logo)
#
# jpackage exige un formato distinto por SO: PNG en Linux, `.ico` en Windows y
# `.icns` en macOS. ImageMagick sabe escribir PNG e ICO, pero **no** `.icns` —si
# le pides `.icns` guarda un PNG con el nombre equivocado—, así que el contenedor
# icns lo arma el pequeño montador en Python de abajo.
#
# Si cambias el icono de Android, vuelve a ejecutarlo y actualiza los binarios:
#
#   ./desktop/packaging/make-icons.sh
set -euo pipefail

cd "$(dirname "$0")/../.."   # raíz del repositorio

SRC="app/src/main/ic_launcher-playstore.png"
PKG="desktop/packaging"
RES="desktop/src/main/resources/icon"

[ -f "$SRC" ] || { echo "error: no existe $SRC" >&2; exit 1; }
command -v convert >/dev/null || { echo "error: hace falta ImageMagick (convert)" >&2; exit 1; }

mkdir -p "$PKG" "$RES"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

# La fuente ya es 512x512: el PNG va tal cual, sin remuestrear.
convert "$SRC" "$PKG/icon.png"
cp "$PKG/icon.png" "$RES/plyr.png"

# ICO para Windows: 256 va comprimido en PNG (Format 6) y el resto en BMP, que
# es lo que espera Explorer en todos los tamaños de la barra de tareas.
convert "$SRC" -define icon:auto-resize=256,128,64,48,32,16 "$PKG/icon.ico"

# PNG sueltos con los que se monta el `.icns` (16..1024). El 1024 es un
# reescalado de la fuente de 512: con un logo plano sobre fondo uniforme el
# reescalado con Lanczos no se nota.
for size in 16 32 64 128 256 512 1024; do
    convert "$SRC" -filter Lanczos -resize "${size}x${size}!" -strip "$tmp/$size.png"
done

# Montador del contenedor icns: cabecera 'icns' + longitud total, y después un
# elemento por tamaño con su tipo, su longitud (incluidos los 8 bytes de
# cabecera) y los datos en PNG, que es exactamente lo que escribe `iconutil` en
# macOS. `ic07..ic10` son las versiones 1x (128..1024), `ic11..ic14` las @2x
# (32..512) y `icp4..icp6` los tamaños pequeños 16/32/64.
python3 - "$tmp" "$PKG/icon.icns" <<'PY'
import pathlib
import struct
import sys

tmp = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])

entries = [
    ("icp4", 16), ("icp5", 32), ("ic11", 32),
    ("icp6", 64), ("ic12", 64),
    ("ic07", 128),
    ("ic08", 256), ("ic13", 256),
    ("ic09", 512), ("ic14", 512),
    ("ic10", 1024),
]

body = b""
for kind, size in entries:
    png = (tmp / f"{size}.png").read_bytes()
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"error: {size}.png no es PNG")
    body += kind.encode("ascii") + struct.pack(">I", len(png) + 8) + png

out.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
print(f"icono icns escrito: {out} ({len(body) + 8} bytes, {len(entries)} elementos)")
PY

# Comprobación: la firma tiene que ser `icns` y los tamaños cuadrar.
python3 - "$PKG/icon.icns" <<'PY'
import pathlib
import struct
import sys

data = pathlib.Path(sys.argv[1]).read_bytes()
if data[:4] != b"icns":
    raise SystemExit("error: la firma no es 'icns'")
total = struct.unpack(">I", data[4:8])[0]
if total != len(data):
    raise SystemExit(f"error: cabecera dice {total} y hay {len(data)}")

pos, count = 8, 0
while pos < len(data):
    _, size = struct.unpack(">4sI", data[pos:pos + 8])
    if size < 8 or pos + size > len(data):
        raise SystemExit(f"error: elemento corrupto en {pos}")
    payload = data[pos + 8:pos + size]
    if payload[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"error: el elemento {pos} no contiene PNG")
    pos += size
    count += 1

print(f"icns verificado: {count} elementos, {len(data)} bytes")
PY

echo "iconos regenerados en $PKG y $RES"
