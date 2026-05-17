# TangMiner

![tangminer](tangminer.png)

TangMiner is an experimental Bitcoin miner for the [Sipeed Tang Nano 20K](https://wiki.sipeed.com/hardware/en/tang/tang-nano-20k/nano-20k.html) and [Tang Nano 9K](https://wiki.sipeed.com/hardware/en/tang/Tang-Nano-9K/Nano-9K.html) FPGA boards based on [Gowin](https://gowinsemi.com) Arora FPGAs.

FPGAs are generally more efficient Bitcoin miners than CPUs and GPUs, but are nowhere near the performance of even the oldest mining ASICs. Gowin Arora FPGAs are small by FPGA standards but were selected for this project for affordability and open source toolchain support. I don't wish the horror of proprietary vendor FPGA toolchains on anyone.

This is a learning and integration project, not an economically useful miner. The active gateware is authored in SpinalHDL/Scala, generates Verilog, and uses a compact iterative SHA-256 compressor driven over the Tang Nano USB-UART by host miner software such as [Mujina](https://github.com/256foundation/mujina).

## Status

Current working state:

- Tang Nano 20K is the default target. The active SpinalHDL design uses four compact SHA-256 lanes and is focused on the 20K area/timing budget.
- Tang Nano 9K board files remain in the tree for comparison and simulation, but the four-lane bitstream is not aimed at fitting the 9K.
- The host protocol is documented below and implemented directly in the FPGA UART parser.
- Mujina integration work lives in its experimental Tang Nano FPGA backend [mujina-tangminer](https://github.com/skot/mujina/tree/tangminer). The current hardware contract reports nonce-only candidates and leaves full share validation on the host.
- The SpinalHDL bitstream uses a small byte-order-aware candidate filter instead of a full 256-bit FPGA target comparator.
- Legacy hand-written Verilog remains in the tree for comparison and Icarus-based simulation.

Still worth improving:

- SpinalHDL-native simulation coverage with known block-header vectors.
- Better hashrate accounting and long-run hardware statistics in the host integration.
- More cores or pipelining if the design graduates from bring-up into performance work.

## Toolchain

TangMiner uses the open Gowin FPGA flow. The easiest complete install is [OSS CAD Suite](https://github.com/YosysHQ/oss-cad-suite-build), which bundles the FPGA build tools used here.

Main tools:

- [Yosys](https://github.com/YosysHQ/yosys) for Verilog synthesis.
- [nextpnr](https://github.com/YosysHQ/nextpnr), specifically `nextpnr-himbaechel`, for Gowin place and route.
- [Project Apicula](https://github.com/YosysHQ/apicula), specifically `gowin_pack`, for Gowin bitstream packing.
- [openFPGALoader](https://github.com/trabucayre/openFPGALoader) for SRAM loading and flash programming.
- [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL) for Scala-authored hardware generation.
- [sbt](https://github.com/sbt/sbt) for building and running the SpinalHDL generator.
- [OpenJDK](https://github.com/openjdk/jdk) for the JVM used by sbt and SpinalHDL.
- [Icarus Verilog](https://github.com/steveicarus/iverilog), provided by OSS CAD Suite as `iverilog` and `vvp`, for the legacy Verilog simulations.
- [cocotb](https://www.cocotb.org/) with Verilator or Icarus for software-only UART-level RTL simulation.

## Installing The Complete Toolchain

On macOS, install Java and sbt with [Homebrew](https://github.com/Homebrew/brew):

```sh
brew install openjdk sbt
```

Install OSS CAD Suite from the official releases. Pick the asset that matches your machine; for Apple Silicon macOS:

```sh
cd "$HOME"
OSS_CAD_PLATFORM=darwin-arm64
export OSS_CAD_PLATFORM
OSS_CAD_URL="$(
  curl -fsSL https://api.github.com/repos/YosysHQ/oss-cad-suite-build/releases/latest |
    python3 -c 'import json,sys,os
platform=os.environ["OSS_CAD_PLATFORM"]
assets=json.load(sys.stdin)["assets"]
print(next(a["browser_download_url"] for a in assets if platform in a["name"]))'
)"
curl -L -o oss-cad-suite.tgz "$OSS_CAD_URL"
tar -xzf oss-cad-suite.tgz
rm oss-cad-suite.tgz
```

For Linux, set `OSS_CAD_PLATFORM` to `linux-x64` or `linux-arm64`. For Windows, download the `windows-x64` installer from the release page.

Activate the FPGA tools in a shell:

```sh
source "$HOME/oss-cad-suite/environment"
```

The Makefile defaults to `OSS_CAD_SUITE=$HOME/oss-cad-suite` and `TARGET=tangnano20k`. Override either value when needed:

```sh
make build OSS_CAD_SUITE=/path/to/oss-cad-suite TARGET=tangnano9k
```

If your `openFPGALoader` comes from Homebrew or another package manager, you can override just that tool:

```sh
make flash OPENFPGALOADER=openFPGALoader
```

## Build

Build the default Tang Nano 20K SpinalHDL bitstream:

```sh
make build
```

This generates target-specific Verilog such as `build/spinal/tangnano20k/top.v` from `src/main/scala/tangminer/TangMiner.scala`, synthesizes it, runs place and route, and writes a target-specific bitstream such as `build/tangminer_spinal_tangnano20k.fs`.

Build with the Tang Nano 9K constraints. The current four-lane SpinalHDL design
is not area-validated for 9K:

```sh
make build TARGET=tangnano9k
```

Generate only the SpinalHDL Verilog:

```sh
make spinal-verilog
```

Build the legacy hand-written Verilog bitstream for comparison:

```sh
make build-verilog
```

## Software-Only Emulation And RTL Simulation

Set up the Python tools:

```sh
make setup-emulation
. .venv/bin/activate
```

On Ubuntu 24.04, the repo installer can set up `.venv` and a local OSS CAD Suite install:

```sh
scripts/install_ubuntu_24_04.sh
```

Run the lightweight protocol emulator smoke test:

```sh
make emu-smoke TARGET=tangnano9k
scripts/launch_ubuntu_24_04.sh emu-smoke
```

Run a software-only miner-style share log. This uses the Python hash model to
find candidates, but reports the default `81 MHz / 16 cycles` RTL hashrate
estimate so the output matches expected hardware behavior:

```sh
make software-mine
make software-mine MINE_ARGS='--count 5'
```

Use `--rate-source software` when you specifically want to see Python model
speed instead of the hardware estimate. Use `--verbose` for scanned counts and
timing detail.

Run the software UART emulator as a pseudo-terminal:

```sh
scripts/run_emulator.sh
```

The wrapper starts an automatic benchmark job after the PTY is ready, so
hashrate appears without a separate host client. By default it reports
`source=hardware_estimate`, computed from the measured RTL cycle count and the
configured hardware clock. Change the interval with:

```sh
scripts/run_emulator.sh --stats-interval 1
```

Use `--no-auto-benchmark` when you only want a quiet PTY for host software.
Use `--stats-source software` only when you want to measure Python emulator
execution speed.

Run top-level UART RTL simulation with cocotb and Verilator:

```sh
source "$HOME/oss-cad-suite/environment"
make sim-cocotb TARGET=tangnano9k SIM=verilator
scripts/launch_ubuntu_24_04.sh sim-cocotb
```

If Java and sbt are installed, run the same cocotb tests against simulation-tuned SpinalHDL-generated Verilog:

```sh
make sim-cocotb-spinal TARGET=tangnano9k SIM=verilator
scripts/launch_ubuntu_24_04.sh sim-cocotb-spinal
```

The SpinalHDL cocotb suite includes a cycle-count hashrate check. It reports
`source=rtl_cycles` by watching the RTL nonce counter and computing the
hardware-rate estimate from simulated clock cycles, not simulator wall-clock
time. On the Tang Nano 20K, the active four-lane pass-pipelined SHA-256 design
uses an internal PLL to run the FPGA fabric at `81 MHz` and currently measures:

```text
81 MHz / 16 clocks per aggregate nonce = 5.06 MH/s
```

Set `HARDWARE_CLOCK_HZ` when running cocotb to report the same measured cycle
count at a different planned hardware clock.

See [docs/hardware-overview.md](docs/hardware-overview.md) for a block diagram
of the generated hardware datapath and nonce loop.

See [docs/compression-circuitry.md](docs/compression-circuitry.md) for diagrams
of the compact SHA-256 compressor and the two-pass nonce pipeline.

See [docs/software-emulation.md](docs/software-emulation.md) for the emulator, pseudo-terminal, and simulator options.

## Load To SRAM

Load the SpinalHDL bitstream to SRAM:

```sh
make load
```

For the Tang Nano 20K, explicitly selecting the FTDI channel and a conservative JTAG clock can help if the onboard debugger was recently switched into UART mode:

```sh
make load OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
```

## Flash

Flash the SpinalHDL bitstream:

```sh
make flash
```

For the Tang Nano 20K, this form is often the most reliable:

```sh
make flash OPENFPGALOADER='openFPGALoader --ftdi-channel 0 --freq 2000000'
```

Use `make load-verilog` or `make flash-verilog` only when testing the legacy Verilog design.

## Serial Smoke Test

The board communicates over USB-UART at `115200 8N1`. After loading the FPGA bitstream, put the Tang Nano 20K BL616 bridge into UART mode from its console if needed:

```text
choose uart
```

Then run the smoke tests:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install pyserial

python scripts/serial_smoke.py --echo --timeout 2 /dev/cu.usbserial-*
python scripts/serial_smoke.py --timeout 3 /dev/cu.usbserial-*
```

The echo test should report `ECHO OK`. The hash test uses an easy all-ones target and should return an `F` response with a nonce. The script recomputes the double-SHA-256 hash on the host side, prints the candidate share difficulty in Bitcoin difficulty-1 units, and reports whether the returned nonce meets the requested target.

For more frequent candidate output on the default `81 MHz` 20K build, use the
named `quick23` target:

```sh
python scripts/serial_smoke.py --target quick23 --watch --timeout 10 /dev/cu.usbserial-*
```

For a hardware miner-style share log:

```sh
make hardware-mine MINE_ARGS='/dev/cu.usbserial-*'
```

Use `--verbose` to include observed serial round-trip rate and timing detail on
each share line.

## Host Serial Protocol

TangMiner exposes a tiny binary UART protocol so host miner software can treat the FPGA as a hash engine. The FPGA UART is fixed at `115200 8N1` with no flow control.

Every host command starts with the two sync bytes `TN`, followed by a one-byte command tag. Unknown command tags are ignored and reset the parser back to sync search.

### Start Job

Host to FPGA:

```text
"T" "N" "J"
midstate[32]
tail[12]
target[32]
```

Total length: `79` bytes.

Fields:

- `midstate`: SHA-256 internal state after bytes `0..63` of the 80-byte Bitcoin block header.
- `tail`: header bytes `64..75`, excluding the nonce. These 12 bytes become the final three SHA-256 message words before the nonce word.
- `target`: 32-byte big-endian proof-of-work target integer. In the current four-lane 20K design, this field selects a cheap FPGA-side candidate filter instead of a full 256-bit exact comparator. The host remains responsible for the authoritative target check.

When a job is accepted, the FPGA starts four lanes at nonce words `0x00000000`
through `0x00000003`; each lane increments by `4`. A new `TNJ` command
replaces the current work and restarts those lane residues from zero.

The FPGA constructs the first-pass final block as:

```text
tail[12] || nonce_word[4] || 0x80 || zero padding || 0x00000280
```

It then performs the second SHA-256 pass over the 32-byte first digest. The FPGA byte-reverses the final SHA digest for Bitcoin proof-of-work bit ordering, checks only the selected candidate prefix, and reports candidate nonces to the host. Recognized target aliases are:

- `all-ones` / `easy`: always report the first checked nonce for smoke tests.
- `quick3`: require the top 3 bits of `reverse_bytes(hash)` to be zero; this is used by the short RTL test.
- `quick21`: require the top 21 bits of `reverse_bytes(hash)` to be zero.
- `quick23`: require the top 23 bits of `reverse_bytes(hash)` to be zero; this averages about 1.7 seconds per candidate on the 81 MHz 20K build and remains the default for arbitrary targets.
- `quick26`: require the top 26 bits of `reverse_bytes(hash)` to be zero; this averages about 13 seconds per candidate on the 81 MHz 20K build.

### Found Response

FPGA to host:

```text
"F"
nonce[4]
```

Total length: `5` bytes.

`nonce` is returned as the four bytes that were inserted into the hashed Bitcoin header. Host software should copy those bytes into header bytes `76..79` or parse them as a Bitcoin little-endian nonce field.

The host must validate every returned nonce before submitting a share. Mujina does this by rebuilding the block header, double-hashing it on the host, and checking the resulting block hash against the actual share target.

### Echo Job

Host to FPGA:

```text
"T" "N" "E"
midstate[32]
tail[12]
target[32]
```

FPGA to host:

```text
"E"
midstate[32]
tail[12]
target[32]
```

Total response length: `77` bytes.

This command does not start hashing. It echoes the parsed payload and is useful for checking serial wiring, byte order, and parser alignment.

### Stop Job

Host to FPGA:

```text
"T" "N" "S"
```

This stops the current scan and returns the core to idle. There is no acknowledgement response.

### Hardcoded Test Job

Host to FPGA:

```text
"T" "N" "H"
```

This starts a built-in genesis-style easy-target test job. It exists for bring-up and smoke testing; miner host software should use `TNJ` for real work.

## Make A Test Job Packet

```sh
python3 scripts/make_job.py \
  --header <80-byte-header-hex> \
  --target <32-byte-big-endian-target-hex|quick26|quick23|quick21|quick3|all-ones> \
  > job.bin
```

Send `job.bin` to the board over USB-UART. Mujina generates equivalent packets directly from pool work.

## Simulate

```sh
make sim
```

The current Icarus testbenches exercise the legacy hand-written Verilog. SpinalHDL simulation coverage is a next step.

## Hardware Notes

- Default board: Sipeed Tang Nano 20K
- 20K FPGA: `GW2AR-LV18QN88C8/I7`
- 20K family: `GW2A-18C`
- 9K FPGA: `GW1NR-LV9QN88PC6/I5`
- 9K family: `GW1N-9C`
- Clock input: onboard `27 MHz`
- 20K system clock: internal rPLL to `81 MHz`
- 9K system clock: onboard `27 MHz`
- UART: `115200 8N1`

The LED, clock, and UART pins follow the board-specific constraints in `constr/`.
