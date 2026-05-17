from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .common import as_bool, result

PROJECT_MARKER_SCHEMA = "uth-governance-project/v1"


def route_result(decision: str, code: str, message: str, route_action: str) -> dict[str, Any]:
    item = result(decision, code, message)
    item["route_action"] = route_action
    return item


def check_l0_router(ctx: dict[str, Any], project: Path) -> list[dict[str, Any]]:
    if as_bool(ctx.get("no_engineering_action")) or not as_bool(ctx.get("engineering_action")):
        return [route_result("PASS", "no-engineering-action", "No engineering action; UTH routing stays idle.", "idle")]

    if as_bool(ctx.get("skill_creator_active") or ctx.get("explicit_skill_creator")):
        return [route_result("PASS", "skill-creator-yield", "Explicit skill-creator work bypasses UTH scene routing.", "yield-skill-creator")]

    if as_bool(ctx.get("explicit_onboarding") or ctx.get("explicit_uth_enable") or ctx.get("explicit_uth_takeover")):
        return [route_result("PASS", "explicit-onboarding", "Explicit UTH onboarding or enablement may run without a project marker.", "enter-onboarding")]

    marker = project / ".uth-governance" / "project.json"
    if not marker.exists():
        return [
            route_result(
                "PASS",
                "uth-silent-without-project-marker",
                "Project marker is absent; UTH child scenes stay silent unless onboarding is explicit.",
                "silent",
            )
        ]

    marker_error = validate_project_marker(marker)
    if marker_error:
        return [marker_error]

    if as_bool(ctx.get("scene_ambiguous")):
        return [route_result("BLOCK", "scene-ambiguous", "Scene is unclear; ask one concise clarification question.", "ask")]

    if ctx.get("active_scene") or ctx.get("requested_scene"):
        return [route_result("PASS", "scene-declared", "UTH-enabled project has an explicit scene.", "enter-scene")]

    return [route_result("BLOCK", "scene-required", "UTH-enabled project engineering action requires an explicit scene.", "block")]


def validate_project_marker(marker: Path) -> dict[str, Any] | None:
    try:
        data = json.loads(marker.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        return result("BLOCK", "project-marker-invalid", f"Project marker is not valid JSON: {exc}.", str(marker))

    required = {
        "schema": str,
        "enabled": bool,
        "onboarded_at": str,
        "onboarding_mode": str,
        "docs_root": str,
        "entrypoints": dict,
    }
    for key, expected_type in required.items():
        if key not in data:
            return result("BLOCK", "project-marker-invalid", f"Project marker missing required field: {key}.", str(marker))
        if not isinstance(data[key], expected_type):
            return result("BLOCK", "project-marker-invalid", f"Project marker field has wrong type: {key}.", str(marker))

    if data["schema"] != PROJECT_MARKER_SCHEMA:
        return result("BLOCK", "project-marker-invalid", f"Project marker schema must be {PROJECT_MARKER_SCHEMA}.", str(marker))
    if data["enabled"] is not True:
        return result("BLOCK", "project-marker-disabled", "Project marker is present but UTH is not enabled.", str(marker))

    entrypoints = data["entrypoints"]
    for key in ("agent", "docs", "current_state", "context"):
        if not isinstance(entrypoints.get(key), str) or not entrypoints[key]:
            return result("BLOCK", "project-marker-invalid", f"Project marker entrypoints.{key} must be a non-empty string.", str(marker))
    return None
