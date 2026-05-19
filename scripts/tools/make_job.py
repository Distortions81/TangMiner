#!/usr/bin/env python3
import argparse
import hashlib
import sys


ALL_ONES_TARGET = b"\xff" * 32
QUICK3_TARGET = bytes.fromhex("1fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
QUICK21_TARGET = bytes.fromhex("000007ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
QUICK23_TARGET = bytes.fromhex("000001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
QUICK26_TARGET = bytes.fromhex("0000003fffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
DIFFICULTY_1_TARGET = int(
    "00000000ffff0000000000000000000000000000000000000000000000000000",
    16,
)

IV = (
    0x6A09E667,
    0xBB67AE85,
    0x3C6EF372,
    0xA54FF53A,
    0x510E527F,
    0x9B05688C,
    0x1F83D9AB,
    0x5BE0CD19,
)

K = (
    0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
    0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
    0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
    0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
    0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
    0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
    0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
    0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
    0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
    0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
    0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
    0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
    0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
    0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
    0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
    0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
)


def rotr(x, n):
    return ((x >> n) | (x << (32 - n))) & 0xFFFFFFFF


def compress(state, block):
    w = [int.from_bytes(block[i:i + 4], "big") for i in range(0, 64, 4)]
    for i in range(16, 64):
        s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3)
        s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10)
        w.append((w[i - 16] + s0 + w[i - 7] + s1) & 0xFFFFFFFF)

    a, b, c, d, e, f, g, h = state
    for i in range(64):
        s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
        ch = (e & f) ^ (~e & g)
        t1 = (h + s1 + ch + K[i] + w[i]) & 0xFFFFFFFF
        s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
        maj = (a & b) ^ (a & c) ^ (b & c)
        t2 = (s0 + maj) & 0xFFFFFFFF
        h, g, f, e, d, c, b, a = g, f, e, (d + t1) & 0xFFFFFFFF, c, b, a, (t1 + t2) & 0xFFFFFFFF

    return tuple((x + y) & 0xFFFFFFFF for x, y in zip(state, (a, b, c, d, e, f, g, h)))


def words_to_bytes(words):
    return b"".join(word.to_bytes(4, "big") for word in words)


def pow_hash_value(digest):
    return int.from_bytes(digest[::-1], "big")


def meets_target(digest, target):
    return pow_hash_value(digest) <= int.from_bytes(target, "big")


def share_difficulty(digest):
    value = pow_hash_value(digest)
    if value == 0:
        return float("inf")
    return DIFFICULTY_1_TARGET / value


def target_difficulty(target):
    value = int.from_bytes(target, "big")
    if value == 0:
        return float("inf")
    return DIFFICULTY_1_TARGET / value


def format_difficulty(difficulty):
    if difficulty == float("inf"):
        return "inf"
    if difficulty == 0:
        return "0"
    if difficulty < 0.001 or difficulty >= 1_000_000:
        return f"{difficulty:.6e}"
    return f"{difficulty:.6f}".rstrip("0").rstrip(".")


def format_difficulty_units(difficulty):
    if difficulty == float("inf"):
        return "inf D"
    if difficulty == 0:
        return "0 D"

    units = (
        (1_000_000_000_000.0, "TD"),
        (1_000_000_000.0, "GD"),
        (1_000_000.0, "MD"),
        (1_000.0, "kD"),
        (1.0, "D"),
        (0.001, "mD"),
        (0.000001, "uD"),
        (0.000000001, "nD"),
        (0.000000000001, "pD"),
    )
    abs_difficulty = abs(difficulty)
    for scale, suffix in units:
        if abs_difficulty >= scale:
            return f"{format_difficulty(difficulty / scale)} {suffix}"
    return f"{difficulty:.6e} D"


def parse_target(value):
    name = value.lower().replace("_", "-")
    if name in ("all-ones", "allones", "easy"):
        return ALL_ONES_TARGET
    if name in ("quick3", "quick-3", "test3", "test-3"):
        return QUICK3_TARGET
    if name in ("quick21", "quick-21"):
        return QUICK21_TARGET
    if name in ("quick23", "quick-23", "10s", "ten-second", "ten-seconds", "10s4", "ten-second-4-lane", "ten-seconds-4-lane"):
        return QUICK23_TARGET
    if name in ("quick26", "quick-26"):
        return QUICK26_TARGET
    return bytes.fromhex(value)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--header", required=True, help="80-byte Bitcoin header as hex, wire order")
    parser.add_argument(
        "--target",
        required=True,
        help="32-byte big-endian target hex, or alias quick23/quick26/quick21/quick3/all-ones",
    )
    parser.add_argument("--verify", action="store_true", help="print the double-SHA256 hash for the header")
    args = parser.parse_args()

    header = bytes.fromhex(args.header)
    target = parse_target(args.target)

    if len(header) != 80:
        raise SystemExit("header must be exactly 80 bytes")
    if len(target) != 32:
        raise SystemExit("target must be exactly 32 bytes")

    midstate = words_to_bytes(compress(IV, header[:64]))
    tail = header[64:76]
    packet = b"TNJ" + midstate + tail + target

    if args.verify:
        print(hashlib.sha256(hashlib.sha256(header).digest()).digest().hex(), file=sys.stderr)

    sys.stdout.buffer.write(packet)


if __name__ == "__main__":
    main()
