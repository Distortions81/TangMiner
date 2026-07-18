#!/usr/bin/env python3

import os
from pathlib import Path

from cocotb_tools.runner import get_runner


def main():
    repo = Path(__file__).resolve().parents[2]
    sim_dir = Path(__file__).resolve().parent
    build_dir = sim_dir / ("sim_build_half" if os.environ.get("UNROLLED_VARIANT") == "half" else "sim_build")
    variant = os.environ.get("UNROLLED_VARIANT", "full")
    top = "BitcoinHashHalfUnrolledPipelineSimTop" if variant == "half" else "BitcoinHashUnrolledPipelineSimTop"
    rtl_dir = "half-unrolled-pipeline-sim" if variant == "half" else "unrolled-pipeline-sim"
    rtl = repo / "build" / rtl_dir / f"{top}.v"

    runner = get_runner(os.environ.get("SIM", "verilator"))
    runner.build(
        verilog_sources=[rtl],
        hdl_toplevel=top,
        build_dir=build_dir,
        always=True,
    )
    runner.test(
        hdl_toplevel=top,
        test_module="test_unrolled_pipeline",
        build_dir=build_dir,
        results_xml="results.xml",
        extra_env={
            "UNROLLED_VARIANT": variant,
            "PYTHONPATH": (
                f"{sim_dir}:{repo / 'scripts' / 'tools'}:"
                f"{os.environ.get('PYTHONPATH', '')}"
            )
        },
    )


if __name__ == "__main__":
    main()
