from __future__ import annotations

import unittest

from helpers import assert_has
from uth_hooks.l1_process import check_l1_process


class TestL1Process(unittest.TestCase):
    def test_scene_missing_blocks(self):
        findings = check_l1_process({"engineering_action": True})

        assert_has(findings, "BLOCK", "scene-ambiguous")


    def test_ambiguity_unresolved_blocks(self):
        findings = check_l1_process(
            {
                "active_scene": "uth-design",
                "ambiguity": {"present": True},
                "require_uth_sp_decision": False,
            }
        )

        assert_has(findings, "BLOCK", "ambiguity-unresolved")


    def test_worker_without_prompt_blocks(self):
        findings = check_l1_process(
            {
                "active_scene": "uth-dev",
                "mode": "formal-dev",
                "worker": {"role": "worker"},
                "require_uth_sp_decision": False,
            }
        )

        assert_has(findings, "BLOCK", "worker-prompt-missing")


if __name__ == "__main__":
    unittest.main()
