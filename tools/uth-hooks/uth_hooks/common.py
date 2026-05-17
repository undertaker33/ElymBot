from __future__ import annotations

import argparse
import json
import re
import sys
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Any

DECISION_RANK = {"PASS": 0, "WARN": 1, "ASK": 2, "BLOCK": 3}
EXIT_CODE = {"PASS": 0, "WARN": 0, "ASK": 2, "BLOCK": 3}
RESULT_SCHEMA_VERSION = "uth-hook-result/v1"
FENCE_RE = re.compile(r"^\s*```")
DEFAULT_MOJIBAKE = ["\ufffd", "Ã", "Â", "锟斤拷", "ï»¿"]
CODE_CHANGING_SCENES = {"uth-dev", "uth-debug", "uth-design", "uth-review", "uth-git"}
L3_SCENES = {"uth-onboarding", "uth-dev", "uth-debug", "uth-design", "uth-review", "uth-docs", "uth-git", "uth-context-trace"}
CODE_EXTENSIONS = {
    ".c",
    ".cc",
    ".cpp",
    ".cs",
    ".css",
    ".go",
    ".gradle",
    ".h",
    ".hpp",
    ".html",
    ".java",
    ".js",
    ".jsx",
    ".kt",
    ".kts",
    ".mjs",
    ".php",
    ".ps1",
    ".py",
    ".rb",
    ".rs",
    ".scala",
    ".sh",
    ".swift",
    ".ts",
    ".tsx",
    ".vue",
    ".xml",
    ".yaml",
    ".yml",
}
POSITIVE_CLAIMS = {"complete", "completed", "fixed", "passing", "pass", "ready", "deliverable", "accepted", "mergeable"}
POSITIVE_CLAIM_PHRASES = {
    "完成",
    "完成了",
    "已完成",
    "修好",
    "修好了",
    "已修复",
    "通过",
    "通过了",
    "测试通过",
    "编译通过",
    "构建通过",
    "可交付",
    "可以提交",
    "可以合并",
}
NEGATED_CLAIM_PHRASES = {
    "未完成",
    "没完成",
    "没有完成",
    "未修复",
    "没修好",
    "没有修好",
    "未通过",
    "没通过",
    "没有通过",
    "不通过",
    "不可交付",
}


def load_json_path(raw: str | None, default: dict[str, Any]) -> dict[str, Any]:
    if not raw:
        return default
    path = Path(raw)
    if not path.exists():
        raise SystemExit(f"JSON file not found: {raw}")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def load_event(args: argparse.Namespace) -> dict[str, Any]:
    if args.event_json:
        return json.loads(args.event_json)
    if not args.event:
        raise SystemExit("Provide --event <path>, --event -, or --event-json")
    if args.event == "-":
        return json.loads(sys.stdin.read())
    return json.loads(Path(args.event).read_text(encoding="utf-8-sig"))


def normalize_event_type(value: str | None) -> str:
    return (value or "").strip().lower().replace("_", "-")


def result(decision: str, code: str, message: str, path: str | None = None) -> dict[str, Any]:
    item: dict[str, Any] = {"decision": decision, "code": code, "message": message}
    if path:
        item["path"] = path
    return item


def final_response(event_type: str, findings: list[dict[str, Any]]) -> dict[str, Any]:
    decision = "PASS"
    for item in findings:
        if DECISION_RANK[item["decision"]] > DECISION_RANK[decision]:
            decision = item["decision"]
    response: dict[str, Any] = {"schema_version": RESULT_SCHEMA_VERSION, "decision": decision, "event_type": event_type, "findings": findings}
    route_actions = [item["route_action"] for item in findings if item.get("route_action")]
    if route_actions:
        response["route_action"] = route_actions[0]
    return response


def as_bool(value: Any) -> bool:
    return bool(value) and str(value).lower() not in {"false", "0", "none", "null"}


def merge_context(event: dict[str, Any], state: dict[str, Any]) -> dict[str, Any]:
    merged = dict(state)
    merged.update(event)
    return merged


def strip_current_dir(path: str) -> str:
    clean = path.replace("\\", "/")
    while clean.startswith("./"):
        clean = clean[2:]
    return clean


def normalize_path(raw: str, project: Path) -> str:
    path = Path(raw)
    try:
        if path.is_absolute():
            rel = path.resolve().relative_to(project.resolve())
            return rel.as_posix()
    except ValueError:
        pass
    return strip_current_dir(raw.replace("\\", "/"))


def match_pattern(path: str, pattern: str) -> bool:
    clean_path = strip_current_dir(path.replace("\\", "/"))
    clean_pattern = strip_current_dir(pattern.replace("\\", "/"))
    if clean_pattern.endswith("/**"):
        prefix = clean_pattern[:-3].rstrip("/")
        if any(ch in prefix for ch in "*?["):
            return (
                fnmatchcase(clean_path, prefix)
                or fnmatchcase(clean_path, prefix + "/*")
                or fnmatchcase(clean_path, prefix + "/**")
            )
        return clean_path == prefix or clean_path.startswith(prefix + "/")
    return fnmatchcase(clean_path, clean_pattern)


def matches_any(path: str, patterns: list[str]) -> bool:
    return any(match_pattern(path, pattern) for pattern in patterns)


def get_paths(event: dict[str, Any]) -> list[str]:
    paths = event.get("paths")
    if paths is None and event.get("path"):
        paths = [event["path"]]
    if isinstance(paths, str):
        return [paths]
    return list(paths or [])


def listify(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    if isinstance(value, str):
        return [value] if value else []
    return [value]


def get_changed_files(ctx: dict[str, Any]) -> list[str]:
    for key in ("changed_files", "modified_files", "files_changed", "paths"):
        files = listify(ctx.get(key))
        if files:
            return [strip_current_dir(str(item).replace("\\", "/")) for item in files]
    return []


def is_docs_markdown(path: str) -> bool:
    clean = strip_current_dir(path.replace("\\", "/"))
    lower = clean.lower()
    return (
        lower.endswith((".md", ".markdown"))
        and (
            lower.startswith("docs/")
            or lower in {"agents.md", "readme.md"}
            or "/docs/" in lower
        )
    )


def is_code_like_path(path: str) -> bool:
    clean = strip_current_dir(path.replace("\\", "/"))
    lower = clean.lower()
    if is_docs_markdown(lower):
        return False
    if lower.startswith(("docs/", "skills/")) and lower.endswith((".md", ".markdown", ".json", ".yaml", ".yml")):
        return False
    return Path(lower).suffix in CODE_EXTENSIONS or lower.startswith(("src/", "app/", "lib/", "test/", "tests/"))


def has_code_change(ctx: dict[str, Any]) -> bool:
    if as_bool(ctx.get("code_changed")) or as_bool(ctx.get("source_changed")) or as_bool(ctx.get("tests_changed")):
        return True
    return any(is_code_like_path(path) for path in get_changed_files(ctx))


def has_markdown_doc_change(ctx: dict[str, Any]) -> bool:
    if as_bool(ctx.get("docs_changed")) or as_bool(ctx.get("markdown_changed")):
        return True
    return any(is_docs_markdown(path) for path in get_changed_files(ctx))


def parse_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def get_verification(ctx: dict[str, Any]) -> dict[str, Any]:
    verification = ctx.get("verification") or {}
    if not isinstance(verification, dict):
        verification = {}
    merged = dict(verification)
    for key in (
        "verification_evidence",
        "evidence",
        "verification_command",
        "verification_result",
        "compile_build_pass",
        "compile_pass",
        "build_pass",
        "warnings",
        "exceptions",
        "fresh",
        "waiver_granted",
        "waiver_reason",
        "user_risk_confirmed",
    ):
        if key in ctx and key not in merged:
            merged[key] = ctx[key]
    return merged


def verification_has_evidence(verification: dict[str, Any]) -> bool:
    if as_bool(verification.get("fresh")) is False and str(verification.get("fresh")).lower() in {"false", "0"}:
        return False
    evidence = listify(verification.get("evidence")) or listify(verification.get("verification_evidence"))
    return bool(evidence or verification.get("verification_command") or verification.get("verification_result"))


def compile_build_passed(verification: dict[str, Any]) -> bool | None:
    for key in ("compile_build_pass", "compile_pass", "build_pass"):
        if key in verification:
            return as_bool(verification.get(key))
    result_text = str(verification.get("verification_result", "")).lower()
    if result_text:
        if any(token in result_text for token in ("pass", "passed", "success", "ok")):
            return True
        if any(token in result_text for token in ("fail", "failed", "error")):
            return False
    return None


def is_positive_claim_text(value: str) -> bool:
    text = value.strip().lower()
    if not text:
        return False
    if text in POSITIVE_CLAIMS:
        return True
    if any(phrase in text for phrase in NEGATED_CLAIM_PHRASES):
        return False
    return any(phrase in text for phrase in POSITIVE_CLAIM_PHRASES)


def positive_claim_present(ctx: dict[str, Any]) -> bool:
    claims = {str(item).strip().lower() for item in listify(ctx.get("claims"))}
    claim = str(ctx.get("claim", "")).strip().lower()
    if claim:
        claims.add(claim)
    for key in ("claims_complete", "claims_fixed", "claims_passing", "claims_ready", "claims_deliverable", "claims_accepted"):
        if as_bool(ctx.get(key)):
            return True
    recommendation = str(ctx.get("recommendation", "")).strip().lower()
    if recommendation in {"pass", "accepted", "ready", "mergeable"}:
        return True
    return any(is_positive_claim_text(item) for item in claims)


def design_patch_authorized(ctx: dict[str, Any]) -> bool:
    transition = ctx.get("transition") or {}
    return as_bool(ctx.get("design_patch_authorized") or transition.get("authorized_design_patch"))


def check_positive_claim_evidence(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    if not positive_claim_present(ctx):
        return []
    verification = get_verification(ctx)
    if verification_has_evidence(verification):
        return [result("PASS", "positive-claim-evidence-present", "Positive completion claim has supplied evidence.")]
    return [result("BLOCK", "positive-claim-evidence-missing", "Positive completion/fix/pass/ready claim requires fresh evidence.")]


def check_code_verification(ctx: dict[str, Any], *, force: bool = False) -> list[dict[str, Any]]:
    if not force and not has_code_change(ctx):
        return []
    verification = get_verification(ctx)
    findings: list[dict[str, Any]] = []
    if not verification_has_evidence(verification):
        return [result("BLOCK", "code-verification-evidence-missing", "Code-changing closeout requires supplied compile/build evidence.")]

    passed = compile_build_passed(verification)
    if passed is not True:
        findings.append(result("BLOCK", "compile-build-not-passing", "Compile/build evidence is missing or not passing."))

    warnings = parse_int(verification.get("warnings"))
    exceptions = parse_int(verification.get("exceptions"))
    if warnings is None or exceptions is None:
        findings.append(result("BLOCK", "warning-exception-count-missing", "Code closeout requires warning and exception counts."))
        return findings

    if warnings == 0 and exceptions == 0:
        findings.append(result("PASS", "code-verification-clean", "Compile/build evidence is clean: 0 warning, 0 exception."))
        return findings

    if positive_claim_present(ctx):
        findings.append(result("BLOCK", "positive-claim-with-warning-exception", "Positive complete/pass/ready claims require warnings=0 and exceptions=0."))
        return findings

    waiver = as_bool(verification.get("waiver_granted") or ctx.get("waiver_granted"))
    if not waiver:
        findings.append(
            result(
                "ASK",
                "warning-exception-waiver-needed",
                f"Compile/build evidence has warnings={warnings}, exceptions={exceptions}; ask user for temporary waiver or clean them.",
            )
        )
        return findings

    if positive_claim_present(ctx):
        findings.append(result("BLOCK", "positive-claim-with-waiver", "Waived warning/exception gate cannot support complete/pass/ready claims."))
    else:
        findings.append(result("WARN", "code-verification-waived", f"Warning/exception gate waived with warnings={warnings}, exceptions={exceptions}."))

    wants_git = ctx.get("next_scene") == "uth-git" or as_bool(ctx.get("git_closure_requested")) or as_bool(ctx.get("release_or_tag"))
    if wants_git and not as_bool(verification.get("user_risk_confirmed") or ctx.get("user_risk_confirmed")):
        findings.append(result("ASK", "git-risk-confirmation-needed", "Git/release closure after waiver requires explicit user risk confirmation."))
    return findings
