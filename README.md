# TangMiner

![TangMiner board](tangminer.png)

TangMiner is an experimental Bitcoin hash engine for Sipeed Tang Nano FPGA
boards. The active design is written in SpinalHDL/Scala, generated to Verilog,
built with the open Gowin FPGA toolchain, and driven from a host over USB-UART.

This is a learning and integration project, not an economically useful miner.
The FPGA scans nonces and reports candidate nonces. The host handles Stratum
pool work, full double-SHA256 validation, target checks, and share submission.

## Supported Boards

- Tang Nano 20K: default target, `TARGET=tangnano20k`.
- Tang Nano 9K: available with `TARGET=tangnano9k`, but do not expect the
  default four-lane 20K design to fit. Build a smaller lane count with
  `SPINAL_LANES=1` or `SPINAL_LANES=2`.

`TARGET` selects the board, FPGA family, device, and constraints. `SPINAL_LANES`
selects how many top-level SHA-256 lanes are generated. More lanes use more
FPGA area and increase modeled hashrate; fewer lanes are the first thing to try
on the 9K.

## Setup

On Ubuntu 24.04, use the installer. It creates `.venv`, downloads a local `sbt`,
and installs OSS CAD Suite under ignored `local/` paths.

```sh
scripts/install_ubuntu_24_04.sh --target tangnano20k
```

For a manual setup, install or unpack OSS CAD Suite, then set up the Python
helpers:

```sh
source "$HOME/oss-cad-suite/environment"
make setup-emulation
```

The Makefile expects OSS CAD Suite at `$HOME/oss-cad-suite` by default. Override
it when needed:

```sh
make build OSS_CAD_SUITE=/path/to/oss-cad-suite
```

Main tools used by the repo:

- OSS CAD Suite: Yosys, nextpnr-himbaechel, gowin_pack, Verilator, Icarus
  Verilog, openFPGALoader.
- OpenJDK and `sbt` for SpinalHDL generation.
- Python 3 with `cocotb` and `pyserial` for simulation and UART helpers.
- A C compiler and `make` for the Stratum client in `stratum/`.

## Build

Build the default Tang Nano 20K bitstream:

```sh
make build
```

Build explicitly for the 20K:

```sh
make build TARGET=tangnano20k
```

Build for the 9K with fewer lanes:

```sh
make build TARGET=tangnano9k SPINAL_LANES=1
make build TARGET=tangnano9k SPINAL_LANES=2
```

The default lane count is `SPINAL_LANES=4`, which is intended for the 20K. If a
9K build fails placement, routing, or resource use, reduce `SPINAL_LANES` and
build again.

Generated bitstreams are written under `build/`, for example:

- `build/tangminer_spinal_tangnano20k.fs`
- `build/tangminer_spinal_tangnano9k.fs`

The filename includes the board target, not the lane count. Keep the lane count
in your command history or build notes when comparing bitstreams.

Useful build variants:

```sh
make spinal-verilog TARGET=tangnano20k
make spinal-verilog TARGET=tangnano9k SPINAL_LANES=1
make build-verilog TARGET=tangnano9k
```

`build-verilog` uses the legacy hand-written Verilog path. The default `build`
target uses the SpinalHDL design.

For a smaller production-oriented 20K build, remove UART echo support, remove
the hardcoded smoke-test job, and fix the FPGA candidate filter to the Stratum
wrapper's default `quick21` mode:

```sh
make build TARGET=tangnano20k \
  SPINAL_ENABLE_ECHO=0 \
  SPINAL_ENABLE_HARDCODED=0 \
  SPINAL_FIXED_CANDIDATE=2
```

`SPINAL_FIXED_CANDIDATE` values are `0` for always report, `1` for `quick3`,
`2` for `quick21`, `3` for `quick23`, and `4` for `quick26`. Leave it unset
when you want the FPGA to infer the filter from target aliases in each job.
The round-skipped SHA path uses independent round-constant lookups for the
first and second compression engines in each lane. Set `SPINAL_ROUND_SKIP=0`
to build the experimental full 64-round A/B path instead.

Experimental wider lanes can be generated with `SPINAL_PAIRS_PER_LANE=2` or
`SPINAL_PAIRS_PER_LANE=4`. This keeps multiple A/B compressor pairs inside one
local lane wrapper, sharing job state, first-pass prefix preparation,
round-constant lookup wires, and local result selection. The default remains
`SPINAL_PAIRS_PER_LANE=1`.

Modeled hashrate is:

```text
clock_hz * SPINAL_LANES * SPINAL_PAIRS_PER_LANE / rounds_per_nonce
```

`rounds_per_nonce` is `61` when `SPINAL_ROUND_SKIP=1` and `64` when
`SPINAL_ROUND_SKIP=0`. The default 20K build uses four lanes at `111 MHz`, or
about `7.28 MH/s`. A 9K build uses the direct `27 MHz` clock, so one lane is
about `443 kH/s` and two lanes are about `885 kH/s`, before any real hardware
effects.

## Load Or Flash

Load the bitstream to SRAM:

```sh
make load TARGET=tangnano20k
make load TARGET=tangnano9k SPINAL_LANES=1
```

Flash it:

```sh
make flash TARGET=tangnano20k
make flash TARGET=tangnano9k SPINAL_LANES=1
```

Use the same `TARGET`, `SPINAL_LANES`, `SPINAL_PAIRS_PER_LANE`, and
`SPINAL_ROUND_SKIP` values for `build`, `load`, and `flash`. If you omit
`SPINAL_LANES`, the Makefile falls back to four lanes. If you omit
`SPINAL_PAIRS_PER_LANE`, it falls back to one A/B pair per lane.

For Tang Nano 20K boards, selecting the FTDI channel and using a slower JTAG
clock is often more reliable:

```sh
make load TARGET=tangnano20k OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
make flash TARGET=tangnano20k OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
```

If the 20K BL616 bridge is not in UART mode, open its console and select:

```text
choose uart
```

## Test

Run the fast software and Stratum tests:

```sh
make setup-emulation
make emu-smoke
make -C stratum
make -C stratum test
make -C stratum smoke-fakes
```

Run cocotb against SpinalHDL-generated RTL:

```sh
make sim-cocotb-spinal TARGET=tangnano20k SIM=verilator
make sim-cocotb-spinal TARGET=tangnano9k SPINAL_LANES=1 SIM=verilator
```

Run the legacy Verilog tests:

```sh
make sim
```

## Software Emulation

Use this before connecting hardware. It starts a fake FPGA UART, runs the C
Stratum client, validates candidates, and mines against the default pool.

```sh
make stratum-mine-software
```

Defaults:

- Pool: `tinyminer.m45core.com:3333`
- Worker: `3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45`
- Software FPGA filter: `quick3`
- Suggested difficulty: `0.0000046566`

Use `VERBOSE=1 make stratum-mine-software` to print every candidate. Use
`--no-submit` with `stratum/build/stratum-client` when manually testing without
submitting shares.

## Hardware Mining

Build and load the 20K bitstream, then point the Stratum wrapper at the board
UART:

```sh
make build TARGET=tangnano20k
make load TARGET=tangnano20k
make stratum-mine-hardware SERIAL_PORT=/dev/ttyUSB0
```

The hardware wrapper defaults to the `quick21` FPGA candidate filter. The board
UART is fixed at `115200 8N1`.

For a simple UART smoke test:

```sh
python scripts/serial_smoke.py --echo --timeout 2 /dev/ttyUSB0
python scripts/serial_smoke.py --target quick23 --watch --timeout 10 /dev/ttyUSB0
```

## Project Layout

- `src/main/scala/tangminer/TangMiner.scala`: active SpinalHDL implementation.
- `src/*.v`: legacy hand-written Verilog.
- `constr/`: Tang Nano board constraints.
- `scripts/`: setup, emulation, UART smoke tests, and hardware mining helpers.
- `stratum/`: C Stratum client and fake pool/FPGA test tools.
- `sim/cocotb/`: UART-level RTL tests.
- `docs/`: detailed protocol, hardware, emulation, and bring-up notes.

Start with these docs when you need more detail:

- [docs/bringup.md](docs/bringup.md)
- [docs/uart-protocol.md](docs/uart-protocol.md)
- [docs/software-emulation.md](docs/software-emulation.md)
- [docs/hardware-overview.md](docs/hardware-overview.md)

## Hardware Notes

- Tang Nano 20K FPGA: `GW2AR-LV18QN88C8/I7`, family `GW2A-18C`.
- Tang Nano 9K FPGA: `GW1NR-LV9QN88PC6/I5`, family `GW1N-9C`.
- Board clock: `27 MHz`.
- 20K hash clock: internal rPLL to `111 MHz`.
- 9K clock path: direct `27 MHz` board clock.
- Protocol: binary UART packets starting with `TN`.

The selected 20K build currently uses four compact single-pair SHA-256 lanes.
The experimental paired-lane shape uses fewer top-level lanes with multiple
local A/B pairs each. For the 9K, start with one lane and only increase after
nextpnr reports that placement, routing, timing, and utilization are acceptable.

## License

TangMiner is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
