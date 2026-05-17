#!/usr/bin/env bash
set -euo pipefail

mode="${1:-software}"
serial_port="${2:-${SERIAL_PORT:-}}"

host="${STRATUM_HOST:-tinyminer.m45core.com}"
port="${STRATUM_PORT:-3333}"
user="${STRATUM_USER:-3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45}"
pass="${STRATUM_PASS:-x}"
software_target="${SOFTWARE_FPGA_TARGET:-quick3}"
hardware_target="${HARDWARE_FPGA_TARGET:-quick21}"
software_difficulty="${SOFTWARE_SUGGEST_DIFFICULTY:-0.0000046566}"
hardware_difficulty="${HARDWARE_SUGGEST_DIFFICULTY:-0.00646187}"
max_nonces="${SOFTWARE_MAX_NONCES:-100000}"

client="stratum/build/stratum-client"
fake_fpga_pid=""
fake_fpga_log=""

cleanup() {
  if [ -n "$fake_fpga_pid" ]; then
    kill "$fake_fpga_pid" 2>/dev/null || true
    wait "$fake_fpga_pid" 2>/dev/null || true
  fi
  if [ -n "$fake_fpga_log" ]; then
    rm -f "$fake_fpga_log"
  fi
}

usage() {
  cat <<'EOF'
usage:
  scripts/stratum_mine.sh software
  scripts/stratum_mine.sh hardware /dev/ttyUSB0

environment overrides:
  STRATUM_HOST, STRATUM_PORT, STRATUM_USER, STRATUM_PASS
  SOFTWARE_FPGA_TARGET, SOFTWARE_SUGGEST_DIFFICULTY, SOFTWARE_MAX_NONCES
  HARDWARE_FPGA_TARGET, HARDWARE_SUGGEST_DIFFICULTY, SERIAL_PORT
  NO_SUBMIT=1
EOF
}

run_client() {
  local target="$1"
  local difficulty="$2"
  local port_path="$3"
  local args=(
    "$client"
    --host "$host"
    --port "$port"
    --user "$user"
    --pass "$pass"
    --serial-port "$port_path"
    --fpga-target "$target"
    --suggest-difficulty "$difficulty"
  )
  if [ "${NO_SUBMIT:-0}" = "1" ]; then
    args+=(--no-submit)
  fi
  "${args[@]}"
}

if [ "${mode}" = "-h" ] || [ "${mode}" = "--help" ]; then
  usage
  exit 0
fi

if [ ! -x "$client" ]; then
  echo "missing $client; run: make -C stratum" >&2
  exit 1
fi

case "$mode" in
  software)
    fake_fpga_log="$(mktemp)"
    trap cleanup EXIT INT TERM
    python3 stratum/tools/fake_fpga.py \
      --mode hash \
      --target "$software_target" \
      --max-nonces "$max_nonces" >"$fake_fpga_log" 2>&1 &
    fake_fpga_pid="$!"

    for _ in $(seq 1 50); do
      if grep -q '^fake_fpga_pty=' "$fake_fpga_log"; then
        serial_port="$(sed -n 's/^fake_fpga_pty=//p' "$fake_fpga_log" | tail -1)"
        break
      fi
      sleep 0.1
    done

    if [ -z "$serial_port" ]; then
      cat "$fake_fpga_log" >&2 || true
      echo "fake FPGA did not print a PTY" >&2
      exit 1
    fi

    echo "fake_fpga_pid=$fake_fpga_pid"
    echo "fake_fpga_pty=$serial_port"
    echo "software_target=$software_target suggested_difficulty=$software_difficulty"
    run_client "$software_target" "$software_difficulty" "$serial_port"
    ;;
  hardware)
    if [ -z "$serial_port" ]; then
      echo "missing serial port; use: scripts/stratum_mine.sh hardware /dev/ttyUSB0" >&2
      exit 1
    fi
    echo "hardware_port=$serial_port"
    echo "hardware_target=$hardware_target suggested_difficulty=$hardware_difficulty"
    run_client "$hardware_target" "$hardware_difficulty" "$serial_port"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
