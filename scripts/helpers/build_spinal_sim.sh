#!/usr/bin/env bash
set -Eeuo pipefail

# Generates simulation-tuned SpinalHDL Verilog at build/spinal-sim/top.v.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

# shellcheck disable=SC1091
source scripts/helpers/common.sh

target="${TARGET:-tangnano20k}"
lanes="${SPINAL_LANES:-4}"
shared_k="${SPINAL_SHARED_K:-1}"
enable_echo="${SPINAL_ENABLE_ECHO:-1}"
enable_hardcoded="${SPINAL_ENABLE_HARDCODED:-1}"
fixed_candidate="${SPINAL_FIXED_CANDIDATE:-}"

require_command java "Install OpenJDK or run scripts/setup.sh."
sbt="$(sbt_bin)"
require_command "$sbt" "Install sbt or run scripts/setup.sh."

mkdir -p build/spinal-sim

config="build/spinal-sim/.config"
tmp="$config.tmp"
{
  echo "target=$target"
  echo "lanes=$lanes"
  echo "clks_per_bit=8"
  echo "shared_k=$shared_k"
  echo "enable_echo=$enable_echo"
  echo "enable_hardcoded=$enable_hardcoded"
  echo "fixed_candidate=$fixed_candidate"
} > "$tmp"

if [[ -e "$config" ]] && cmp -s "$tmp" "$config" && [[ -e build/spinal-sim/top.v ]]; then
  rm "$tmp"
  exit 0
fi

mv "$tmp" "$config"
TANGMINER_LANES="$lanes" \
TANGMINER_CLKS_PER_BIT=8 \
TANGMINER_SHARED_K="$shared_k" \
TANGMINER_ENABLE_ECHO="$enable_echo" \
TANGMINER_ENABLE_HARDCODED="$enable_hardcoded" \
TANGMINER_FIXED_CANDIDATE="$fixed_candidate" \
  "$sbt" "runMain tangminer.GenerateSimVerilog"

