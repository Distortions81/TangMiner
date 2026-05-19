#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

cc_bin="${CC:-cc}"
cflags="${CFLAGS:--std=c99 -Wall -Wextra -Werror -O2}"
cppflags="${CPPFLAGS:--D_DEFAULT_SOURCE -D_POSIX_C_SOURCE=200112L -Istratum/include}"
ldflags="${LDFLAGS:--pthread}"

mkdir -p stratum/build

sources=(
  stratum/src/stratum_client.c
  stratum/src/stratum_miner.c
  stratum/src/stratum_json.c
  stratum/src/stratum_messages.c
  stratum/src/stratum_serial_posix.c
  stratum/src/stratum_sha256.c
  stratum/src/stratum_transport_posix.c
  stratum/src/stratum_cli.c
)

objects=()
for source in "${sources[@]}"; do
  object="stratum/build/$(basename "${source%.c}").o"
  objects+=("$object")
  if [[ ! -e "$object" || "$source" -nt "$object" ]]; then
    "$cc_bin" $cppflags $cflags -c "$source" -o "$object"
  fi
done

if [[ ! -x stratum/build/stratum-client ]]; then
  "$cc_bin" $ldflags "${objects[@]}" -o stratum/build/stratum-client
else
  newest_source=0
  for object in "${objects[@]}"; do
    if [[ "$object" -nt stratum/build/stratum-client ]]; then
      newest_source=1
      break
    fi
  done
  if ((newest_source)); then
    "$cc_bin" $ldflags "${objects[@]}" -o stratum/build/stratum-client
  fi
fi

