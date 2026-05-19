#!/usr/bin/env bash
set -Eeuo pipefail

# Mine through a real Tang Nano UART device.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

serial_port="${1:-${SERIAL_PORT:-}}"
if [[ -z "$serial_port" ]]; then
  echo "missing serial port" >&2
  echo "usage: scripts/mine-hardware.sh /dev/ttyUSB0" >&2
  exit 2
fi

scripts/helpers/build_stratum_client.sh
exec scripts/helpers/stratum_mine.sh hardware "$serial_port"

