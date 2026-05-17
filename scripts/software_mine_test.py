#!/usr/bin/env python3
import argparse
import time

from make_job import (
    format_difficulty_units,
    meets_target,
    parse_target,
    pow_hash_value,
    share_difficulty,
    target_difficulty,
)
from tangminer_emulator import (
    DEFAULT_HARDWARE_CLOCK_HZ,
    GENESIS_HEADER,
    MEASURED_HARDWARE_CYCLES_PER_NONCE,
    bitcoin_hash,
    build_job_from_header,
    candidate_zero_bits_for_target,
    format_rate,
    hardware_hashrate,
)


DEFAULT_SOFTWARE_HASHRATE_HZ = 6_000
DEFAULT_SOFTWARE_TARGET = "quick14"
SOFTWARE_QUICK14_BITS = 14
SOFTWARE_QUICK14_TARGET = ((1 << (256 - SOFTWARE_QUICK14_BITS)) - 1).to_bytes(32, "big")


def parse_software_target(value):
    name = value.lower().replace("_", "-")
    if name in ("quick14", "quick-14", "software", "software-default"):
        return SOFTWARE_QUICK14_TARGET, SOFTWARE_QUICK14_BITS

    target = parse_target(value)
    return target, candidate_zero_bits_for_target(target)


def meets_candidate_filter(digest, zero_bits):
    if zero_bits == 0:
        return True
    return pow_hash_value(digest) >> (256 - zero_bits) == 0


def header_for_job(job_index):
    if job_index == 0:
        return GENESIS_HEADER

    header = bytearray(GENESIS_HEADER)
    timestamp = int.from_bytes(header[68:72], "little")
    header[68:72] = ((timestamp + job_index) & 0xFFFFFFFF).to_bytes(4, "little")
    return bytes(header)


def display_rate(rate_source, software_estimated_hps, hardware_estimated_hps, hashes, elapsed):
    if rate_source == "estimate":
        return "software_estimate", software_estimated_hps
    if rate_source == "hardware":
        return "hardware_estimate", hardware_estimated_hps
    return "software_model", hashes / max(elapsed, 1e-9)


def print_progress(
    job_index,
    job_hashes,
    total_hashes,
    job_started,
    started,
    rate_source,
    software_estimated_hps,
    hardware_estimated_hps,
):
    now = time.monotonic()
    job_elapsed = max(now - job_started, 1e-9)
    total_elapsed = max(now - started, 1e-9)
    source, rate_hps = display_rate(
        rate_source,
        software_estimated_hps,
        hardware_estimated_hps,
        total_hashes,
        total_elapsed,
    )
    print(
        f"status job={job_index} scanned={job_hashes} "
        f"rate={format_rate(rate_hps)} source={source} "
        f"software_rate={format_rate(total_hashes / total_elapsed)} "
        f"estimated_elapsed={total_hashes / max(rate_hps, 1e-9):.3f}s",
        flush=True,
    )


def scan_job(
    job,
    job_index,
    max_nonces,
    stats_interval,
    started,
    total_hashes,
    rate_source,
    software_estimated_hps,
    hardware_estimated_hps,
    candidate_zero_bits,
):
    limit = max_nonces if max_nonces is not None else 2**32
    job_started = time.monotonic()
    last_report = job_started

    for nonce in range(limit):
        digest = bitcoin_hash(job, nonce)
        job_hashes = nonce + 1
        total_hashes += 1
        now = time.monotonic()

        if stats_interval and now - last_report >= stats_interval:
            print_progress(
                job_index,
                job_hashes,
                total_hashes,
                job_started,
                started,
                rate_source,
                software_estimated_hps,
                hardware_estimated_hps,
            )
            last_report = now

        if meets_candidate_filter(digest, candidate_zero_bits):
            return nonce, digest, job_hashes, total_hashes, now - job_started

    return None, None, limit, total_hashes, time.monotonic() - job_started


def main():
    parser = argparse.ArgumentParser(description="Run a software-only TangMiner candidate mining test")
    parser.add_argument(
        "--target",
        default=DEFAULT_SOFTWARE_TARGET,
        help="target hex or alias. quick14 is the software default, averaging a share every few seconds at 6 kH/s.",
    )
    parser.add_argument("--count", type=int, default=0, help="candidate count to print; default 0 runs until interrupted")
    parser.add_argument(
        "--max-nonces",
        type=int,
        default=10_000_000,
        help="maximum nonces per generated job; use 0 for full nonce space",
    )
    parser.add_argument(
        "--stats-interval",
        type=float,
        default=0.0,
        help="seconds between status lines; default 0 prints only candidate shares",
    )
    parser.add_argument("--verbose", action="store_true", help="print scanned counts, elapsed time, and model speed on each candidate")
    parser.add_argument(
        "--rate-source",
        choices=("estimate", "hardware", "software"),
        default="estimate",
        help="show the 6 kH/s software estimate by default; use software for measured Python speed or hardware for RTL estimate",
    )
    parser.add_argument("--software-hashrate-hz", type=float, default=DEFAULT_SOFTWARE_HASHRATE_HZ)
    parser.add_argument("--hardware-clock-hz", type=int, default=DEFAULT_HARDWARE_CLOCK_HZ)
    parser.add_argument("--hardware-cycles-per-nonce", type=int, default=MEASURED_HARDWARE_CYCLES_PER_NONCE)
    args = parser.parse_args()

    target, zero_bits = parse_software_target(args.target)
    target_diff = target_difficulty(target)
    software_estimated_hps = args.software_hashrate_hz
    hardware_estimated_hps = hardware_hashrate(args.hardware_clock_hz, args.hardware_cycles_per_nonce)
    max_nonces = None if args.max_nonces == 0 else args.max_nonces
    total_candidates = None if args.count == 0 else max(args.count, 1)
    stats_interval = None if args.stats_interval <= 0 else args.stats_interval
    source, displayed_hps = display_rate(
        args.rate_source,
        software_estimated_hps,
        hardware_estimated_hps,
        0,
        1e-9,
    )
    expected_share_seconds = (2 ** zero_bits) / max(software_estimated_hps, 1e-9)

    print(
        f"miner=software target={args.target} target_diff={format_difficulty_units(target_diff)} "
        f"candidate_bits={zero_bits} expected_share_interval={expected_share_seconds:.2f}s "
        f"rate={format_rate(displayed_hps)} source={source}",
        flush=True,
    )

    started = time.monotonic()
    total_hashes = 0
    candidates = 0
    job_index = 0

    try:
        while total_candidates is None or candidates < total_candidates:
            header = header_for_job(job_index)
            job = build_job_from_header(header, target)
            nonce, digest, job_hashes, total_hashes, job_elapsed = scan_job(
                job,
                job_index,
                max_nonces,
                stats_interval,
                started,
                total_hashes,
                args.rate_source,
                software_estimated_hps,
                hardware_estimated_hps,
                zero_bits,
            )
            total_elapsed = max(time.monotonic() - started, 1e-9)

            if digest is None:
                print(
                    f"exhausted job={job_index} scanned={job_hashes} "
                    f"job_elapsed={job_elapsed:.3f}s total_rate={format_rate(total_hashes / total_elapsed)}",
                    flush=True,
                )
                job_index += 1
                continue

            candidates += 1
            share_diff = share_difficulty(digest)
            source, rate_hps = display_rate(
                args.rate_source,
                software_estimated_hps,
                hardware_estimated_hps,
                total_hashes,
                total_elapsed,
            )
            candidate_line = (
                f"share #{candidates} miner=software nonce=0x{nonce:08x} "
                f"diff={format_difficulty_units(share_diff)} target={format_difficulty_units(target_diff)} "
                f"rate={format_rate(rate_hps)} ok={'yes' if meets_target(digest, target) else 'no'} "
                f"hash={digest.hex()}"
            )
            if args.verbose:
                candidate_line += (
                    f" job={job_index} scanned={job_hashes} job_elapsed={job_elapsed:.3f}s "
                    f"hashrate_source={source} "
                    f"software_rate={format_rate(total_hashes / total_elapsed)} "
                    f"estimated_elapsed={total_hashes / max(rate_hps, 1e-9):.3f}s "
                    f"estimated_candidates_per_min={rate_hps / max(total_hashes / candidates, 1e-9) * 60.0:.2f}"
                )
            print(candidate_line, flush=True)
            job_index += 1
    except KeyboardInterrupt:
        print("stopped", flush=True)


if __name__ == "__main__":
    main()
