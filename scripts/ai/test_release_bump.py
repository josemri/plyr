#!/usr/bin/env python3
"""Pruebas de la resolución de bump de release.

Se ejecutan en el job `guardrails` de CI:
    python3 scripts/ai/test_release_bump.py
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from release_bump import MAJOR, MINOR, PATCH, SKIP, labels_from_event, resolve


class ResolveTest(unittest.TestCase):
    def test_sin_etiquetas_es_patch(self) -> None:
        self.assertEqual(resolve([]), PATCH)

    def test_patch(self) -> None:
        self.assertEqual(resolve(["release:patch"]), PATCH)

    def test_minor(self) -> None:
        self.assertEqual(resolve(["release:minor"]), MINOR)

    def test_major(self) -> None:
        self.assertEqual(resolve(["release:major"]), MAJOR)

    def test_gana_la_mayor(self) -> None:
        self.assertEqual(
            resolve(["release:patch", "release:major", "release:minor"]),
            MAJOR,
        )

    def test_skip_gana_sobre_todo(self) -> None:
        self.assertEqual(resolve(["release:major", "release:skip"]), SKIP)

    def test_etiquetas_ajenas_se_ignoran(self) -> None:
        self.assertEqual(resolve(["bug", "documentation"]), PATCH)

    def test_etiqueta_desconocida_no_rompe(self) -> None:
        self.assertEqual(resolve(["release:urgent"]), PATCH)

    def test_espacios_alrededor(self) -> None:
        self.assertEqual(resolve([" release:minor "]), MINOR)


class LabelsFromEventTest(unittest.TestCase):
    def _event(self, labels: list[object]) -> Path:
        handle = tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False, encoding="utf-8"
        )
        with handle:
            json.dump({"pull_request": {"labels": labels}}, handle)
        return Path(handle.name)

    def test_lee_objetos_de_etiqueta(self) -> None:
        path = self._event([{"name": "release:minor"}, {"name": "bug"}])
        try:
            self.assertEqual(labels_from_event(path), ["release:minor", "bug"])
        finally:
            path.unlink()

    def test_ignora_nombres_vacios(self) -> None:
        path = self._event([{"name": ""}, {"name": "release:major"}])
        try:
            self.assertEqual(labels_from_event(path), ["release:major"])
        finally:
            path.unlink()


if __name__ == "__main__":
    unittest.main()
