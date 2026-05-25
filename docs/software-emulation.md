# Software Emulation And RTL Simulation

These flows do not require a Tang Nano board. They exercise the same host-side
C Stratum program used for hardware mining.

## Setup

On Ubuntu 24.04:

```sh
scripts/setup.sh
```

This installs Python dependencies into `.venv`, downloads sbt into `local/sbt`,
and downloads OSS CAD Suite into `local/oss-cad-suite`. The `local/` directory
is ignored by git.

## Protocol Smoke Test

Run the lightweight software-only UART protocol test:

```sh
python scripts/tools/emulator_smoke.py
```

Run a pseudo-terminal that behaves like a TangMiner UART device:

```sh
python scripts/tools/tangminer_emulator.py --board tangnano20k --pty
```

The emulator prints a PTY path. Host software can open that path instead of
`/dev/ttyUSB*`. This emulator does not execute RTL; it models the observable
UART protocol and hash behavior.

## Stratum Host With A Fake FPGA

Run the C Stratum client against the Python fake FPGA:

```sh
scripts/mine-software.sh
```

This starts `stratum/tools/fake_fpga.py`, waits for its PTY, builds the Stratum
client if needed, and runs the host against the default pool.

Use `NO_SUBMIT=1` when testing against a live pool without submitting shares:

```sh
NO_SUBMIT=1 scripts/mine-software.sh
```

## Stratum Host With Verilated RTL

Run the same C host through the Verilated SpinalHDL UART design:

```sh
scripts/mine-rtl.sh
```

The launcher builds the simulation-tuned SpinalHDL Verilog, builds the
Verilator PTY bridge, benchmarks simulator wall-clock speed, chooses an
RTL-friendly candidate filter, and then starts the Stratum host.

For an offline fake-pool stack:

```sh
python stratum/tools/smoke_fake_stack.py --backend rtl
```

## Cocotb RTL Tests

Run the UART-level tests against generated SpinalHDL RTL:

```sh
scripts/sim.sh
```

The tests drive real UART bits into `uart_rx_pin`, read real UART bits from
`uart_tx_pin`, and verify:

- `TNE` echoes the parsed job payload in simulation builds.
- `TNJ` returns the expected nonce-zero genesis candidate with an all-ones
  target.
- `TNJ` with `quick3` returns the expected nonce-three genesis candidate.
- `TNH` runs the built-in nonce-zero genesis job in simulation builds.
- The measured RTL cadence matches one tested nonce every `64 / lane_count`
  fabric clocks.

The normal `make build` path uses production timing and disables simulation-only
echo and hardcoded-job support. The simulation build enables those features so
the UART parser and smoke-job paths stay covered.
