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
export LANE_COUNT="${SPINAL_LANES:-5}"
export HARDWARE_CLOCK_HZ="${HARDWARE_CLOCK_HZ:-100286000}"
round_skip="${SPINAL_ROUND_SKIP:-0}"
round_skip="${round_skip,,}"
if [[ "$round_skip" == "1" || "$round_skip" == "true" || "$round_skip" == "yes" || "$round_skip" == "on" ]]; then
  export EXPECTED_LANE_PERIOD_CYCLES="${EXPECTED_LANE_PERIOD_CYCLES:-61}"
else
  export EXPECTED_LANE_PERIOD_CYCLES="${EXPECTED_LANE_PERIOD_CYCLES:-64}"
fi

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
        "LANE_COUNT": os.environ.get("LANE_COUNT", "4"),
        "HARDWARE_CLOCK_HZ": os.environ.get("HARDWARE_CLOCK_HZ", "100286000"),
        "EXPECTED_LANE_PERIOD_CYCLES": os.environ.get("EXPECTED_LANE_PERIOD_CYCLES", "0"),
    },
)
PY
