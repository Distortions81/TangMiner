#!/usr/bin/env python3
from make_job import format_difficulty
from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_EXPECTED_HASH_NONCE_ZERO,
    GENESIS_HEADER,
    QUICK21_TARGET,
    QUICK14_TARGET,
    QUICK23_TARGET,
    QUICK26_TARGET,
    QUICK3_TARGET,
    TangMinerEmulator,
    bitcoin_hash,
    build_job_from_header,
    encode_job_payload,
    meets_hardware_candidate_filter,
    meets_target,
    share_difficulty,
    target_difficulty,
)


EXPECTED_ALIAS_CANDIDATES = (
    (QUICK3_TARGET, 3, "1498a37b8059cca064bcc16d7a727156907beb5d9bd4641b003b09d911b00f1c"),
    (QUICK14_TARGET, 34368, "761fffad21ec37329561eb2e15912524c9b44c34b27f00df85ebc20c6d190200"),
    (QUICK21_TARGET, 213373, "d4dfb69f98e5c3c36efdb8aa134677f0b229f658fdc35c1747a474a552040000"),
    (QUICK23_TARGET, 7651038, "0a66cd262862865f93e08fb0e80ecd2bc52dcf58100842a9a11c1e9473000000"),
    (QUICK26_TARGET, 26309569, "762334dca2a62282076426cb814d6a8a3de7ff57bb4f2754895b695726000000"),
)


def feed_bytewise(emulator, packet):
    response = bytearray()
    for byte in packet:
        response.extend(emulator.feed(bytes([byte])))
    return bytes(response)


def validate_candidate(job, nonce, expected_hash_hex=None):
    digest = bitcoin_hash(job, nonce)
    if expected_hash_hex and digest.hex() != expected_hash_hex:
        raise SystemExit(f"FAIL hash for nonce {nonce}: {digest.hex()}")
    if not meets_hardware_candidate_filter(digest, job.target):
        raise SystemExit(f"FAIL hardware candidate filter for nonce {nonce}")
    if not meets_target(digest, job.target):
        raise SystemExit(f"FAIL exact target validation for nonce {nonce}")
    if share_difficulty(digest) < target_difficulty(job.target):
        raise SystemExit(
            "FAIL share difficulty below target: "
            f"share={format_difficulty(share_difficulty(digest))} "
            f"target={format_difficulty(target_difficulty(job.target))}"
        )


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
    validate_candidate(job, 0, GENESIS_EXPECTED_HASH_NONCE_ZERO.hex())

    quick3_job = build_job_from_header(GENESIS_HEADER, QUICK3_TARGET)
    quick3_payload = encode_job_payload(quick3_job)
    quick3_found = feed_bytewise(TangMinerEmulator(max_nonces=8), b"TNJ" + quick3_payload)
    if len(quick3_found) != 5 or quick3_found[:1] != b"F":
        raise SystemExit(f"FAIL quick3 found response: {quick3_found.hex()}")
    if quick3_found[1:5] != b"\x00\x00\x00\x03":
        raise SystemExit(f"FAIL quick3 nonce: {quick3_found[1:5].hex()}")
    validate_candidate(quick3_job, 3)

    for target, nonce, digest_hex in EXPECTED_ALIAS_CANDIDATES:
        validate_candidate(build_job_from_header(GENESIS_HEADER, target), nonce, digest_hex)

    hardcoded = feed_bytewise(emulator, b"TNH")
    if hardcoded != found:
        raise SystemExit(f"FAIL hardcoded job: {hardcoded.hex()}")

    print("PASS software emulator echo/hash/hardcoded paths")


if __name__ == "__main__":
    main()
