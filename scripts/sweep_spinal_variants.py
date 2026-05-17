#!/usr/bin/env python3
import argparse
import json
import os
import re
import select
import signal
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROFILES = ("90m", "100m286", "111m", "120m")
DEFAULT_LANES = (4, 5, 6)
PROFILE_CLOCK_MHZ = {
    "90m": 90.000,
    "100m286": 100.286,
    "111m": 111.000,
    "120m": 120.000,
}
UTIL_LIMITS = {
    "LUT4": 85,
    "DFF": 80,
}


@dataclass
class SweepResult:
    lanes: int
    profile: str
    clock_mhz: float
    modeled_hps: float
    log_path: str
    returncode: int
    timing_status: str = "UNKNOWN"
    fmax_mhz: float = 0.0
    requested_mhz: float = 0.0
    margin_percent: float = 0.0
    lut4_percent: int = -1
    dff_percent: int = -1
    bsram_used: int = 0
    bsram_total: int = 0
    routing_arcs: int = 0
    route_time_seconds: float = 0.0
    elapsed_seconds: float = 0.0
    timed_out: bool = False
    failure_reason: str = ""
    selected_ok: bool = False


def parse_csv_ints(value):
    return [int(item) for item in value.split(",") if item.strip()]


def parse_csv_strings(value):
    return [item.strip() for item in value.split(",") if item.strip()]


def tool_defaults():
    env = os.environ.copy()
    local_sbt = REPO_ROOT / "local" / "sbt" / "bin" / "sbt"
    local_oss = REPO_ROOT / "local" / "oss-cad-suite"
    if local_sbt.exists() and "SBT" not in env:
        env["SBT"] = str(local_sbt)
    if (local_oss / "bin" / "yosys").exists() and "OSS_CAD_SUITE" not in env:
        env["OSS_CAD_SUITE"] = str(local_oss)
    return env


def build_variant(args, lanes, profile):
    clock_mhz = PROFILE_CLOCK_MHZ[profile]
    variant = f"lanes{lanes}_{profile}"
    build_dir = REPO_ROOT / args.build_root / variant
    build_dir.mkdir(parents=True, exist_ok=True)
    log_path = build_dir / "build.log"
    modeled_hps = clock_mhz * 1_000_000.0 * lanes / 64.0

    result = SweepResult(
        lanes=lanes,
        profile=profile,
        clock_mhz=clock_mhz,
        modeled_hps=modeled_hps,
        log_path=str(log_path.relative_to(REPO_ROOT)),
        returncode=0,
    )

    cmd = [
        "make",
        "-B",
        "build-spinal",
        "TARGET=tangnano20k",
        f"SPINAL_LANES={lanes}",
        f"SPINAL_CLOCK_PROFILE={profile}",
        f"BUILD={args.build_root}/{variant}",
    ]
    if args.dry_run:
        print(" ".join(cmd))
        return result

    env = tool_defaults()
    timeout_seconds = args.variant_timeout_seconds
    print(f"\n==> lanes={lanes} profile={profile} clock={clock_mhz:.3f}MHz")
    print(f"    log: {log_path.relative_to(REPO_ROOT)}")
    start_time = time.monotonic()
    timed_out = False
    output_lines = []
    proc = subprocess.Popen(
        cmd,
        cwd=REPO_ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        start_new_session=True,
        bufsize=1,
    )
    assert proc.stdout is not None
    with log_path.open("w") as log_file:
        try:
            while True:
                readable, _, _ = select.select([proc.stdout], [], [], 1.0)
                if readable:
                    line = proc.stdout.readline()
                    if line:
                        output_lines.append(line)
                        log_file.write(line)
                        log_file.flush()
                        if should_print_progress(line):
                            print(line, end="")
                    elif proc.poll() is not None:
                        break
                elif proc.poll() is not None:
                    break

                if timeout_seconds > 0 and time.monotonic() - start_time > timeout_seconds:
                    timed_out = True
                    message = (
                        f"ERROR: sweep variant timed out after {timeout_seconds}s; "
                        "terminating build process group.\n"
                    )
                    output_lines.append(message)
                    log_file.write(message)
                    log_file.flush()
                    print(message, end="")
                    os.killpg(proc.pid, signal.SIGTERM)
                    try:
                        proc.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(proc.pid, signal.SIGKILL)
                        proc.wait()
                    break
        finally:
            for line in proc.stdout:
                output_lines.append(line)
                log_file.write(line)
                if should_print_progress(line):
                    print(line, end="")

    result.elapsed_seconds = time.monotonic() - start_time
    if timed_out:
        result.returncode = 124
        result.timed_out = True
        result.failure_reason = "timeout"
    else:
        result.returncode = proc.returncode if proc.returncode is not None else 1

    stdout_text = "".join(output_lines)
    parse_build_log(result, stdout_text)
    if result.returncode != 0 and not result.failure_reason:
        result.failure_reason = classify_failure(stdout_text)
    return result


def should_print_progress(line):
    if line.startswith("==>"):
        return True
    progress_patterns = (
        "Running custom HCLK placer",
        "Running main analytical placer",
        "Unable to find legal placement",
        "Info: Routing",
        "remaining|       time spent",
        "batch(sec) total(sec)",
        "Routing complete",
        "Router1 time",
        "Router2 time",
        "Max frequency for clock",
        "Device utilisation",
        "LUT4:",
        "DFF:",
        "BSRAM:",
        "ERROR:",
    )
    if any(pattern in line for pattern in progress_patterns):
        return True
    return bool(re.match(r"Info:\s+[0-9]+\s+\|", line))


def classify_failure(text):
    if "Unable to find legal placement" in text:
        return "placement"
    if "timed out" in text:
        return "timeout"
    if "failed to route" in text.lower() or "Routing failed" in text:
        return "routing"
    if "ERROR:" in text:
        return "error"
    return "failed"


def parse_build_log(result, text):
    timing_matches = re.findall(
        r"Max frequency for clock '[^']+':\s+([0-9.]+) MHz \((PASS|FAIL) at ([0-9.]+) MHz\)",
        text,
    )
    if timing_matches:
        fmax, status, requested = timing_matches[-1]
        result.fmax_mhz = float(fmax)
        result.timing_status = status
        result.requested_mhz = float(requested)
        if result.requested_mhz > 0:
            result.margin_percent = (result.fmax_mhz / result.requested_mhz - 1.0) * 100.0

    for name, used, total, percent in re.findall(r"Info:\s+([A-Za-z0-9_]+):\s+([0-9]+)/\s*([0-9]+)\s+([0-9]+)%", text):
        if name == "LUT4":
            result.lut4_percent = int(percent)
        elif name == "DFF":
            result.dff_percent = int(percent)
        elif name == "BSRAM":
            result.bsram_used = int(used)
            result.bsram_total = int(total)

    routing_arcs = re.findall(r"Info:\s+Routing\s+([0-9]+)\s+arcs\.", text)
    if routing_arcs:
        result.routing_arcs = int(routing_arcs[-1])

    route_times = re.findall(r"Info:\s+Router[12] time\s+([0-9.]+)s", text)
    if route_times:
        result.route_time_seconds = float(route_times[-1])

    result.selected_ok = (
        result.returncode == 0
        and result.timing_status == "PASS"
        and result.margin_percent >= 5.0
        and 0 <= result.lut4_percent <= UTIL_LIMITS["LUT4"]
        and 0 <= result.dff_percent <= UTIL_LIMITS["DFF"]
    )


def format_rate(hps):
    rate = hps
    for unit in ("H/s", "kH/s", "MH/s", "GH/s"):
        if abs(rate) < 1000.0 or unit == "GH/s":
            return f"{rate:.2f} {unit}"
        rate /= 1000.0
    return f"{hps:.2f} H/s"


def print_table(results):
    print("| lanes | clock | modeled | timing | fmax | margin | LUT4 | DFF | BSRAM | route | elapsed | ok | reason | log |")
    print("| ---: | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |")
    for item in sorted(results, key=lambda r: (r.selected_ok, r.modeled_hps), reverse=True):
        bsram = f"{item.bsram_used}/{item.bsram_total}" if item.bsram_total else "0/0"
        route_time = f"{item.route_time_seconds:.1f}s" if item.route_time_seconds else "-"
        print(
            f"| {item.lanes} | {item.profile} | {format_rate(item.modeled_hps)} | "
            f"{item.timing_status} | {item.fmax_mhz:.2f} | {item.margin_percent:.1f}% | "
            f"{item.lut4_percent}% | {item.dff_percent}% | {bsram} | {route_time} | "
            f"{item.elapsed_seconds:.1f}s | {'yes' if item.selected_ok else 'no'} | "
            f"{item.failure_reason or '-'} | {item.log_path} |"
        )

    passing = [item for item in results if item.selected_ok]
    if passing:
        best = max(passing, key=lambda r: r.modeled_hps)
        print()
        print(
            "Best passing variant: "
            f"lanes={best.lanes} profile={best.profile} "
            f"modeled={format_rate(best.modeled_hps)} fmax={best.fmax_mhz:.2f}MHz"
        )


def main():
    parser = argparse.ArgumentParser(description="Build and rank TangMiner SpinalHDL lane/clock variants")
    parser.add_argument("--lanes", default=",".join(str(v) for v in DEFAULT_LANES), help="comma-separated lane counts")
    parser.add_argument("--profiles", default=",".join(DEFAULT_PROFILES), help="comma-separated clock profiles")
    parser.add_argument("--build-root", default="build/sweep", help="ignored build directory for variant outputs")
    parser.add_argument("--dry-run", action="store_true", help="print make commands without running them")
    parser.add_argument("--json", dest="json_path", help="write machine-readable result JSON")
    parser.add_argument(
        "--variant-timeout-seconds",
        type=int,
        default=1800,
        help="terminate a single variant after this many seconds; use 0 to disable",
    )
    args = parser.parse_args()

    lanes = parse_csv_ints(args.lanes)
    profiles = parse_csv_strings(args.profiles)
    unknown_profiles = [profile for profile in profiles if profile not in PROFILE_CLOCK_MHZ]
    if unknown_profiles:
        raise SystemExit(f"unsupported profile(s): {', '.join(unknown_profiles)}")

    results = []
    for lane_count in lanes:
        if lane_count <= 0:
            raise SystemExit("lane counts must be positive")
        for profile in profiles:
            results.append(build_variant(args, lane_count, profile))

    print_table(results)

    if args.json_path:
        output_path = Path(args.json_path)
        if not output_path.is_absolute():
            output_path = REPO_ROOT / output_path
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps([asdict(result) for result in results], indent=2) + "\n")

    if any(result.returncode != 0 for result in results):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
