from __future__ import annotations

import unittest

from helpers import assert_has, dispatch
from uth_hooks.l3_closeout import check_l3_closeout


class TestL3Closeout(unittest.TestCase):
    def test_code_closeout_without_compile_evidence_blocks(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-dev",
                "mode": "light-dev",
                "changed_files": ["src/app.py"],
                "lw_final_record_written": True,
            }
        )

        assert_has(findings, "BLOCK", "code-verification-evidence-missing")


    def test_chinese_complete_claim_without_evidence_blocks(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "claims": ["完成了"],
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
            }
        )

        assert_has(findings, "BLOCK", "positive-claim-evidence-missing")


    def test_warning_positive_claim_blocks(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-dev",
                "mode": "light-dev",
                "changed_files": ["src/app.py"],
                "lw_final_record_written": True,
                "claims": ["complete"],
                "verification": {
                    "compile_build_pass": True,
                    "warnings": 1,
                    "exceptions": 0,
                    "evidence": ["build completed with warning"],
                },
            }
        )

        assert_has(findings, "BLOCK", "positive-claim-with-warning-exception")

    def test_light_dev_requires_final_record_at_completion(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-dev",
                "mode": "light-dev",
                "changed_files": ["src/app.py"],
                "verification": {
                    "compile_build_pass": True,
                    "warnings": 0,
                    "exceptions": 0,
                    "evidence": ["compile pass"],
                },
            }
        )

        assert_has(findings, "BLOCK", "lw-final-missing")


    def test_onboarding_markdown_requires_document_language(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "new-project",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
            }
        )

        assert_has(findings, "BLOCK", "document-language-missing")


    def test_docs_markdown_requires_document_language(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
            }
        )

        assert_has(findings, "BLOCK", "document-language-missing")


    def test_docs_markdown_accepts_persisted_document_language(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")


    def test_docs_markdown_accepts_available_document_language(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "document_language_available": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")


    def test_selected_document_language_requires_localized_closeout_report(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "new-project",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "document_language_selected": True,
                "document_language_persisted": True,
            }
        )

        assert_has(findings, "BLOCK", "closeout-report-language-missing")


    def test_selected_document_language_accepts_localized_closeout_report(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "new-project",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "document_language_selected": True,
                "document_language_persisted": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")


    def test_light_dev_commit_requires_git_baseline_append(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-git",
                "git_write_performed": True,
                "user_git_confirmed": True,
                "commands_executed": ["git commit -m test"],
                "final_status": "clean except baseline append",
                "commit_created": True,
                "commit_hash": "abc1234",
                "light_dev_commit": True,
            }
        )

        assert_has(findings, "BLOCK", "lw-git-baseline-missing")


    def test_formal_task_commit_requires_feedback_git_baseline_append(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-git",
                "git_write_performed": True,
                "user_git_confirmed": True,
                "commands_executed": ["git commit -m test"],
                "final_status": "clean except baseline append",
                "commit_created": True,
                "commit_hash": "abc1234",
                "formal_task_commit": True,
                "associated_task_package": "docs/work/D26051601-demo",
            }
        )

        assert_has(findings, "BLOCK", "feedback-git-baseline-missing")


    def test_docs_context_change_requires_source_evidence(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
            }
        )

        assert_has(findings, "BLOCK", "context-source-missing")


    def test_uth_git_plan_only_passes(self):
        response = dispatch(
            {
                "type": "l3-closeout",
                "active_scene": "uth-git",
                "plan_only": True,
                "git_plan_present": True,
            }
        )

        self.assertEqual(response["decision"], "PASS")
        assert_has(response["findings"], "PASS", "git-plan-only")

    def test_existing_project_requires_takeover_scope(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "takeover-scope-missing")

    def test_existing_project_enable_only_does_not_require_takeover_artifacts(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "enable-only",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")

    def test_existing_project_full_takeover_preflight_requires_onboarding_followup_route(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "full-takeover",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "backup_zip_created": True,
                "handoff_snapshot_created": True,
                "old_docs_unclassified_count": 0,
                "unconfirmed_entrypoint_count": 0,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "onboarding-docs-handoff-missing")

    def test_existing_project_full_takeover_preflight_accepts_onboarding_followup_route(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "full-takeover",
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "backup_zip_created": True,
                "handoff_snapshot_created": True,
                "old_docs_unclassified_count": 0,
                "unconfirmed_entrypoint_count": 0,
                "next_scene": "uth-docs",
                "next_mode": "onboarding-followup",
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")

    def test_docs_project_completion_claim_requires_full_project_completion_level(self):
        for claim in (
            "project documentation governance is complete",
            "full-project docs complete",
            "\u9879\u76ee\u5b8c\u6574\u6587\u6863\u6cbb\u7406\u5b8c\u6210",
            "\u5168\u9879\u76ee\u6587\u6863\u6cbb\u7406\u5b8c\u6210",
        ):
            with self.subTest(claim=claim):
                findings = check_l3_closeout(
                    {
                        "active_scene": "uth-docs",
                        "claims": [claim],
                        "changed_files": ["docs/current-state.md"],
                        "utf8_guard_passed": True,
                        "project_marker_document_language": True,
                        "closeout_report_language_applied": True,
                        "verification": {"evidence": ["docs closeout evidence supplied"]},
                    }
                )

                assert_has(findings, "BLOCK", "project-docs-completion-level-missing")

    def test_docs_onboarding_followup_requires_full_project_completion(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "onboarding-followup",
                "docs_completion_level": "scoped-docs-complete",
                "changed_files": ["docs/context/README.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["read first-party source and build config"],
                "claims": ["项目完整文档治理完成"],
                "verification": {"evidence": ["docs closeout evidence supplied"]},
            }
        )

        assert_has(findings, "BLOCK", "docs-full-project-required")

    def test_docs_onboarding_followup_requires_explicit_old_doc_classification_evidence(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "onboarding-followup",
                "docs_completion_level": "full-project-docs-complete",
                "full_project_baseline_completed": True,
                "baseline_source_scope": ["src", "docs"],
                "baseline_excluded_paths": [".git", "build"],
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "changed_files": ["docs/context/README.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["full-project baseline"],
            }
        )

        assert_has(findings, "BLOCK", "old-doc-classification-evidence-missing")

    def test_docs_onboarding_followup_blocks_unclassified_old_docs(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "onboarding-followup",
                "docs_completion_level": "full-project-docs-complete",
                "full_project_baseline_completed": True,
                "baseline_source_scope": ["src", "build.gradle.kts", "settings.gradle.kts", "docs"],
                "baseline_excluded_paths": [".git", "build", ".gradle"],
                "old_docs_unclassified_count": 2,
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "changed_files": ["docs/context/README.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["full-project baseline"],
            }
        )

        assert_has(findings, "BLOCK", "old-docs-unclassified")

    def test_docs_onboarding_followup_accepts_string_zero_old_docs_count(self):
        cases = (
            {"old_docs_unclassified_count": "0", "old_docs_unclassified_list": []},
            {"old_docs_unclassified_list": "0"},
        )
        for fields in cases:
            with self.subTest(fields=fields):
                findings = check_l3_closeout(
                    {
                        "active_scene": "uth-docs",
                        "mode": "onboarding-followup",
                        "docs_completion_level": "full-project-docs-complete",
                        "full_project_baseline_completed": True,
                        "baseline_source_scope": ["src", "docs"],
                        "baseline_excluded_paths": [".git", "build"],
                        "current_state_cleaned": True,
                        "context_rebuilt_or_confirmed": True,
                        "changed_files": ["docs/context/README.md"],
                        "utf8_guard_passed": True,
                        "project_marker_document_language": True,
                        "closeout_report_language_applied": True,
                        "context_source_evidence": ["full-project baseline"],
                        **fields,
                    }
                )

                assert_has(findings, "PASS", "l3-closeout-pass")

    def test_full_project_completion_blocks_unconfirmed_entrypoint_count(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "docs_completion_level": "full-project-docs-complete",
                "full_project_baseline_completed": True,
                "baseline_source_scope": ["src", "docs"],
                "baseline_excluded_paths": [".git", "build"],
                "unconfirmed_entrypoint_count": 1,
                "changed_files": ["docs/context/README.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["full-project baseline"],
            }
        )

        assert_has(findings, "BLOCK", "docs-baseline-unresolved-facts")

    def test_full_project_completion_blocks_unresolved_baseline_fact_lists(self):
        fields = (
            ("unread_critical_modules", ["backend"]),
            ("unconfirmed_entrypoints", ["main"]),
            ("unconfirmed_verification_paths", ["python -m unittest"]),
            ("unresolved_doc_conflicts", ["README conflicts with docs/current-state.md"]),
        )
        for key, value in fields:
            with self.subTest(key=key):
                findings = check_l3_closeout(
                    {
                        "active_scene": "uth-docs",
                        "docs_completion_level": "full-project-docs-complete",
                        "full_project_baseline_completed": True,
                        "baseline_source_scope": ["src", "docs"],
                        "baseline_excluded_paths": [".git", "build"],
                        key: value,
                        "changed_files": ["docs/context/README.md"],
                        "utf8_guard_passed": True,
                        "project_marker_document_language": True,
                        "closeout_report_language_applied": True,
                        "context_source_evidence": ["full-project baseline"],
                    }
                )

                assert_has(findings, "BLOCK", "docs-baseline-unresolved-facts")

    def test_docs_cleanup_moved_paths_must_be_verified_in_backup_zip(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "onboarding-followup",
                "docs_completion_level": "full-project-docs-complete",
                "full_project_baseline_completed": True,
                "baseline_source_scope": ["src", "docs"],
                "baseline_excluded_paths": [".git", "build"],
                "old_docs_unclassified_count": 0,
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "cleanup_paths_modified": ["OLD_README.md"],
                "cleanup_paths_verified_in_backup_zip": False,
                "changed_files": ["docs/archive/OLD_README.md"],
                "archive_paths_listed": True,
                "current_state_active_cleaned": True,
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["full-project baseline"],
            }
        )

        assert_has(findings, "BLOCK", "cleanup-backup-verification-missing")

    def test_scoped_docs_completion_requires_existing_trusted_baseline(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "scoped-sync",
                "docs_completion_level": "scoped-docs-complete",
                "scoped_source_scope": "git diff HEAD~1..HEAD",
                "scoped_impact_traced": True,
                "baseline_still_trusted": True,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["git diff HEAD~1..HEAD"],
            }
        )

        assert_has(findings, "BLOCK", "scoped-baseline-missing")

    def test_scoped_docs_completion_passes_with_trusted_baseline_and_impact_trace(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "scoped-sync",
                "docs_completion_level": "scoped-docs-complete",
                "trusted_full_project_baseline": True,
                "scoped_source_scope": "git diff HEAD~1..HEAD",
                "scoped_impact_traced": True,
                "baseline_still_trusted": True,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["git diff HEAD~1..HEAD"],
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")

    def test_scoped_docs_completion_blocks_none_source_scope_values(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "scoped-sync",
                "docs_completion_level": "scoped-docs-complete",
                "trusted_full_project_baseline": True,
                "scoped_source_scope": None,
                "git_range": None,
                "commit": None,
                "tag": None,
                "scoped_impact_traced": True,
                "baseline_still_trusted": True,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["scoped impact trace"],
            }
        )

        assert_has(findings, "BLOCK", "scoped-source-scope-missing")

    def test_docs_module_split_requires_report_index_and_pause(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-split",
                "module_split_confirmed_by_user": True,
                "module_split_report_written": False,
                "module_context_index_written": False,
                "module_queue": ["backend", "frontend"],
                "paused_for_user_confirmation": False,
                "changed_files": ["docs/context/README.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["directory tree and build config"],
            }
        )

        assert_has(findings, "BLOCK", "module-split-report-missing")
        assert_has(findings, "BLOCK", "module-context-index-missing")
        assert_has(findings, "BLOCK", "module-split-pause-missing")

    def test_docs_module_governance_requires_pause_after_each_module(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-governance",
                "module_current": "backend",
                "module_completed": ["backend"],
                "module_queue": ["frontend"],
                "module_context_report_written": True,
                "module_source_evidence": ["backend/src", "backend/pom.xml"],
                "module_pause_after_each_completed": False,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["backend module source"],
            }
        )

        assert_has(findings, "BLOCK", "module-governance-pause-missing")

    def test_docs_module_governance_requires_completed_state_for_written_report(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-governance",
                "module_current": "backend",
                "module_queue": ["frontend"],
                "module_context_report_written": True,
                "module_source_evidence": ["backend/src", "backend/pom.xml"],
                "module_pause_after_each_completed": True,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["backend module source"],
            }
        )

        assert_has(findings, "BLOCK", "module-completed-missing")

    def test_docs_module_governance_requires_queue_state_for_written_report(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-governance",
                "module_current": "backend",
                "module_completed": ["backend"],
                "module_context_report_written": True,
                "module_source_evidence": ["backend/src", "backend/pom.xml"],
                "module_pause_after_each_completed": True,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["backend module source"],
            }
        )

        assert_has(findings, "BLOCK", "module-queue-state-missing")

    def test_docs_long_context_handoff_requires_lw_record_and_prompt(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-governance",
                "context_too_long": True,
                "lw_final_record_written": False,
                "handoff_prompt_for_new_window": "",
                "changed_files": ["docs/LW-Work/LW26051701-docs-module-split.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["module governance state"],
            }
        )

        assert_has(findings, "BLOCK", "lw-final-record-missing")
        assert_has(findings, "BLOCK", "handoff-prompt-missing")

    def test_docs_resumed_module_governance_requires_read_lw_record(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-docs",
                "mode": "module-governance",
                "resumed_from_new_window": True,
                "read_lw_final_record": False,
                "changed_files": ["docs/context/backend.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
                "context_source_evidence": ["module governance state"],
            }
        )

        assert_has(findings, "BLOCK", "resumed-record-not-read")

    def test_onboarding_final_takeover_requires_docs_followup_completion(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_final_closeout": True,
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "backup_zip_created": True,
                "handoff_snapshot_created": True,
                "next_scene": "uth-docs",
                "docs_followup_completed": False,
                "return_to_onboarding": True,
                "backup_zip_reported_to_user": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "takeover-docs-followup-missing")

    def test_onboarding_final_takeover_passes_with_full_docs_completion(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "full-takeover",
                "takeover_final_closeout": True,
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "backup_zip_created": True,
                "handoff_snapshot_created": True,
                "next_scene": "uth-docs",
                "docs_followup_completed": True,
                "docs_completion_level": "full-project-docs-complete",
                "return_to_onboarding": True,
                "backup_zip_reported_to_user": True,
                "old_docs_unclassified_count": 0,
                "active_takeover_blockers": [],
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "PASS", "l3-closeout-pass")

    def test_onboarding_final_takeover_requires_full_takeover_scope(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_final_closeout": True,
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "docs_followup_completed": True,
                "docs_completion_level": "full-project-docs-complete",
                "return_to_onboarding": True,
                "backup_zip_reported_to_user": True,
                "old_docs_unclassified_count": 0,
                "active_takeover_blockers": [],
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "takeover-scope-missing")

    def test_onboarding_final_takeover_requires_old_doc_classification_evidence(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "full-takeover",
                "takeover_final_closeout": True,
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "docs_followup_completed": True,
                "docs_completion_level": "full-project-docs-complete",
                "return_to_onboarding": True,
                "backup_zip_reported_to_user": True,
                "active_takeover_blockers": [],
                "current_state_cleaned": True,
                "context_rebuilt_or_confirmed": True,
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "old-doc-classification-evidence-missing")

    def test_onboarding_final_takeover_requires_current_state_and_context_evidence(self):
        findings = check_l3_closeout(
            {
                "active_scene": "uth-onboarding",
                "mode": "existing-project",
                "takeover_scope": "full-takeover",
                "takeover_final_closeout": True,
                "project_marker_written": True,
                "current_state_written": True,
                "hook_tools_copied": True,
                "docs_followup_completed": True,
                "docs_completion_level": "full-project-docs-complete",
                "return_to_onboarding": True,
                "backup_zip_reported_to_user": True,
                "old_docs_unclassified_count": 0,
                "active_takeover_blockers": [],
                "changed_files": ["docs/current-state.md"],
                "utf8_guard_passed": True,
                "project_marker_document_language": True,
                "closeout_report_language_applied": True,
            }
        )

        assert_has(findings, "BLOCK", "takeover-current-state-not-cleaned")
        assert_has(findings, "BLOCK", "takeover-context-not-rebuilt")


if __name__ == "__main__":
    unittest.main()
