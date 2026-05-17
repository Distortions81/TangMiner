#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/install_ubuntu_24_04.sh [options]

Set up TangMiner's software-only Ubuntu 24.04 environment.

Options:
  --target TARGET          Target board label for verification (default: tangnano9k)
  --install-dir DIR        OSS CAD Suite install dir (default: local/oss-cad-suite)
  --sbt-dir DIR            sbt install dir (default: local/sbt)
  --skip-apt              Do not attempt apt package installation
  --skip-sbt              Do not download/install sbt
  --skip-oss-cad          Do not download/install OSS CAD Suite
  --skip-verify           Do not run post-install smoke checks
  --force-sbt             Replace an existing sbt install dir
  --force-oss-cad         Replace an existing OSS CAD Suite install dir
  -h, --help              Show this help

The script avoids root-only installs unless passwordless sudo or root is
available. OSS CAD Suite is installed inside the repo by default and is ignored
by git.
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

tmp_paths=()
cleanup_tmp_paths() {
  for tmp_path in "${tmp_paths[@]}"; do
    rm -rf "$tmp_path"
  done
}
trap cleanup_tmp_paths EXIT

target="${TARGET:-tangnano9k}"
install_dir="${OSS_CAD_SUITE:-local/oss-cad-suite}"
sbt_dir="${SBT_HOME:-local/sbt}"
skip_apt=0
skip_sbt=0
skip_oss_cad=0
skip_verify=0
force_sbt=0
force_oss_cad=0

while (($#)); do
  case "$1" in
    --target)
      target="${2:?missing value for --target}"
      shift 2
      ;;
    --install-dir)
      install_dir="${2:?missing value for --install-dir}"
      shift 2
      ;;
    --sbt-dir)
      sbt_dir="${2:?missing value for --sbt-dir}"
      shift 2
      ;;
    --skip-apt)
      skip_apt=1
      shift
      ;;
    --skip-sbt)
      skip_sbt=1
      shift
      ;;
    --skip-oss-cad)
      skip_oss_cad=1
      shift
      ;;
    --skip-verify)
      skip_verify=1
      shift
      ;;
    --force-sbt)
      force_sbt=1
      shift
      ;;
    --force-oss-cad)
      force_oss_cad=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

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

if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
  if [[ "${ID:-}" != "ubuntu" || "${VERSION_ID:-}" != "24.04" ]]; then
    echo "warning: this script is tuned for Ubuntu 24.04; detected ${PRETTY_NAME:-unknown OS}" >&2
  fi
fi

run_apt=0
if ((skip_apt == 0)); then
  if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    run_apt=1
    apt_prefix=()
  elif command -v sudo >/dev/null 2>&1 && sudo -n true >/dev/null 2>&1; then
    run_apt=1
    apt_prefix=(sudo)
  else
    echo "Skipping apt package installation; root/passwordless sudo is unavailable."
    echo "If needed, install: ca-certificates curl tar gzip xz-utils python3 python3-venv python3-pip make gcc g++ openjdk-17-jre-headless git"
  fi
fi

if ((run_apt)); then
  echo "Installing Ubuntu base packages..."
  "${apt_prefix[@]}" apt-get update
  "${apt_prefix[@]}" apt-get install -y \
    ca-certificates \
    curl \
    tar \
    gzip \
    xz-utils \
    python3 \
    python3-venv \
    python3-pip \
    make \
    gcc \
    g++ \
    openjdk-17-jre-headless \
    git
fi

echo "Installing Python emulation dependencies into .venv..."
make setup-emulation

if ((skip_sbt == 0)); then
  sbt_version="$(sed -n 's/^sbt.version=//p' project/build.properties | head -n 1)"
  if [[ -z "$sbt_version" ]]; then
    echo "could not determine sbt version from project/build.properties" >&2
    exit 1
  fi

  if [[ -e "$sbt_dir" && "$force_sbt" -eq 0 ]]; then
    if [[ -x "$sbt_dir/bin/sbt" ]]; then
      echo "sbt already exists at $sbt_dir; keeping it."
    else
      echo "$sbt_dir exists but does not look like sbt. Re-run with --force-sbt to replace it." >&2
      exit 1
    fi
  else
    tmpdir_sbt="$(mktemp -d)"
    tmp_paths+=("$tmpdir_sbt")

    sbt_url="https://github.com/sbt/sbt/releases/download/v${sbt_version}/sbt-${sbt_version}.tgz"
    echo "Downloading sbt $sbt_version from $sbt_url"
    curl -L --fail --retry 3 -o "$tmpdir_sbt/sbt.tgz" "$sbt_url"
    tar -xzf "$tmpdir_sbt/sbt.tgz" -C "$tmpdir_sbt"

    mkdir -p "$(dirname "$sbt_dir")"
    rm -rf "$sbt_dir"
    mv "$tmpdir_sbt/sbt" "$sbt_dir"
    echo "Installed sbt to $sbt_dir"
  fi
fi

platform=""
case "$(uname -m)" in
  x86_64|amd64)
    platform="linux-x64"
    ;;
  aarch64|arm64)
    platform="linux-arm64"
    ;;
  *)
    echo "unsupported architecture for automatic OSS CAD Suite install: $(uname -m)" >&2
    echo "Install OSS CAD Suite manually, then set OSS_CAD_SUITE before launching." >&2
    skip_oss_cad=1
    ;;
esac

if ((skip_oss_cad == 0)); then
  if [[ -e "$install_dir" && "$force_oss_cad" -eq 0 ]]; then
    if [[ -x "$install_dir/bin/verilator" || -r "$install_dir/environment" ]]; then
      echo "OSS CAD Suite already exists at $install_dir; keeping it."
    else
      echo "$install_dir exists but does not look like OSS CAD Suite. Re-run with --force-oss-cad to replace it." >&2
      exit 1
    fi
  else
    tmpdir="$(mktemp -d)"
    tmp_paths+=("$tmpdir")

    echo "Finding latest OSS CAD Suite release for $platform..."
    oss_url="$(
      PLATFORM="$platform" python3 - <<'PY'
import json
import os
import sys
import urllib.request

platform = os.environ["PLATFORM"]
with urllib.request.urlopen("https://api.github.com/repos/YosysHQ/oss-cad-suite-build/releases/latest") as response:
    release = json.load(response)

matches = [
    asset["browser_download_url"]
    for asset in release["assets"]
    if platform in asset["name"] and asset["name"].endswith((".tgz", ".tar.gz"))
]

if not matches:
    raise SystemExit(f"no OSS CAD Suite asset found for {platform}")
print(matches[0])
PY
    )"

    echo "Downloading $oss_url"
    curl -L --fail --retry 3 -o "$tmpdir/oss-cad-suite.tgz" "$oss_url"
    tar -xzf "$tmpdir/oss-cad-suite.tgz" -C "$tmpdir"

    mkdir -p "$(dirname "$install_dir")"
    rm -rf "$install_dir"
    mv "$tmpdir/oss-cad-suite" "$install_dir"
    echo "Installed OSS CAD Suite to $install_dir"
  fi
fi

if ((skip_verify == 0)); then
  echo "Running software emulator smoke test..."
  scripts/launch_ubuntu_24_04.sh --target "$target" --oss-cad-suite "$install_dir" emu-smoke

  if [[ -x "$install_dir/bin/verilator" || -x "$install_dir/bin/iverilog" ]]; then
    echo "Toolchain installed. Run RTL simulation with:"
    echo "  scripts/launch_ubuntu_24_04.sh --target $target sim-cocotb"
  fi
fi

echo "Ubuntu software-emulation setup complete."
echo "Launch with: scripts/launch_ubuntu_24_04.sh --target $target sim-cocotb"
