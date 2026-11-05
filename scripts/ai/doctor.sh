#!/usr/bin/env bash
# Diagnóstico del entorno. No instala nada: informa y sugiere.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Un JAVA_HOME obsoleto (p. ej. tras `./run.sh clean`) no debe impedir el
# diagnóstico: se ignora y se usa el `java` del PATH.
if [[ -n "${JAVA_HOME:-}" && ! -d "$JAVA_HOME" ]]; then
    echo "aviso: JAVA_HOME apunta a un directorio inexistente ($JAVA_HOME); se ignora"
    unset JAVA_HOME
fi

FAIL=0
WARN=0

ok()   { echo "  ✔ $1"; }
warn() { echo "  ⚠ $1"; WARN=$((WARN + 1)); }
fail() { echo "  ✖ $1"; FAIL=$((FAIL + 1)); }

section() { echo ""; echo "--- $1"; }

section "Java"
if command -v java >/dev/null 2>&1; then
    version="$(java -version 2>&1 | head -1)"
    major="$(sed -E 's/.*version "([0-9]+).*/\1/' <<<"$version")"
    if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 21 )); then
        ok "JDK $major ($version)"
    else
        fail "se requiere JDK 21+; encontrado: $version"
    fi
else
    fail "java no está en PATH"
fi

section "Gradle"
if [[ -x ./gradlew ]]; then
    if out="$(./gradlew --version 2>&1)"; then
        ok "wrapper $(grep -m1 '^Gradle ' <<<"$out" | awk '{print $2}')"
    else
        fail "./gradlew --version falló"
        printf '%s\n' "$out" | head -5
    fi
else
    fail "falta ./gradlew o no es ejecutable"
fi

section "Android SDK"
ANDROID_HOME="${ANDROID_HOME:-/tmp/plyr-android/android-sdk}"
if [[ -d "$ANDROID_HOME" ]]; then
    ok "ANDROID_HOME=$ANDROID_HOME"
else
    warn "ANDROID_HOME no existe ($ANDROID_HOME); ejecuta ./run.sh setup"
fi
[[ -x "$ANDROID_HOME/platform-tools/adb" ]] && ok "adb" || warn "adb no encontrado"
[[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]] && ok "sdkmanager" || warn "sdkmanager no encontrado"
[[ -d "$ANDROID_HOME/platforms/android-37.0" ]] && ok "platform 37.0" || warn "falta platform android-37.0"
[[ -d "$ANDROID_HOME/build-tools/37.0.0" ]] && ok "build-tools 37.0.0" || warn "falta build-tools 37.0.0"
if command -v adb >/dev/null 2>&1; then
    devices="$(adb devices 2>/dev/null | grep -cw 'device' || true)"
    if [[ "$devices" -gt 0 ]]; then
        ok "$devices dispositivo(s) conectado(s)"
    else
        warn "sin dispositivos; los tests instrumentados se omitirán"
    fi
fi

section "Audio de escritorio"
vlc_dir="${PLYR_VLC_DIR:-}"
if [[ -n "$vlc_dir" && -d "$vlc_dir" ]]; then
    ok "PLYR_VLC_DIR=$vlc_dir"
elif ldconfig -p 2>/dev/null | grep -q libvlc; then
    ok "libvlc encontrada en el sistema"
else
    warn "sin libvlc: los tests de reproducción real se omitirán (apt install libvlc5 vlc-plugin-base)"
fi

section "Python y herramientas"
if command -v python3 >/dev/null 2>&1; then
    ok "$(python3 --version 2>&1)"
    if python3 -c 'import yaml' >/dev/null 2>&1; then
        ok "PyYAML $(python3 -c 'import yaml; print(yaml.__version__)')"
    else
        fail "PyYAML no instalado (necesario para okf-check)"
    fi
else
    fail "python3 no encontrado"
fi
for tool in git curl node rg; do
    if command -v "$tool" >/dev/null 2>&1; then
        ok "$tool"
    else
        warn "$tool no encontrado"
    fi
done

section "Bundle OKF"
if [[ -f okf/index.md ]]; then
    ok "okf/index.md"
else
    fail "falta okf/index.md"
fi
if [[ -f okf/maps/repo-map.md ]]; then
    ok "okf/maps/repo-map.md"
else
    warn "falta el mapa; ejecuta ./run.sh map"
fi

section "Conectividad (opcional)"
if curl -fsS -m 5 -o /dev/null https://www.youtube.com 2>/dev/null; then
    ok "YouTube alcanzable"
else
    warn "sin acceso a YouTube; los smoke tests de red se omitirán"
fi

echo ""
if [[ "$FAIL" -ne 0 ]]; then
    echo "doctor: $FAIL fallo(s), $WARN aviso(s)"
    exit 1
fi
echo "doctor: OK ($WARN aviso(s))"
