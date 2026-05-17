# TangMiner

![TangMiner board](tangminer.png)

TangMiner is an experimental Bitcoin hash engine for Sipeed Tang Nano FPGA
boards. The active design targets the [Tang Nano 20K][tn20k] and is authored in
SpinalHDL/Scala, generated to Verilog, built with the open Gowin FPGA toolchain,
and driven over the board USB-UART by host mining software.

This is a learning and integration project, not an economically useful miner.
The design is intentionally small: the FPGA scans nonces, reports candidate
nonces, and leaves pool work, full double-hash validation, and share submission
to the host.

[tn20k]: https://wiki.sipeed.com/hardware/en/tang/tang-nano-20k/nano-20k.html

## Software Emulation

Use this path first if you want to exercise Stratum, share validation, and
submission without a Tang Nano board. It starts a software FPGA UART emulator,
connects the C Stratum client to it, and mines against the default pool.

```sh
make stratum-mine-software
```

Defaults:

- Pool: `tinyminer.m45core.com:3333`
- Worker: `3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45`
- Software FPGA gate: `quick3`
- Suggested difficulty: `0.0000046566`, about `15` shares/minute at `5 kH/s`
- Quiet logging: hides `share=no` candidates and still prints shares/submits.
  Use `VERBOSE=1 make stratum-mine-software` to see every candidate.

For manual software-emulated runs, start the emulator in one terminal and leave
it running:

```sh
python3 stratum/tools/fake_fpga.py --mode hash --target quick3 --max-nonces 100000
```

The fake FPGA `--target` is only the software candidate gate. Keep it on a
quick filter for 5 kH/s runs; the host still validates returned candidates
against the pool share target before submitting.

It prints a pseudo-terminal path such as:

```text
fake_fpga_pty=/dev/pts/7
```

In another terminal, replace `/dev/pts/N` with that printed path:

```sh
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/pts/N \
  --fpga-target quick3 \
  --suggest-difficulty 0.0000046566
```

Use `--no-submit` on the Stratum command to validate candidates without sending
shares to the pool.

## Real Hardware

Build and flash the selected Tang Nano 20K bitstream, then point the Stratum
client at the board UART.

```sh
make build
make load
make stratum-mine-hardware SERIAL_PORT=/dev/ttyUSB0
```

The default 20K bitstream runs four SHA-256 lanes at `111 MHz`, modeled as
`6.94 MH/s`. The hardware mining wrapper uses the `quick21` FPGA gate and a
matching suggested difficulty.

For manual hardware runs:

```sh
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/ttyUSB0 \
  --fpga-target quick21
```

## Quick Tests

```sh
make -C stratum
make -C stratum test
make -C stratum smoke-fakes
make emu-smoke
make sim-cocotb-spinal TARGET=tangnano20k SIM=verilator
```

## Current Status

- Default target: Tang Nano 20K (`TARGET=tangnano20k`).
- Gateware: four compact pass-pipelined SHA-256 lanes in
  `src/main/scala/tangminer/TangMiner.scala`.
- Clocking: onboard `27 MHz` through a Gowin `rPLL` to `111 MHz`.
- Modeled rate: one aggregate nonce every `16` fabric clocks, or `6.94 MH/s`.
- Timing: the selected 20K route reports `119.13 MHz` Fmax and passes the
  `111.00 MHz` constraint with `7.3%` margin.
- Protocol: fixed `115200 8N1` binary UART with nonce-only `F || nonce`
  candidate responses.
- Candidate filtering: cheap FPGA-side prefix filters (`quick3`, `quick21`,
  `quick23`, `quick26`) instead of a full 256-bit target comparator.
- Host contract: rebuild the 80-byte header, double-hash the returned nonce,
  perform the exact target check, and submit valid shares with the Stratum
  nonce byte order.

Tang Nano 9K board files remain in the tree for comparison and simulation, but
the current four-lane SpinalHDL bitstream is focused on the 20K area and timing
budget.

## Repository Map

- `src/main/scala/tangminer/TangMiner.scala`: active SpinalHDL implementation.
- `src/*.v`: legacy hand-written Verilog kept for comparison and Icarus tests.
- `constr/`: board-specific Gowin constraints.
- `scripts/`: packet generation, serial smoke tests, hardware mining logs, and
  software protocol emulation.
- `sim/cocotb/`: UART-level cocotb tests for legacy and SpinalHDL-generated RTL.
- `docs/hardware-overview.md`: datapath and nonce-loop overview.
- `docs/compression-circuitry.md`: SHA-256 compressor and pipeline details.
- `docs/uart-protocol.md`: host-to-FPGA packet contract.
- `docs/software-emulation.md`: Python emulator, PTY, and RTL simulation flows.
- `docs/bringup.md`: board bring-up checklist.
- `docs/mujina-integration.md`: notes for the experimental Mujina backend.

## Toolchain

The FPGA flow uses open tooling:

- [OSS CAD Suite](https://github.com/YosysHQ/oss-cad-suite-build), including
  Yosys, nextpnr-himbaechel, gowin_pack, Verilator, Icarus Verilog, and
  openFPGALoader.
- [sbt](https://github.com/sbt/sbt) and OpenJDK for SpinalHDL generation.
- Python 3 with `cocotb` and `pyserial` for emulation and simulation helpers.

On Ubuntu 24.04, the repo installer sets up `.venv`, downloads a local sbt, and
downloads OSS CAD Suite into ignored `local/` paths:

```sh
scripts/install_ubuntu_24_04.sh --target tangnano20k
```

For a manual install, activate OSS CAD Suite before building or simulating:

```sh
source "$HOME/oss-cad-suite/environment"
make setup-emulation
```

The Makefile defaults to `OSS_CAD_SUITE=$HOME/oss-cad-suite`. Override tool
paths when needed:

```sh
make build OSS_CAD_SUITE=/path/to/oss-cad-suite
make flash OPENFPGALOADER=openFPGALoader
```

## Build

Build the default Tang Nano 20K SpinalHDL bitstream:

```sh
make build
```

This generates `build/spinal/tangnano20k/top.v`, synthesizes it, places and
routes it, and writes `build/tangminer_spinal_tangnano20k.fs`.

Useful build targets:

```sh
make spinal-verilog
make build TARGET=tangnano9k
make build-verilog
```

`build-verilog` uses the legacy hand-written Verilog path. The 9K target is
useful for comparison and simulation naming, but the active four-lane design is
not area-validated for the 9K.

## Simulate And Emulate

Set up Python helpers:

```sh
make setup-emulation
```

Run the software protocol smoke test:

```sh
make emu-smoke TARGET=tangnano9k
```

Run a software-only miner-style candidate log:

```sh
make software-mine
make software-mine MINE_ARGS='--count 5 --verbose'
```

Run a pseudo-terminal that behaves like the FPGA UART:

```sh
scripts/run_emulator.sh --no-auto-benchmark --max-nonces 1000 --stats-interval 0
make hardware-mine MINE_ARGS='--target quick3 --count 3 /dev/pts/N'
```

Run UART-level RTL simulation against SpinalHDL-generated Verilog:

```sh
make sim-cocotb-spinal TARGET=tangnano9k SIM=verilator
```

See [docs/software-emulation.md](docs/software-emulation.md) for the full set of
emulator, PTY, and cocotb options.

## Load Or Flash

Load the SpinalHDL bitstream to SRAM:

```sh
make load
```

Flash it:

```sh
make flash
```

For Tang Nano 20K boards, explicitly selecting the FTDI channel and a slower
JTAG clock is often more reliable:

```sh
make load OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
make flash OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
```

If the 20K BL616 bridge was switched away from UART mode, open its console and
select:

```text
choose uart
```

## Hardware Smoke Tests

The board UART is fixed at `115200 8N1`.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements-emulation.txt

python scripts/serial_smoke.py --echo --timeout 2 /dev/cu.usbserial-*
python scripts/serial_smoke.py --timeout 3 /dev/cu.usbserial-*
```

For repeated candidate output on the default 20K build:

```sh
python scripts/serial_smoke.py --target quick23 --watch --timeout 10 /dev/cu.usbserial-*
make hardware-mine MINE_ARGS='--target quick21 /dev/cu.usbserial-*'
```

`quick21` is the default `hardware_mine.py` target for frequent miner-style
logs. `quick23` averages about `1.2 s` per candidate on the 20K build, and
`quick26` averages about `9.7 s`.

## UART Contract

Every command starts with `TN` and a one-byte command:

- `TNJ + midstate[32] + tail[12] + target[32]`: start a job.
- `TNE + midstate[32] + tail[12] + target[32]`: echo parsed payload.
- `TNS`: stop the current job.
- `TNH`: start a built-in genesis-style smoke-test job.

Found response:

```text
"F"
nonce[4]
```

The nonce bytes are the exact bytes inserted into header bytes `76..79` before
hashing. The host must validate every returned candidate before submitting it.
See [docs/uart-protocol.md](docs/uart-protocol.md) for the complete protocol.

## Hardware Notes

- Tang Nano 20K FPGA: `GW2AR-LV18QN88C8/I7`, family `GW2A-18C`.
- Tang Nano 9K FPGA: `GW1NR-LV9QN88PC6/I5`, family `GW1N-9C`.
- Board clock: `27 MHz`.
- 20K hash clock: internal rPLL to `111 MHz`.
- 9K clock path: direct `27 MHz` board clock.
- UART: `115200 8N1`, no flow control.

The LED, clock, and UART pins follow the board-specific constraints in
`constr/`.

## License

TangMiner is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
