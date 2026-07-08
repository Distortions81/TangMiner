# SHA256d Pipeline Design Notes

The current 20K core is an iterative one-round-per-cycle SHA256d engine. With
six lanes at 67.5 MHz and the pass-output fence enabled, it launches one nonce
per lane every 65 clocks:

```text
67.5 MHz * 6 lanes / 65 clocks = 6.231 MH/s
```

The lane/clock sweeps show that tuning this structure is not enough for
`>10 MH/s`: 7 lanes no longer fits with local K in the full 64-round path, and
5/6 lanes do not close above 67.5 MHz in the non-CSA datapath. CSA can close 5
lanes at 81 MHz, but that only ties the current 6.231 MH/s baseline.

## Host-Round-Skip Result

Moving the job-invariant first-pass rounds 0..2 to the host is implemented as
`SPINAL_HOST_ROUND_SKIP=1` on top of `SPINAL_ROUND_SKIP=1`. Strict RTL validates
the 7-lane 67.5 MHz shape with a 62-cycle lane period:

```text
67.5 MHz * 7 lanes / 62 clocks = 7.62 MH/s
```

That is above the 20% target on paper, but it does not close on the 20K:

| Build | RTL | Gowin result |
| --- | --- | --- |
| 7 lanes, `67m5`, host-round-skip, no pass fence, minimized reset | elaborates, 61-cycle period, modeled 7.75 MH/s | route option 0 routes but fails setup, Fmax 51.612 MHz |
| 7 lanes, `67m5`, host-round-skip, pass fence, minimized reset | strict cocotb passes, 62-cycle period, modeled 7.62 MH/s | normal route fails with 7098 unrouted nets; route option 0 routes but fails setup, Fmax 57.929 MHz |
| 7 lanes, `67m5`, host-round-skip, pass fence, minimized reset, second-pass low-word-only output | strict cocotb passes, 62-cycle period, modeled 7.62 MH/s | route option 0 routes but still fails setup, Fmax 57.929 MHz |
| 7 lanes, `67m5`, host-round-skip, shared K, pass fence, minimized reset, `GOWIN_ROUTE_MAXFAN=12` | strict cocotb passes, 62-cycle period, modeled 7.62 MH/s | normal route leaves 1617 unrouted nets; route option 0 routes but fails setup, Fmax 54.002 MHz |
| 7 lanes, `67m5`, host-round-skip, shared K, CSA-lite, pass fence, minimized reset, `GOWIN_ROUTE_MAXFAN=12` | strict cocotb passes, 62-cycle period, modeled 7.62 MH/s | synthesis exceeds 20K resources, 23587 logic |
| 6 lanes, `81m`, host-round-skip, pass fence, minimized reset | strict cocotb passes, 62-cycle period, modeled 7.84 MH/s | routes but fails setup, Fmax 69.220 MHz |
| 6 lanes, `81m`, host-round-skip, shared K, CSA-lite, pass fence, minimized reset, `GOWIN_ROUTE_MAXFAN=12` | strict cocotb passes, 62-cycle period, modeled 7.84 MH/s | normal route leaves 749 unrouted nets; route option 0 routes but fails setup, Fmax 73.456 MHz |
| 7 lanes, `67m5`, host-round-skip + `SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD=1` | strict cocotb passes, 63-cycle period, modeled 7.50 MH/s | normal route fails with 10546 unrouted nets; route option 0 routes but fails setup, Fmax 55.354 MHz |
| 6 lanes, `81m`, host-round-skip + `SPINAL_REGISTER_COMPRESSOR_OUTPUTS=1` | strict cocotb passes, 63-cycle period, modeled 7.71 MH/s | routes but fails setup, Fmax 62.839 MHz |

The hardware nonce-attempt counter is now pipelined and split into 32-bit
low/high halves, so it no longer dominates the dense host-round-skip timing
reports. After that cleanup, the remaining failing paths are inside the SHA
round update itself (`shaSecond/core/a_*` and first-pass prepared `core/a_*`).
Host-side precompute helps cadence, but not enough to overcome the one-round
critical path and routing pressure.

## Full-Path +20 Timing Attempts

The full SHA256d path also has RTL-valid configurations above the 20% modeled
target, but they do not close at the requested clocks:

| Build | RTL | Gowin result |
| --- | --- | --- |
| 6 lanes, `81m`, local K, pass fence, minimized reset, shared job state | strict cocotb passes, 65-cycle period, modeled 7.48 MH/s | routes but fails setup, Fmax 73.603 MHz; route option 0 Fmax 71.478 MHz |
| 6 lanes, `81m`, shared K, CSA-lite, pass fence, minimized reset, `GOWIN_ROUTE_MAXFAN=12` | strict cocotb passes, 65-cycle period, modeled 7.48 MH/s | closes at Fmax 81.015 MHz, but hardware `quick21` rejects it with 43 false positives in 100 jobs |
| 6 lanes, `84m`, local K, pass fence + first-pass feed-forward fence, minimized reset, shared job state | strict cocotb passes, 66-cycle period, modeled 7.64 MH/s | routes but fails setup, Fmax 77.155 MHz; maxfan 12 Fmax 77.106 MHz |
| 6 lanes, `84m`, previous row plus `SPINAL_BALANCED_ROUND_ADDER=1` | strict cocotb passes, 66-cycle period, modeled 7.64 MH/s | routes but fails setup, Fmax 71.450 MHz |

The shared-K CSA-lite row is the first image in this pass that meets the static
timing and modeled-throughput target, but the hardware false positives reject
it. At measured Fmax, the best hardware-unflashed timing-failing local-K row is
about `77.155 MHz * 6 lanes / 66 clocks = 7.01 MH/s`, still below the
7.48 MH/s 20% target. The data points away from more one-cycle round mapping
tweaks and toward deeper pipelining.

## Target Architecture

The first attempted design is a partially unrolled compressor with two SHA-256
rounds per cycle:

```text
rounds_per_cycle = 2
full compression = 32 cycles
round-skip first pass = ceil((64 - 3) / 2) = 31 cycles
round-skip second pass = ceil(61 / 2) = 31 cycles
```

Expected upper bounds before timing derate:

| Lanes | Clock | Full 2-round period | Modeled |
| ---: | ---: | ---: | ---: |
| 4 | 81.0 MHz | 33 cycles | 9.82 MH/s |
| 5 | 67.5 MHz | 33 cycles | 10.23 MH/s |
| 5 | 81.0 MHz | 33 cycles | 12.27 MH/s |
| 6 | 67.5 MHz | 33 cycles | 12.27 MH/s |

The first practical goal was `5 lanes @ 67.5 MHz` or `4 lanes @ 81 MHz`.

## Two-Round Combinational Result

`SPINAL_TWO_ROUNDS_PER_CYCLE=1` is implemented as an opt-in compressor mode.
It validates in RTL but is not a successful hardware improvement:

| Build | RTL | Gowin result |
| --- | --- | --- |
| 5 lanes, `67m5`, pass fence, minimized reset | strict cocotb passes, 33-cycle lane period, modeled 10.23 MH/s | placement fails with 336 unplaced LUT-equivalent resources |
| 6 lanes, `67m5`, pass fence, minimized reset | elaborates | synthesis resource failure, 24729 logic vs 20736 available |
| 4 lanes, `67m5`, pass fence, minimized reset | elaborates | routes but fails setup, Fmax 42.851 MHz, 836 setup endpoints |
| 4 lanes, `81m`, pass fence, minimized reset | elaborates | routes but fails setup, Fmax 44.450 MHz, 1215 setup endpoints |

At actual routed Fmax, even the 4-lane build would be about
`44.450 MHz * 4 / 33 = 5.388 MH/s`, below the current validated baseline.
The useful next architecture is therefore not a wider combinational two-round
step; it needs a deeper internal pipeline that keeps the round critical path
near one SHA round while improving throughput with staged nonce flow.

## Two-Round Pipelined Result

`SPINAL_TWO_ROUND_PIPELINE=1` adds a two-slot, two-stage compressor path. Stage
0 issues the first SHA round of a pair, stage 1 completes the second round or
the odd final round, and each compressor accepts a new full SHA256d start every
33 clocks in steady state. The lane controller uses small FIFOs between the
first and second pass so first-pass nonce starts, not final-result latency,
define the measured nonce-attempt cadence.

Strict RTL validation works:

| Build | RTL result | Modeled rate |
| --- | --- | --- |
| 1 lane, `67m5`, two-round pipeline | strict cocotb passes 7/7; 33-cycle cadence | 2.05 MH/s |
| 4 lanes, `67m5`, two-round pipeline | strict cocotb passes 7/7; 44 counted attempts | `4 * 67.5 / 33 = 8.18 MH/s` |

The 4-lane point is the first RTL-valid result above the 20% target, but it is
not hardware-usable on the 20K:

| Build | Gowin result |
| --- | --- |
| 4 lanes, `67m5`, two-round pipeline | synthesis resource failure, 31126 logic vs 20736 available |
| 3 lanes, `84m`, two-round pipeline | synthesis resource failure, 23476 logic vs 20736 available |
| 2 lanes, `126m`, two-round pipeline | routes but fails setup, Fmax 61.831 MHz, 4605 setup endpoints |

At the routed 2-lane Fmax, actual throughput would be only
`61.831 MHz * 2 / 33 = 3.75 MH/s`. Timing inspection shows the worst paths still
run through the round-pair datapath and first/second-pass output path, not
through the nonce-attempt counter or UART/control logic.

## Two-Phase Round Pipeline Result

`SPINAL_TWO_PHASE_ROUND_PIPELINE=1` is the first smaller per-round sub-stage
attempt. It splits one SHA round into a registered prepare phase (`t1`, `t2`,
message-schedule next word) and a completion phase. The first register-only
version interleaved two context slots so a compressor could accept a new full
SHA256d start every 64 clocks even though each individual round had two-cycle
latency.

Strict RTL validation works:

| Build | RTL result | Modeled rate |
| --- | --- | --- |
| 1 lane, `67m5`, two-phase round pipeline | strict cocotb passes 7/7; 64-cycle cadence | 1.05 MH/s |
| 6 lanes, `81m`, two-phase round pipeline | strict cocotb passes 7/7; 42 counted attempts, 64-cycle cadence | `6 * 81 / 64 = 7.59 MH/s` |

The 6-lane 81 MHz point is above the 20% target in RTL, but it is not
hardware-usable on the 20K. Gowin synthesis fails before placement because the
design requests 33155 DFFs against 15750 available. A 5-lane `100m286` build is
also rejected before timing with 25412 logic cells requested against 20736
available, so the 5/6/7-lane two-phase sweep is resource-limited independent of
clock profile. The duplicated context storage is the issue: applying two full
state/schedule slots plus a result stage to every first-pass and second-pass
compressor costs more resources than the device can provide.

The next per-round pipeline attempt needs to reduce context storage rather than
only splitting the arithmetic. Useful directions are RAM-like storage for the
two active schedules or a fuller streaming SHA256d pipeline with fewer
replicated lane shells.

The first context-memory variant used an async-read `Mem` for slot state and
schedule. It wrote slot context through a tiny memory and blocked new starts on
context writeback cycles, so the full-path lane cadence became 65 clocks instead
of 64. Strict RTL validated that form:

| Build | RTL result | Modeled rate |
| --- | --- | --- |
| 1 lane, `67m5`, async context-memory two-phase pipeline | strict cocotb passes 7/7; 65-cycle cadence | 1.038 MH/s |
| 4 lanes, `126m`, async context-memory two-phase pipeline | strict cocotb passes 7/7; 28 counted attempts, 65-cycle cadence | `4 * 126 / 65 = 7.75 MH/s` |

That was a valid modeled +20 candidate in RTL, but not a hardware candidate:
Gowin synthesis reported 21515 DFFs against 15750 available for the 4-lane
126 MHz build. Padding the context memory to 16 entries did not materially
change DFF mapping.

The current code uses a three-slot synchronous-read context memory. The extra
read pipeline recovered the 64-cycle launch cadence while avoiding Spinal's
async-memory warning:

| Build | RTL result | Gowin result |
| --- | --- | --- |
| 1 lane, `67m5`, sync context-memory two-phase pipeline | strict cocotb passes 7/7; 64-cycle cadence | not run |
| 4 lanes, `126m`, sync context-memory two-phase pipeline, FIFO depth 4 | strict cocotb passes 7/7; 28 counted attempts, 64-cycle cadence | synthesis resource failure, 16415 DFF vs 15750 available |
| 4 lanes, `126m`, same plus conditional K/fixed-stop register trims | strict cocotb passes 7/7; modeled `4 * 126 / 64 = 7.875 MH/s` | synthesis resource failure, 16399 DFF vs 15750 available |
| 4 lanes, `126m`, FIFO depth 2 | rejected by strict cocotb: quick14/quick21 time out and the counter sees a 130-cycle gap | not run |
| 4 lanes, `126m`, direct first-pass to second-pass handoff | one-lane strict cocotb passes 7/7 | synthesis resource failure, 29063 DFF vs 15750 available |
| 4 lanes, `126m`, no pass-output fence or two-phase first-pass fence bypass | strict cocotb passes 7/7 | synthesis resource failure, about 23110-23111 logic vs 20736 available |

The current sync-memory form is the best RTL two-phase point so far, but it is
still not a 20K hardware candidate. The remaining register overage is small
enough to study, but the failed fence-bypass experiments show that simply
removing output registers moves pressure into LUT/ALU resources. A successful
next version needs a schedule/context storage form that Gowin maps out of DFFs,
or a more global streaming SHA256d architecture with fewer replicated context
shells.

## Next Implementation Plan

1. Replace the per-compressor 768-bit context read register / stage storage with
   storage that Gowin maps into RAM, or redesign the SHA256d flow as a streaming
   pipeline with fewer replicated context shells.
2. Rework the first/second-pass interface as a streaming SHA256d pipeline so
   each stage has bounded fanout and only narrow nonce metadata crosses pass
   boundaries.
3. Keep K constants local to each compressor or stage. Shared or registered K
   is not a selected timing/correctness improvement for the current core.
4. Keep host-round-skip support as an input-side optimization, but do not count
   it as a hashrate improvement until a routed image closes timing and passes
   strict hardware nonce validation with `TNC`.

## Acceptance Gates

Do not count an unrolled build as a hashrate improvement unless all gates pass:

1. Cocotb strict nonce checks, including quick21.
2. Gowin timing with zero setup and hold violations.
3. Hardware `serial_smoke.py --target quick21 --count 100 --require-target`.
4. UART `TNC` nonce-attempt counter present during hardware validation.
5. End-to-end modeled MH/s greater than the validated 6.231 MH/s baseline.
