from __future__ import annotations

import unittest

from helpers import FIXTURES, assert_has, load_config
from uth_hooks.l2_utf8_doc import check_utf8_doc


class TestL2Utf8Doc(unittest.TestCase):
    def test_docs_markdown_mojibake_blocks(self):
        findings = check_utf8_doc(
            {"paths": [str(FIXTURES / "mojibake.md")]},
            load_config(),
            FIXTURES,
        )

        assert_has(findings, "BLOCK", "mojibake-marker")


    def test_unbalanced_markdown_fence_blocks(self):
        findings = check_utf8_doc(
            {"paths": [str(FIXTURES / "unbalanced_fence.md")]},
            load_config(),
            FIXTURES,
        )

        assert_has(findings, "BLOCK", "markdown-fence-unbalanced")


if __name__ == "__main__":
    unittest.main()
