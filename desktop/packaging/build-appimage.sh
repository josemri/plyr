#!/usr/bin/env bash
#
# Envuelve el `app-image` que genera jpackage (el `TargetFormat.AppImage` de
# Compose: un directorio descomprimido) en un `.AppImage` de verdad (M34). Es el
# binario universal de Linux: se ejecuta en cualquier distro —incluida Arch, que
# no tiene `.deb` ni `.rpm`— sin instalar nada, y no exige VLC ni Java del
# sistema (el runtime de la app va embebido).
#
# Uso: build-appimage.sh <app-image> <dir-salida> <version>
#   app-image : p. ej. desktop/build/compose/binaries/main/app/plyr
#   dir-salida: p. ej. desktop/build/compose/binaries/main/appimage
#   version   : la misma que Android, p. ej. 1.3.0
set -euo pipefail

app_image="${1:?uso: build-appimage.sh <app-image> <dir-salida> <version>}"
out_dir="${2:?falta el directorio de salida}"
version="${3:?falta la versión}"

app_name="$(basename "$app_image")"
launcher="$app_image/bin/$app_name"
[ -x "$launcher" ] || { echo "error: no se encontró el lanzador $launcher" >&2; exit 1; }

icon="$app_image/lib/$app_name.png"
[ -f "$icon" ] || { echo "error: no se encontró el icono $icon" >&2; exit 1; }

# Herramientas de empaquetado, con versión y hash fijados. No viajan dentro de la
# app: solo construyen el AppImage. Al subir una versión, actualizar los cuatro
# valores (versión e hash de appimagetool y del runtime) a la vez.
appimagetool_version="1.9.1"
runtime_version="20251108"

arch="$(uname -m)"
case "$arch" in
    x86_64)
        appimagetool_sha256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
        runtime_sha256="2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d"
        ;;
    aarch64)
        appimagetool_sha256="f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158"
        runtime_sha256="00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444"
        ;;
    *)
        echo "error: arquitectura no soportada para AppImage: $arch" >&2
        exit 1
        ;;
esac

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() {
    # fetch <url> <destino> <sha256>
    curl -fsSL --retry 5 --retry-delay 10 --connect-timeout 15 --max-time 600 -o "$2" "$1"
    echo "$3  $2" | sha256 -c -
}

fetch "https://github.com/AppImage/appimagetool/releases/download/${appimagetool_version}/appimagetool-${arch}.AppImage" \
    "$work/appimagetool" "$appimagetool_sha256"
fetch "https://github.com/AppImage/type2-runtime/releases/download/${runtime_version}/runtime-${arch}" \
    "$work/runtime" "$runtime_sha256"
chmod +x "$work/appimagetool"

# El AppDir es el app-image más el andamiaje que exige el formato: AppRun, la
# entrada .desktop y el icono en la raíz.
appdir="$work/${app_name}.AppDir"
mkdir -p "$appdir"
cp -a "$app_image/." "$appdir/"

{
    printf '#!/bin/sh\n'
    printf 'HERE="$(dirname "$(readlink -f "$0")")"\n'
    printf 'exec "$HERE/bin/%s" "$@"\n' "$app_name"
} > "$appdir/AppRun"
chmod +x "$appdir/AppRun"

cat > "$appdir/$app_name.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$app_name
Exec=AppRun
Icon=$app_name
Comment=Reproductor de musica minimalista (YouTube + listas locales).
Categories=Audio;
Terminal=false
EOF
cp "$icon" "$appdir/$app_name.png"

mkdir -p "$out_dir"
output="$out_dir/${app_name}-${version}-${arch}.AppImage"
# APPIMAGE_EXTRACT_AND_RUN: ejecuta appimagetool sin depender de FUSE (los
# runners de CI no lo tienen). --no-appstream: no enviamos metadatos AppStream.
ARCH="$arch" APPIMAGE_EXTRACT_AND_RUN=1 "$work/appimagetool" \
    --no-appstream --runtime-file "$work/runtime" "$appdir" "$output"

echo "AppImage creado: $output"
