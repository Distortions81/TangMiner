#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

# shellcheck disable=SC1091
source scripts/helpers/common.sh

scripts/helpers/build_spinal_sim.sh
load_oss_cad_suite
require_command verilator "Install OSS CAD Suite or run scripts/setup.sh."

mkdir -p build/verilator-pty

if [[ -x build/verilator-pty/Vtop && build/verilator-pty/Vtop -nt sim/verilator_uart_pty.cpp && build/verilator-pty/Vtop -nt build/spinal-sim/top.v ]]; then
  exit 0
fi

verilator --cc --exe --build \
  --Mdir build/verilator-pty \
  -top-module top \
  -CFLAGS "-DCLKS_PER_BIT=8" \
  build/spinal-sim/top.v sim/verilator_uart_pty.cpp

