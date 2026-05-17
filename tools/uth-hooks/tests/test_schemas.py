from __future__ import annotations

import json
import unittest

from helpers import HOOK_ROOT


class TestHookSchemas(unittest.TestCase):
    def test_event_schema_exists_and_declares_event_type(self):
        data = json.loads((HOOK_ROOT / "schemas" / "event.schema.json").read_text(encoding="utf-8"))

        self.assertEqual(data["$schema"], "https://json-schema.org/draft/2020-12/schema")
        self.assertIn("type", data["properties"])

    def test_project_marker_schema_exists_and_matches_marker_version(self):
        data = json.loads((HOOK_ROOT / "schemas" / "project-marker.schema.json").read_text(encoding="utf-8"))

        self.assertEqual(data["properties"]["schema"]["const"], "uth-governance-project/v1")
        self.assertIn("entrypoints", data["required"])
        self.assertIn("document_language", data["properties"])

    def test_closeout_evidence_schema_exists(self):
        data = json.loads((HOOK_ROOT / "schemas" / "closeout-evidence.schema.json").read_text(encoding="utf-8"))

        self.assertIn("active_scene", data["required"])
        self.assertIn("verification", data["properties"])
        self.assertIn("document_language_persisted", data["properties"])
        self.assertIn("closeout_report_language_applied", data["properties"])
        self.assertIn("takeover_scope", data["properties"])
        self.assertIn("next_mode", data["properties"])
        self.assertIn("docs_completion_level", data["properties"])
        self.assertIn("full_project_baseline_completed", data["properties"])
        self.assertIn("module_split_report_written", data["properties"])
        self.assertIn("cleanup_paths_verified_in_backup_zip", data["properties"])
        self.assertIn("lw_final_record_written", data["properties"])
        self.assertIn("read_lw_final_record", data["properties"])


if __name__ == "__main__":
    unittest.main()
