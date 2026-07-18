# TangMiner

![TangMiner board](tangminer.png)

TangMiner is an experimental Bitcoin hash engine for Sipeed Tang Nano and Tang
Mega FPGA boards. The FPGA scans nonce ranges and reports candidates over
USB-UART; the host Stratum client handles pool work, full double-SHA256
validation, target checks, and share submission.

This is a learning and integration project, not an economically useful miner.

## Current Status

Default target:

- Board: Tang Nano 20K, `GW2AR-LV18QN88C8/I7`, 27 MHz input clock.
- Design: SpinalHDL/Scala top module generated to Verilog.
- Default build: official Gowin EDA, 6 lanes at `67.500 MHz`, local K
  constants, one-cycle pass-output fence, minimized SHA reset fanout.
- Modeled rate: `67.500 MHz * 6 / 65 = 6.231 MH/s`.
- Hardware validation: strict host nonce validation passes on real hardware.
  The latest SRAM-loaded revalidation on 2026-07-08 passed 100/100 `quick21`
  checks, with the hardware nonce-attempt counter queried over UART through
  `TNC`.

Tang Mega 138K default:

- The selected default is 28 iterative lanes at 50 MHz, local K constants, a
  pass-output fence, and minimized SHA reset fanout. It models at `21.538 MH/s`
  (28 lanes / 65 cycles) and passed 100/100 strict `quick21` jobs when loaded
  to SRAM.
- The default uses the validated Gowin place/route settings: place option 3,
  route option 2, clock-route order 0, hold correction enabled, and resource
  replication disabled.

The selected 20K default is:

```text
TARGET=tangnano20k
DEFAULT_FLOW=gowin
SPINAL_LANES=6
SPINAL_CLOCK_PROFILE=67m5
SPINAL_SHARED_K=0
SPINAL_REGISTER_PASS_OUTPUTS=1
SPINAL_MINIMIZE_SHA_RESET=1
SPINAL_ROUND_SKIP=0
SPINAL_CSA_ROUND=0
```

The previous open-source `5x54` image remains a hardware-validated fallback,
but it is no longer the default.

Recent 20K results are grouped by validation status below. A build is only
treated as hardware-valid after strict host nonce validation on a loaded FPGA.

Hardware-validated builds:

| Build | Rate | Evidence |
| --- | ---: | --- |
| 6 lanes, `67m5`, local K, pass fence, minimized reset | `6.231 MH/s` | current default; closes at `68.525 MHz` in the 2026-07-08 rerun; 100/100 `quick21` valid with `TNC` counter reads; 20/20 `quick14` spot check valid |
| 5 lanes, `67m5`, local K, pass fence, minimized reset | `5.192 MH/s` | closes at `67.507 MHz`; 20/20 `quick21` valid |
| 5 lanes, `54m`, open-source `synth_gowin -nowidelut` fallback | `4.219 MH/s` | historical fallback; 50/50 and 100/100 strict `quick21` valid |

Hardware-rejected builds:

| Build | Result | Hardware |
| --- | --- | --- |
| 6 lanes, `81m`, shared K, CSA-lite, pass fence, minimized reset, `GOWIN_ROUTE_MAXFAN=12` | strict cocotb passes at 65-cycle cadence, modeled `7.48 MH/s`; closes at Fmax `81.015 MHz` with 20299/20736 logic and 10283/10368 CLS | rejected: `quick21` returned 43 false positives in 100 jobs |

RTL-valid or modeled speedups that are not flashable yet:

| Build | Result | Blocking issue |
| --- | --- | --- |
| 4 lanes, `67m5`, local K, two-round pipeline, pass fence, minimized reset | strict cocotb passes at 33-cycle cadence, modeled `8.18 MH/s` | synthesis exceeds 20K resources, 31126 logic |
| 5 lanes, `100m286`, local K, register-only two-phase round pipeline, pass fence, minimized reset | modeled `7.83 MH/s` | synthesis exceeds 20K logic resources, 25412/20736 |
| 6 lanes, `81m`, local K, register-only two-phase round pipeline, pass fence, minimized reset | strict cocotb passes at 64-cycle cadence, modeled `7.59 MH/s` | synthesis exceeds 20K DFF resources, 33155/15750 |
| 4 lanes, `126m`, local K, async context-memory two-phase round pipeline, pass fence, minimized reset | strict cocotb passes at 65-cycle cadence, modeled `7.75 MH/s` | synthesis exceeds 20K DFF resources, 21515/15750 |
| 4 lanes, `126m`, local K, sync context-memory two-phase round pipeline, FIFO depth 4, pass fence, minimized reset | strict cocotb passes at 64-cycle cadence, modeled `7.875 MH/s` | synthesis exceeds 20K DFF resources, 16399/15750 after small register trims |
| 4 lanes, `126m`, sync context-memory two-phase with first-pass output fence bypass or no pass fence | strict cocotb passes | Gowin shifts failure from DFF to logic overuse, about 23110-23111/20736 logic |

Timing, placement, resource, or RTL failures:

| Build | Result | Hardware |
| --- | --- | --- |
| 6 lanes, `67m5`, pass fence only | fails setup timing | not flashed |
| 6 lanes, `67m5`, local K, pass fence, registered K | fails setup, Fmax `64.099 MHz` | not flashed |
| 6 lanes, `81m`, local K, pass fence, minimized reset, shared job state | strict cocotb passes at 65-cycle cadence, modeled `7.48 MH/s`; routes but fails setup, Fmax `73.603 MHz` | not flashed |
| 6 lanes, `84m`, local K, pass fence + first-pass feed-forward fence, minimized reset, shared job state | strict cocotb passes at 66-cycle cadence, modeled `7.64 MH/s`; routes but fails setup, Fmax `77.155 MHz`; `GOWIN_ROUTE_MAXFAN=12` is unchanged at Fmax `77.106 MHz` | not flashed |
| 6 lanes, `84m`, previous row plus balanced round adder | strict cocotb passes, but timing worsens to Fmax `71.450 MHz` | not flashed |
| 7 lanes, `67m5`, host-round-skip, pass fence, minimized reset | strict cocotb passes at 62-cycle cadence, modeled `7.62 MH/s`; route-option-0 Fmax `57.929 MHz`, unchanged after trimming second-pass output to the candidate low word | not flashed |
| 6 lanes, `81m`, host-round-skip, pass fence, minimized reset | routes but fails setup, Fmax `69.220 MHz`; shared-K CSA-lite with `GOWIN_ROUTE_MAXFAN=12` route-option-0 reaches only Fmax `73.456 MHz` | not flashed |
| 7 lanes, `67m5`, host-round-skip + first-pass feed-forward fence | strict cocotb passes at 63-cycle cadence, modeled `7.50 MH/s`; route-option-0 Fmax `55.354 MHz` | not flashed |
| 4 lanes, `120m`, local K, no pass fence, minimized reset | strict cocotb passes at 64-cycle cadence, modeled `7.50 MH/s`; routes but fails setup, Fmax `68.337 MHz` | not flashed |
| 5 lanes, `67m5`, local K, two rounds/cycle, pass fence, minimized reset | validates in cocotb at 33-cycle cadence but fails placement | not flashed |
| 4 lanes, `67m5`/`81m`, local K, two rounds/cycle, pass fence, minimized reset | routes but fails setup, Fmax `42.851`/`44.450 MHz` | not flashed |
| 3 lanes, `84m`, local K, two-round pipeline, pass fence, minimized reset | synthesis exceeds 20K resources, 23476 logic | not flashed |
| 2 lanes, `126m`, local K, two-round pipeline, pass fence, minimized reset | routes but fails setup, Fmax `61.831 MHz`; actual at Fmax is `3.75 MH/s` | not flashed |
| 4 lanes, `126m`, sync context-memory two-phase with FIFO depth 2 | strict cocotb rejects it: quick14/quick21 time out and the counter sees a 130-cycle gap | not flashed |

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

Build the Tang Mega 138K target with Official Gowin EDA:

```sh
make build TARGET=tangmega138k
make gowin-fmax TARGET=tangmega138k
```

Load it to SRAM and start the host miner:

```sh
TARGET=tangmega138k scripts/flash-and-mine.sh --load /dev/ttyUSB1
```

This target uses the `GW5AST-LV138PG484AC1/I0`, its 50 MHz board clock, the
Dock USB-UART pins, and the `tangmega138k` openFPGALoader board definition.
Its default is the hardware-validated 28-lane iterative configuration at
50 MHz (modeled `21.538 MH/s`); it uses the legacy 115200-baud UART protocol.
The constraint file selects the current Dock RX pin (`V14`); older Dock boards
that route RX to `Y14` need that one pin changed in `constr/tangmega138k.cst`.

Current implementation progress, synthesis evidence, routing diagnosis, and
next steps are tracked in the
[Tang Mega 138K bring-up status](docs/tang-mega-138k-status.md).

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
scripts/test-unrolled-pipeline.sh
scripts/mine-software.sh
scripts/mine-rtl.sh
scripts/sim.sh
SPINAL_ROUND_SKIP=1 scripts/sim.sh
TARGET=tangmega138k SPINAL_LANES=2 \
  SPINAL_SIM_STRICT_NONCE_CHECKS=1 scripts/sim.sh
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
another cycle for the one-cycle compressor datapath.
`SPINAL_FULLY_UNROLLED=1` selects the Tang Mega throughput architecture. Each
pipeline contains 61 registered first-pass rounds, a registered feed-forward,
61 registered second-pass rounds, and a registered candidate output. The first
candidate appears 124 clocks after injection and subsequent candidates appear
every clock, so its modeled rate is simply `clock_hz * SPINAL_LANES`. It
requires both `SPINAL_HOST_ROUND_SKIP=1` and `SPINAL_ROUND_SKIP=1`.
`SPINAL_HALF_UNROLLED=1` selects a two-cycle folded version of that pipeline.
Each SHA pass uses 31 physical round stages: an advance cycle processes the
even member of each round pair and the feedback cycle processes the odd member.
The first candidate appears 123 clocks after start and subsequent candidates
appear every two clocks, for a modeled rate of
`clock_hz * SPINAL_LANES / 2`. It also requires host round skip and round skip.
Use `scripts/test-half-unrolled-pipeline.sh` for direct bit-exact RTL coverage.
`SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD=1` adds a targeted first-pass
feed-forward fence in the full path and host-round-skip experiments.
`SPINAL_TWO_ROUNDS_PER_CYCLE=1` is an experimental, local-K-only partially
unrolled compressor; it passes RTL strict nonce checks, but current 20K Gowin
builds either fail placement or fail setup timing, so it is not a selected
hardware image. `SPINAL_TWO_ROUND_PIPELINE=1` is an experimental staged
two-round compressor for full SHA256d mode only; it validates in strict RTL at a
33-cycle steady-state nonce-start cadence, but current 20K builds fail resource
or timing closure. `SPINAL_BALANCED_ROUND_ADDER=1` is an experimental one-cycle
round-adder mapping; it validates in RTL but worsened the measured Gowin Fmax.
`SPINAL_CSA_ROUND=1` uses carry-save reduction for the round state adders.
`SPINAL_CSA_SCHEDULE=1` also maps the message-schedule adder to CSA, but the
full CSA form is usually too large for dense 20K builds, so the schedule path is
left normal by default.
Treat round-skip, host-round-skip, first-pass feed-forward, two-round,
two-round-pipeline, balanced-adder, and `SPINAL_CSA_ROUND=1` builds as
experimental until
`serial_smoke.py --target quick21 --count 100 --require-target` passes on real
hardware. Match additional spot-check targets to the hardware candidate-filter
mode; the selected default uses `SPINAL_FIXED_CANDIDATE=2`, so it is a quick21
filter image.

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
- `sim/unrolled_pipeline/`: direct, bit-exact fully unrolled pipeline test.
- `docs/`: protocol, hardware, emulation, and bring-up notes.

Start with:

- [docs/bringup.md](docs/bringup.md)
- [docs/uart-protocol.md](docs/uart-protocol.md)
- [docs/software-emulation.md](docs/software-emulation.md)
- [docs/hardware-overview.md](docs/hardware-overview.md)
- [docs/sha256d-pipeline-design.md](docs/sha256d-pipeline-design.md)

## License

TangMiner is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
