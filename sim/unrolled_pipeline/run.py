#!/usr/bin/env python3

import os
from pathlib import Path

from cocotb_tools.runner import get_runner


def main():
    repo = Path(__file__).resolve().parents[2]
    sim_dir = Path(__file__).resolve().parent
    build_dir = sim_dir / "sim_build"
    rtl = repo / "build" / "unrolled-pipeline-sim" / "BitcoinHashUnrolledPipelineSimTop.v"

    runner = get_runner(os.environ.get("SIM", "verilator"))
    runner.build(
        verilog_sources=[rtl],
        hdl_toplevel="BitcoinHashUnrolledPipelineSimTop",
        build_dir=build_dir,
        always=True,
    )
    runner.test(
        hdl_toplevel="BitcoinHashUnrolledPipelineSimTop",
        test_module="test_unrolled_pipeline",
        build_dir=build_dir,
        results_xml="results.xml",
        extra_env={
            "PYTHONPATH": (
                f"{sim_dir}:{repo / 'scripts' / 'tools'}:"
                f"{os.environ.get('PYTHONPATH', '')}"
            )
        },
    )


if __name__ == "__main__":
    main()
