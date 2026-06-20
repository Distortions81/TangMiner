#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "usage: scripts/gowin-sh-env.sh /path/to/gw_sh [args...]" >&2
    exit 2
fi

gw_sh="$1"
shift
gowin_bin="$(cd -- "$(dirname -- "${gw_sh}")" && pwd)"
gowin_ide="${gowin_bin%/bin}"

if [ -d "${gowin_ide}/plugins/qt" ]; then
    export QT_QPA_PLATFORM_PLUGIN_PATH="${QT_QPA_PLATFORM_PLUGIN_PATH:-${gowin_ide}/plugins/qt}"
else
    export QT_QPA_PLATFORM_PLUGIN_PATH="${QT_QPA_PLATFORM_PLUGIN_PATH:-/usr/lib/x86_64-linux-gnu/qt5/plugins}"
fi
export QT_XCB_GL_INTEGRATION="${QT_XCB_GL_INTEGRATION:-none}"
export QT_OPENGL="${QT_OPENGL:-software}"
export QT_QUICK_BACKEND="${QT_QUICK_BACKEND:-software}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
export LD_LIBRARY_PATH="${gowin_ide}/lib:${LD_LIBRARY_PATH:-}"

if [ "${GOWIN_NO_XVFB:-0}" != "1" ] && command -v xvfb-run >/dev/null 2>&1; then
    export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"
    exec xvfb-run -a -s "${GOWIN_XVFB_SERVER_ARGS:--screen 0 1280x1024x24 +extension GLX +render}" "${gw_sh}" "$@"
fi

export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-offscreen}"
exec "${gw_sh}" "$@"
