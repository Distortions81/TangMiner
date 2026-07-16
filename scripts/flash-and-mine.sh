#!/usr/bin/env bash
set -Eeuo pipefail

# Build the active bitstream, program a Tang Nano board, then run the C Stratum
# host against the board UART.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

program_action="flash"
serial_port_arg=""

usage() {
  cat <<'EOF'
usage:
  scripts/flash-and-mine.sh [--flash|--load] /dev/ttyUSB1

options:
  --flash       write the bitstream to FPGA flash (default)
  --load        load the bitstream to SRAM for this power cycle
  -h, --help    show this help

environment:
  TARGET=tangnano20k|tangnano9k|tangmega138k
  BITSTREAM_FLOW=gowin|oss (default: gowin for 20K/138K, oss for 9K)
  DEFAULT_FLOW=gowin|oss (Makefile flow, used when BITSTREAM_FLOW is unset)
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
target="${TARGET:-tangnano20k}"
flow="${BITSTREAM_FLOW:-${DEFAULT_FLOW:-}}"

if [[ -z "$serial_port" ]]; then
  echo "missing serial port" >&2
  usage >&2
  exit 2
fi

if [[ -z "$flow" ]]; then
  case "$target" in
    tangnano20k|tangmega138k) flow="gowin" ;;
    tangnano9k) flow="oss" ;;
    *)
      echo "unsupported target: $target" >&2
      usage >&2
      exit 2
      ;;
  esac
fi

case "$flow" in
  gowin|oss) ;;
  *)
    echo "unsupported BITSTREAM_FLOW: $flow" >&2
    usage >&2
    exit 2
    ;;
esac

echo "target=$target flow=$flow action=$program_action serial_port=$serial_port"

make_args=()
if [[ -n "${OPENFPGALOADER:-}" ]]; then
  make_args+=(OPENFPGALOADER="$OPENFPGALOADER")
fi
make_args+=(TARGET="$target")
make_args+=(DEFAULT_FLOW="$flow")

if [[ "$flow" = "gowin" ]]; then
  exec make "${make_args[@]}" "gowin-${program_action}-and-mine" SERIAL_PORT="$serial_port"
fi

make "${make_args[@]}" oss-build
make "${make_args[@]}" "oss-$program_action"
exec make "${make_args[@]}" mine-hardware SERIAL_PORT="$serial_port"
