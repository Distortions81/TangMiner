# Mujina Integration Notes

Mujina is a Rust mining workspace centered around `mujina-minerd`. The TangMiner
integration should sit at the same boundary as a small hardware backend: Mujina
receives pool work, converts it into a compact FPGA job, reads candidate nonces,
and validates shares on the host.

## Backend Boundary

The backend needs four pieces:

- serial port discovery and `115200 8N1` configuration for the Tang Nano
  USB-UART
- job conversion from Mujina work into `midstate`, `tail`, and `target`
- response parsing for `F || nonce`
- host-side share validation and submission through Mujina's existing pipeline

TangMiner does not currently expose temperature, frequency control, lane status,
nonce-range completion, or target-hit hashes. Those can be added later as new
command bytes without changing the basic `TNJ` job path.

## Endian Contract

Keep the FPGA simple:

- The FPGA consumes SHA-256 big-endian words.
- The FPGA returns only the four nonce bytes inserted into the hashed header.
- The FPGA uses a cheap Bitcoin-order hash prefix filter, not a full 256-bit
  target comparator.
- Mujina owns Bitcoin wire-format conversion, compact target expansion,
  host-side double hashing, exact target comparison, and pool share formatting.

The current 20K bitstream starts four lanes at nonce words `0`, `1`, `2`, and
`3`; each lane then advances by `4`. Mujina should treat returned nonce bytes as
header wire order, copy them into header bytes `76..79`, and then let its normal
block-hash path decide whether the share is valid.

## First Useful Milestone

Start with a non-pooled dummy-work path:

1. Generate a synthetic 80-byte block header.
2. Set an easy target alias such as `all-ones` or `quick21`.
3. Send one `TNJ` packet.
4. Confirm the FPGA returns `F || nonce`.
5. Recompute the double-SHA-256 hash through Mujina's CPU path.
6. Reject or accept the candidate using the exact target comparison.

Once that is stable, pool work can use the same packet and validation path.

## Local Smoke-Test Pattern

The exact Mujina branch and environment variable names may change while the
backend is experimental. A typical dummy-work run looks like:

```sh
cd /path/to/mujina
MUJINA_TANG_NANO_PORT=/dev/cu.usbserial-1101 \
MUJINA_USB_DISABLE=1 \
RUST_LOG=mujina_miner=debug \
cargo run -p mujina-miner --bin mujina-minerd
```

Useful log evidence:

```text
fpga_miner::thread: sending Tang Nano work
scheduler: Share found
```

For Stratum work, add the usual Mujina pool variables, such as
`MUJINA_POOL_URL`, `MUJINA_POOL_USER`, and optionally
`MUJINA_POOL_FORCED_RATE` while the FPGA candidate rate is still low.

## Protocol Reference

See [uart-protocol.md](uart-protocol.md) for packet layout and target aliases.
For development without hardware, run the PTY emulator:

```sh
scripts/run_emulator.sh --no-auto-benchmark --max-nonces 1000 --stats-interval 0
```

Then point the Mujina serial path at the printed `/dev/pts/N`.
