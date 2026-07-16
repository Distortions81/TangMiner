#!/usr/bin/env bash
set -Eeuo pipefail

# Mine through the Verilated SpinalHDL RTL UART. No Tang Nano required.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

target="${TARGET:-tangnano20k}"
flow="${DEFAULT_FLOW:-}"
if [[ -z "$flow" ]]; then
  case "$target" in
    tangnano20k|tangmega138k) flow="gowin" ;;
    tangnano9k) flow="oss" ;;
    *) flow="oss" ;;
  esac
fi

default_lanes=4
default_register_pass_outputs=0
default_minimize_sha_reset=0
default_pll_kind=rpll
default_input_clock_mhz=27
default_fully_unrolled=0
default_round_skip=0
default_host_round_skip=0
default_sim_fixed_candidate=
if [[ "$target" = "tangnano20k" && "$flow" = "oss" ]]; then
  default_lanes=5
elif [[ "$target" = "tangnano20k" ]]; then
  default_lanes=6
  default_register_pass_outputs=1
  default_minimize_sha_reset=1
elif [[ "$target" = "tangmega138k" ]]; then
  default_lanes=1
  default_pll_kind=gw5
  default_input_clock_mhz=50
  default_fully_unrolled=1
  default_round_skip=1
  default_host_round_skip=1
  default_sim_fixed_candidate=2
fi

export SPINAL_LANES="${SPINAL_LANES:-$default_lanes}"
export SPINAL_PLL_KIND="${SPINAL_PLL_KIND:-$default_pll_kind}"
export SPINAL_INPUT_CLOCK_MHZ="${SPINAL_INPUT_CLOCK_MHZ:-$default_input_clock_mhz}"
export SPINAL_FULLY_UNROLLED="${SPINAL_FULLY_UNROLLED:-$default_fully_unrolled}"
export SPINAL_REGISTER_PASS_OUTPUTS="${SPINAL_REGISTER_PASS_OUTPUTS:-$default_register_pass_outputs}"
export SPINAL_MINIMIZE_SHA_RESET="${SPINAL_MINIMIZE_SHA_RESET:-$default_minimize_sha_reset}"
export SPINAL_ROUND_SKIP="${SPINAL_ROUND_SKIP:-$default_round_skip}"
export SPINAL_HOST_ROUND_SKIP="${SPINAL_HOST_ROUND_SKIP:-$default_host_round_skip}"
export SPINAL_SIM_FIXED_CANDIDATE="${SPINAL_SIM_FIXED_CANDIDATE:-$default_sim_fixed_candidate}"

scripts/helpers/build_stratum_client.sh
scripts/helpers/build_verilator_pty.sh

benchmark_seconds="${RTL_BENCHMARK_SECONDS:-2}"
lane_count="$SPINAL_LANES"
lane_period_cycles="${SPINAL_LANE_PERIOD_CYCLES:-$(
  python3 - <<'PY'
import os

def truthy(name):
    return os.environ.get(name, "0").strip().lower() in ("1", "true", "yes", "on")

if truthy("SPINAL_FULLY_UNROLLED"):
    print(1)
    raise SystemExit

base_rounds = 61 if truthy("SPINAL_ROUND_SKIP") else 64
base = (base_rounds + 1) // 2 if truthy("SPINAL_TWO_ROUNDS_PER_CYCLE") else base_rounds
mult = 3 if truthy("SPINAL_THREE_CYCLE_ROUND") else 2 if truthy("SPINAL_TWO_CYCLE_ROUND") else 1
extra = (1 if mult == 1 else 2) if truthy("SPINAL_REGISTER_PASS_OUTPUTS") else 0
if mult == 1 and (truthy("SPINAL_REGISTER_COMPRESSOR_OUTPUTS") or truthy("SPINAL_REGISTER_COMPRESS_OUTPUTS")):
    extra += 1
if mult == 1 and truthy("SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD"):
    extra += 1
print(base * mult + extra)
PY
)}"

if [[ -z "${RTL_FPGA_TARGET:-}" || -z "${RTL_SUGGEST_DIFFICULTY:-}" ]]; then
  benchmark_line="$(build/verilator-pty/Vtop --benchmark-seconds "$benchmark_seconds" --lanes "$lane_count" --lane-period-cycles "$lane_period_cycles")"
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
