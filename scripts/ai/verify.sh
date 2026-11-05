#!/usr/bin/env bash
# Verificación por modos. Ejecuta exactamente lo que corre CI, sin duplicar la
# matriz en varios sitios.
#
# Uso: scripts/ai/verify.sh [fast|unit|analysis|full|changed]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MODE="${1:-fast}"
GRADLE_ARGS=()
if [[ -n "${GRADLE_PROJECT_CACHE:-}" ]]; then
    GRADLE_ARGS+=(--project-cache-dir "$GRADLE_PROJECT_CACHE")
fi

# Compose Desktop no debe abrir ventana en tests/CI.
export DISPLAY=""

gradle() {
    ./gradlew "${GRADLE_ARGS[@]}" "$@"
}

banner() {
    echo ""
    echo "=============================================================="
    echo "  $1"
    echo "=============================================================="
}

changed_files() {
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        return 0
    fi
    {
        git diff --name-only HEAD 2>/dev/null || true
        git ls-files --others --exclude-standard 2>/dev/null || true
    } | sort -u
}

affected_modules() {
    local files
    files="$(changed_files)"
    if [[ -z "$files" ]]; then
        echo "app core desktop"
        return
    fi
    local modules=()
    grep -q '^app/' <<<"$files" && modules+=("app")
    grep -q '^core/' <<<"$files" && modules+=("core")
    grep -q '^desktop/' <<<"$files" && modules+=("desktop")
    # Cambios de build/config afectan a todo.
    if grep -qE '^(build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties|gradle/)' <<<"$files"; then
        modules=("app" "core" "desktop")
    fi
    if [[ ${#modules[@]} -eq 0 ]]; then
        echo "app core desktop"
    else
        echo "${modules[@]}"
    fi
}

compile_tasks() {
    local -a tasks=()
    for module in "$@"; do
        case "$module" in
            app)     tasks+=(":app:compileDebugKotlin") ;;
            core)    tasks+=(":core:compileKotlinJvm") ;;
            desktop) tasks+=(":desktop:compileKotlin") ;;
        esac
    done
    printf '%s\n' "${tasks[@]}"
}

unit_tasks() {
    local -a tasks=()
    for module in "$@"; do
        case "$module" in
            app)     tasks+=(":app:testDebugUnitTest") ;;
            core)    tasks+=(":core:jvmTest" ":core:testAndroidHostTest") ;;
            desktop) tasks+=(":desktop:test") ;;
        esac
    done
    printf '%s\n' "${tasks[@]}"
}

run_fast() {
    banner "VERIFY FAST · configuración + compilación"
    gradle help --no-daemon
    local modules tasks
    modules="$(affected_modules)"
    # shellcheck disable=SC2206
    local -a module_list=($modules)
    echo "Módulos afectados: $modules"
    mapfile -t tasks < <(compile_tasks "${module_list[@]}")
    gradle "${tasks[@]}"
}

run_unit() {
    banner "VERIFY UNIT · tests JVM/host"
    gradle \
        :app:testDebugUnitTest \
        :core:jvmTest \
        :core:testAndroidHostTest \
        :desktop:test
}

run_analysis() {
    banner "VERIFY ANALYSIS · detekt + lint"
    gradle :app:detektMain :desktop:detektMain :core:detektMainJvm
    gradle :app:lint
}

run_full() {
    run_unit
    run_analysis
    banner "VERIFY FULL · artefactos"
    gradle :app:assembleDebug :desktop:createDistributable
}

run_changed() {
    banner "VERIFY CHANGED · solo módulos tocados"
    local modules tasks
    modules="$(affected_modules)"
    # shellcheck disable=SC2206
    local -a module_list=($modules)
    echo "Módulos afectados: $modules"
    gradle help --no-daemon
    mapfile -t tasks < <(compile_tasks "${module_list[@]}")
    gradle "${tasks[@]}"
    mapfile -t tasks < <(unit_tasks "${module_list[@]}")
    gradle "${tasks[@]}"
}

case "$MODE" in
    fast)     run_fast ;;
    unit)     run_unit ;;
    analysis) run_analysis ;;
    full)     run_full ;;
    changed)  run_changed ;;
    *)
        echo "Modo desconocido: $MODE" >&2
        echo "Usa: fast | unit | analysis | full | changed" >&2
        exit 2
        ;;
esac

banner "VERIFY OK · $MODE"
echo "Recuerda: los tests omitidos (red, VLC, dispositivo) no cuentan como pasados."
