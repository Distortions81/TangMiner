#!/usr/bin/env python3
"""Software-only TangMiner UART protocol emulator.

This is not an HDL simulator. It emulates the observable UART protocol and
hash-core behavior so host software can be developed without a Tang Nano board.
"""

import argparse
import errno
import os
import pty
import select
import sys
import threading
import time
import tty
from dataclasses import dataclass
from typing import Optional, TextIO

from make_job import (
    ALL_ONES_TARGET,
    IV,
    QUICK3_TARGET,
    QUICK21_TARGET,
    QUICK23_TARGET,
    QUICK26_TARGET,
    compress,
    meets_target,
    pow_hash_value,
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

GENESIS_EXPECTED_HASH_NONCE_ZERO = bytes.fromhex(
    "bf483998a9b44cbf5a113973e34da96b5cf3c7757d75ac3bd7c6b30af5a7c12b"
)
DEFAULT_HARDWARE_CLOCK_HZ = 111_000_000
MEASURED_HARDWARE_CYCLES_PER_NONCE = 16.0


@dataclass(frozen=True)
class Job:
    midstate: bytes
    tail: bytes
    target: bytes


def build_job_from_header(header: bytes, target: bytes) -> Job:
    if len(header) != 80:
        raise ValueError("header must be exactly 80 bytes")
    if len(target) != 32:
        raise ValueError("target must be exactly 32 bytes")

    return Job(
        midstate=words_to_bytes(compress(IV, header[:64])),
        tail=header[64:76],
        target=target,
    )


def encode_job_payload(job: Job) -> bytes:
    return job.midstate + job.tail + job.target


def decode_job_payload(payload: bytes) -> Job:
    if len(payload) != 76:
        raise ValueError("job payload must be exactly 76 bytes")
    return Job(midstate=payload[:32], tail=payload[32:44], target=payload[44:76])


def _first_pass_digest(job: Job, nonce: int) -> bytes:
    block = (
        job.tail
        + nonce.to_bytes(4, "big")
        + b"\x80"
        + b"\x00" * 39
        + (80 * 8).to_bytes(8, "big")
    )
    state = tuple(int.from_bytes(job.midstate[i:i + 4], "big") for i in range(0, 32, 4))
    return words_to_bytes(compress(state, block))


def bitcoin_hash(job: Job, nonce: int) -> bytes:
    first_digest = _first_pass_digest(job, nonce)
    second_block = first_digest + b"\x80" + b"\x00" * 23 + (32 * 8).to_bytes(8, "big")
    return words_to_bytes(compress(IV, second_block))


def candidate_zero_bits_for_target(target: bytes) -> int:
    if target == ALL_ONES_TARGET:
        return 0
    if target == QUICK3_TARGET:
        return 3
    if target == QUICK21_TARGET:
        return 21
    if target == QUICK26_TARGET:
        return 26
    return 23


def meets_hardware_candidate_filter(digest: bytes, target: bytes) -> bool:
    zero_bits = candidate_zero_bits_for_target(target)
    if zero_bits == 0:
        return True
    value = pow_hash_value(digest)
    return value >> (256 - zero_bits) == 0


def format_rate(hashes_per_second: float) -> str:
    units = ("H/s", "kH/s", "MH/s", "GH/s")
    rate = hashes_per_second
    for unit in units:
        if abs(rate) < 1000.0 or unit == units[-1]:
            return f"{rate:.2f} {unit}"
        rate /= 1000.0
    return f"{rate:.2f} H/s"


def hardware_hashrate(clock_hz: int, cycles_per_nonce: float) -> float:
    return clock_hz / cycles_per_nonce


class TangMinerEmulator:
    def __init__(
        self,
        max_nonces: Optional[int] = 1_000_000,
        stats_interval: Optional[float] = None,
        stats_stream: Optional[TextIO] = None,
        stats_source: str = "hardware",
        hardware_clock_hz: int = DEFAULT_HARDWARE_CLOCK_HZ,
        hardware_cycles_per_nonce: float = MEASURED_HARDWARE_CYCLES_PER_NONCE,
        candidate_target_override: Optional[bytes] = None,
    ):
        if stats_source not in ("hardware", "software"):
            raise ValueError("stats_source must be hardware or software")
        if hardware_clock_hz <= 0:
            raise ValueError("hardware_clock_hz must be positive")
        if hardware_cycles_per_nonce <= 0:
            raise ValueError("hardware_cycles_per_nonce must be positive")
        self.max_nonces = max_nonces
        self.stats_interval = stats_interval
        self.stats_stream = stats_stream
        self.stats_source = stats_source
        self.hardware_clock_hz = hardware_clock_hz
        self.hardware_cycles_per_nonce = hardware_cycles_per_nonce
        self.candidate_target_override = candidate_target_override
        self._rx_state = "sync0"
        self._command = 0
        self._payload = bytearray()

    def feed(self, data: bytes) -> bytes:
        output = bytearray()
        for byte in data:
            response = self._feed_byte(byte)
            if response:
                output.extend(response)
        return bytes(output)

    def _feed_byte(self, byte: int) -> bytes:
        if self._rx_state == "sync0":
            if byte == ord("T"):
                self._rx_state = "sync1"
            return b""

        if self._rx_state == "sync1":
            self._rx_state = "cmd" if byte == ord("N") else "sync0"
            return b""

        if self._rx_state == "cmd":
            self._command = byte
            self._payload.clear()
            if byte == ord("S"):
                self._rx_state = "sync0"
                return b""
            if byte == ord("H"):
                self._rx_state = "sync0"
                return self._run_job(build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET))
            if byte in (ord("J"), ord("E")):
                self._rx_state = "payload"
                return b""
            self._rx_state = "sync0"
            return b""

        if self._rx_state == "payload":
            self._payload.append(byte)
            if len(self._payload) == 76:
                payload = bytes(self._payload)
                self._rx_state = "sync0"
                if self._command == ord("E"):
                    return b"E" + payload
                return self._run_job(self._candidate_job(decode_job_payload(payload)))
            return b""

        self._rx_state = "sync0"
        return b""

    def _candidate_job(self, job: Job) -> Job:
        if self.candidate_target_override is None:
            return job
        return Job(midstate=job.midstate, tail=job.tail, target=self.candidate_target_override)

    def _run_job(self, job: Job) -> bytes:
        limit = self.max_nonces if self.max_nonces is not None else 2**32
        started = time.monotonic()
        last_report = started
        last_report_scanned = 0
        for nonce in range(limit):
            digest = bitcoin_hash(job, nonce)
            scanned = nonce + 1
            now = time.monotonic()
            if self.stats_interval and now - last_report >= self.stats_interval:
                self._report_stats("progress", scanned, started, now, last_report_scanned, last_report)
                last_report = now
                last_report_scanned = scanned
            if meets_hardware_candidate_filter(digest, job.target):
                self._report_stats("found", scanned, started, time.monotonic(), last_report_scanned, last_report)
                return b"F" + nonce.to_bytes(4, "big")
        self._report_stats("exhausted", limit, started, time.monotonic(), last_report_scanned, last_report)
        return b""

    def _report_stats(
        self,
        state: str,
        scanned: int,
        started: float,
        now: float,
        last_scanned: int,
        last_report: float,
    ) -> None:
        if not self.stats_stream:
            return
        elapsed = max(now - started, 1e-9)
        if self.stats_source == "hardware":
            rate_hps = hardware_hashrate(self.hardware_clock_hz, self.hardware_cycles_per_nonce)
            print(
                "hashrate source=hardware_estimate "
                f"state={state} scanned={scanned} elapsed={elapsed:.2f}s "
                f"cycles_per_nonce={self.hardware_cycles_per_nonce:g} "
                f"clock_hz={self.hardware_clock_hz} "
                f"rate={format_rate(rate_hps)} rate_hps={rate_hps:.2f}",
                file=self.stats_stream,
                flush=True,
            )
            return

        window_elapsed = max(now - last_report, 1e-9)
        window_scanned = max(scanned - last_scanned, 0)
        rate = format_rate(scanned / elapsed)
        window_rate = format_rate(window_scanned / window_elapsed)
        print(
            "hashrate source=software_model "
            f"state={state} scanned={scanned} elapsed={elapsed:.2f}s "
            f"rate={rate} window_rate={window_rate}",
            file=self.stats_stream,
            flush=True,
        )


def _normalise_board(value: str) -> str:
    aliases = {
        "9k": "tangnano9k",
        "tn9k": "tangnano9k",
        "tangnano9": "tangnano9k",
        "tangnano90": "tangnano9k",
        "20k": "tangnano20k",
        "tn20k": "tangnano20k",
        "tangnano20": "tangnano20k",
    }
    return aliases.get(value.lower(), value.lower())


def run_stdio(emulator: TangMinerEmulator) -> None:
    stdin_fd = sys.stdin.fileno()
    stdout_fd = sys.stdout.fileno()
    while True:
        data = os.read(stdin_fd, 4096)
        if not data:
            return
        response = emulator.feed(data)
        if response:
            os.write(stdout_fd, response)


def _write_auto_benchmark(slave_name: str) -> None:
    job = build_job_from_header(GENESIS_HEADER, b"\x00" * 32)
    packet = b"TNJ" + encode_job_payload(job)
    # Give the PTY loop a moment to enter select before injecting the job.
    time.sleep(0.1)
    with open(slave_name, "wb", buffering=0) as slave:
        slave.write(packet)


def run_pty(emulator: TangMinerEmulator, board: str, auto_benchmark: bool = False) -> None:
    master_fd, slave_fd = pty.openpty()
    tty.setraw(slave_fd)
    slave_name = os.ttyname(slave_fd)
    os.close(slave_fd)

    print(f"TangMiner {board} software UART: {slave_name}", flush=True)
    if auto_benchmark:
        print("Starting automatic hashrate benchmark...", file=sys.stderr, flush=True)
        threading.Thread(target=_write_auto_benchmark, args=(slave_name,), daemon=True).start()
    try:
        while True:
            readable, _, _ = select.select([master_fd], [], [], 0.25)
            if not readable:
                continue
            try:
                data = os.read(master_fd, 4096)
            except OSError as exc:
                if exc.errno == errno.EIO:
                    continue
                raise
            if not data:
                continue
            response = emulator.feed(data)
            if response:
                os.write(master_fd, response)
    finally:
        os.close(master_fd)


def main() -> None:
    parser = argparse.ArgumentParser(description="Software-only TangMiner protocol emulator")
    parser.add_argument(
        "--board",
        default="tangnano20k",
        help="board label for logs; 9k/tn9k and 20k/tn20k aliases are accepted",
    )
    parser.add_argument(
        "--max-nonces",
        type=int,
        default=1_000_000,
        help="maximum nonces to scan per job; use 0 for the full 32-bit space",
    )
    parser.add_argument(
        "--stats-interval",
        type=float,
        default=2.0,
        help="seconds between hashrate reports while hashing; use 0 to disable",
    )
    parser.add_argument(
        "--stats-source",
        choices=("hardware", "software"),
        default="hardware",
        help="report hardware cycle estimate by default; use software for Python emulator speed",
    )
    parser.add_argument(
        "--hardware-clock-hz",
        type=int,
        default=DEFAULT_HARDWARE_CLOCK_HZ,
        help="hardware clock used for source=hardware hashrate reports",
    )
    parser.add_argument(
        "--hardware-cycles-per-nonce",
        type=float,
        default=MEASURED_HARDWARE_CYCLES_PER_NONCE,
        help="measured RTL cycles per nonce used for source=hardware hashrate reports",
    )
    parser.add_argument(
        "--auto-benchmark",
        action="store_true",
        help="inject an impossible-target benchmark job after the PTY starts",
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--stdio", action="store_true", help="read binary protocol from stdin and write responses to stdout")
    mode.add_argument("--pty", action="store_true", help="create a pseudo-terminal that behaves like the FPGA UART")
    args = parser.parse_args()

    board = _normalise_board(args.board)
    if board not in ("tangnano9k", "tangnano20k"):
        raise SystemExit("unsupported board label; use tangnano9k or tangnano20k")

    max_nonces = None if args.max_nonces == 0 else args.max_nonces
    stats_interval = None if args.stats_interval <= 0 else args.stats_interval
    stats_stream = sys.stderr if stats_interval else None
    emulator = TangMinerEmulator(
        max_nonces=max_nonces,
        stats_interval=stats_interval,
        stats_stream=stats_stream,
        stats_source=args.stats_source,
        hardware_clock_hz=args.hardware_clock_hz,
        hardware_cycles_per_nonce=args.hardware_cycles_per_nonce,
    )
    if args.stdio:
        run_stdio(emulator)
    else:
        run_pty(emulator, board, auto_benchmark=args.auto_benchmark)


if __name__ == "__main__":
    main()
