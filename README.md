# TangMiner

![TangMiner board](tangminer.png)

TangMiner is an experimental Bitcoin hash engine for Sipeed Tang Nano FPGA
boards. The FPGA scans nonce ranges and reports candidates over USB-UART; the
host Stratum client handles pool work, full double-SHA256 validation, target
checks, and share submission.

This is a learning and integration project, not an economically useful miner.

## Current Status

Default target:

- Board: Tang Nano 20K, `GW2AR-LV18QN88C8/I7`, 27 MHz input clock.
- Design: SpinalHDL/Scala top module generated to Verilog.
- Known-good build: 5 lanes at `54.000 MHz`.
- Modeled rate: `54.000 MHz * 5 / 64 = 4.219 MH/s`.
- Hardware validation: strict host nonce validation passes on real hardware.

The open-source flow remains the hardware-validated default:

```text
TARGET=tangnano20k
SPINAL_LANES=5
SPINAL_CLOCK_PROFILE=54m
YOSYS_SYNTH_ARGS=-nowidelut
NEXTPNR_SEED=13
```

Official Gowin EDA timing results for the same 5-lane design:

| Clock profile | Result | Routed Fmax |
| --- | --- | --- |
| `54m` | closes with margin | `59.776 MHz` |
| `67m5` | barely closes | `67.505 MHz` |
| `81m` | fails setup timing | `68.218 MHz` |

Static timing closure is not hardware validation. Higher-clock images still
need strict host nonce validation before use.

## Quick Start

Install local tools on Ubuntu 24.04:

```sh
scripts/setup.sh
```

Build, flash, and mine:

```sh
scripts/flash-and-mine.sh /dev/ttyUSB0
```

Load to SRAM instead of persistent flash:

```sh
scripts/flash-and-mine.sh --load /dev/ttyUSB0
```

Tang Nano 20K boards are often more reliable with a slower JTAG clock and an
explicit FTDI channel:

```sh
OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000' \
  scripts/flash-and-mine.sh /dev/ttyUSB0
```

If the 20K BL616 bridge is not in UART mode, open its console and select:

```text
choose uart
```

## Build

Open-source bitstream flow:

```sh
make build
make load
make flash
```

Official Gowin EDA flow:

```sh
make gowin-fmax
make gowin-fmax SPINAL_CLOCK_PROFILE=67m5
```

The Gowin flow auto-detects `local/gowin-eda`, `../MIPS-FPGA/local/gowin-eda`,
`../TMS9900-FPGA/local/gowin-eda`, and `../FocusTerm/local/gowin-eda`. Set
`GOWIN_SH=/path/to/gw_sh` if Gowin is installed elsewhere.

Load a Gowin-built bitstream to SRAM and start the host miner:

```sh
make gowin-load-and-mine SERIAL_PORT=/dev/ttyUSB0
```

For the higher-clock Gowin build:

```sh
make gowin-load-and-mine SERIAL_PORT=/dev/ttyUSB0 SPINAL_CLOCK_PROFILE=67m5
```

Use `gowin-flash-and-mine` instead if you want to write the bitstream to
persistent FPGA flash.

Tang Nano 9K is available as a smaller experimental target:

```sh
make build TARGET=tangnano9k SPINAL_LANES=1
```

Generated bitstreams are written under `build/`.

## Run Host Miner

After loading or flashing the FPGA:

```sh
scripts/mine-hardware.sh /dev/ttyUSB0
```

Useful overrides:

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

## Test Without Hardware

```sh
python scripts/tools/emulator_smoke.py
scripts/mine-software.sh
scripts/mine-rtl.sh
scripts/sim.sh
make -C stratum test
make -C stratum smoke-fakes
```

## Common Options

Modeled hashrate:

```text
clock_hz * SPINAL_LANES / 64
```

`SPINAL_FIXED_CANDIDATE` values:

| Value | Mode |
| --- | --- |
| unset | infer from job target |
| `0` | always report |
| `1` | `quick3` |
| `2` | `quick21` |
| `3` | `quick23` |
| `4` | `quick26` |
| `5` | `quick14` |

Development image with UART echo and hardcoded smoke work:

```sh
make build TARGET=tangnano20k \
  SPINAL_LANES=4 \
  SPINAL_CLOCK_PROFILE=111m \
  SPINAL_ENABLE_ECHO=1 \
  SPINAL_ENABLE_HARDCODED=1 \
  SPINAL_FIXED_CANDIDATE=
```

## Layout

- `src/main/scala/tangminer/TangMiner.scala`: active SpinalHDL implementation.
- `constr/`: board constraints.
- `scripts/`: setup, build, flash, simulation, and mining helpers.
- `stratum/`: C Stratum client and fake pool/FPGA test tools.
- `sim/cocotb/`: UART-level RTL tests.
- `docs/`: protocol, hardware, emulation, and bring-up notes.

Start with:

- [docs/bringup.md](docs/bringup.md)
- [docs/uart-protocol.md](docs/uart-protocol.md)
- [docs/software-emulation.md](docs/software-emulation.md)
- [docs/hardware-overview.md](docs/hardware-overview.md)

## License

TangMiner is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
