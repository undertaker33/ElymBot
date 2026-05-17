from __future__ import annotations

import unittest

from helpers import REPO_ROOT, assert_has, load_config
from uth_hooks.l2_write_scope import check_file_write


class TestL2WriteScope(unittest.TestCase):
    def test_write_skills_in_uth_dev_blocks(self):
        findings = check_file_write(
            {"active_scene": "uth-dev", "paths": ["skills/uth-dev/SKILL.md"]},
            load_config(),
            REPO_ROOT,
        )

        assert_has(findings, "BLOCK", "skill-write-blocked")


    def test_write_docs_context_in_uth_dev_blocks(self):
        findings = check_file_write(
            {"active_scene": "uth-dev", "paths": ["docs/context/10-backend.md"]},
            load_config(),
            REPO_ROOT,
        )

        assert_has(findings, "BLOCK", "context-write-blocked")


    def test_forbidden_writes_override_allowed_writes(self):
        findings = check_file_write(
            {
                "active_scene": "uth-dev",
                "paths": ["src/generated/out.py"],
                "allowed_writes": ["src/**"],
                "forbidden_writes": ["src/generated/**"],
            },
            load_config(),
            REPO_ROOT,
        )

        assert_has(findings, "BLOCK", "write-forbidden")


    def test_uth_git_can_append_formal_feedback_baseline(self):
        findings = check_file_write(
            {
                "active_scene": "uth-git",
                "paths": ["docs/work/D26051601-demo/11-D26051601-T01-feedback-demo.md"],
            },
            load_config(),
            REPO_ROOT,
        )

        assert_has(findings, "PASS", "write-allowed")


if __name__ == "__main__":
    unittest.main()
