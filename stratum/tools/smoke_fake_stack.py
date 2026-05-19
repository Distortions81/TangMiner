#!/usr/bin/env python3
"""Run fake pool + fake or RTL FPGA + C client as a single integration smoke test."""

import argparse
import re
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
CLIENT = REPO_ROOT / "stratum" / "build" / "stratum-client"
RTL_FPGA = REPO_ROOT / "build" / "verilator-pty" / "Vtop"


def read_until(pattern: str, proc: subprocess.Popen, label: str, limit: int = 50) -> re.Match:
    compiled = re.compile(pattern)
    lines = []
    for _ in range(limit):
        line = proc.stdout.readline()
        if not line:
            continue
        lines.append(line.rstrip())
        match = compiled.search(line)
        if match:
            return match
    raise RuntimeError(f"{label} did not print expected endpoint; saw: {lines}")


def terminate(proc: subprocess.Popen) -> None:
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke-test the local fake Stratum/UART stack")
    parser.add_argument("--client", default=str(CLIENT))
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--backend", choices=("fake", "rtl"), default="fake")
    parser.add_argument("--fpga-mode", choices=("fast", "hash"), default="fast")
    parser.add_argument("--pool-difficulty", type=float, default=0.00000001)
    args = parser.parse_args()

    pool = subprocess.Popen(
        [
            sys.executable,
            str(REPO_ROOT / "stratum" / "tools" / "fake_pool.py"),
            "--notify-count",
            "1",
            "--close-after-submits",
            "1",
            "--run-seconds",
            "5",
            "--difficulty",
            str(args.pool_difficulty),
        ],
        cwd=REPO_ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if args.backend == "rtl":
        fpga_cmd = [str(RTL_FPGA)]
        fpga_pattern = r"rtl_fpga_pty=(/dev/pts/\d+)"
        fpga_label = "rtl_fpga"
    else:
        fpga_cmd = [
            sys.executable,
            str(REPO_ROOT / "stratum" / "tools" / "fake_fpga.py"),
            "--mode",
            args.fpga_mode,
        ]
        fpga_pattern = r"fake_fpga_pty=(/dev/pts/\d+)"
        fpga_label = "fake_fpga"

    fpga = subprocess.Popen(
        fpga_cmd,
        cwd=REPO_ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )

    try:
        pool_match = read_until(r"fake_pool_addr=([^:]+):(\d+)", pool, "fake_pool")
        fpga_match = read_until(fpga_pattern, fpga, fpga_label)
        client = subprocess.run(
            [
                args.client,
                "--host",
                pool_match.group(1),
                "--port",
                pool_match.group(2),
                "--user",
                "tester.worker",
                "--pass",
                "x",
                "--serial-port",
                fpga_match.group(1),
                "--fpga-target",
                "quick3",
                "--quiet",
            ],
            cwd=REPO_ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=args.timeout,
        )
        submitted = "submitted job=" in client.stdout
        print(f"client_return={client.returncode}")
        print(f"submitted_seen={str(submitted).lower()}")
        if not submitted:
            print(client.stdout)
            print(client.stderr, file=sys.stderr)
            return 1
        return 0
    finally:
        terminate(pool)
        terminate(fpga)


if __name__ == "__main__":
    raise SystemExit(main())
