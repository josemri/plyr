#!/usr/bin/env bash
set -euo pipefail

# ==============================================================
#   PLYR
#   Monta Java + Android SDK + caches en /tmp (autocontenido).
#   "clean" borra todo y deja el proyecto recién clonado.
# ==============================================================

PACKAGE_NAME="com.plyr"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- Rutas: todo vive bajo /tmp/plyr-android ---
TMP_ROOT="${PLYR_TMP_ROOT:-/tmp/plyr-android}"
ANDROID_HOME="$TMP_ROOT/android-sdk"                # Android SDK
JAVA_HOME="$TMP_ROOT/jdk"                           # JDK 21
GRADLE_USER_HOME="$TMP_ROOT/gradle-home"            # caches/daemons de Gradle
GRADLE_PROJECT_CACHE="$TMP_ROOT/gradle-project-cache" # cache del proyecto (antes <repo>/.gradle)
ANDROID_USER_HOME="$TMP_ROOT/android-user"          # claves adb + debug.keystore
XDG_DATA_HOME="$TMP_ROOT/xdg/data"                  # estado del daemon de Kotlin
XDG_CACHE_HOME="$TMP_ROOT/xdg/cache"
XDG_CONFIG_HOME="$TMP_ROOT/xdg/config"

CMD_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"

# --- Estado (defaults) ---
COMMAND=""
BUILD_TYPE="debug"
TEST_DEVICE=false
DO_CLEAN=false
SHOW_LOGS=false
TMUX_SPLIT=""
LOG_TAGS=""
APK_PATH=""

# ==============================================================
#   AYUDA
# ==============================================================

usage() {
    cat <<'EOF'
Uso: ./run.sh <comando> [opciones]

El entorno (Java, Android SDK, caches) se monta solo en /tmp y no crea nada
en tu carpeta personal. Para borrarlo todo: ./run.sh clean

Comandos:
  build [release] [--clean]   Compila el APK (debug por defecto).
  run [release] [-log...]     Compila, instala y abre la app en el móvil.
  install [release] [-log...] Instala el APK ya compilado (sin recompilar).
  test [device]               Ejecuta los tests unitarios. Con "device", los del móvil.
  env                         Imprime las variables de entorno (eval "$(./run.sh env)").
  setup                       (Re)monta el entorno en /tmp (igual que haría build/run solo).
  log [-tags...] [-split...]  Muestra los logs de la app en tiempo real.
  stop                        Cierra la app en el móvil.
  clean                       Borra TODO lo generado (entorno /tmp y builds).
  help                        Muestra esta ayuda.

Opciones:
  release           Compila/usa la variante release (por defecto: debug).
  --clean           Fuerza la recompilación completa (sin borrar el entorno).
  -log              Muestra los logs al terminar (solo con run/install).
  -tags "A B"       Filtra los logs por etiquetas (con run/install/log).
  -split v|h        Muestra los logs en un panel de tmux, vertical (v) u horizontal (h).

Ejemplos:
  ./run.sh build release          # compilar el APK release
  ./run.sh run -log -tags Player  # compilar, instalar, abrir y ver los logs
  ./run.sh test device            # tests en el móvil
  ./run.sh log -tags Player       # ver los logs de la app
  ./run.sh clean                  # borrar todo lo generado
EOF
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_opts() {
    local opt="$1"; shift
    local c
    for c in "$@"; do
        [[ "$COMMAND" == "$c" ]] && return 0
    done
    die "La opción '$opt' no se aplica a '$COMMAND' (usá ./run.sh help)"
}

# ==============================================================
#   ENTORNO (montaje automático en /tmp)
# ==============================================================

export_env() {
    # El daemon de Kotlin usa $XDG_DATA_HOME solo si el directorio existe;
    # si no, cae a ~/.local/share. Por eso se crea siempre antes de gradle.
    mkdir -p "$XDG_DATA_HOME" "$XDG_CACHE_HOME" "$XDG_CONFIG_HOME"
    export ANDROID_HOME
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export JAVA_HOME
    export GRADLE_USER_HOME
    export ANDROID_USER_HOME
    export XDG_DATA_HOME
    export XDG_CACHE_HOME
    export XDG_CONFIG_HOME
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"
}

env_installed() {
    [[ -x "$JAVA_HOME/bin/java" ]] \
        && [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]] \
        && [[ -x "$ANDROID_HOME/platform-tools/adb" ]] \
        && [[ -d "$ANDROID_HOME/platforms/android-36" ]] \
        && [[ -d "$ANDROID_HOME/build-tools/36.0.0" ]]
}

fix_stale_gradle_daemons() {
    # El daemon de Gradle usa el entorno de cuando nació, no el del shell.
    # Si quedó vivo un daemon sin XDG_DATA_HOME, el daemon de Kotlin que lance
    # escribirá sus ficheros en ~/.local/share. Lo reiniciamos la primera vez.
    local pid env_ok=1 pids=()
    mapfile -t pids < <(pgrep -f 'GradleDaemon' 2>/dev/null || true)
    for pid in "${pids[@]}"; do
        if ! grep -aq "XDG_DATA_HOME=$XDG_DATA_HOME" "/proc/$pid/environ" 2>/dev/null; then
            env_ok=0
            break
        fi
    done
    [[ "$env_ok" -eq 1 ]] && return 0

    echo "[env] Daemon de Gradle con entorno obsoleto detectado; reiniciándolo ..."
    ( cd "$SCRIPT_DIR" && GRADLE_USER_HOME="$GRADLE_USER_HOME" ./gradlew --stop ) >/dev/null 2>&1 || true
    sleep 1
    pkill -f 'GradleDaemon' 2>/dev/null || true
    pkill -f 'KotlinCompileDaemon' 2>/dev/null || true
}

ensure_env() {
    export_env
    fix_stale_gradle_daemons
    if ! env_installed; then
        setup_env
    fi
    export_env
}

setup_env() {
    require_tools

    echo "=============================================================="
    echo "  MONTANDO ENTORNO EN /tmp (Java 21 + Android SDK + caches)"
    echo "=============================================================="

    # 1/4 JDK 21 (siempre en /tmp)
    if [[ -x "$JAVA_HOME/bin/java" ]]; then
        echo "[1/4] JDK 21 listo"
    else
        echo "[1/4] Descargando JDK 21 ..."
        curl -fsSL "$JDK_URL" -o "$TMP_ROOT/jdk.tar.gz"
        mkdir -p "$TMP_ROOT/jdk-extract"
        tar -xzf "$TMP_ROOT/jdk.tar.gz" -C "$TMP_ROOT/jdk-extract" --strip-components=1
        mv "$TMP_ROOT/jdk-extract" "$JAVA_HOME"
        rm -f "$TMP_ROOT/jdk.tar.gz"
    fi

    # 2/4 Android cmdline-tools
    if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
        echo "[2/4] cmdline-tools de Android listos"
    else
        echo "[2/4] Descargando cmdline-tools de Android ..."
        mkdir -p "$ANDROID_HOME"
        curl -fsSL "$CMD_TOOLS_URL" -o "$TMP_ROOT/cmdtools.zip"
        rm -rf "$ANDROID_HOME/cmdline-tools-tmp"
        mkdir -p "$ANDROID_HOME/cmdline-tools-tmp"
        unzip -q "$TMP_ROOT/cmdtools.zip" -d "$ANDROID_HOME/cmdline-tools-tmp"
        rm -rf "$ANDROID_HOME/cmdline-tools/latest"
        mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
        mv "$ANDROID_HOME/cmdline-tools-tmp/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
        rm -rf "$ANDROID_HOME/cmdline-tools-tmp" "$TMP_ROOT/cmdtools.zip"
    fi

    # 3/4 SDK (platform, build-tools, platform-tools)
    if [[ -d "$ANDROID_HOME/platforms/android-36" ]] \
        && [[ -d "$ANDROID_HOME/build-tools/36.0.0" ]] \
        && [[ -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        echo "[3/4] SDK de Android listo"
    else
        echo "[3/4] Instalando SDK (platform-36, build-tools, platform-tools) ..."
        yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
            --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
            --sdk_root="$ANDROID_HOME" "platform-tools" "$PLATFORM" "$BUILD_TOOLS"
    fi

    # 4/4 Carpetas de caches dedicadas
    mkdir -p "$GRADLE_USER_HOME" "$GRADLE_PROJECT_CACHE" "$ANDROID_USER_HOME" \
        "$XDG_DATA_HOME" "$XDG_CACHE_HOME" "$XDG_CONFIG_HOME"
    rm -f "$SCRIPT_DIR/local.properties"

    echo ""
    echo "Entorno listo en $TMP_ROOT"
    echo "Para borrarlo todo y empezar de cero: ./run.sh clean"
}

require_tools() {
    command -v curl >/dev/null 2>&1 || die "curl no instalado (sudo apt install curl)"
    command -v unzip >/dev/null 2>&1 || die "unzip no instalado (sudo apt install unzip)"
    command -v tar >/dev/null 2>&1 || die "tar no instalado (sudo apt install tar)"
}

run_gradle() {
    cd "$SCRIPT_DIR"
    export_env
    ./gradlew --project-cache-dir "$GRADLE_PROJECT_CACHE" "$@"
}

# ==============================================================
#   DISPOSITIVO / APK
# ==============================================================

check_device() {
    local count
    count=$(adb devices | grep -w "device" | wc -l)
    [[ "$count" -gt 0 ]] || die "No hay ningún móvil conectado. Activa la depuración USB."
}

find_apk() {
    local matches
    matches=("$SCRIPT_DIR"/app/build/outputs/apk/"$BUILD_TYPE"/*.apk)
    [[ -e ${matches[0]:-} ]] || die "No hay APK $BUILD_TYPE compilado. Ejecuta antes: ./run.sh build $BUILD_TYPE"
    APK_PATH="${matches[0]}"
}

install_apk() {
    local out
    if ! out=$(adb install -r "$APK_PATH" 2>&1); then
        printf '%s\n' "$out" >&2
        die "La instalación en el móvil falló."
    fi
    echo "Instalado: $APK_PATH"
}

launch_app() {
    adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null
    echo "App lanzada: $PACKAGE_NAME"
}

show_logs() {
    local pid filter t
    pid=$(adb shell pidof -s "$PACKAGE_NAME")
    [[ -n "$pid" ]] || die "La app $PACKAGE_NAME no está abierta. Lánzala con: ./run.sh run"

    filter=""
    if [[ -n "$LOG_TAGS" ]]; then
        for t in $LOG_TAGS; do filter+="$t:D "; done
        filter+="*:S"
    fi

    if [[ -n "$TMUX_SPLIT" ]]; then
        [[ -n "${TMUX-}" ]] || die "El 'split' de tmux requiere ejecutar el script dentro de una sesión de tmux."
        local opt="-v"
        [[ "$TMUX_SPLIT" == "v" ]] && opt="-h"
        tmux split-window "$opt" "adb logcat --pid=$pid $filter"
        echo "Logs en un panel de tmux (ciérralo o pulsa Ctrl+C)."
        return
    fi

    echo "=============================================================="
    echo "  LOGS DE $PACKAGE_NAME"${LOG_TAGS:+ (filtro: $LOG_TAGS)}
    echo "  Pulsa Ctrl+C para salir."
    echo "=============================================================="
    exec adb logcat --pid=$pid $filter
}

# ==============================================================
#   COMANDOS
# ==============================================================

cmd_build() {
    local task="assembleDebug"
    [[ "$BUILD_TYPE" == "release" ]] && task="assembleRelease"

    echo "=============================================================="
    echo "  COMPILANDO APK ($BUILD_TYPE)"
    echo "=============================================================="
    local extra=()
    [[ "$DO_CLEAN" == "true" ]] && extra+=(clean)
    run_gradle "${extra[@]}" "$task"
    find_apk
    echo ""
    echo "APK listo: $APK_PATH"
    echo "Para instalarlo en el móvil: ./run.sh install $BUILD_TYPE"
}

cmd_run() {
    check_device
    cmd_build

    echo "=============================================================="
    echo "  INSTALANDO Y ABRIENDO EN EL MÓVIL"
    echo "=============================================================="
    install_apk
    launch_app

    [[ "$SHOW_LOGS" == "true" ]] && show_logs
}

cmd_install() {
    check_device
    find_apk

    echo "=============================================================="
    echo "  INSTALANDO APK $BUILD_TYPE (sin recompilar)"
    echo "=============================================================="
    install_apk
    launch_app

    [[ "$SHOW_LOGS" == "true" ]] && show_logs
}

cmd_test() {
    local task results_dir gradle_rc=0

    if [[ "$TEST_DEVICE" == "true" ]]; then
        check_device
        echo "=============================================================="
        echo "  TESTS EN EL MÓVIL"
        echo "=============================================================="
        task="connectedDebugAndroidTest"
        results_dir="$SCRIPT_DIR/app/build/outputs/androidTest-results/connected"
        list_test_files "$SCRIPT_DIR/app/src/androidTest"
    else
        echo "=============================================================="
        echo "  TESTS UNITARIOS"
        echo "=============================================================="
        task="testDebugUnitTest"
        results_dir="$SCRIPT_DIR/app/build/test-results/$task"
        list_test_files "$SCRIPT_DIR/app/src/test"
    fi

    run_gradle "$task" || gradle_rc=$?

    echo ""
    echo "=============================================================="
    echo "  RESULTADOS"
    echo "=============================================================="
    print_test_summary "$results_dir"

    [[ "$gradle_rc" -ne 0 ]] && die "Tests con fallos (código $gradle_rc)."
    echo ""
    echo "Tests completados."
}

cmd_env() {
    if ! env_installed; then
        die "El entorno aún no está montado. Ejecutá primero: ./run.sh setup"
    fi
    export_env
    echo "# Aplica con: eval \"\$(./run.sh env)\""
    echo "export TMP_ROOT=\"$TMP_ROOT\""
    echo "export ANDROID_HOME=\"$ANDROID_HOME\""
    echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""
    echo "export JAVA_HOME=\"$JAVA_HOME\""
    echo "export GRADLE_USER_HOME=\"$GRADLE_USER_HOME\""
    echo "export ANDROID_USER_HOME=\"$ANDROID_USER_HOME\""
    echo "export XDG_DATA_HOME=\"$XDG_DATA_HOME\""
    echo "export XDG_CACHE_HOME=\"$XDG_CACHE_HOME\""
    echo "export XDG_CONFIG_HOME=\"$XDG_CONFIG_HOME\""
    echo "export PATH=\"$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:\$PATH\""
}

cmd_setup() {
    export_env
    setup_env
}

cmd_log() {
    check_device
    show_logs
}

cmd_stop() {
    check_device
    adb shell am force-stop "$PACKAGE_NAME"
    echo "App cerrada: $PACKAGE_NAME"
}

cmd_clean() {
    echo "=============================================================="
    echo "  BORRANDO TODO LO GENERADO"
    echo "=============================================================="

    # 1) Parar daemons de Gradle (tienen abierto el GRADLE_USER_HOME de /tmp)
    if [[ -x "$SCRIPT_DIR/gradlew" && -d "$GRADLE_USER_HOME" ]]; then
        echo "[1/4] Parando daemons de Gradle ..."
        ( cd "$SCRIPT_DIR" && GRADLE_USER_HOME="$GRADLE_USER_HOME" ./gradlew --stop ) >/dev/null 2>&1 || true
    fi

    # 2) Cerrar procesos que aún usen /tmp (daemon de Kotlin, adb, ...)
    pkill -f -- "$TMP_ROOT" >/dev/null 2>&1 || true

    # 3) Borrar el entorno de /tmp (con guardas de seguridad)
    if [[ -n "$TMP_ROOT" && "$TMP_ROOT" == /tmp/* && "$TMP_ROOT" != /tmp ]]; then
        echo "[3/4] Borrando $TMP_ROOT ..."
        rm -rf -- "$TMP_ROOT"
    else
        die "TMP_ROOT inseguro para borrar: '$TMP_ROOT'"
    fi

    # 4) Borrar lo generado en el propio proyecto
    echo "[4/4] Limpiando el proyecto ..."
    rm -rf -- "$SCRIPT_DIR/.gradle" \
        "$SCRIPT_DIR/build" \
        "$SCRIPT_DIR/app/build" \
        "$SCRIPT_DIR/.kotlin" \
        "$SCRIPT_DIR/local.properties"

    echo ""
    echo "Todo eliminado. La próxima vez que ejecutes ./run.sh <build|run|test>"
    echo "se montará de nuevo el entorno en /tmp desde cero."
}

list_test_files() {
    local dir="$1"
    [[ -d "$dir" ]] || { echo "   (no existe $dir)"; return; }
    local i=0
    while IFS= read -r -d '' f; do
        i=$((i+1))
        echo "    $i. ${f#"$SCRIPT_DIR"/app/src/}"
    done < <(find "$dir" -name '*.kt' -print0 | sort -z)
    echo ""
}

print_test_summary() {
    local dir="$1"
    if [[ -z "$dir" || ! -d "$dir" ]]; then
        echo "   (no se encontraron resultados de tests en: $dir)"
        return 0
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        echo "   (python3 no disponible; los resultados XML están en: $dir)"
        return 0
    fi
    python3 - "$dir" <<'PY'
import os, sys, xml.etree.ElementTree as ET

root_dir = sys.argv[1]
files = sorted(
    os.path.join(dp, f)
    for dp, _, fns in os.walk(root_dir)
    for f in fns
    if f.startswith("TEST-") and f.endswith(".xml")
)
if not files:
    print("   (no se encontraron ficheros de resultados TEST-*.xml)")
    sys.exit(0)

grand_total = grand_pass = grand_fail = grand_error = grand_skip = 0
for path in files:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        print(f"   [XML inválido] {path}")
        continue
    root = tree.getroot()
    suites = root.findall("testsuite") or [root]
    for ts in suites:
        name = ts.get("name") or os.path.basename(path)
        total = int(ts.get("tests", 0))
        fail = int(ts.get("failures", 0))
        err = int(ts.get("errors", 0))
        skip = int(ts.get("skipped", 0))
        grand_total += total
        grand_pass += total - fail - err - skip
        grand_fail += fail
        grand_error += err
        grand_skip += skip
        print(f"\n  ┌─ {name}")
        for tc in ts.findall("testcase"):
            tcname = tc.get("name", "?")
            if tc.find("failure") is not None:
                print(f"  │   ✖ {tcname}")
            elif tc.find("error") is not None:
                print(f"  │   ✖ {tcname} (error)")
            elif tc.find("skipped") is not None:
                print(f"  │   – {tcname} (omitido)")
            else:
                print(f"  │   ✔ {tcname}")
        for tc in ts.findall("testcase"):
            for kind in ("failure", "error"):
                el = tc.find(kind)
                if el is not None:
                    msg = (el.get("message") or "").strip().replace("\n", " ")[:140]
                    print(f"  │   └ {kind.upper()}: {tc.get('classname','')}.{tc.get('name','')}: {msg}")
        print(f"  └── {total} tests ({fail} fallos, {err} errores, {skip} omitidos)")

print(f"\n  RESUMEN GLOBAL: {grand_total} tests | ✔ {grand_pass} | ✖ {grand_fail} | errores {grand_error} | omitidos {grand_skip}")
PY
}

# ==============================================================
#   PUNTO DE ENTRADA
# ==============================================================

parse_args() {
    [[ $# -ge 1 ]] || return 0
    COMMAND="$1"; shift

    case "$COMMAND" in
        build|run|install|test|log|env|setup|stop|clean) ;;
        help|-h|--help)  COMMAND="help" ;;
        reiniciar)       COMMAND="clean" ;;
        -stop)           COMMAND="stop" ;;
        *) die "Comando desconocido: '$COMMAND' (usá ./run.sh help)" ;;
    esac

    while [[ $# -gt 0 ]]; do
        case "$1" in
            debug|release)
                require_opts "$1" build run install
                BUILD_TYPE="$1"; shift
                ;;
            device)
                require_opts "device" test
                TEST_DEVICE=true; shift
                ;;
            --clean|-clean)
                require_opts "--clean" build run
                DO_CLEAN=true; shift
                ;;
            -log)
                require_opts "-log" run install
                SHOW_LOGS=true; shift
                ;;
            -tags)
                require_opts "-tags" run install log
                shift
                [[ $# -ge 1 ]] || die "Usá: -tags 'Tag1 Tag2'"
                LOG_TAGS="$1"; shift
                ;;
            -split)
                require_opts "-split" run log
                shift
                [[ $# -ge 1 ]] || die "Usá: -split v|h"
                [[ "$1" == v || "$1" == h ]] || die "-split solo acepta 'v' (vertical) u 'h' (horizontal)"
                TMUX_SPLIT="$1"; shift
                ;;
            *)
                die "Opción desconocida para '$COMMAND': '$1' (usá ./run.sh help)"
                ;;
        esac
    done
}

parse_args "$@"

[[ -n "$COMMAND" ]] || { usage; exit 0; }

case "$COMMAND" in
    help)    usage ;;
    clean)   cmd_clean ;;
    env)     cmd_env ;;
    setup)   cmd_setup ;;
    build)   ensure_env; cmd_build ;;
    run)     ensure_env; cmd_run ;;
    test)    ensure_env; cmd_test ;;
    install) ensure_env; cmd_install ;;
    log)     ensure_env; cmd_log ;;
    stop)    ensure_env; cmd_stop ;;
esac