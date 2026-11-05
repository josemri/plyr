#!/usr/bin/env python3
"""Genera okf/maps/repo-map.md desde el árbol real del repositorio.

Uso:
    python3 scripts/ai/okf_map.py            # regenera el mapa
    python3 scripts/ai/okf_map.py --check    # falla si el mapa está desfasado

El mapa es la única parte del bundle que se genera automáticamente. No editar a
mano: cambia el script o vuelve a ejecutarlo.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from datetime import date, timedelta
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("okf-map: falta PyYAML.\n")
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[2]
BUNDLE = ROOT / "okf"
OUTPUT = BUNDLE / "maps" / "repo-map.md"

SOURCE_AREAS = [
    ("`app/src/main`", "app/src/main"),
    ("`app/src/test`", "app/src/test"),
    ("`core/src/commonMain`", "core/src/commonMain"),
    ("`core/src/jvmSharedMain`", "core/src/jvmSharedMain"),
    ("`core/src/commonTest`", "core/src/commonTest"),
    ("`core/src/jvmTest`", "core/src/jvmTest"),
    ("`desktop/src/main`", "desktop/src/main"),
    ("`desktop/src/test`", "desktop/src/test"),
]

TEST_AREAS = [
    ("Android host", "app/src/test"),
    ("Android instrumentado", "app/src/androidTest"),
    ("core commonTest", "core/src/commonTest"),
    ("core jvmTest", "core/src/jvmTest"),
    ("escritorio", "desktop/src/test"),
]

LARGEST_LIMIT = 15


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def count_lines(paths: list[Path]) -> int:
    total = 0
    for path in paths:
        total += read(path).count("\n")
    return total


def kt_files(directory: Path) -> list[Path]:
    if not directory.is_dir():
        return []
    return sorted(p for p in directory.rglob("*.kt") if p.is_file())


def table(headers: list[str], rows: list[list[Any]]) -> str:
    out = ["| " + " | ".join(headers) + " |"]
    out.append("|" + "|".join("---" for _ in headers) + "|")
    for row in rows:
        out.append("| " + " | ".join(str(cell) for cell in row) + " |")
    return "\n".join(out)


def extract(pattern: str, text: str, default: str = "?") -> str:
    match = re.search(pattern, text)
    return match.group(1) if match else default


def build_versions() -> dict[str, str]:
    toml = read(ROOT / "gradle" / "libs.versions.toml")
    versions: dict[str, str] = {}
    in_versions = False
    for line in toml.splitlines():
        stripped = line.strip()
        if stripped.startswith("["):
            in_versions = stripped == "[versions]"
            continue
        if in_versions:
            match = re.match(r"^([\w.-]+)\s*=\s*\"([^\"]+)\"", stripped)
            if match:
                versions[match.group(1)] = match.group(2)
    return versions


def build_info() -> list[tuple[str, str]]:
    app = read(ROOT / "app" / "build.gradle.kts")
    db = read(ROOT / "app" / "src" / "main" / "java" / "com" / "plyr" / "database" / "PlaylistDatabase.kt")
    export = read(ROOT / "core" / "src" / "jvmSharedMain" / "kotlin" / "com" / "plyr" / "utils" / "ExportManifest.kt")
    wrapper = read(ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties")
    versions = build_versions()

    return [
        ("Gradle", extract(r"gradle-([\d.]+)-bin", wrapper)),
        ("AGP", versions.get("agp", "?")),
        ("Kotlin", versions.get("kotlin", "?")),
        ("Compose Multiplatform", versions.get("composeMultiplatform", "?")),
        ("Java toolchain", "21"),
        ("compileSdk", extract(r"compileSdk\s*=\s*(\d+)", app)),
        ("targetSdk", extract(r"targetSdk\s*=\s*(\d+)", app)),
        ("minSdk", extract(r"minSdk\s*=\s*(\d+)", app)),
        ("versionCode", extract(r"versionCode\s*=\s*(\d+)", app)),
        ("versionName", extract(r"versionName\s*=\s*\"([^\"]+)\"", app)),
        ("Room version", extract(r"version\s*=\s*(\d+)", db)),
        ("FORMAT_VERSION", extract(r"FORMAT_VERSION\s*=\s*(\d+)", export)),
    ]


def concept_counts() -> Counter[str]:
    counts: Counter[str] = Counter()
    if not BUNDLE.is_dir():
        return counts
    for path in BUNDLE.rglob("*.md"):
        if path.name in ("index.md", "log.md"):
            continue
        text = read(path)
        if not text.startswith("---\n"):
            continue
        end = text.find("\n---\n", 4)
        if end == -1:
            continue
        try:
            meta = yaml.safe_load(text[4:end])
        except yaml.YAMLError:
            continue
        if isinstance(meta, dict) and isinstance(meta.get("type"), str):
            counts[meta["type"]] += 1
    return counts


def build_map(today: date) -> str:
    areas: list[list[Any]] = []
    all_main: list[Path] = []
    for label, relative in SOURCE_AREAS:
        directory = ROOT / relative
        files = kt_files(directory)
        if relative.endswith("main"):
            all_main.extend(files)
        areas.append([label, len(files), count_lines(files)])

    test_rows: list[list[Any]] = []
    for label, relative in TEST_AREAS:
        files = kt_files(ROOT / relative)
        tests = sum(read(f).count("@Test") for f in files)
        test_rows.append([label, f"`{relative}`", len(files), tests])

    largest = sorted(all_main, key=lambda p: read(p).count("\n"), reverse=True)[:LARGEST_LIMIT]
    largest_rows = [
        [f"`{p.relative_to(ROOT)}`", read(p).count("\n")]
        for p in largest
    ]

    info_rows = [[name, f"`{value}`"] for name, value in build_info()]
    type_rows = [[name, count] for name, count in sorted(concept_counts().items())]

    generated_at = f"{today.isoformat()}T00:00:00Z"
    stale_after = (today + timedelta(days=180)).isoformat()

    parts = [
        "---",
        "type: Map",
        "title: Mapa del repositorio",
        "description: Estadísticas generadas desde el árbol real (módulos, source sets, LOC, tests, versiones). No editar a mano.",
        "resource: repo://.",
        "tags: [mapa, generado, estadisticas]",
        "module: repo",
        f"generated: {{ by: tool:okf-map/1.0, at: {generated_at} }}",
        "status: stable",
        f"stale_after: {stale_after}",
        "---",
        "",
        "# Cómo se genera",
        "",
        "```bash",
        "./run.sh map           # regenera este fichero",
        "./run.sh okf-check     # falla si está desfasado",
        "```",
        "",
        "Fuente: `scripts/ai/okf_map.py`. Si el mapa y el código discrepan, el",
        "código manda y el mapa se regenera.",
        "",
        "# Módulos y código",
        "",
        table(["Área", "Ficheros `.kt`", "Líneas"], areas),
        "",
        "# Tests",
        "",
        table(["Área", "Ruta", "Ficheros", "`@Test`"], test_rows),
        "",
        "# Ficheros más grandes (solo `main`)",
        "",
        table(["Fichero", "Líneas"], largest_rows),
        "",
        "# Versiones y contratos",
        "",
        table(["Clave", "Valor"], info_rows),
        "",
        "# Conceptos del bundle por tipo",
        "",
        table(["Tipo", "Conceptos"], type_rows),
        "",
        "# Comandos de verificación",
        "",
        "| Necesitas | Comando |",
        "|---|---|",
        "| Todo lo barato | `./run.sh verify fast` |",
        "| Tests JVM/host | `./run.sh verify unit` |",
        "| detekt + lint | `./run.sh verify analysis` |",
        "| Replicar CI | `./run.sh verify full` |",
        "| Reglas del repo | `./run.sh guards` |",
        "| Bundle OKF | `./run.sh okf-check` |",
        "",
    ]
    return "\n".join(parts)


def normalize(text: str) -> str:
    lines = []
    for line in text.splitlines():
        if line.startswith("generated:") or line.startswith("stale_after:"):
            continue
        lines.append(line)
    return "\n".join(lines).strip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Genera el mapa OKF del repositorio")
    parser.add_argument("--check", action="store_true", help="falla si el mapa no está actualizado")
    args = parser.parse_args()

    content = build_map(date.today())

    if args.check:
        if not OUTPUT.is_file():
            print(f"okf-map: falta {OUTPUT.relative_to(ROOT)}; ejecuta ./run.sh map", file=sys.stderr)
            return 1
        if normalize(read(OUTPUT)) != normalize(content):
            print(
                f"okf-map: {OUTPUT.relative_to(ROOT)} está desactualizado; ejecuta ./run.sh map",
                file=sys.stderr,
            )
            return 1
        print("okf-map: mapa actualizado")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(content, encoding="utf-8")
    print(f"okf-map: escrito {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
