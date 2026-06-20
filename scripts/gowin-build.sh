#!/usr/bin/env bash
set -euo pipefail

GOWIN_SH=${GOWIN_SH:-gw_sh}
GOWIN_SH_RUNNER=${GOWIN_SH_RUNNER:-}
GOWIN_PROJECT_DIR=${GOWIN_PROJECT_DIR:?GOWIN_PROJECT_DIR is required}
GOWIN_PROJECT_NAME=${GOWIN_PROJECT_NAME:?GOWIN_PROJECT_NAME is required}
DEVICE=${DEVICE:?DEVICE is required}
GOWIN_DEVICE_VERSION=${GOWIN_DEVICE_VERSION:-}
TOP=${TOP:?TOP is required}
CST=${CST:?CST is required}
SDC=${SDC:?SDC is required}
RTL_SRC=${RTL_SRC:?RTL_SRC is required}
GOWIN_PLACE_OPTION=${GOWIN_PLACE_OPTION:-2}
GOWIN_ROUTE_OPTION=${GOWIN_ROUTE_OPTION:-1}
GOWIN_ROUTE_MAXFAN=${GOWIN_ROUTE_MAXFAN:-23}
GOWIN_CLOCK_ROUTE_ORDER=${GOWIN_CLOCK_ROUTE_ORDER:-1}
GOWIN_CORRECT_HOLD=${GOWIN_CORRECT_HOLD:-0}
GOWIN_REPLICATE_RESOURCES=${GOWIN_REPLICATE_RESOURCES:-1}

if ! command -v "${GOWIN_SH}" >/dev/null 2>&1; then
    echo "error: ${GOWIN_SH} not found. Install Official Gowin EDA and set GOWIN_SH=/path/to/gw_sh." >&2
    exit 127
fi

case "${GOWIN_SH}" in
    */*) GOWIN_SH="$(cd -- "$(dirname -- "${GOWIN_SH}")" && pwd)/$(basename -- "${GOWIN_SH}")" ;;
esac
if [ -n "${GOWIN_SH_RUNNER}" ]; then
    case "${GOWIN_SH_RUNNER}" in
        */*) GOWIN_SH_RUNNER="$(cd -- "$(dirname -- "${GOWIN_SH_RUNNER}")" && pwd)/$(basename -- "${GOWIN_SH_RUNNER}")" ;;
    esac
fi

run_gowin_sh() {
    if [ -n "${GOWIN_SH_RUNNER}" ] && [ -x "${GOWIN_SH_RUNNER}" ]; then
        "${GOWIN_SH_RUNNER}" "${GOWIN_SH}" "$@"
    else
        "${GOWIN_SH}" "$@"
    fi
}

final_project_dir="${GOWIN_PROJECT_DIR}"
final_project_leaf="$(basename -- "${final_project_dir}")"
work_parent_dir="${final_project_dir}.work.$$"
work_project_dir="${work_parent_dir}/${final_project_leaf}"

cleanup_work_project_dir() {
    rm -rf "${work_parent_dir}"
}
trap cleanup_work_project_dir EXIT

publish_work_project_dir() {
    rm -rf "${final_project_dir}.prev"
    if [ -e "${final_project_dir}" ]; then
        mv "${final_project_dir}" "${final_project_dir}.prev"
    fi
    mv "${work_project_dir}" "${final_project_dir}"
    rmdir "${work_parent_dir}" 2>/dev/null || true
    rm -rf "${final_project_dir}.prev"
    trap - EXIT
}

rm -rf "${work_parent_dir}"
GOWIN_PROJECT_DIR="${work_project_dir}"
mkdir -p "${GOWIN_PROJECT_DIR}/constr"
cp "${CST}" "${GOWIN_PROJECT_DIR}/constr/$(basename "${CST}")"
cp "${SDC}" "${GOWIN_PROJECT_DIR}/constr/$(basename "${SDC}")"

for src in ${RTL_SRC}; do
    mkdir -p "${GOWIN_PROJECT_DIR}/$(dirname "${src}")"
    cp "${src}" "${GOWIN_PROJECT_DIR}/${src}"
done

project_parent="$(cd -- "$(dirname -- "${GOWIN_PROJECT_DIR}")" && pwd)"
project_leaf="$(basename -- "${GOWIN_PROJECT_DIR}")"
tcl="${project_parent}/${project_leaf}.tcl"
{
    printf 'create_project -name %s -dir . -pn %s' "${GOWIN_PROJECT_NAME}" "${DEVICE}"
    if [ -n "${GOWIN_DEVICE_VERSION}" ]; then
        printf ' -device_version %s' "${GOWIN_DEVICE_VERSION}"
    fi
    printf ' -force\n'
    printf 'add_file -type cst constr/%s\n' "$(basename "${CST}")"
    printf 'add_file -type sdc constr/%s\n' "$(basename "${SDC}")"
    for src in ${RTL_SRC}; do
        printf 'add_file -type verilog %s\n' "${src}"
    done
    printf 'set_option -output_base_name %s\n' "${GOWIN_PROJECT_NAME}"
    printf 'set_option -top_module %s\n' "${TOP}"
    echo 'set_option -looplimit 1000000'
} > "${tcl}"

syn_tcl="${GOWIN_PROJECT_DIR}/run-syn.tcl"
{
    printf 'open_project %s.gprj\n' "${GOWIN_PROJECT_NAME}"
    echo 'run syn'
    echo 'run close'
} > "${syn_tcl}"

pnr_tcl="${GOWIN_PROJECT_DIR}/run-pnr.tcl"
{
    printf 'open_project %s.gprj\n' "${GOWIN_PROJECT_NAME}"
    echo 'run pnr'
    echo 'run close'
} > "${pnr_tcl}"

(
    cd "${project_parent}"
    run_gowin_sh "$(basename -- "${tcl}")"
)

(
    cd "${GOWIN_PROJECT_DIR}"
    run_gowin_sh "$(basename -- "${syn_tcl}")"
)

process_config="${GOWIN_PROJECT_DIR}/impl/${GOWIN_PROJECT_NAME}_process_config.json"
if [ -f "${process_config}" ]; then
    python3 - \
        "${process_config}" \
        "${GOWIN_PLACE_OPTION}" \
        "${GOWIN_ROUTE_OPTION}" \
        "${GOWIN_ROUTE_MAXFAN}" \
        "${GOWIN_CLOCK_ROUTE_ORDER}" \
        "${GOWIN_CORRECT_HOLD}" \
        "${GOWIN_REPLICATE_RESOURCES}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="ascii"))
data["MSPI"] = True
data["Generate_Plain_Text_Timing_Report"] = True
data["Place_Option"] = sys.argv[2]
data["Route_Option"] = sys.argv[3]
data["Route_Maxfan"] = sys.argv[4]
data["Clock_Route_Order"] = int(sys.argv[5])
data["Correct_Hold_Violation"] = sys.argv[6] != "0"
data["Replicate_Resources"] = sys.argv[7] != "0"
data["Run_Timing_Driven"] = True
path.write_text(json.dumps(data, indent=1) + "\n", encoding="ascii")
PY
fi

(
    cd "${GOWIN_PROJECT_DIR}"
    run_gowin_sh "$(basename -- "${pnr_tcl}")"
)

publish_work_project_dir
