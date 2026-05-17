from __future__ import annotations

from pathlib import Path
from typing import Any

from .common import DEFAULT_MOJIBAKE, FENCE_RE, get_paths, normalize_path, result

def check_utf8_doc(ctx: dict[str, Any], config: dict[str, Any], project: Path) -> list[dict[str, Any]]:
    markers = config.get("utf8_doc_guard", {}).get("mojibake_markers", DEFAULT_MOJIBAKE)
    findings: list[dict[str, Any]] = []
    for raw in get_paths(ctx):
        path = (project / raw).resolve() if not Path(raw).is_absolute() else Path(raw)
        rel = normalize_path(str(path), project)
        if not path.exists() or not path.is_file():
            findings.append(result("BLOCK", "doc-file-missing", "Document file does not exist.", rel))
            continue
        data = path.read_bytes()
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            findings.append(result("BLOCK", "invalid-utf8", f"Invalid UTF-8 at byte {exc.start}: {exc.reason}.", rel))
            continue
        for marker in markers:
            if marker in text:
                findings.append(result("BLOCK", "mojibake-marker", f"Mojibake marker found: {marker!r}.", rel))
        if path.suffix.lower() in {".md", ".markdown"}:
            fence_count = sum(1 for line in text.splitlines() if FENCE_RE.match(line))
            if fence_count % 2:
                findings.append(result("BLOCK", "markdown-fence-unbalanced", f"Unbalanced Markdown fences ({fence_count}).", rel))
        if not any(item.get("path") == rel for item in findings):
            findings.append(result("PASS", "utf8-doc-pass", "UTF-8 doc guard passed.", rel))
    return findings or [result("BLOCK", "no-doc-paths", "utf8-doc event requires paths.")]
