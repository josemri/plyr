#!/usr/bin/env bash
set -e

# ==============================================================
#   ANDROID BUILD & INSTALL SCRIPT
# ==============================================================

# === CONFIGURACIÓN ===
SDK_PATH="$HOME/.local/Android/Sdk"
JAVA_PATH="/usr/lib/jvm/java-17-openjdk-amd64"
PACKAGE_NAME="com.plyr"
SHOW_LOGS=false
TMUX_SPLIT=""
LOG_TAGS=""

# === PARÁMETRO DE LOGS ===
while [[ $# -gt 0 ]]; do
    case $1 in
        -log)
            SHOW_LOGS=true
            shift
            LOG_TAGS="$*"
            break
            ;;
        -logv)
            SHOW_LOGS=true
            TMUX_SPLIT="v"
            shift
            LOG_TAGS="$*"
            break
            ;;
        -logh)
            SHOW_LOGS=true
            TMUX_SPLIT="h"
            shift
            LOG_TAGS="$*"
            break
            ;;
        -stop)
            echo "Deteniendo la app $PACKAGE_NAME..."
            adb shell am force-stop "$PACKAGE_NAME"
            echo "App detenida."
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# === EXPORT VARIABLES ===
export ANDROID_HOME="$SDK_PATH"
export JAVA_HOME="$JAVA_PATH"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"

echo "=============================================================="
echo "  VERIFICANDO DEPENDENCIAS"
echo "=============================================================="
command -v adb >/dev/null 2>&1 || { echo "ERROR: adb no encontrado. Instálalo con: sudo apt install android-sdk-platform-tools"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "ERROR: Java no encontrado. Instálalo con: sudo apt install openjdk-17-jdk"; exit 1; }

echo "=============================================================="
echo "  VERIFICANDO DISPOSITIVO CONECTADO"
echo "=============================================================="
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "ERROR: No se detectó ningún dispositivo. Conecta tu Android y habilita la depuración USB."
  exit 1
fi

echo "=============================================================="
echo "  LIMPIANDO COMPILACIÓN ANTERIOR"
echo "=============================================================="
./gradlew clean

echo "=============================================================="
echo "  COMPILANDO APK (modo debug)"
echo "=============================================================="
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
  echo "ERROR: No se encontró el APK compilado en: $APK_PATH"
  exit 1
fi

echo "=============================================================="
echo "  INSTALANDO APK EN EL DISPOSITIVO"
echo "=============================================================="
if [ "$SHOW_LOGS" = true ] && [ -z "$TMUX_SPLIT" ]; then
    adb install -r "$APK_PATH" || { echo "ERROR: instalación fallida"; exit 1; }
else
    adb install -r "$APK_PATH" >/dev/null 2>&1 && echo "INSTALACIÓN COMPLETADA."
fi

echo "=============================================================="
echo "  LANZANDO LA APLICACIÓN ($PACKAGE_NAME)"
echo "=============================================================="
adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1

# Mostrar logs si se pasó -log o -logv/-logh
if [ "$SHOW_LOGS" = true ]; then
    PID=$(adb shell pidof -s "$PACKAGE_NAME")
    if [ -z "$PID" ]; then
        echo "ERROR: No se pudo obtener PID de la app"
        exit 1
    fi

    if [ -n "$TMUX_SPLIT" ]; then
        if [ -z "$TMUX" ]; then
            echo "ERROR: Para usar -logv o -logh debes ejecutar el script dentro de una sesión de tmux."
            exit 1
        fi
        echo "=============================================================="
        echo "  MOSTRANDO LOGCAT EN TMUX ($TMUX_SPLIT)"
        echo "=============================================================="

        if [ -z "$LOG_TAGS" ]; then
            CMD="adb logcat --pid=$PID"
        else
            CMD="adb logcat --pid=$PID ${LOG_TAGS// /:D } *:S"
        fi

        if [ "$TMUX_SPLIT" == "v" ]; then
            tmux split-window -h "$CMD"
        else
            tmux split-window -v "$CMD"
        fi
    else
        echo "=============================================================="
        echo "  MOSTRANDO LOGCAT EN TERMINAL"
        echo "=============================================================="
        if [ -z "$LOG_TAGS" ]; then
            adb logcat --pid=$PID
        else
            adb logcat --pid=$PID ${LOG_TAGS// /:D } *:S
        fi
    fi
fi

echo "=============================================================="
echo "  PROCESO FINALIZADO"
echo "=============================================================="

