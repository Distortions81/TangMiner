# Software-Only Emulation And Simulation

These flows do not require a Tang Nano board. They serve different purposes:

- `emu-smoke` runs the lightweight Python UART protocol emulator. This is useful for host software and packet-format testing.
- `sim-cocotb` runs Python/cocotb tests against the hand-written Verilog top level.
- `sim-cocotb-spinal` generates simulation-tuned SpinalHDL Verilog and runs the same cocotb UART tests against it.

The Tang Nano target is board-independent for simulation. Use `TARGET=tangnano9k` when you want the rest of the Makefile and logs to match the Tang Nano 9K.

## Python Environment

On Ubuntu 24.04, use the repo installer:

```sh
scripts/install_ubuntu_24_04.sh
```

By default this installs Python dependencies into `.venv`, downloads sbt into `local/sbt`, downloads OSS CAD Suite into `local/oss-cad-suite`, and uses Tang Nano 9K for verification. The `local/` directory is ignored by git.

Create a local virtualenv and install the Python-side tools:

```sh
make setup-emulation
. .venv/bin/activate
```

This installs `cocotb` and `pyserial` into `.venv`.

## Protocol Emulator

Run the software-only protocol smoke test:

```sh
make emu-smoke TARGET=tangnano9k
```

The Ubuntu launcher runs this with the repo environment loaded:

```sh
scripts/launch_ubuntu_24_04.sh emu-smoke
```

Run a pseudo-terminal that behaves like a TangMiner UART device:

```sh
scripts/run_emulator.sh
make emu-pty TARGET=tangnano9k
```

Or through the launcher:

```sh
scripts/launch_ubuntu_24_04.sh emu-pty
```

The emulator prints a PTY path. Host software can open that path instead of `/dev/ttyUSB*` or `/dev/cu.usbserial-*`.
`scripts/run_emulator.sh` also starts an automatic benchmark job after the PTY is ready, so hashrate appears without a separate host client.
The default reporting interval is 2 seconds; use `scripts/run_emulator.sh --stats-interval 1` to change it, `--stats-interval 0` to disable reports, or `--no-auto-benchmark` to skip the automatic job.
These lines are tagged `source=hardware_estimate` by default because they report the measured RTL cycle-count rate, not Python emulator speed.
Use `--stats-source software` only when you want to measure the software model itself.

This emulator does not execute RTL. It is only a host/protocol model.

## RTL Simulation With Cocotb

Install a Verilog simulator. OSS CAD Suite is the simplest because it includes Verilator, Icarus Verilog, and the Gowin build tools used by the rest of the project:

```sh
source "$HOME/oss-cad-suite/environment"
```

Run the top-level UART tests against the hand-written Verilog:

```sh
make sim-cocotb TARGET=tangnano9k SIM=verilator
```

Or through the Ubuntu launcher, which automatically activates `.venv` and sources `local/oss-cad-suite/environment` when present:

```sh
scripts/launch_ubuntu_24_04.sh sim-cocotb
```

Use Icarus instead if that is the simulator you have installed:

```sh
make sim-cocotb TARGET=tangnano9k SIM=icarus
scripts/launch_ubuntu_24_04.sh --sim icarus sim-cocotb
```

The cocotb tests drive real UART bits into `uart_rx_pin`, read real UART bits from `uart_tx_pin`, and verify:

- `TNE` echoes the parsed job payload.
- `TNJ` returns the expected nonce-zero genesis hash with an all-ones target.
- `TNH` runs the built-in nonce-zero genesis job.

For speed, the hand-written Verilog top has simulation-only macros that reduce UART bit time and reset delay. These macros are only passed by the cocotb Makefile and do not affect normal synthesis.

## SpinalHDL RTL Simulation

If Java and sbt are installed, generate simulation-tuned SpinalHDL Verilog and run the same cocotb tests:

```sh
make sim-cocotb-spinal TARGET=tangnano9k SIM=verilator
scripts/launch_ubuntu_24_04.sh sim-cocotb-spinal
```

This uses `GenerateSimVerilog`, which keeps the production top-level ports but shortens the UART divider and reset counter for simulation. The normal `make build TARGET=tangnano9k` path still uses the production timing.
The SpinalHDL cocotb run also prints a `source=rtl_cycles` hashrate line derived from RTL nonce-counter cycles, so that estimate is independent of simulator wall-clock speed.
