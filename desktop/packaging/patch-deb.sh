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
# códec que VLCJ necesita para reproducir. El `grep` previo hace que la tarea se
# pueda repetir sobre el mismo `.deb` sin duplicar la dependencia.
if ! grep -q 'libvlc5' "$work/root/DEBIAN/control"; then
    if grep -q '^Depends:' "$work/root/DEBIAN/control"; then
        sed -i 's/^Depends:\(.*\)$/Depends:\1, libvlc5 | libvlc-bin, vlc-plugin-base/' "$work/root/DEBIAN/control"
    else
        printf 'Depends: libvlc5 | libvlc-bin, vlc-plugin-base\n' >> "$work/root/DEBIAN/control"
    fi
fi

install -Dm644 "$metainfo" "$work/root/usr/share/metainfo/$component"

# jpackage deja en los scripts de maintainer una llamada suelta a
# `xdg-desktop-menu` con `set -e` delante. Ese comando aborta con «No writable
# system menu directory found» en cualquier sistema que no tenga el directorio
# `/usr/share/desktop-directories`: no lo crea ningún paquete base, y el test
# `[ -w ]` que hace es falso **aunque se sea root** si el directorio no existe.
# El resultado era un paquete desempaquetado pero sin configurar (estado `iF`)
# sin que hubiera nada malo en la propia app, y sin entrada en el menú.
#
# A mayor abundamiento, ese chequeo es para los ficheros `.directory`, de los
# que plyr no lleva ninguno. Se instala y se quita el `.desktop` a mano, sin
# depender de xdg-utils ni de sus comprobaciones de entorno.
sustituir_llamada() { # $1 = script, $2 = install|uninstall
    local f="$1" orden="$2"
    [ -f "$f" ] || return 0
    awk -v orden="$orden" '
        $1 == "xdg-desktop-menu" && $2 == orden {
            n = split($3, partes, "/")
            base = partes[n]
            if (orden == "install") {
                print "install -d /usr/share/applications"
                print "install -m 644 " $3 " /usr/share/applications/" base
            } else {
                print "rm -f /usr/share/applications/" base
            }
            # Best effort: si la base de datos de tipos MIME no se puede
            # actualizar, la app se instala igualmente.
            print "update-desktop-database /usr/share/applications 2>/dev/null || :"
            next
        }
        { print }
    ' "$f" > "$f.nuevo"
    # El fichero nuevo nace con los permisos del umask (0664) y `dpkg-deb`
    # exige que los scripts de maintainer sean ejecutables (>= 0555), así que
    # se hereda el modo del original antes de sustituirlo.
    chmod --reference="$f" "$f.nuevo"
    mv "$f.nuevo" "$f"
}

sustituir_llamada "$work/root/DEBIAN/postinst" install
sustituir_llamada "$work/root/DEBIAN/prerm" uninstall

dpkg-deb --build --root-owner-group "$work/root" "$deb"
