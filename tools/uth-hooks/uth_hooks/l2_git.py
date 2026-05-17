from __future__ import annotations

import re
from typing import Any

from .common import as_bool, result

READ_ONLY_PATTERNS = [
    r"^git\s+status(\s|$)",
    r"^git\s+diff(\s|$)",
    r"^git\s+log(\s|$)",
    r"^git\s+show(\s|$)",
    r"^git\s+branch\s+--show-current\s*$",
    r"^git\s+branch\s+(-a|--all|-r|--remotes|--list)(\s|$)",
    r"^git\s+branch\s+(-v|-vv)(\s|$)",
    r"^git\s+branch\s*$",
    r"^git\s+tag\s*$",
    r"^git\s+tag\s+(-l|--list)(\s|$)",
    r"^git\s+worktree\s+list(\s|$)",
    r"^gh\s+pr\s+(view|checks|status|diff)(\s|$)",
    r"^gh\s+run\s+(view|list)(\s|$)",
]

WRITE_PATTERNS = [
    r"^git\s+add\b",
    r"^git\s+commit\b",
    r"^git\s+push\b",
    r"^git\s+tag\b",
    r"^git\s+merge\b",
    r"^git\s+rebase\b",
    r"^git\s+switch\b",
    r"^git\s+checkout\b",
    r"^git\s+branch\s+.+",
    r"^git\s+reset\b",
    r"^git\s+restore\b",
    r"^git\s+rm\b",
    r"^git\s+mv\b",
    r"^git\s+clean\b",
    r"^git\s+fetch\b",
    r"^git\s+stash\b",
    r"^git\s+pull\b",
    r"^git\s+cherry-pick\b",
    r"^git\s+revert\b",
    r"^git\s+worktree\s+(add|remove|move|prune|repair)\b",
    r"^gh\s+pr\s+(checkout|create|merge|edit|close|reopen|ready)\b",
    r"^gh\s+release\s+(create|delete|edit|upload)\b",
]


def is_read_only_git_command(command: str) -> bool:
    text = command.strip()
    return any(re.search(pattern, text) for pattern in READ_ONLY_PATTERNS)


def is_git_write_command(command: str) -> bool:
    text = command.strip()
    if is_read_only_git_command(text):
        return False
    return any(re.search(pattern, text) for pattern in WRITE_PATTERNS)


def check_git_write(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    commands = ctx.get("commands", [])
    if isinstance(commands, str):
        commands = [commands]
    write_commands = [cmd for cmd in commands if is_git_write_command(cmd)]
    if not write_commands:
        return [result("PASS", "no-git-write", "No Git write command detected.")]

    findings: list[dict[str, Any]] = []
    if ctx.get("active_scene") != "uth-git":
        findings.append(result("BLOCK", "git-scene-required", "Git writes require active_scene = uth-git."))
    if not as_bool(ctx.get("git_plan_present")):
        findings.append(result("BLOCK", "git-plan-missing", "Git writes require a shown Git plan."))
    if not as_bool(ctx.get("user_git_confirmed")):
        findings.append(result("BLOCK", "git-confirmation-missing", "Git writes require explicit user confirmation."))
    if as_bool(ctx.get("release_or_tag")) and not as_bool(ctx.get("changelog_ok")):
        findings.append(result("BLOCK", "changelog-required", "Release/tag Git writes require changelog rule satisfaction."))

    if findings:
        return findings
    return [result("PASS", "git-write-allowed", "Git write gate passed.")]
