#!/usr/bin/env bash
# Reglas estáticas del repo que ninguna herramienta cubre por sí sola.
# Barato (segundos), sin Gradle. Pensado para local y para el job guardrails.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FAIL=0
WARN=0

if ! command -v rg >/dev/null 2>&1; then
    echo "guards: falta ripgrep (rg). Instálalo con 'sudo apt-get install ripgrep'." >&2
    exit 2
fi

fail() {
    echo "  ✖ $1"
    shift
    printf '    %s\n' "$@"
    FAIL=1
}

warn() {
    echo "  ⚠ $1"
    shift
    printf '    %s\n' "$@"
    WARN=$((WARN + 1))
}

ok() {
    echo "  ✔ $1"
}

section() {
    echo ""
    echo "--- $1"
}

section "Imports de commonMain"
violations="$(rg -n --glob '*.kt' '^import (java|javax|android|androidx|org\.json|okhttp3|org\.schabi)\.' core/src/commonMain 2>/dev/null || true)"
if [[ -n "$violations" ]]; then
    fail "commonMain importa APIs de plataforma" "$violations"
else
    ok "commonMain solo usa Kotlin/KotlinX/com.plyr"
fi

section "Imports de jvmSharedMain"
violations="$(rg -n --glob '*.kt' '^import (android|androidx)\.' core/src/jvmSharedMain 2>/dev/null || true)"
if [[ -n "$violations" ]]; then
    fail "jvmSharedMain importa Android" "$violations"
else
    ok "jvmSharedMain libre de Android"
fi

section "Fronteras entre módulos"
violations="$(rg -n --glob '*.kt' '^import android\.' desktop/src/main 2>/dev/null || true)"
if [[ -n "$violations" ]]; then
    fail "desktop importa Android" "$violations"
else
    ok "desktop sin Android"
fi
violations="$(rg -n --glob '*.kt' '^import com\.plyr\.desktop' app/src 2>/dev/null || true)"
if [[ -n "$violations" ]]; then
    fail "app importa código de desktop" "$violations"
else
    ok "app sin dependencias de desktop"
fi

section "Salida por consola en código de producción"
violations="$(rg -n --glob '*.kt' 'println\(|System\.out|System\.err' \
    app/src/main core/src desktop/src/main 2>/dev/null \
    | grep -v 'SoundCheck.kt' || true)"
if [[ -n "$violations" ]]; then
    fail "usa println/System.out; usa el Logger compartido" "$violations"
else
    ok "sin prints fuera de la CLI SoundCheck"
fi

section "Baselines de detekt"
if git rev-parse --git-dir >/dev/null 2>&1; then
    changed_baselines="$({
        git diff --name-only HEAD 2>/dev/null || true
        git ls-files --others --exclude-standard 2>/dev/null || true
    } | grep 'detekt-baseline\.xml' || true)"
    if [[ -n "$changed_baselines" ]]; then
        fail "baseline de detekt modificado sin permiso" "$changed_baselines"
    else
        ok "baselines intactos"
    fi
else
    warn "sin git; no se pueden comprobar baselines"
fi

section "Espacios y finales de línea"
if git rev-parse --git-dir >/dev/null 2>&1; then
    if git diff --check 2>/dev/null | grep -q .; then
        fail "git diff --check encontró espacios o conflictos" "$(git diff --check 2>/dev/null | head -20)"
    else
        ok "sin espacios sobrantes ni marcadores de conflicto"
    fi
fi

section "Módulos que no existen"
violations="$(rg -n --glob '!okf/**' --glob '!**/build/**' --glob '!**/.gradle/**' \
    '"(shared|web|server)"|:(shared|web|server)\b' \
    --glob '*.{kt,kts,md,yml,yaml}' . 2>/dev/null \
    | grep -vE 'okf/proposals|okf/decisions/0005|AGENTS.md' || true)"
if [[ -n "$violations" ]]; then
    warn "referencias a :shared/:web/:server fuera de la propuesta" "$violations"
else
    ok "sin módulos fantasma"
fi

section "Versión"
if git rev-parse --git-dir >/dev/null 2>&1; then
    version_diff="$(git diff -U0 HEAD -- app/build.gradle.kts 2>/dev/null | grep -E '^[+-].*(versionCode|versionName)' || true)"
    if [[ -n "$version_diff" && "${PLYR_ALLOW_VERSION_BUMP:-false}" != "true" ]]; then
        warn "cambio de versión fuera de una release" "$version_diff"
    else
        ok "versión sin cambios accidentales"
    fi
fi

section "Cobertura de tests (aviso)"
if git rev-parse --git-dir >/dev/null 2>&1; then
    new_sources="$({
        git diff --name-only HEAD 2>/dev/null || true
        git ls-files --others --exclude-standard 2>/dev/null || true
    } | grep -E '^[^/]+/src/main/.*\.kt$' || true)"
    missing=()
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        base="$(basename "$file" .kt)"
        case "$base" in
            Main|MainActivity|PlyrApp|Theme|AppIcon|AsciiArt|BuildInfo|Translations) continue ;;
        esac
        if ! find . -path '*/build' -prune -o -name "${base}Test.kt" -print 2>/dev/null | grep -q .; then
            missing+=("$file")
        fi
    done <<<"$new_sources"
    if [[ ${#missing[@]} -gt 0 ]]; then
        warn "ficheros nuevos de producción sin test con nombre equivalente" "${missing[@]}"
    else
        ok "sin avisos de cobertura por nombre"
    fi
fi

echo ""
if [[ "$FAIL" -ne 0 ]]; then
    echo "guards: FALLÓ ($WARN avisos)"
    exit 1
fi
echo "guards: OK ($WARN avisos)"
