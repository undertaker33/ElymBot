from __future__ import annotations

import unittest

from helpers import assert_has
from uth_hooks.l2_git import check_git_write


class TestL2Git(unittest.TestCase):
    def test_git_commit_without_uth_git_blocks(self):
        findings = check_git_write(
            {
                "active_scene": "uth-dev",
                "commands": ["git commit -m test"],
                "git_plan_present": True,
                "user_git_confirmed": True,
            }
        )

        assert_has(findings, "BLOCK", "git-scene-required")


    def test_git_commit_with_uth_git_but_no_user_confirmation_blocks(self):
        findings = check_git_write(
            {
                "active_scene": "uth-git",
                "commands": ["git commit -m test"],
                "git_plan_present": True,
            }
        )

        assert_has(findings, "BLOCK", "git-confirmation-missing")


    def test_git_branch_new_name_blocks(self):
        findings = check_git_write(
            {
                "active_scene": "uth-dev",
                "commands": ["git branch feature/new-name"],
                "git_plan_present": True,
                "user_git_confirmed": True,
            }
        )

        assert_has(findings, "BLOCK", "git-scene-required")


    def test_git_reset_hard_blocks(self):
        findings = check_git_write(
            {
                "active_scene": "uth-dev",
                "commands": ["git reset --hard HEAD"],
                "git_plan_present": True,
                "user_git_confirmed": True,
            }
        )

        assert_has(findings, "BLOCK", "git-scene-required")


    def test_extended_git_write_commands_block_outside_uth_git(self):
        commands = [
            "git restore src/app.py",
            "git rm old.py",
            "git mv old.py new.py",
            "git clean -fd",
            "git stash push",
            "git pull --rebase",
            "git fetch origin",
            "git cherry-pick abc123",
            "git revert abc123",
            "gh pr checkout 12",
            "gh pr create --fill",
            "gh pr merge 12 --squash",
            "gh release create v1.0.0",
        ]

        for command in commands:
            with self.subTest(command=command):
                findings = check_git_write(
                    {
                        "active_scene": "uth-dev",
                        "commands": [command],
                        "git_plan_present": True,
                        "user_git_confirmed": True,
                    }
                )
                assert_has(findings, "BLOCK", "git-scene-required")


    def test_readonly_git_commands_pass(self):
        findings = check_git_write(
            {
                "active_scene": "uth-dev",
                "commands": [
                    "git status --short",
                    "git diff --stat",
                    "git log -1",
                    "git show --stat",
                    "git branch --show-current",
                    "git branch -vv",
                    "git tag -l",
                    "git worktree list",
                    "gh pr view 12",
                ],
            }
        )

        assert_has(findings, "PASS", "no-git-write")


if __name__ == "__main__":
    unittest.main()
