#!/usr/bin/env bash
#
# Inyecta en el .deb lo que jpackage no genera (M14):
#   - la dependencia de `libvlc`, que VLCJ carga en tiempo de ejecución;
#   - la ficha AppStream en /usr/share/metainfo/.
#
# Uso: patch-deb.sh <paquete.deb> <metainfo.xml>
set -euo pipefail

deb="${1:?uso: patch-deb.sh <paquete.deb> <metainfo.xml>}"
metainfo="${2:?falta la ruta del metainfo.xml}"
component="$(basename "$metainfo")"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

dpkg-deb -R "$deb" "$work/root"

# `libvlc5` aporta la librería y `vlc-plugin-base` los módulos de acceso y
# códec que VLCJ necesita para reproducir.
if grep -q '^Depends:' "$work/root/DEBIAN/control"; then
    sed -i 's/^Depends:\(.*\)$/Depends:\1, libvlc5 | libvlc-bin, vlc-plugin-base/' "$work/root/DEBIAN/control"
else
    printf 'Depends: libvlc5 | libvlc-bin, vlc-plugin-base\n' >> "$work/root/DEBIAN/control"
fi

install -Dm644 "$metainfo" "$work/root/usr/share/metainfo/$component"

dpkg-deb --build --root-owner-group "$work/root" "$deb"
