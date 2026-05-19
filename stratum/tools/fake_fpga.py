#!/usr/bin/env python3
"""Configurable fake TangMiner FPGA UART endpoint.

It creates a PTY and speaks the TangMiner UART protocol on the master side.
Point the C miner's --serial-port at the printed /dev/pts/N path.
"""

import argparse
import os
import pty
import select
import sys
import time
import tty
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "scripts" / "tools"))

from make_job import QUICK3_TARGET, QUICK21_TARGET, QUICK23_TARGET, QUICK26_TARGET  # noqa: E402
from tangminer_emulator import TangMinerEmulator  # noqa: E402


QUICK_TARGETS = {
    "quick3": QUICK3_TARGET,
    "quick21": QUICK21_TARGET,
    "quick23": QUICK23_TARGET,
    "quick26": QUICK26_TARGET,
}


def parse_quick_target(name: str):
    key = name.lower().replace("-", "")
    if key not in QUICK_TARGETS:
        raise argparse.ArgumentTypeError("target must be quick3, quick21, quick23, or quick26")
    return QUICK_TARGETS[key]


class FastFakeFpga:
    def __init__(self, nonce: int, delay_ms: int, bad_every: int, drop_every: int):
        self.nonce = nonce & 0xFFFFFFFF
        self.delay_ms = delay_ms
        self.bad_every = bad_every
        self.drop_every = drop_every
        self.state = "sync0"
        self.command = 0
        self.payload = bytearray()
        self.jobs = 0

    def feed(self, data: bytes) -> bytes:
        out = bytearray()
        for byte in data:
            response = self._feed_byte(byte)
            if response:
                out.extend(response)
        return bytes(out)

    def _feed_byte(self, byte: int) -> bytes:
        if self.state == "sync0":
            if byte == ord("T"):
                self.state = "sync1"
            return b""
        if self.state == "sync1":
            self.state = "cmd" if byte == ord("N") else "sync0"
            return b""
        if self.state == "cmd":
            self.command = byte
            self.payload.clear()
            if byte == ord("S"):
                self.state = "sync0"
                return b""
            if byte in (ord("J"), ord("E")):
                self.state = "payload"
                return b""
            self.state = "sync0"
            return b""
        if self.state == "payload":
            self.payload.append(byte)
            if len(self.payload) < 76:
                return b""
            payload = bytes(self.payload)
            self.state = "sync0"
            if self.command == ord("E"):
                return b"E" + payload
            self.jobs += 1
            if self.drop_every > 0 and self.jobs % self.drop_every == 0:
                return b""
            if self.delay_ms > 0:
                time.sleep(self.delay_ms / 1000.0)
            if self.bad_every > 0 and self.jobs % self.bad_every == 0:
                return b"X" + self.nonce.to_bytes(4, "big")
            response = b"F" + self.nonce.to_bytes(4, "big")
            self.nonce = (self.nonce + 1) & 0xFFFFFFFF
            return response
        self.state = "sync0"
        return b""


def serve_pty(fake, max_jobs: int) -> None:
    master_fd, slave_fd = pty.openpty()
    tty.setraw(master_fd)
    tty.setraw(slave_fd)
    slave_name = os.ttyname(slave_fd)
    print(f"fake_fpga_pty={slave_name}", flush=True)
    jobs_seen = 0
    try:
        while max_jobs <= 0 or jobs_seen < max_jobs:
            ready, _, _ = select.select([master_fd], [], [], 0.1)
            if not ready:
                continue
            data = os.read(master_fd, 4096)
            if not data:
                break
            before = getattr(fake, "jobs", 0)
            response = fake.feed(data)
            after = getattr(fake, "jobs", before)
            jobs_seen += max(0, after - before)
            if response:
                os.write(master_fd, response)
    except KeyboardInterrupt:
        pass
    finally:
        os.close(master_fd)
        os.close(slave_fd)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a fake TangMiner FPGA UART endpoint")
    parser.add_argument("--mode", choices=("fast", "hash"), default="fast")
    parser.add_argument("--nonce", default="00000000", help="initial fast-mode nonce as 8 hex digits")
    parser.add_argument("--delay-ms", type=int, default=0)
    parser.add_argument("--bad-every", type=int, default=0, help="return malformed X||nonce every N jobs")
    parser.add_argument("--drop-every", type=int, default=0, help="drop response every N jobs")
    parser.add_argument("--max-jobs", type=int, default=0, help="exit after N TNJ jobs; 0 runs forever")
    parser.add_argument("--max-nonces", type=int, default=1_000_000, help="hash-mode scan limit")
    parser.add_argument(
        "--target",
        type=parse_quick_target,
        default=None,
        metavar="quick3|quick21|quick23|quick26",
        help="hash-mode candidate filter override; defaults to the TNJ packet target",
    )
    args = parser.parse_args()

    if args.mode == "hash":
        fake = TangMinerEmulator(
            max_nonces=args.max_nonces,
            stats_interval=None,
            candidate_target_override=args.target,
        )
    else:
        fake = FastFakeFpga(
            nonce=int(args.nonce, 16),
            delay_ms=args.delay_ms,
            bad_every=args.bad_every,
            drop_every=args.drop_every,
        )
    serve_pty(fake, args.max_jobs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
