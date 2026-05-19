# Software-Only Emulation And Simulation

These flows do not require a Tang Nano board. They serve different purposes:

- `emu-smoke` runs the lightweight Python UART protocol emulator. This is
  useful for host software and packet-format testing.
- `software-mine` runs a pure Python miner-style candidate loop and prints
  share-like log lines.
- `hardware-mine` talks to a real serial port, or to the PTY emulator, using
  the same nonce-only hardware protocol as the FPGA.
- `sim-cocotb` runs Python/cocotb tests against the hand-written Verilog top
  level.
- `sim-cocotb-spinal` generates simulation-tuned SpinalHDL Verilog and runs the
  same cocotb UART tests against it.

The Tang Nano target is board-independent for simulation. The default target is
`tangnano20k`; use `TARGET=tangnano9k` when you want the rest of the Makefile
and logs to match the Tang Nano 9K.

The current 20K hardware model is:

```text
111 MHz / 16 aggregate clocks per nonce = 6.94 MH/s
```

The Python software miner intentionally defaults to a much easier `quick14`
target and a `6 kH/s` software estimate so it prints a candidate every few
seconds on a normal machine.

## Python Environment

On Ubuntu 24.04, use the repo installer:

```sh
scripts/setup.sh
```

By default this installs Python dependencies into `.venv`, downloads sbt into
`local/sbt`, downloads OSS CAD Suite into `local/oss-cad-suite`, and verifies
against the Tang Nano 20K target. The `local/` directory is ignored by git.

This installs `cocotb` and `pyserial` into `.venv`.

## Protocol Emulator

Run the software-only protocol smoke test:

```sh
python scripts/tools/emulator_smoke.py
```

Run a pseudo-terminal that behaves like a TangMiner UART device:

```sh
python scripts/tools/tangminer_emulator.py --board tangnano20k --pty
```

The emulator prints a PTY path. Host software can open that path instead of
`/dev/ttyUSB*` or `/dev/cu.usbserial-*`.
`tangminer_emulator.py` can also start an automatic benchmark job after the PTY
is ready, so hashrate appears without a separate host client.
The default reporting interval is 2 seconds; use
`--stats-interval 1` to change it,
`--stats-interval 0` to disable reports, or `--no-auto-benchmark` to skip the
automatic job.
These lines are tagged `source=hardware_estimate` by default because they report
the measured RTL cycle-count rate, not Python emulator speed.
Use `--stats-source software` only when you want to measure the software model
itself.

This emulator does not execute RTL. It is only a host/protocol model.

To exercise the hardware mining loop without a board, start the PTY emulator
without its automatic benchmark and point `hardware_mine.py` at the printed PTY:

```sh
python scripts/tools/tangminer_emulator.py --board tangnano20k --pty --no-auto-benchmark --max-nonces 1000 --stats-interval 0
python scripts/tools/hardware_mine.py --target quick3 --count 3 /dev/pts/N
```

For a pure software candidate test, use:

```sh
python scripts/tools/software_mine_test.py --count 10
```

The software test defaults to `quick14` and a `6 kH/s` software estimate, which
averages about one candidate every `2.7` seconds. Add `--rate-source software`
to display measured Python model speed, or `--rate-source hardware` to display
the RTL estimate. Both mining scripts print concise `share` lines by default;
add `--verbose` for timing details.

For a real board after loading or flashing the bitstream, use the same hardware
miner script with the serial device. `quick21` is the script default and is
useful for frequent logs; use `quick23` or `quick26` for quieter candidate
output:

```sh
python scripts/tools/hardware_mine.py --target quick21 --count 10 /dev/cu.usbserial-*
```

## RTL Simulation With Cocotb

Install a Verilog simulator. OSS CAD Suite is the simplest because it includes
Verilator, Icarus Verilog, and the Gowin build tools used by the rest of the
project:

```sh
source "$HOME/oss-cad-suite/environment"
```

Run the top-level UART tests against the SpinalHDL-generated RTL:

```sh
scripts/sim.sh
```

The lower-level cocotb make targets still support Icarus if needed:

```sh
make sim-cocotb SIM=icarus
```

The cocotb tests drive real UART bits into `uart_rx_pin`, read real UART bits
from `uart_tx_pin`, and verify:

- `TNE` echoes the parsed job payload.
- `TNJ` returns the expected nonce-zero genesis candidate with an all-ones
  target.
- `TNJ` with `quick3` returns the expected nonce-three genesis candidate.
- `TNH` runs the built-in nonce-zero genesis job.

For speed, the hand-written Verilog top has simulation-only macros that reduce
UART bit time and reset delay. These macros are only passed by the cocotb
Makefile and do not affect normal synthesis.

## SpinalHDL RTL Simulation

If Java and sbt are installed, generate simulation-tuned SpinalHDL Verilog and
run the same cocotb tests:

```sh
scripts/sim.sh
```

This uses `GenerateSimVerilog`, which keeps the production top-level ports but
shortens the UART divider and reset counter for simulation and bypasses the
20K production PLL. The normal `make build` path still uses production timing
and the current four-lane default, which is focused on Tang Nano 20K.
The SpinalHDL cocotb run also prints a `source=rtl_cycles` hashrate line derived
from RTL nonce-counter cycles, so that estimate is independent of simulator
wall-clock speed.
