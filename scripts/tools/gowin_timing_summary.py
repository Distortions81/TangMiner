#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: scripts/tools/gowin_timing_summary.py path/to/report.tr", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8", errors="replace")
    setup = re.search(r"<Numbers of Setup Violated Endpoints>:(\d+)", text)
    hold = re.search(r"<Numbers of Hold Violated Endpoints>:(\d+)", text)
    clock_rows = re.findall(
        r"^\s*\d+\s+([A-Za-z_][A-Za-z0-9_]*)\s+([0-9.]+)\(MHz\)\s+([0-9.]+)\(MHz\)",
        text,
        flags=re.MULTILINE,
    )
    tns_rows = re.findall(
        r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s+(setup|hold)\s+(-?[0-9.]+)\s+(\d+)\s*$",
        text,
        flags=re.MULTILINE,
    )

    print(f"Gowin timing report: {path}")
    failed = False
    if setup:
        setup_count = int(setup.group(1))
        failed = failed or setup_count != 0
        print(f"setup violated endpoints: {setup_count}")
    if hold:
        hold_count = int(hold.group(1))
        failed = failed or hold_count != 0
        print(f"hold violated endpoints: {hold_count}")
    if clock_rows:
        for clock, target, fmax in clock_rows:
            target_mhz = float(target)
            fmax_mhz = float(fmax)
            margin = fmax_mhz - target_mhz
            failed = failed or margin < -0.001
            print(f"{clock}: target {target_mhz:.3f} MHz, fmax {fmax_mhz:.3f} MHz, margin {margin:.3f} MHz")
    else:
        for line in text.splitlines():
            if "MHz" in line and ("Fmax" in line or "Frequency" in line or "systemClock" in line):
                print(line.strip())
    for clock, analysis, tns, endpoints in tns_rows:
        if float(tns) != 0.0 or int(endpoints) != 0:
            print(f"{clock} {analysis}: TNS {float(tns):.3f}, endpoints {int(endpoints)}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
