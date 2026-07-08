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
- Default build: official Gowin EDA, 6 lanes at `67.500 MHz`, one-cycle
  pass-output fence, minimized SHA reset fanout.
- Modeled rate: `67.500 MHz * 6 / 65 = 6.231 MH/s`.
- Hardware validation: strict host nonce validation passes on real hardware
  with 100/100 `quick21` and 20/20 `quick14` checks.

The selected 20K default is:

```text
TARGET=tangnano20k
DEFAULT_FLOW=gowin
SPINAL_LANES=6
SPINAL_CLOCK_PROFILE=67m5
SPINAL_REGISTER_PASS_OUTPUTS=1
SPINAL_MINIMIZE_SHA_RESET=1
SPINAL_ROUND_SKIP=0
SPINAL_CSA_ROUND=0
```

The previous open-source `5x54` image remains a hardware-validated fallback,
but it is no longer the default.

Official Gowin EDA timing results for recent 20K builds:

| Build | Result | Hardware |
| --- | --- | --- |
| 6 lanes, `67m5`, pass fence, minimized reset | closes at `67.682 MHz` | 100/100 `quick21`, 20/20 `quick14` valid |
| 6 lanes, `67m5`, pass fence only | fails setup timing | not flashed |
| 6 lanes, `67m5`, pass fence, registered K | closes at `67.505 MHz` | invalid quick21 nonces |

Static timing closure is not hardware validation. Any new image still needs
strict host nonce validation before use.

## Quick Start

Install local tools on Ubuntu 24.04:

```sh
scripts/setup.sh
```

The default 20K build also needs Official Gowin EDA with a valid license. The
Makefile auto-detects local installs under `local/gowin-eda` and several sibling
repo paths, or you can set `GOWIN_SH=/path/to/gw_sh`.

Build, flash, and mine:

```sh
scripts/flash-and-mine.sh /dev/ttyUSB1
```

Load to SRAM instead of persistent flash:

```sh
scripts/flash-and-mine.sh --load /dev/ttyUSB1
```

Tang Nano 20K boards are often more reliable with a slower JTAG clock and an
explicit FTDI channel:

```sh
OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000' \
  scripts/flash-and-mine.sh /dev/ttyUSB1
```

On the Sipeed FTDI bridge, JTAG is commonly `/dev/ttyUSB0` and the FPGA UART is
commonly `/dev/ttyUSB1`; pass the UART port to the miner.

If the 20K BL616 bridge is not in UART mode, open its console and select:

```text
choose uart
```

## Build

Default 20K build flow, using Official Gowin EDA:

```sh
make build
make load
make flash
```

Print the Gowin timing summary for the default build:

```sh
make gowin-fmax
```

The Gowin flow auto-detects `local/gowin-eda`, `../MIPS-FPGA/local/gowin-eda`,
`../TMS9900-FPGA/local/gowin-eda`, and `../FocusTerm/local/gowin-eda`. Set
`GOWIN_SH=/path/to/gw_sh` if Gowin is installed elsewhere.

Load a Gowin-built bitstream to SRAM and start the host miner:

```sh
make gowin-load-and-mine SERIAL_PORT=/dev/ttyUSB1
```

Use `gowin-flash-and-mine` instead if you want to write the bitstream to
persistent FPGA flash.

The previous hardware-validated open-source build is still available:

```sh
DEFAULT_FLOW=oss make build
```

For Tang Nano 20K, `DEFAULT_FLOW=oss` selects the historical `5x54`
`synth_gowin -nowidelut` fallback unless you override the lane or clock
settings.

Tang Nano 9K is available as a smaller experimental target:

```sh
make build TARGET=tangnano9k SPINAL_LANES=1
```

Generated bitstreams are written under `build/`.

## Run Host Miner

After loading or flashing the FPGA:

```sh
scripts/mine-hardware.sh /dev/ttyUSB1
```

Useful overrides:

```sh
STRATUM_HOST=pool.example.com
STRATUM_PORT=3333
STRATUM_USER='wallet.worker'
STRATUM_PASS=x
HARDWARE_FPGA_TARGET=quick21
HARDWARE_SUGGEST_DIFFICULTY=0.009539
NO_SUBMIT=1
VERBOSE=1
```

## Test Without Hardware

```sh
python scripts/tools/emulator_smoke.py
scripts/mine-software.sh
scripts/mine-rtl.sh
scripts/sim.sh
SPINAL_ROUND_SKIP=1 scripts/sim.sh
make -C stratum test
make -C stratum smoke-fakes
```

## Common Options

Modeled hashrate:

```text
clock_hz * SPINAL_LANES / lane_period_cycles
```

`lane_period_cycles` is `65` for the default 20K Gowin build: the full SHA
pipeline has a 64-cycle base cadence plus one pass-boundary fence. Unfenced
full-64 builds use `64`. `SPINAL_ROUND_SKIP=1` uses a 61-cycle base cadence by
precomputing rounds 0..2 once per job and stopping the second pass at round 60
for the low-word candidate filter. `SPINAL_REGISTER_PASS_OUTPUTS=1` adds one
lane-cycle pass-boundary fence; `SPINAL_REGISTER_COMPRESSOR_OUTPUTS=1` adds
another cycle for the one-cycle compressor datapath. Treat round-skip and
`SPINAL_CSA_ROUND=1` builds as experimental until
`serial_smoke.py --target quick21 --count 100 --require-target` and quick14/23
spot checks pass on real hardware.

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
DEFAULT_FLOW=oss make build TARGET=tangnano20k \
  SPINAL_LANES=4 \
  SPINAL_CLOCK_PROFILE=111m \
  SPINAL_ENABLE_ECHO=1 \
  SPINAL_ENABLE_HARDCODED=1 \
  SPINAL_REGISTER_PASS_OUTPUTS=0 \
  SPINAL_MINIMIZE_SHA_RESET=0 \
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
