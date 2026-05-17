from __future__ import annotations

from typing import Any

from .common import (
    L3_SCENES,
    as_bool,
    check_code_verification,
    check_positive_claim_evidence,
    design_patch_authorized,
    get_changed_files,
    get_verification,
    has_code_change,
    has_markdown_doc_change,
    listify,
    parse_int,
    result,
)

DOCS_COMPLETION_LEVELS = {
    "full-project-docs-complete",
    "scoped-docs-complete",
    "blocked",
    "partial/paused",
}

TAKEOVER_SCOPES = {
    "enable-only",
    "full-takeover",
}

OLD_DOC_CLASSIFICATION_KEYS = (
    "old_docs_unclassified_count",
    "old_docs_unclassified",
    "old_docs_unclassified_list",
)

CRITICAL_FACT_EVIDENCE_KEYS = (
    "unread_critical_count",
    "unconfirmed_critical_count",
    "unresolved_critical_count",
    "critical_unread_count",
    "critical_unconfirmed_count",
    "critical_unresolved_count",
    "unread_critical_module_count",
    "unconfirmed_entrypoint_count",
    "unconfirmed_verification_path_count",
    "unresolved_doc_conflict_count",
    "unread_critical_facts",
    "unconfirmed_critical_facts",
    "unresolved_critical_facts",
    "critical_unread",
    "critical_unconfirmed",
    "critical_unresolved",
    "unread_critical_list",
    "unconfirmed_critical_list",
    "unresolved_critical_list",
    "unread_critical_modules",
    "unconfirmed_entrypoints",
    "unconfirmed_verification_paths",
    "unresolved_doc_conflicts",
)

PROJECT_DOCS_COMPLETION_TEXT_KEYS = (
    "closeout_text",
    "closeout_summary",
    "summary",
    "final_summary",
    "final_status",
    "status",
    "message",
    "result",
    "report",
    "closeout_report",
)

PROJECT_DOCS_COMPLETION_PHRASES = (
    "project documentation governance is complete",
    "project docs governance is complete",
    "full-project docs complete",
    "full project docs complete",
    "full-project documentation complete",
    "\u9879\u76ee\u5b8c\u6574\u6587\u6863\u6cbb\u7406\u5b8c\u6210",
    "\u5168\u9879\u76ee\u6587\u6863\u6cbb\u7406\u5b8c\u6210",
)


def check_l3_closeout(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    scene = ctx.get("active_scene")
    if scene not in L3_SCENES:
        return [result("BLOCK", "l3-scene-unsupported", f"L3 closeout requires one of {sorted(L3_SCENES)}.")]

    findings: list[dict[str, Any]] = []
    findings.extend(check_positive_claim_evidence(ctx))
    findings.extend(check_closeout_report_language(ctx))

    if scene == "uth-onboarding":
        findings.extend(check_l3_onboarding(ctx))
    elif scene == "uth-dev":
        findings.extend(check_l3_dev(ctx))
    elif scene == "uth-debug":
        findings.extend(check_l3_debug(ctx))
    elif scene == "uth-design":
        findings.extend(check_l3_design(ctx))
    elif scene == "uth-review":
        findings.extend(check_l3_review(ctx))
    elif scene == "uth-docs":
        findings.extend(check_l3_docs(ctx))
    elif scene == "uth-git":
        findings.extend(check_l3_git(ctx))
    elif scene == "uth-context-trace":
        findings.extend(check_l3_context_trace(ctx))

    return findings or [result("PASS", "l3-closeout-pass", "L3 closeout gate passed.")]


def check_l3_onboarding(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    mode = ctx.get("mode")
    if mode not in {"new-project", "existing-project"}:
        findings.append(result("BLOCK", "onboarding-mode-missing", "uth-onboarding requires mode new-project or existing-project."))
    if not as_bool(ctx.get("project_marker_written")):
        findings.append(result("BLOCK", "project-marker-missing", "uth-onboarding closeout requires .uth-governance/project.json."))
    if not as_bool(ctx.get("current_state_written")):
        findings.append(result("BLOCK", "initial-current-state-missing", "uth-onboarding closeout requires initial docs/current-state.md."))
    if not as_bool(ctx.get("hook_tools_copied")):
        findings.append(result("BLOCK", "hook-tools-missing", "uth-onboarding closeout requires project-local tools/uth-hooks copied."))
    if has_markdown_doc_change(ctx) and not document_language_ready(ctx):
        findings.append(result("BLOCK", "document-language-missing", "First governed Markdown writes require a selected and persisted project document_language."))
    if has_markdown_doc_change(ctx) and not as_bool(ctx.get("utf8_guard_passed")):
        findings.append(result("BLOCK", "utf8-guard-missing", "Governed Markdown changes require UTF-8/fence guard evidence."))
    if mode == "existing-project":
        findings.extend(check_existing_project_takeover_scope(ctx))
    if as_bool(ctx.get("git_write_performed")):
        findings.append(result("BLOCK", "git-write-in-onboarding", "uth-onboarding must not perform Git writes."))
    if has_code_change(ctx) or as_bool(ctx.get("code_files_modified")):
        findings.append(result("BLOCK", "code-write-in-onboarding", "uth-onboarding must not modify source/test code."))
    return findings


def check_existing_project_takeover_scope(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    scope = takeover_scope(ctx)
    final_closeout = as_bool(ctx.get("takeover_final_closeout"))

    if final_closeout:
        if not scope:
            findings.append(result("BLOCK", "takeover-scope-missing", "takeover_final_closeout requires takeover_scope=full-takeover."))
        elif scope != "full-takeover":
            findings.append(result("BLOCK", "takeover-scope-invalid", "takeover_final_closeout requires takeover_scope=full-takeover."))
        findings.extend(check_onboarding_takeover_final(ctx))
        return findings

    if not scope:
        findings.append(result("BLOCK", "takeover-scope-missing", "existing-project onboarding requires takeover_scope=enable-only or full-takeover."))
        return findings
    if scope not in TAKEOVER_SCOPES:
        findings.append(result("BLOCK", "takeover-scope-invalid", "existing-project onboarding takeover_scope must be enable-only or full-takeover."))
        return findings
    if scope == "full-takeover":
        findings.extend(check_onboarding_full_takeover_preflight(ctx))
    return findings


def check_onboarding_full_takeover_preflight(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not as_bool(ctx.get("backup_zip_created")):
        findings.append(result("BLOCK", "onboarding-backup-missing", "existing-project full-takeover preflight requires docs backup zip before docs writes."))
    if not as_bool(ctx.get("handoff_snapshot_created")):
        findings.append(result("BLOCK", "onboarding-snapshot-missing", "existing-project full-takeover preflight requires handoff snapshot under docs/snapshots/."))
    if not old_docs_classification_evidence_present(ctx):
        findings.append(result("BLOCK", "old-doc-classification-evidence-missing", "existing-project full-takeover preflight requires explicit old-doc classification marker/count/list."))
    if not critical_fact_evidence_present(ctx):
        findings.append(result("BLOCK", "critical-fact-evidence-missing", "existing-project full-takeover preflight requires explicit unread/unconfirmed/unresolved fact marker/count/list."))
    if not takeover_handoff_paused_or_blocked(ctx) and not is_onboarding_followup_route(ctx):
        findings.append(result("BLOCK", "onboarding-docs-handoff-missing", "existing-project full-takeover preflight must hand off to uth-docs onboarding-followup unless paused or blocked."))
    return findings


def check_onboarding_takeover_final(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    if not as_bool(ctx.get("takeover_final_closeout")):
        return []

    findings: list[dict[str, Any]] = []
    if not as_bool(ctx.get("docs_followup_completed")):
        findings.append(result("BLOCK", "takeover-docs-followup-missing", "Final existing-project takeover requires completed uth-docs onboarding-followup evidence."))
    if docs_completion_level(ctx) != "full-project-docs-complete":
        findings.append(result("BLOCK", "takeover-full-docs-completion-missing", "Final existing-project takeover requires docs_completion_level=full-project-docs-complete."))
    if not as_bool(ctx.get("return_to_onboarding")):
        findings.append(result("BLOCK", "takeover-return-missing", "Final existing-project takeover must return from uth-docs to uth-onboarding for total closeout."))
    if not as_bool(ctx.get("backup_zip_reported_to_user")):
        findings.append(result("BLOCK", "takeover-backup-report-missing", "Final existing-project takeover must report the backup zip path to the user."))
    if not old_docs_classification_evidence_present(ctx):
        findings.append(result("BLOCK", "old-doc-classification-evidence-missing", "Final existing-project takeover requires explicit old-doc classification evidence."))
    if old_docs_unclassified_count(ctx) > 0:
        findings.append(result("BLOCK", "takeover-old-docs-unclassified", "Final existing-project takeover cannot leave old docs unclassified."))
    if non_empty_list(ctx, "active_takeover_blockers"):
        findings.append(result("BLOCK", "takeover-active-blockers", "Final existing-project takeover cannot have active takeover blockers."))
    if not as_bool(ctx.get("current_state_cleaned")):
        findings.append(result("BLOCK", "takeover-current-state-not-cleaned", "Final existing-project takeover requires cleaned docs/current-state.md evidence."))
    if not as_bool(ctx.get("context_rebuilt_or_confirmed")):
        findings.append(result("BLOCK", "takeover-context-not-rebuilt", "Final existing-project takeover requires rebuilt or confirmed docs/context/ evidence."))
    return findings


def check_l3_dev(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    mode = ctx.get("mode")
    changed = get_changed_files(ctx)
    if as_bool(ctx.get("files_changed", True)) and not changed and not as_bool(ctx.get("no_files_changed")):
        findings.append(result("BLOCK", "changed-files-missing", "uth-dev closeout requires changed files or explicit no-files-changed."))

    findings.extend(check_code_verification(ctx))

    if mode == "light-dev":
        if not as_bool(ctx.get("lw_final_record_written")):
            findings.append(result("BLOCK", "lw-final-missing", "light-dev closeout requires the final LW record at task completion."))
    elif mode in {"formal-dev", "todo-implementation"}:
        if not as_bool(ctx.get("feedback_written")) and not ctx.get("feedback_not_written_reason"):
            findings.append(result("BLOCK", "feedback-missing", "formal development closeout requires Feedback or an explicit reason."))

    if as_bool(ctx.get("git_write_performed")):
        findings.append(result("BLOCK", "git-write-in-dev", "uth-dev must not perform Git writes; hand off to uth-git after user confirmation."))
    return findings


def check_l3_debug(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not ctx.get("root_cause") and not as_bool(ctx.get("root_cause_unknown")):
        findings.append(result("BLOCK", "root-cause-status-needed", "uth-debug closeout must state root cause or explicitly say it remains unknown."))
    if has_code_change(ctx) and not ctx.get("fix_scope"):
        findings.append(result("BLOCK", "fix-scope-missing", "Debug repair closeout requires fix scope."))
    findings.extend(check_code_verification(ctx))
    if as_bool(ctx.get("git_write_performed")):
        findings.append(result("BLOCK", "git-write-in-debug", "uth-debug must not perform Git writes; hand off to uth-git after user confirmation."))
    return findings


def check_l3_design(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if has_code_change(ctx):
        if not design_patch_authorized(ctx):
            findings.append(result("ASK", "design-patch-authorization-needed", "Code patch in uth-design requires asking the user for explicit authorization."))
        if as_bool(ctx.get("design_patch_scope_expanded")):
            findings.append(result("BLOCK", "design-patch-scope-expanded", "Expanded code patch must switch to uth-dev or uth-debug."))
        findings.extend(check_code_verification(ctx))
    if as_bool(ctx.get("feature_implementation_started")):
        findings.append(result("BLOCK", "implementation-in-design", "Feature implementation belongs to uth-dev, not uth-design."))
    return findings


def check_l3_review(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if has_code_change(ctx) or as_bool(ctx.get("code_files_modified")):
        findings.append(result("BLOCK", "code-write-in-review", "uth-review forbids source/test code writes; route fixes to uth-debug or uth-dev."))

    reviewing_code = as_bool(ctx.get("review_target_code_changed") or ctx.get("reviewing_code_changes"))
    recommendation = str(ctx.get("recommendation", "")).strip().lower()
    if reviewing_code and recommendation in {"pass", "accepted", "ready", "mergeable"}:
        findings.extend(check_code_verification(ctx, force=True))
    if reviewing_code and recommendation in {"pass with risk", "pass-with-risk"}:
        verification = get_verification(ctx)
        warnings = parse_int(verification.get("warnings"))
        exceptions = parse_int(verification.get("exceptions"))
        has_counts = warnings is not None and exceptions is not None
        has_risk = has_counts and (warnings > 0 or exceptions > 0)
        if has_risk and not as_bool(verification.get("waiver_granted") or ctx.get("waiver_granted") or ctx.get("review_risk_accepted")):
            findings.append(result("ASK", "review-risk-waiver-needed", "pass with risk for warnings/exceptions requires user risk waiver."))
    if as_bool(ctx.get("static_review_only")) and recommendation in {"pass", "accepted", "ready", "mergeable"}:
        findings.append(result("BLOCK", "static-review-positive-claim", "Static-only review cannot claim pass/accepted/ready; use pass with risk or needs follow-up."))
    if as_bool(ctx.get("git_write_performed")):
        findings.append(result("BLOCK", "git-write-in-review", "uth-review must not perform Git writes."))
    return findings


def check_l3_docs(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if has_code_change(ctx) or as_bool(ctx.get("code_files_modified")):
        findings.append(result("BLOCK", "code-write-in-docs", "uth-docs is documentation-only; route code changes to implementation/debug scenes."))
    if has_markdown_doc_change(ctx) and not document_language_ready(ctx):
        findings.append(result("BLOCK", "document-language-missing", "uth-docs Markdown writes require a selected and persisted project document_language."))
    if has_markdown_doc_change(ctx) and not as_bool(ctx.get("utf8_guard_passed")):
        findings.append(result("BLOCK", "utf8-guard-missing", "Governed Markdown changes require UTF-8/fence guard evidence."))
    context_touched = as_bool(ctx.get("context_touched")) or any(path.startswith("docs/context/") for path in get_changed_files(ctx))
    if context_touched and not (ctx.get("context_source_evidence") or ctx.get("context_source_omitted_reason")):
        findings.append(result("BLOCK", "context-source-missing", "docs/context changes require source evidence or an explicit omission reason."))
    archive_touched = as_bool(ctx.get("archive_touched")) or any(path.startswith("docs/archive/") for path in get_changed_files(ctx))
    if archive_touched:
        if not as_bool(ctx.get("archive_paths_listed")):
            findings.append(result("BLOCK", "archive-paths-missing", "Archive cleanup requires before/after paths."))
        if not as_bool(ctx.get("current_state_active_cleaned")):
            findings.append(result("BLOCK", "archive-current-state-cleanup-missing", "Archive cleanup must confirm current-state no longer lists archived items as active."))
    if as_bool(ctx.get("tests_run")) or as_bool(ctx.get("compile_run")):
        findings.append(result("WARN", "docs-validation-run", "uth-docs should report documentation-only; do not claim code validation from this scene."))
    findings.extend(check_l3_docs_completion(ctx))
    return findings


def check_l3_docs_completion(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    level = docs_completion_level(ctx)
    if level and level not in DOCS_COMPLETION_LEVELS:
        findings.append(result("BLOCK", "docs-completion-level-invalid", f"Unknown docs_completion_level: {level}."))
    if project_docs_completion_claimed(ctx) and level != "full-project-docs-complete":
        findings.append(result("BLOCK", "project-docs-completion-level-missing", "Project-wide documentation completion claims require docs_completion_level=full-project-docs-complete."))

    mode = ctx.get("mode")
    if mode == "onboarding-followup":
        findings.extend(check_docs_onboarding_followup(ctx))
    if level == "full-project-docs-complete":
        findings.extend(check_docs_full_project_completion(ctx))
    if level == "scoped-docs-complete":
        findings.extend(check_docs_scoped_completion(ctx))
    if mode == "module-split":
        findings.extend(check_docs_module_split(ctx))
    if mode == "module-governance":
        findings.extend(check_docs_module_governance(ctx))
    findings.extend(check_docs_cleanup_backup(ctx))
    return findings


def check_docs_onboarding_followup(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if docs_completion_level(ctx) != "full-project-docs-complete":
        findings.append(result("BLOCK", "docs-full-project-required", "uth-docs onboarding-followup must finish with full-project-docs-complete."))
    if not as_bool(ctx.get("full_project_baseline_completed")):
        findings.append(result("BLOCK", "docs-full-project-baseline-missing", "uth-docs onboarding-followup requires a completed full-project baseline."))
    if not old_docs_classification_evidence_present(ctx):
        findings.append(result("BLOCK", "old-doc-classification-evidence-missing", "uth-docs onboarding-followup requires explicit old-doc classification evidence."))
    if old_docs_unclassified_count(ctx) > 0:
        findings.append(result("BLOCK", "old-docs-unclassified", "uth-docs onboarding-followup cannot leave old docs unclassified."))
    if non_empty_list(ctx, "active_takeover_blockers"):
        findings.append(result("BLOCK", "active-takeover-blockers", "uth-docs onboarding-followup cannot close with active takeover blockers."))
    if not as_bool(ctx.get("current_state_cleaned")):
        findings.append(result("BLOCK", "takeover-current-state-not-cleaned", "uth-docs onboarding-followup requires cleaned docs/current-state.md."))
    if not as_bool(ctx.get("context_rebuilt_or_confirmed")):
        findings.append(result("BLOCK", "takeover-context-not-rebuilt", "uth-docs onboarding-followup requires rebuilt or confirmed docs/context/."))
    return findings


def check_docs_full_project_completion(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not as_bool(ctx.get("full_project_baseline_completed")):
        findings.append(result("BLOCK", "docs-full-project-baseline-missing", "full-project-docs-complete requires a completed full-project baseline."))
    if not non_empty_list(ctx, "baseline_source_scope"):
        findings.append(result("BLOCK", "docs-baseline-scope-missing", "full-project-docs-complete requires baseline_source_scope."))
    if not non_empty_list(ctx, "baseline_excluded_paths") and not text_value(ctx, "baseline_excluded_reason"):
        findings.append(result("BLOCK", "docs-baseline-exclusions-missing", "full-project-docs-complete requires baseline exclusions or an exclusion reason."))
    if has_unresolved_critical_facts(ctx):
        findings.append(result("BLOCK", "docs-baseline-unresolved-facts", "full-project-docs-complete cannot leave unread, unconfirmed, or unresolved critical facts."))
    if as_bool(ctx.get("module_split_required")) and non_empty_list(ctx, "module_queue"):
        findings.append(result("BLOCK", "module-queue-incomplete", "full-project-docs-complete requires an empty module queue when module split is required."))
    return findings


def check_docs_scoped_completion(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not as_bool(ctx.get("trusted_full_project_baseline")):
        findings.append(result("BLOCK", "scoped-baseline-missing", "scoped-docs-complete requires a trusted full-project baseline."))
    if not any(text_value(ctx, key) for key in ("scoped_source_scope", "git_range", "commit", "tag", "module_scope")):
        findings.append(result("BLOCK", "scoped-source-scope-missing", "scoped-docs-complete requires scoped source scope, git range, commit, tag, or module scope."))
    if not as_bool(ctx.get("scoped_impact_traced")):
        findings.append(result("BLOCK", "scoped-impact-trace-missing", "scoped-docs-complete requires scoped impact trace evidence."))
    if not as_bool(ctx.get("baseline_still_trusted")):
        findings.append(result("BLOCK", "scoped-baseline-trust-missing", "scoped-docs-complete requires confirming the baseline is still trusted."))
    return findings


def check_docs_module_split(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not as_bool(ctx.get("module_split_confirmed_by_user")):
        findings.append(result("ASK", "module-split-confirmation-missing", "module-split requires user confirmation before proceeding."))
    if not as_bool(ctx.get("module_split_report_written")):
        findings.append(result("BLOCK", "module-split-report-missing", "module-split requires a written split report."))
    if not as_bool(ctx.get("module_context_index_written")):
        findings.append(result("BLOCK", "module-context-index-missing", "module-split requires a written module context index."))
    if not non_empty_list(ctx, "module_queue"):
        findings.append(result("BLOCK", "module-queue-missing", "module-split requires a nonempty module queue."))
    if not as_bool(ctx.get("paused_for_user_confirmation")):
        findings.append(result("BLOCK", "module-split-pause-missing", "module-split must pause for user confirmation."))
    return findings


def check_docs_module_governance(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if as_bool(ctx.get("module_context_report_written")):
        if not non_empty_list(ctx, "module_source_evidence"):
            findings.append(result("BLOCK", "module-source-evidence-missing", "module context reports require module source evidence."))
        if not non_empty_list(ctx, "module_completed"):
            findings.append(result("BLOCK", "module-completed-missing", "module context reports require updated module_completed."))
        if "module_queue" not in ctx:
            findings.append(result("BLOCK", "module-queue-state-missing", "module context reports require updated module_queue state."))
    if non_empty_list(ctx, "module_completed") and not as_bool(ctx.get("module_pause_after_each_completed")):
        findings.append(result("BLOCK", "module-governance-pause-missing", "module governance must pause after each completed module."))
    if as_bool(ctx.get("context_too_long")):
        if not as_bool(ctx.get("lw_final_record_written")):
            findings.append(result("BLOCK", "lw-final-record-missing", "Long-context module governance requires an LW final record."))
        if not text_value(ctx, "handoff_prompt_for_new_window"):
            findings.append(result("BLOCK", "handoff-prompt-missing", "Long-context module governance requires a new-window handoff prompt."))
    if as_bool(ctx.get("resumed_from_new_window")) and not as_bool(ctx.get("read_lw_final_record")):
        findings.append(result("BLOCK", "resumed-record-not-read", "Resumed module governance must read the LW final record first."))
    return findings


def check_docs_cleanup_backup(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    if non_empty_list(ctx, "cleanup_paths_modified") and not as_bool(ctx.get("cleanup_paths_verified_in_backup_zip")):
        return [result("BLOCK", "cleanup-backup-verification-missing", "Modified cleanup paths must be verified in the backup zip.")]
    return []


def check_l3_git(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    git_write_performed = as_bool(ctx.get("git_write_performed"))
    if not git_write_performed:
        if as_bool(ctx.get("git_plan_present")) or as_bool(ctx.get("plan_only")):
            findings.append(result("PASS", "git-plan-only", "Git plan produced; no Git writes executed."))
        else:
            findings.append(result("BLOCK", "git-plan-missing", "uth-git closeout without Git writes requires a shown plan or explicit plan-only marker."))
        return findings

    if not as_bool(ctx.get("user_git_confirmed")):
        findings.append(result("BLOCK", "git-confirmation-missing", "uth-git closeout requires explicit user confirmation for Git writes."))
    if git_write_performed and not listify(ctx.get("commands_executed")):
        findings.append(result("BLOCK", "git-commands-missing", "Git closeout requires executed commands."))
    if git_write_performed and not ctx.get("final_status"):
        findings.append(result("BLOCK", "git-final-status-missing", "Git closeout requires final branch/status summary."))
    if as_bool(ctx.get("commit_created")) and not ctx.get("commit_hash"):
        findings.append(result("BLOCK", "commit-hash-missing", "Created commit requires commit hash evidence."))
    if as_bool(ctx.get("pr_created")) and not ctx.get("pr_reference"):
        findings.append(result("BLOCK", "pr-reference-missing", "Created PR requires PR reference evidence."))
    if as_bool(ctx.get("tag_created")) and not ctx.get("tag_name"):
        findings.append(result("BLOCK", "tag-name-missing", "Created tag requires tag name evidence."))
    if as_bool(ctx.get("release_or_tag")) and not as_bool(ctx.get("changelog_ok")):
        findings.append(result("BLOCK", "release-changelog-missing", "Release/tag closeout requires changelog rule satisfaction."))
    if as_bool(ctx.get("light_dev_commit")) and not as_bool(ctx.get("lw_git_baseline_appended")):
        findings.append(result("BLOCK", "lw-git-baseline-missing", "light-dev Git closeout must append Git baseline to the existing LW final record."))
    if as_bool(ctx.get("formal_task_commit")) and not (
        as_bool(ctx.get("feedback_git_baseline_appended")) or ctx.get("feedback_git_baseline_omitted_reason")
    ):
        findings.append(result("BLOCK", "feedback-git-baseline-missing", "formal task Git closeout must append Git baseline to Feedback or state why it was omitted."))
    if as_bool(ctx.get("lw_record_included_in_git") or ctx.get("git_baseline_record_included_in_git")) and not as_bool(ctx.get("lw_second_confirmation") or ctx.get("baseline_second_confirmation")):
        findings.append(result("BLOCK", "baseline-second-confirmation-missing", "Including Git-baseline record changes in Git requires a second shown diff and confirmation."))
    if as_bool(ctx.get("formal_task_commit")) and not (ctx.get("associated_task_package") or ctx.get("associated_todo")):
        findings.append(result("BLOCK", "formal-task-link-missing", "Formal task Git closeout must record associated task package or Todo when known."))

    if as_bool(ctx.get("underlying_code_changed")):
        findings.extend(check_code_verification(ctx, force=True))
    return findings


def check_l3_context_trace(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if get_changed_files(ctx) or as_bool(ctx.get("files_modified")):
        findings.append(result("BLOCK", "context-trace-modified-files", "uth-context-trace is read-only and must not modify files."))
    if not as_bool(ctx.get("active_and_archived_separated")):
        findings.append(result("BLOCK", "evidence-separation-missing", "Context trace must separate active/current facts from historical/archive evidence."))
    if not ctx.get("recommended_next_scene"):
        findings.append(result("BLOCK", "next-scene-missing", "Context trace should recommend the next scene or state none."))
    return findings


def text_value(ctx: dict[str, Any], key: str) -> str:
    value = ctx.get(key)
    if value is None:
        return ""
    return str(value).strip()


def non_empty_list(ctx: dict[str, Any], key: str) -> bool:
    return bool(listify(ctx.get(key)))


def list_count(ctx: dict[str, Any], count_key: str, *list_keys: str) -> int:
    count = parse_int(ctx.get(count_key))
    if count is not None:
        return count
    for list_key in list_keys:
        value = ctx.get(list_key)
        count = parse_int(value)
        if count is not None:
            return count
        items = listify(value)
        if items:
            return len(items)
    return 0


def docs_completion_level(ctx: dict[str, Any]) -> str:
    return text_value(ctx, "docs_completion_level")


def takeover_scope(ctx: dict[str, Any]) -> str:
    return text_value(ctx, "takeover_scope")


def old_docs_classification_evidence_present(ctx: dict[str, Any]) -> bool:
    return any(key in ctx for key in OLD_DOC_CLASSIFICATION_KEYS)


def critical_fact_evidence_present(ctx: dict[str, Any]) -> bool:
    return any(key in ctx for key in CRITICAL_FACT_EVIDENCE_KEYS)


def takeover_handoff_paused_or_blocked(ctx: dict[str, Any]) -> bool:
    return as_bool(ctx.get("user_paused_after_onboarding")) or non_empty_list(ctx, "active_takeover_blockers")


def is_onboarding_followup_route(ctx: dict[str, Any]) -> bool:
    next_scene = text_value(ctx, "next_scene")
    next_mode = text_value(ctx, "next_mode")
    return next_scene == "uth-docs onboarding-followup" or (next_scene == "uth-docs" and next_mode == "onboarding-followup")


def project_docs_completion_claimed(ctx: dict[str, Any]) -> bool:
    return any(project_docs_completion_phrase_found(text) for text in project_docs_completion_texts(ctx))


def project_docs_completion_texts(ctx: dict[str, Any]) -> list[str]:
    texts: list[str] = []
    for item in listify(ctx.get("claims")):
        texts.append(str(item))
    for key in PROJECT_DOCS_COMPLETION_TEXT_KEYS:
        value = ctx.get(key)
        if value is None:
            continue
        for item in listify(value):
            texts.append(str(item))
    return texts


def project_docs_completion_phrase_found(text: str) -> bool:
    normalized = text.strip().lower()
    return any(phrase in normalized for phrase in PROJECT_DOCS_COMPLETION_PHRASES)


def old_docs_unclassified_count(ctx: dict[str, Any]) -> int:
    return list_count(
        ctx,
        "old_docs_unclassified_count",
        "old_docs_unclassified",
        "old_docs_unclassified_list",
    )


def has_unresolved_critical_facts(ctx: dict[str, Any]) -> bool:
    count_keys = (
        "unread_critical_count",
        "unconfirmed_critical_count",
        "unresolved_critical_count",
        "critical_unread_count",
        "critical_unconfirmed_count",
        "critical_unresolved_count",
        "unread_critical_module_count",
        "unconfirmed_entrypoint_count",
        "unconfirmed_verification_path_count",
        "unresolved_doc_conflict_count",
    )
    list_keys = (
        "unread_critical_facts",
        "unconfirmed_critical_facts",
        "unresolved_critical_facts",
        "critical_unread",
        "critical_unconfirmed",
        "critical_unresolved",
        "unread_critical_list",
        "unconfirmed_critical_list",
        "unresolved_critical_list",
        "unread_critical_modules",
        "unconfirmed_entrypoints",
        "unconfirmed_verification_paths",
        "unresolved_doc_conflicts",
    )
    return any((parse_int(ctx.get(key)) or 0) > 0 for key in count_keys) or any(
        non_empty_list(ctx, key) for key in list_keys
    )


def document_language_ready(ctx: dict[str, Any]) -> bool:
    available = as_bool(ctx.get("document_language_available") or ctx.get("project_marker_document_language"))
    if available:
        return True
    selected = as_bool(ctx.get("document_language_selected"))
    persisted = as_bool(
        ctx.get("document_language_persisted")
        or ctx.get("project_marker_document_language")
    )
    return selected and persisted


def check_closeout_report_language(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    if not closeout_language_expected(ctx):
        return []
    if as_bool(ctx.get("closeout_report_language_applied")):
        return []
    return [
        result(
            "BLOCK",
            "closeout-report-language-missing",
            "Scene closeout report must be rendered in the selected project document_language.",
        )
    ]


def closeout_language_expected(ctx: dict[str, Any]) -> bool:
    return as_bool(
        ctx.get("document_language_available")
        or ctx.get("project_marker_document_language")
        or ctx.get("document_language_selected")
        or ctx.get("document_language_persisted")
    )
