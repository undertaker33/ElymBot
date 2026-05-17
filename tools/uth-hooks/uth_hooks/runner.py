from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from .common import EXIT_CODE, final_response, load_event, load_json_path, merge_context, normalize_event_type, result
from .l0_router import check_l0_router
from .l1_process import check_l1_process
from .l2_git import check_git_write
from .l2_script_guard import check_script_guard
from .l2_utf8_doc import check_utf8_doc
from .l2_write_scope import check_file_write
from .l3_closeout import check_l3_closeout

def dispatch(event: dict[str, Any], config: dict[str, Any], state: dict[str, Any], project: Path) -> dict[str, Any]:
    event_type = normalize_event_type(event.get("type") or event.get("event_type"))
    ctx = merge_context(event, state)
    if event_type in {"l0", "l0-router", "router", "preflight"}:
        findings = check_l0_router(ctx, project)
    elif event_type in {"l1", "l1-process", "process"}:
        findings = check_l1_process(ctx)
    elif event_type in {"file-write", "write", "pre-write"}:
        findings = check_file_write(ctx, config, project)
    elif event_type in {"git-write", "git"}:
        findings = check_git_write(ctx)
    elif event_type in {"utf8-doc", "utf8", "doc-guard"}:
        findings = check_utf8_doc(ctx, config, project)
    elif event_type in {"script-guard", "script"}:
        findings = check_script_guard(ctx, project)
    elif event_type in {"l3", "l3-closeout", "closeout"}:
        findings = check_l3_closeout(ctx)
    else:
        findings = [result("BLOCK", "unknown-event-type", f"Unknown hook event type: {event_type or '<missing>'}.")]
    return final_response(event_type or "<missing>", findings)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Run UTH L0/L1/L2/L3 hook gates.")
    parser.add_argument("--event", help="Event JSON path, or '-' for stdin")
    parser.add_argument("--event-json", help="Inline event JSON")
    parser.add_argument("--config", help="Config JSON path")
    parser.add_argument("--state", help="Session state JSON path")
    parser.add_argument("--project", default=".", help="Target project root")
    parser.add_argument("--pretty", action="store_true", help="Pretty-print JSON output")
    args = parser.parse_args(argv)

    event = load_event(args)
    config = load_json_path(args.config, {})
    state = load_json_path(args.state, {})
    project = Path(args.project).resolve()
    response = dispatch(event, config, state, project)

    print(json.dumps(response, ensure_ascii=False, indent=2 if args.pretty else None))
    return EXIT_CODE[response["decision"]]
