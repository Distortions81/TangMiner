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
import tty
from dataclasses import dataclass
from typing import Optional

from make_job import IV, compress, words_to_bytes


GENESIS_HEADER = bytes.fromhex(
    "01000000"
    "0000000000000000000000000000000000000000000000000000000000000000"
    "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a"
    "29ab5f49"
    "ffff001d"
    "1dac2b7c"
)

ALL_ONES_TARGET = b"\xff" * 32
GENESIS_EXPECTED_HASH_NONCE_ZERO = bytes.fromhex(
    "bf483998a9b44cbf5a113973e34da96b5cf3c7757d75ac3bd7c6b30af5a7c12b"
)


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


def meets_target(digest: bytes, target: bytes) -> bool:
    return int.from_bytes(digest[::-1], "big") <= int.from_bytes(target, "big")


class TangMinerEmulator:
    def __init__(self, max_nonces: Optional[int] = 1_000_000):
        self.max_nonces = max_nonces
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
                return self._run_job(decode_job_payload(payload))
            return b""

        self._rx_state = "sync0"
        return b""

    def _run_job(self, job: Job) -> bytes:
        limit = self.max_nonces if self.max_nonces is not None else 2**32
        for nonce in range(limit):
            digest = bitcoin_hash(job, nonce)
            if meets_target(digest, job.target):
                return b"F" + nonce.to_bytes(4, "big") + digest
        return b""


def _normalise_board(value: str) -> str:
    aliases = {
        "9k": "tangnano9k",
        "tn9k": "tangnano9k",
        "tangnano9": "tangnano9k",
        "tangnano90": "tangnano9k",
        "20k": "tangnano20k",
        "tn20k": "tangnano20k",
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


def run_pty(emulator: TangMinerEmulator, board: str) -> None:
    master_fd, slave_fd = pty.openpty()
    tty.setraw(slave_fd)
    slave_name = os.ttyname(slave_fd)
    os.close(slave_fd)

    print(f"TangMiner {board} software UART: {slave_name}", flush=True)
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
        default="tangnano9k",
        help="board label for logs; tangnano90 is accepted as an alias for tangnano9k",
    )
    parser.add_argument(
        "--max-nonces",
        type=int,
        default=1_000_000,
        help="maximum nonces to scan per job; use 0 for the full 32-bit space",
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--stdio", action="store_true", help="read binary protocol from stdin and write responses to stdout")
    mode.add_argument("--pty", action="store_true", help="create a pseudo-terminal that behaves like the FPGA UART")
    args = parser.parse_args()

    board = _normalise_board(args.board)
    if board not in ("tangnano9k", "tangnano20k"):
        raise SystemExit("unsupported board label; use tangnano9k or tangnano20k")

    max_nonces = None if args.max_nonces == 0 else args.max_nonces
    emulator = TangMinerEmulator(max_nonces=max_nonces)
    if args.stdio:
        run_stdio(emulator)
    else:
        run_pty(emulator, board)


if __name__ == "__main__":
    main()
