from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

from helpers import REPO_ROOT


def load_check_assets_sync():
    script = REPO_ROOT / "scripts" / "check_assets_sync.py"
    if not script.exists():
        raise unittest.SkipTest("repository-level scripts/check_assets_sync.py is not available")
    spec = importlib.util.spec_from_file_location("check_assets_sync", script)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TestAssetsSyncScript(unittest.TestCase):
    def test_compare_identical_directories_passes(self):
        module = load_check_assets_sync()
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            left = root / "left"
            right = root / "right"
            left.mkdir()
            right.mkdir()
            (left / "hook.py").write_text("print('same')\n", encoding="utf-8")
            (right / "hook.py").write_text("print('same')\n", encoding="utf-8")

            mismatches = module.compare_dirs(left, right)

        self.assertEqual(mismatches, [])

    def test_compare_different_directories_reports_mismatch(self):
        module = load_check_assets_sync()
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            left = root / "left"
            right = root / "right"
            left.mkdir()
            right.mkdir()
            (left / "hook.py").write_text("print('left')\n", encoding="utf-8")
            (right / "hook.py").write_text("print('right')\n", encoding="utf-8")

            mismatches = module.compare_dirs(left, right)

        self.assertEqual(len(mismatches), 1)
        self.assertEqual(mismatches[0].path, "hook.py")


if __name__ == "__main__":
    unittest.main()
