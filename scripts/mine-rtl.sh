#!/usr/bin/env bash
set -Eeuo pipefail

# Mine through the Verilated SpinalHDL RTL UART. No Tang Nano required.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

scripts/helpers/build_stratum_client.sh
scripts/helpers/build_verilator_pty.sh
exec scripts/helpers/stratum_mine.sh rtl "$@"

