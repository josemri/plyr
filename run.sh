#!/usr/bin/env bash
set -e

# ==============================================================
#   PLYR - BUILD & RUN ENTRY POINT
#   (unifica run.sh + setup-env.sh)
# ==============================================================

PACKAGE_NAME="com.plyr"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# === RUTAS (todo en /tmp, reinstalable) ===
TMP_ROOT="${PLYR_TMP_ROOT:-/tmp/plyr-android}"
ANDROID_HOME="${ANDROID_HOME:-$TMP_ROOT/android-sdk}"
JAVA_DIR="${JAVA_DIR:-$TMP_ROOT/jdk}"
SYSTEM_JAVA="/usr/lib/jvm/java-21-openjdk-amd64"

CMD_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"

# === ESTADO ===
CMD=""
INSTALL_SUB="env"
BUILD_TYPE="debug"
DO_CLEAN=false
SHOW_LOGS=false
TMUX_SPLIT=""
LOG_TAGS=""
APK_PATH=""
TEST_DEVICE=false

usage() {
    cat <<'EOF'
Uso: ./run.sh <comando> [opciones]

Comandos:
  install env            Instala JDK 21 + Android SDK en /tmp/plyr-android
                         (idempotente: no reinstala lo que ya existe).
  install target [debug|release]
                         Instala en el móvil el APK ya compilado (sin compilar)
                         del tipo indicado (por defecto: debug).
  env                    Imprime las variables de entorno necesarias.
                         Para aplicarlas:  eval "$(./run.sh env)"
  build [debug|release]  Compila el APK (por defecto: debug). No lo instala.
  run   [debug|release]  Compila, instala en el dispositivo y lanza la app.
  test  [debug|release]  Ejecuta los tests unitarios (por defecto: debug).
  test  device [debug|release]
                         Ejecuta los tests instrumentados en el dispositivo.
  -stop | stop           Detiene la app en el dispositivo.
  help                   Muestra esta ayuda.

Opciones (build/run/install target):
  --clean               Limpia la compilación anterior antes de compilar.
  -log                  Muestra logcat al lanzar la app (solo con run).
  -logv / -logh         Muestra logcat en un split de tmux (vertical/horizontal).
  -tags "Tag1 Tag2"     Filtra logcat por tags (con -log*).

Ejemplos:
  ./run.sh install env
  eval "$(./run.sh env)"
  ./run.sh build release --clean
  ./run.sh install target release
  ./run.sh run debug -log -tags PlaylistScreen
  ./run.sh test
  ./run.sh test device
  ./run.sh -stop
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -log|-logv|-logh)
                SHOW_LOGS=true
                case "$1" in
                    -logv) TMUX_SPLIT="v" ;;
                    -logh) TMUX_SPLIT="h" ;;
                esac
                shift
                LOG_TAGS="$*"
                break
                ;;
            install)
                CMD="install"
                INSTALL_SUB="env"
                if [ "$2" = "env" ] || [ "$2" = "target" ]; then
                    INSTALL_SUB="$2"
                    shift
                fi
                shift
                ;;
            *)
                case "$1" in
                    env)                CMD="env" ;;
                    build)              CMD="build" ;;
                    run)                CMD="run" ;;
                    test)               CMD="test" ;;
                    device)
                        if [ "$CMD" = "test" ]; then
                            TEST_DEVICE=true
                        else
                            echo "ERROR: 'device' solo se usa como 'test device'" >&2
                            usage
                            exit 1
                        fi
                        ;;
                    stop|-stop)         CMD="stop" ;;
                    help|-h|--help)     CMD="help" ;;
                    debug)              BUILD_TYPE="debug" ;;
                    release)            BUILD_TYPE="release" ;;
                    --clean)            DO_CLEAN=true ;;
                    target)
                        if [ "$CMD" = "install" ]; then
                            INSTALL_SUB="target"
                        else
                            echo "ERROR: 'target' solo se usa como 'install target'" >&2
                            usage
                            exit 1
                        fi
                        ;;
                    *)
                        echo "ERROR: argumento desconocido: $1" >&2
                        usage
                        exit 1
                        ;;
                esac
                shift
                ;;
        esac
    done
}

# === ENV ===

resolve_env() {
    if [ -x "$SYSTEM_JAVA/bin/java" ]; then
        JAVA_HOME="$SYSTEM_JAVA"
    elif [ -x "$JAVA_DIR/bin/java" ]; then
        JAVA_HOME="$JAVA_DIR"
    else
        echo "ERROR: no se encuentra JDK 21. Ejecuta primero: ./run.sh install env" >&2
        exit 1
    fi

    if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "ERROR: Android SDK no encontrado en $ANDROID_HOME. Ejecuta primero: ./run.sh install env" >&2
        exit 1
    fi

    export ANDROID_HOME
    export JAVA_HOME
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"
}

cmd_env() {
    resolve_env
    echo "# Aplica con: eval \"\$(./run.sh env)\""
    echo "export ANDROID_HOME=\"$ANDROID_HOME\""
    echo "export JAVA_HOME=\"$JAVA_HOME\""
    echo "export PATH=\"$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:\$PATH\""
}

# === INSTALL ENV ===

require_tools() {
    command -v curl >/dev/null 2>&1 || { echo "ERROR: curl no instalado (sudo apt install curl)" >&2; exit 1; }
    command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip no instalado (sudo apt install unzip)" >&2; exit 1; }
}

setup_env() {
    echo "=============================================================="
    echo "  INSTALANDO ENTORNO (JDK + Android SDK)"
    echo "  Destino: $TMP_ROOT"
    echo "=============================================================="
    require_tools

    # --- 1/4 JDK 21 ---
    if [ -x "$SYSTEM_JAVA/bin/java" ]; then
        JAVA_HOME="$SYSTEM_JAVA"
        echo "[1/4] JDK 21 detectado en el sistema: $JAVA_HOME"
    elif [ -x "$JAVA_DIR/bin/java" ]; then
        JAVA_HOME="$JAVA_DIR"
        echo "[1/4] JDK 21 ya instalado en $JAVA_DIR"
    else
        echo "[1/4] Descargando JDK 21 a $JAVA_DIR ..."
        mkdir -p "$TMP_ROOT"
        curl -sL "$JDK_URL" -o "$TMP_ROOT/jdk.tar.gz"
        mkdir -p "$TMP_ROOT/jdk-extract"
        tar -xzf "$TMP_ROOT/jdk.tar.gz" -C "$TMP_ROOT/jdk-extract" --strip-components=1
        mv "$TMP_ROOT/jdk-extract" "$JAVA_DIR"
        rm -f "$TMP_ROOT/jdk.tar.gz"
        JAVA_HOME="$JAVA_DIR"
        echo "      JDK 21 instalado en $JAVA_DIR"
    fi

    # --- 2/4 Android cmdline-tools ---
    if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "[2/4] Android cmdline-tools ya instalados"
    else
        echo "[2/4] Descargando Android cmdline-tools ..."
        mkdir -p "$ANDROID_HOME"
        curl -sL "$CMD_TOOLS_URL" -o "$TMP_ROOT/cmdtools.zip"
        rm -rf "$ANDROID_HOME/cmdline-tools-tmp"
        unzip -qo "$TMP_ROOT/cmdtools.zip" -d "$ANDROID_HOME/cmdline-tools-tmp"
        rm -rf "$ANDROID_HOME/cmdline-tools/latest"
        mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
        mv "$ANDROID_HOME/cmdline-tools-tmp/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
        rm -rf "$ANDROID_HOME/cmdline-tools-tmp" "$TMP_ROOT/cmdtools.zip"
        echo "      cmdline-tools instalados"
    fi

    # --- 3/4 SDK platform + build-tools + platform-tools ---
    if [ -d "$ANDROID_HOME/platforms/android-36" ] \
        && [ -d "$ANDROID_HOME/build-tools/36.0.0" ] \
        && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        echo "[3/4] SDK (platform-36, build-tools, platform-tools) ya instalado"
    else
        echo "[3/4] Instalando $PLATFORM, $BUILD_TOOLS y platform-tools ..."
        yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" > /dev/null
        echo "      SDK instalado"
    fi

    # --- 4/4 local.properties ---
    echo "[4/4] Configurando proyecto ..."
    echo "sdk.dir=$ANDROID_HOME" > "$SCRIPT_DIR/local.properties"

    echo ""
    echo "Entorno listo en $TMP_ROOT"
    echo "Aplica las variables con:  eval \"\$(./run.sh env)\""
}

# === HELPERS DE DISPOSITIVO / APK ===

check_device() {
    if ! command -v adb >/dev/null 2>&1; then
        echo "ERROR: adb no encontrado. Ejecuta primero: ./run.sh install env" >&2
        exit 1
    fi
    local count
    count=$(adb devices | grep -w "device" | wc -l)
    if [ "$count" -eq 0 ]; then
        echo "ERROR: No se detectó ningún dispositivo. Conecta tu Android y habilita la depuración USB." >&2
        exit 1
    fi
}

find_apk() {
    APK_PATH=$(ls "$SCRIPT_DIR/app/build/outputs/apk/$BUILD_TYPE"/*.apk 2>/dev/null | head -n 1)
    if [ -z "$APK_PATH" ]; then
        echo "ERROR: no se encontró el APK $BUILD_TYPE. Compílalo antes con: ./run.sh build $BUILD_TYPE" >&2
        exit 1
    fi
}

install_apk() {
    if [ "$SHOW_LOGS" = true ] && [ -z "$TMUX_SPLIT" ]; then
        adb install -r "$APK_PATH" || { echo "ERROR: instalación fallida" >&2; exit 1; }
    else
        adb install -r "$APK_PATH" >/dev/null 2>&1 && echo "INSTALACIÓN COMPLETADA."
    fi
}

launch_app() {
    echo "=============================================================="
    echo "  LANZANDO LA APLICACIÓN ($PACKAGE_NAME)"
    echo "=============================================================="
    adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
}

show_logs() {
    local pid
    pid=$(adb shell pidof -s "$PACKAGE_NAME")
    if [ -z "$pid" ]; then
        echo "ERROR: No se pudo obtener PID de la app" >&2
        exit 1
    fi

    if [ -n "$TMUX_SPLIT" ]; then
        if [ -z "$TMUX" ]; then
            echo "ERROR: Para usar -logv o -logh debes ejecutar el script dentro de una sesión de tmux." >&2
            exit 1
        fi
        echo "=============================================================="
        echo "  MOSTRANDO LOGCAT EN TMUX ($TMUX_SPLIT)"
        echo "=============================================================="
        local cmd_log
        if [ -z "$LOG_TAGS" ]; then
            cmd_log="adb logcat --pid=$pid"
        else
            cmd_log="adb logcat --pid=$pid ${LOG_TAGS// /:D } *:S"
        fi
        if [ "$TMUX_SPLIT" == "v" ]; then
            tmux split-window -h "$cmd_log"
        else
            tmux split-window -v "$cmd_log"
        fi
    else
        echo "=============================================================="
        echo "  MOSTRANDO LOGCAT EN TERMINAL"
        echo "=============================================================="
        if [ -z "$LOG_TAGS" ]; then
            adb logcat --pid=$pid
        else
            adb logcat --pid=$pid ${LOG_TAGS// /:D } *:S
        fi
    fi
}

# === INSTALL TARGET (instala sin compilar) ===

cmd_install_target() {
    resolve_env
    cd "$SCRIPT_DIR"

    echo "=============================================================="
    echo "  VERIFICANDO DISPOSITIVO CONECTADO"
    echo "=============================================================="
    check_device

    find_apk

    echo "=============================================================="
    echo "  INSTALANDO APK $BUILD_TYPE EN EL DISPOSITIVO (sin compilar)"
    echo "  APK: $APK_PATH"
    echo "=============================================================="
    install_apk
    launch_app

    if [ "$SHOW_LOGS" = true ]; then
        show_logs
    fi

    echo "=============================================================="
    echo "  PROCESO FINALIZADO"
    echo "=============================================================="
}

# === BUILD ===

cmd_build() {
    resolve_env
    cd "$SCRIPT_DIR"

    local task="assembleDebug"
    [ "$BUILD_TYPE" = "release" ] && task="assembleRelease"

    echo "=============================================================="
    echo "  COMPILANDO APK ($BUILD_TYPE)"
    echo "=============================================================="
    if [ "$DO_CLEAN" = true ]; then
        echo "Limpiando compilación anterior ..."
        ./gradlew clean
    fi
    ./gradlew "$task"

    find_apk

    echo ""
    echo "APK generado: $APK_PATH"
}

# === RUN (build + install + launch + logs) ===

cmd_run() {
    resolve_env
    cd "$SCRIPT_DIR"

    echo "=============================================================="
    echo "  VERIFICANDO DISPOSITIVO CONECTADO"
    echo "=============================================================="
    check_device

    cmd_build

    echo "=============================================================="
    echo "  INSTALANDO APK EN EL DISPOSITIVO"
    echo "=============================================================="
    install_apk
    launch_app

    if [ "$SHOW_LOGS" = true ]; then
        show_logs
    fi

    echo "=============================================================="
    echo "  PROCESO FINALIZADO"
    echo "=============================================================="
}

# === TEST ===

cmd_test() {
    resolve_env
    cd "$SCRIPT_DIR"

    local task
    if [ "$TEST_DEVICE" = true ]; then
        check_device
        echo "=============================================================="
        echo "  EJECUTANDO TESTS INSTRUMENTADOS EN EL DISPOSITIVO ($BUILD_TYPE)"
        echo "=============================================================="
        task="connected${BUILD_TYPE^}AndroidTest"
    else
        echo "=============================================================="
        echo "  EJECUTANDO TESTS UNITARIOS ($BUILD_TYPE)"
        echo "=============================================================="
        task="test${BUILD_TYPE^}UnitTest"
    fi

    ./gradlew "$task"

    echo ""
    echo "TESTS COMPLETADOS."
}

# === STOP ===

cmd_stop() {
    resolve_env
    check_device
    echo "Deteniendo la app $PACKAGE_NAME..."
    adb shell am force-stop "$PACKAGE_NAME"
    echo "App detenida."
}

# === MAIN ===

parse_args "$@"

if [ -z "$CMD" ]; then
    usage
    exit 0
fi

case "$CMD" in
    help)    usage ;;
    env)     cmd_env ;;
    build)   cmd_build ;;
    run)     cmd_run ;;
    test)    cmd_test ;;
    stop)    cmd_stop ;;
    install)
        if [ "$INSTALL_SUB" = "target" ]; then
            cmd_install_target
        else
            setup_env
        fi
        ;;
esac
