#!/usr/bin/env python3
"""UTH hook runner entrypoint.

The public CLI path stays stable here. Gate implementations live in the
uth_hooks package so skills and project docs do not need to reference internal
module names.
"""

from __future__ import annotations

import sys

from uth_hooks.runner import main


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
