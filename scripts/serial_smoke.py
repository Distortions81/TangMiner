#!/usr/bin/env python3
import argparse
import glob
import hashlib
import time

import serial

from make_job import (
    ALL_ONES_TARGET,
    IV,
    compress,
    format_difficulty_units,
    meets_target,
    parse_target,
    share_difficulty,
    target_difficulty,
    words_to_bytes,
)


GENESIS_HEADER = bytes.fromhex(
    "01000000"
    "0000000000000000000000000000000000000000000000000000000000000000"
    "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a"
    "29ab5f49"
    "ffff001d"
    "1dac2b7c"
)


def make_packet(header, target, command=b"J"):
    midstate = words_to_bytes(compress(IV, header[:64]))
    tail = header[64:76]
    return b"TN" + command + midstate + tail + target


def bitcoin_hash_for_nonce(header, nonce):
    candidate = header[:76] + nonce.to_bytes(4, "big")
    return hashlib.sha256(hashlib.sha256(candidate).digest()).digest()


def header_for_job(job_index):
    if job_index == 0:
        return GENESIS_HEADER

    header = bytearray(GENESIS_HEADER)
    timestamp = int.from_bytes(header[68:72], "little")
    header[68:72] = ((timestamp + job_index) & 0xFFFFFFFF).to_bytes(4, "little")
    return bytes(header)


def read_response(ser, response_len):
    response = ser.read(response_len)
    if len(response) != response_len:
        response += ser.read(response_len - len(response))
    return response


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("ports", nargs="*", help="serial ports to try")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--timeout", type=float, default=1.0)
    parser.add_argument(
        "--target",
        default="all-ones",
        help="target hex or alias: all-ones, quick23, quick26, quick21, quick3. quick23 averages about 1.3 seconds on the 100.286 MHz 20K build.",
    )
    parser.add_argument("--echo", action="store_true", help="ask FPGA to echo the parsed job instead of hashing")
    parser.add_argument("--hardcoded", action="store_true", help="ask FPGA to run its built-in genesis nonce-zero job")
    parser.add_argument("--count", type=int, default=1, help="number of hash jobs to run; use 0 to run until interrupted")
    parser.add_argument("--watch", action="store_true", help="keep sending fresh smoke jobs and printing candidates")
    args = parser.parse_args()

    ports = args.ports or sorted(glob.glob("/dev/cu.usbserial-*"))
    target = parse_target(args.target)
    validation_target = ALL_ONES_TARGET if args.hardcoded else target
    expected_payload = make_packet(GENESIS_HEADER, target)[3:]
    response_len = 77 if args.echo else 5
    total_jobs = None if args.watch or args.count == 0 else max(args.count, 1)

    for port in ports:
        try:
            with serial.Serial(port, baudrate=args.baud, timeout=args.timeout, write_timeout=args.timeout) as ser:
                time.sleep(0.1)
                ser.reset_input_buffer()
                ser.reset_output_buffer()

                if args.echo:
                    packet = make_packet(GENESIS_HEADER, target, b"E")
                    print(f"packet_len={len(packet)}")
                    ser.write(packet)
                    ser.flush()
                    response = read_response(ser, response_len)
                    print(f"{port}: read {len(response)} bytes {response.hex()}")
                    if len(response) == 77 and response[:1] == b"E":
                        echoed = response[1:]
                        print(f"{port}: ECHO {'OK' if echoed == expected_payload else 'MISMATCH'}")
                        if echoed != expected_payload:
                            for i, (got, exp) in enumerate(zip(echoed, expected_payload)):
                                if got != exp:
                                    print(f"first mismatch payload[{i}] got=0x{got:02x} expected=0x{exp:02x}")
                                    break
                        return
                    continue

                print(f"packet_len={3 if args.hardcoded else 79}")
                job_index = 0
                try:
                    while total_jobs is None or job_index < total_jobs:
                        header = GENESIS_HEADER if args.hardcoded else header_for_job(job_index)
                        packet = b"TNH" if args.hardcoded else make_packet(header, target)
                        ser.write(packet)
                        ser.flush()
                        response = read_response(ser, response_len)
                        print(f"{port}: job={job_index} read {len(response)} bytes {response.hex()}")
                        if len(response) != 5 or response[:1] != b"F":
                            break

                        nonce = int.from_bytes(response[1:5], "big")
                        digest = bitcoin_hash_for_nonce(header, nonce)
                        candidate_difficulty = share_difficulty(digest)
                        requested_difficulty = target_difficulty(validation_target)
                        print(
                            f"{port}: job={job_index} FOUND nonce=0x{nonce:08x} "
                            f"host_hash={digest.hex()} "
                            f"share_difficulty={format_difficulty_units(candidate_difficulty)} "
                            f"target_difficulty={format_difficulty_units(requested_difficulty)} "
                            f"target_met={'yes' if meets_target(digest, validation_target) else 'no'}"
                        )
                        job_index += 1
                except KeyboardInterrupt:
                    print(f"{port}: stopped")
                    return
                if job_index:
                    return
        except Exception as exc:
            print(f"{port}: ERROR {exc}")
            continue

    raise SystemExit("no valid FPGA response")


if __name__ == "__main__":
    main()
