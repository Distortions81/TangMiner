#!/usr/bin/env bash
set -Eeuo pipefail

# Run the SpinalHDL RTL UART tests with cocotb/Verilator.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

for arg in "$@"; do
  case "$arg" in
    [A-Za-z_]*=*)
      export "$arg"
      ;;
    *)
      echo "usage: $0 [NAME=value ...]" >&2
      exit 2
      ;;
  esac
done

# shellcheck disable=SC1091
source scripts/helpers/common.sh

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
default_hardware_clock_hz=27000000
default_pll_kind=rpll
default_input_clock_mhz=27
default_fully_unrolled=0
default_round_skip=0
default_host_round_skip=0
default_sim_fixed_candidate=
if [[ "$target" = "tangnano20k" && "$flow" = "oss" ]]; then
  default_lanes=5
  default_hardware_clock_hz=54000000
elif [[ "$target" = "tangnano20k" ]]; then
  default_lanes=6
  default_register_pass_outputs=1
  default_minimize_sha_reset=1
  default_hardware_clock_hz=67500000
elif [[ "$target" = "tangmega138k" ]]; then
  default_lanes=1
  default_hardware_clock_hz=100000000
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
export HARDWARE_CLOCK_HZ="${HARDWARE_CLOCK_HZ:-$default_hardware_clock_hz}"

scripts/helpers/build_spinal_sim.sh
load_oss_cad_suite

py="$(python_bin)"
require_command verilator "Install OSS CAD Suite or run scripts/setup.sh."
"$py" -c "import cocotb" >/dev/null 2>&1 || {
  echo "cocotb is not installed. Run scripts/setup.sh." >&2
  exit 1
}

export PYTHONPATH="$repo_root/scripts/tools:${PYTHONPATH:-}"
export CLKS_PER_BIT=8
export LANE_COUNT="$SPINAL_LANES"
export EXPECTED_LANE_PERIOD_CYCLES="${EXPECTED_LANE_PERIOD_CYCLES:-$(
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

cd sim/cocotb
"$py" - <<'PY'
import os
from pathlib import Path
from cocotb_tools.runner import get_runner

repo = Path.cwd().parents[1]
runner = get_runner("verilator")
runner.build(
    verilog_sources=[repo / "build" / "spinal-sim" / "top.v"],
    hdl_toplevel="top",
    build_dir="sim_build",
    always=True,
)
runner.test(
    hdl_toplevel="top",
    test_module="test_top_uart",
    build_dir="sim_build",
    results_xml="results.xml",
    extra_env={
        "PYTHONPATH": f"{repo / 'scripts' / 'tools'}:{os.environ.get('PYTHONPATH', '')}",
        "CLKS_PER_BIT": os.environ.get("CLKS_PER_BIT", "8"),
        "LANE_COUNT": os.environ.get("LANE_COUNT", "6"),
        "HARDWARE_CLOCK_HZ": os.environ.get("HARDWARE_CLOCK_HZ", "67500000"),
        "EXPECTED_LANE_PERIOD_CYCLES": os.environ.get("EXPECTED_LANE_PERIOD_CYCLES", "65"),
        "HOST_ROUND_SKIP_PAYLOAD": os.environ.get("SPINAL_HOST_ROUND_SKIP", "0"),
        "FIXED_CANDIDATE_MODE": os.environ.get("SPINAL_SIM_FIXED_CANDIDATE", ""),
        "STRICT_NONCE_CHECKS": os.environ.get("SPINAL_SIM_STRICT_NONCE_CHECKS", "0"),
    },
)
PY
