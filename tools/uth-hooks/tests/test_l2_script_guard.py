from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from helpers import FIXTURES, assert_has
from uth_hooks.l2_script_guard import check_script_guard


class TestL2ScriptGuard(unittest.TestCase):
    def test_script_bom_blocks(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp_path = Path(raw)
            script = tmp_path / "script_bom.py"
            script.write_bytes(b"\xef\xbb\xbfprint('ok')\n")

            findings = check_script_guard({"paths": [str(script)]}, tmp_path)

        assert_has(findings, "BLOCK", "script-bom")


    def test_script_syntax_failure_blocks(self):
        findings = check_script_guard(
            {"paths": [str(FIXTURES / "script_syntax_failure.py")]},
            FIXTURES,
        )

        assert_has(findings, "BLOCK", "script-syntax-failed")


if __name__ == "__main__":
    unittest.main()
