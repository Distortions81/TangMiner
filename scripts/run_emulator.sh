#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

echo "Starting TangMiner software UART emulator..."
echo "Keep this process running and use the printed /dev/pts/* path as the serial port."
echo "Protocol responses are sent to that PTY, not echoed in this terminal."

launcher_args=()
emulator_args=()
auto_benchmark=1

while (($#)); do
  case "$1" in
    --target|--sim|--oss-cad-suite|--sbt-dir)
      launcher_args+=("$1" "${2:?missing value for $1}")
      shift 2
      ;;
    --no-venv)
      launcher_args+=("$1")
      shift
      ;;
    --no-auto-benchmark)
      auto_benchmark=0
      shift
      ;;
    --)
      shift
      emulator_args+=("$@")
      break
      ;;
    *)
      emulator_args+=("$1")
      shift
      ;;
  esac
done

if ((auto_benchmark)); then
  echo "An automatic software benchmark job will start after the PTY is ready."
  emulator_args=(--auto-benchmark "${emulator_args[@]}")
else
  echo "Automatic software benchmark disabled."
fi

exec scripts/launch_ubuntu_24_04.sh "${launcher_args[@]}" emu-pty "${emulator_args[@]}"
