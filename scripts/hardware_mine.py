#!/usr/bin/env python3
import argparse
import glob
import time

import serial

from make_job import (
    format_difficulty_units,
    meets_target,
    parse_target,
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
    encode_job_payload,
    format_rate,
    hardware_hashrate,
)


def default_ports():
    patterns = ("/dev/cu.usbserial-*", "/dev/ttyUSB*", "/dev/ttyACM*")
    ports = []
    for pattern in patterns:
        ports.extend(glob.glob(pattern))
    return sorted(dict.fromkeys(ports))


def header_for_job(job_index):
    if job_index == 0:
        return GENESIS_HEADER

    header = bytearray(GENESIS_HEADER)
    timestamp = int.from_bytes(header[68:72], "little")
    header[68:72] = ((timestamp + job_index) & 0xFFFFFFFF).to_bytes(4, "little")
    return bytes(header)


def make_job_packet(header, target):
    return b"TNJ" + encode_job_payload(build_job_from_header(header, target))


def read_exact(ser, length):
    response = ser.read(length)
    if len(response) != length:
        response += ser.read(length - len(response))
    return response


def run_port(port, args):
    target = parse_target(args.target)
    target_diff = target_difficulty(target)
    zero_bits = candidate_zero_bits_for_target(target)
    estimated_hps = hardware_hashrate(args.hardware_clock_hz, args.hardware_cycles_per_nonce)
    total_jobs = None if args.count == 0 else max(args.count, 1)

    print(
        f"miner=hardware port={port} target={args.target} target_diff={format_difficulty_units(target_diff)} "
        f"candidate_bits={zero_bits} rate={format_rate(estimated_hps)} source=rtl_estimate",
        flush=True,
    )

    candidates = 0
    total_observed_hashes = 0

    with serial.Serial(port, baudrate=args.baud, timeout=args.timeout, write_timeout=args.timeout) as ser:
        time.sleep(0.1)
        ser.reset_input_buffer()
        ser.reset_output_buffer()
        started = time.monotonic()

        try:
            while total_jobs is None or candidates < total_jobs:
                job_index = candidates
                header = header_for_job(job_index)
                job = build_job_from_header(header, target)
                packet = make_job_packet(header, target)

                job_started = time.monotonic()
                ser.write(packet)
                ser.flush()
                response = read_exact(ser, 5)
                job_elapsed = max(time.monotonic() - job_started, 1e-9)

                if len(response) != 5 or response[:1] != b"F":
                    print(f"response_error job={job_index} read={len(response)} bytes={response.hex()}", flush=True)
                    return candidates > 0

                nonce = int.from_bytes(response[1:5], "big")
                observed_hashes = nonce + 1
                total_observed_hashes += observed_hashes
                total_elapsed = max(time.monotonic() - started, 1e-9)
                candidates += 1

                digest = bitcoin_hash(job, nonce)
                share_diff = share_difficulty(digest)
                candidate_line = (
                    f"share #{candidates} miner=hardware nonce=0x{nonce:08x} "
                    f"diff={format_difficulty_units(share_diff)} target={format_difficulty_units(target_diff)} "
                    f"rate={format_rate(estimated_hps)} ok={'yes' if meets_target(digest, target) else 'no'} "
                    f"hash={digest.hex()}"
                )
                if args.verbose:
                    candidate_line += (
                        f" job={job_index} observed_hashes={observed_hashes} job_elapsed={job_elapsed:.3f}s "
                        f"observed_rate={format_rate(observed_hashes / job_elapsed)} "
                        f"total_observed_rate={format_rate(total_observed_hashes / total_elapsed)} "
                        f"candidates_per_min={candidates / total_elapsed * 60.0:.2f}"
                    )
                print(candidate_line, flush=True)
        except KeyboardInterrupt:
            print("stopped", flush=True)
            try:
                ser.write(b"TNS")
                ser.flush()
            except serial.SerialException:
                pass
            return candidates > 0

    return candidates > 0


def main():
    parser = argparse.ArgumentParser(description="Run TangMiner hardware over USB-UART and print candidates")
    parser.add_argument("ports", nargs="*", help="serial ports to try; defaults to common USB-UART device globs")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--target", default="quick21", help="target hex or alias; quick21 is the default miner-style share target")
    parser.add_argument("--count", type=int, default=0, help="candidate count to print; default 0 runs until interrupted")
    parser.add_argument("--verbose", action="store_true", help="print observed serial timing and rates on each share")
    parser.add_argument("--hardware-clock-hz", type=int, default=DEFAULT_HARDWARE_CLOCK_HZ)
    parser.add_argument("--hardware-cycles-per-nonce", type=float, default=MEASURED_HARDWARE_CYCLES_PER_NONCE)
    args = parser.parse_args()

    ports = args.ports or default_ports()
    if not ports:
        raise SystemExit("no serial ports found; pass a /dev/cu.usbserial-* or /dev/ttyUSB* path")

    for port in ports:
        try:
            if run_port(port, args):
                return
        except serial.SerialException as exc:
            print(f"{port}: ERROR {exc}", flush=True)

    raise SystemExit("no valid FPGA response")


if __name__ == "__main__":
    main()
