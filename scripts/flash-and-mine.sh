#!/usr/bin/env bash
set -Eeuo pipefail

# Build the active SpinalHDL bitstream, program a Tang Nano board, then run the
# C Stratum host against the board UART.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

program_action="flash"
serial_port_arg=""

usage() {
  cat <<'EOF'
usage:
  scripts/flash-and-mine.sh [--flash|--load] /dev/ttyUSB0

options:
  --flash       write the bitstream to FPGA flash (default)
  --load        load the bitstream to SRAM for this power cycle
  -h, --help    show this help

environment:
  TARGET=tangnano20k|tangnano9k
  SPINAL_LANES=N
  OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
  STRATUM_HOST, STRATUM_PORT, STRATUM_USER, STRATUM_PASS
  HARDWARE_FPGA_TARGET, HARDWARE_SUGGEST_DIFFICULTY, NO_SUBMIT=1, VERBOSE=1
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --flash)
      program_action="flash"
      shift
      ;;
    --load)
      program_action="load"
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
    -*)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      if [[ -n "$serial_port_arg" ]]; then
        echo "unexpected extra argument: $1" >&2
        usage >&2
        exit 2
      fi
      serial_port_arg="$1"
      shift
      ;;
  esac
done

if [[ $# -gt 0 ]]; then
  if [[ -n "$serial_port_arg" ]]; then
    echo "unexpected extra argument: $1" >&2
    usage >&2
    exit 2
  fi
  serial_port_arg="$1"
  shift
fi

if [[ $# -gt 0 ]]; then
  echo "unexpected extra argument: $1" >&2
  usage >&2
  exit 2
fi

serial_port="${serial_port_arg:-${SERIAL_PORT:-}}"

if [[ -z "$serial_port" ]]; then
  echo "missing serial port" >&2
  usage >&2
  exit 2
fi

echo "target=${TARGET:-tangnano20k} action=$program_action serial_port=$serial_port"
make build
make "$program_action"
exec scripts/mine-hardware.sh "$serial_port"
