#!/usr/bin/env bash
set -Eeuo pipefail

# Mine through the Python fake FPGA. No Tang Nano required.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

scripts/helpers/build_stratum_client.sh
exec scripts/helpers/stratum_mine.sh software "$@"

