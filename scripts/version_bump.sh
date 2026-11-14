#!/usr/bin/env bash
# Compatibility entry point: local edits only; never resets, commits or pushes.
set -euo pipefail
exec python3 "$(cd "$(dirname "$0")" && pwd)/version.py" bump "${1:-patch}"
