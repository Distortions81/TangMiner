#!/usr/bin/env bash
set -Eeuo pipefail

# Ubuntu-friendly setup for trying TangMiner without learning the build system.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
else
  ID=""
fi

if [[ "${ID:-}" == "ubuntu" ]]; then
  apt_prefix=()
  if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    apt_prefix=(sudo)
  fi

  "${apt_prefix[@]}" apt-get update
  "${apt_prefix[@]}" apt-get install -y \
    ca-certificates curl tar gzip xz-utils \
    python3 python3-venv python3-pip \
    build-essential git openjdk-17-jre-headless
else
  echo "Non-Ubuntu system detected. Ensure python3, C/C++ build tools, git, Java, sbt, and Verilator are installed."
fi

python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements-emulation.txt

if [[ ! -x local/sbt/bin/sbt ]]; then
  sbt_version="$(sed -n 's/^sbt.version=//p' project/build.properties | head -n 1)"
  mkdir -p local
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  curl -L --fail --retry 3 \
    -o "$tmpdir/sbt.tgz" \
    "https://github.com/sbt/sbt/releases/download/v${sbt_version}/sbt-${sbt_version}.tgz"
  tar -xzf "$tmpdir/sbt.tgz" -C "$tmpdir"
  rm -rf local/sbt
  mv "$tmpdir/sbt" local/sbt
fi

if [[ ! -x local/oss-cad-suite/bin/verilator ]]; then
  platform=""
  case "$(uname -m)" in
    x86_64|amd64) platform="linux-x64" ;;
    aarch64|arm64) platform="linux-arm64" ;;
  esac

  if [[ -n "$platform" ]]; then
    mkdir -p local
    tmpdir="$(mktemp -d)"
    trap 'rm -rf "$tmpdir"' EXIT
    oss_url="$(
      PLATFORM="$platform" python3 - <<'PY'
import json
import os
import urllib.request

platform = os.environ["PLATFORM"]
with urllib.request.urlopen("https://api.github.com/repos/YosysHQ/oss-cad-suite-build/releases/latest") as response:
    release = json.load(response)

for asset in release["assets"]:
    name = asset["name"]
    if platform in name and name.endswith((".tgz", ".tar.gz")):
        print(asset["browser_download_url"])
        break
else:
    raise SystemExit(f"no OSS CAD Suite asset found for {platform}")
PY
    )"
    curl -L --fail --retry 3 -o "$tmpdir/oss-cad-suite.tgz" "$oss_url"
    tar -xzf "$tmpdir/oss-cad-suite.tgz" -C "$tmpdir"
    rm -rf local/oss-cad-suite
    mv "$tmpdir/oss-cad-suite" local/oss-cad-suite
  else
    echo "Skipping automatic OSS CAD Suite install for unsupported architecture: $(uname -m)"
  fi
fi

echo "Setup complete."
echo "Try: scripts/sim.sh"
echo "Try: scripts/mine-software.sh"
echo "Try: scripts/mine-rtl.sh"
echo "With hardware: scripts/flash-and-mine.sh /dev/ttyUSB0"
