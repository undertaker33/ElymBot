from __future__ import annotations

from pathlib import Path
from typing import Any

from .common import as_bool, get_paths, match_pattern, matches_any, normalize_path, result

def scene_allowed_patterns(ctx: dict[str, Any], config: dict[str, Any]) -> list[str]:
    active_scene = ctx.get("active_scene")
    patterns: list[str] = []
    patterns.extend(config.get("default_allowed_writes", []))
    patterns.extend(config.get("scene_write_rules", {}).get(active_scene, []))
    patterns.extend(ctx.get("allowed_writes", []))
    for expansion in ctx.get("scope_expansions", []):
        if isinstance(expansion, str):
            patterns.append(expansion)
        elif expansion.get("approved_by_user") and expansion.get("path"):
            patterns.append(expansion["path"])
    return patterns


def scene_forbidden_patterns(ctx: dict[str, Any], config: dict[str, Any]) -> list[str]:
    active_scene = ctx.get("active_scene")
    patterns: list[str] = []
    patterns.extend(config.get("default_forbidden_writes", []))
    patterns.extend(config.get("scene_forbidden_writes", {}).get(active_scene, []))
    patterns.extend(ctx.get("forbidden_writes", []))
    return patterns


def skill_maintenance_allowed(ctx: dict[str, Any]) -> bool:
    return (
        as_bool(ctx.get("skill_creator_active"))
        or as_bool(ctx.get("explicit_skill_maintenance"))
        or ctx.get("active_scene") == "skill-creator"
    )


def uth_sp_maintenance_allowed(ctx: dict[str, Any]) -> bool:
    return skill_maintenance_allowed(ctx) and as_bool(ctx.get("explicit_uth_sp_maintenance"))


def check_hard_forbidden(path: str, ctx: dict[str, Any]) -> dict[str, Any] | None:
    active_scene = ctx.get("active_scene")

    if match_pattern(path, ".git/**"):
        return result("BLOCK", "git-dir-write", "Direct writes under .git are forbidden.", path)

    if match_pattern(path, "skills/uth-sp-*/**"):
        if uth_sp_maintenance_allowed(ctx):
            return result("PASS", "uth-sp-skill-maintenance", "UTH-SP skill maintenance write is explicitly allowed.", path)
        return result(
            "BLOCK",
            "uth-sp-skill-write-blocked",
            "skills/uth-sp-*/** requires explicit UTH-SP maintenance and skill-creator flow.",
            path,
        )

    if match_pattern(path, "skills/**"):
        if skill_maintenance_allowed(ctx):
            return result("PASS", "skill-maintenance", "Skill maintenance write is explicitly allowed.", path)
        return result("BLOCK", "skill-write-blocked", "skills/** writes require explicit skill maintenance.", path)

    if match_pattern(path, "docs/context/**") and active_scene not in {"uth-docs", "uth-onboarding"}:
        return result("BLOCK", "context-write-blocked", "docs/context/** is writable only in uth-docs or uth-onboarding.", path)

    if match_pattern(path, "docs/archive/**") and active_scene not in {"uth-docs", "uth-onboarding"}:
        return result("BLOCK", "archive-write-blocked", "docs/archive/** is writable only in uth-docs or uth-onboarding.", path)

    if match_pattern(path, "docs/decisions/ADR-*.md") and active_scene != "uth-design":
        return result("BLOCK", "adr-write-blocked", "ADR body/status writes require uth-design.", path)

    if match_pattern(path, "docs/changelogs/**") and active_scene not in {"uth-git", "uth-onboarding"}:
        return result("BLOCK", "changelog-write-blocked", "docs/changelogs/** writes require uth-git or uth-onboarding for scaffold indexes.", path)

    return None


def check_file_write(ctx: dict[str, Any], config: dict[str, Any], project: Path) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    allowed = scene_allowed_patterns(ctx, config)
    forbidden = scene_forbidden_patterns(ctx, config)
    for raw in get_paths(ctx):
        path = normalize_path(raw, project)
        hard = check_hard_forbidden(path, ctx)
        if hard:
            findings.append(hard)
            continue
        if matches_any(path, forbidden):
            findings.append(result("BLOCK", "write-forbidden", "Path matches current forbidden write scope.", path))
            continue
        if matches_any(path, config.get("extra_hard_forbidden", [])):
            findings.append(result("BLOCK", "extra-hard-forbidden", "Path matches project extra_hard_forbidden.", path))
            continue
        if allowed and matches_any(path, allowed):
            findings.append(result("PASS", "write-allowed", "Path is within current allowed write scope.", path))
        else:
            findings.append(result("ASK", "write-scope-expansion", "Path is outside current allowed write scope; ask user for temporary expansion.", path))
    return findings or [result("BLOCK", "no-write-paths", "file-write event requires paths.")]
