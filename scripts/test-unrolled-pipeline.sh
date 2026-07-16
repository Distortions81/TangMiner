#!/usr/bin/env bash
set -Eeuo pipefail

# Generate and directly verify the fully unrolled SHA256d pipeline, without the
# UART/parser layers used by the board-level simulation.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# shellcheck disable=SC1091
source scripts/helpers/common.sh

require_command java "Install OpenJDK or run scripts/setup.sh."
sbt="$(sbt_bin)"
require_command "$sbt" "Install sbt or run scripts/setup.sh."

mkdir -p build/unrolled-pipeline-sim
TANGMINER_UNROLLED_SIM_DIR=build/unrolled-pipeline-sim \
  "$sbt" "runMain tangminer.GenerateUnrolledPipelineSimVerilog"

load_oss_cad_suite
py="$(python_bin)"
require_command verilator "Install OSS CAD Suite or run scripts/setup.sh."
"$py" -c "import cocotb, cocotb_tools.runner" >/dev/null 2>&1 || {
  echo "cocotb is not installed. Run scripts/setup.sh." >&2
  exit 1
}

export PYTHONPATH="$repo_root/sim/unrolled_pipeline:$repo_root/scripts/tools:${PYTHONPATH:-}"
"$py" sim/unrolled_pipeline/run.py
