# Tang Mega 138K Bring-up Status

Status as of 2026-07-16: board support and the fully unrolled SHA-256d RTL are
implemented and simulation-tested. Routing closure and hardware validation are
still outstanding, so there is not yet a usable Tang Mega 138K bitstream.

## Completed

- Added the `tangmega138k` target for the `GW5AST-LV138PG484AC1/I0`, including
  the 50 MHz board clock, Dock USB-UART constraints, GW5 PLL generation, the
  Official Gowin EDA flow, and openFPGALoader integration.
- Added a fully unrolled SHA-256d pipeline: 61 first-pass rounds, a registered
  feed-forward boundary, and 61 second-pass rounds. The fixed Bitcoin padding
  rounds are precomputed or skipped.
- The pipeline has a 124-cycle result latency and an initiation interval of one,
  so one instance produces one completed double hash per clock after filling.
- Verified bit-exact results against Python `hashlib` over randomized headers,
  start nonces, and strides. Tests also cover continuous output, job flush,
  stop/restart, and nonce wrap.
- Verified the UART miner integration in simulation with both one- and
  two-pipeline configurations.

## Synthesis and Routing Evidence

These are diagnostic one-pipeline results, not final utilization or timing
claims. All three mappings reported a synthesis-only Fmax of 69.815 MHz.
The Gowin Logic total includes LUT, ALU, and RAM16 primitives.

| Delay mapping | Logic | Registers | Delay storage | Placed CLS | Routing result |
| --- | ---: | ---: | --- | ---: | --- |
| Automatic | 59,282 (43%) | 42,403 (31%) | 85 BSRAM, 656 RAM16 | not recorded | Best observed attempt ended with 199 unrouted nets at 100 MHz, placer 0/router 2 |
| Forced distributed | 64,437 (47%) | 44,998 (33%) | 1,492 RAM16 | 49,674 (72%) | 5,523 unrouted nets at 50 MHz, placer 0/router 2 |
| Forced block/native 36-bit | 55,346 (41%) | 39,467 (29%) | 160 BSRAM | 46,638 (68%) | 5,673 unrouted nets at 50 MHz, placer 0/router 2 |

An automatic-mapping/native-36-bit routing experiment was interrupted before
completion and produced no result. The Gowin build flow now exposes
`GOWIN_CONVERT_SDP32_36_TO_SDP16_18` so that experiment is reproducible.

Two complete pipelines synthesize at roughly 86% logic utilization and their
routing attempts failed with more than 133,000 unrouted nets. They are therefore
not a practical next step until one pipeline routes cleanly.

## Diagnosis

The generated design contains one registered SHA-256 round per stage; the first
and second passes are balanced, schedule bounds are correct, and resource usage
scales almost exactly twofold when a second pipeline is enabled. There is no
evidence of an accidentally duplicated pass or a multi-round combinational path.

The dominant routing problem is the topology Gowin creates for the shallow,
wide pipeline delays. Automatic inference mixes BSRAM and distributed RAM, with
shared memory-address nets reaching hundreds of loads. Forcing everything into
distributed RAM increases both CLS occupancy and address-net fanout; forcing
everything into block RAM instead congests the fixed BSRAM columns. The RTL no
longer forces either extreme and leaves mapping to synthesis.

## Prioritized TODO

1. Remove the redundant runtime resets on the 608-bit job-state registers, then
   rerun the bit-exact and UART regressions and compare synthesis fanout.
2. Replace the nonce and valid signals currently carried through all 124 stages
   with deterministic result-nonce and pipeline-fill tracking. Preserve job
   replacement, stop, restart, found-stop, and wrap behavior in tests.
3. Synthesize the cleaned-up automatic mapping and a register-only delay
   diagnostic. Compare CLS use, memory shape, and high-fanout address nets before
   spending time on another route.
4. Route one automatic-mapping pipeline with native 36-bit SDP memories, Gowin
   placer 1/router 0, and the default maximum-fanout setting. Keep the failed
   reports so congestion can be compared.
5. Require zero unrouted nets, timing closure, and a hardware UART mining smoke
   test before increasing the clock or reconsidering a second full pipeline.
6. If one full pipeline still cannot route after the metadata cleanup, implement
   the three-half architecture: fixed first and second halves plus one reusable
   half alternating between passes. Its target throughput is 1.5 hashes/clock
   with lower routing pressure than two full pipelines.

## Resume Command

After the RTL cleanup and synthesis comparison, the next route-worthy command is:

```sh
make TARGET=tangmega138k \
  SPINAL_LANES=1 SPINAL_CLOCK_PROFILE=50m \
  GOWIN_PROJECT_NAME=tangminer_mega138k_auto_native36_p1r0 \
  GOWIN_PLACE_OPTION=1 GOWIN_ROUTE_OPTION=0 \
  GOWIN_CONVERT_SDP32_36_TO_SDP16_18=0 \
  GOWIN_KEEP_FAILED=1 gowin-build
```

Do not treat synthesis Fmax as delivered hashrate. Acceptance requires a routed
bitstream, a valid post-route timing report, and end-to-end work submission on
hardware.
