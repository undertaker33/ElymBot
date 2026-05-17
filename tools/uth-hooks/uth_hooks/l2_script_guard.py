from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path
from typing import Any

from .common import as_bool, get_paths, normalize_path, result

SCRIPT_SYNTAX_TIMEOUT_SECONDS = 10

def find_bash() -> str | None:
    if os.name == "nt":
        roots = [os.environ.get("ProgramFiles"), os.environ.get("ProgramFiles(x86)")]
        for root in [value for value in roots if value]:
            for suffix in ("Git/bin/bash.exe", "Git/usr/bin/bash.exe"):
                candidate = Path(root) / suffix
                if candidate.exists():
                    return str(candidate)
    return shutil.which("bash")


def find_powershell() -> str | None:
    return shutil.which("pwsh") or shutil.which("powershell")


def run_command(command: list[str], timeout: int = SCRIPT_SYNTAX_TIMEOUT_SECONDS) -> tuple[int, str]:
    try:
        proc = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        return 124, f"timed out after {timeout}s. {str(output).strip()}".strip()
    return proc.returncode, proc.stdout.strip()


def check_script_guard(ctx: dict[str, Any], project: Path) -> list[dict[str, Any]]:
    strict = any(
        as_bool(ctx.get(key))
        for key in ("script_is_deliverable", "claimed_verified", "syntax_required", "script_is_key_entrypoint")
    )
    findings: list[dict[str, Any]] = []
    for raw in get_paths(ctx):
        path = (project / raw).resolve() if not Path(raw).is_absolute() else Path(raw)
        rel = normalize_path(str(path), project)
        findings.extend(check_one_script(path, rel, strict))
    return findings or [result("BLOCK", "no-script-paths", "script-guard event requires paths.")]


def check_one_script(path: Path, rel: str, strict: bool) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not path.exists() or not path.is_file():
        return [result("BLOCK", "script-file-missing", "Script file does not exist.", rel)]
    data = path.read_bytes()
    if not data:
        findings.append(result("BLOCK", "empty-script", "Script file is empty.", rel))
        return findings
    if data.startswith(b"\xef\xbb\xbf"):
        findings.append(result("BLOCK", "script-bom", "Script file has UTF-8 BOM.", rel))
        data = data[3:]
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        findings.append(result("BLOCK", "script-invalid-utf8", f"Invalid UTF-8 at byte {exc.start}: {exc.reason}.", rel))
        return findings

    if "#!" in text.splitlines()[0] and not text.startswith("#!"):
        findings.append(result("BLOCK", "shebang-hidden-prefix", "Shebang is not the first bytes of the file.", rel))
    elif text.lstrip().startswith("#!") and not text.startswith("#!"):
        findings.append(result("BLOCK", "shebang-leading-whitespace", "Shebang has leading whitespace.", rel))
    else:
        findings.append(result("PASS", "script-basic-pass", "Script UTF-8/no-BOM/shebang guard passed.", rel))

    suffix = path.suffix.lower()
    if suffix in {".js", ".cjs", ".mjs"}:
        findings.append(run_syntax_check(rel, ["node", "--check", str(path)], "node --check", strict))
    elif suffix == ".sh" or text.startswith("#!/usr/bin/env bash") or text.startswith("#!/bin/bash") or text.startswith("#!/usr/bin/env sh"):
        bash = find_bash()
        if bash:
            findings.append(run_syntax_check(rel, [bash, "-n", str(path)], "bash -n", strict))
        else:
            findings.append(missing_tool_result(rel, "bash", strict))
    elif suffix == ".py" or text.startswith("#!/usr/bin/env python") or text.startswith("#!/usr/bin/python"):
        findings.append(check_python_syntax(rel, text, str(path)))
    elif suffix == ".ps1":
        ps = find_powershell()
        if ps:
            command = (
                "$errors = $null; "
                "[System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw -LiteralPath $args[0]), [ref]$errors) | Out-Null; "
                "if ($errors -and $errors.Count -gt 0) { $errors | ForEach-Object { Write-Error $_ }; exit 1 }"
            )
            findings.append(run_syntax_check(rel, [ps, "-NoProfile", "-NonInteractive", "-Command", command, str(path)], "PowerShell parser", strict))
        else:
            findings.append(missing_tool_result(rel, "powershell", strict))

    return findings


def missing_tool_result(path: str, tool: str, strict: bool) -> dict[str, Any]:
    if strict:
        return result("BLOCK", "syntax-tool-missing", f"{tool} is unavailable; cannot claim script verification.", path)
    return result("WARN", "syntax-tool-missing", f"{tool} is unavailable; syntax check skipped.", path)


def check_python_syntax(path: str, text: str, filename: str) -> dict[str, Any]:
    try:
        compile(text, filename, "exec")
    except SyntaxError as exc:
        return result("BLOCK", "script-syntax-failed", f"Python syntax compile failed: {exc}", path)
    return result("PASS", "script-syntax-pass", "Python syntax compile passed.", path)


def run_syntax_check(path: str, command: list[str], label: str, strict: bool) -> dict[str, Any]:
    executable = command[0]
    if not Path(executable).exists() and not shutil.which(executable):
        return missing_tool_result(path, executable, strict)
    code, output = run_command(command)
    if code == 0:
        return result("PASS", "script-syntax-pass", f"{label} passed.", path)
    return result("BLOCK", "script-syntax-failed", f"{label} failed: {output}", path)
