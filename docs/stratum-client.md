# Stratum V1 Client Plan

TangMiner should keep Stratum and FPGA control as separate layers:

- Stratum session: pool TCP, JSON-RPC, subscribe, authorize, difficulty, notify,
  submit.
- Work builder: coinbase, merkle root, header, share target, midstate.
- Hardware backend: TangMiner UART `TNJ`, `F || nonce`, candidate validation.

The C implementation in `stratum/` now covers the first UART mining path. It
runs standalone on a PC so protocol and hardware work can be tested before
ESP32 integration.

## Common Commands

```sh
make -C stratum
make -C stratum test
make -C stratum smoke-fakes
```

Normal UART mining:

```sh
scripts/mine-hardware.sh /dev/ttyUSB0
```

Manual equivalent:

```sh
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/ttyUSB0 \
  --fpga-target quick21
```

Software-emulated mining at about `5 kH/s`. This starts the fake FPGA and the
Stratum client together:

```sh
scripts/mine-software.sh
```

RTL-emulated mining uses Verilator and the same UART protocol:

```sh
scripts/mine-rtl.sh
python stratum/tools/smoke_fake_stack.py --backend rtl
```

Manual equivalent:

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

Use `--no-submit` for live-pool dry runs.

## Nonce Byte Order

The TangMiner UART response nonce is the exact four bytes inserted into header
bytes `76..79` for local validation. For Stratum `mining.submit`, pools expect
the displayed 32-bit nonce value, so the PC client byte-swaps the validated FPGA
nonce only for submit. The `extranonce2` string remains in the little-endian
byte order used when building the coinbase.

## Minimal Milestones

1. PC Stratum client logs jobs from a pool. Done.
2. Add work builder that converts `mining.notify` into an 80-byte header. Done.
3. Add TangMiner job encoder for `midstate[32] + tail[12] + target[32]`. Done.
4. Add a PC backend that talks to either `/dev/ttyUSB*` hardware or the PTY
   emulator. Done.
5. Validate `F || nonce` with CPU double-SHA256 and submit accepted shares. Done.
6. Move the same session and work-builder code into ESP-IDF with an lwIP
   transport and UART backend.

## ESP32 Boundary

The ESP32 port should provide:

- socket transport implementation for `include/stratum_transport.h`
- UART implementation for the current TangMiner binary protocol
- task/reconnect wrapper around `stratum_client_run_once`
- persistent pool settings outside the Stratum core

The Stratum core should not include ESP-IDF headers. That keeps PC tests useful
and prevents the protocol code from depending on FreeRTOS task state.

## Future ESP32 SPI Path

UART remains the current hardware interface. SPI is a good future ESP32-to-FPGA
interface once the Stratum and UART mining path is proven.

The SPI design should keep the same software layering:

- Stratum client: unchanged.
- Work builder: unchanged.
- Hardware backend: replace UART backend with an SPI backend.

Recommended shape:

- ESP32 is SPI master.
- FPGA is SPI slave.
- Use one chip-select dedicated to TangMiner.
- Keep mode and clock conservative at first, such as SPI mode 0 at `1 MHz`,
  then raise speed after signal integrity and timing are verified.
- Use a small register/FIFO protocol rather than streaming raw UART frames.

Candidate register map:

```text
0x00  ID/version/status
0x04  control: start, stop, clear result FIFO
0x08  candidate mode or target mode
0x10  job write window: midstate[32] + tail[12] + target[32]
0x60  result FIFO count
0x64  result read window: nonce[4], optional flags/lane/timestamp
```

The first SPI milestone should behave exactly like UART:

1. Host writes one job.
2. Host starts scanning.
3. FPGA scans nonces.
4. Host polls result count.
5. Host reads nonce candidates.
6. Host still performs full double-SHA256 validation and target comparison.

Do not move Stratum, coinbase construction, merkle calculation, or share
submission into the FPGA. SPI should only improve the hardware transport and
make room for future status/control features such as result FIFO depth, lane
state, job sequence numbers, clock status, or nonce-range completion.

## Current Limitations

- Plain TCP only.
- No reconnect loop.
- No share queue.
- JSON support is intentionally narrow and assumes normal Stratum server
  formatting without escaped strings in job fields.
- The CLI blocks in the job callback while waiting for one FPGA candidate, which
  is acceptable for the first UART path but should become an event loop or task
- The PC CLI now has a network thread and UART worker thread. This is close to
  the ESP32 task shape, but the thread/queue glue is still POSIX-specific and
  should be ported to FreeRTOS queues/task notifications for ESP32.
