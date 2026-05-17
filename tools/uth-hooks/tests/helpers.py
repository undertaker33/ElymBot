from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

HOOK_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURES = Path(__file__).resolve().parent / "fixtures"

if str(HOOK_ROOT) not in sys.path:
    sys.path.insert(0, str(HOOK_ROOT))


def load_config() -> dict[str, Any]:
    return json.loads((HOOK_ROOT / "config.example.json").read_text(encoding="utf-8"))


def codes(findings: list[dict[str, Any]]) -> set[str]:
    return {str(item.get("code")) for item in findings}


def decisions(findings: list[dict[str, Any]]) -> set[str]:
    return {str(item.get("decision")) for item in findings}


def assert_has(findings: list[dict[str, Any]], decision: str, code: str) -> None:
    assert any(item.get("decision") == decision and item.get("code") == code for item in findings), findings


def dispatch(event: dict[str, Any], *, project: Path | None = None, state: dict[str, Any] | None = None) -> dict[str, Any]:
    from uth_hooks.runner import dispatch as run_dispatch

    return run_dispatch(event, load_config(), state or {}, project or REPO_ROOT)
