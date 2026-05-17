#!/usr/bin/env python3
from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_EXPECTED_HASH_NONCE_ZERO,
    GENESIS_HEADER,
    QUICK3_TARGET,
    TangMinerEmulator,
    bitcoin_hash,
    build_job_from_header,
    encode_job_payload,
)


def feed_bytewise(emulator, packet):
    response = bytearray()
    for byte in packet:
        response.extend(emulator.feed(bytes([byte])))
    return bytes(response)


def main():
    job = build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET)
    payload = encode_job_payload(job)

    emulator = TangMinerEmulator(max_nonces=8)
    echo = feed_bytewise(emulator, b"TNE" + payload)
    if echo != b"E" + payload:
        raise SystemExit(f"FAIL echo: {echo.hex()}")

    found = feed_bytewise(emulator, b"TNJ" + payload)
    if len(found) != 5 or found[:1] != b"F":
        raise SystemExit(f"FAIL found response: {found.hex()}")
    if found[1:5] != b"\x00\x00\x00\x00":
        raise SystemExit(f"FAIL nonce: {found[1:5].hex()}")
    if bitcoin_hash(job, 0) != GENESIS_EXPECTED_HASH_NONCE_ZERO:
        raise SystemExit("FAIL host hash validation")

    quick3_job = build_job_from_header(GENESIS_HEADER, QUICK3_TARGET)
    quick3_payload = encode_job_payload(quick3_job)
    quick3_found = feed_bytewise(TangMinerEmulator(max_nonces=8), b"TNJ" + quick3_payload)
    if len(quick3_found) != 5 or quick3_found[:1] != b"F":
        raise SystemExit(f"FAIL quick3 found response: {quick3_found.hex()}")
    if quick3_found[1:5] != b"\x00\x00\x00\x03":
        raise SystemExit(f"FAIL quick3 nonce: {quick3_found[1:5].hex()}")
    if bitcoin_hash(quick3_job, 3)[::-1][0] & 0xE0:
        raise SystemExit("FAIL quick3 host hash validation")

    hardcoded = feed_bytewise(emulator, b"TNH")
    if hardcoded != found:
        raise SystemExit(f"FAIL hardcoded job: {hardcoded.hex()}")

    print("PASS software emulator echo/hash/hardcoded paths")


if __name__ == "__main__":
    main()
