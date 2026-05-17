#!/usr/bin/env python3
from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_EXPECTED_HASH_NONCE_ZERO,
    GENESIS_HEADER,
    TangMinerEmulator,
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
    if len(found) != 37 or found[:1] != b"F":
        raise SystemExit(f"FAIL found response: {found.hex()}")
    if found[1:5] != b"\x00\x00\x00\x00":
        raise SystemExit(f"FAIL nonce: {found[1:5].hex()}")
    if found[5:] != GENESIS_EXPECTED_HASH_NONCE_ZERO:
        raise SystemExit(f"FAIL hash: {found[5:].hex()}")

    hardcoded = feed_bytewise(emulator, b"TNH")
    if hardcoded != found:
        raise SystemExit(f"FAIL hardcoded job: {hardcoded.hex()}")

    print("PASS software emulator echo/hash/hardcoded paths")


if __name__ == "__main__":
    main()
