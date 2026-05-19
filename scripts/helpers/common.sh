#!/usr/bin/env bash

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python_bin() {
  if [[ -x "$repo_root/.venv/bin/python3" ]]; then
    printf '%s\n' "$repo_root/.venv/bin/python3"
  elif [[ -x "$repo_root/.venv/bin/python" ]]; then
    printf '%s\n' "$repo_root/.venv/bin/python"
  else
    printf '%s\n' python3
  fi
}

sbt_bin() {
  if [[ -x "$repo_root/local/sbt/bin/sbt" ]]; then
    printf '%s\n' "$repo_root/local/sbt/bin/sbt"
  else
    printf '%s\n' sbt
  fi
}

load_oss_cad_suite() {
  local oss_dir="${OSS_CAD_SUITE:-}"
  if [[ -z "$oss_dir" ]]; then
    if [[ -r "$repo_root/local/oss-cad-suite/environment" ]]; then
      oss_dir="$repo_root/local/oss-cad-suite"
    elif [[ -r "$HOME/oss-cad-suite/environment" ]]; then
      oss_dir="$HOME/oss-cad-suite"
    fi
  fi

  if [[ -n "$oss_dir" ]]; then
    if [[ -r "$oss_dir/environment" ]]; then
      # shellcheck disable=SC1090
      source "$oss_dir/environment"
    fi
    export OSS_CAD_SUITE="$oss_dir"
    export PATH="$oss_dir/bin:$PATH"
  fi
}

require_command() {
  local name="$1"
  local install_hint="$2"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name is not on PATH. $install_hint" >&2
    exit 127
  fi
}

