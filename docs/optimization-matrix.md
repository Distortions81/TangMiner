# Optimization Matrix

Snapshot date: 2026-05-25.

This note summarizes the optimization branches and local build artifacts that
exist in this checkout. It keeps historical sweep data because that explains
the current defaults. The active branch graph is:

- `main` / `origin/main`: selected production-trimmed 5-lane 20K design at
  `100.286 MHz`, with `NEXTPNR_SEED=13`.
- `width-exp` / `origin/width-exp`: experimental 61-cycle round skipping and
  wider local A/B compressor-pair lanes.
- `origin/sram-optimize`: experimental SHA message schedule storage using
  distributed LUT RAM.

No `AGENTS.md` or `agents.md` file exists inside this repository at the time of
this snapshot.

## Current Selected Build

The current default build is:

```text
TARGET=tangnano20k
SPINAL_LANES=5
SPINAL_CLOCK_PROFILE=100m286
SPINAL_ENABLE_ECHO=0
SPINAL_ENABLE_HARDCODED=0
SPINAL_FIXED_CANDIDATE=2
NEXTPNR_SEED=13
```

It models at `7.84 MH/s`. The relevant evidence is the direct seed-13
production result: `116.28 MHz` Fmax against the `100.286 MHz` timing target,
about `15.9%` margin.

## Historical Main Baseline

The previous selected baseline was four top-level lanes, one A/B compressor
pair per lane, full 64-cycle nonce cadence, and the 111 MHz clock profile.

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 90 MHz | Pass | 5.63 MH/s | 118.82 MHz | 32.0% | 65% | 52% | `build/sweep/lanes4_90m/build.log` |
| 4 lanes, 100.286 MHz | Pass | 6.27 MH/s | 116.90 MHz | 16.6% | 65% | 52% | `build/sweep/lanes4_100m286/build.log` |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 119.13 MHz | 7.3% | 65% | 52% | `build/sweep/lanes4_111m/build.log` |
| 4 lanes, 120 MHz | Fail timing | 7.50 MH/s | 110.75 MHz | -7.7% | 65% | 52% | `build/sweep/lanes4_120m/build.log` |
| 5 lanes, 90/100.286/111/120 MHz | Fail placement | 7.03-9.38 MH/s | n/a | n/a | 80% | 63% | `build/sweep/lanes5_*` |
| 5 lanes, 100.286 MHz, `synth_gowin -nowidelut`, seed 13 | Fail placement | 7.84 MH/s | n/a | n/a | 85% | 63% | `build/attempt-logs/normal5-100m286-nowidelut-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -retime`, seed 13 | Stopped/no final result | 7.84 MH/s | n/a | n/a | 90% | 71% | `build/attempt-logs/normal5-100m286-retime-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noabc9`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 196% | 63% | `build/attempt-logs/normal5-100m286-noabc9-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nodffe`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 118% | 63% | `build/attempt-logs/normal5-100m286-nodffe-seed13.log` |
| 5 lanes, 90 MHz, `synth_gowin -nowidelut`, seed 13 | Fail placement | 7.03 MH/s | n/a | n/a | 85% | 63% | `build/attempt-logs/normal5-90m-nowidelut-seed13.log` |

Historical takeaway: the 4-lane 111 MHz build was the best proven baseline in
the plain design. 120 MHz did not close timing, and a fifth plain lane did not
place even at lower clocks. The simple synthesis knobs that were worth trying on
production 5-lane builds did not rescue the normal untrimmed 5-lane build:
`-nowidelut` still failed placement even at 90 MHz, `-retime` increased area,
and disabling ABC9 or DFFE mapping badly overused LUT4s.

## Production Trimming

The `sweep-prod` and `sweep-prod5` artifacts appear to use production-oriented
trims such as no echo, no hardcoded smoke job, and fixed candidate filtering.

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 3 lanes, 111 MHz | Pass, below 5% preferred margin | 5.20 MH/s | 116.29 MHz | 4.8% | 44% | 39% | `build/sweep-prod/lanes3_111m/build.log` |
| 3 lanes, 120 MHz | Fail timing | 5.63 MH/s | 113.11 MHz | -5.7% | 43% | 39% | `build/sweep-prod/lanes3_120m/build.log` |
| 3 lanes, 135 MHz | Fail timing | 6.33 MH/s | 113.52 MHz | -15.9% | 44% | 39% | `build/sweep-prod/lanes3_135m/build.log` |
| 3 lanes, 150 MHz | Fail timing | 7.03 MH/s | 118.46 MHz | -21.0% | 44% | 39% | `build/sweep-prod/lanes3_150m/build.log` |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 118.61 MHz | 6.9% | 58% | 50% | `build/sweep-prod/lanes4_111m/build.log` |
| 4 lanes, 120 MHz | Fail timing | 7.50 MH/s | 108.19 MHz | -9.8% | 58% | 50% | `build/sweep-prod/lanes4_120m/build.log` |
| 5 lanes, 90 MHz | Pass | 7.03 MH/s | 101.98 MHz | 13.3% | 73% | 61% | `build/sweep-prod5/lanes5_90m/build.log` |
| 5 lanes, 100.286 MHz | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/sweep-prod5/lanes5_100m286/build.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 4 | Pass | 7.84 MH/s | 104.98 MHz | 4.7% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed4.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 5 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed5.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 6 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed6.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 7 | Fail timing | 7.84 MH/s | 97.22 MHz | -3.1% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed7.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 8 | No final result | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed8.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 9 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed9.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 10 | Pass | 7.84 MH/s | 108.68 MHz | 8.4% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed10.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 11 | No final result | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed11.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 12 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed12.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 13 | Pass | 7.84 MH/s | 116.28 MHz | 15.9% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, wide lane block, seeds 4/5/6/7/10/11/12/13 | Fail placement | 7.84 MH/s | n/a | n/a | 73% | 63% | `build/attempt-logs/wide5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noflatten` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/noflatten5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noflatten -noiopads` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/noflatten-noiopads5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `keep_hierarchy` on `BitcoinHashCore` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/keephier-core5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, staged top-only IO pads plus `keep_hierarchy` | Fail placement | 7.84 MH/s | n/a | n/a | 79% | 70% | `build/attempt-logs/staged-keephier-core5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nowidelut`, seed 13 | Pass | 7.84 MH/s | 110.04 MHz | 9.7% | 72% | 61% | `build/attempt-logs/opt-nowidelut5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -retime`, seed 13 | Fail placement | 7.84 MH/s | n/a | n/a | 78% | 67% | `build/attempt-logs/opt-retime5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noabc9`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 115% | 61% | `build/attempt-logs/opt-noabc9-5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nodffe`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 111% | 61% | `build/attempt-logs/opt-nodffe5-100m286-seed13.log` |

Takeaway: production trimming reduces area enough for a 5-lane 90 MHz build,
which slightly beats 4 lanes at 111 MHz in modeled rate. A direct nextpnr seed
rerun of the already-synthesized 5-lane 100.286 MHz netlist found three passing
seeds, four placement failures, one timing failure, and two no-result runs that
were stopped after about 24 minutes without a final result. That makes 5x100.286
the highest modeled local result tried so far, and seed 13 had a strong 116.28
MHz Fmax, but placement is still not deterministic enough to treat the result as
a stable replacement without locking down the seed and validating functionally.
Seed 13 has a packaged bitstream at
`build/direct-prod5-100m286-seed13/tangminer_spinal_tangnano20k.fs`.

The wide-lane experiment adds `SPINAL_WIDE_LANES=1`, which wraps all lanes in a
single `BitcoinHashWideLaneBlock` with local job registers and local found
selection. It did not help placement for the 5-lane 100.286 MHz case: all tried
comparison seeds failed legal placement, and area increased to 73% LUT4 and 63%
DFF.

The `-noflatten` experiment did preserve five `BitcoinHashCore` submodules, but
the open Gowin flow did not pack the resulting JSON. Plain `-noflatten` inserted
I/O buffers on internal module boundaries, causing unconstrained internal I/O
errors. Adding `-noiopads` removed that issue but left top-level outputs driven
by unsupported non-pad ports, so nextpnr still failed before placement.

Selective `keep_hierarchy` on `BitcoinHashCore` has the same direct-flow problem:
Yosys preserves the five core modules, but `iopadmap` still inserts internal I/O
buffers on core ports. A staged Yosys flow can avoid that by running
`synth_gowin -noiopads`, applying `iopadmap` only to `top`, and then finishing
the Gowin flow. That does produce a nextpnr-readable hierarchical JSON, but the
packed design is much larger than the flat baseline and seed 13 fails placement
with 79% LUT4 and 70% DFF.

Of the simple synthesis knobs tried after the online Yosys/Apicula guidance,
only `-nowidelut` produced a useful passing result. It avoids wide LUT packing
and still places/routes at seed 13, but Fmax drops to 110.04 MHz versus the
already-known flat seed 13 result at 116.28 MHz. `-retime` raises area and fails
placement; `-noabc9` and `-nodffe` both overuse LUT4 after packing.

Additional production seed checks for 4 lanes at 120 MHz:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 120 MHz, seed 2/full make | Fail timing | 7.50 MH/s | 108.19 MHz | -9.8% | 58% | 50% | `build/attempt-logs/prod4-120m-seed2.log` |
| 4 lanes, 120 MHz, direct nextpnr seed 4 | Fail timing | 7.50 MH/s | 116.65 MHz | -2.8% | 58% | 50% | `build/attempt-logs/direct-prod4-120m-seed4.log` |
| 4 lanes, 120 MHz, direct nextpnr seed 5 | Fail timing | 7.50 MH/s | 107.41 MHz | -10.5% | 58% | 50% | `build/attempt-logs/direct-prod4-120m-seed5.log` |

Takeaway: 4x120 remains unclosed. Seed 4 came close enough that more seed
searching may find a timing pass, but it would have little margin.

## Width Experiment

Branch `width-exp` adds these knobs:

- `SPINAL_ROUND_SKIP=1`: prepare first-pass rounds 0..2 once per job, start
  nonce work at round 3, and stop the second pass at round 60 for the low32
  candidate word. This changes cadence from 64 to 61 clocks per A/B pair.
- `SPINAL_PAIRS_PER_LANE=2` or `4`: place multiple local A/B compressor pairs
  inside each top-level lane so they share job state, prefix-prep outputs,
  constant lookup wiring, and local result selection.
- `NEXTPNR_ARGS`: allows seed and placer experiments from make.

Local logs show these paired-lane attempts:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 1 lane x 4 pairs, full 64, 111 MHz | Fail timing | 6.94 MH/s | 76.96 MHz | -30.7% | 58% | 47% | `build/paired1x4_full64/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 111 MHz | Fail timing | 7.28 MH/s | 85.06 MHz | -23.4% | 63% | 48% | `build/paired1x4/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 100.286 MHz | Fail timing | 6.58 MH/s | 92.85 MHz | -7.4% | 62% | 48% | `build/paired1x4_61_100m286/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 90 MHz | Pass | 5.90 MH/s | 94.62 MHz | 5.1% | 62% | 48% | `build/paired1x4_61_90m/pnr.log` |
| 2 lanes x 2 pairs, 111 MHz | Fail timing | 7.28 MH/s | 66.60 MHz | -40.0% | 70% | 52% | `build/paired2x2/place.log` |
| 4 lanes x 1 pair, 61-cycle, 90 MHz | Fail placement | 5.90 MH/s | n/a | n/a | 80% | 58% | `../TangMiner-width-exp/build/sweep-roundskip-single/lanes4_pairs1_skip1_90m/build.log` |
| 3 lanes x 1 pair, 61-cycle, 111 MHz | Fail timing | 5.46 MH/s | 56.38 MHz | -49.2% | 64% | 46% | terminal run, `../TangMiner-width-exp/build/roundskip-3x111` |
| 4 lanes x 1 pair, 61-cycle, 111 MHz, production trim | Fail timing | 7.28 MH/s | 91.64 MHz | -17.4% | n/a | n/a | terminal run, `../TangMiner-width-exp/build/roundskip-prod-4x111` |

Takeaway: round skipping helps modeled rate by about 4.9% at the same clock, but
the implementations tried so far have much worse Fmax or placement behavior
than the plain 4-lane layout. The only round-skip result that closes timing is
1x4 at 90 MHz, and its modeled 5.90 MH/s is worse than the previous 4-lane
111 MHz baseline. Single-pair round skip also failed in both untrimmed and
production-trimmed trials, so the current round-skip implementation is not
helpful without a structural timing fix.

## SRAM Schedule Experiment

Branch `origin/sram-optimize` changes `Sha256CompressWords` by replacing the
16-register shifting schedule window with three distributed LUT RAM taps
(`scheduleTap1`, `scheduleTap9`, and `scheduleTap14`) plus async reads. It also
registers active nonce/digest inputs for the first and second pass wrappers.

Tried result:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Extra RAM | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 122.13 MHz | 10.0% | 87% | 42% | 192 RAM16SDP4 | terminal run, `../TangMiner-sram-opt/build/sram-baseline-4x111` |

Observed impact:

- DFF drops materially versus the plain 4-lane baseline, from about 52% to 42%.
- LUT4 rises sharply, from about 65% to 87%, plus 192 distributed RAM cells.
- Timing still closes at 111 MHz and this run produced the best 4-lane 111 MHz
  Fmax observed locally. It is useful if register pressure is the limiting
  problem, but it consumes too much LUT headroom to be an obvious lane-count win
  by itself.

## What Helps

| Change | Status | Result |
| --- | --- | --- |
| 4 plain lanes at 111 MHz | Proven historical baseline | 6.94 MH/s modeled, 7.3% timing margin. |
| Production trim with fixed candidate/no echo/no hardcoded job | Proven useful | Reduces 4-lane LUT4 from about 65% to 58%, and enables a 5-lane 90 MHz build. |
| 5 production lanes at 90 MHz | Proven useful but marginal | 7.03 MH/s modeled, slightly above 4x111, with 13.3% timing margin. Needs functional/hardware validation before replacing baseline. |
| 5 production lanes at 100.286 MHz with seed search | Current default, seed-sensitive | Seeds 4, 10, and 13 pass; seed 13 reaches 116.28 MHz for 7.84 MH/s modeled. Several seeds still fail placement, so the default locks seed 13. |
| `synth_gowin -nowidelut` on 5 production lanes | Tried, possible fallback | Seed 13 passes at 110.04 MHz with the same headline LUT/DFF percentage as baseline, but it is slower than the normal seed 13 build. |
| 5 production lanes as one wide block | Tried, not helpful | `SPINAL_WIDE_LANES=1` increased area and failed placement for every tried comparison seed. |
| Global `synth_gowin -noflatten` | Tried, not usable directly | Preserves hierarchy but produces JSON that nextpnr rejects during I/O packing in this flow. |
| Selective/staged hierarchy preservation | Tried, not helpful | A custom top-only IO-pad flow reaches nextpnr, but area rises to 79% LUT4/70% DFF and placement fails. |
| SRAM/distributed schedule taps | Useful for DFF pressure, costly for LUTs | 4x111 passes at 122.13 MHz and cuts DFF to 42%, but LUT4 rises to 87%. |
| 61-cycle round skip | Tried, not helpful yet | Theoretical 4.9% cadence gain, but both paired and single-pair implementations lose too much Fmax or fail placement. |
| Wider local pairs, 1x4 or 2x2 | Tried, not helpful so far | Worse Fmax and lower best passing rate than baseline. |
| Plain 5 lanes | Tried, not helpful | Fails placement at 90 MHz and above without production trimming; `-nowidelut`, `-retime`, `-noabc9`, and `-nodffe` did not recover it. |
| 120 MHz clock profile | Tried, not helpful yet | Fails timing for baseline and production-trimmed 4-lane builds. |

## Untested Or Incomplete Combinations

- Continue hardware validation of the locked 5 production lane 100.286 MHz seed
  13 result.
- 5 production lanes at 111 MHz with seed search, if placement can be made
  reliable enough to justify trying a higher clock.
- `origin/sram-optimize` baseline sweep at 90/100.286/120 MHz.
- `origin/sram-optimize` plus production trim.
- Combined `width-exp` round-skip single-pair with `sram-optimize`. This needs
  a merge/test branch because the two branches touch the same compressor code.
- nextpnr seed sweeps for the close cases: 4-lane 120 MHz production and
  5-lane 100.286 MHz production.

## Recommended Next Sweep Order

1. Continue validating the production 5-lane 100.286 MHz seed 13 bitstream on
   hardware.
2. Sweep `origin/sram-optimize` with production trims to see whether the DFF
   reduction can combine with the smaller control surface without exhausting
   LUTs.
3. Pause further round-skip work until the critical path is restructured; the
   current implementation has now failed the clean single-pair checks.
