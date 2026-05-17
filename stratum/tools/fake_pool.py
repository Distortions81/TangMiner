#!/usr/bin/env python3
"""Configurable fake Stratum v1 pool for TangMiner client tests."""

import argparse
import json
import socket
import time


def send_line(conn: socket.socket, obj) -> None:
    data = json.dumps(obj, separators=(",", ":")).encode() + b"\n"
    conn.sendall(data)


def recv_lines(conn: socket.socket, timeout: float):
    conn.settimeout(timeout)
    data = b""
    try:
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            data += chunk
            while b"\n" in data:
                line, data = data.split(b"\n", 1)
                if line:
                    yield line.decode(errors="replace")
    except socket.timeout:
        return


def notify_params(args, index: int):
    suffix = f"{index:08x}"
    prev_hash = args.prevhash
    if args.vary_prevhash:
        prev_hash = (args.prevhash[:-8] + suffix)[-64:].rjust(64, "0")
    return [
        f"{args.job_prefix}{index}",
        prev_hash,
        args.coinbase1,
        args.coinbase2,
        args.merkle_branch,
        args.version,
        args.nbits,
        f"{(int(args.ntime, 16) + index) & 0xFFFFFFFF:08x}",
        args.clean_jobs,
    ]


def handle_client(conn: socket.socket, args) -> None:
    print("fake_pool_client=connected", flush=True)
    submit_count = 0
    startup_deadline = time.monotonic() + 5.0
    while time.monotonic() < startup_deadline:
        for line in recv_lines(conn, 0.2):
            print(f"recv={line}", flush=True)
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                continue
            method = msg.get("method")
            msg_id = msg.get("id")
            if method == "mining.subscribe":
                send_line(conn, {
                    "id": msg_id,
                    "result": [[["mining.notify", "1"], ["mining.set_difficulty", "1"]], args.extranonce1, args.extranonce2_size],
                    "error": None,
                })
            elif method == "mining.authorize":
                send_line(conn, {"id": msg_id, "result": args.authorize, "error": None})
            elif method == "mining.suggest_difficulty":
                send_line(conn, {"id": msg_id, "result": True, "error": None})
        if args.authorize:
            break

    send_line(conn, {"id": None, "method": "mining.set_difficulty", "params": [args.difficulty]})

    next_notify = time.monotonic()
    sent = 0
    end = time.monotonic() + args.run_seconds if args.run_seconds > 0 else None
    while end is None or time.monotonic() < end:
        now = time.monotonic()
        if sent < args.notify_count and now >= next_notify:
            send_line(conn, {"id": None, "method": "mining.notify", "params": notify_params(args, sent + 1)})
            print(f"sent_notify={sent + 1}", flush=True)
            sent += 1
            next_notify = now + args.notify_interval_ms / 1000.0

        for line in recv_lines(conn, 0.05):
            print(f"recv={line}", flush=True)
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                continue
            if msg.get("method") == "mining.submit":
                submit_count += 1
                result = args.accept_submits
                error = None if result else [23, "low difficulty share", None]
                send_line(conn, {"id": msg.get("id"), "result": result, "error": error})
                print(f"submit_count={submit_count} accepted={result}", flush=True)
                if args.close_after_submits > 0 and submit_count >= args.close_after_submits:
                    return

        if sent >= args.notify_count and args.exit_after_notifies:
            return


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a fake Stratum v1 pool")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--difficulty", type=float, default=0.00000001)
    parser.add_argument("--authorize", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--accept-submits", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--close-after-submits", type=int, default=0)
    parser.add_argument("--notify-count", type=int, default=1)
    parser.add_argument("--notify-interval-ms", type=int, default=1000)
    parser.add_argument("--exit-after-notifies", action="store_true")
    parser.add_argument("--run-seconds", type=float, default=0)
    parser.add_argument("--extranonce1", default="abcd")
    parser.add_argument("--extranonce2-size", type=int, default=4)
    parser.add_argument("--job-prefix", default="job")
    parser.add_argument("--prevhash", default="0" * 63 + "1")
    parser.add_argument("--vary-prevhash", action="store_true")
    parser.add_argument("--coinbase1", default="0100000001")
    parser.add_argument("--coinbase2", default="ffffffff")
    parser.add_argument("--merkle-branch", action="append", default=[])
    parser.add_argument("--version", default="20000000")
    parser.add_argument("--nbits", default="207fffff")
    parser.add_argument("--ntime", default="65abcdef")
    parser.add_argument("--clean-jobs", action=argparse.BooleanOptionalAction, default=True)
    args = parser.parse_args()

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((args.host, args.port))
        server.listen(1)
        host, port = server.getsockname()
        print(f"fake_pool_addr={host}:{port}", flush=True)
        try:
            conn, _ = server.accept()
            with conn:
                handle_client(conn, args)
        except KeyboardInterrupt:
            pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
