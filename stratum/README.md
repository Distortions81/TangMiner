# TangMiner Stratum C Client

This directory contains the first portable C Stratum v1 miner path for
TangMiner. It connects to a plain TCP Stratum pool, subscribes, authorizes,
parses core server messages, builds TangMiner UART jobs, validates returned
nonces, and submits shares that meet the pool target. The PC CLI uses separate
network and UART worker threads so the active job can be replaced while the FPGA
is scanning.

## Quick Start

Build and run the local tests:

```sh
make -C stratum
make -C stratum test
make -C stratum smoke-fakes
```

Run a normal mining session against a TangMiner UART device:

```sh
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/ttyUSB0 \
  --fpga-target quick21
```

Run a software-emulated FPGA session. The hash-mode emulator is slow, about
`5 kH/s`, so use a low FPGA filter and a low suggested difficulty:

```sh
python3 stratum/tools/fake_fpga.py --mode hash --max-nonces 100000
stratum/build/stratum-client \
  --host tinyminer.m45core.com \
  --port 3333 \
  --user 3B86bWqfjdQeLEr8nkeeWU6ygksc2K7MoL.0M45 \
  --pass x \
  --serial-port /dev/pts/N \
  --fpga-target quick3 \
  --suggest-difficulty 0.0000049892
```

Useful options:

```text
--suggest-difficulty N    send mining.suggest_difficulty before authorize
--miner-name NAME         subscribe agent string, default TangMiner/0.1
--serial-port PORT        enable TangMiner UART backend
--serial-baud BAUD        default 115200
--fpga-target NAME        quick23 default; quick21/quick26/quick3/all-ones/share
--no-submit               validate candidates but do not submit shares
--quiet                   only print state changes and jobs
```

Add `--no-submit` when testing against a live pool and you only want local
validation logs.

## Hashrate And Byte Order

The selected Tang Nano 20K gateware runs four lanes at `111 MHz`, modeled as one
aggregate nonce every `16` fabric clocks, or `6.94 MH/s`.

The FPGA returns `F || nonce[4]` in the byte order inserted into Bitcoin header
bytes `76..79` for local double-SHA256 validation. Stratum submit uses the
displayed `uint32` nonce form, so the client byte-swaps the validated FPGA nonce
only when building `mining.submit`. `extranonce2` is still submitted in the same
little-endian byte order used in the coinbase.

## Current Scope

Implemented:

- POSIX TCP transport.
- `mining.subscribe`
- optional `mining.suggest_difficulty`
- `mining.authorize`
- `mining.set_difficulty`
- `mining.notify`
- `mining.set_extranonce`
- `mining.ping` response
- newline-delimited JSON receive loop
- POSIX serial backend for TangMiner `115200 8N1`
- coinbase, merkle root, header, midstate, and `TNJ` packet generation
- CPU double-SHA256 nonce validation
- `mining.submit` for candidates that meet the pool share target
- automatic repeated UART jobs from one pool notify through a bounded
  UART worker thread
- in-memory share queue drained by the Stratum/network thread

Deferred:

- TLS Stratum.
- Reconnect policy.
- ESP-IDF build files.

## Fake Test Tools

Two local test tools cover most PC-side scenarios without a real pool or FPGA:

```sh
python3 stratum/tools/fake_pool.py
python3 stratum/tools/fake_fpga.py
```

Or run a full fake-stack smoke test:

```sh
make -C stratum smoke-fakes
```

Both print the endpoint to use:

```text
fake_pool_addr=127.0.0.1:NNNNN
fake_fpga_pty=/dev/pts/N
```

Then run:

```sh
stratum/build/stratum-client \
  --host 127.0.0.1 \
  --port NNNNN \
  --user tester.worker \
  --pass x \
  --serial-port /dev/pts/N \
  --fpga-target all-ones
```

Useful fake pool options:

```text
--notify-count N
--notify-interval-ms MS
--difficulty N
--accept-submits / --no-accept-submits
--close-after-submits N
--vary-prevhash
--run-seconds N
```

Useful fake FPGA options:

```text
--mode fast|hash
--nonce 00000000
--delay-ms MS
--bad-every N
--drop-every N
--max-jobs N
```

`--mode hash` reuses the Python TangMiner protocol emulator and performs the
same candidate filtering as the current FPGA model. `--mode fast` is for
stress and failure testing; it accepts `TNJ` packets and returns deterministic
`F || nonce` responses immediately.

## ESP32 Port Plan

The reusable client code is in `src/stratum_client.c`, `src/stratum_json.c`,
and `src/stratum_messages.c`. ESP32 should keep those files and replace only
the transport:

```text
include/stratum_transport.h
src/stratum_transport_posix.c     PC implementation
esp32/stratum_transport_espidf.c  future lwIP/ESP-IDF implementation
```

For ESP-IDF, the transport can use lwIP sockets with the same behavior as the
POSIX file: `connect`, `recv`, `send`, and `close`, with short timeouts. Keep
plain TCP first. TLS can be added later with mbedTLS or `esp_transport_ssl`,
but it should stay behind the same transport interface.

UART should remain the first TangMiner hardware backend. SPI is a later ESP32
backend option, not a replacement for the Stratum code. The future SPI backend
should expose the same operations as the UART backend: load job, start/stop,
poll for candidate nonces, and read nonce results. See
`docs/stratum-client.md` for the proposed SPI register/FIFO outline.

The TangMiner-specific layer translates parsed `mining.notify` messages into:

1. coinbase transaction from `coinbase1 + extranonce1 + extranonce2 + coinbase2`
2. coinbase hash and merkle root
3. 80-byte Bitcoin header
4. SHA-256 midstate for header bytes `0..63`
5. `TNJ + midstate[32] + tail[12] + target[32]`

That layer is separate from the Stratum session code so the same client can
drive the PC emulator, a real Tang Nano over USB-UART, or an ESP32 UART.
