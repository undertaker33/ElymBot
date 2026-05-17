from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from helpers import assert_has, dispatch


class TestL0Router(unittest.TestCase):
    def test_missing_project_marker_keeps_uth_silent(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp_path = Path(raw)
            response = dispatch(
                {
                    "type": "l0-router",
                    "engineering_action": True,
                    "requested_scene": "uth-dev",
                },
                project=tmp_path,
            )

        self.assertEqual(response["decision"], "PASS")
        self.assertEqual(response["route_action"], "silent")
        self.assertEqual(response["schema_version"], "uth-hook-result/v1")
        assert_has(response["findings"], "PASS", "uth-silent-without-project-marker")


    def test_enabled_project_with_engineering_action_requires_scene(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp_path = Path(raw)
            marker_dir = tmp_path / ".uth-governance"
            marker_dir.mkdir()
            (marker_dir / "project.json").write_text(
                json.dumps(
                    {
                        "schema": "uth-governance-project/v1",
                        "enabled": True,
                        "onboarded_at": "2026-05-16T00:00:00+08:00",
                        "onboarding_mode": "new-project",
                        "docs_root": "docs",
                        "entrypoints": {
                            "agent": "AGENTS.md",
                            "docs": "docs/README.md",
                            "current_state": "docs/current-state.md",
                            "context": "docs/context/README.md",
                        },
                    }
                ),
                encoding="utf-8",
            )

            response = dispatch({"type": "l0-router", "engineering_action": True}, project=tmp_path)

        self.assertEqual(response["decision"], "BLOCK")
        assert_has(response["findings"], "BLOCK", "scene-required")


    def test_explicit_onboarding_can_run_without_marker(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp_path = Path(raw)
            response = dispatch(
                {
                    "type": "l0-router",
                    "engineering_action": True,
                    "explicit_onboarding": True,
                },
                project=tmp_path,
            )

        self.assertEqual(response["decision"], "PASS")
        self.assertEqual(response["route_action"], "enter-onboarding")
        assert_has(response["findings"], "PASS", "explicit-onboarding")


if __name__ == "__main__":
    unittest.main()
