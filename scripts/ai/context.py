#!/usr/bin/env python3
"""Muestra el paquete de contexto de un concepto OKF.

Uso:
    python3 scripts/ai/context.py domain/sync-format
    ./run.sh context domain/sync-format

Imprime el concepto, los ficheros `resource:`, los tests declarados, los
conceptos enlazados y los backlinks. Pensado para que un agente cargue contexto
sin abrir el bundle entero.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("context: falta PyYAML.\n")
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[2]
BUNDLE = ROOT / "okf"
LINK_RE = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def split_frontmatter(text: str) -> tuple[dict[str, Any], str]:
    if not text.startswith("---\n"):
        return {}, text
    end = text.find("\n---\n", 4)
    if end == -1:
        return {}, text
    try:
        meta = yaml.safe_load(text[4:end])
    except yaml.YAMLError:
        meta = None
    return (meta if isinstance(meta, dict) else {}), text[end + 5 :]


def repo_file(resource: str) -> str:
    return resource[len("repo://") :] if resource.startswith("repo://") else resource


def links_of(path: Path, body: str) -> set[str]:
    out: set[str] = set()
    for match in LINK_RE.finditer(body):
        target = match.group(1).strip()
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        target = target.split("#", 1)[0].split(" ", 1)[0]
        if not target.endswith(".md"):
            continue
        resolved = (BUNDLE / target.lstrip("/")) if target.startswith("/") else (path.parent / target)
        try:
            out.add(str(resolved.resolve().relative_to(BUNDLE.resolve()).with_suffix("")))
        except ValueError:
            continue
    return out


def find(concept_id: str) -> Path | None:
    cleaned = concept_id.strip().removesuffix(".md").lstrip("/")
    direct = BUNDLE / f"{cleaned}.md"
    if direct.is_file():
        return direct
    matches = [p for p in BUNDLE.rglob("*.md") if p.stem == cleaned]
    if len(matches) == 1:
        return matches[0]
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Paquete de contexto de un concepto OKF")
    parser.add_argument("concept", help="id del concepto, p. ej. domain/sync-format")
    parser.add_argument("--body", action="store_true", help="incluye el cuerpo Markdown")
    args = parser.parse_args()

    path = find(args.concept)
    if path is None:
        print(f"context: no existe el concepto {args.concept!r}", file=sys.stderr)
        candidates = sorted(str(p.relative_to(BUNDLE).with_suffix("")) for p in BUNDLE.rglob("*.md"))
        print("Conceptos disponibles:", file=sys.stderr)
        for candidate in candidates:
            print(f"  {candidate}", file=sys.stderr)
        return 1

    meta, body = split_frontmatter(read(path))
    concept_id = str(path.relative_to(BUNDLE).with_suffix(""))

    print(f"# {meta.get('title', concept_id)}")
    print(f"id: {concept_id}")
    print(f"type: {meta.get('type', '?')} · status: {meta.get('status', '?')}")
    if meta.get("description"):
        print(f"description: {meta['description']}")
    print()

    resources: list[str] = []
    if isinstance(meta.get("resource"), str):
        resources.append(meta["resource"])
    for source in meta.get("sources") or []:
        if isinstance(source, dict) and isinstance(source.get("resource"), str):
            resources.append(source["resource"])

    print("## Ficheros para abrir")
    seen: set[str] = set()
    for resource in resources:
        if resource in seen:
            continue
        seen.add(resource)
        if not resource.startswith("repo://"):
            print(f"- {resource}")
            continue
        relative = repo_file(resource)
        target = ROOT / relative
        state = "ok" if target.exists() else "FALTA"
        lines = read(target).count("\n") if target.is_file() else 0
        print(f"- `{relative}` ({lines} líneas, {state})")
    print()

    tests = meta.get("tests") or []
    if isinstance(tests, str):
        tests = [tests]
    if tests:
        print("## Tests")
        for test in tests:
            target = ROOT / str(test)
            print(f"- `{test}` ({'ok' if target.exists() else 'FALTA'})")
        print()

    outgoing = links_of(path, body)
    if outgoing:
        print("## Conceptos enlazados")
        for linked in sorted(outgoing):
            print(f"- {linked}")
        print()

    backlinks: list[str] = []
    for other in BUNDLE.rglob("*.md"):
        if other == path:
            continue
        other_meta, other_body = split_frontmatter(read(other))
        if other_meta.get("title") is None and other.name not in ("index.md", "log.md"):
            continue
        if concept_id in links_of(other, other_body):
            backlinks.append(str(other.relative_to(BUNDLE).with_suffix("")))
    if backlinks:
        print("## Referenciado desde")
        for linked in sorted(set(backlinks)):
            print(f"- {linked}")
        print()

    print("## Comandos")
    print("./run.sh verify fast")
    print("./run.sh okf-check")
    print()

    if args.body:
        print("## Cuerpo")
        print(body)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
