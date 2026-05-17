# UART Protocol

The FPGA side intentionally uses a tiny binary protocol so Mujina can treat the Tang Nano board like a simple hash engine.

UART settings:

- `115200`
- `8N1`
- no flow control

All multi-byte fields are sent big-endian.

## Start Job

Host to FPGA:

```text
"T" "N" "J"
midstate[32]
tail[12]
target[32]
```

Total length: `79` bytes.

Fields:

- `midstate`: SHA-256 internal state after the first 64 bytes of the Bitcoin block header.
- `tail`: bytes 64 through 75 of the 80-byte block header, excluding the nonce. These are interpreted as three big-endian SHA-256 message words.
- `target`: 256-bit big-endian integer target. In the current four-lane 20K bitstream this field selects a cheap FPGA-side candidate filter; the host still performs the authoritative full target comparison after receiving a nonce.

The repository tools accept these target aliases:

- `all-ones` / `easy`: always report the first checked nonce.
- `quick3`: require the top 3 bits of `reverse_bytes(hash)` to be zero.
- `quick21`: require the top 21 bits of `reverse_bytes(hash)` to be zero.
- `quick23`: require the top 23 bits of `reverse_bytes(hash)` to be zero.

At the default `27 MHz` clock, `quick23` averages roughly 10 seconds per
candidate with four lanes:

```text
000001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
```

Arbitrary target values currently select the same `quick23` hardware candidate
filter. This keeps the FPGA comparator tiny and leaves exact share validation on
the host.

The FPGA constructs the final first-pass SHA-256 block as:

```text
tail[0:12] || nonce || 0x80 || zero padding || 0x00000280
```

The current 20K bitstream starts four internal lanes at nonces `0`, `1`, `2`,
and `3`; each lane then increments by `4`. Each lane performs the second
SHA-256 pass over the 32-byte first digest.

## Stop Job

Host to FPGA:

```text
"T" "N" "S"
```

## Found Response

FPGA to host:

```text
"F"
nonce[4]
```

Total length: `5` bytes.

The nonce is returned in the four wire-order bytes used in the hashed Bitcoin
header. The FPGA does not return the hash; the host rebuilds the 80-byte header,
double-hashes it, and checks the resulting proof-of-work integer against the
actual share target.

## Mujina Integration Sketch

A Mujina driver can sit at the same layer as an ASIC hashboard driver:

1. Convert pool work into a block header and target.
2. Precompute SHA-256 midstate for bytes `0..63` of the header.
3. Send `TNJ` job packets with unique work, such as distinct extranonce2/merkle roots.
4. Listen for `F` responses.
5. Reconstruct, hash, validate, and submit shares after converting the nonce endian form expected by the pool stack.

This first protocol does not include temperature, clock control, nonce range completion, or lane enumeration. Those can be added as separate command bytes once the basic hash path is verified.

For standalone testing, `scripts/make_job.py` emits this packet format from an 80-byte header and a big-endian target.
