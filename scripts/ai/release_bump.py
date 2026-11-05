#!/usr/bin/env python3
"""Resuelve el salto de versión de un release a partir de las etiquetas del PR.

Uso:
    python3 scripts/ai/release_bump.py --event "$GITHUB_EVENT_PATH"
    python3 scripts/ai/release_bump.py --labels release:minor

Reglas:
    - `release:skip` gana sobre todo lo demás.
    - Si hay varias `release:patch|minor|major`, gana la mayor.
    - Sin etiqueta de release, el salto por defecto es `patch`.

La lógica vive aquí, no en el YAML, para poder probarla sin GitHub Actions.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

PATCH = "patch"
MINOR = "minor"
MAJOR = "major"
SKIP = "skip"

ORDER = {PATCH: 1, MINOR: 2, MAJOR: 3}
PREFIX = "release:"


def resolve(labels: list[str]) -> str:
    """Devuelve `skip`, `major`, `minor` o `patch` para una lista de etiquetas."""
    names = {label.strip() for label in labels if label and label.strip()}
    if f"{PREFIX}{SKIP}" in names:
        return SKIP

    bump = PATCH
    for name in names:
        if not name.startswith(PREFIX):
            continue
        candidate = name[len(PREFIX) :]
        if candidate in ORDER and ORDER[candidate] > ORDER[bump]:
            bump = candidate
    return bump


def labels_from_event(path: Path) -> list[str]:
    """Extrae los nombres de etiqueta del payload de GitHub Actions."""
    raw = path.read_text(encoding="utf-8")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"release-bump: evento JSON inválido: {exc}") from exc

    pull_request = data.get("pull_request")
    if not isinstance(pull_request, dict):
        raise SystemExit("release-bump: el evento no contiene 'pull_request'")

    labels = pull_request.get("labels") or []
    names: list[str] = []
    for label in labels:
        if isinstance(label, dict):
            name = label.get("name")
        else:
            name = label
        if isinstance(name, str) and name.strip():
            names.append(name)
    return names


def main() -> int:
    parser = argparse.ArgumentParser(description="Resuelve el bump de release de un PR")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--event", type=Path, help="payload JSON del evento de GitHub")
    source.add_argument("--labels", nargs="*", help="nombres de etiqueta (pruebas)")
    args = parser.parse_args()

    labels = labels_from_event(args.event) if args.event else (args.labels or [])
    print(resolve(labels))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
