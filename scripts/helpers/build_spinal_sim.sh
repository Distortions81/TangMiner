#!/usr/bin/env bash
set -Eeuo pipefail

# Generates simulation-tuned SpinalHDL Verilog at build/spinal-sim/top.v.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

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
default_pll_kind=rpll
default_input_clock_mhz=27
default_fully_unrolled=0
default_half_unrolled=0
default_round_skip=0
default_host_round_skip=0
default_fixed_candidate=
if [[ "$target" = "tangnano20k" && "$flow" = "oss" ]]; then
  default_lanes=5
elif [[ "$target" = "tangnano20k" ]]; then
  default_lanes=6
  default_register_pass_outputs=1
  default_minimize_sha_reset=1
elif [[ "$target" = "tangmega138k" ]]; then
  default_lanes=28
  default_pll_kind=gw5
  default_input_clock_mhz=50
  default_fully_unrolled=0
  default_register_pass_outputs=1
  default_minimize_sha_reset=1
  default_round_skip=0
  default_host_round_skip=0
  default_fixed_candidate=2
fi

lanes="${SPINAL_LANES:-$default_lanes}"
pll_kind="${SPINAL_PLL_KIND:-$default_pll_kind}"
input_clock_mhz="${SPINAL_INPUT_CLOCK_MHZ:-$default_input_clock_mhz}"
shared_k="${SPINAL_SHARED_K:-0}"
fully_unrolled="${SPINAL_FULLY_UNROLLED:-$default_fully_unrolled}"
half_unrolled="${SPINAL_HALF_UNROLLED:-$default_half_unrolled}"
enable_echo="${SPINAL_SIM_ENABLE_ECHO:-1}"
enable_hardcoded="${SPINAL_SIM_ENABLE_HARDCODED:-1}"
fixed_candidate="${SPINAL_SIM_FIXED_CANDIDATE:-$default_fixed_candidate}"
wide_lanes="${SPINAL_WIDE_LANES:-0}"
share_job_state="${SPINAL_SHARE_JOB_STATE:-0}"
lane_start_stagger="${SPINAL_LANE_START_STAGGER:-0}"
register_pass_outputs="${SPINAL_REGISTER_PASS_OUTPUTS:-$default_register_pass_outputs}"
register_compressor_outputs="${SPINAL_REGISTER_COMPRESSOR_OUTPUTS:-${SPINAL_REGISTER_COMPRESS_OUTPUTS:-0}}"
register_first_pass_feedforward="${SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD:-0}"
two_cycle_round="${SPINAL_TWO_CYCLE_ROUND:-0}"
three_cycle_round="${SPINAL_THREE_CYCLE_ROUND:-0}"
two_rounds_per_cycle="${SPINAL_TWO_ROUNDS_PER_CYCLE:-0}"
register_round_constant="${SPINAL_REGISTER_ROUND_CONSTANT:-0}"
minimize_sha_reset="${SPINAL_MINIMIZE_SHA_RESET:-$default_minimize_sha_reset}"
split_sha_clock="${SPINAL_SPLIT_SHA_CLOCK:-0}"
round_skip="${SPINAL_ROUND_SKIP:-$default_round_skip}"
csa_round="${SPINAL_CSA_ROUND:-0}"
host_round_skip="${SPINAL_HOST_ROUND_SKIP:-$default_host_round_skip}"

require_command java "Install OpenJDK or run scripts/setup.sh."
sbt="$(sbt_bin)"
require_command "$sbt" "Install sbt or run scripts/setup.sh."

mkdir -p build/spinal-sim

config="build/spinal-sim/.config"
tmp="$config.tmp"
{
  echo "target=$target"
  echo "lanes=$lanes"
  echo "pll_kind=$pll_kind"
  echo "input_clock_mhz=$input_clock_mhz"
  echo "clks_per_bit=8"
  echo "shared_k=$shared_k"
  echo "fully_unrolled=$fully_unrolled"
  echo "half_unrolled=$half_unrolled"
  echo "enable_echo=$enable_echo"
  echo "enable_hardcoded=$enable_hardcoded"
  echo "fixed_candidate=$fixed_candidate"
  echo "wide_lanes=$wide_lanes"
  echo "share_job_state=$share_job_state"
  echo "lane_start_stagger=$lane_start_stagger"
  echo "register_pass_outputs=$register_pass_outputs"
  echo "register_compressor_outputs=$register_compressor_outputs"
  echo "register_first_pass_feedforward=$register_first_pass_feedforward"
  echo "two_cycle_round=$two_cycle_round"
  echo "three_cycle_round=$three_cycle_round"
  echo "two_rounds_per_cycle=$two_rounds_per_cycle"
  echo "register_round_constant=$register_round_constant"
  echo "minimize_sha_reset=$minimize_sha_reset"
  echo "split_sha_clock=$split_sha_clock"
  echo "round_skip=$round_skip"
  echo "csa_round=$csa_round"
  echo "host_round_skip=$host_round_skip"
} > "$tmp"

if [[ -e "$config" ]] &&
  cmp -s "$tmp" "$config" &&
  [[ -e build/spinal-sim/top.v ]] &&
  [[ build/spinal-sim/top.v -nt src/main/scala/tangminer/TangMiner.scala ]] &&
  [[ build/spinal-sim/top.v -nt build.sbt ]] &&
  [[ build/spinal-sim/top.v -nt project/build.properties ]]; then
  rm "$tmp"
  exit 0
fi

mv "$tmp" "$config"
TANGMINER_PLL_KIND="$pll_kind" \
TANGMINER_INPUT_CLOCK_MHZ="$input_clock_mhz" \
TANGMINER_LANES="$lanes" \
TANGMINER_CLKS_PER_BIT=8 \
TANGMINER_SHARED_K="$shared_k" \
TANGMINER_FULLY_UNROLLED="$fully_unrolled" \
TANGMINER_HALF_UNROLLED="$half_unrolled" \
TANGMINER_ENABLE_ECHO="$enable_echo" \
TANGMINER_ENABLE_HARDCODED="$enable_hardcoded" \
TANGMINER_FIXED_CANDIDATE="$fixed_candidate" \
TANGMINER_WIDE_LANES="$wide_lanes" \
TANGMINER_SHARE_JOB_STATE="$share_job_state" \
TANGMINER_LANE_START_STAGGER="$lane_start_stagger" \
TANGMINER_REGISTER_PASS_OUTPUTS="$register_pass_outputs" \
TANGMINER_REGISTER_COMPRESSOR_OUTPUTS="$register_compressor_outputs" \
TANGMINER_REGISTER_FIRST_PASS_FEEDFORWARD="$register_first_pass_feedforward" \
TANGMINER_TWO_CYCLE_ROUND="$two_cycle_round" \
TANGMINER_THREE_CYCLE_ROUND="$three_cycle_round" \
TANGMINER_TWO_ROUNDS_PER_CYCLE="$two_rounds_per_cycle" \
TANGMINER_REGISTER_ROUND_CONSTANT="$register_round_constant" \
TANGMINER_MINIMIZE_SHA_RESET="$minimize_sha_reset" \
TANGMINER_SPLIT_SHA_CLOCK="$split_sha_clock" \
TANGMINER_ROUND_SKIP="$round_skip" \
TANGMINER_CSA_ROUND="$csa_round" \
TANGMINER_HOST_ROUND_SKIP="$host_round_skip" \
  "$sbt" "runMain tangminer.GenerateSimVerilog"
