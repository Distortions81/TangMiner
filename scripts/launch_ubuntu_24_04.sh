#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/launch_ubuntu_24_04.sh [options] [command] [args...]

Launch TangMiner software-only emulation/simulation on Ubuntu 24.04.

Options:
  --target TARGET          Target board label (default: tangnano9k)
  --sim SIM                cocotb simulator: verilator or icarus (default: verilator)
  --oss-cad-suite DIR      OSS CAD Suite directory (default: local/oss-cad-suite, then ~/oss-cad-suite)
  --sbt-dir DIR            sbt directory (default: local/sbt)
  --no-venv               Do not activate .venv
  -h, --help              Show this help

Commands:
  emu-smoke               Run the Python protocol emulator smoke test (default)
  emu-pty                 Launch a pseudo-terminal UART emulator
  sim-cocotb              Run top-level UART cocotb RTL simulation
  sim-cocotb-icarus       Run top-level UART cocotb RTL simulation with Icarus
  sim-cocotb-spinal       Generate simulation SpinalHDL Verilog and run cocotb
  make ARGS...            Run make with the configured environment
  shell                   Start an interactive shell with the environment loaded

Examples:
  scripts/launch_ubuntu_24_04.sh emu-smoke
  scripts/launch_ubuntu_24_04.sh emu-pty
  scripts/launch_ubuntu_24_04.sh sim-cocotb
  scripts/launch_ubuntu_24_04.sh --sim icarus sim-cocotb
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

target="${TARGET:-tangnano9k}"
sim="${SIM:-verilator}"
oss_cad_suite="${OSS_CAD_SUITE:-}"
sbt_dir="${SBT_HOME:-}"
use_venv=1

while (($#)); do
  case "$1" in
    --target)
      target="${2:?missing value for --target}"
      shift 2
      ;;
    --sim)
      sim="${2:?missing value for --sim}"
      shift 2
      ;;
    --oss-cad-suite)
      oss_cad_suite="${2:?missing value for --oss-cad-suite}"
      shift 2
      ;;
    --sbt-dir)
      sbt_dir="${2:?missing value for --sbt-dir}"
      shift 2
      ;;
    --no-venv)
      use_venv=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

command_name="${1:-emu-smoke}"
if (($#)); then
  shift
fi

case "$target" in
  tangnano9k|tangnano20k) ;;
  tangnano90|9k|tn9k)
    target="tangnano9k"
    ;;
  *)
    echo "unsupported target '$target'; use tangnano9k or tangnano20k" >&2
    exit 2
    ;;
esac

case "$sim" in
  verilator|icarus) ;;
  *)
    echo "unsupported simulator '$sim'; use verilator or icarus" >&2
    exit 2
    ;;
esac

if [[ -z "$oss_cad_suite" ]]; then
  if [[ -r "$repo_root/local/oss-cad-suite/environment" ]]; then
    oss_cad_suite="$repo_root/local/oss-cad-suite"
  elif [[ -r "$HOME/oss-cad-suite/environment" ]]; then
    oss_cad_suite="$HOME/oss-cad-suite"
  fi
fi

if [[ -n "$oss_cad_suite" ]]; then
  if [[ -r "$oss_cad_suite/environment" ]]; then
    # shellcheck disable=SC1090
    source "$oss_cad_suite/environment"
    export OSS_CAD_SUITE="$oss_cad_suite"
  else
    echo "warning: OSS CAD Suite environment not found at $oss_cad_suite/environment" >&2
    export OSS_CAD_SUITE="$oss_cad_suite"
  fi
fi

if [[ -z "$sbt_dir" && -x "$repo_root/local/sbt/bin/sbt" ]]; then
  sbt_dir="$repo_root/local/sbt"
fi

if [[ -n "$sbt_dir" ]]; then
  if [[ -x "$sbt_dir/bin/sbt" ]]; then
    export SBT_HOME="$sbt_dir"
    export PATH="$sbt_dir/bin:$PATH"
  else
    echo "warning: sbt not found at $sbt_dir/bin/sbt" >&2
  fi
fi

if ((use_venv)); then
  if [[ ! -r "$repo_root/.venv/bin/activate" ]]; then
    echo ".venv is missing. Run: scripts/install_ubuntu_24_04.sh --skip-oss-cad" >&2
    exit 1
  fi
  # shellcheck disable=SC1091
  source "$repo_root/.venv/bin/activate"
fi

export TARGET="$target"
export SIM="$sim"

case "$command_name" in
  emu-smoke)
    exec make emu-smoke TARGET="$target"
    ;;
  emu-pty)
    exec make emu-pty TARGET="$target" EMU_TARGET="$target" EMU_ARGS="$*"
    ;;
  sim-cocotb)
    exec make sim-cocotb TARGET="$target" SIM="$sim"
    ;;
  sim-cocotb-icarus)
    exec make sim-cocotb TARGET="$target" SIM=icarus
    ;;
  sim-cocotb-spinal)
    exec make sim-cocotb-spinal TARGET="$target" SIM="$sim"
    ;;
  make)
    exec make "$@"
    ;;
  shell)
    echo "TangMiner environment loaded."
    echo "TARGET=$target"
    echo "SIM=$sim"
    if [[ -n "${OSS_CAD_SUITE:-}" ]]; then
      echo "OSS_CAD_SUITE=$OSS_CAD_SUITE"
    fi
    exec "${SHELL:-bash}"
    ;;
  *)
    echo "unknown command: $command_name" >&2
    usage >&2
    exit 2
    ;;
esac
