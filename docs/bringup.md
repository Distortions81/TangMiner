# Bring-Up

This checklist is for the active Tang Nano 20K SpinalHDL bitstream. The Tang
Nano 9K target is still useful for simulation and comparison, but the current
four-lane design is focused on the 20K.

## 1. Prepare Tools

Install or activate the FPGA and Python tools:

```sh
scripts/setup.sh
```

On Ubuntu 24.04, the repo installer can provision local ignored copies of OSS
CAD Suite and sbt:

```sh
scripts/setup.sh
```

## 2. Verify The Host-Side Model

Run the protocol smoke test before touching hardware:

```sh
make emu-smoke
```

Run the UART-level SpinalHDL RTL test when Java, sbt, and Verilator are
available:

```sh
scripts/sim.sh
```

The SpinalHDL cocotb run should include a `source=rtl_cycles` hashrate line.
For the current 20K model, the expected rate is:

```text
111,000,000 Hz / 16 clocks per aggregate nonce = 6.94 MH/s
```

## 3. Build The Bitstream

Build the default 20K target:

```sh
make build
```

Inspect the nextpnr output for timing and resource utilization. The selected
20K route reports `119.13 MHz` Fmax against the `111.00 MHz` hash clock
constraint.

## 4. Load Or Flash

Load to SRAM for quick iteration:

```sh
make load
```

Flash for persistent boot:

```sh
make flash
```

For Tang Nano 20K boards, this openFPGALoader form is often more reliable:

```sh
make load OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
make flash OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
```

If the onboard BL616 bridge was left in a non-UART mode, open its console and
select:

```text
choose uart
```

## 5. Check UART And Byte Order

The FPGA UART is `115200 8N1`.

```sh
. .venv/bin/activate
python scripts/tools/serial_smoke.py --echo --timeout 2 /dev/cu.usbserial-*
python scripts/tools/serial_smoke.py --timeout 3 /dev/cu.usbserial-*
```

The echo command should report `ECHO OK`. The hash command sends an easy
genesis-style job, expects an `F || nonce` response, and validates the returned
nonce by double-hashing on the host.

The important early check is endian correctness:

- `midstate`, `tail`, and target aliases are sent as big-endian SHA-256 values.
- The FPGA inserts the returned nonce bytes directly into header bytes `76..79`.
- The host owns Bitcoin wire-format conversion, host-side double hashing, exact
  target comparison, and pool share formatting.

## 6. Exercise Candidate Output

For frequent candidate output on the default 20K bitstream:

```sh
python scripts/tools/serial_smoke.py --target quick23 --watch --timeout 10 /dev/cu.usbserial-*
make hardware-mine MINE_ARGS='--target quick21 --count 5 /dev/cu.usbserial-*'
```

`quick21` is useful for frequent hardware-miner logs, `quick23` averages about
`1.2 s` per candidate, and `quick26` averages about `9.7 s` per candidate on
the `111 MHz` 20K build.

## 7. Integrate A Host Miner

Host software should:

1. Build or receive an 80-byte Bitcoin header.
2. Compute the SHA-256 midstate for header bytes `0..63`.
3. Send `TNJ + midstate[32] + tail[12] + target[32]`.
4. Read `F || nonce` responses.
5. Rebuild the header with the returned nonce bytes.
6. Double-hash and perform the authoritative share target check.

Mujina integration notes live in [mujina-integration.md](mujina-integration.md).
