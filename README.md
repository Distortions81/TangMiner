# TangMiner

![TangMiner board](tangminer.png)

TangMiner is an experimental Bitcoin hash engine for Sipeed Tang Nano FPGA
boards. The supported hardware path is:

1. Generate the FPGA design from SpinalHDL/Scala.
2. Build a Gowin bitstream with the open OSS CAD Suite flow.
3. Flash or load the bitstream with `openFPGALoader`.
4. Run the C Stratum host program over the board USB-UART.

This is a learning and integration project, not an economically useful miner.
The FPGA scans nonces and reports candidate nonces. The host handles Stratum
pool work, full double-SHA256 validation, target checks, and share submission.

## Supported Boards

- Tang Nano 20K: default target, `TARGET=tangnano20k`.
- Tang Nano 9K: available with `TARGET=tangnano9k`, but use a smaller lane
  count such as `SPINAL_LANES=1` or `SPINAL_LANES=2`.

The current 20K default is a production-oriented 5-lane build at `100.286 MHz`
with seed `13`, modeled at about `7.84 MH/s`:

```text
100.286 MHz * 5 lanes / 64 = 7.835 MH/s
```

## Quick Start

Set up local tools on Ubuntu 24.04:

```sh
scripts/setup.sh
```

Build, flash, and run the host-side miner in one command:

```sh
scripts/flash-and-mine.sh /dev/ttyUSB0
```

For a non-persistent SRAM load instead of flash:

```sh
scripts/flash-and-mine.sh --load /dev/ttyUSB0
```

The same flow is exposed through Make:

```sh
make flash-and-mine SERIAL_PORT=/dev/ttyUSB0
make load-and-mine SERIAL_PORT=/dev/ttyUSB0
```

For Tang Nano 20K boards, a slower JTAG clock and explicit FTDI channel are
often more reliable:

```sh
OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000' \
  scripts/flash-and-mine.sh /dev/ttyUSB0
```

If the 20K BL616 bridge is not in UART mode, open its console and select:

```text
choose uart
```

## Build And Program Separately

Build the default Tang Nano 20K bitstream:

```sh
make build
```

Build a smaller Tang Nano 9K image:

```sh
make build TARGET=tangnano9k SPINAL_LANES=1
```

Load to SRAM:

```sh
make load
```

Flash persistently:

```sh
make flash
```

Generated bitstreams are written under `build/`, for example:

- `build/tangminer_spinal_tangnano20k.fs`
- `build/tangminer_spinal_tangnano9k.fs`

Use the same `TARGET`, `SPINAL_LANES`, and clock options for `build`, `load`,
and `flash`. If you omit them, the Makefile uses the target defaults.

## Run The Host Program

After the FPGA is loaded or flashed, run the C Stratum host against the board
UART:

```sh
scripts/mine-hardware.sh /dev/ttyUSB0
```

The wrapper builds `stratum/build/stratum-client` if needed and then connects
to the default pool. The board UART is fixed at `115200 8N1`.

Useful environment overrides:

```sh
STRATUM_HOST=pool.example.com
STRATUM_PORT=3333
STRATUM_USER='wallet.worker'
STRATUM_PASS=x
HARDWARE_FPGA_TARGET=quick21
HARDWARE_SUGGEST_DIFFICULTY=0.00646187
NO_SUBMIT=1
VERBOSE=1
```

Manual equivalent:

```sh
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/ttyUSB0 \
  --fpga-target quick21 \
  --suggest-difficulty 0.00646187
```

## Test Without Hardware

Run the UART protocol smoke test:

```sh
python scripts/tools/emulator_smoke.py
```

Mine through the Python fake FPGA and the real C Stratum host:

```sh
scripts/mine-software.sh
```

Mine through the Verilated SpinalHDL UART design:

```sh
scripts/mine-rtl.sh
```

Run the cocotb RTL simulator:

```sh
scripts/sim.sh
```

Run the C Stratum tests:

```sh
make -C stratum test
make -C stratum smoke-fakes
```

## Bitstream Options

The default 20K build uses:

```text
TARGET=tangnano20k
SPINAL_LANES=5
SPINAL_CLOCK_PROFILE=100m286
SPINAL_ENABLE_ECHO=0
SPINAL_ENABLE_HARDCODED=0
SPINAL_FIXED_CANDIDATE=2
NEXTPNR_SEED=13
```

For a development-friendly image with UART echo and the hardcoded smoke job:

```sh
make build TARGET=tangnano20k \
  SPINAL_LANES=4 \
  SPINAL_CLOCK_PROFILE=111m \
  SPINAL_ENABLE_ECHO=1 \
  SPINAL_ENABLE_HARDCODED=1 \
  SPINAL_FIXED_CANDIDATE=
```

`SPINAL_FIXED_CANDIDATE` values are `0` for always report, `1` for `quick3`,
`2` for `quick21`, `3` for `quick23`, `4` for `quick26`, and `5` for `quick14`.
Leave it unset when you want the FPGA to infer the filter from each job target.

Modeled hashrate is:

```text
clock_hz * SPINAL_LANES / 64
```

## Project Layout

- `src/main/scala/tangminer/TangMiner.scala`: active SpinalHDL implementation.
- `constr/`: Tang Nano board constraints.
- `scripts/*.sh`: main user-facing setup, simulation, flash, and mining flows.
- `scripts/helpers/`: lower-level shell helpers used by scripts and Make.
- `scripts/tools/`: protocol emulator, UART smoke tests, and build utilities.
- `stratum/`: C Stratum client and fake pool/FPGA test tools.
- `sim/cocotb/`: UART-level RTL tests against generated SpinalHDL Verilog.
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
- 20K hash clock: internal rPLL to `100.286 MHz`.
- 9K clock path: direct `27 MHz` board clock.
- Protocol: binary UART packets starting with `TN`.

## License

TangMiner is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
