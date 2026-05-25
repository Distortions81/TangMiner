# Bring-Up

This checklist is for the active Tang Nano 20K SpinalHDL bitstream and the C
Stratum host program.

## 1. Prepare Tools

On Ubuntu 24.04, install the local Python, sbt, and OSS CAD Suite dependencies:

```sh
scripts/setup.sh
```

## 2. Verify The Host And RTL Models

Run the lightweight protocol smoke test:

```sh
python scripts/tools/emulator_smoke.py
```

Run the UART-level RTL tests:

```sh
scripts/sim.sh
```

The current 20K model should report about `7.84 MH/s`:

```text
100,286,000 Hz * 5 lanes / 64 = 7.835 MH/s
```

## 3. Flash Or Load And Mine

For the normal persistent flow:

```sh
scripts/flash-and-mine.sh /dev/ttyUSB0
```

For volatile SRAM loading during iteration:

```sh
scripts/flash-and-mine.sh --load /dev/ttyUSB0
```

This builds the bitstream, programs the board, builds the C Stratum client if
needed, and then runs `scripts/mine-hardware.sh` against the selected serial
port.

For Tang Nano 20K boards, this programmer command is often more reliable:

```sh
OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000' \
  scripts/flash-and-mine.sh /dev/ttyUSB0
```

If the onboard BL616 bridge was left in a non-UART mode, open its console and
select:

```text
choose uart
```

## 4. Build And Program Manually

Build the default 20K bitstream:

```sh
make build
```

Load to SRAM:

```sh
make load
```

Flash persistently:

```sh
make flash
```

Run the host after programming:

```sh
scripts/mine-hardware.sh /dev/ttyUSB0
```

## 5. UART Smoke Tests

The FPGA UART is `115200 8N1`.

```sh
python scripts/tools/serial_smoke.py --timeout 3 /dev/ttyUSB0
python scripts/tools/serial_smoke.py --target quick23 --watch --timeout 10 /dev/ttyUSB0
```

The smoke test sends an easy genesis-style job, expects an `F || nonce`
response, and validates the returned nonce by double-hashing on the host.

The endian contract is:

- `midstate`, `tail`, and target aliases are sent as big-endian SHA-256 values.
- The FPGA inserts the returned nonce bytes directly into header bytes `76..79`.
- The host owns Bitcoin wire-format conversion, host-side double hashing, exact
  target comparison, and pool share formatting.

## 6. Host Miner Responsibilities

Host software should:

1. Build or receive an 80-byte Bitcoin header.
2. Compute the SHA-256 midstate for header bytes `0..63`.
3. Send `TNJ + midstate[32] + tail[12] + target[32]`.
4. Read `F || nonce` responses.
5. Rebuild the header with the returned nonce bytes.
6. Double-hash and perform the authoritative share target check.

Mujina integration notes live in [mujina-integration.md](mujina-integration.md).
