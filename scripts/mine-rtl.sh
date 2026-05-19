#!/usr/bin/env bash
set -Eeuo pipefail

# Mine through the Verilated SpinalHDL RTL UART. No Tang Nano required.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

scripts/helpers/build_stratum_client.sh
scripts/helpers/build_verilator_pty.sh

benchmark_seconds="${RTL_BENCHMARK_SECONDS:-2}"
lane_count="${SPINAL_LANES:-5}"

if [[ -z "${RTL_FPGA_TARGET:-}" || -z "${RTL_SUGGEST_DIFFICULTY:-}" ]]; then
  benchmark_line="$(build/verilator-pty/Vtop --benchmark-seconds "$benchmark_seconds" --lanes "$lane_count")"
  echo "$benchmark_line"

  eval "$(
    BENCHMARK_LINE="$benchmark_line" \
    RTL_TARGET_SHARES_PER_MINUTE="${RTL_TARGET_SHARES_PER_MINUTE:-6}" \
    python3 - <<'PY'
import os
import re

line = os.environ["BENCHMARK_LINE"]
shares_per_minute = float(os.environ["RTL_TARGET_SHARES_PER_MINUTE"])
match = re.search(r"hashes_per_second=([0-9.]+)", line)
if not match:
    raise SystemExit("could not parse RTL benchmark output")

hps = float(match.group(1))
if hps >= (2 ** 21) / 8.0:
    target = "quick21"
elif hps >= (2 ** 14) / 8.0:
    target = "quick14"
else:
    target = "quick3"

difficulty = max(hps * 60.0 / (shares_per_minute * 4294967296.0), 1e-9)
print(f'auto_target="{target}"')
print(f'auto_difficulty="{difficulty:.8g}"')
print(f'auto_hps="{hps:.2f}"')
PY
  )"

  export RTL_FPGA_TARGET="${RTL_FPGA_TARGET:-$auto_target}"
  export RTL_SUGGEST_DIFFICULTY="${RTL_SUGGEST_DIFFICULTY:-$auto_difficulty}"
  echo "rtl_autotune hps=$auto_hps target=$RTL_FPGA_TARGET suggested_difficulty=$RTL_SUGGEST_DIFFICULTY"
fi

exec scripts/helpers/stratum_mine.sh rtl "$@"
